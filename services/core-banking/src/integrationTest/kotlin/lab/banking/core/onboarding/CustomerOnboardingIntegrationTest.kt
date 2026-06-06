package lab.banking.core.onboarding

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Paths
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(properties = ["banking-lab.security.enabled=true"])
@AutoConfigureMockMvc
@Testcontainers
class CustomerOnboardingIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @Autowired
    lateinit var passwordEncoder: PasswordEncoder

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              customer_auth_identities,
              customer_onboarding_requests,
              operator_approvals,
              audit_events,
              customer_kyc_profiles,
              customers
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
    }

    @Test
    fun `customer onboarding creates synthetic customer and auth identity only after checker approval`() {
        val requestBody = onboardingRequest("ONB-P1-001", username = "newcustomer01")
        val created = postOnboarding(requestBody, bearer("branch01", listOf("BRANCH_STAFF")))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.syntheticOnly").value(true))
            .andExpect(jsonPath("$.item.status").value("PENDING_APPROVAL"))
            .andExpect(jsonPath("$.item.requestedUsername").value("newcustomer01"))
            .andExpect(jsonPath("$.approval.businessType").value("CUSTOMER_ONBOARDING"))
            .andExpect(jsonPath("$.approval.status").value("PENDING"))
            .andReturn()
        val requestId = created.read("$.item.requestId")
        val approvalId = created.read("$.item.approvalId")

        postOnboarding(requestBody, bearer("branch01", listOf("BRANCH_STAFF")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replayed").value(true))
            .andExpect(jsonPath("$.item.requestId").value(requestId))
        assertEquals(1, countRows("customer_onboarding_requests"))
        assertEquals(0, countRows("customers"))

        val changedPayload = requestBody.toMutableMap()
        changedPayload["customerName"] = "Changed Synthetic Name"
        postOnboarding(changedPayload, bearer("branch01", listOf("BRANCH_STAFF")))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_CONFLICT"))

        approveOnboarding(requestId, "branch01", "BRANCH_MANAGER")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("MAKER_CHECKER_SELF_APPROVAL_REJECTED"))

        approveOnboarding(requestId, "manager01", "BRANCH_MANAGER")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.item.status").value("APPROVED"))
            .andExpect(jsonPath("$.item.approvedBy").value("manager01"))
            .andExpect(jsonPath("$.approval.approvalId").value(approvalId))
            .andExpect(jsonPath("$.approval.status").value("APPROVED"))

        val executed = executeOnboarding(requestId, "ONB-EXEC-P1-001")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.item.status").value("EXECUTED"))
            .andExpect(jsonPath("$.item.generatedCustomerId").value("SYN-CUS-NEW-000001"))
            .andExpect(jsonPath("$.item.generatedAuthSubject").value("SYN-AUTH-SYN-CUS-NEW-000001"))
            .andExpect(jsonPath("$.customer.customerId").value("SYN-CUS-NEW-000001"))
            .andExpect(jsonPath("$.customer.username").value("newcustomer01"))
            .andReturn()
        val customerId = executed.read("$.customer.customerId")

        executeOnboarding(requestId, "ONB-EXEC-P1-001")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replayed").value(true))
            .andExpect(jsonPath("$.customer.customerId").value(customerId))

        getOnboarding(requestId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("EXECUTED"))
            .andExpect(jsonPath("$.item.generatedCustomerId").value(customerId))

        assertEquals(1, countRows("customers WHERE customer_id = '$customerId'"))
        assertEquals(1, countRows("customer_kyc_profiles WHERE customer_id = '$customerId' AND kyc_status = 'VERIFIED'"))
        assertEquals(1, countRows("customer_auth_identities WHERE customer_id = '$customerId' AND username = 'newcustomer01'"))
        assertEquals(1, countRows("customer_onboarding_requests WHERE status = 'EXECUTED' AND generated_customer_id = '$customerId'"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'CUSTOMER_ONBOARDING_EXECUTED' AND customer_id = '$customerId'"))
        assertEquals(0, countRows("audit_events WHERE payload_json::text LIKE '%TempPass123!%'"))
        assertEquals(0, countRows("customer_onboarding_requests WHERE metadata_json::text LIKE '%TempPass123!%'"))

        val passwordHash = singleString("SELECT password_hash FROM customer_auth_identities WHERE username = 'newcustomer01'")
        assertTrue(passwordHash.startsWith("{bcrypt}"))
        assertFalse(passwordHash.contains("TempPass123!"))
        assertTrue(passwordEncoder.matches("TempPass123!", passwordHash))
    }

    @Test
    fun `customer onboarding rejects missing reason weak password duplicate username and rejected execute`() {
        val noReason = onboardingRequest("ONB-P1-NO-REASON", username = "noreason01").toMutableMap()
        noReason["reason"] = " "
        postOnboarding(noReason, bearer("branch01", listOf("BRANCH_STAFF")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("POLICY_REASON_REQUIRED"))

        val weakPassword = onboardingRequest("ONB-P1-WEAK", username = "weakuser01").toMutableMap()
        weakPassword["temporaryPassword"] = "short"
        postOnboarding(weakPassword, bearer("branch01", listOf("BRANCH_STAFF")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("REQUEST_VALIDATION_FAILED"))

        val first = postOnboarding(onboardingRequest("ONB-P1-DUP-1", username = "dupeuser01"), bearer("branch01", listOf("BRANCH_STAFF")))
            .andExpect(status().isCreated)
            .andReturn()
        val requestId = first.read("$.item.requestId")

        postOnboarding(onboardingRequest("ONB-P1-DUP-2", username = "DUPEUSER01"), bearer("branch01", listOf("BRANCH_STAFF")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("REQUEST_VALIDATION_FAILED"))

        rejectOnboarding(requestId, "manager01", "BRANCH_MANAGER")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.status").value("REJECTED"))
            .andExpect(jsonPath("$.approval.status").value("REJECTED"))

        executeOnboarding(requestId, "ONB-EXEC-REJECTED")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("WORKFLOW_STATE_VIOLATION"))
        assertEquals(0, countRows("customer_auth_identities"))
        assertEquals(0, countRows("customers"))
    }

    private fun postOnboarding(payload: Map<String, Any?>, authorization: String) =
        mockMvc.perform(
            post("/api/staff/customers/onboarding-requests")
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )

    private fun getOnboarding(requestId: String) =
        mockMvc.perform(
            get("/api/staff/customers/onboarding-requests/$requestId")
                .header("Authorization", bearer("auditor01", listOf("AUDITOR")))
        )

    private fun approveOnboarding(requestId: String, actor: String, role: String) =
        mockMvc.perform(
            post("/api/staff/customers/onboarding-requests/$requestId/approve")
                .header("Authorization", bearer(actor, listOf(role)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "approvedBy" to actor,
                            "approvedByRole" to role
                        )
                    )
                )
        )

    private fun rejectOnboarding(requestId: String, actor: String, role: String) =
        mockMvc.perform(
            post("/api/staff/customers/onboarding-requests/$requestId/reject")
                .header("Authorization", bearer(actor, listOf(role)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "rejectedBy" to actor,
                            "rejectedByRole" to role,
                            "rejectReason" to "Synthetic onboarding request rejected by checker"
                        )
                    )
                )
        )

    private fun executeOnboarding(requestId: String, idempotencyKey: String) =
        mockMvc.perform(
            post("/api/staff/customers/onboarding-requests/$requestId/execute")
                .header("Authorization", bearer("branch01", listOf("BRANCH_STAFF")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "executedBy" to "branch01",
                            "executedByRole" to "BRANCH_STAFF",
                            "reason" to "Execute approved synthetic onboarding request",
                            "idempotencyKey" to idempotencyKey
                        )
                    )
                )
        )

    private fun onboardingRequest(idempotencyKey: String, username: String): Map<String, Any?> =
        mapOf(
            "requestedBy" to "branch01",
            "requestedByRole" to "BRANCH_STAFF",
            "reason" to "Synthetic customer onboarding integration test",
            "idempotencyKey" to idempotencyKey,
            "customerName" to "Synthetic Customer $username",
            "customerPhone" to "010-9999-0001",
            "customerAddress" to "Seoul Synthetic Onboarding District",
            "customerGrade" to "STANDARD",
            "riskGrade" to "LOW",
            "sourceOfFundsCode" to "SALARY",
            "transactionPurposeCode" to "DAILY_BANKING",
            "username" to username,
            "temporaryPassword" to "TempPass123!"
        )

    private fun bearer(subject: String, roles: List<String>, customerId: String? = null): String {
        val payload = linkedMapOf<String, Any?>(
            "iss" to "http://keycloak.local/realms/banking-lab",
            "sub" to subject,
            "roles" to roles,
            "customerId" to customerId,
            "active" to true
        )
        val json = objectMapper.writeValueAsBytes(payload)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json)
        return "Bearer lab.$encoded.sig"
    }

    private fun MvcResult.read(jsonPointer: String): String =
        objectMapper.readTree(response.contentAsString).at(jsonPointer.replace("$.", "/").replace(".", "/")).asText()

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $tableExpression", emptyMap<String, Any?>(), Int::class.java) ?: 0

    private fun singleString(sql: String): String =
        jdbc.queryForObject(sql, emptyMap<String, Any?>(), String::class.java) ?: error("query returned null")

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.locations") {
                val userDir = Paths.get(System.getProperty("user.dir"))
                listOf(
                    "filesystem:${userDir.resolve("db/migrations").normalize()}",
                    "filesystem:${userDir.resolve("../../db/migrations").normalize()}"
                ).joinToString(",")
            }
        }
    }
}
