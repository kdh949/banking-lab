package lab.banking.core.ledger.application

import java.nio.file.Paths
import java.time.LocalDate
import lab.banking.core.common.BankingLabDomainException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
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
class LedgerProjectionRebuildWorkflowIntegrationTest {
    @Autowired
    lateinit var ledgerCommandService: LedgerCommandService

    @Autowired
    lateinit var projectionIntegrityService: LedgerProjectionIntegrityService

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @BeforeEach
    fun resetDatabase() {
        truncateCoreTables(jdbc)
    }

    @Test
    fun `approved rebuild restores projection without mutating ledger source rows and execute replay is idempotent`() {
        seedAccount("CUS-LPR-A", "ACC-LPR-A", "LAB-LPR-000001")
        ledgerCommandService.deposit(deposit("ACC-LPR-A", 100_000, "LPR-DEP-001"))
        ledgerCommandService.withdraw(withdrawal("ACC-LPR-A", 25_000, "LPR-WDR-001"))
        ledgerCommandService.closeBusinessDay(
            DailyClosingCommand(
                businessDate = LocalDate.of(2026, 1, 31),
                idempotencyKey = "LPR-CLOSE-001",
                requestedBy = "ops01"
            )
        )
        jdbc.update(
            """
            UPDATE account_balance_projections
            SET ledger_balance_minor = 10_000,
                available_balance_minor = 10_000,
                updated_at = now()
            WHERE account_id = 'ACC-LPR-A'
              AND currency = 'KRW'
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        val beforeTransactions = countRows("ledger_transactions")
        val beforePostings = countRows("ledger_postings")

        val request = projectionIntegrityService.requestRebuild(
            LedgerProjectionRebuildRequestCommand(
                requestedBy = "ops01",
                requestedByRole = "OPS_OPERATOR",
                reason = "Synthetic projection rebuild request",
                idempotencyKey = "LPR-REQUEST-001",
                accountId = "ACC-LPR-A",
                currency = "KRW"
            )
        )

        assertEquals(false, request.replayed)
        assertEquals("PENDING_APPROVAL", request.item.status)
        assertEquals("ACC-LPR-A", request.item.accountId)
        assertNotNull(request.approval.approvalId)
        assertEquals(1, countRows("operator_approvals"))

        val selfApproval = assertThrows(BankingLabDomainException::class.java) {
            projectionIntegrityService.approveRebuildRequest(
                request.item.requestId,
                LedgerProjectionRebuildApproveCommand(
                    approvedBy = "ops01",
                    approvedByRole = "OPS_MANAGER"
                )
            )
        }
        assertEquals("MAKER_CHECKER_SELF_APPROVAL_REJECTED", selfApproval.code)

        val executeBeforeApproval = assertThrows(BankingLabDomainException::class.java) {
            projectionIntegrityService.executeRebuild(
                request.item.requestId,
                LedgerProjectionRebuildExecuteCommand(
                    executedBy = "ops01",
                    executedByRole = "OPS_OPERATOR",
                    reason = "Synthetic premature projection rebuild",
                    idempotencyKey = "LPR-EXEC-BEFORE-APPROVAL"
                )
            )
        }
        assertEquals("WORKFLOW_STATE_VIOLATION", executeBeforeApproval.code)

        val approval = projectionIntegrityService.approveRebuildRequest(
            request.item.requestId,
            LedgerProjectionRebuildApproveCommand(
                approvedBy = "manager01",
                approvedByRole = "OPS_MANAGER"
            )
        )
        assertEquals(false, approval.replayed)
        assertEquals("APPROVED", approval.item.status)
        assertEquals("manager01", approval.item.approvedBy)

        val run = projectionIntegrityService.executeRebuild(
            request.item.requestId,
            LedgerProjectionRebuildExecuteCommand(
                executedBy = "ops01",
                executedByRole = "OPS_OPERATOR",
                reason = "Synthetic approved projection rebuild",
                idempotencyKey = "LPR-EXEC-001"
            )
        )

        assertEquals(false, run.replayed)
        assertEquals("COMPLETED", run.item.status)
        assertEquals(1, run.item.rebuiltItemCount)
        assertEquals(beforeTransactions, countRows("ledger_transactions"))
        assertEquals(beforePostings, countRows("ledger_postings"))
        assertEquals(75_000, balance("ACC-LPR-A", "ledger_balance_minor"))
        assertEquals(75_000, balance("ACC-LPR-A", "available_balance_minor"))
        assertEquals(run.item.beforeSourceHash, run.item.afterSourceHash)
        assertNotEquals(run.item.beforeProjectionHash, run.item.afterProjectionHash)
        assertEquals("REBUILT", run.items.single().status)
        assertEquals(10_000, run.items.single().previousLedgerBalanceMinor)
        assertEquals(75_000, run.items.single().rebuiltLedgerBalanceMinor)
        assertEquals(1, countAuditEvents("LEDGER_PROJECTION_REBUILD_EXECUTED", request.item.requestId))

        val replay = projectionIntegrityService.executeRebuild(
            request.item.requestId,
            LedgerProjectionRebuildExecuteCommand(
                executedBy = "ops01",
                executedByRole = "OPS_OPERATOR",
                reason = "Synthetic approved projection rebuild",
                idempotencyKey = "LPR-EXEC-001"
            )
        )
        assertEquals(true, replay.replayed)
        assertEquals(run.item.runId, replay.item.runId)
        assertEquals(1, countRows("ledger_projection_rebuild_runs"))
        assertEquals(1, countRows("ledger_projection_rebuild_items"))

        val postRebuildDrift = projectionIntegrityService.startDriftRun(
            LedgerProjectionDriftRunCommand(
                requestedBy = "ops01",
                requestedByRole = "OPS_OPERATOR",
                reason = "Synthetic post-rebuild drift verification",
                idempotencyKey = "LPR-DRIFT-AFTER-001",
                accountId = "ACC-LPR-A",
                currency = "KRW"
            )
        )
        assertEquals(0, postRebuildDrift.item.driftItemCount)
    }

    @Test
    fun `rebuild request is reason-required and request idempotency rejects changed payload`() {
        seedAccount("CUS-LPR-B", "ACC-LPR-B", "LAB-LPR-000002")
        ledgerCommandService.deposit(deposit("ACC-LPR-B", 20_000, "LPR-DEP-002"))

        val reasonError = assertThrows(BankingLabDomainException::class.java) {
            projectionIntegrityService.requestRebuild(
                LedgerProjectionRebuildRequestCommand(
                    requestedBy = "ops01",
                    requestedByRole = "OPS_OPERATOR",
                    reason = "",
                    idempotencyKey = "LPR-REQUEST-REASON",
                    accountId = "ACC-LPR-B",
                    currency = "KRW"
                )
            )
        }
        assertEquals("POLICY_REASON_REQUIRED", reasonError.code)

        projectionIntegrityService.requestRebuild(
            LedgerProjectionRebuildRequestCommand(
                requestedBy = "ops01",
                requestedByRole = "OPS_OPERATOR",
                reason = "Synthetic projection rebuild request",
                idempotencyKey = "LPR-REQUEST-CONFLICT",
                accountId = "ACC-LPR-B",
                currency = "KRW"
            )
        )
        val conflict = assertThrows(BankingLabDomainException::class.java) {
            projectionIntegrityService.requestRebuild(
                LedgerProjectionRebuildRequestCommand(
                    requestedBy = "ops01",
                    requestedByRole = "OPS_OPERATOR",
                    reason = "Different synthetic projection rebuild request",
                    idempotencyKey = "LPR-REQUEST-CONFLICT",
                    accountId = "ACC-LPR-B",
                    currency = "KRW"
                )
            )
        }
        assertEquals("IDEMPOTENCY_KEY_CONFLICT", conflict.code)
    }

    private fun deposit(accountId: String, amountMinor: Long, key: String): DepositCommand =
        DepositCommand(
            accountId = accountId,
            amountMinor = amountMinor,
            idempotencyKey = key,
            requestedBy = "branch01",
            requestedChannel = "STAFF_TERMINAL"
        )

    private fun withdrawal(accountId: String, amountMinor: Long, key: String): WithdrawalCommand =
        WithdrawalCommand(
            accountId = accountId,
            amountMinor = amountMinor,
            idempotencyKey = key,
            requestedBy = "branch01",
            requestedChannel = "STAFF_TERMINAL"
        )

    private fun seedAccount(customerId: String, accountId: String, accountNo: String) {
        jdbc.update(
            """
            INSERT INTO customers (customer_id, customer_name, customer_grade, risk_grade)
            VALUES (:customerId, :customerName, 'STANDARD', 'LOW')
            """.trimIndent(),
            mapOf("customerId" to customerId, "customerName" to "Synthetic $customerId")
        )
        jdbc.update(
            """
            INSERT INTO accounts (account_id, customer_id, account_no, currency, status)
            VALUES (:accountId, :customerId, :accountNo, 'KRW', 'ACTIVE')
            """.trimIndent(),
            mapOf("accountId" to accountId, "customerId" to customerId, "accountNo" to accountNo)
        )
    }

    private fun countRows(table: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $table", emptyMap<String, Any?>(), Int::class.java) ?: 0

    private fun balance(accountId: String, column: String): Long =
        jdbc.queryForObject(
            """
            SELECT $column
            FROM account_balance_projections
            WHERE account_id = :accountId
              AND currency = 'KRW'
            """.trimIndent(),
            mapOf("accountId" to accountId),
            Long::class.java
        ) ?: 0L

    private fun countAuditEvents(eventType: String, businessReferenceId: String): Int =
        jdbc.queryForObject(
            """
            SELECT count(*)
            FROM audit_events
            WHERE event_type = :eventType
              AND business_reference_id = :businessReferenceId
            """.trimIndent(),
            mapOf("eventType" to eventType, "businessReferenceId" to businessReferenceId),
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
