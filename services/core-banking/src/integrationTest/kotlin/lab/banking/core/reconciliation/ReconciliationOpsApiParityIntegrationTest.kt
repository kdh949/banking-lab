package lab.banking.core.reconciliation

import com.fasterxml.jackson.databind.ObjectMapper
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReconciliationOpsApiParityIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              reconciliation_adjustment_requests,
              reconciliation_items,
              outbox_events,
              inbox_events,
              idempotency_keys,
              ledger_postings,
              ledger_transactions,
              daily_closings,
              account_balance_projections,
              account_holds,
              account_limits,
              operator_approvals,
              audit_events,
              accounts,
              customers
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
        seedAccountsAndBalances()
    }

    @Test
    fun `EOD reconciliation adjustment posts only after checker approval on open day`() {
        val businessDate = LocalDate.of(2026, 2, 3)
        val adjustmentDate = businessDate.plusDays(1)

        mockMvc.perform(
            post("/api/ledger/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "fromAccountId": "ACC-SYN-001-001",
                      "toAccountId": "ACC-SYN-002-001",
                      "amountMinor": 1234,
                      "idempotencyKey": "RECON-TRF-001",
                      "businessDate": "$businessDate",
                      "requestedBy": "ops01",
                      "requestedChannel": "OPS_CONSOLE",
                      "reason": "Seed reconciliation internal transfer"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.value.transactionType").value("INTERNAL_TRANSFER"))

        val closingResponse = mockMvc.perform(
            post("/api/ops/daily-closings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "businessDate": "$businessDate",
                      "idempotencyKey": "RECON-EOD-001",
                      "requestedBy": "ops01",
                      "externalMode": "MISMATCH"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.ledgerInvariantValid").value(true))
            .andExpect(jsonPath("$.item.status").value("UNMATCHED"))
            .andExpect(jsonPath("$.item.unmatchedItemCount").value(1))
            .andExpect(jsonPath("$.reconciliationItems[0].status").value("OPEN"))
            .andExpect(jsonPath("$.reconciliationItems[0].owner").value("ops01"))
            .andExpect(jsonPath("$.reconciliationItems[0].mismatchType").value("AMOUNT_MISMATCH"))
            .andExpect(jsonPath("$.reconciliationItems[0].amountMinor").value(1000))
            .andExpect(jsonPath("$.reconciliationItems[0].internalAmountMinor").value(1234))
            .andExpect(jsonPath("$.reconciliationItems[0].externalAmountMinor").value(2234))
            .andExpect(jsonPath("$.reconciliationItems[0].externalStatus").value("SETTLED"))
            .andExpect(jsonPath("$.reconciliationItems[0].feedFileId").value(org.hamcrest.Matchers.startsWith("EXT-FILE-")))
            .andExpect(jsonPath("$.reconciliationItems[0].detectedReason").value("Synthetic external feed amount differs from the posted internal transfer"))
            .andReturn()

        val itemId = objectMapper.readTree(closingResponse.response.contentAsString)
            .path("reconciliationItems")
            .path(0)
            .path("itemId")
            .asText()

        mockMvc.perform(
            post("/api/ledger/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "fromAccountId": "ACC-SYN-001-001",
                      "toAccountId": "ACC-SYN-002-001",
                      "amountMinor": 100,
                      "idempotencyKey": "RECON-CLOSED-DAY-001",
                      "businessDate": "$businessDate",
                      "requestedBy": "ops01",
                      "requestedChannel": "OPS_CONSOLE",
                      "reason": "Should fail after close"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("LEDGER_CLOSED_DAY_IMMUTABLE"))

        mockMvc.perform(
            post("/api/ops/reconciliation-items/$itemId/adjustment-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"requestedBy":"ops01","accountId":"ACC-SYN-001-001"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("POLICY_REASON_REQUIRED"))

        val adjustmentResponse = mockMvc.perform(
            post("/api/ops/reconciliation-items/$itemId/adjustment-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requestedBy": "ops01",
                      "requestedByRole": "OPS_OPERATOR",
                      "reason": "Synthetic reconciliation adjustment",
                      "accountId": "ACC-SYN-001-001",
                      "direction": "CREDIT",
                      "amountMinor": 1000,
                      "businessDate": "$adjustmentDate",
                      "idempotencyKey": "RECON-ADJ-001"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.status").value("ADJUSTMENT_REQUESTED"))
            .andExpect(jsonPath("$.item.adjustmentRequest.status").value("REQUESTED"))
            .andExpect(jsonPath("$.approval.status").value("PENDING"))
            .andReturn()

        val approvalId = objectMapper.readTree(adjustmentResponse.response.contentAsString)
            .path("approval")
            .path("approvalId")
            .asText()
        assertEquals("ADJUSTMENT_REQUESTED", reconciliationStatus(itemId))
        assertEquals(0, countRows("ledger_transactions WHERE transaction_type = 'ADJUSTMENT'"))

        mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"approvedBy":"ops01","approvedByRole":"OPS_OPERATOR","screenId":"OPS-201"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("MAKER_CHECKER_SELF_APPROVAL_REJECTED"))
        assertEquals("ADJUSTMENT_REQUESTED", reconciliationStatus(itemId))
        assertEquals(0, countRows("ledger_transactions WHERE transaction_type = 'ADJUSTMENT'"))

        mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"approvedBy":"manager01","approvedByRole":"BRANCH_MANAGER","screenId":"OPS-201"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executed").value(true))
            .andExpect(jsonPath("$.reconciliationItem.status").value("ADJUSTED"))
            .andExpect(jsonPath("$.reconciliationItem.adjustmentRequest.status").value("POSTED"))
            .andExpect(jsonPath("$.ledgerTransaction.value.transactionType").value("ADJUSTMENT"))
            .andExpect(jsonPath("$.ledgerTransaction.value.businessReferenceId").value(itemId))
            .andExpect(jsonPath("$.ledgerTransaction.value.businessDate").value(adjustmentDate.toString()))
            .andExpect(jsonPath("$.ledgerTransaction.value.idempotencyKey").value("RECON-ADJ-001"))

        assertEquals("ADJUSTED", reconciliationStatus(itemId))
        assertEquals(1, countRows("ledger_transactions WHERE transaction_type = 'ADJUSTMENT'"))
        assertEquals(0, unbalancedTransactionCount())
        assertEquals(1, countRows("outbox_events WHERE event_type = 'AdjustmentPosted'"))
        assertEquals(1, countRows("reconciliation_adjustment_requests WHERE status = 'POSTED'"))
        assertEquals(9_999_766L, balance("ACC-SYN-001-001"))

        mockMvc.perform(
            post("/api/ops/reconciliation-items/$itemId/adjustment-requests")
                .header("x-request-id", "REQ-REC-ADJUSTED-ADJUSTMENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requestedBy": "ops01",
                      "requestedByRole": "OPS_OPERATOR",
                      "reason": "Duplicate reconciliation adjustment after item was adjusted",
                      "accountId": "ACC-SYN-001-001",
                      "direction": "CREDIT",
                      "amountMinor": 1000,
                      "businessDate": "$adjustmentDate",
                      "idempotencyKey": "RECON-ADJ-DUPLICATE-001"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("WORKFLOW_STATE_VIOLATION"))
            .andExpect(jsonPath("$.error.domain").value("workflow"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-REC-ADJUSTED-ADJUSTMENT"))
            .andExpect(jsonPath("$.error.route").value("/api/ops/reconciliation-items/$itemId/adjustment-requests"))
    }

    @Test
    fun `external simulator feed modes classify duplicate stale external-only and missing-external items`() {
        val duplicateDate = LocalDate.of(2026, 2, 10)
        postSyntheticTransfer(duplicateDate, "RECON-TRF-DUP-001", 1_500)
        mockMvc.perform(
            post("/api/ops/daily-closings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(closingCommand(duplicateDate, "RECON-EOD-DUP-001", "DUPLICATE"))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.reconciliationItems[0].mismatchType").value("DUPLICATE_EXTERNAL"))
            .andExpect(jsonPath("$.reconciliationItems[0].amountMinor").value(1_500))
            .andExpect(jsonPath("$.reconciliationItems[0].internalAmountMinor").value(1_500))
            .andExpect(jsonPath("$.reconciliationItems[0].externalAmountMinor").value(3_000))
            .andExpect(jsonPath("$.reconciliationItems[0].externalStatus").value("DUPLICATE"))

        val staleDate = LocalDate.of(2026, 2, 11)
        postSyntheticTransfer(staleDate, "RECON-TRF-STALE-001", 2_000)
        mockMvc.perform(
            post("/api/ops/daily-closings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(closingCommand(staleDate, "RECON-EOD-STALE-001", "STALE"))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.reconciliationItems[0].mismatchType").value("STALE_EXTERNAL"))
            .andExpect(jsonPath("$.reconciliationItems[0].amountMinor").value(2_000))
            .andExpect(jsonPath("$.reconciliationItems[0].internalAmountMinor").value(2_000))
            .andExpect(jsonPath("$.reconciliationItems[0].externalAmountMinor").value(2_000))
            .andExpect(jsonPath("$.reconciliationItems[0].externalStatus").value("STALE"))

        val externalOnlyDate = LocalDate.of(2026, 2, 12)
        mockMvc.perform(
            post("/api/ops/daily-closings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(closingCommand(externalOnlyDate, "RECON-EOD-EXT-ONLY-001", "EXTERNAL_ONLY"))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.reconciliationItems[0].mismatchType").value("UNEXPECTED_EXTERNAL"))
            .andExpect(jsonPath("$.reconciliationItems[0].internalAmountMinor").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reconciliationItems[0].externalAmountMinor").value(1_000))
            .andExpect(jsonPath("$.reconciliationItems[0].externalStatus").value("SETTLED"))

        val missingDate = LocalDate.of(2026, 2, 13)
        postSyntheticTransfer(missingDate, "RECON-TRF-MISSING-001", 2_500)
        mockMvc.perform(
            post("/api/ops/daily-closings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(closingCommand(missingDate, "RECON-EOD-MISSING-001", "MISSING_EXTERNAL"))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.reconciliationItems[0].mismatchType").value("MISSING_EXTERNAL"))
            .andExpect(jsonPath("$.reconciliationItems[0].internalAmountMinor").value(2_500))
            .andExpect(jsonPath("$.reconciliationItems[0].externalAmountMinor").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reconciliationItems[0].detectedReason").value("Internal posted transfer is missing from synthetic external feed"))
    }

    private fun postSyntheticTransfer(businessDate: LocalDate, idempotencyKey: String, amountMinor: Long) {
        mockMvc.perform(
            post("/api/ledger/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "fromAccountId": "ACC-SYN-001-001",
                      "toAccountId": "ACC-SYN-002-001",
                      "amountMinor": $amountMinor,
                      "idempotencyKey": "$idempotencyKey",
                      "businessDate": "$businessDate",
                      "requestedBy": "ops01",
                      "requestedChannel": "OPS_CONSOLE",
                      "reason": "Seed reconciliation simulator feed taxonomy"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
    }

    private fun closingCommand(businessDate: LocalDate, idempotencyKey: String, externalMode: String): String =
        """
        {
          "businessDate": "$businessDate",
          "idempotencyKey": "$idempotencyKey",
          "requestedBy": "ops01",
          "externalMode": "$externalMode"
        }
        """.trimIndent()

    private fun seedAccountsAndBalances() {
        TransactionTemplate(transactionManager).executeWithoutResult {
        jdbc.update(
            """
            INSERT INTO customers (customer_id, customer_name, customer_grade, risk_grade)
            VALUES
              ('BANK', 'Bank Suspense', 'INTERNAL', 'LOW'),
              ('SYN-CUS-001', 'Lab Customer Alpha', 'STANDARD', 'LOW'),
              ('SYN-CUS-002', 'Lab Customer Beta', 'STANDARD', 'LOW')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO accounts (account_id, customer_id, account_no, currency, status)
            VALUES
              ('BANK-SUSPENSE', 'BANK', 'LAB-000-000000', 'KRW', 'ACTIVE'),
              ('ACC-SYN-001-001', 'SYN-CUS-001', 'LAB-001-000001', 'KRW', 'ACTIVE'),
              ('ACC-SYN-002-001', 'SYN-CUS-002', 'LAB-002-000001', 'KRW', 'ACTIVE')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO ledger_transactions (
              ledger_transaction_id, transaction_type, business_reference_id, idempotency_key,
              business_date, status, requested_by, requested_channel, posted_at
            )
            VALUES (
              'TX-OPEN-REC-001', 'SYNTHETIC_OPENING_BALANCE', 'TX-OPEN-REC-001', 'SEED-TX-OPEN-REC-001',
              CURRENT_DATE, 'POSTED', 'SEED', 'SYNTHETIC_DATA_GENERATOR', now()
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
              ('LP-REC-OPEN-D', 'TX-OPEN-REC-001', 'BANK-SUSPENSE', 'KRW', 'DEBIT', 10000000, 'OPENING'),
              ('LP-REC-OPEN-C', 'TX-OPEN-REC-001', 'ACC-SYN-001-001', 'KRW', 'CREDIT', 10000000, 'OPENING')
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
              ('ACC-SYN-001-001', 'KRW', 10000000, 10000000, 0),
              ('ACC-SYN-002-001', 'KRW', 0, 0, 0)
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        }
    }

    private fun reconciliationStatus(itemId: String): String? =
        jdbc.queryForObject(
            "SELECT status FROM reconciliation_items WHERE reconciliation_item_id = :itemId",
            mapOf("itemId" to itemId),
            String::class.java
        )

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM $tableExpression",
            emptyMap<String, Any?>(),
            Int::class.java
        ) ?: 0

    private fun balance(accountId: String): Long =
        jdbc.queryForObject(
            "SELECT available_balance_minor FROM account_balance_projections WHERE account_id = :accountId",
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
