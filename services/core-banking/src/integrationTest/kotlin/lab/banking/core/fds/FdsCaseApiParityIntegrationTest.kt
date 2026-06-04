package lab.banking.core.fds

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Paths
import lab.banking.core.testsupport.ParameterSeedSupport
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
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FdsCaseApiParityIntegrationTest {
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
              customer_transfer_results,
              fds_case_timeline,
              fds_cases,
              outbox_events,
              inbox_events,
              idempotency_keys,
              ledger_postings,
              ledger_transactions,
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
        ParameterSeedSupport.reseedFdsRuleParameters(jdbc)
        seedAccountsAndBalances()
    }

    @Test
    fun `FDS release posts held transfer only after checker approval`() {
        val beforeCount = countRows("ledger_transactions")
        val heldResponse = mockMvc.perform(
            post("/api/customer/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "fromAccountId": "ACC-SYN-001-001",
                      "toAccountId": "ACC-SYN-002-001",
                      "amountMinor": 5000000,
                      "idempotencyKey": "FDS-RELEASE-FLOW-001",
                      "requestedBy": "SYN-CUS-001",
                      "reason": "Synthetic high-risk transfer release parity",
                      "newDevice": true
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.item.status").value("HELD"))
            .andExpect(jsonPath("$.item.caseId").exists())
            .andReturn()

        val caseId = objectMapper.readTree(heldResponse.response.contentAsString)
            .path("item")
            .path("caseId")
            .asText()
        assertEquals(beforeCount, countRows("ledger_transactions"))

        val heldCase = mockMvc.perform(get("/api/staff/fds-cases/$caseId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("HELD"))
            .andReturn()
        val heldAlerts = objectMapper.readTree(heldCase.response.contentAsString).path("alerts")
        assertTrue(heldAlerts.any { it.path("ruleId").asText() == "FDS-RULE-UNUSUAL-AMOUNT" })
        assertTrue(heldAlerts.any { it.path("ruleId").asText() == "FDS-RULE-NEW-DEVICE" })

        mockMvc.perform(
            post("/api/staff/fds-cases/$caseId/assign")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"actorId":"fds01","owner":"fds01","reason":"Synthetic FDS investigation assignment"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("INVESTIGATING"))
            .andExpect(jsonPath("$.owner").value("fds01"))

        val releaseRequest = mockMvc.perform(
            post("/api/staff/fds-cases/$caseId/release-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"actorId":"fds01"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("POLICY_REASON_REQUIRED"))
        releaseRequest.andReturn()

        val requestResponse = mockMvc.perform(
            post("/api/staff/fds-cases/$caseId/release-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "actorId": "fds01",
                      "requestedByRole": "FDS_REVIEWER",
                      "reason": "Synthetic FDS release after reviewer investigation"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("RELEASE_REQUESTED"))
            .andExpect(jsonPath("$.approval.status").value("PENDING"))
            .andReturn()

        val approvalId = objectMapper.readTree(requestResponse.response.contentAsString)
            .path("approval")
            .path("approvalId")
            .asText()
        val beforeApprovalResults = transferStatuses("SYN-CUS-001")
        assertEquals("HELD", beforeApprovalResults.first { it.path("caseId").asText() == caseId }.path("status").asText())
        assertEquals(0, countRows("ledger_transactions WHERE transaction_type = 'INTERNAL_TRANSFER'"))

        mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"approvedBy":"fds01","approvedByRole":"FDS_REVIEWER","screenId":"FDS-201"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("MAKER_CHECKER_SELF_APPROVAL_REJECTED"))
        assertEquals("RELEASE_REQUESTED", fdsStatus(caseId))
        assertEquals(0, countRows("ledger_transactions WHERE transaction_type = 'INTERNAL_TRANSFER'"))

        mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"approvedBy":"manager01","approvedByRole":"BRANCH_MANAGER","screenId":"FDS-201"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executed").value(true))
            .andExpect(jsonPath("$.fdsCase.status").value("RELEASED"))
            .andExpect(jsonPath("$.fdsCase.transferStatus").value("POSTED"))
            .andExpect(jsonPath("$.ledgerTransaction.value.businessReferenceId").value(caseId))
            .andExpect(jsonPath("$.ledgerTransaction.value.idempotencyKey").value("FDS-RELEASE-$caseId"))

        val afterApprovalResults = transferStatuses("SYN-CUS-001")
        val postedTransfer = afterApprovalResults.first { it.path("caseId").asText() == caseId }
        assertEquals("POSTED", postedTransfer.path("status").asText())
        assertEquals("RELEASED", postedTransfer.path("caseStatus").asText())
        assertEquals("POSTED", postedTransfer.path("transferStatus").asText())
        assertEquals("RELEASED", fdsStatus(caseId))
        assertEquals(1, countRows("ledger_transactions WHERE transaction_type = 'INTERNAL_TRANSFER'"))
        assertEquals(0, unbalancedTransactionCount())
        assertEquals(5_000_000L, balance("ACC-SYN-001-001"))
        assertEquals(5_000_000L, balance("ACC-SYN-002-001"))
        assertEquals(1, countRows("outbox_events WHERE event_type = 'LedgerTransactionPosted'"))

        mockMvc.perform(
            post("/api/staff/fds-cases/$caseId/release-requests")
                .header("x-request-id", "REQ-FDS-RELEASED-RELEASE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "actorId": "fds01",
                      "requestedByRole": "FDS_REVIEWER",
                      "reason": "Duplicate release request after posted FDS release"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("WORKFLOW_STATE_VIOLATION"))
            .andExpect(jsonPath("$.error.domain").value("workflow"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-FDS-RELEASED-RELEASE"))
            .andExpect(jsonPath("$.error.route").value("/api/staff/fds-cases/$caseId/release-requests"))
    }

    @Test
    fun `FDS block closes held transfer without ledger posting`() {
        val beforeCount = countRows("ledger_transactions")
        val heldResponse = mockMvc.perform(
            post("/api/customer/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "fromAccountId": "ACC-SYN-001-001",
                      "toAccountId": "ACC-SYN-002-001",
                      "amountMinor": 6000000,
                      "idempotencyKey": "FDS-BLOCK-FLOW-001",
                      "requestedBy": "SYN-CUS-001",
                      "reason": "Synthetic high-risk transfer block parity",
                      "firstTimeBeneficiary": true
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.item.status").value("HELD"))
            .andExpect(jsonPath("$.item.caseId").exists())
            .andReturn()

        val caseId = objectMapper.readTree(heldResponse.response.contentAsString)
            .path("item")
            .path("caseId")
            .asText()
        assertEquals(beforeCount, countRows("ledger_transactions"))

        val heldCase = mockMvc.perform(get("/api/staff/fds-cases/$caseId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("HELD"))
            .andReturn()
        val heldAlerts = objectMapper.readTree(heldCase.response.contentAsString).path("alerts")
        assertTrue(heldAlerts.any { it.path("ruleId").asText() == "FDS-RULE-FIRST-BENEFICIARY" })

        mockMvc.perform(
            post("/api/staff/fds-cases/$caseId/assign")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"actorId":"fds01","owner":"fds01","reason":"Synthetic FDS block assignment"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("INVESTIGATING"))

        val requestResponse = mockMvc.perform(
            post("/api/staff/fds-cases/$caseId/block-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"actorId":"fds01","reason":"Synthetic suspicious beneficiary block"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("BLOCK_REQUESTED"))
            .andReturn()

        val approvalId = objectMapper.readTree(requestResponse.response.contentAsString)
            .path("approval")
            .path("approvalId")
            .asText()

        mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"approvedBy":"manager01","approvedByRole":"BRANCH_MANAGER","screenId":"FDS-201"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executed").value(true))
            .andExpect(jsonPath("$.fdsCase.status").value("BLOCKED"))
            .andExpect(jsonPath("$.fdsCase.transferStatus").value("BLOCKED"))
            .andExpect(jsonPath("$.ledgerTransaction").doesNotExist())

        val results = transferStatuses("SYN-CUS-001")
        val blockedTransfer = results.first { it.path("caseId").asText() == caseId }
        assertEquals("BLOCKED", blockedTransfer.path("status").asText())
        assertEquals("BLOCKED", blockedTransfer.path("caseStatus").asText())
        assertEquals("BLOCKED", blockedTransfer.path("transferStatus").asText())
        assertEquals("BLOCKED", fdsStatus(caseId))
        assertEquals(beforeCount, countRows("ledger_transactions"))
        assertEquals(0, countRows("ledger_transactions WHERE transaction_type = 'INTERNAL_TRANSFER'"))
        assertEquals(10_000_000L, balance("ACC-SYN-001-001"))
        assertEquals(0L, balance("ACC-SYN-002-001"))
    }

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
              'TX-OPEN-FDS-001', 'SYNTHETIC_OPENING_BALANCE', 'TX-OPEN-FDS-001', 'SEED-TX-OPEN-FDS-001',
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
              ('LP-FDS-OPEN-D', 'TX-OPEN-FDS-001', 'BANK-SUSPENSE', 'KRW', 'DEBIT', 10000000, 'OPENING'),
              ('LP-FDS-OPEN-C', 'TX-OPEN-FDS-001', 'ACC-SYN-001-001', 'KRW', 'CREDIT', 10000000, 'OPENING')
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

    private fun seedFdsCase(caseId: String, status: String, transferStatus: String, amountMinor: Long) {
        jdbc.update(
            """
            INSERT INTO fds_cases (
              fds_case_id, transfer_reference_id, customer_id, status, risk_score,
              alerts_json, owner_id, from_account_id, to_account_id, amount_minor,
              transfer_idempotency_key, requested_by, business_date, transfer_status
            )
            VALUES (
              :caseId, :transferReferenceId, 'SYN-CUS-001', :status, 600,
              CAST(:alerts AS jsonb), 'fds01', 'ACC-SYN-001-001', 'ACC-SYN-002-001', :amountMinor,
              :transferIdempotencyKey, 'SYN-CUS-001', CURRENT_DATE, :transferStatus
            )
            """.trimIndent(),
            mapOf(
                "caseId" to caseId,
                "transferReferenceId" to "TRF-$caseId",
                "status" to status,
                "alerts" to """[{"ruleId":"FDS-RULE-UNUSUAL-AMOUNT","message":"Synthetic unusual transfer amount"}]""",
                "amountMinor" to amountMinor,
                "transferIdempotencyKey" to "TRF-IDEMP-$caseId",
                "transferStatus" to transferStatus
            )
        )
    }

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM $tableExpression",
            emptyMap<String, Any?>(),
            Int::class.java
        ) ?: 0

    private fun fdsStatus(caseId: String): String? =
        jdbc.queryForObject(
            "SELECT status FROM fds_cases WHERE fds_case_id = :caseId",
            mapOf("caseId" to caseId),
            String::class.java
        )

    private fun transferStatuses(customerId: String) =
        objectMapper.readTree(
            mockMvc.perform(get("/api/customer/transfers").queryParam("customerId", customerId))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString
        ).path("items")

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
