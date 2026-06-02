package lab.banking.core.ledger.application

import java.time.LocalDate
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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

        ledgerCommandService.deposit(deposit("ACC-A", 100_000, "IT-DEP-A"))
        val withdrawal = ledgerCommandService.withdraw(withdrawal("ACC-A", 20_000, "IT-WDR-A"))
        val transfer = ledgerCommandService.internalTransfer(transfer("ACC-A", "ACC-B", 30_000, "IT-TRF-A"))

        assertEquals("WITHDRAWAL", withdrawal.value.transactionType)
        assertEquals("INTERNAL_TRANSFER", transfer.value.transactionType)
        assertEquals(50_000, ledgerCommandService.balance("ACC-A").availableBalanceMinor)
        assertEquals(30_000, ledgerCommandService.balance("ACC-B").availableBalanceMinor)
    }

    @Test
    fun `duplicate idempotency key replays the original ledger result without duplicate postings or outbox events`() {
        seedAccount("CUS-A", "ACC-A", "LAB-100-000001")

        val first = ledgerCommandService.deposit(deposit("ACC-A", 10_000, "IT-IDEMP-DEP"))
        val second = ledgerCommandService.deposit(deposit("ACC-A", 10_000, "IT-IDEMP-DEP"))

        assertEquals(false, first.replayed)
        assertEquals(true, second.replayed)
        assertEquals(first.value.id, second.value.id)
        assertEquals(1, ledgerCommandService.countTransactionsByIdempotencyKey("IT-IDEMP-DEP"))
        assertEquals(1, countOutboxEvents(first.value.id, "LedgerTransactionPosted"))
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

    private fun deposit(accountId: String, amountMinor: Long, key: String, businessDate: LocalDate? = null): DepositCommand =
        DepositCommand(
            accountId = accountId,
            amountMinor = amountMinor,
            idempotencyKey = key,
            requestedBy = "branch01",
            requestedChannel = "STAFF_TERMINAL",
            businessDate = businessDate
        )

    private fun withdrawal(accountId: String, amountMinor: Long, key: String): WithdrawalCommand =
        WithdrawalCommand(
            accountId = accountId,
            amountMinor = amountMinor,
            idempotencyKey = key,
            requestedBy = "branch01",
            requestedChannel = "STAFF_TERMINAL"
        )

    private fun transfer(fromAccountId: String, toAccountId: String, amountMinor: Long, key: String): InternalTransferCommand =
        InternalTransferCommand(
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amountMinor = amountMinor,
            idempotencyKey = key,
            requestedBy = "customer01",
            requestedChannel = "CUSTOMER_WEB"
        )

    private fun countOutboxEvents(aggregateId: String, eventType: String): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM outbox_events WHERE aggregate_id = :aggregateId AND event_type = :eventType",
            mapOf("aggregateId" to aggregateId, "eventType" to eventType),
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
