package lab.banking.core.customer

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Paths
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(properties = ["banking-lab.security.enabled=true"])
@AutoConfigureMockMvc
@Testcontainers
class CustomerSelfService360IntegrationTest {
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
              statement_artifact_snapshots,
              customer_self_service_account_opening_requests,
              customer_onboarding_checks,
              account_opening_requests,
              ledger_postings,
              ledger_transactions,
              idempotency_keys,
              outbox_events,
              inbox_events,
              audit_events,
              account_balance_projections,
              account_limits,
              account_holds,
              cards,
              loans,
              complaint_cases,
              trusted_devices,
              customer_auth_identities,
              customer_kyc_profiles,
              accounts,
              customers,
              operator_approvals
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
        seedCustomer360Fixtures()
    }

    @Test
    fun `profile onboarding checks are masked synthetic and audited`() {
        val result = mockMvc.perform(
            get("/api/customer/me")
                .header("Authorization", bearer("customer360", listOf("CUSTOMER"), customerId = "SYN-CUS-C360-001"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.customerId").value("SYN-CUS-C360-001"))
            .andExpect(jsonPath("$.username").value("customer360"))
            .andExpect(jsonPath("$.maskedPhone").value("***-****-2222"))
            .andExpect(jsonPath("$.maskedAddress").value("Seoul ********"))
            .andExpect(jsonPath("$.kycStatus").value("VERIFIED"))
            .andExpect(jsonPath("$.duplicateCheckStatus").value("REVIEW_REQUIRED"))
            .andExpect(jsonPath("$.nextRequiredAction").value("WAIT_FOR_STAFF_DUPLICATE_REVIEW"))
            .andExpect(jsonPath("$.onboardingChecks.length()").value(4))
            .andExpect(jsonPath("$.syntheticOnly").value(true))
            .andExpect(jsonPath("$.maskingPolicy").value("CUSTOMER_SELF"))
            .andReturn()

        val response = result.response.contentAsString
        assertFalse(response.contains("Synthetic Alpha"))
        assertFalse(response.contains("010-1111-2222"))
        assertEquals(4, countRows("customer_onboarding_checks WHERE customer_id = 'SYN-CUS-C360-001' AND raw_pii_stored = false"))
        assertEquals(
            1,
            countRows(
                """
                audit_events
                WHERE event_type = 'CUSTOMER_PROFILE_VIEW'
                  AND actor_type = 'CUSTOMER'
                  AND screen_id = 'CWB-003'
                  AND customer_id = 'SYN-CUS-C360-001'
                """.trimIndent()
            )
        )
    }

    @Test
    fun `customer self service routes reject missing session before profile audit`() {
        mockMvc.perform(
            get("/api/customer/me")
                .header("x-request-id", "REQ-C360-MISSING-SESSION")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-C360-MISSING-SESSION"))
            .andExpect(jsonPath("$.error.route").value("/api/customer/me"))

        mockMvc.perform(
            get("/api/customer/360")
                .header("x-request-id", "REQ-C360-360-MISSING-SESSION")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))
            .andExpect(jsonPath("$.error.route").value("/api/customer/360"))

        assertEquals(0, countRows("audit_events WHERE event_type = 'CUSTOMER_PROFILE_VIEW'"))
        assertEquals(0, countRows("audit_events WHERE event_type = 'CUSTOMER_360_VIEW'"))
    }

    @Test
    fun `self service account opening is idempotent intake without account or ledger mutation`() {
        val accountsBefore = countRows("accounts")
        val ledgerBefore = countRows("ledger_transactions")
        val requestBody = mapOf(
            "idempotencyKey" to "CSAO-C360-001",
            "productCode" to "SYNTHETIC_DEPOSIT",
            "accountAlias" to "travel reserve",
            "currency" to "KRW",
            "syntheticInitialDepositAmountMinor" to 10_000,
            "termsAccepted" to true
        )

        val created = postSelfServiceOpening(requestBody)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.syntheticOnly").value(true))
            .andExpect(jsonPath("$.item.status").value("CUSTOMER_SUBMITTED"))
            .andExpect(jsonPath("$.item.customerId").value("SYN-CUS-C360-001"))
            .andExpect(jsonPath("$.item.generatedAccountId").doesNotExist())
            .andReturn()
        val requestId = created.read("$.item.requestId")

        postSelfServiceOpening(requestBody)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replayed").value(true))
            .andExpect(jsonPath("$.item.requestId").value(requestId))

        val duplicatePending = requestBody.toMutableMap()
        duplicatePending["idempotencyKey"] = "CSAO-C360-002"
        postSelfServiceOpening(duplicatePending)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("CUSTOMER_ACCOUNT_OPENING_ALREADY_PENDING"))

        mockMvc.perform(
            get("/api/customer/account-opening-requests")
                .header("Authorization", bearer("customer360", listOf("CUSTOMER"), customerId = "SYN-CUS-C360-001"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].requestId").value(requestId))

        assertEquals(accountsBefore, countRows("accounts"))
        assertEquals(ledgerBefore, countRows("ledger_transactions"))
        assertEquals(1, countRows("customer_self_service_account_opening_requests WHERE request_id = '$requestId'"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'CUSTOMER_ACCOUNT_OPENING_REQUESTED' AND customer_id = 'SYN-CUS-C360-001'"))
    }

    @Test
    fun `customer 360 and statement artifacts use owned ledger projections without new postings`() {
        val ledgerBefore = countRows("ledger_transactions")
        val postingBefore = countRows("ledger_postings")

        mockMvc.perform(
            get("/api/customer/360")
                .header("Authorization", bearer("customer360", listOf("CUSTOMER"), customerId = "SYN-CUS-C360-001"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.profile.customerId").value("SYN-CUS-C360-001"))
            .andExpect(jsonPath("$.accountSummary.totalAccounts").value(1))
            .andExpect(jsonPath("$.accountSummary.totalLedgerBalanceMinor").value(96_500))
            .andExpect(jsonPath("$.accounts[0].maskedAccountNo").value("LAB-***-0001"))
            .andExpect(jsonPath("$.accounts[0].statementActions[0].actionType").value("ACCOUNT_STATEMENT"))
            .andExpect(jsonPath("$.recentLedgerActivity.length()").value(2))
            .andExpect(jsonPath("$.paymentSummary.source").value("payment-service-read-model-not-co-located"))
            .andExpect(jsonPath("$.notificationSummary.source").value("notification-service-read-model-not-co-located"))
            .andExpect(jsonPath("$.syntheticOnly").value(true))
            .andExpect(jsonPath("$.maskingPolicy").value("CUSTOMER_SELF"))

        val statement = mockMvc.perform(
            get("/api/customer/accounts/ACC-C360-001/statement")
                .header("Authorization", bearer("customer360", listOf("CUSTOMER"), customerId = "SYN-CUS-C360-001"))
                .queryParam("from", "2026-02-01")
                .queryParam("to", "2026-02-04")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accountId").value("ACC-C360-001"))
            .andExpect(jsonPath("$.customerId").value("SYN-CUS-C360-001"))
            .andExpect(jsonPath("$.openingBalanceMinor").value(0))
            .andExpect(jsonPath("$.closingBalanceMinor").value(96_500))
            .andExpect(jsonPath("$.lineCount").value(2))
            .andExpect(jsonPath("$.sourceLedgerHash").isString)
            .andExpect(jsonPath("$.payloadHash").isString)
            .andExpect(jsonPath("$.generatedAt").isString)
            .andReturn()
        val statementId = statement.read("$.statementId")

        mockMvc.perform(
            get("/api/customer/statements/artifacts")
                .header("Authorization", bearer("customer360", listOf("CUSTOMER"), customerId = "SYN-CUS-C360-001"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].statementId").value(statementId))
            .andExpect(jsonPath("$.items[0].statementScope").value("ACCOUNT"))
            .andExpect(jsonPath("$.items[0].syntheticOnly").value(true))

        mockMvc.perform(
            get("/api/customer/accounts/ACC-C360-OTHER/statement")
                .header("Authorization", bearer("customer360", listOf("CUSTOMER"), customerId = "SYN-CUS-C360-001"))
                .queryParam("from", "2026-02-01")
                .queryParam("to", "2026-02-04")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))

        assertTrue(statementId.startsWith("STMT-"))
        assertEquals(1, countRows("statement_artifact_snapshots WHERE statement_id = '$statementId' AND synthetic_only = true"))
        assertEquals(ledgerBefore, countRows("ledger_transactions"))
        assertEquals(postingBefore, countRows("ledger_postings"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'CUSTOMER_360_VIEW' AND screen_id = 'CWB-104'"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'CUSTOMER_ACCOUNT_STATEMENT_VIEW' AND screen_id = 'CWB-105'"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'STATEMENT_ARTIFACT_HISTORY_VIEW' AND screen_id = 'CWB-107'"))
        assertEquals(0, countRows("audit_events WHERE payload_json::text LIKE '%LAB-360-000001%'"))
    }

    private fun postSelfServiceOpening(payload: Map<String, Any?>) =
        mockMvc.perform(
            post("/api/customer/account-opening-requests")
                .header("Authorization", bearer("customer360", listOf("CUSTOMER"), customerId = "SYN-CUS-C360-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )

    private fun seedCustomer360Fixtures() {
        TransactionTemplate(transactionManager).executeWithoutResult {
            jdbc.update(
                """
                INSERT INTO customers (
                  customer_id, customer_name, customer_phone, customer_address, customer_grade, risk_grade
                )
                VALUES
                  ('BANK', 'Synthetic Bank Suspense', '010-0000-0000', 'Synthetic Bank Address', 'INTERNAL', 'LOW'),
                  ('SYN-CUS-C360-001', 'Synthetic Alpha', '010-1111-2222', 'Seoul Synthetic District', 'STANDARD', 'LOW'),
                  ('SYN-CUS-C360-002', 'Synthetic Alpha', '010-1111-2222', 'Seoul Synthetic District', 'STANDARD', 'LOW')
                """.trimIndent(),
                emptyMap<String, Any?>()
            )
            jdbc.update(
                """
                INSERT INTO customer_kyc_profiles (
                  customer_id, kyc_status, source_of_funds_code, transaction_purpose_code, simulated_provider_reference
                )
                VALUES
                  ('SYN-CUS-C360-001', 'VERIFIED', 'SELF_SERVICE_SYNTHETIC', 'DAILY_BANKING', 'KYC-C360-001'),
                  ('SYN-CUS-C360-002', 'VERIFIED', 'SELF_SERVICE_SYNTHETIC', 'DAILY_BANKING', 'KYC-C360-002')
                """.trimIndent(),
                emptyMap<String, Any?>()
            )
            jdbc.update(
                """
                INSERT INTO customer_auth_identities (
                  auth_subject, customer_id, username, password_hash, status, metadata_json
                )
                VALUES
                  ('AUTH-C360-001', 'SYN-CUS-C360-001', 'customer360', '{bcrypt}synthetic-hash', 'ACTIVE', '{"syntheticOnly":true}'::jsonb),
                  ('AUTH-C360-002', 'SYN-CUS-C360-002', 'customer361', '{bcrypt}synthetic-hash', 'ACTIVE', '{"syntheticOnly":true}'::jsonb)
                """.trimIndent(),
                emptyMap<String, Any?>()
            )
            jdbc.update(
                """
                INSERT INTO accounts (account_id, customer_id, account_no, currency, status, opened_at)
                VALUES
                  ('BANK-SUSPENSE-C360', 'BANK', 'LAB-000-000000', 'KRW', 'ACTIVE', TIMESTAMPTZ '2026-02-01T00:00:00Z'),
                  ('ACC-C360-001', 'SYN-CUS-C360-001', 'LAB-360-000001', 'KRW', 'ACTIVE', TIMESTAMPTZ '2026-02-01T00:00:00Z'),
                  ('ACC-C360-OTHER', 'SYN-CUS-C360-002', 'LAB-361-000001', 'KRW', 'ACTIVE', TIMESTAMPTZ '2026-02-01T00:00:00Z')
                """.trimIndent(),
                emptyMap<String, Any?>()
            )
            jdbc.update(
                """
                INSERT INTO ledger_transactions (
                  ledger_transaction_id, transaction_type, business_reference_id, idempotency_key,
                  business_date, status, requested_by, requested_channel, posted_at, reason
                )
                VALUES
                  ('TX-C360-OPEN', 'SYNTHETIC_OPENING_BALANCE', 'TX-C360-OPEN', 'SEED-TX-C360-OPEN', DATE '2026-02-01', 'POSTED', 'SEED', 'SYNTHETIC_DATA_GENERATOR', TIMESTAMPTZ '2026-02-01T00:00:00Z', 'Synthetic opening balance'),
                  ('TX-C360-FEE', 'FEE_POSTING', 'TX-C360-FEE', 'SEED-TX-C360-FEE', DATE '2026-02-04', 'POSTED', 'ops01', 'OPS_CONSOLE', TIMESTAMPTZ '2026-02-04T00:00:00Z', 'Synthetic monthly fee')
                """.trimIndent(),
                emptyMap<String, Any?>()
            )
            jdbc.update(
                """
                INSERT INTO ledger_postings (
                  ledger_posting_id, ledger_transaction_id, account_id, currency, direction, amount_minor, posting_type
                )
                VALUES
                  ('LP-C360-OPEN-D', 'TX-C360-OPEN', 'BANK-SUSPENSE-C360', 'KRW', 'DEBIT', 100000, 'OPENING'),
                  ('LP-C360-OPEN-C', 'TX-C360-OPEN', 'ACC-C360-001', 'KRW', 'CREDIT', 100000, 'OPENING'),
                  ('LP-C360-FEE-D', 'TX-C360-FEE', 'ACC-C360-001', 'KRW', 'DEBIT', 3500, 'FEE'),
                  ('LP-C360-FEE-C', 'TX-C360-FEE', 'BANK-SUSPENSE-C360', 'KRW', 'CREDIT', 3500, 'FEE')
                """.trimIndent(),
                emptyMap<String, Any?>()
            )
            jdbc.update(
                """
                INSERT INTO account_balance_projections (
                  account_id, currency, ledger_balance_minor, available_balance_minor, hold_amount_minor
                )
                VALUES
                  ('BANK-SUSPENSE-C360', 'KRW', -96500, -96500, 0),
                  ('ACC-C360-001', 'KRW', 96500, 96500, 0),
                  ('ACC-C360-OTHER', 'KRW', 0, 0, 0)
                """.trimIndent(),
                emptyMap<String, Any?>()
            )
            jdbc.update(
                """
                INSERT INTO account_limits (
                  account_id, daily_transfer_limit_minor, single_transfer_limit_minor
                )
                VALUES
                  ('ACC-C360-001', 100000000, 10000000),
                  ('ACC-C360-OTHER', 100000000, 10000000)
                """.trimIndent(),
                emptyMap<String, Any?>()
            )
        }
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
