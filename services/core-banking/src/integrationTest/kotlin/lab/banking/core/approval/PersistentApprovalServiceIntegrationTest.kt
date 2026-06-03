package lab.banking.core.approval

import java.nio.file.Paths
import lab.banking.core.common.BankingLabDomainException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers
class PersistentApprovalServiceIntegrationTest {
    @Autowired
    lateinit var approvals: PersistentApprovalService

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
    fun `persistent approval rejects incomplete high-risk request before writing state`() {
        val error = assertThrows(BankingLabDomainException::class.java) {
            approvals.submit(
                SubmitApprovalCommand(
                    businessType = ApprovalBusinessTypes.ACCOUNT_HOLD,
                    businessReferenceId = "ACC-SYN-001-001",
                    requestedBy = "branch01",
                    requestReason = null,
                    afterSnapshot = mapOf("status" to "HOLD_REQUESTED")
                )
            )
        }

        assertEquals("POLICY_REASON_REQUIRED", error.code)
        assertEquals("REASON_REQUIRED", error.policy)
        assertEquals(0, countRows("operator_approvals"))
        assertEquals(0, countRows("audit_events"))
    }

    @Test
    fun `persistent approval enforces maker-checker and appends durable audit events`() {
        val approval = approvals.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.ACCOUNT_HOLD,
                businessReferenceId = "ACC-SYN-001-001",
                requestedBy = "branch01",
                requestReason = "Synthetic hold verification",
                beforeSnapshot = mapOf("status" to "ACTIVE"),
                afterSnapshot = mapOf("status" to "HOLD_REQUESTED"),
                screenId = "ACC-103"
            )
        )

        assertEquals(ApprovalStatus.PENDING, approval.status)
        assertNotNull(approval.auditEventId)
        assertEquals(1, countRows("operator_approvals"))
        assertEquals(1, countRows("audit_events"))
        assertEquals("HOLD_REQUESTED", approvals.approval(approval.approvalId).afterSnapshot?.get("status"))

        val selfApproval = assertThrows(BankingLabDomainException::class.java) {
            approvals.approve(
                approval.approvalId,
                ApproveApprovalCommand(approvedBy = "branch01", approvedByRole = "BRANCH_STAFF")
            )
        }
        assertEquals("MAKER_CHECKER_SELF_APPROVAL_REJECTED", selfApproval.code)
        assertEquals("MAKER_CHECKER_SEPARATION_OF_DUTIES", selfApproval.policy)
        assertEquals(ApprovalStatus.PENDING, approvals.approval(approval.approvalId).status)

        val approved = approvals.approve(
            approval.approvalId,
            ApproveApprovalCommand(approvedBy = "manager01", approvedByRole = "BRANCH_MANAGER")
        )

        assertEquals(ApprovalStatus.APPROVED, approved.status)
        assertEquals("manager01", approved.approvedBy)
        val events = approvals.auditEvents()
        assertEquals(2, events.size)
        assertTrue(events.any { it.eventType == "COMMAND_REQUESTED" && it.payload["approvalId"] == approval.approvalId })
        assertTrue(events.any { it.eventType == "COMMAND_APPROVED" && it.payload["approvalId"] == approval.approvalId })
        assertEquals(1, countRows("audit_events WHERE previous_event_hash IS NOT NULL"))
    }

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
