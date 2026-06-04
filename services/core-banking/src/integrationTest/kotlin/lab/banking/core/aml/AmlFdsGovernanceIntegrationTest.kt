package lab.banking.core.aml

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.Base64
import org.hamcrest.Matchers.hasItem
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(
    properties = [
        "banking-lab.security.enabled=true",
        "banking-lab.security.step-up.enforcement-enabled=true"
    ]
)
@AutoConfigureMockMvc
@Testcontainers
class AmlFdsGovernanceIntegrationTest {
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
              sanctions_screening_hits,
              synthetic_str_reports,
              synthetic_aml_regulatory_reports,
              aml_case_evidence_packages,
              aml_case_comments,
              aml_cases,
              customer_transfer_results,
              fds_case_timeline,
              fds_cases,
              outbox_events,
              inbox_events,
              ledger_postings,
              ledger_transactions,
              account_balance_projections,
              account_limits,
              account_holds,
              accounts,
              customer_kyc_profiles,
              customers,
              operator_approvals,
              audit_events
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
        jdbc.update(
            """
            INSERT INTO customers (
              customer_id, customer_name, customer_grade, risk_grade, customer_phone, customer_address
            )
            VALUES
              (
                'CUS-H6-SANCTION', 'Synthetic Sanction Match', 'STANDARD', 'HIGH',
                '010-0000-6601', 'Synthetic sanctions screening district'
              ),
              (
                'CUS-H6-PEP', 'Synthetic Normal Customer', 'STANDARD', 'MEDIUM',
                '010-0000-6602', 'Synthetic PEP transfer district'
              )
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

    @Test
    fun `sanctions customer screening requires step up and records false-positive disposition with independent checker`() {
        val requestBody =
            """
            {
              "actorId": "aml01",
              "actorRole": "AML_REVIEWER",
              "reason": "Synthetic onboarding sanctions screening"
            }
            """.trimIndent()

        mockMvc.perform(
            post("/api/aml/governance/screen/customers/CUS-H6-SANCTION")
                .header("Authorization", bearer("aml01", listOf("AML_REVIEWER"), stepUp = false))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("STEP_UP_REQUIRED"))

        val screeningResponse = mockMvc.perform(
            post("/api/aml/governance/screen/customers/CUS-H6-SANCTION")
                .header("Authorization", bearer("aml01", listOf("AML_REVIEWER"), stepUp = true))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.syntheticOnly").value(true))
            .andExpect(jsonPath("$.hits[0].listType").value("SANCTIONS_SIM"))
            .andExpect(jsonPath("$.hits[0].matchType").value("CUSTOMER_ONBOARDING"))
            .andExpect(jsonPath("$.hits[0].riskScore").value(980))
            .andExpect(jsonPath("$.amlCase.status").value("OPEN"))
            .andExpect(jsonPath("$.amlCase.riskScore").value(980))
            .andReturn()
        val hitId = objectMapper.readTree(screeningResponse.response.contentAsString)
            .path("hits")
            .path(0)
            .path("hitId")
            .asText()

        mockMvc.perform(
            post("/api/aml/governance/hits/$hitId/false-positive-dispositions")
                .header("Authorization", bearer("aml01", listOf("AML_REVIEWER"), stepUp = true))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "dispositionBy": "aml01",
                      "dispositionByRole": "AML_REVIEWER",
                      "approvedBy": "aml01",
                      "approvedByRole": "COMPLIANCE_MANAGER",
                      "reason": "Synthetic self approval should fail"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("MAKER_CHECKER_SELF_APPROVAL_REJECTED"))
            .andExpect(jsonPath("$.error.policy").value("SANCTIONS_FALSE_POSITIVE_DISPOSITION_SEPARATION_OF_DUTIES"))

        mockMvc.perform(
            post("/api/aml/governance/hits/$hitId/false-positive-dispositions")
                .header("Authorization", bearer("aml01", listOf("AML_REVIEWER"), stepUp = true))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "dispositionBy": "aml01",
                      "dispositionByRole": "AML_REVIEWER",
                      "approvedBy": "compliance01",
                      "approvedByRole": "BRANCH_MANAGER",
                      "reason": "Synthetic wrong checker role should fail"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))

        mockMvc.perform(
            post("/api/aml/governance/hits/$hitId/false-positive-dispositions")
                .header("Authorization", bearer("aml01", listOf("AML_REVIEWER"), stepUp = true))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "dispositionBy": "aml01",
                      "dispositionByRole": "AML_REVIEWER",
                      "approvedBy": "compliance01",
                      "approvedByRole": "COMPLIANCE_MANAGER",
                      "reason": "Synthetic exact-name watchlist false positive"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("DISPOSITIONED"))
            .andExpect(jsonPath("$.disposition").value("FALSE_POSITIVE"))
            .andExpect(jsonPath("$.approvedBy").value("compliance01"))
            .andExpect(jsonPath("$.syntheticOnly").value(true))

        assertEquals(1, countRows("sanctions_screening_hits WHERE status = 'DISPOSITIONED'"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'SANCTIONS_SCREENING_HIT'"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'SANCTIONS_FALSE_POSITIVE_DISPOSITIONED'"))
    }

    @Test
    fun `transfer counterparty screening flags synthetic PEP and exposes active model card`() {
        mockMvc.perform(
            post("/api/aml/governance/screen/transfers")
                .header("Authorization", bearer("aml01", listOf("AML_REVIEWER"), stepUp = true))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "CUS-H6-PEP",
                      "transferReferenceId": "TR-H6-PEP-001",
                      "counterpartyName": "Synthetic PEP Match",
                      "actorId": "aml01",
                      "actorRole": "AML_REVIEWER",
                      "reason": "Synthetic transfer PEP screening"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.syntheticOnly").value(true))
            .andExpect(jsonPath("$.hits[0].listType").value("PEP_SIM"))
            .andExpect(jsonPath("$.hits[0].matchType").value("TRANSFER_COUNTERPARTY"))
            .andExpect(jsonPath("$.hits[0].transferReferenceId").value("TR-H6-PEP-001"))
            .andExpect(jsonPath("$.amlCase.status").value("OPEN"))

        mockMvc.perform(
            get("/api/aml/governance/model-card")
                .header("Authorization", bearer("audit01", listOf("AUDITOR"), stepUp = false))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.modelVersionId").value("AML-MODEL-SYN-RULES-V1"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.features").value(hasItem("amount_minor")))
            .andExpect(jsonPath("$.scoreDistribution.HIGH").value(2))
            .andExpect(jsonPath("$.syntheticOnly").value(true))
    }

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM $tableExpression",
            emptyMap<String, Any?>(),
            Int::class.java
        ) ?: 0

    private fun bearer(subject: String, roles: List<String>, stepUp: Boolean = false): String {
        val payload = linkedMapOf<String, Any?>(
            "iss" to "http://keycloak.local/realms/banking-lab",
            "sub" to subject,
            "roles" to roles,
            "active" to true,
            "auth_time" to Instant.now().epochSecond,
            "iat" to Instant.now().epochSecond
        )
        if (stepUp) {
            payload["amr"] = listOf("otp")
            payload["acr"] = "banking-lab-step-up"
        }
        val json = objectMapper.writeValueAsBytes(payload)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json)
        return "Bearer lab.$encoded.sig"
    }

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
                "filesystem:${migrationsPath()}"
            }
            registry.add("banking-lab.synthetic-seed.enabled") { "false" }
            registry.add("banking-lab.temporal.worker.enabled") { "false" }
            registry.add("banking-lab.outbox.worker.enabled") { "false" }
            registry.add("banking-lab.tracing.enabled") { "false" }
            registry.add("banking-lab.otel.tracing.export.enabled") { "false" }
        }

        private fun migrationsPath(): Path =
            generateSequence(Paths.get("").toAbsolutePath()) { it.parent }
                .map { it.resolve("db/migrations") }
                .first(Files::isDirectory)
    }
}
