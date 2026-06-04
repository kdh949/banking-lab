package lab.banking.core.resilience

import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import lab.banking.core.CoreBankingApplication
import lab.banking.core.ledger.application.DepositCommand
import lab.banking.core.ledger.application.LedgerCommandService
import lab.banking.core.ledger.application.WithdrawalCommand
import lab.banking.core.ledger.domain.LedgerCommandResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class MultiInstanceLedgerHaDrIntegrationTest {
    private lateinit var instanceA: ConfigurableApplicationContext
    private lateinit var instanceB: ConfigurableApplicationContext
    private lateinit var ledgerA: LedgerCommandService
    private lateinit var ledgerB: LedgerCommandService
    private lateinit var jdbc: NamedParameterJdbcTemplate

    @BeforeEach
    fun startIndependentInstances() {
        instanceA = startInstance("h4-core-a")
        instanceB = startInstance("h4-core-b")
        ledgerA = instanceA.getBean(LedgerCommandService::class.java)
        ledgerB = instanceB.getBean(LedgerCommandService::class.java)
        jdbc = instanceA.getBean(NamedParameterJdbcTemplate::class.java)
        resetDatabase()
    }

    @AfterEach
    fun stopIndependentInstances() {
        if (::instanceB.isInitialized) {
            instanceB.close()
        }
        if (::instanceA.isInitialized) {
            instanceA.close()
        }
    }

    @Test
    fun `serializable withdrawals across independent Spring instances cannot double spend`() {
        seedAccount("CUS-H4-001", "ACC-H4-FROM", "LAB-H4-000001")
        seedTransferLimits("ACC-H4-FROM", daily = 10_000, monthly = 100_000, single = 10_000)
        ledgerA.deposit(deposit("ACC-H4-FROM", 1_000, "H4-SEED-DOUBLE-SPEND"))

        val results = runConcurrently(
            { ledgerA.withdraw(withdrawal("ACC-H4-FROM", 800, "H4-WDR-A")) },
            { ledgerB.withdraw(withdrawal("ACC-H4-FROM", 800, "H4-WDR-B")) }
        )

        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, results.count { it.isFailure })
        assertEquals(1, countRows("ledger_transactions WHERE transaction_type = 'WITHDRAWAL'"))
        assertEquals(200, ledgerB.balance("ACC-H4-FROM").availableBalanceMinor)
        assertNoUnbalancedTransactions()
    }

    @Test
    fun `same idempotency key across independent Spring instances creates one ledger result`() {
        seedAccount("CUS-H4-002", "ACC-H4-IDEMP", "LAB-H4-000002")
        seedTransferLimits("ACC-H4-IDEMP", daily = 10_000, monthly = 100_000, single = 10_000)
        ledgerA.deposit(deposit("ACC-H4-IDEMP", 1_000, "H4-SEED-IDEMPOTENCY"))

        val results = runConcurrently(
            { ledgerA.withdraw(withdrawal("ACC-H4-IDEMP", 400, "H4-SAME-IDEMPOTENCY")) },
            { ledgerB.withdraw(withdrawal("ACC-H4-IDEMP", 400, "H4-SAME-IDEMPOTENCY")) }
        )

        val successes = results.map { it.getOrThrow() }
        assertEquals(2, successes.size)
        assertEquals(1, successes.count { it.replayed })
        assertEquals(successes.first().value.id, successes.last().value.id)
        assertEquals(1, countRows("ledger_transactions WHERE idempotency_key = 'H4-SAME-IDEMPOTENCY'"))
        assertEquals(600, ledgerB.balance("ACC-H4-IDEMP").availableBalanceMinor)
        assertNoUnbalancedTransactions()
    }

    private fun startInstance(instanceName: String): ConfigurableApplicationContext {
        val userDir = Paths.get(System.getProperty("user.dir"))
        val flywayLocations = listOf(
            "filesystem:${userDir.resolve("db/migrations").normalize()}",
            "filesystem:${userDir.resolve("../../db/migrations").normalize()}"
        ).joinToString(",")
        return SpringApplicationBuilder(CoreBankingApplication::class.java)
            .web(WebApplicationType.NONE)
            .properties(
                mapOf(
                    "spring.application.name" to instanceName,
                    "spring.datasource.url" to postgres.jdbcUrl,
                    "spring.datasource.username" to postgres.username,
                    "spring.datasource.password" to postgres.password,
                    "spring.flyway.locations" to flywayLocations,
                    "banking-lab.security.enabled" to "false",
                    "banking-lab.synthetic-only" to "true",
                    "banking-lab.synthetic-seed.enabled" to "false",
                    "banking-lab.temporal.worker.enabled" to "false",
                    "banking-lab.outbox.worker.enabled" to "false",
                    "banking-lab.tracing.enabled" to "false",
                    "banking-lab.otel.tracing.export.enabled" to "false"
                )
            )
            .run()
    }

    private fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              ledger_posting_partition_routes,
              ledger_transaction_partition_routes,
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

    private fun seedTransferLimits(accountId: String, daily: Long, monthly: Long, single: Long) {
        jdbc.update(
            """
            INSERT INTO account_limits (
              account_id, daily_transfer_limit_minor, monthly_transfer_limit_minor, single_transfer_limit_minor,
              staff_terminal_daily_transfer_limit_minor, staff_terminal_monthly_transfer_limit_minor, staff_terminal_single_transfer_limit_minor
            )
            VALUES (:accountId, :daily, :monthly, :single, :daily, :monthly, :single)
            """.trimIndent(),
            mapOf("accountId" to accountId, "daily" to daily, "monthly" to monthly, "single" to single)
        )
    }

    private fun deposit(accountId: String, amountMinor: Long, idempotencyKey: String): DepositCommand =
        DepositCommand(
            accountId = accountId,
            amountMinor = amountMinor,
            idempotencyKey = idempotencyKey,
            requestedBy = "h4-ops",
            requestedChannel = "STAFF_TERMINAL",
            reason = "Synthetic H4 HA seed"
        )

    private fun withdrawal(accountId: String, amountMinor: Long, idempotencyKey: String): WithdrawalCommand =
        WithdrawalCommand(
            accountId = accountId,
            amountMinor = amountMinor,
            idempotencyKey = idempotencyKey,
            requestedBy = "h4-ops",
            requestedChannel = "STAFF_TERMINAL",
            reason = "Synthetic H4 HA cross-instance withdrawal"
        )

    private fun runConcurrently(
        first: () -> LedgerCommandResult,
        second: () -> LedgerCommandResult
    ): List<Result<LedgerCommandResult>> {
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val futures = listOf(first, second).map { command ->
                executor.submit<Result<LedgerCommandResult>> {
                    ready.countDown()
                    assertTrue(ready.await(10, TimeUnit.SECONDS))
                    assertTrue(start.await(10, TimeUnit.SECONDS))
                    runCatching { command() }
                }
            }
            start.countDown()
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun assertNoUnbalancedTransactions() {
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

    private fun countRows(fromClause: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $fromClause", emptyMap<String, Any?>(), Int::class.java) ?: 0

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
