package lab.banking.core.security

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Paths
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(properties = ["banking-lab.security.enabled=true"])
@AutoConfigureMockMvc
@Testcontainers
class SecurityAuthorizationIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              fee_waiver_requests,
              account_hold_requests,
              reconciliation_adjustment_requests,
              reconciliation_items,
              operator_approvals,
              audit_events,
              masking_access_logs,
              screen_access_logs,
              account_balance_projections,
              ledger_postings,
              ledger_transactions,
              idempotency_keys,
              accounts,
              customers
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
        seedCustomersAndAccounts()
    }

    @Test
    fun `Keycloak simulator tokens enforce route roles actor binding ownership and denial audit`() {
        mockMvc.perform(
            get("/api/staff/customers/SYN-CUS-001/detail")
                .header("x-request-id", "REQ-AUTH-MISSING")
                .queryParam("reason", "Branch service request")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))
            .andExpect(jsonPath("$.error.domain").value("auth"))
            .andExpect(jsonPath("$.error.policy").value("RBAC_ABAC_POLICY_REQUIRED"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-AUTH-MISSING"))

        mockMvc.perform(
            get("/api/ops/reconciliation-items")
                .header("Authorization", bearer("branch01", listOf("BRANCH_STAFF")))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))

        mockMvc.perform(
            get("/api/customer/accounts/ACC-SYN-002-001/detail")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .queryParam("customerId", "SYN-CUS-002")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))
            .andExpect(jsonPath("$.error.domain").value("auth"))

        mockMvc.perform(
            get("/api/customer/accounts/ACC-SYN-001-001/detail")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .queryParam("customerId", "SYN-CUS-001")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.customerId").value("SYN-CUS-001"))
            .andExpect(jsonPath("$.maskedAccountNo").value("LAB-***-0001"))

        val changeResponse = mockMvc.perform(
            post("/api/staff/customers/SYN-CUS-001/change-requests")
                .header("Authorization", bearer("branch01", listOf("BRANCH_STAFF")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requestedBy": "branch01",
                      "requestedByRole": "BRANCH_STAFF",
                      "reason": "Customer requested phone update",
                      "afterSnapshot": {
                        "phone": "010-0000-1999"
                      }
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.status").value("PENDING"))
            .andReturn()

        val approvalId = objectMapper.readTree(changeResponse.response.contentAsString)
            .path("item")
            .path("approvalId")
            .asText()

        mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .header("Authorization", bearer("branch01", listOf("BRANCH_STAFF")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"approvedBy":"branch01","approvedByRole":"BRANCH_STAFF","screenId":"CST-103"}""")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))

        mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .header("Authorization", bearer("manager01", listOf("BRANCH_MANAGER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"approvedBy":"manager02","approvedByRole":"BRANCH_MANAGER","screenId":"CST-103"}""")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))
            .andExpect(jsonPath("$.error.policy").value("RBAC_ABAC_POLICY_REQUIRED"))

        mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .header("Authorization", bearer("manager01", listOf("BRANCH_MANAGER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"approvedBy":"manager01","approvedByRole":"BRANCH_MANAGER","screenId":"CST-103"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executed").value(true))
            .andExpect(jsonPath("$.customer.maskedPhone").value("010-****-1999"))

        assertEquals("010-0000-1999", customerPhone())
        assertEquals(4, countRows("audit_events WHERE event_type = 'AUTHORIZATION_DENIED'"))
    }

    @Test
    fun `invalid simulator token is rejected before domain execution`() {
        mockMvc.perform(
            get("/api/staff/customers/SYN-CUS-001/detail")
                .header("Authorization", "Bearer not-a-valid-token")
                .header("x-request-id", "REQ-AUTH-INVALID")
                .queryParam("reason", "Branch service request")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-AUTH-INVALID"))

        assertEquals(1, countRows("audit_events WHERE event_type = 'AUTHORIZATION_DENIED'"))
    }

    @Test
    fun `authorization denial keeps CORS headers for browser channel clients`() {
        mockMvc.perform(
            post("/api/staff/pii/unmask")
                .header("Origin", "http://localhost:3002")
                .header("Authorization", bearer("branch01", listOf("BRANCH_STAFF")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "requestedBy": "branch01",
                      "actorRole": "BRANCH_STAFF",
                      "reason": "Browser privileged unmask denial smoke"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isForbidden)
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3002"))
            .andExpect(header().string("Access-Control-Expose-Headers", "x-request-id"))
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))

        assertEquals(1, countRows("audit_events WHERE event_type = 'AUTHORIZATION_DENIED'"))
    }

    private fun seedCustomersAndAccounts() {
        jdbc.update(
            """
            INSERT INTO customers (
              customer_id, customer_name, customer_phone, customer_address, customer_grade, risk_grade
            )
            VALUES
              ('SYN-CUS-001', 'Lab Customer Alpha', '010-0000-1001', 'Seoul Synthetic District', 'STANDARD', 'LOW'),
              ('SYN-CUS-002', 'Lab Customer Beta', '010-0000-1002', 'Busan Synthetic District', 'STANDARD', 'LOW')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO accounts (account_id, customer_id, account_no, currency, status)
            VALUES
              ('ACC-SYN-001-001', 'SYN-CUS-001', 'LAB-001-000001', 'KRW', 'ACTIVE'),
              ('ACC-SYN-002-001', 'SYN-CUS-002', 'LAB-002-000001', 'KRW', 'ACTIVE')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO account_balance_projections (
              account_id, currency, ledger_balance_minor, available_balance_minor, hold_amount_minor
            )
            VALUES
              ('ACC-SYN-001-001', 'KRW', 100000000, 100000000, 0),
              ('ACC-SYN-002-001', 'KRW', 200000000, 200000000, 0)
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

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

    private fun customerPhone(): String? =
        jdbc.queryForObject(
            "SELECT customer_phone FROM customers WHERE customer_id = 'SYN-CUS-001'",
            emptyMap<String, Any?>(),
            String::class.java
        )

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM $tableExpression",
            emptyMap<String, Any?>(),
            Int::class.java
        ) ?: 0

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
