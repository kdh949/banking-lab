package lab.banking.core.aml

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Paths
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AmlCaseApiParityIntegrationTest {
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
              aml_case_comments,
              aml_cases,
              operator_approvals,
              audit_events,
              customers
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
        jdbc.update(
            """
            INSERT INTO customers (
              customer_id, customer_name, customer_phone, customer_address, customer_grade, risk_grade
            )
            VALUES (
              'SYN-CUS-003', 'Lab Customer Gamma', '010-0000-1003', 'Seoul Synthetic Risk District', 'STANDARD', 'HIGH'
            )
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO aml_cases (
              aml_case_id, customer_id, status, risk_score, alerts_json, str_simulation_json
            )
            VALUES (
              'AML-00000001', 'SYN-CUS-003', 'OPEN', 850,
              CAST(:alerts AS jsonb), '{}'::jsonb
            )
            """.trimIndent(),
            mapOf(
                "alerts" to """[{"ruleId":"AML-RULE-HIGH-RISK-CUSTOMER","message":"Synthetic high-risk customer transfer"}]"""
            )
        )
    }

    @Test
    fun `AML case closure reports simulated STR only after maker-checker approval`() {
        mockMvc.perform(
            post("/api/staff/aml-cases/AML-00000001/assign")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"actorId":"aml01","owner":"aml01"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("INVESTIGATING"))
            .andExpect(jsonPath("$.owner").value("aml01"))

        mockMvc.perform(
            post("/api/staff/aml-cases/AML-00000001/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"actorId":"aml01","body":"Synthetic AML investigation note."}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.comments[0].body").value("Synthetic AML investigation note."))

        mockMvc.perform(
            post("/api/staff/aml-cases/AML-00000001/closure-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"actorId":"aml01","disposition":"STR_SIMULATED"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("POLICY_REASON_REQUIRED"))

        val closureResponse = mockMvc.perform(
            post("/api/staff/aml-cases/AML-00000001/closure-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "actorId": "aml01",
                      "requestedByRole": "AML_REVIEWER",
                      "reason": "Synthetic high-risk customer AML investigation complete",
                      "disposition": "STR_SIMULATED",
                      "reportReferenceId": "STR-SIM-TEST-001"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("CLOSURE_REQUESTED"))
            .andExpect(jsonPath("$.item.strSimulation.reported").value(false))
            .andExpect(jsonPath("$.item.strSimulation.disposition").value("STR_SIMULATED"))
            .andExpect(jsonPath("$.approval.status").value("PENDING"))
            .andReturn()

        val approvalId = objectMapper.readTree(closureResponse.response.contentAsString)
            .path("approval")
            .path("approvalId")
            .asText()
        assertEquals("CLOSURE_REQUESTED", amlStatus())

        mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"approvedBy":"aml01","approvedByRole":"AML_REVIEWER","screenId":"AML-201"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("MAKER_CHECKER_SELF_APPROVAL_REJECTED"))
        assertEquals("CLOSURE_REQUESTED", amlStatus())

        mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"approvedBy":"manager01","approvedByRole":"COMPLIANCE_MANAGER","screenId":"AML-201"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executed").value(true))
            .andExpect(jsonPath("$.amlCase.status").value("CLOSED"))
            .andExpect(jsonPath("$.amlCase.approvalId").doesNotExist())
            .andExpect(jsonPath("$.amlCase.strSimulation.reported").value(true))
            .andExpect(jsonPath("$.amlCase.strSimulation.disposition").value("STR_SIMULATED"))
            .andExpect(jsonPath("$.amlCase.strSimulation.reportReferenceId").value("STR-SIM-TEST-001"))

        assertEquals("CLOSED", amlStatus())
        assertEquals(1, countRows("aml_case_comments WHERE aml_case_id = 'AML-00000001'"))
        assertEquals(1, countRows("operator_approvals WHERE business_type = 'AML_CASE_CLOSE' AND status = 'APPROVED'"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'COMMAND_REQUESTED'"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'COMMAND_APPROVED'"))

        mockMvc.perform(
            post("/api/staff/aml-cases/AML-00000001/closure-requests")
                .header("x-request-id", "REQ-AML-CLOSED-CLOSURE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "actorId": "aml01",
                      "requestedByRole": "AML_REVIEWER",
                      "reason": "Duplicate AML closure request after case closure",
                      "disposition": "STR_SIMULATED",
                      "reportReferenceId": "STR-SIM-TEST-001"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("WORKFLOW_STATE_VIOLATION"))
            .andExpect(jsonPath("$.error.domain").value("workflow"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-AML-CLOSED-CLOSURE"))
            .andExpect(jsonPath("$.error.route").value("/api/staff/aml-cases/AML-00000001/closure-requests"))
    }

    private fun amlStatus(): String? =
        jdbc.queryForObject(
            "SELECT status FROM aml_cases WHERE aml_case_id = 'AML-00000001'",
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
