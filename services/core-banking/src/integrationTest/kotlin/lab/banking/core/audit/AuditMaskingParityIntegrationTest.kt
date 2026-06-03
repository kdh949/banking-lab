package lab.banking.core.audit

import java.nio.file.Paths
import lab.banking.core.common.BankingLabDomainException
import lab.banking.core.staff.StaffAccessService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
class AuditMaskingParityIntegrationTest {
    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @Autowired
    lateinit var auditEventAppender: AuditEventAppender

    @Autowired
    lateinit var auditEventService: AuditEventService

    @Autowired
    lateinit var staffAccessService: StaffAccessService

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              masking_access_logs,
              screen_access_logs,
              operator_approvals,
              audit_events,
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
        jdbc.update(
            """
            INSERT INTO customers (
              customer_id, customer_name, customer_phone, customer_address, customer_grade, risk_grade
            )
            VALUES
              ('BANK', 'Bank Suspense', NULL, NULL, 'INTERNAL', 'LOW'),
              ('SYN-CUS-001', 'Lab Customer Alpha', '010-0000-1001', 'Seoul Synthetic District', 'STANDARD', 'LOW')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO accounts (account_id, customer_id, account_no, currency, status)
            VALUES
              ('BANK-SUSPENSE', 'BANK', 'LAB-000-000000', 'KRW', 'ACTIVE'),
              ('ACC-SYN-001-001', 'SYN-CUS-001', 'LAB-001-000001', 'KRW', 'ACTIVE')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO account_balance_projections (
              account_id, currency, ledger_balance_minor, available_balance_minor, hold_amount_minor
            )
            VALUES ('ACC-SYN-001-001', 'KRW', 100000000, 100000000, 0)
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

    @Test
    fun `sensitive staff access requires business reason before audit append`() {
        val error = assertThrows(BankingLabDomainException::class.java) {
            staffAccessService.searchCustomers("SYN-CUS-001", reason = null)
        }

        assertEquals("POLICY_REASON_REQUIRED", error.code)
        assertEquals("REASON_REQUIRED", error.policy)
        assertEquals(0, countRows("audit_events WHERE event_type = 'CUSTOMER_SEARCH'"))

        val allowed = staffAccessService.searchCustomers(
            query = "SYN-CUS-001",
            reason = "Synthetic customer service request"
        )

        assertEquals("SYN-CUS-001", allowed.items.single().customerId)
        assertEquals(1, countRows("audit_events WHERE event_type = 'CUSTOMER_SEARCH'"))
    }

    @Test
    fun `audit events are append-only hash chained`() {
        val firstId = auditEventAppender.append(
            eventType = "LOGIN_SUCCESS",
            actorType = "STAFF",
            actorId = "branch01",
            actorRole = "BRANCH_STAFF",
            screenId = null,
            reason = null,
            payload = mapOf("syntheticOnly" to true)
        )
        Thread.sleep(5)
        val secondId = auditEventAppender.append(
            eventType = "CUSTOMER_SEARCH",
            actorType = "STAFF",
            actorId = "branch01",
            actorRole = "BRANCH_STAFF",
            screenId = "CST-001",
            reason = "Synthetic customer service request",
            payload = mapOf("query" to "SYN-CUS-001", "resultCount" to 1, "syntheticOnly" to true)
        )

        val result = auditEventService.list()
        val first = result.items.single { it.auditEventId == firstId }
        val second = result.items.single { it.auditEventId == secondId }

        assertTrue(result.hashChainValid)
        assertNull(first.previousEventHash)
        assertEquals(first.payloadHash, second.previousEventHash)
    }

    @Test
    fun `PII masking hides customer and account details by default`() {
        val customer = staffAccessService.customerDetail(
            customerId = "SYN-CUS-001",
            reason = "Synthetic masking parity check"
        ).item
        val account = staffAccessService.searchAccounts(
            customerId = "SYN-CUS-001",
            accountId = null,
            reason = "Synthetic masked account parity check"
        ).items.single()

        assertEquals("SYN-CUS-001", customer.customerId)
        assertEquals("MASKED", customer.piiExposure)
        assertNull(customer.name)
        assertEquals("010-****-1001", customer.maskedPhone)
        assertEquals("LAB-***-0001", account.maskedAccountNo)
        assertTrue(account.maskedAccountNo.contains("*"))
        assertEquals(1, countRows("masking_access_logs WHERE access_level = 'MASKED'"))
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
