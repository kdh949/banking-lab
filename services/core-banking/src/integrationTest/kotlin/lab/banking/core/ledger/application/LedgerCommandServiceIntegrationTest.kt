package lab.banking.core.ledger.application

import java.time.LocalDate
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import lab.banking.core.approval.ApprovalBusinessTypes
import lab.banking.core.common.BankingLabDomainException
import lab.banking.core.ledger.domain.PostingDirection
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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
class LedgerCommandServiceIntegrationTest {
    @Autowired
    lateinit var ledgerCommandService: LedgerCommandService

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              aml_cases,
              fds_cases,
              reconciliation_items,
              inbox_events,
              outbox_events,
              workflow_events,
              workflow_instances,
              masking_access_logs,
              screen_access_logs,
              operator_approvals,
              audit_events,
              daily_closings,
              idempotency_keys,
              ledger_postings,
              ledger_transactions,
              account_balance_projections,
              limit_usage_counters,
              account_holds,
              account_limits,
              accounts,
              customer_kyc_profiles,
              customers
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
    }

    @AfterEach
    fun verifyNoUnbalancedTransactions() {
        val unbalanced = jdbc.queryForObject(
            """
            SELECT count(*)
            FROM (
              SELECT ledger_transaction_id,
                     currency,
                     SUM(CASE WHEN direction = 'DEBIT' THEN -amount_minor ELSE amount_minor END) AS signed_total
              FROM ledger_postings
              GROUP BY ledger_transaction_id, currency
            ) totals
            WHERE signed_total <> 0
            """.trimIndent(),
            emptyMap<String, Any?>(),
            Int::class.java
        ) ?: 0
        assertEquals(0, unbalanced)
    }

    @Test
    fun `deposit withdrawal and transfer update PostgreSQL balance projections from postings`() {
        seedAccount("CUS-A", "ACC-A", "LAB-100-000001")
        seedAccount("CUS-B", "ACC-B", "LAB-100-000002")

        val deposit = ledgerCommandService.deposit(deposit("ACC-A", 100_000, "IT-DEP-A"))
        val withdrawal = ledgerCommandService.withdraw(withdrawal("ACC-A", 20_000, "IT-WDR-A"))
        val transfer = ledgerCommandService.internalTransfer(transfer("ACC-A", "ACC-B", 30_000, "IT-TRF-A"))

        assertEquals("DEPOSIT", deposit.value.transactionType)
        assertEquals("WITHDRAWAL", withdrawal.value.transactionType)
        assertEquals("INTERNAL_TRANSFER", transfer.value.transactionType)
        assertEquals(1, ledgerCommandService.countTransactionsByIdempotencyKey("IT-DEP-A"))
        assertEquals(1, ledgerCommandService.countTransactionsByIdempotencyKey("IT-WDR-A"))
        assertEquals(1, ledgerCommandService.countTransactionsByIdempotencyKey("IT-TRF-A"))
        assertEquals(50_000, ledgerCommandService.balance("ACC-A").availableBalanceMinor)
        assertEquals(30_000, ledgerCommandService.balance("ACC-B").availableBalanceMinor)
    }

    @Test
    fun `withdrawal cannot exceed available balance and does not mutate ledger on failure`() {
        seedAccount("CUS-A", "ACC-A", "LAB-100-000001")
        ledgerCommandService.deposit(deposit("ACC-A", 10_000, "IT-WDR-FAIL-SEED"))
        val beforeTransactionCount = countTransactions()
        val beforeBalance = ledgerCommandService.balance("ACC-A").availableBalanceMinor

        val error = assertThrows(BankingLabDomainException::class.java) {
            ledgerCommandService.withdraw(withdrawal("ACC-A", 999_999_999, "IT-WDR-BAD-001"))
        }

        assertEquals("LEDGER_INSUFFICIENT_AVAILABLE_BALANCE", error.code)
        assertEquals(beforeTransactionCount, countTransactions())
        assertEquals(0, ledgerCommandService.countTransactionsByIdempotencyKey("IT-WDR-BAD-001"))
        assertEquals(beforeBalance, ledgerCommandService.balance("ACC-A").availableBalanceMinor)
    }

    @Test
    fun `over-limit withdrawal is rejected with no ledger mutation idempotency row or usage side effect`() {
        seedAccount("CUS-LIM-A", "ACC-LIM-A", "LAB-300-000001")
        seedTransferLimits("ACC-LIM-A", daily = 100_000, monthly = 1_000_000, single = 5_000)
        ledgerCommandService.deposit(deposit("ACC-LIM-A", 100_000, "IT-LIM-SEED-A"))
        val beforeTransactionCount = countTransactions()
        val beforeBalance = ledgerCommandService.balance("ACC-LIM-A").availableBalanceMinor

        val error = assertThrows(BankingLabDomainException::class.java) {
            ledgerCommandService.withdraw(withdrawal("ACC-LIM-A", 6_000, "IT-LIM-WDR-OVER"))
        }

        assertEquals("LIMIT_EXCEEDED", error.code)
        assertEquals("PER_TRANSACTION", error.details?.get("limitKind"))
        assertEquals("STAFF_TERMINAL", error.details?.get("channel"))
        assertEquals(beforeTransactionCount, countTransactions())
        assertEquals(0, ledgerCommandService.countTransactionsByIdempotencyKey("IT-LIM-WDR-OVER"))
        assertEquals(beforeBalance, ledgerCommandService.balance("ACC-LIM-A").availableBalanceMinor)
        assertEquals(0, countLimitCounters("ACC-LIM-A"))
    }

    @Test
    fun `cumulative daily limit breach across transactions rejects the later posting`() {
        seedAccount("CUS-LIM-B", "ACC-LIM-B", "LAB-300-000002")
        seedAccount("CUS-LIM-C", "ACC-LIM-C", "LAB-300-000003")
        seedTransferLimits("ACC-LIM-B", daily = 15_000, monthly = 100_000, single = 10_000)
        ledgerCommandService.deposit(deposit("ACC-LIM-B", 100_000, "IT-LIM-SEED-B"))
        ledgerCommandService.withdraw(withdrawal("ACC-LIM-B", 6_000, "IT-LIM-WDR-001"))
        ledgerCommandService.internalTransfer(transfer("ACC-LIM-B", "ACC-LIM-C", 7_000, "IT-LIM-TRF-001", channel = "STAFF_TERMINAL"))
        val beforeTransactionCount = countTransactions()

        val error = assertThrows(BankingLabDomainException::class.java) {
            ledgerCommandService.withdraw(withdrawal("ACC-LIM-B", 3_000, "IT-LIM-WDR-DAILY-OVER"))
        }

        assertEquals("LIMIT_EXCEEDED", error.code)
        assertEquals("DAILY", error.details?.get("limitKind"))
        assertEquals(2_000L, error.details?.get("remainingMinor"))
        assertEquals(beforeTransactionCount, countTransactions())
        assertEquals(13_000, limitUsed("ACC-LIM-B", "STAFF_TERMINAL", "DAILY", LocalDate.now()))
        assertEquals(13_000, limitUsed("ACC-LIM-B", "STAFF_TERMINAL", "MONTHLY", LocalDate.now().withDayOfMonth(1)))
    }

    @Test
    fun `daily and monthly counters reset on the next business date and next month`() {
        val janOne = LocalDate.of(2026, 1, 1)
        val janTwo = LocalDate.of(2026, 1, 2)
        val janThree = LocalDate.of(2026, 1, 3)
        val febOne = LocalDate.of(2026, 2, 1)
        seedAccount("CUS-LIM-D", "ACC-LIM-D", "LAB-300-000004")
        seedTransferLimits("ACC-LIM-D", daily = 10_000, monthly = 15_000, single = 10_000)
        ledgerCommandService.deposit(deposit("ACC-LIM-D", 100_000, "IT-LIM-SEED-D"))

        ledgerCommandService.withdraw(withdrawal("ACC-LIM-D", 10_000, "IT-LIM-WDR-JAN-1", janOne))
        val dailyError = assertThrows(BankingLabDomainException::class.java) {
            ledgerCommandService.withdraw(withdrawal("ACC-LIM-D", 1_000, "IT-LIM-WDR-JAN-1-OVER", janOne))
        }
        ledgerCommandService.withdraw(withdrawal("ACC-LIM-D", 5_000, "IT-LIM-WDR-JAN-2", janTwo))
        val monthlyError = assertThrows(BankingLabDomainException::class.java) {
            ledgerCommandService.withdraw(withdrawal("ACC-LIM-D", 1_000, "IT-LIM-WDR-JAN-3-OVER", janThree))
        }
        ledgerCommandService.withdraw(withdrawal("ACC-LIM-D", 10_000, "IT-LIM-WDR-FEB-1", febOne))

        assertEquals("DAILY", dailyError.details?.get("limitKind"))
        assertEquals("MONTHLY", monthlyError.details?.get("limitKind"))
        assertEquals(10_000, limitUsed("ACC-LIM-D", "STAFF_TERMINAL", "DAILY", janOne))
        assertEquals(5_000, limitUsed("ACC-LIM-D", "STAFF_TERMINAL", "DAILY", janTwo))
        assertEquals(15_000, limitUsed("ACC-LIM-D", "STAFF_TERMINAL", "MONTHLY", janOne.withDayOfMonth(1)))
        assertEquals(10_000, limitUsed("ACC-LIM-D", "STAFF_TERMINAL", "MONTHLY", febOne.withDayOfMonth(1)))
    }

    @Test
    fun `concurrent withdrawal burst never exceeds configured daily limit`() {
        seedAccount("CUS-LIM-CON", "ACC-LIM-CON", "LAB-300-000005")
        seedTransferLimits("ACC-LIM-CON", daily = 10_000, monthly = 100_000, single = 1_000)
        ledgerCommandService.deposit(deposit("ACC-LIM-CON", 100_000, "IT-LIM-CON-SEED"))
        val executor = Executors.newFixedThreadPool(16)
        try {
            val futures = (0 until 120).map { index ->
                executor.submit<Result<Any>> {
                    runCatching {
                        ledgerCommandService.withdraw(
                            withdrawal("ACC-LIM-CON", 1_000, "IT-LIM-CON-WDR-${index.toString().padStart(3, '0')}")
                        )
                    }
                }
            }
            val results = futures.map { it.get(30, TimeUnit.SECONDS) }
            val successCount = results.count { it.isSuccess }
            val rejectedCount = results.count { it.isFailure }

            assertEquals(10, successCount)
            assertEquals(110, rejectedCount)
            assertEquals(10_000, limitUsed("ACC-LIM-CON", "STAFF_TERMINAL", "DAILY", LocalDate.now()))
            assertEquals(90_000, ledgerCommandService.balance("ACC-LIM-CON").availableBalanceMinor)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `reversal releases counted transfer usage so a later command can consume the limit`() {
        seedAccount("CUS-LIM-E", "ACC-LIM-E", "LAB-300-000006")
        seedAccount("CUS-LIM-F", "ACC-LIM-F", "LAB-300-000007")
        seedTransferLimits("ACC-LIM-E", daily = 10_000, monthly = 100_000, single = 10_000)
        ledgerCommandService.deposit(deposit("ACC-LIM-E", 100_000, "IT-LIM-SEED-E"))
        val transfer = ledgerCommandService.internalTransfer(transfer("ACC-LIM-E", "ACC-LIM-F", 8_000, "IT-LIM-TRF-REV", channel = "STAFF_TERMINAL"))
        val beforeReversalError = assertThrows(BankingLabDomainException::class.java) {
            ledgerCommandService.withdraw(withdrawal("ACC-LIM-E", 3_000, "IT-LIM-WDR-BEFORE-REV"))
        }

        ledgerCommandService.reverseTransaction(
            ReversalCommand(
                originalTransactionId = transfer.value.id,
                idempotencyKey = "IT-LIM-REV-001",
                requestedBy = "branch01",
                requestedChannel = "STAFF_TERMINAL",
                reason = "Synthetic reversal releases limit usage"
            )
        )
        ledgerCommandService.withdraw(withdrawal("ACC-LIM-E", 3_000, "IT-LIM-WDR-AFTER-REV"))

        assertEquals("LIMIT_EXCEEDED", beforeReversalError.code)
        assertEquals(3_000, limitUsed("ACC-LIM-E", "STAFF_TERMINAL", "DAILY", LocalDate.now()))
        assertEquals(97_000, ledgerCommandService.balance("ACC-LIM-E").availableBalanceMinor)
    }

    @Test
    fun `duplicate idempotency key replays the original ledger result without duplicate postings or outbox events`() {
        seedAccount("CUS-A", "ACC-A", "LAB-100-000001")
        seedAccount("CUS-B", "ACC-B", "LAB-100-000002")
        ledgerCommandService.deposit(deposit("ACC-A", 50_000, "IT-IDEMP-SEED"))

        val first = ledgerCommandService.internalTransfer(transfer("ACC-A", "ACC-B", 10_000, "IT-IDEMP-TRF"))
        val second = ledgerCommandService.internalTransfer(transfer("ACC-A", "ACC-B", 10_000, "IT-IDEMP-TRF"))

        assertEquals(false, first.replayed)
        assertEquals(true, second.replayed)
        assertEquals(first.value.id, second.value.id)
        assertEquals(1, ledgerCommandService.countTransactionsByIdempotencyKey("IT-IDEMP-TRF"))
        assertEquals(1, countOutboxEvents(first.value.id, "LedgerTransactionPosted"))
        assertEquals(40_000, ledgerCommandService.balance("ACC-A").availableBalanceMinor)
        assertEquals(10_000, ledgerCommandService.balance("ACC-B").availableBalanceMinor)
    }

    @Test
    fun `idempotency key conflict is rejected`() {
        seedAccount("CUS-A", "ACC-A", "LAB-100-000001")

        ledgerCommandService.deposit(deposit("ACC-A", 10_000, "IT-IDEMP-CONFLICT"))
        val error = assertThrows(BankingLabDomainException::class.java) {
            ledgerCommandService.deposit(deposit("ACC-A", 11_000, "IT-IDEMP-CONFLICT"))
        }

        assertEquals("IDEMPOTENCY_KEY_CONFLICT", error.code)
    }

    @Test
    fun `reversal references original transaction and restores projected balances`() {
        seedAccount("CUS-A", "ACC-A", "LAB-100-000001")
        seedAccount("CUS-B", "ACC-B", "LAB-100-000002")
        ledgerCommandService.deposit(deposit("ACC-A", 100_000, "IT-REV-SEED"))
        val transfer = ledgerCommandService.internalTransfer(transfer("ACC-A", "ACC-B", 12_000, "IT-REV-TRF"))

        val reversal = ledgerCommandService.reverseTransaction(
            ReversalCommand(
                originalTransactionId = transfer.value.id,
                idempotencyKey = "IT-REV-001",
                requestedBy = "branch01",
                requestedChannel = "STAFF_TERMINAL",
                reason = "Synthetic reversal test"
            )
        )

        assertEquals(transfer.value.id, reversal.value.originalTransactionId)
        assertEquals(100_000, ledgerCommandService.balance("ACC-A").availableBalanceMinor)
        assertEquals(0, ledgerCommandService.balance("ACC-B").availableBalanceMinor)

        val beforeDuplicateReversalCount = countTransactionsByType("REVERSAL")
        val duplicateError = assertThrows(BankingLabDomainException::class.java) {
            ledgerCommandService.reverseTransaction(
                ReversalCommand(
                    originalTransactionId = transfer.value.id,
                    idempotencyKey = "IT-REV-DUP-001",
                    requestedBy = "branch01",
                    requestedChannel = "STAFF_TERMINAL",
                    reason = "Synthetic duplicate reversal test"
                )
            )
        }

        assertEquals("LEDGER_REVERSAL_POLICY_VIOLATION", duplicateError.code)
        assertEquals(beforeDuplicateReversalCount, countTransactionsByType("REVERSAL"))
        assertEquals(0, ledgerCommandService.countTransactionsByIdempotencyKey("IT-REV-DUP-001"))
        assertEquals(100_000, ledgerCommandService.balance("ACC-A").availableBalanceMinor)
        assertEquals(0, ledgerCommandService.balance("ACC-B").availableBalanceMinor)
    }

    @Test
    fun `closed business date rejects direct posting`() {
        seedAccount("CUS-A", "ACC-A", "LAB-100-000001")
        val closedDate = LocalDate.of(2026, 1, 31)
        ledgerCommandService.closeBusinessDay(
            DailyClosingCommand(
                businessDate = closedDate,
                idempotencyKey = "IT-CLOSE-001",
                requestedBy = "ops01"
            )
        )

        val error = assertThrows(BankingLabDomainException::class.java) {
            ledgerCommandService.deposit(deposit("ACC-A", 10_000, "IT-CLOSED-DEP", closedDate))
        }

        assertEquals("LEDGER_CLOSED_DAY_IMMUTABLE", error.code)
        assertEquals(0, countTransactionsByType("DEPOSIT"))
        assertEquals(0, ledgerCommandService.countTransactionsByIdempotencyKey("IT-CLOSED-DEP"))
        assertEquals(0, ledgerCommandService.balance("ACC-A").availableBalanceMinor)
    }

    @Test
    fun `reconciliation adjustment requires maker-checker approval`() {
        seedAccount("CUS-A", "ACC-A", "LAB-100-000001")

        val error = assertThrows(BankingLabDomainException::class.java) {
            ledgerCommandService.adjustment(adjustment("ACC-A", 1_000, "IT-ADJ-NO-APPROVAL", "REC-IT-001", null))
        }

        assertEquals("REQUEST_VALIDATION_FAILED", error.code)
        assertEquals(0, countTransactionsByType("ADJUSTMENT"))
    }

    @Test
    fun `approved reconciliation adjustment posts balanced transaction with audit and outbox evidence`() {
        seedAccount("CUS-A", "ACC-A", "LAB-100-000001")
        seedApprovedApproval(
            approvalId = "APR-IT-ADJ-001",
            businessType = ApprovalBusinessTypes.RECONCILIATION_ADJUSTMENT,
            businessReferenceId = "REC-IT-002",
            requestedBy = "ops01",
            approvedBy = "manager01"
        )

        val result = ledgerCommandService.adjustment(
            adjustment("ACC-A", 1_000, "IT-ADJ-APPROVED", "REC-IT-002", "APR-IT-ADJ-001")
        )

        assertEquals("ADJUSTMENT", result.value.transactionType)
        assertEquals("REC-IT-002", result.value.businessReferenceId)
        assertEquals(1_000, ledgerCommandService.balance("ACC-A").availableBalanceMinor)
        assertEquals(1, countOutboxEvents(result.value.id, "AdjustmentPosted"))
        assertEquals(1, countAuditEvents("COMMAND_EXECUTED", "REC-IT-002"))
    }

    @Test
    fun `repeatable read row locking prevents concurrent overdraft`() {
        assertConcurrentWithdrawalsCannotOverdraw("RR") { command ->
            ledgerCommandService.withdrawRepeatableReadForIsolationTest(command)
        }
    }

    @Test
    fun `serializable row locking prevents concurrent overdraft`() {
        assertConcurrentWithdrawalsCannotOverdraw("SER") { command ->
            ledgerCommandService.withdraw(command)
        }
    }

    private fun assertConcurrentWithdrawalsCannotOverdraw(prefix: String, withdraw: (WithdrawalCommand) -> Any) {
        seedAccount("CUS-CON-$prefix", "ACC-CON-$prefix", "LAB-200-$prefix")
        ledgerCommandService.deposit(deposit("ACC-CON-$prefix", 100_000, "IT-$prefix-SEED"))
        val executor = Executors.newFixedThreadPool(16)
        try {
            val futures = (0 until 120).map { index ->
                executor.submit<Result<Any>> {
                    runCatching {
                        withdraw(withdrawal("ACC-CON-$prefix", 1_000, "IT-$prefix-WDR-${index.toString().padStart(3, '0')}"))
                    }
                }
            }
            val results = futures.map { it.get(30, TimeUnit.SECONDS) }
            val successCount = results.count { it.isSuccess }
            assertEquals(true, successCount in 1..100)
            assertEquals(120 - successCount, results.count { it.isFailure })
            assertEquals(100_000L - (successCount * 1_000L), ledgerCommandService.balance("ACC-CON-$prefix").availableBalanceMinor)
        } finally {
            executor.shutdownNow()
        }
    }

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

    private fun seedTransferLimits(accountId: String, daily: Long, monthly: Long, single: Long, channel: String? = null) {
        val channelColumns = when (channel) {
            "CUSTOMER_WEB" -> """
                , customer_web_daily_transfer_limit_minor = EXCLUDED.customer_web_daily_transfer_limit_minor,
                  customer_web_monthly_transfer_limit_minor = EXCLUDED.customer_web_monthly_transfer_limit_minor,
                  customer_web_single_transfer_limit_minor = EXCLUDED.customer_web_single_transfer_limit_minor
            """.trimIndent()
            "STAFF_TERMINAL" -> """
                , staff_terminal_daily_transfer_limit_minor = EXCLUDED.staff_terminal_daily_transfer_limit_minor,
                  staff_terminal_monthly_transfer_limit_minor = EXCLUDED.staff_terminal_monthly_transfer_limit_minor,
                  staff_terminal_single_transfer_limit_minor = EXCLUDED.staff_terminal_single_transfer_limit_minor
            """.trimIndent()
            else -> ""
        }
        val insertChannelColumns = when (channel) {
            "CUSTOMER_WEB" -> ", customer_web_daily_transfer_limit_minor, customer_web_monthly_transfer_limit_minor, customer_web_single_transfer_limit_minor"
            "STAFF_TERMINAL" -> ", staff_terminal_daily_transfer_limit_minor, staff_terminal_monthly_transfer_limit_minor, staff_terminal_single_transfer_limit_minor"
            else -> ""
        }
        val insertChannelValues = if (channel == "CUSTOMER_WEB" || channel == "STAFF_TERMINAL") ", :daily, :monthly, :single" else ""
        jdbc.update(
            """
            INSERT INTO account_limits (
              account_id, daily_transfer_limit_minor, monthly_transfer_limit_minor, single_transfer_limit_minor
              $insertChannelColumns
            )
            VALUES (:accountId, :daily, :monthly, :single $insertChannelValues)
            ON CONFLICT (account_id) DO UPDATE SET
              daily_transfer_limit_minor = EXCLUDED.daily_transfer_limit_minor,
              monthly_transfer_limit_minor = EXCLUDED.monthly_transfer_limit_minor,
              single_transfer_limit_minor = EXCLUDED.single_transfer_limit_minor,
              updated_at = now()
              $channelColumns
            """.trimIndent(),
            mapOf("accountId" to accountId, "daily" to daily, "monthly" to monthly, "single" to single)
        )
    }

    private fun deposit(accountId: String, amountMinor: Long, key: String, businessDate: LocalDate? = null): DepositCommand =
        DepositCommand(
            accountId = accountId,
            amountMinor = amountMinor,
            idempotencyKey = key,
            requestedBy = "branch01",
            requestedChannel = "STAFF_TERMINAL",
            businessDate = businessDate
        )

    private fun withdrawal(
        accountId: String,
        amountMinor: Long,
        key: String,
        businessDate: LocalDate? = null,
        channel: String = "STAFF_TERMINAL"
    ): WithdrawalCommand =
        WithdrawalCommand(
            accountId = accountId,
            amountMinor = amountMinor,
            idempotencyKey = key,
            requestedBy = "branch01",
            requestedChannel = channel,
            businessDate = businessDate
        )

    private fun transfer(
        fromAccountId: String,
        toAccountId: String,
        amountMinor: Long,
        key: String,
        businessDate: LocalDate? = null,
        channel: String = "CUSTOMER_WEB"
    ): InternalTransferCommand =
        InternalTransferCommand(
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amountMinor = amountMinor,
            idempotencyKey = key,
            requestedBy = "customer01",
            requestedChannel = channel,
            businessDate = businessDate
        )

    private fun adjustment(
        accountId: String,
        amountMinor: Long,
        key: String,
        businessReferenceId: String,
        approvalId: String?
    ): AdjustmentCommand =
        AdjustmentCommand(
            accountId = accountId,
            direction = PostingDirection.CREDIT,
            amountMinor = amountMinor,
            idempotencyKey = key,
            requestedBy = "ops01",
            requestedChannel = "OPS_RECONCILIATION",
            reason = "Synthetic reconciliation adjustment",
            businessReferenceId = businessReferenceId,
            approvalId = approvalId
        )

    private fun seedApprovedApproval(
        approvalId: String,
        businessType: String,
        businessReferenceId: String,
        requestedBy: String,
        approvedBy: String
    ) {
        jdbc.update(
            """
            INSERT INTO operator_approvals (
              approval_id, business_type, business_reference_id, requested_by, request_reason,
              before_snapshot_json, after_snapshot_json, status, approved_by, approved_at
            )
            VALUES (
              :approvalId, :businessType, :businessReferenceId, :requestedBy,
              'Synthetic maker-checker approval',
              CAST(:beforeSnapshot AS jsonb), CAST(:afterSnapshot AS jsonb),
              'APPROVED', :approvedBy, now()
            )
            """.trimIndent(),
            mapOf(
                "approvalId" to approvalId,
                "businessType" to businessType,
                "businessReferenceId" to businessReferenceId,
                "requestedBy" to requestedBy,
                "approvedBy" to approvedBy,
                "beforeSnapshot" to """{"status":"OPEN"}""",
                "afterSnapshot" to """{"status":"ADJUSTED"}"""
            )
        )
    }

    private fun countOutboxEvents(aggregateId: String, eventType: String): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM outbox_events WHERE aggregate_id = :aggregateId AND event_type = :eventType",
            mapOf("aggregateId" to aggregateId, "eventType" to eventType),
            Int::class.java
        ) ?: 0

    private fun countTransactionsByType(transactionType: String): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM ledger_transactions WHERE transaction_type = :transactionType",
            mapOf("transactionType" to transactionType),
            Int::class.java
        ) ?: 0

    private fun countTransactions(): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM ledger_transactions",
            emptyMap<String, Any?>(),
            Int::class.java
        ) ?: 0

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

    private fun countLimitCounters(accountId: String): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM limit_usage_counters WHERE account_id = :accountId",
            mapOf("accountId" to accountId),
            Int::class.java
        ) ?: 0

    private fun limitUsed(accountId: String, channel: String, periodKind: String, businessDate: LocalDate): Long =
        jdbc.queryForObject(
            """
            SELECT COALESCE(sum(used_amount_minor), 0)
            FROM limit_usage_counters
            WHERE account_id = :accountId
              AND channel = :channel
              AND period_kind = :periodKind
              AND business_date = :businessDate
            """.trimIndent(),
            mapOf(
                "accountId" to accountId,
                "channel" to channel,
                "periodKind" to periodKind,
                "businessDate" to businessDate
            ),
            Long::class.java
        ) ?: 0L

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
