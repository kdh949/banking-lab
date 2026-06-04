package lab.banking.core.ledger.application

import java.nio.file.Paths
import java.time.LocalDate
import lab.banking.core.ledger.domain.BANK_FEE_INCOME_ACCOUNT_ID
import lab.banking.core.ledger.domain.BANK_INTEREST_EXPENSE_ACCOUNT_ID
import lab.banking.core.ledger.domain.BANK_LOAN_INTEREST_INCOME_ACCOUNT_ID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers
class LedgerDatabaseIntegrityIntegrationTest {
    @Autowired
    lateinit var ledgerCommandService: LedgerCommandService

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun resetDatabase() {
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

    @Test
    fun `deferred database trigger rejects direct unbalanced posted transaction at commit`() {
        seedAccount("CUS-H3-001", "ACC-H3-001", "LAB-H3-000001")
        seedAccount("CUS-H3-002", "ACC-H3-002", "LAB-H3-000002")

        val error = assertThrows(Exception::class.java) {
            TransactionTemplate(transactionManager).executeWithoutResult {
                insertDirectTransaction("TX-H3-UNBALANCED", LocalDate.of(2026, 3, 3))
                insertDirectPosting("TX-H3-UNBALANCED-P001", "TX-H3-UNBALANCED", "ACC-H3-001", "DEBIT", 1000)
                insertDirectPosting("TX-H3-UNBALANCED-P002", "TX-H3-UNBALANCED", "ACC-H3-002", "CREDIT", 900)
            }
        }

        assertTrue(rootCauseMessage(error).contains("is not balanced"))
        assertEquals(0, countRows("ledger_transactions WHERE ledger_transaction_id = 'TX-H3-UNBALANCED'"))
    }

    @Test
    fun `deferred database trigger rejects posted transaction with fewer than two postings`() {
        seedAccount("CUS-H3-003", "ACC-H3-003", "LAB-H3-000003")

        val error = assertThrows(Exception::class.java) {
            TransactionTemplate(transactionManager).executeWithoutResult {
                insertDirectTransaction("TX-H3-ONE-POSTING", LocalDate.of(2026, 3, 4))
                insertDirectPosting("TX-H3-ONE-POSTING-P001", "TX-H3-ONE-POSTING", "ACC-H3-003", "DEBIT", 1000)
            }
        }

        assertTrue(rootCauseMessage(error).contains("must contain at least two postings"))
        assertEquals(0, countRows("ledger_transactions WHERE ledger_transaction_id = 'TX-H3-ONE-POSTING'"))
    }

    @Test
    fun `interest fee and loan interest postings route through typed system accounts`() {
        seedAccount("CUS-H3-004", "ACC-H3-004", "LAB-H3-000004")
        ledgerCommandService.deposit(
            DepositCommand(
                accountId = "ACC-H3-004",
                amountMinor = 100_000,
                idempotencyKey = "H3-SYSTEM-SEED",
                requestedBy = "branch01",
                requestedChannel = "STAFF_TERMINAL"
            )
        )

        val interest = ledgerCommandService.interestPosting(
            InterestPostingCommand(
                credits = listOf(InterestPostingCredit("ACC-H3-004", 700)),
                idempotencyKey = "H3-INTEREST-POSTING",
                requestedBy = "ops01",
                requestedChannel = "OPS_CONSOLE",
                reason = "Synthetic H3 interest expense routing"
            )
        )
        val fee = ledgerCommandService.feePosting(
            FeePostingCommand(
                charges = listOf(FeePostingCharge("ACC-H3-004", 300)),
                idempotencyKey = "H3-FEE-POSTING",
                requestedBy = "ops01",
                requestedChannel = "OPS_CONSOLE",
                reason = "Synthetic H3 fee income routing"
            )
        )
        val loanRepayment = ledgerCommandService.repayLoan(
            LoanRepaymentCommand(
                loanId = "LOAN-H3-001",
                depositAccountId = "ACC-H3-004",
                principalMinor = 1000,
                interestMinor = 200,
                idempotencyKey = "H3-LOAN-REPAYMENT",
                requestedBy = "customer01",
                requestedChannel = "CUSTOMER_WEB",
                reason = "Synthetic H3 loan interest income routing"
            )
        )

        assertSystemPosting(
            transactionId = interest.value.id,
            accountId = BANK_INTEREST_EXPENSE_ACCOUNT_ID,
            accountClass = "EXPENSE",
            systemAccountKind = "INTEREST_EXPENSE",
            direction = "DEBIT",
            amountMinor = 700
        )
        assertSystemPosting(
            transactionId = fee.value.id,
            accountId = BANK_FEE_INCOME_ACCOUNT_ID,
            accountClass = "INCOME",
            systemAccountKind = "FEE_INCOME",
            direction = "CREDIT",
            amountMinor = 300
        )
        assertSystemPosting(
            transactionId = loanRepayment.value.id,
            accountId = BANK_LOAN_INTEREST_INCOME_ACCOUNT_ID,
            accountClass = "INCOME",
            systemAccountKind = "LOAN_INTEREST_INCOME",
            direction = "CREDIT",
            amountMinor = 200
        )
    }

    @Test
    fun `ledger partition routing records 2026 business date rows in 2026 child partitions`() {
        seedAccount("CUS-H3-005", "ACC-H3-005", "LAB-H3-000005")

        val result = ledgerCommandService.deposit(
            DepositCommand(
                accountId = "ACC-H3-005",
                amountMinor = 5000,
                idempotencyKey = "H3-PARTITION-2026",
                requestedBy = "branch01",
                requestedChannel = "STAFF_TERMINAL",
                businessDate = LocalDate.of(2026, 2, 15)
            )
        )

        assertEquals(
            "ledger_transaction_partition_routes_2026",
            partitionTable("ledger_transaction_partition_routes", "ledger_transaction_id", result.value.id)
        )
        val postingRouteTables = jdbc.query(
            """
            SELECT tableoid::regclass::text AS partition_table
            FROM ledger_posting_partition_routes
            WHERE ledger_transaction_id = :transactionId
            ORDER BY ledger_posting_id
            """.trimIndent(),
            mapOf("transactionId" to result.value.id)
        ) { rs, _ -> rs.getString("partition_table") }

        assertEquals(2, postingRouteTables.size)
        assertEquals(setOf("ledger_posting_partition_routes_2026"), postingRouteTables.toSet())
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

    private fun insertDirectTransaction(transactionId: String, businessDate: LocalDate) {
        jdbc.update(
            """
            INSERT INTO ledger_transactions (
              ledger_transaction_id, transaction_type, business_reference_id, idempotency_key,
              business_date, status, requested_by, requested_channel, posted_at
            )
            VALUES (
              :transactionId, 'DIRECT_SQL_TEST', :transactionId, :transactionId,
              :businessDate, 'POSTED', 'h3-test', 'DIRECT_SQL', now()
            )
            """.trimIndent(),
            mapOf("transactionId" to transactionId, "businessDate" to businessDate)
        )
    }

    private fun insertDirectPosting(postingId: String, transactionId: String, accountId: String, direction: String, amountMinor: Long) {
        jdbc.update(
            """
            INSERT INTO ledger_postings (
              ledger_posting_id, ledger_transaction_id, account_id, currency, direction, amount_minor, posting_type
            )
            VALUES (
              :postingId, :transactionId, :accountId, 'KRW', :direction, :amountMinor, 'PRINCIPAL'
            )
            """.trimIndent(),
            mapOf(
                "postingId" to postingId,
                "transactionId" to transactionId,
                "accountId" to accountId,
                "direction" to direction,
                "amountMinor" to amountMinor
            )
        )
    }

    private fun assertSystemPosting(
        transactionId: String,
        accountId: String,
        accountClass: String,
        systemAccountKind: String,
        direction: String,
        amountMinor: Long
    ) {
        val count = jdbc.queryForObject(
            """
            SELECT count(*)
            FROM ledger_postings posting
            JOIN accounts account ON account.account_id = posting.account_id
            WHERE posting.ledger_transaction_id = :transactionId
              AND posting.account_id = :accountId
              AND posting.direction = :direction
              AND posting.amount_minor = :amountMinor
              AND account.account_class = :accountClass
              AND account.system_account_kind = :systemAccountKind
              AND account.synthetic_system_account = TRUE
            """.trimIndent(),
            mapOf(
                "transactionId" to transactionId,
                "accountId" to accountId,
                "direction" to direction,
                "amountMinor" to amountMinor,
                "accountClass" to accountClass,
                "systemAccountKind" to systemAccountKind
            ),
            Int::class.java
        ) ?: 0
        assertEquals(1, count)
    }

    private fun partitionTable(tableName: String, idColumn: String, id: String): String =
        jdbc.queryForObject(
            "SELECT tableoid::regclass::text FROM $tableName WHERE $idColumn = :id",
            mapOf("id" to id),
            String::class.java
        ) ?: error("partition route not found for $id")

    private fun countRows(fromClause: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $fromClause", emptyMap<String, Any?>(), Int::class.java) ?: 0

    private fun rootCauseMessage(error: Throwable): String {
        var current = error
        while (current.cause != null) {
            current = current.cause!!
        }
        return current.message ?: error.message ?: ""
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
                val userDir = Paths.get(System.getProperty("user.dir"))
                listOf(
                    "filesystem:${userDir.resolve("db/migrations").normalize()}",
                    "filesystem:${userDir.resolve("../../db/migrations").normalize()}"
                ).joinToString(",")
            }
        }
    }
}
