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
class FeePolicyApiIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              fee_policy_change_requests,
              fee_posting_batches,
              fee_policy_versions,
              fee_policies,
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
              ('CUS-FEE-001', 'Synthetic Fee Customer', 'STANDARD', 'LOW')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO accounts (account_id, customer_id, account_no, currency, status)
            VALUES
              ('BANK-SUSPENSE', 'BANK', 'LAB-000-000000', 'KRW', 'ACTIVE'),
              ('ACC-FEE-001', 'CUS-FEE-001', 'LAB-040-000001', 'KRW', 'ACTIVE')
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
              ('ACC-FEE-001', 'KRW', 10000000, 10000000, 0)
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO deposit_products (
              product_id, product_code, product_name, currency, status, minimum_opening_balance_minor, synthetic_only
            )
            VALUES ('DP-FEE-SAVINGS', 'SYN-FEE-SAVINGS-001', 'Synthetic Fee Savings Product', 'KRW', 'ACTIVE', 0, TRUE)
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO product_interest_rate_versions (
              rate_version_id, product_id, annual_rate_bps, effective_from, status, created_by
            )
            VALUES ('RATE-FEE-001', 'DP-FEE-SAVINGS', 100, DATE '2026-01-01', 'ACTIVE', 'test-seed')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO account_product_enrollments (enrollment_id, account_id, product_id, status)
            VALUES ('ENR-FEE-001', 'ACC-FEE-001', 'DP-FEE-SAVINGS', 'ACTIVE')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO fee_policies (
              policy_id, fee_code, fee_name, product_id, currency, status, waiver_eligible, synthetic_only
            )
            VALUES (
              'FEE-SYN-MONTHLY', 'MONTHLY_SERVICE_FEE', 'Synthetic Monthly Service Fee',
              'DP-FEE-SAVINGS', 'KRW', 'ACTIVE', TRUE, TRUE
            )
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO fee_policy_versions (
              fee_policy_version_id, policy_id, amount_minor, effective_from, status, created_by
            )
            VALUES ('FVER-FEE-001', 'FEE-SYN-MONTHLY', 1000, DATE '2026-01-01', 'ACTIVE', 'test-seed')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

    @Test
    fun `fee policy change posting and refund preserve ledger controls`() {
        mockMvc.perform(get("/api/fees/policies").queryParam("asOf", "2026-02-01"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].policyId").value("FEE-SYN-MONTHLY"))
            .andExpect(jsonPath("$.items[0].amountMinor").value(1000))

        mockMvc.perform(get("/api/staff/fees").queryParam("accountId", "ACC-FEE-001"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("POLICY_REASON_REQUIRED"))

        mockMvc.perform(get("/api/staff/fees").queryParam("accountId", "ACC-FEE-001").queryParam("reason", "Synthetic fee inquiry"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].feeCode").value("MONTHLY_SERVICE_FEE"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'FEE_VIEW'"))

        mockMvc.perform(
            post("/api/staff/fee-policies/FEE-SYN-MONTHLY/change-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requestedAmountMinor": 1500,
                      "effectiveFrom": "2026-02-02",
                      "requestedBy": "branch01",
                      "actorRole": "BRANCH_STAFF",
                      "reason": "Unauthorized branch attempt",
                      "idempotencyKey": "FEEPOL-UNAUTHORIZED"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))
        assertEquals(0, countRows("fee_policy_change_requests"))

        mockMvc.perform(
            post("/api/staff/fee-policies/FEE-SYN-MONTHLY/change-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requestedAmountMinor": 1500,
                      "effectiveFrom": "2026-02-02",
                      "requestedBy": "opsmanager01",
                      "actorRole": "OPS_MANAGER",
                      "reason": "",
                      "idempotencyKey": "FEEPOL-MISSING-REASON"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("POLICY_REASON_REQUIRED"))
        assertEquals(0, countRows("fee_policy_change_requests"))

        val approvalId = requestFeePolicyChange()

        mockMvc.perform(get("/api/fees/policies/FEE-SYN-MONTHLY").queryParam("asOf", "2026-02-03"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.amountMinor").value(1000))

        mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "approvedBy": "opsmanager01",
                      "approvedByRole": "OPS_MANAGER",
                      "screenId": "FEE-103"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("MAKER_CHECKER_SELF_APPROVAL_REJECTED"))
        assertEquals("PENDING_APPROVAL", scalarString("SELECT status FROM fee_policy_change_requests WHERE approval_id = :approvalId", mapOf("approvalId" to approvalId)))

        mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "approvedBy": "compliance01",
                      "approvedByRole": "COMPLIANCE_MANAGER",
                      "screenId": "FEE-103"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executed").value(true))
            .andExpect(jsonPath("$.feePolicyChangeRequest.status").value("APPLIED"))
            .andExpect(jsonPath("$.feePolicyChangeRequest.requestedAmountMinor").value(1500))

        mockMvc.perform(get("/api/fees/policies/FEE-SYN-MONTHLY").queryParam("asOf", "2026-02-03"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.amountMinor").value(1500))

        mockMvc.perform(
            post("/api/ops/fee-posting-batches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(feeBatchJson("2026-02-03", "FEE-BATCH-001"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("POSTED"))
            .andExpect(jsonPath("$.item.totalFeeMinor").value(1500))
            .andExpect(jsonPath("$.item.accountCount").value(1))
            .andExpect(jsonPath("$.ledgerTransaction.transactionType").value("FEE_POSTING"))
            .andExpect(jsonPath("$.ledgerTransaction.postings[0].postingType").value("FEE"))

        mockMvc.perform(
            post("/api/ops/fee-posting-batches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(feeBatchJson("2026-02-03", "FEE-BATCH-001"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replayed").value(true))
            .andExpect(jsonPath("$.item.totalFeeMinor").value(1500))

        assertEquals(1, countRows("ledger_transactions WHERE idempotency_key = 'FEE-BATCH-001'"))
        assertEquals(1, countRows("fee_posting_batches WHERE idempotency_key = 'FEE-BATCH-001'"))
        assertEquals(2, countRows("ledger_postings WHERE posting_type = 'FEE'"))
        assertEquals(9_998_500, balance("ACC-FEE-001"))
        assertEquals(0, unbalancedTransactionCount())

        val feeTransactionId = scalarString(
            "SELECT ledger_transaction_id FROM fee_posting_batches WHERE idempotency_key = 'FEE-BATCH-001'",
            emptyMap()
        )
        val feeWaiverApprovalId = requestTargetedFeeWaiver(feeTransactionId)
        mockMvc.perform(
            post("/api/staff/approvals/$feeWaiverApprovalId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "approvedBy": "manager02",
                      "approvedByRole": "BRANCH_MANAGER",
                      "screenId": "FEE-102"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executed").value(true))
            .andExpect(jsonPath("$.feeWaiverRequest.status").value("APPROVED"))
            .andExpect(jsonPath("$.feeWaiverRequest.refundLedgerTransactionId").exists())
            .andExpect(jsonPath("$.ledgerTransaction.value.transactionType").value("REVERSAL"))
            .andExpect(jsonPath("$.ledgerTransaction.value.originalTransactionId").value(feeTransactionId))

        assertEquals(10_000_000, balance("ACC-FEE-001"))
        assertEquals(0, unbalancedTransactionCount())

        jdbc.update(
            "INSERT INTO daily_closings (business_date, status, closed_by, closed_at) VALUES (:businessDate, 'CLOSED', 'ops01', now())",
            mapOf("businessDate" to LocalDate.of(2026, 2, 4))
        )
        val beforeClosedPostingTransactions = countRows("ledger_transactions")

        mockMvc.perform(
            post("/api/ops/fee-posting-batches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(feeBatchJson("2026-02-04", "FEE-BATCH-CLOSED"))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("LEDGER_CLOSED_DAY_IMMUTABLE"))

        assertEquals(beforeClosedPostingTransactions, countRows("ledger_transactions"))
        assertEquals(0, countRows("fee_posting_batches WHERE idempotency_key = 'FEE-BATCH-CLOSED'"))
        assertEquals(0, countRows("idempotency_keys WHERE idempotency_key = 'FEE-BATCH-CLOSED'"))
    }

    private fun requestFeePolicyChange(): String {
        mockMvc.perform(
            post("/api/staff/fee-policies/FEE-SYN-MONTHLY/change-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requestedAmountMinor": 1500,
                      "effectiveFrom": "2026-02-02",
                      "requestedBy": "opsmanager01",
                      "actorRole": "OPS_MANAGER",
                      "reason": "Approved synthetic fee policy amount change",
                      "idempotencyKey": "FEEPOL-CHANGE-001"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("PENDING_APPROVAL"))
            .andExpect(jsonPath("$.approval.businessType").value("FEE_POLICY_PARAMETER_CHANGE"))

        mockMvc.perform(
            post("/api/staff/fee-policies/FEE-SYN-MONTHLY/change-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requestedAmountMinor": 1500,
                      "effectiveFrom": "2026-02-02",
                      "requestedBy": "opsmanager01",
                      "actorRole": "OPS_MANAGER",
                      "reason": "Approved synthetic fee policy amount change",
                      "idempotencyKey": "FEEPOL-CHANGE-001"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replayed").value(true))

        assertEquals(1, countRows("fee_policy_change_requests WHERE idempotency_key = 'FEEPOL-CHANGE-001'"))
        return scalarString(
            "SELECT approval_id FROM fee_policy_change_requests WHERE idempotency_key = 'FEEPOL-CHANGE-001'",
            emptyMap()
        )
    }

    private fun requestTargetedFeeWaiver(feeTransactionId: String): String {
        mockMvc.perform(
            post("/api/staff/accounts/ACC-FEE-001/fee-waiver-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requestedBy": "manager01",
                      "requestedByRole": "BRANCH_MANAGER",
                      "reason": "Approved synthetic fee refund",
                      "reasonCode": "SYSTEM_ERROR",
                      "feeCode": "MONTHLY_SERVICE_FEE",
                      "waivedAmountMinor": 1500,
                      "currency": "KRW",
                      "targetTransactionId": "$feeTransactionId",
                      "description": "Refund posted synthetic fee through reversal",
                      "idempotencyKey": "FEE-WAIVER-REFUND-001"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.status").value("PENDING_APPROVAL"))
            .andExpect(jsonPath("$.approval.businessType").value("FEE_WAIVER"))
        return scalarString(
            "SELECT approval_id FROM fee_waiver_requests WHERE idempotency_key = 'FEE-WAIVER-REFUND-001'",
            emptyMap()
        )
    }

    private fun feeBatchJson(businessDate: String, idempotencyKey: String): String =
        """
        {
          "policyId": "FEE-SYN-MONTHLY",
          "businessDate": "$businessDate",
          "requestedBy": "ops01",
          "actorRole": "OPS_OPERATOR",
          "reason": "Synthetic fee posting batch",
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
            .withDatabaseName("banking_lab_fee_test")
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
