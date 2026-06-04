package lab.banking.core.card

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
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
class CardDomainIntegrationTest {
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
              card_captures,
              card_authorizations,
              card_3ds_simulations,
              card_limit_usage_counters,
              card_limits,
              cards,
              loan_interest_accruals,
              loan_payments,
              loan_repayment_schedule,
              loans,
              loan_applications,
              loan_products,
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
              customer_kyc_profiles,
              customers
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
        seedSyntheticCardFixtures()
    }

    @Test
    fun `card issue authorization capture cancellation limits loss and 3ds stay synthetic and ledger backed`() {
        mockMvc.perform(
            post("/api/cards")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-CARD-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(issueCardJson(panToken = "4111111111111111", key = "CARD-IT-RAW"))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("REQUEST_VALIDATION_FAILED"))
        assertEquals(0, countRows("cards"))

        val issued = mockMvc.perform(
            post("/api/cards")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-CARD-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(issueCardJson(panToken = "tok_pan_synthetic_001", key = "CARD-IT-ISSUE-001"))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.status").value("ACTIVE"))
            .andExpect(jsonPath("$.item.panToken").value("tok_pan_synthetic_001"))
            .andReturn()

        val cardId = objectMapper.readTree(issued.response.contentAsString)
            .path("item")
            .path("cardId")
            .asText()
        assertEquals(0, countRows("cards WHERE pan_token ~ '^[0-9]{12,19}$'"))

        mockMvc.perform(
            post("/api/cards/authorizations")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-CARD-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(authorizationJson(cardId, amount = 120_000, threeDs = null, key = "CARD-IT-AUTH-NO-3DS"))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("WORKFLOW_STATE_VIOLATION"))
        assertEquals(0, countRows("card_authorizations"))
        assertEquals(0, countRows("account_holds WHERE reason_code = 'CARD_AUTHORIZATION'"))

        val threeDs = mockMvc.perform(
            post("/api/cards/3ds-simulations")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-CARD-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardId":"$cardId","amountMinor":120000,"idempotencyKey":"CARD-IT-3DS-001"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
            .andReturn()
        val threeDsId = objectMapper.readTree(threeDs.response.contentAsString)
            .path("authenticationId")
            .asText()

        val authorization = mockMvc.perform(
            post("/api/cards/authorizations")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-CARD-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(authorizationJson(cardId, amount = 120_000, threeDs = threeDsId, key = "CARD-IT-AUTH-001"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("HELD"))
            .andReturn()
        val authorizationId = objectMapper.readTree(authorization.response.contentAsString)
            .path("item")
            .path("authorizationId")
            .asText()
        assertEquals(120_000, holdAmount("ACC-CARD-001"))
        assertEquals(380_000, availableBalance("ACC-CARD-001"))
        assertEquals(120_000, cardLimitUsed(cardId, "DAILY"))

        mockMvc.perform(
            post("/api/cards/authorizations")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-CARD-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(authorizationJson(cardId, amount = 90_000, threeDs = null, key = "CARD-IT-AUTH-LIMIT"))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("LIMIT_EXCEEDED"))
        assertEquals(1, countRows("card_authorizations"))

        val capture = mockMvc.perform(
            post("/api/cards/authorizations/$authorizationId/captures")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-CARD-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "idempotencyKey": "CARD-IT-CAP-001",
                      "requestedBy": "customer01",
                      "requestedChannel": "CARD_CAPTURE",
                      "reason": "Synthetic card capture"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ledgerTransaction.value.transactionType").value("CARD_CAPTURE"))
            .andExpect(jsonPath("$.item.status").value("POSTED"))
            .andReturn()
        val captureId = objectMapper.readTree(capture.response.contentAsString)
            .path("item")
            .path("captureId")
            .asText()
        val captureTransactionId = objectMapper.readTree(capture.response.contentAsString)
            .path("ledgerTransaction")
            .path("value")
            .path("id")
            .asText()
        assertEquals(0, signedTransactionSum(captureTransactionId))
        assertEquals(0, holdAmount("ACC-CARD-001"))
        assertEquals(380_000, availableBalance("ACC-CARD-001"))
        assertEquals(1, countRows("outbox_events WHERE event_type = 'CardCapturePosted'"))

        val reversal = mockMvc.perform(
            post("/api/cards/captures/$captureId/reverse")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-CARD-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "idempotencyKey": "CARD-IT-REV-001",
                      "requestedBy": "customer01",
                      "requestedChannel": "CARD_CAPTURE_REVERSAL",
                      "reason": "Synthetic card capture reversal"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ledgerTransaction.value.transactionType").value("REVERSAL"))
            .andExpect(jsonPath("$.item.status").value("REVERSED"))
            .andReturn()
        val reversalTransactionId = objectMapper.readTree(reversal.response.contentAsString)
            .path("ledgerTransaction")
            .path("value")
            .path("id")
            .asText()
        assertEquals(0, signedTransactionSum(reversalTransactionId))
        assertProjectionMatchesPostings("ACC-CARD-001")
        assertProjectionMatchesPostings("BANK-CARD-CLEARING")

        val cancellable = mockMvc.perform(
            post("/api/cards/authorizations")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-CARD-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(authorizationJson(cardId, amount = 50_000, threeDs = null, key = "CARD-IT-AUTH-CANCEL"))
        )
            .andExpect(status().isOk)
            .andReturn()
        val cancellableAuthorizationId = objectMapper.readTree(cancellable.response.contentAsString)
            .path("item")
            .path("authorizationId")
            .asText()
        mockMvc.perform(
            post("/api/cards/authorizations/$cancellableAuthorizationId/cancel")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-CARD-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idempotencyKey":"CARD-IT-CANCEL-001","requestedBy":"customer01","reason":"Synthetic auth cancel"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("CANCELLED"))
        mockMvc.perform(
            post("/api/cards/authorizations/$cancellableAuthorizationId/cancel")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-CARD-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idempotencyKey":"CARD-IT-CANCEL-001","requestedBy":"customer01","reason":"Synthetic auth cancel"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replayed").value(true))
        assertEquals(0, holdAmount("ACC-CARD-001"))

        mockMvc.perform(
            post("/api/cards/$cardId/loss-report")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-CARD-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"requestedBy":"customer01","requestedByRole":"CUSTOMER","reason":"Synthetic lost card report"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("LOST"))

        mockMvc.perform(
            post("/api/cards/authorizations")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-CARD-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(authorizationJson(cardId, amount = 10_000, threeDs = null, key = "CARD-IT-AUTH-LOST"))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("WORKFLOW_STATE_VIOLATION"))

        assertEquals(0, unbalancedTransactionCount())
        assertProjectionMatchesPostings("ACC-CARD-001")
        assertTrue(availableBalance("ACC-CARD-001") >= 0)
        assertTrue(countRows("audit_events WHERE event_type LIKE 'CARD_%'") >= 7)
    }

    private fun issueCardJson(panToken: String, key: String): String =
        """
        {
          "customerId": "SYN-CUS-CARD-001",
          "accountId": "ACC-CARD-001",
          "panToken": "$panToken",
          "panLast4": "4242",
          "dailyLimitMinor": 200000,
          "monthlyLimitMinor": 300000,
          "singleLimitMinor": 150000,
          "requestedBy": "customer01",
          "requestedByRole": "CUSTOMER",
          "reason": "Synthetic card issue",
          "idempotencyKey": "$key"
        }
        """.trimIndent()

    private fun authorizationJson(cardId: String, amount: Long, threeDs: String?, key: String): String =
        """
        {
          "cardId": "$cardId",
          "amountMinor": $amount,
          "merchantName": "Synthetic Merchant",
          "threeDsAuthenticationId": ${threeDs?.let { "\"$it\"" } ?: "null"},
          "requestedBy": "customer01",
          "requestedChannel": "CARD_AUTH",
          "reason": "Synthetic card authorization",
          "idempotencyKey": "$key"
        }
        """.trimIndent()

    private fun seedSyntheticCardFixtures() {
        TransactionTemplate(transactionManager).executeWithoutResult {
        jdbc.update(
            """
            INSERT INTO customers (customer_id, customer_name, customer_grade, risk_grade)
            VALUES
              ('BANK', 'Synthetic Bank', 'SYSTEM', 'LOW'),
              ('SYN-CUS-CARD-001', 'Synthetic Card Customer', 'STANDARD', 'LOW')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO accounts (account_id, customer_id, account_no, currency, status)
            VALUES
              ('BANK-SUSPENSE', 'BANK', 'LAB-000-000000', 'KRW', 'ACTIVE'),
              ('BANK-CARD-CLEARING', 'BANK', 'LAB-000-000002', 'KRW', 'ACTIVE'),
              ('ACC-CARD-001', 'SYN-CUS-CARD-001', 'LAB-080-000001', 'KRW', 'ACTIVE')
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
              'TX-CARD-OPENING', 'SYNTHETIC_OPENING_BALANCE', 'TX-CARD-OPENING',
              'SEED-TX-CARD-OPENING', DATE '2026-02-01', 'POSTED',
              'SEED', 'SYNTHETIC_DATA_GENERATOR', TIMESTAMPTZ '2026-02-01T00:00:00Z',
              'Synthetic card funding'
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
              ('LP-CARD-OPENING-D', 'TX-CARD-OPENING', 'BANK-SUSPENSE', 'KRW', 'DEBIT', 500000, 'OPENING'),
              ('LP-CARD-OPENING-C', 'TX-CARD-OPENING', 'ACC-CARD-001', 'KRW', 'CREDIT', 500000, 'OPENING')
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
              ('BANK-CARD-CLEARING', 'KRW', 0, 0, 0),
              ('ACC-CARD-001', 'KRW', 500000, 500000, 0)
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

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $tableExpression", emptyMap<String, Any?>(), Int::class.java) ?: 0

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

    private fun holdAmount(accountId: String): Long =
        jdbc.queryForObject(
            "SELECT hold_amount_minor FROM account_balance_projections WHERE account_id = :accountId",
            mapOf("accountId" to accountId),
            Long::class.java
        ) ?: 0L

    private fun availableBalance(accountId: String): Long =
        jdbc.queryForObject(
            "SELECT available_balance_minor FROM account_balance_projections WHERE account_id = :accountId",
            mapOf("accountId" to accountId),
            Long::class.java
        ) ?: 0L

    private fun cardLimitUsed(cardId: String, periodKind: String): Long =
        jdbc.queryForObject(
            "SELECT used_amount_minor FROM card_limit_usage_counters WHERE card_id = :cardId AND period_kind = :periodKind",
            mapOf("cardId" to cardId, "periodKind" to periodKind),
            Long::class.java
        ) ?: 0L

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
            "SELECT ledger_balance_minor FROM account_balance_projections WHERE account_id = :accountId",
            mapOf("accountId" to accountId),
            Long::class.java
        ) ?: 0L
        assertEquals(postingSum, projection, "projection mismatch for $accountId")
    }

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
