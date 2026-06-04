package lab.banking.core.loan

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
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

@SpringBootTest(properties = ["banking-lab.security.enabled=true"])
@AutoConfigureMockMvc
@Testcontainers
class LoanDomainIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              loan_interest_accruals,
              loan_payments,
              loan_repayment_schedule,
              loans,
              loan_applications,
              loan_products,
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
        seedSyntheticLoanFixtures()
    }

    @Test
    fun `loan application execution repayment accrual and prepayment post through ledger controls`() {
        mockMvc.perform(
            get("/api/loans/products")
                .header("Authorization", bearer("manager01", listOf("BRANCH_MANAGER")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].productId").value("LOAN-PROD-IT-001"))

        mockMvc.perform(
            post("/api/loans/applications")
                .header("Authorization", bearer("manager01", listOf("BRANCH_MANAGER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(applicationJson(reason = ""))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("POLICY_REASON_REQUIRED"))
        assertEquals(0, countRows("loan_applications"))
        assertEquals(0, countRows("operator_approvals WHERE business_type = 'LOAN_EXECUTION'"))

        val applicationResponse = mockMvc.perform(
            post("/api/loans/applications")
                .header("Authorization", bearer("manager01", listOf("BRANCH_MANAGER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(applicationJson(reason = "Synthetic loan origination integration test"))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.status").value("PENDING_APPROVAL"))
            .andExpect(jsonPath("$.item.underwritingDecision").value("APPROVE"))
            .andExpect(jsonPath("$.approval.businessType").value("LOAN_EXECUTION"))
            .andReturn()

        val applicationId = objectMapper.readTree(applicationResponse.response.contentAsString)
            .path("item")
            .path("applicationId")
            .asText()
        val approvalId = objectMapper.readTree(applicationResponse.response.contentAsString)
            .path("approval")
            .path("approvalId")
            .asText()

        mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .header("Authorization", bearer("manager01", listOf("BRANCH_MANAGER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"approvedBy":"manager01","approvedByRole":"BRANCH_MANAGER","screenId":"LON-102"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("MAKER_CHECKER_SELF_APPROVAL_REJECTED"))
        assertEquals("PENDING_APPROVAL", scalarString("SELECT status FROM loan_applications WHERE application_id = :applicationId", mapOf("applicationId" to applicationId)))
        assertEquals(0, countRows("loans"))

        val executionResponse = mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .header("Authorization", bearer("manager02", listOf("BRANCH_MANAGER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"approvedBy":"manager02","approvedByRole":"BRANCH_MANAGER","screenId":"LON-102"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executed").value(true))
            .andExpect(jsonPath("$.loanExecution.application.status").value("EXECUTED"))
            .andExpect(jsonPath("$.loanExecution.loan.status").value("ACTIVE"))
            .andExpect(jsonPath("$.loanExecution.ledgerTransaction.value.transactionType").value("LOAN_DISBURSEMENT"))
            .andReturn()

        val loanId = objectMapper.readTree(executionResponse.response.contentAsString)
            .path("loanExecution")
            .path("loan")
            .path("loanId")
            .asText()
        val disbursementTransactionId = objectMapper.readTree(executionResponse.response.contentAsString)
            .path("loanExecution")
            .path("ledgerTransaction")
            .path("value")
            .path("id")
            .asText()

        assertEquals(0, signedTransactionSum(disbursementTransactionId))
        assertEquals(2, countRows("ledger_postings WHERE ledger_transaction_id = '$disbursementTransactionId' AND posting_type = 'LOAN_PRINCIPAL'"))
        assertEquals(1, countRows("outbox_events WHERE event_type = 'LoanDisbursed'"))
        assertProjectionMatchesPostings("ACC-LOAN-001")
        assertProjectionMatchesPostings("BANK-LOAN-ASSET")
        assertTrue(availableBalance("ACC-LOAN-001") >= 0)
        assertEquals(1_700_000, availableBalance("ACC-LOAN-001"))

        val principalScheduleSum = scalarLong("SELECT sum(principal_minor) FROM loan_repayment_schedule WHERE loan_id = :loanId", mapOf("loanId" to loanId))
        val interestScheduleSum = scalarLong("SELECT sum(interest_minor) FROM loan_repayment_schedule WHERE loan_id = :loanId", mapOf("loanId" to loanId))
        assertEquals(1_200_000, principalScheduleSum)
        assertTrue(interestScheduleSum > 0)

        mockMvc.perform(
            get("/api/loans/$loanId")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-LOAN-001"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.loanId").value(loanId))
            .andExpect(jsonPath("$.schedule.length()").value(12))

        val nextPrincipal = scalarLong(
            """
            SELECT principal_minor
            FROM loan_repayment_schedule
            WHERE loan_id = :loanId
              AND status = 'PENDING'
            ORDER BY due_date
            LIMIT 1
            """.trimIndent(),
            mapOf("loanId" to loanId)
        )
        val nextInterest = scalarLong(
            """
            SELECT interest_minor
            FROM loan_repayment_schedule
            WHERE loan_id = :loanId
              AND status = 'PENDING'
            ORDER BY due_date
            LIMIT 1
            """.trimIndent(),
            mapOf("loanId" to loanId)
        )
        val repaymentResponse = mockMvc.perform(
            post("/api/loans/$loanId/repayments")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-LOAN-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "principalMinor": $nextPrincipal,
                      "interestMinor": $nextInterest,
                      "idempotencyKey": "LOAN-IT-REPAY-001",
                      "requestedBy": "customer01",
                      "requestedChannel": "CUSTOMER_WEB",
                      "reason": "Synthetic scheduled loan repayment"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.ledgerTransaction.value.transactionType").value("LOAN_REPAYMENT"))
            .andExpect(jsonPath("$.loan.outstandingPrincipalMinor").value(1_100_000))
            .andReturn()
        val repaymentTransactionId = objectMapper.readTree(repaymentResponse.response.contentAsString)
            .path("ledgerTransaction")
            .path("value")
            .path("id")
            .asText()
        assertEquals(0, signedTransactionSum(repaymentTransactionId))
        assertEquals(1, countRows("outbox_events WHERE event_type = 'LoanRepaymentPosted'"))
        assertEquals(1, countRows("loan_payments WHERE idempotency_key = 'LOAN-IT-REPAY-001'"))

        val accrualDate = LocalDate.now().plusMonths(3)
        mockMvc.perform(
            post("/api/loans/$loanId/accruals/run")
                .header("Authorization", bearer("ops01", listOf("OPS_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "accrualDate": "$accrualDate",
                      "requestedBy": "ops01",
                      "actorRole": "OPS_OPERATOR",
                      "reason": "Synthetic overdue loan accrual"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("CALCULATED"))
            .andExpect(jsonPath("$.loan.status").value("OVERDUE"))
        assertTrue(scalarLong("SELECT overdue_days FROM loans WHERE loan_id = :loanId", mapOf("loanId" to loanId)) > 0)

        mockMvc.perform(
            post("/api/loans/$loanId/accruals/run")
                .header("Authorization", bearer("ops01", listOf("OPS_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "accrualDate": "$accrualDate",
                      "requestedBy": "ops01",
                      "actorRole": "OPS_OPERATOR",
                      "reason": "Synthetic overdue loan accrual"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replayed").value(true))
        assertEquals(1, countRows("loan_interest_accruals WHERE loan_id = '$loanId' AND accrual_date = DATE '$accrualDate'"))

        val prepaymentResponse = mockMvc.perform(
            post("/api/loans/$loanId/prepayments")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-LOAN-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "principalMinor": 1100000,
                      "interestMinor": 0,
                      "idempotencyKey": "LOAN-IT-PREPAY-001",
                      "requestedBy": "customer01",
                      "requestedChannel": "CUSTOMER_WEB",
                      "reason": "Synthetic full prepayment"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.ledgerTransaction.value.transactionType").value("LOAN_PREPAYMENT"))
            .andExpect(jsonPath("$.loan.status").value("CLOSED"))
            .andExpect(jsonPath("$.loan.outstandingPrincipalMinor").value(0))
            .andReturn()
        val prepaymentTransactionId = objectMapper.readTree(prepaymentResponse.response.contentAsString)
            .path("ledgerTransaction")
            .path("value")
            .path("id")
            .asText()
        assertEquals(0, signedTransactionSum(prepaymentTransactionId))

        mockMvc.perform(
            post("/api/loans/$loanId/prepayments")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-LOAN-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "principalMinor": 1100000,
                      "interestMinor": 0,
                      "idempotencyKey": "LOAN-IT-PREPAY-001",
                      "requestedBy": "customer01",
                      "requestedChannel": "CUSTOMER_WEB",
                      "reason": "Synthetic full prepayment"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replayed").value(true))
            .andExpect(jsonPath("$.item.ledgerTransactionId").value(prepaymentTransactionId))

        assertEquals(1, countRows("loan_payments WHERE idempotency_key = 'LOAN-IT-PREPAY-001'"))
        assertEquals(1, countRows("outbox_events WHERE event_type = 'LoanPrepaymentPosted'"))
        assertEquals(0, unbalancedTransactionCount())
        assertProjectionMatchesPostings("ACC-LOAN-001")
        assertProjectionMatchesPostings("BANK-SUSPENSE")
        assertProjectionMatchesPostings("BANK-LOAN-ASSET")
        assertTrue(availableBalance("ACC-LOAN-001") >= 0)
        assertEquals(0, scalarLong("SELECT outstanding_principal_minor FROM loans WHERE loan_id = :loanId", mapOf("loanId" to loanId)))
        assertEquals(0, countRows("loan_repayment_schedule WHERE loan_id = '$loanId' AND status IN ('PENDING', 'OVERDUE')"))
        assertEquals(0, countRows("ledger_transactions WHERE transaction_type LIKE 'LOAN%' AND status <> 'POSTED'"))
        assertEquals(3, countRows("ledger_transactions WHERE transaction_type IN ('LOAN_DISBURSEMENT', 'LOAN_REPAYMENT', 'LOAN_PREPAYMENT')"))
        assertEquals(3, countRows("idempotency_keys WHERE command_type IN ('LOAN_DISBURSEMENT', 'LOAN_REPAYMENT', 'LOAN_PREPAYMENT')"))
        assertTrue(countRows("audit_events WHERE event_type LIKE 'LOAN_%'") >= 4)
    }

    private fun applicationJson(reason: String): String =
        """
        {
          "customerId": "SYN-CUS-LOAN-001",
          "depositAccountId": "ACC-LOAN-001",
          "productId": "LOAN-PROD-IT-001",
          "requestedAmountMinor": 1200000,
          "requestedTermMonths": 12,
          "syntheticMonthlyIncomeMinor": 5000000,
          "syntheticMonthlyDebtMinor": 500000,
          "syntheticCreditGrade": "A",
          "syntheticRiskGrade": "LOW",
          "requestedBy": "manager01",
          "requestedByRole": "BRANCH_MANAGER",
          "reason": "$reason",
          "idempotencyKey": "LOAN-IT-APP-001"
        }
        """.trimIndent()

    private fun seedSyntheticLoanFixtures() {
        jdbc.update(
            """
            INSERT INTO customers (customer_id, customer_name, customer_grade, risk_grade)
            VALUES
              ('BANK', 'Synthetic Bank', 'SYSTEM', 'LOW'),
              ('SYN-CUS-LOAN-001', 'Synthetic Loan Customer', 'STANDARD', 'LOW')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO accounts (account_id, customer_id, account_no, currency, status)
            VALUES
              ('BANK-SUSPENSE', 'BANK', 'LAB-000-000000', 'KRW', 'ACTIVE'),
              ('BANK-LOAN-ASSET', 'BANK', 'LAB-000-000001', 'KRW', 'ACTIVE'),
              ('ACC-LOAN-001', 'SYN-CUS-LOAN-001', 'LAB-070-000001', 'KRW', 'ACTIVE')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO loan_products (
              product_id, product_code, product_name, currency, annual_rate_bps, term_months,
              minimum_amount_minor, maximum_amount_minor, approval_threshold_minor, status, synthetic_only
            )
            VALUES (
              'LOAN-PROD-IT-001', 'IT-SYN-PERSONAL', 'Integration Synthetic Personal Loan',
              'KRW', 720, 12, 100000, 5000000, 100000, 'ACTIVE', TRUE
            )
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
              'TX-LOAN-OPENING', 'SYNTHETIC_OPENING_BALANCE', 'TX-LOAN-OPENING',
              'SEED-TX-LOAN-OPENING', DATE '2026-02-01', 'POSTED',
              'SEED', 'SYNTHETIC_DATA_GENERATOR', TIMESTAMPTZ '2026-02-01T00:00:00Z',
              'Synthetic opening loan repayment capacity'
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
              ('LP-LOAN-OPENING-D', 'TX-LOAN-OPENING', 'BANK-SUSPENSE', 'KRW', 'DEBIT', 500000, 'OPENING'),
              ('LP-LOAN-OPENING-C', 'TX-LOAN-OPENING', 'ACC-LOAN-001', 'KRW', 'CREDIT', 500000, 'OPENING')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO account_balance_projections (
              account_id, currency, ledger_balance_minor, available_balance_minor, hold_amount_minor
            )
            VALUES
              ('BANK-SUSPENSE', 'KRW', -500000, -500000, 0),
              ('BANK-LOAN-ASSET', 'KRW', 0, 0, 0),
              ('ACC-LOAN-001', 'KRW', 500000, 500000, 0)
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

    private fun bearer(subject: String, roles: List<String>, customerId: String? = null): String {
        val payload = linkedMapOf<String, Any?>(
            "iss" to "http://keycloak.local/realms/banking-lab",
            "sub" to subject,
            "roles" to roles,
            "customerId" to customerId,
            "active" to true
        )
        val json = objectMapper.writeValueAsBytes(payload)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json)
        return "Bearer lab.$encoded.sig"
    }

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $tableExpression", emptyMap<String, Any?>(), Int::class.java) ?: 0

    private fun scalarString(sql: String, params: Map<String, Any?>): String =
        jdbc.queryForObject(sql, params, String::class.java) ?: ""

    private fun scalarLong(sql: String, params: Map<String, Any?>): Long =
        jdbc.queryForObject(sql, params, Long::class.java) ?: 0L

    private fun signedTransactionSum(transactionId: String): Long =
        jdbc.queryForObject(
            """
            SELECT COALESCE(SUM(CASE WHEN direction = 'DEBIT' THEN -amount_minor ELSE amount_minor END), 0)
            FROM ledger_postings
            WHERE ledger_transaction_id = :transactionId
            """.trimIndent(),
            mapOf("transactionId" to transactionId),
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

    private fun assertProjectionMatchesPostings(accountId: String) {
        val postingSum = jdbc.queryForObject(
            """
            SELECT COALESCE(SUM(CASE WHEN direction = 'DEBIT' THEN -amount_minor ELSE amount_minor END), 0)
            FROM ledger_postings
            WHERE account_id = :accountId
            """.trimIndent(),
            mapOf("accountId" to accountId),
            Long::class.java
        ) ?: 0L
        val projection = jdbc.queryForObject(
            """
            SELECT ledger_balance_minor
            FROM account_balance_projections
            WHERE account_id = :accountId
            """.trimIndent(),
            mapOf("accountId" to accountId),
            Long::class.java
        ) ?: 0L
        assertEquals(postingSum, projection, "projection mismatch for $accountId")
    }

    private fun availableBalance(accountId: String): Long =
        jdbc.queryForObject(
            """
            SELECT available_balance_minor
            FROM account_balance_projections
            WHERE account_id = :accountId
            """.trimIndent(),
            mapOf("accountId" to accountId),
            Long::class.java
        ) ?: 0L

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.locations") {
                "filesystem:${migrationsPath()}"
            }
        }

        private fun migrationsPath(): Path =
            generateSequence(Paths.get("").toAbsolutePath()) { it.parent }
                .map { it.resolve("db/migrations") }
                .first(Files::isDirectory)
    }
}
