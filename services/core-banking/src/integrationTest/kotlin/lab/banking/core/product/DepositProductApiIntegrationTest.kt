package lab.banking.core.product

import java.nio.file.Paths
import java.time.LocalDate
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
class DepositProductApiIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              deposit_rate_change_requests,
              interest_accruals,
              interest_posting_batches,
              account_product_enrollments,
              product_interest_rate_versions,
              deposit_products,
              transaction_correction_requests,
              fee_waiver_requests,
              customer_kyc_review_requests,
              account_limit_change_requests,
              account_hold_requests,
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
        jdbc.update(
            """
            INSERT INTO customers (customer_id, customer_name, customer_grade, risk_grade)
            VALUES
              ('BANK', 'Bank Suspense', 'INTERNAL', 'LOW'),
              ('CUS-PROD-001', 'Synthetic Product Customer', 'STANDARD', 'LOW')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO accounts (account_id, customer_id, account_no, currency, status)
            VALUES
              ('BANK-SUSPENSE', 'BANK', 'LAB-000-000000', 'KRW', 'ACTIVE'),
              ('ACC-PROD-001', 'CUS-PROD-001', 'LAB-030-000001', 'KRW', 'ACTIVE')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO account_balance_projections (
              account_id, currency, ledger_balance_minor, available_balance_minor, hold_amount_minor
            )
            VALUES
              ('BANK-SUSPENSE', 'KRW', 0, 0, 0),
              ('ACC-PROD-001', 'KRW', 10000000, 10000000, 0)
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO deposit_products (
              product_id, product_code, product_name, currency, status, minimum_opening_balance_minor, synthetic_only
            )
            VALUES ('DP-SYN-SAVINGS', 'SYN-SAVINGS-001', 'Synthetic Savings Product', 'KRW', 'ACTIVE', 0, TRUE)
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO product_interest_rate_versions (
              rate_version_id, product_id, annual_rate_bps, effective_from, status, created_by
            )
            VALUES ('RATE-SYN-001', 'DP-SYN-SAVINGS', 365, DATE '2026-01-01', 'ACTIVE', 'test-seed')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO account_product_enrollments (enrollment_id, account_id, product_id, status)
            VALUES ('ENR-SYN-001', 'ACC-PROD-001', 'DP-SYN-SAVINGS', 'ACTIVE')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

    @Test
    fun `deposit product interest accrual posting and rate change preserve ledger controls`() {
        mockMvc.perform(get("/api/products/deposits").queryParam("asOf", "2026-02-01"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].productId").value("DP-SYN-SAVINGS"))
            .andExpect(jsonPath("$.items[0].annualRateBps").value(365))

        mockMvc.perform(
            post("/api/ops/interest-accruals/run")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "accrualDate": "2026-02-01",
                      "requestedBy": "ops01",
                      "actorRole": "OPS_OPERATOR",
                      "reason": "Daily synthetic interest accrual"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].accountId").value("ACC-PROD-001"))
            .andExpect(jsonPath("$.items[0].annualRateBps").value(365))
            .andExpect(jsonPath("$.items[0].accruedInterestMinor").value(1000))
            .andExpect(jsonPath("$.totalInterestMinor").value(1000))

        mockMvc.perform(
            post("/api/staff/products/deposits/DP-SYN-SAVINGS/rate-change-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requestedAnnualRateBps": 730,
                      "effectiveFrom": "2026-02-02",
                      "requestedBy": "branch01",
                      "actorRole": "BRANCH_STAFF",
                      "reason": "Unauthorized branch attempt",
                      "idempotencyKey": "RATE-UNAUTHORIZED"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))
        assertEquals(0, countRows("deposit_rate_change_requests"))

        mockMvc.perform(
            post("/api/staff/products/deposits/DP-SYN-SAVINGS/rate-change-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requestedAnnualRateBps": 730,
                      "effectiveFrom": "2026-02-02",
                      "requestedBy": "opsmanager01",
                      "actorRole": "OPS_MANAGER",
                      "reason": "",
                      "idempotencyKey": "RATE-MISSING-REASON"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("POLICY_REASON_REQUIRED"))
        assertEquals(0, countRows("deposit_rate_change_requests"))

        val approvalId = requestRateChange()

        mockMvc.perform(get("/api/products/deposits/DP-SYN-SAVINGS").queryParam("asOf", "2026-02-03"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.annualRateBps").value(365))

        mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "approvedBy": "opsmanager01",
                      "approvedByRole": "OPS_MANAGER",
                      "screenId": "PRD-102"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("MAKER_CHECKER_SELF_APPROVAL_REJECTED"))
        assertEquals("PENDING_APPROVAL", scalarString("SELECT status FROM deposit_rate_change_requests WHERE approval_id = :approvalId", mapOf("approvalId" to approvalId)))

        mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "approvedBy": "compliance01",
                      "approvedByRole": "COMPLIANCE_MANAGER",
                      "screenId": "PRD-102"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executed").value(true))
            .andExpect(jsonPath("$.depositRateChangeRequest.status").value("APPLIED"))
            .andExpect(jsonPath("$.depositRateChangeRequest.requestedAnnualRateBps").value(730))

        mockMvc.perform(get("/api/products/deposits/DP-SYN-SAVINGS").queryParam("asOf", "2026-02-03"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.annualRateBps").value(730))

        mockMvc.perform(
            post("/api/ops/interest-accruals/run")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "accrualDate": "2026-02-03",
                      "requestedBy": "ops01",
                      "actorRole": "OPS_OPERATOR",
                      "reason": "Daily synthetic interest accrual after rate change"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].annualRateBps").value(730))
            .andExpect(jsonPath("$.items[0].accruedInterestMinor").value(2000))

        mockMvc.perform(
            post("/api/ops/interest-posting-batches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(interestBatchJson("2026-02-03", "INT-BATCH-001"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("POSTED"))
            .andExpect(jsonPath("$.item.totalInterestMinor").value(3000))
            .andExpect(jsonPath("$.item.accountCount").value(1))
            .andExpect(jsonPath("$.ledgerTransaction.transactionType").value("INTEREST_POSTING"))
            .andExpect(jsonPath("$.ledgerTransaction.postings[0].postingType").value("INTEREST"))

        mockMvc.perform(
            post("/api/ops/interest-posting-batches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(interestBatchJson("2026-02-03", "INT-BATCH-001"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replayed").value(true))
            .andExpect(jsonPath("$.item.totalInterestMinor").value(3000))

        assertEquals(1, countRows("ledger_transactions WHERE idempotency_key = 'INT-BATCH-001'"))
        assertEquals(1, countRows("interest_posting_batches WHERE idempotency_key = 'INT-BATCH-001'"))
        assertEquals(2, countRows("ledger_postings WHERE posting_type = 'INTEREST'"))
        assertEquals(10_003_000, balance("ACC-PROD-001"))
        assertEquals(0, unbalancedTransactionCount())

        mockMvc.perform(
            post("/api/ops/interest-accruals/run")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "accrualDate": "2026-02-04",
                      "requestedBy": "ops01",
                      "actorRole": "OPS_OPERATOR",
                      "reason": "Closed day accrual setup"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
        jdbc.update(
            "INSERT INTO daily_closings (business_date, status, closed_by, closed_at) VALUES (:businessDate, 'CLOSED', 'ops01', now())",
            mapOf("businessDate" to LocalDate.of(2026, 2, 4))
        )
        val beforeClosedPostingTransactions = countRows("ledger_transactions")

        mockMvc.perform(
            post("/api/ops/interest-posting-batches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(interestBatchJson("2026-02-04", "INT-BATCH-CLOSED"))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("LEDGER_CLOSED_DAY_IMMUTABLE"))

        assertEquals(beforeClosedPostingTransactions, countRows("ledger_transactions"))
        assertEquals(0, countRows("interest_posting_batches WHERE idempotency_key = 'INT-BATCH-CLOSED'"))
        assertEquals(0, countRows("idempotency_keys WHERE idempotency_key = 'INT-BATCH-CLOSED'"))
        assertEquals("CALCULATED", scalarString("SELECT status FROM interest_accruals WHERE accrual_date = DATE '2026-02-04'", emptyMap()))
    }

    private fun requestRateChange(): String {
        mockMvc.perform(
            post("/api/staff/products/deposits/DP-SYN-SAVINGS/rate-change-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requestedAnnualRateBps": 730,
                      "effectiveFrom": "2026-02-02",
                      "requestedBy": "opsmanager01",
                      "actorRole": "OPS_MANAGER",
                      "reason": "Approved synthetic rate change",
                      "idempotencyKey": "RATE-CHANGE-001"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("PENDING_APPROVAL"))
            .andExpect(jsonPath("$.approval.businessType").value("PRODUCT_PARAMETER_CHANGE"))

        mockMvc.perform(
            post("/api/staff/products/deposits/DP-SYN-SAVINGS/rate-change-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requestedAnnualRateBps": 730,
                      "effectiveFrom": "2026-02-02",
                      "requestedBy": "opsmanager01",
                      "actorRole": "OPS_MANAGER",
                      "reason": "Approved synthetic rate change",
                      "idempotencyKey": "RATE-CHANGE-001"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replayed").value(true))

        assertEquals(1, countRows("deposit_rate_change_requests WHERE idempotency_key = 'RATE-CHANGE-001'"))
        return scalarString(
            "SELECT approval_id FROM deposit_rate_change_requests WHERE idempotency_key = 'RATE-CHANGE-001'",
            emptyMap()
        )
    }

    private fun interestBatchJson(businessDate: String, idempotencyKey: String): String =
        """
        {
          "businessDate": "$businessDate",
          "requestedBy": "ops01",
          "actorRole": "OPS_OPERATOR",
          "reason": "Synthetic interest posting batch",
          "idempotencyKey": "$idempotencyKey"
        }
        """.trimIndent()

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $tableExpression", emptyMap<String, Any?>(), Int::class.java) ?: 0

    private fun scalarString(sql: String, params: Map<String, Any?>): String =
        jdbc.queryForObject(sql, params, String::class.java) ?: error("no scalar result")

    private fun balance(accountId: String): Long =
        jdbc.queryForObject(
            "SELECT ledger_balance_minor FROM account_balance_projections WHERE account_id = :accountId",
            mapOf("accountId" to accountId),
            Long::class.java
        ) ?: 0L

    private fun unbalancedTransactionCount(): Int =
        jdbc.queryForObject(
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

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("banking_lab_product_test")
            .withUsername("banking_lab")
            .withPassword("banking_lab")
            .withReuse(false)

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.locations") {
                "filesystem:${Paths.get("..", "..", "db", "migrations").toAbsolutePath().normalize()}"
            }
        }
    }
}
