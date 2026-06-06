package lab.banking.core.account

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Paths
import java.util.Base64
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
import org.springframework.test.web.servlet.MvcResult
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
class AccountOpeningIntegrationTest {
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
              account_opening_requests,
              ledger_postings,
              ledger_transactions,
              idempotency_keys,
              outbox_events,
              inbox_events,
              account_balance_projections,
              account_limits,
              accounts,
              customer_kyc_profiles,
              customers,
              operator_approvals,
              audit_events
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
        jdbc.jdbcTemplate.execute("ALTER SEQUENCE synthetic_account_opening_seq RESTART WITH 100001")
        seedCustomer("SYN-CUS-OPEN-001")
    }

    @Test
    fun `account opening creates account and posts optional opening deposit only after checker approval`() {
        val requestBody = openingRequest("AOR-P2-001")
        val created = postOpening(requestBody, bearer("branch01", listOf("BRANCH_STAFF")))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.syntheticOnly").value(true))
            .andExpect(jsonPath("$.item.status").value("PENDING_APPROVAL"))
            .andExpect(jsonPath("$.item.customerId").value("SYN-CUS-OPEN-001"))
            .andExpect(jsonPath("$.approval.businessType").value("ACCOUNT_OPENING"))
            .andExpect(jsonPath("$.approval.status").value("PENDING"))
            .andReturn()
        val requestId = created.read("$.item.requestId")
        val approvalId = created.read("$.item.approvalId")

        postOpening(requestBody, bearer("branch01", listOf("BRANCH_STAFF")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replayed").value(true))
            .andExpect(jsonPath("$.item.requestId").value(requestId))
        assertEquals(1, countRows("account_opening_requests"))
        assertEquals(0, countRows("accounts"))
        assertEquals(0, countRows("ledger_transactions"))

        val changedPayload = requestBody.toMutableMap()
        changedPayload["initialDepositAmountMinor"] = 30_000
        postOpening(changedPayload, bearer("branch01", listOf("BRANCH_STAFF")))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_CONFLICT"))

        approveOpening(requestId, "branch01", "BRANCH_MANAGER")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("MAKER_CHECKER_SELF_APPROVAL_REJECTED"))

        approveOpening(requestId, "manager01", "BRANCH_MANAGER")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.item.status").value("APPROVED"))
            .andExpect(jsonPath("$.approval.approvalId").value(approvalId))
            .andExpect(jsonPath("$.approval.status").value("APPROVED"))

        val executed = executeOpening(requestId, "AOR-EXEC-P2-001")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.item.status").value("EXECUTED"))
            .andExpect(jsonPath("$.item.generatedAccountId").value("ACC-SYN-NEW-100001"))
            .andExpect(jsonPath("$.item.generatedMaskedAccountNo").value("LAB-***-0001"))
            .andExpect(jsonPath("$.account.accountId").value("ACC-SYN-NEW-100001"))
            .andExpect(jsonPath("$.account.maskedAccountNo").value("LAB-***-0001"))
            .andExpect(jsonPath("$.account.ledgerBalanceMinor").value(25_000))
            .andExpect(jsonPath("$.account.availableBalanceMinor").value(25_000))
            .andExpect(jsonPath("$.account.initialDepositLedgerTransactionId").exists())
            .andReturn()
        val accountId = executed.read("$.account.accountId")
        val ledgerTransactionId = executed.read("$.account.initialDepositLedgerTransactionId")

        executeOpening(requestId, "AOR-EXEC-P2-001")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replayed").value(true))
            .andExpect(jsonPath("$.account.accountId").value(accountId))
            .andExpect(jsonPath("$.account.initialDepositLedgerTransactionId").value(ledgerTransactionId))

        getOpening(requestId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("EXECUTED"))
            .andExpect(jsonPath("$.item.generatedAccountId").value(accountId))

        assertEquals(1, countRows("accounts WHERE account_id = '$accountId' AND customer_id = 'SYN-CUS-OPEN-001'"))
        assertEquals(1, countRows("account_limits WHERE account_id = '$accountId' AND daily_transfer_limit_minor = 100000000"))
        assertEquals(1, countRows("account_balance_projections WHERE account_id = '$accountId' AND ledger_balance_minor = 25000 AND available_balance_minor = 25000"))
        assertEquals(1, countRows("ledger_transactions WHERE ledger_transaction_id = '$ledgerTransactionId' AND transaction_type = 'DEPOSIT' AND business_reference_id = '$requestId'"))
        assertEquals(2, countRows("ledger_postings WHERE ledger_transaction_id = '$ledgerTransactionId'"))
        assertEquals(1, countRows("idempotency_keys WHERE idempotency_key = 'AOR-DEP-P2-001' AND ledger_transaction_id = '$ledgerTransactionId'"))
        assertEquals(1, countRows("outbox_events WHERE aggregate_id = '$ledgerTransactionId' AND event_type = 'LedgerTransactionPosted'"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'ACCOUNT_OPENING_EXECUTED' AND account_id = '$accountId'"))
        assertEquals(1, countRows("account_opening_requests WHERE status = 'EXECUTED' AND initial_deposit_ledger_transaction_id = '$ledgerTransactionId'"))
    }

    @Test
    fun `account opening rejects missing reason invalid limits missing deposit key and rejected execute`() {
        val noReason = openingRequest("AOR-P2-NO-REASON").toMutableMap()
        noReason["reason"] = " "
        postOpening(noReason, bearer("branch01", listOf("BRANCH_STAFF")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("POLICY_REASON_REQUIRED"))

        val invalidLimits = openingRequest("AOR-P2-LIMITS").toMutableMap()
        invalidLimits["singleTransferLimitMinor"] = 200_000_000
        postOpening(invalidLimits, bearer("branch01", listOf("BRANCH_STAFF")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("REQUEST_VALIDATION_FAILED"))

        val missingDepositKey = openingRequest("AOR-P2-NO-DEP-KEY").toMutableMap()
        missingDepositKey.remove("initialDepositIdempotencyKey")
        postOpening(missingDepositKey, bearer("branch01", listOf("BRANCH_STAFF")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("REQUEST_VALIDATION_FAILED"))

        val first = postOpening(openingRequest("AOR-P2-REJECT"), bearer("branch01", listOf("BRANCH_STAFF")))
            .andExpect(status().isCreated)
            .andReturn()
        val requestId = first.read("$.item.requestId")

        rejectOpening(requestId, "manager01", "BRANCH_MANAGER")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.status").value("REJECTED"))
            .andExpect(jsonPath("$.approval.status").value("REJECTED"))

        executeOpening(requestId, "AOR-EXEC-REJECTED")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("WORKFLOW_STATE_VIOLATION"))
        assertEquals(0, countRows("accounts"))
        assertEquals(0, countRows("ledger_transactions"))
    }

    private fun postOpening(payload: Map<String, Any?>, authorization: String) =
        mockMvc.perform(
            post("/api/staff/accounts/opening-requests")
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )

    private fun getOpening(requestId: String) =
        mockMvc.perform(
            get("/api/staff/accounts/opening-requests/$requestId")
                .header("Authorization", bearer("auditor01", listOf("AUDITOR")))
        )

    private fun approveOpening(requestId: String, actor: String, role: String) =
        mockMvc.perform(
            post("/api/staff/accounts/opening-requests/$requestId/approve")
                .header("Authorization", bearer(actor, listOf(role)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "approvedBy" to actor,
                            "approvedByRole" to role
                        )
                    )
                )
        )

    private fun rejectOpening(requestId: String, actor: String, role: String) =
        mockMvc.perform(
            post("/api/staff/accounts/opening-requests/$requestId/reject")
                .header("Authorization", bearer(actor, listOf(role)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "rejectedBy" to actor,
                            "rejectedByRole" to role,
                            "rejectReason" to "Synthetic account opening request rejected by checker"
                        )
                    )
                )
        )

    private fun executeOpening(requestId: String, idempotencyKey: String) =
        mockMvc.perform(
            post("/api/staff/accounts/opening-requests/$requestId/execute")
                .header("Authorization", bearer("branch01", listOf("BRANCH_STAFF")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "executedBy" to "branch01",
                            "executedByRole" to "BRANCH_STAFF",
                            "reason" to "Execute approved synthetic account opening request",
                            "idempotencyKey" to idempotencyKey
                        )
                    )
                )
        )

    private fun openingRequest(idempotencyKey: String): Map<String, Any?> =
        mapOf(
            "requestedBy" to "branch01",
            "requestedByRole" to "BRANCH_STAFF",
            "reason" to "Synthetic account opening integration test",
            "idempotencyKey" to idempotencyKey,
            "customerId" to "SYN-CUS-OPEN-001",
            "productCode" to "SYNTHETIC_DEPOSIT",
            "accountAlias" to "Synthetic Payroll Account",
            "currency" to "KRW",
            "dailyTransferLimitMinor" to 100_000_000,
            "singleTransferLimitMinor" to 50_000_000,
            "initialDepositAmountMinor" to 25_000,
            "initialDepositIdempotencyKey" to "AOR-DEP-P2-001",
            "businessDate" to "2026-06-07"
        )

    private fun seedCustomer(customerId: String) {
        jdbc.update(
            """
            INSERT INTO customers (customer_id, customer_name, customer_grade, risk_grade)
            VALUES (:customerId, 'Synthetic Account Opening Customer', 'STANDARD', 'LOW')
            """.trimIndent(),
            mapOf("customerId" to customerId)
        )
        jdbc.update(
            """
            INSERT INTO customer_kyc_profiles (
              customer_id, kyc_status, source_of_funds_code,
              transaction_purpose_code, simulated_provider_reference
            )
            VALUES (
              :customerId, 'VERIFIED', 'SALARY',
              'DAILY_BANKING', 'SIM-KYC-OPENING'
            )
            """.trimIndent(),
            mapOf("customerId" to customerId)
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

    private fun MvcResult.read(jsonPointer: String): String =
        objectMapper.readTree(response.contentAsString).at(jsonPointer.replace("$.", "/").replace(".", "/")).asText()

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $tableExpression", emptyMap<String, Any?>(), Int::class.java) ?: 0

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
