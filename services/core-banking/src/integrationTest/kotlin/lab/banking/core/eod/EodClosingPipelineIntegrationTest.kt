package lab.banking.core.eod

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Paths
import java.time.LocalDate
import lab.banking.core.approval.ApprovalBusinessTypes
import lab.banking.core.approval.ApproveApprovalCommand
import lab.banking.core.approval.PersistentApprovalService
import lab.banking.core.approval.SubmitApprovalCommand
import lab.banking.core.product.DepositProductService
import lab.banking.core.product.InterestAccrualRunCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
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
class EodClosingPipelineIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @Autowired
    lateinit var approvals: PersistentApprovalService

    @Autowired
    lateinit var depositProductService: DepositProductService

    @Autowired
    lateinit var eodClosingService: EodClosingService

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              eod_closing_steps,
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
              reconciliation_adjustment_requests,
              reconciliation_items,
              transaction_correction_requests,
              fee_waiver_requests,
              customer_kyc_review_requests,
              account_limit_change_requests,
              account_hold_requests,
              masking_access_logs,
              screen_access_logs,
              outbox_events,
              inbox_events,
              idempotency_keys,
              ledger_postings,
              ledger_transactions,
              daily_closings,
              account_balance_projections,
              limit_usage_counters,
              account_holds,
              account_limits,
              operator_approvals,
              audit_events,
              accounts,
              customer_kyc_profiles,
              customers
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
        seedSyntheticEodFixtures()
    }

    @Test
    fun `OPS-101 EOD pipeline closes through maker checker and preserves ledger invariants`() {
        val businessDate = LocalDate.of(2026, 2, 3)

        mockMvc.perform(get("/api/ops/eod/$businessDate"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.steps.length()").value(5))
            .andExpect(jsonPath("$.steps[0].step").value("INTEREST_ACCRUAL"))

        mockMvc.perform(
            post("/api/ops/eod/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "businessDate": "$businessDate",
                      "idempotencyKey": "EOD-PIPE-001",
                      "requestedBy": "ops01",
                      "requestedByRole": "OPS_OPERATOR",
                      "reason": ""
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("POLICY_REASON_REQUIRED"))
        assertEquals(0, countRows("operator_approvals WHERE business_type = 'EOD_CLOSING'"))

        val requestResponse = mockMvc.perform(
            post("/api/ops/eod/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eodRequestJson(businessDate))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.approval.businessType").value("EOD_CLOSING"))
            .andExpect(jsonPath("$.approval.status").value("PENDING"))
            .andExpect(jsonPath("$.monitor.status").value("OPEN"))
            .andReturn()

        val approvalId = objectMapper.readTree(requestResponse.response.contentAsString)
            .path("approval")
            .path("approvalId")
            .asText()
        assertTrue(approvalId.startsWith("APR-"))
        assertEquals(1, countRows("operator_approvals WHERE business_type = 'EOD_CLOSING'"))

        mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"approvedBy":"ops01","approvedByRole":"OPS_MANAGER","screenId":"OPS-101"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("MAKER_CHECKER_SELF_APPROVAL_REJECTED"))
        assertEquals(0, countRows("daily_closings WHERE business_date = DATE '$businessDate' AND status = 'CLOSED'"))
        assertEquals("PENDING", scalarString("SELECT status FROM operator_approvals WHERE approval_id = :approvalId", mapOf("approvalId" to approvalId)))

        mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"approvedBy":"opsmanager01","approvedByRole":"OPS_MANAGER","screenId":"OPS-101"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executed").value(true))
            .andExpect(jsonPath("$.eodClosing.status").value("CLOSED"))
            .andExpect(jsonPath("$.eodClosing.dailyClosingStatus").value("CLOSED"))
            .andExpect(jsonPath("$.eodClosing.ledgerTotalHash").exists())
            .andExpect(jsonPath("$.eodClosing.steps.length()").value(5))
            .andExpect(jsonPath("$.eodClosing.steps[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$.eodClosing.steps[1].status").value("COMPLETED"))
            .andExpect(jsonPath("$.eodClosing.steps[2].status").value("COMPLETED"))
            .andExpect(jsonPath("$.eodClosing.steps[3].status").value("COMPLETED"))
            .andExpect(jsonPath("$.eodClosing.steps[4].status").value("COMPLETED"))

        mockMvc.perform(get("/api/ops/eod/$businessDate"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CLOSED"))
            .andExpect(jsonPath("$.dailyClosingStatus").value("CLOSED"))
            .andExpect(jsonPath("$.ledgerTotalHash").exists())
            .andExpect(jsonPath("$.steps.length()").value(5))

        mockMvc.perform(
            post("/api/ops/eod/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eodRequestJson(businessDate))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replayed").value(true))
            .andExpect(jsonPath("$.monitor.status").value("CLOSED"))
            .andExpect(jsonPath("$.approval.approvalId").value(approvalId))

        val beforeClosedPostingTransactions = countRows("ledger_transactions")
        mockMvc.perform(
            post("/api/ledger/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "fromAccountId": "ACC-EOD-001",
                      "toAccountId": "ACC-EOD-002",
                      "amountMinor": 100,
                      "idempotencyKey": "EOD-CLOSED-POSTING",
                      "businessDate": "$businessDate",
                      "requestedBy": "ops01",
                      "requestedChannel": "OPS_CONSOLE",
                      "reason": "Should fail after EOD close"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("LEDGER_CLOSED_DAY_IMMUTABLE"))
        assertEquals(beforeClosedPostingTransactions, countRows("ledger_transactions"))
        assertEquals(0, countRows("idempotency_keys WHERE idempotency_key = 'EOD-CLOSED-POSTING'"))

        assertEquals(1, countRows("daily_closings WHERE business_date = DATE '$businessDate' AND status = 'CLOSED'"))
        assertEquals(1, countRows("interest_posting_batches WHERE idempotency_key = 'EOD-PIPE-001-INTEREST'"))
        assertEquals(1, countRows("fee_posting_batches WHERE idempotency_key = 'EOD-PIPE-001-FEE'"))
        assertEquals(1, countRows("idempotency_keys WHERE idempotency_key = 'EOD-PIPE-001'"))
        assertEquals(5, countRows("outbox_events WHERE aggregate_type = 'EndOfDayClosing'"))
        assertEquals(5, countRows("audit_events WHERE screen_id = 'OPS-101' AND event_type LIKE 'EOD_STEP_%'"))
        assertEquals(0, unbalancedTransactionCount())
        assertProjectionMatchesPostings("ACC-EOD-001")
        assertProjectionMatchesPostings("BANK-SUSPENSE")
        assertTrue(availableBalance("ACC-EOD-001") >= 0)
        assertNotNull(scalarString("SELECT ledger_total_hash FROM daily_closings WHERE business_date = DATE '$businessDate'", emptyMap()))
    }

    @Test
    fun `approved EOD pipeline resumes after a committed first step`() {
        val businessDate = LocalDate.of(2026, 2, 4)
        eodClosingService.monitor(businessDate)
        val approved = approvals.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.EOD_CLOSING,
                businessReferenceId = "EOD-$businessDate",
                requestedBy = "ops01",
                requestedByRole = "OPS_OPERATOR",
                requestReason = "Synthetic EOD close restart drill",
                beforeSnapshot = mapOf("businessDate" to businessDate.toString(), "status" to "OPEN", "syntheticOnly" to true),
                afterSnapshot = mapOf(
                    "businessDate" to businessDate.toString(),
                    "idempotencyKey" to "EOD-PIPE-RESTART-001",
                    "feePolicyId" to "FEE-EOD-MONTHLY",
                    "externalMode" to "MATCHED",
                    "requestedBy" to "ops01",
                    "requestedByRole" to "OPS_OPERATOR",
                    "reason" to "Synthetic EOD close restart drill",
                    "syntheticOnly" to true
                ),
                screenId = "OPS-101"
            )
        ).let {
            approvals.approve(
                it.approvalId,
                ApproveApprovalCommand(
                    approvedBy = "opsmanager01",
                    approvedByRole = "OPS_MANAGER",
                    screenId = "OPS-101"
                )
            )
        }

        depositProductService.runInterestAccrual(
            InterestAccrualRunCommand(
                accrualDate = businessDate,
                requestedBy = "ops01",
                actorRole = "OPS_OPERATOR",
                reason = "Synthetic first EOD step before worker restart"
            )
        )
        jdbc.update(
            """
            UPDATE eod_closing_steps
            SET status = 'COMPLETED',
                started_at = COALESCE(started_at, now()),
                finished_at = now(),
                result_json = '{"simulatedRestartAfterStep":true,"syntheticOnly":true}'::jsonb
            WHERE business_date = :businessDate
              AND step = 'INTEREST_ACCRUAL'
            """.trimIndent(),
            mapOf("businessDate" to businessDate)
        )

        val execution = eodClosingService.applyApprovedClose(approved)

        assertEquals("CLOSED", execution.monitor.status)
        assertEquals(5, execution.monitor.steps.size)
        assertEquals("COMPLETED", execution.monitor.steps.first { it.step == EodClosingStep.INTEREST_ACCRUAL }.status)
        assertEquals(1, countRows("interest_accruals WHERE accrual_date = DATE '$businessDate'"))
        assertEquals(1, countRows("interest_posting_batches WHERE idempotency_key = 'EOD-PIPE-RESTART-001-INTEREST'"))
        assertEquals(1, countRows("fee_posting_batches WHERE idempotency_key = 'EOD-PIPE-RESTART-001-FEE'"))
        assertEquals(1, countRows("daily_closings WHERE business_date = DATE '$businessDate' AND status = 'CLOSED'"))
        assertEquals(0, unbalancedTransactionCount())
        assertProjectionMatchesPostings("ACC-EOD-001")
        assertProjectionMatchesPostings("BANK-SUSPENSE")
    }

    private fun seedSyntheticEodFixtures() {
        jdbc.update(
            """
            INSERT INTO customers (customer_id, customer_name, customer_grade, risk_grade)
            VALUES
              ('BANK', 'Synthetic Bank Suspense', 'INTERNAL', 'LOW'),
              ('CUS-EOD-001', 'Synthetic EOD Customer One', 'STANDARD', 'LOW'),
              ('CUS-EOD-002', 'Synthetic EOD Customer Two', 'STANDARD', 'LOW')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO accounts (account_id, customer_id, account_no, currency, status)
            VALUES
              ('BANK-SUSPENSE', 'BANK', 'LAB-000-000000', 'KRW', 'ACTIVE'),
              ('ACC-EOD-001', 'CUS-EOD-001', 'LAB-050-000001', 'KRW', 'ACTIVE'),
              ('ACC-EOD-002', 'CUS-EOD-002', 'LAB-050-000002', 'KRW', 'ACTIVE')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO account_limits (
              account_id, daily_transfer_limit_minor, single_transfer_limit_minor, monthly_transfer_limit_minor
            )
            VALUES
              ('ACC-EOD-001', 100000000, 100000000, 3000000000),
              ('ACC-EOD-002', 100000000, 100000000, 3000000000)
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO ledger_transactions (
              ledger_transaction_id, transaction_type, business_reference_id, idempotency_key,
              business_date, status, requested_by, requested_channel, posted_at, reason
            )
            VALUES (
              'TX-EOD-OPEN-001', 'SYNTHETIC_OPENING_BALANCE', 'TX-EOD-OPEN-001', 'SEED-TX-EOD-OPEN-001',
              DATE '2026-02-01', 'POSTED', 'SEED', 'SYNTHETIC_DATA_GENERATOR', now(), 'Synthetic opening balance'
            )
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO ledger_postings (
              ledger_posting_id, ledger_transaction_id, account_id, currency, direction, amount_minor, posting_type
            )
            VALUES
              ('LP-EOD-OPEN-D', 'TX-EOD-OPEN-001', 'BANK-SUSPENSE', 'KRW', 'DEBIT', 10000000, 'OPENING'),
              ('LP-EOD-OPEN-C', 'TX-EOD-OPEN-001', 'ACC-EOD-001', 'KRW', 'CREDIT', 10000000, 'OPENING')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO account_balance_projections (
              account_id, currency, ledger_balance_minor, available_balance_minor, hold_amount_minor
            )
            VALUES
              ('BANK-SUSPENSE', 'KRW', -10000000, -10000000, 0),
              ('ACC-EOD-001', 'KRW', 10000000, 10000000, 0),
              ('ACC-EOD-002', 'KRW', 0, 0, 0)
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO deposit_products (
              product_id, product_code, product_name, currency, status, minimum_opening_balance_minor, synthetic_only
            )
            VALUES ('DP-EOD-SAVINGS', 'SYN-EOD-SAVINGS-001', 'Synthetic EOD Savings Product', 'KRW', 'ACTIVE', 0, TRUE)
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO product_interest_rate_versions (
              rate_version_id, product_id, annual_rate_bps, effective_from, status, created_by
            )
            VALUES ('RATE-EOD-001', 'DP-EOD-SAVINGS', 365, DATE '2026-01-01', 'ACTIVE', 'test-seed')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO account_product_enrollments (enrollment_id, account_id, product_id, status)
            VALUES ('ENR-EOD-001', 'ACC-EOD-001', 'DP-EOD-SAVINGS', 'ACTIVE')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO fee_policies (
              policy_id, fee_code, fee_name, product_id, currency, status, waiver_eligible, synthetic_only
            )
            VALUES (
              'FEE-EOD-MONTHLY', 'EOD_MONTHLY_SERVICE_FEE', 'Synthetic EOD Monthly Service Fee',
              'DP-EOD-SAVINGS', 'KRW', 'ACTIVE', TRUE, TRUE
            )
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO fee_policy_versions (
              fee_policy_version_id, policy_id, amount_minor, effective_from, status, created_by
            )
            VALUES ('FVER-EOD-001', 'FEE-EOD-MONTHLY', 500, DATE '2026-01-01', 'ACTIVE', 'test-seed')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

    private fun eodRequestJson(businessDate: LocalDate): String =
        """
        {
          "businessDate": "$businessDate",
          "idempotencyKey": "EOD-PIPE-001",
          "requestedBy": "ops01",
          "requestedByRole": "OPS_OPERATOR",
          "reason": "Synthetic EOD close with interest fee and reconciliation",
          "feePolicyId": "FEE-EOD-MONTHLY",
          "externalMode": "MATCHED"
        }
        """.trimIndent()

    private fun assertProjectionMatchesPostings(accountId: String) {
        assertEquals(postingSum(accountId), ledgerBalance(accountId), "projection must equal signed ledger postings for $accountId")
    }

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $tableExpression", emptyMap<String, Any?>(), Int::class.java) ?: 0

    private fun scalarString(sql: String, params: Map<String, Any?>): String =
        jdbc.queryForObject(sql, params, String::class.java) ?: error("no scalar result")

    private fun ledgerBalance(accountId: String): Long =
        jdbc.queryForObject(
            "SELECT ledger_balance_minor FROM account_balance_projections WHERE account_id = :accountId",
            mapOf("accountId" to accountId),
            Long::class.java
        ) ?: 0L

    private fun availableBalance(accountId: String): Long =
        jdbc.queryForObject(
            "SELECT available_balance_minor FROM account_balance_projections WHERE account_id = :accountId",
            mapOf("accountId" to accountId),
            Long::class.java
        ) ?: 0L

    private fun postingSum(accountId: String): Long =
        jdbc.queryForObject(
            """
            SELECT COALESCE(SUM(CASE WHEN direction = 'DEBIT' THEN -amount_minor ELSE amount_minor END), 0)
            FROM ledger_postings
            WHERE account_id = :accountId
            """.trimIndent(),
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
            .withDatabaseName("banking_lab_eod_test")
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
                val userDir = Paths.get(System.getProperty("user.dir"))
                listOf(
                    "filesystem:${userDir.resolve("db/migrations").normalize()}",
                    "filesystem:${userDir.resolve("../../db/migrations").normalize()}"
                ).joinToString(",")
            }
        }
    }
}
