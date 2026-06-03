package lab.banking.core.complaint

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
class ComplaintCaseApiParityIntegrationTest {
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
              complaint_case_timeline,
              complaint_cases,
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
              'SYN-CUS-001', 'Lab Customer Alpha', '010-0000-1001', 'Seoul Synthetic District', 'STANDARD', 'LOW'
            )
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO complaint_cases (
              complaint_case_id, customer_id, category, description, status,
              sla_due_at, classification, owner_id
            )
            VALUES (
              'CMP-00000001', 'SYN-CUS-001', 'ACCOUNT_ACCESS', 'Cannot see account history',
              'IN_REVIEW', now() + interval '7 days', 'ACCOUNT_ACCESS', 'complaint01'
            )
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

    @Test
    fun `complaint answer is sent only after maker-checker approval`() {
        mockMvc.perform(
            post("/api/staff/complaints/CMP-00000001/answer-drafts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"actorId":"complaint01","reason":"Prepare customer complaint answer"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("REQUEST_VALIDATION_FAILED"))

        mockMvc.perform(
            post("/api/staff/complaints/CMP-00000001/answer-drafts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"actorId":"complaint01","body":"Synthetic answer explains the account history path."}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("POLICY_REASON_REQUIRED"))

        val draftResponse = mockMvc.perform(
            post("/api/staff/complaints/CMP-00000001/answer-drafts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "actorId": "complaint01",
                      "requestedByRole": "COMPLAINT_HANDLER",
                      "reason": "Prepare customer complaint answer",
                      "body": "Synthetic answer explains the account history path."
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("WAITING_APPROVAL"))
            .andExpect(jsonPath("$.item.answer").doesNotExist())
            .andExpect(jsonPath("$.approval.status").value("PENDING"))
            .andReturn()

        val approvalId = objectMapper.readTree(draftResponse.response.contentAsString)
            .path("approval")
            .path("approvalId")
            .asText()
        assertEquals("WAITING_APPROVAL", complaintStatus())

        mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "approvedBy": "complaint01",
                      "approvedByRole": "COMPLAINT_HANDLER",
                      "screenId": "CMP-201"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("MAKER_CHECKER_SELF_APPROVAL_REJECTED"))
        assertEquals("WAITING_APPROVAL", complaintStatus())

        mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "approvedBy": "manager01",
                      "approvedByRole": "BRANCH_MANAGER",
                      "screenId": "CMP-201"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("APPROVED"))
            .andExpect(jsonPath("$.executed").value(true))
            .andExpect(jsonPath("$.complaint.status").value("ANSWERED"))
            .andExpect(jsonPath("$.complaint.answer.body").value("Synthetic answer explains the account history path."))

        assertEquals("ANSWERED", complaintStatus())
        assertEquals(1, countRows("complaint_case_timeline WHERE event_type = 'ANSWERED'"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'COMMAND_APPROVED'"))

        mockMvc.perform(
            post("/api/staff/complaints/CMP-00000001/answer-drafts")
                .header("x-request-id", "REQ-CMP-ANSWERED-DRAFT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "actorId": "complaint01",
                      "requestedByRole": "COMPLAINT_HANDLER",
                      "reason": "Attempt duplicate answer draft",
                      "body": "Synthetic duplicate answer should be rejected."
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("WORKFLOW_STATE_VIOLATION"))
            .andExpect(jsonPath("$.error.domain").value("workflow"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-CMP-ANSWERED-DRAFT"))
            .andExpect(jsonPath("$.error.route").value("/api/staff/complaints/CMP-00000001/answer-drafts"))
    }

    private fun complaintStatus(): String? =
        jdbc.queryForObject(
            "SELECT status FROM complaint_cases WHERE complaint_case_id = 'CMP-00000001'",
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
