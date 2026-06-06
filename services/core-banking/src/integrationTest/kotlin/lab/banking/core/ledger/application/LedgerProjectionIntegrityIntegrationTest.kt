package lab.banking.core.ledger.application

import java.nio.file.Paths
import lab.banking.core.common.BankingLabDomainException
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
class LedgerProjectionIntegrityIntegrationTest {
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
    fun `drift detector records no items for healthy projection and exact expected actual values for drift`() {
        seedAccount("CUS-LPI-A", "ACC-LPI-A", "LAB-LPI-000001")
        ledgerCommandService.deposit(deposit("ACC-LPI-A", 100_000, "LPI-DEP-001"))

        val healthy = projectionIntegrityService.startDriftRun(
            driftCommand("ACC-LPI-A", "LPI-DRIFT-HEALTHY")
        )

        assertEquals(false, healthy.replayed)
        assertEquals("COMPLETED", healthy.item.status)
        assertEquals(0, healthy.item.driftItemCount)
        assertEquals(emptyList<LedgerProjectionDriftItemDto>(), healthy.items)

        jdbc.update(
            """
            UPDATE account_balance_projections
            SET ledger_balance_minor = 90_000,
                available_balance_minor = 90_000,
                updated_at = now()
            WHERE account_id = 'ACC-LPI-A'
              AND currency = 'KRW'
            """.trimIndent(),
            emptyMap<String, Any?>()
        )

        val drifted = projectionIntegrityService.startDriftRun(
            driftCommand("ACC-LPI-A", "LPI-DRIFT-CORRUPT")
        )
        val item = drifted.items.single()

        assertEquals("COMPLETED", drifted.item.status)
        assertEquals(1, drifted.item.driftItemCount)
        assertEquals("ACC-LPI-A", item.accountId)
        assertEquals("KRW", item.currency)
        assertEquals(100_000, item.expectedLedgerBalanceMinor)
        assertEquals(90_000, item.actualLedgerBalanceMinor)
        assertEquals(100_000, item.expectedAvailableBalanceMinor)
        assertEquals(90_000, item.actualAvailableBalanceMinor)
        assertEquals(10_000, item.driftAmountMinor)
        assertEquals("DRIFT_DETECTED", item.status)
        assertEquals(1, item.sourcePostingCount)

        val replay = projectionIntegrityService.startDriftRun(
            driftCommand("ACC-LPI-A", "LPI-DRIFT-CORRUPT")
        )
        assertEquals(true, replay.replayed)
        assertEquals(drifted.item.runId, replay.item.runId)
        assertEquals(2, countRows("ledger_projection_drift_runs"))
        assertEquals(1, countRows("ledger_projection_drift_items"))
    }

    @Test
    fun `projection drift check is reason-required and idempotency conflicts are rejected`() {
        seedAccount("CUS-LPI-B", "ACC-LPI-B", "LAB-LPI-000002")
        ledgerCommandService.deposit(deposit("ACC-LPI-B", 50_000, "LPI-DEP-002"))

        val reasonError = assertThrows(BankingLabDomainException::class.java) {
            projectionIntegrityService.startDriftRun(
                LedgerProjectionDriftRunCommand(
                    requestedBy = "ops01",
                    requestedByRole = "OPS_OPERATOR",
                    reason = "",
                    idempotencyKey = "LPI-DRIFT-REASON",
                    accountId = "ACC-LPI-B"
                )
            )
        }
        assertEquals("POLICY_REASON_REQUIRED", reasonError.code)

        projectionIntegrityService.startDriftRun(driftCommand("ACC-LPI-B", "LPI-DRIFT-CONFLICT"))
        val conflict = assertThrows(BankingLabDomainException::class.java) {
            projectionIntegrityService.startDriftRun(
                driftCommand("ACC-LPI-B", "LPI-DRIFT-CONFLICT").copy(reason = "Different synthetic reason")
            )
        }
        assertEquals("IDEMPOTENCY_KEY_CONFLICT", conflict.code)
    }

    private fun driftCommand(accountId: String, idempotencyKey: String): LedgerProjectionDriftRunCommand =
        LedgerProjectionDriftRunCommand(
            requestedBy = "ops01",
            requestedByRole = "OPS_OPERATOR",
            reason = "Synthetic projection drift verification",
            idempotencyKey = idempotencyKey,
            accountId = accountId,
            currency = "KRW"
        )

    private fun deposit(accountId: String, amountMinor: Long, key: String): DepositCommand =
        DepositCommand(
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
