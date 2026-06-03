package lab.banking.core.approval

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ApprovalApiParityIntegrationTest {
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
              operator_approvals,
              audit_events
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
    }

    @Test
    fun `approval API persists request and rejects self approval with structured error`() {
        val submitResponse = mockMvc.perform(
            post("/api/approvals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "businessType": "ACCOUNT_HOLD",
                      "businessReferenceId": "ACC-SYN-001-001",
                      "requestedBy": "branch01",
                      "requestedByRole": "BRANCH_STAFF",
                      "requestReason": "Synthetic hold verification",
                      "beforeSnapshot": { "status": "ACTIVE" },
                      "afterSnapshot": { "status": "HOLD_REQUESTED" },
                      "screenId": "ACC-103"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.auditEventId").exists())
            .andReturn()

        val approvalId = objectMapper.readTree(submitResponse.response.contentAsString)
            .path("approvalId")
            .asText()

        mockMvc.perform(
            post("/api/approvals/$approvalId/approve")
                .header("x-request-id", "REQ-APPROVAL-SELF")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "approvedBy": "branch01",
                      "approvedByRole": "BRANCH_STAFF",
                      "screenId": "ACC-103"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("MAKER_CHECKER_SELF_APPROVAL_REJECTED"))
            .andExpect(jsonPath("$.error.policy").value("MAKER_CHECKER_SEPARATION_OF_DUTIES"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-APPROVAL-SELF"))
            .andExpect(jsonPath("$.error.route").value("/api/approvals/$approvalId/approve"))

        assertEquals("PENDING", approvalStatus(approvalId))
        assertEquals(1, countRows("audit_events"))

        mockMvc.perform(
            post("/api/approvals/$approvalId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "approvedBy": "manager01",
                      "approvedByRole": "BRANCH_MANAGER",
                      "screenId": "ACC-103"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.approvedBy").value("manager01"))

        mockMvc.perform(get("/api/approvals/$approvalId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.afterSnapshot.status").value("HOLD_REQUESTED"))

        assertEquals(1, countRows("operator_approvals WHERE status = 'APPROVED'"))
        assertEquals(2, countRows("audit_events"))
    }

    @Test
    fun `approval API validates high-risk reason before persistence`() {
        mockMvc.perform(
            post("/api/approvals")
                .header("x-request-id", "REQ-APPROVAL-REASON")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "businessType": "ACCOUNT_HOLD",
                      "businessReferenceId": "ACC-SYN-001-001",
                      "requestedBy": "branch01",
                      "afterSnapshot": { "status": "HOLD_REQUESTED" }
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("POLICY_REASON_REQUIRED"))
            .andExpect(jsonPath("$.error.policy").value("REASON_REQUIRED"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-APPROVAL-REASON"))

        assertEquals(0, countRows("operator_approvals"))
        assertEquals(0, countRows("audit_events"))
    }

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM $tableExpression",
            emptyMap<String, Any?>(),
            Int::class.java
        ) ?: 0

    private fun approvalStatus(approvalId: String): String? =
        jdbc.queryForObject(
            "SELECT status FROM operator_approvals WHERE approval_id = :approvalId",
            mapOf("approvalId" to approvalId),
            String::class.java
        )

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
