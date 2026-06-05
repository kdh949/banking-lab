package lab.banking.payment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class LivePaymentOutboxWorkerComposeSmokeIntegrationTest {
    private val objectMapper = jacksonObjectMapper()
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    @Test
    fun `live payment outbox worker posts ledger settlement through Compose core banking`() {
        val composeProject = System.getenv("BANKING_LAB_LIVE_PAYMENT_OUTBOX_WORKER_COMPOSE_PROJECT").orEmpty()
        assumeTrue(
            composeProject.isNotBlank(),
            "set BANKING_LAB_LIVE_PAYMENT_OUTBOX_WORKER_COMPOSE_PROJECT to run live payment outbox worker drill"
        )

        val databaseUrl = System.getenv("BANKING_LAB_LIVE_PAYMENT_OUTBOX_WORKER_DATABASE_URL")
            ?: "jdbc:postgresql://127.0.0.1:${System.getenv("BANKING_LAB_POSTGRES_PORT") ?: "5432"}/banking_lab"
        val databaseUser = System.getenv("BANKING_LAB_LIVE_PAYMENT_OUTBOX_WORKER_DATABASE_USER") ?: "banking_lab"
        val databasePassword = System.getenv("BANKING_LAB_LIVE_PAYMENT_OUTBOX_WORKER_DATABASE_PASSWORD") ?: "banking_lab"
        val coreBaseUrl = System.getenv("BANKING_LAB_LIVE_PAYMENT_OUTBOX_WORKER_CORE_URL")
            ?: "http://127.0.0.1:${System.getenv("BANKING_LAB_CORE_BANKING_PORT") ?: "8081"}"
        val paymentBaseUrl = System.getenv("BANKING_LAB_LIVE_PAYMENT_OUTBOX_WORKER_PAYMENT_URL")
            ?: "http://127.0.0.1:${System.getenv("BANKING_LAB_PAYMENT_SERVICE_PORT") ?: "8088"}"
        val workerService = System.getenv("BANKING_LAB_LIVE_PAYMENT_OUTBOX_WORKER_SERVICE")
            ?: "payment-outbox-worker"
        val composeEnv = composeEnvironment()

        val suffix = UUID.randomUUID().toString().uppercase()
        val customerId = "CUS-LIVE-PAY-WORKER-${suffix.take(12)}"
        val accountId = "ACC-LIVE-PAY-WORKER-${suffix.take(12)}"
        val accountNo = "LAB-LIVE-${suffix.take(12)}"
        val depositIdempotencyKey = "IDEMP-LIVE-DEPOSIT-$suffix"
        val paymentIdempotencyKey = "IDEMP-LIVE-PAYMENT-$suffix"
        val paymentAmountMinor = 30_000L
        val startingBalanceMinor = 100_000L

        waitForSchemas(databaseUrl, databaseUser, databasePassword, Duration.ofSeconds(120))
        waitForHttpOk("$coreBaseUrl/health", Duration.ofSeconds(120))
        waitForHttpOk("$paymentBaseUrl/actuator/health", Duration.ofSeconds(120))

        var workerKilled = false
        try {
            runDockerCompose(composeProject, composeEnv, "kill", workerService)
            workerKilled = true

            seedCoreCustomerAccount(
                databaseUrl = databaseUrl,
                databaseUser = databaseUser,
                databasePassword = databasePassword,
                customerId = customerId,
                accountId = accountId,
                accountNo = accountNo
            )
            val deposit = postJson(
                url = "$coreBaseUrl/api/ledger/deposits",
                token = simulatorToken("live-core-ops", listOf("OPS_OPERATOR")),
                body = mapOf(
                    "accountId" to accountId,
                    "amountMinor" to startingBalanceMinor,
                    "idempotencyKey" to depositIdempotencyKey,
                    "requestedBy" to "live-compose-core-ops",
                    "requestedChannel" to "CORE_BANKING",
                    "reason" to "Synthetic live payment outbox worker funding"
                ),
                expectedStatuses = setOf(201)
            )
            assertEquals("DEPOSIT", deposit.path("value").path("transactionType").asText(), deposit.toPrettyString())
            assertTrue(deposit.path("value").path("id").asText().startsWith("TX-"), deposit.toPrettyString())

            val payment = postJson(
                url = "$paymentBaseUrl/api/payments/instructions",
                token = simulatorToken("live-payment-customer", listOf("CUSTOMER"), customerId),
                body = mapOf(
                    "customerId" to customerId,
                    "debitAccountId" to accountId,
                    "billerId" to "SYN-BILLER-UTIL-001",
                    "amountMinor" to paymentAmountMinor,
                    "currency" to "KRW",
                    "idempotencyKey" to paymentIdempotencyKey,
                    "requestedBy" to "live-payment-customer",
                    "requestedChannel" to "CUSTOMER_WEB",
                    "reason" to "Synthetic live payment outbox worker payment"
                ),
                expectedStatuses = setOf(201)
            )
            val paymentInstructionId = payment.path("item").path("paymentInstructionId").asText()
            val outboxEventId = payment.path("item").path("lastOutboxEventId").asText()
            assertTrue(paymentInstructionId.startsWith("PAY-"), payment.toPrettyString())
            assertTrue(outboxEventId.startsWith("POB-"), payment.toPrettyString())
            assertEquals("POSTING_REQUESTED", payment.path("item").path("status").asText(), payment.toPrettyString())
            assertEquals(
                "PaymentLedgerPostingRequested",
                paymentOutboxEventType(databaseUrl, databaseUser, databasePassword, outboxEventId)
            )
            assertEquals("PENDING", paymentOutboxStatus(databaseUrl, databaseUser, databasePassword, outboxEventId))
            assertEquals("POSTING_REQUESTED", paymentInstructionStatus(databaseUrl, databaseUser, databasePassword, paymentInstructionId))
            assertEquals(null, paymentInstructionLedgerTransactionId(databaseUrl, databaseUser, databasePassword, paymentInstructionId))

            runDockerCompose(
                composeProject,
                composeEnv,
                "up",
                "-d",
                "--force-recreate",
                "--no-deps",
                workerService
            )
            workerKilled = false

            val ledgerTransactionId = waitForPaymentSettlement(
                databaseUrl = databaseUrl,
                databaseUser = databaseUser,
                databasePassword = databasePassword,
                paymentInstructionId = paymentInstructionId,
                timeout = Duration.ofSeconds(150)
            )
            assertEquals("PUBLISHED", paymentOutboxStatus(databaseUrl, databaseUser, databasePassword, outboxEventId))
            assertEquals(0, paymentOutboxRetryCount(databaseUrl, databaseUser, databasePassword, outboxEventId))

            val transaction = coreTransaction(
                databaseUrl = databaseUrl,
                databaseUser = databaseUser,
                databasePassword = databasePassword,
                ledgerTransactionId = ledgerTransactionId
            )
            assertEquals("BILL_PAYMENT", transaction.transactionType)
            assertEquals(paymentInstructionId, transaction.businessReferenceId)
            assertEquals("PAYMENT_SERVICE", transaction.requestedChannel)
            assertEquals(2, countPaymentPostings(databaseUrl, databaseUser, databasePassword, ledgerTransactionId))
            assertEquals(
                1,
                countSpecificPosting(
                    databaseUrl,
                    databaseUser,
                    databasePassword,
                    ledgerTransactionId,
                    accountId,
                    "DEBIT",
                    paymentAmountMinor
                )
            )
            assertEquals(
                1,
                countSpecificPosting(
                    databaseUrl,
                    databaseUser,
                    databasePassword,
                    ledgerTransactionId,
                    "BANK-SETTLEMENT",
                    "CREDIT",
                    paymentAmountMinor
                )
            )
            assertEquals(
                startingBalanceMinor - paymentAmountMinor,
                availableBalance(databaseUrl, databaseUser, databasePassword, accountId)
            )
            assertEquals(paymentAmountMinor, availableBalance(databaseUrl, databaseUser, databasePassword, "BANK-SETTLEMENT"))
            assertEquals(
                1,
                countCoreOutboxEvents(
                    databaseUrl,
                    databaseUser,
                    databasePassword,
                    ledgerTransactionId,
                    "PaymentLedgerPostingSettled"
                )
            )
        } finally {
            if (workerKilled) {
                runCatching { runDockerCompose(composeProject, composeEnv, "up", "-d", "--no-deps", workerService) }
            }
        }
    }

    private fun waitForSchemas(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        timeout: Duration
    ) {
        val requiredTables = listOf("ledger_transactions", "ledger_postings", "payment_outbox_events", "payment_billers")
        val deadline = System.nanoTime() + timeout.toNanos()
        var lastError: Throwable? = null
        var missingTables = requiredTables
        while (System.nanoTime() < deadline) {
            try {
                connection(databaseUrl, databaseUser, databasePassword).use { conn ->
                    missingTables = requiredTables.filter { tableName ->
                        conn.prepareStatement("SELECT to_regclass(?)").use { stmt ->
                            stmt.setString(1, "public.$tableName")
                            stmt.executeQuery().use { rs ->
                                !(rs.next() && rs.getString(1) != null)
                            }
                        }
                    }
                    if (missingTables.isEmpty()) {
                        return
                    }
                }
            } catch (error: Exception) {
                lastError = error
            }
            Thread.sleep(500)
        }
        fail<Unit>(
            "live payment outbox worker schemas were not ready: missing=${missingTables.joinToString()} error=${lastError?.message}"
        )
    }

    private fun waitForHttpOk(url: String, timeout: Duration) {
        val deadline = System.nanoTime() + timeout.toNanos()
        var lastStatus: Int? = null
        var lastError: Throwable? = null
        while (System.nanoTime() < deadline) {
            try {
                val response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString()
                )
                lastStatus = response.statusCode()
                if (response.statusCode() in 200..299) {
                    return
                }
            } catch (error: Exception) {
                lastError = error
            }
            Thread.sleep(500)
        }
        fail<Unit>("HTTP endpoint $url was not ready: status=$lastStatus error=${lastError?.message}")
    }

    private fun seedCoreCustomerAccount(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        customerId: String,
        accountId: String,
        accountNo: String
    ) {
        connection(databaseUrl, databaseUser, databasePassword).use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO customers (customer_id, customer_name, customer_grade, risk_grade)
                VALUES (?, ?, 'STANDARD', 'LOW')
                ON CONFLICT (customer_id) DO NOTHING
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, customerId)
                stmt.setString(2, "Synthetic $customerId")
                stmt.executeUpdate()
            }
            conn.prepareStatement(
                """
                INSERT INTO accounts (account_id, customer_id, account_no, currency, status)
                VALUES (?, ?, ?, 'KRW', 'ACTIVE')
                ON CONFLICT (account_id) DO NOTHING
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, accountId)
                stmt.setString(2, customerId)
                stmt.setString(3, accountNo)
                stmt.executeUpdate()
            }
        }
    }

    private fun postJson(
        url: String,
        token: String,
        body: Map<String, Any?>,
        expectedStatuses: Set<Int>
    ): JsonNode {
        val response = httpClient.send(
            HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        if (response.statusCode() !in expectedStatuses) {
            fail<Unit>("POST $url returned ${response.statusCode()}:\n${response.body()}")
        }
        return objectMapper.readTree(response.body())
    }

    private fun waitForPaymentSettlement(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        paymentInstructionId: String,
        timeout: Duration
    ): String {
        val deadline = System.nanoTime() + timeout.toNanos()
        var lastStatus: String? = null
        var lastLedgerTransactionId: String? = null
        while (System.nanoTime() < deadline) {
            lastStatus = paymentInstructionStatus(databaseUrl, databaseUser, databasePassword, paymentInstructionId)
            lastLedgerTransactionId = paymentInstructionLedgerTransactionId(
                databaseUrl,
                databaseUser,
                databasePassword,
                paymentInstructionId
            )
            if (lastStatus == "SETTLED" && !lastLedgerTransactionId.isNullOrBlank()) {
                return lastLedgerTransactionId
            }
            Thread.sleep(500)
        }
        return fail("payment instruction $paymentInstructionId was not settled: status=$lastStatus ledger=$lastLedgerTransactionId")
    }

    private fun paymentOutboxStatus(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        outboxEventId: String
    ): String? =
        queryString(
            databaseUrl,
            databaseUser,
            databasePassword,
            "SELECT status FROM payment_outbox_events WHERE outbox_event_id = ?",
            outboxEventId
        )

    private fun paymentOutboxEventType(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        outboxEventId: String
    ): String? =
        queryString(
            databaseUrl,
            databaseUser,
            databasePassword,
            "SELECT event_type FROM payment_outbox_events WHERE outbox_event_id = ?",
            outboxEventId
        )

    private fun paymentOutboxRetryCount(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        outboxEventId: String
    ): Int =
        queryInt(
            databaseUrl,
            databaseUser,
            databasePassword,
            "SELECT retry_count FROM payment_outbox_events WHERE outbox_event_id = ?",
            outboxEventId
        )

    private fun paymentInstructionStatus(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        paymentInstructionId: String
    ): String? =
        queryString(
            databaseUrl,
            databaseUser,
            databasePassword,
            "SELECT status FROM payment_instructions WHERE payment_instruction_id = ?",
            paymentInstructionId
        )

    private fun paymentInstructionLedgerTransactionId(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        paymentInstructionId: String
    ): String? =
        queryString(
            databaseUrl,
            databaseUser,
            databasePassword,
            "SELECT ledger_transaction_id FROM payment_instructions WHERE payment_instruction_id = ?",
            paymentInstructionId
        )

    private fun coreTransaction(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        ledgerTransactionId: String
    ): CoreTransactionSnapshot =
        connection(databaseUrl, databaseUser, databasePassword).use { conn ->
            conn.prepareStatement(
                """
                SELECT transaction_type, business_reference_id, requested_channel
                FROM ledger_transactions
                WHERE ledger_transaction_id = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, ledgerTransactionId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        CoreTransactionSnapshot(
                            transactionType = rs.getString("transaction_type"),
                            businessReferenceId = rs.getString("business_reference_id"),
                            requestedChannel = rs.getString("requested_channel")
                        )
                    } else {
                        fail("core ledger transaction $ledgerTransactionId was not found")
                    }
                }
            }
        }

    private fun countPaymentPostings(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        ledgerTransactionId: String
    ): Int =
        queryInt(
            databaseUrl,
            databaseUser,
            databasePassword,
            """
            SELECT count(*)
            FROM ledger_postings
            WHERE ledger_transaction_id = ?
              AND posting_type = 'PAYMENT'
            """.trimIndent(),
            ledgerTransactionId
        )

    private fun countSpecificPosting(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        ledgerTransactionId: String,
        accountId: String,
        direction: String,
        amountMinor: Long
    ): Int =
        connection(databaseUrl, databaseUser, databasePassword).use { conn ->
            conn.prepareStatement(
                """
                SELECT count(*)
                FROM ledger_postings
                WHERE ledger_transaction_id = ?
                  AND account_id = ?
                  AND direction = ?
                  AND amount_minor = ?
                  AND posting_type = 'PAYMENT'
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, ledgerTransactionId)
                stmt.setString(2, accountId)
                stmt.setString(3, direction)
                stmt.setLong(4, amountMinor)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }

    private fun availableBalance(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        accountId: String
    ): Long =
        queryLong(
            databaseUrl,
            databaseUser,
            databasePassword,
            "SELECT available_balance_minor FROM account_balance_projections WHERE account_id = ?",
            accountId
        )

    private fun countCoreOutboxEvents(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        ledgerTransactionId: String,
        eventType: String
    ): Int =
        connection(databaseUrl, databaseUser, databasePassword).use { conn ->
            conn.prepareStatement(
                """
                SELECT count(*)
                FROM outbox_events
                WHERE aggregate_id = ?
                  AND event_type = ?
                  AND status = 'PENDING'
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, ledgerTransactionId)
                stmt.setString(2, eventType)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }

    private fun queryString(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        sql: String,
        value: String
    ): String? =
        connection(databaseUrl, databaseUser, databasePassword).use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, value)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        rs.getString(1)
                    } else {
                        null
                    }
                }
            }
        }

    private fun queryInt(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        sql: String,
        value: String
    ): Int =
        connection(databaseUrl, databaseUser, databasePassword).use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, value)
                stmt.executeQuery().use { rs ->
                    assertTrue(rs.next())
                    rs.getInt(1)
                }
            }
        }

    private fun queryLong(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        sql: String,
        value: String
    ): Long =
        connection(databaseUrl, databaseUser, databasePassword).use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, value)
                stmt.executeQuery().use { rs ->
                    assertTrue(rs.next())
                    rs.getLong(1)
                }
            }
        }

    private fun simulatorToken(subject: String, roles: List<String>, customerId: String? = null): String {
        val now = Instant.now().epochSecond
        val payload = linkedMapOf<String, Any?>(
            "iss" to "http://keycloak.local/realms/banking-lab",
            "sub" to subject,
            "roles" to roles,
            "customerId" to customerId,
            "active" to true,
            "iat" to now,
            "auth_time" to now
        )
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(objectMapper.writeValueAsBytes(payload))
        return "lab.$encoded.sig"
    }

    private fun connection(databaseUrl: String, databaseUser: String, databasePassword: String): Connection =
        DriverManager.getConnection(databaseUrl, databaseUser, databasePassword)

    private fun runDockerCompose(composeProject: String, extraEnvironment: Map<String, String>, vararg args: String) {
        val result = runDockerComposeResult(composeProject, extraEnvironment, args.toList())
        if (result.exitCode != 0) {
            fail<Unit>(
                "docker compose ${args.joinToString(" ")} failed with exit ${result.exitCode}\n${result.output}"
            )
        }
    }

    private fun runDockerComposeResult(
        composeProject: String,
        extraEnvironment: Map<String, String>,
        args: List<String>,
        timeout: Duration = Duration.ofSeconds(90)
    ): CommandResult {
        val process = ProcessBuilder(listOf("docker", "compose") + args)
            .directory(java.nio.file.Paths.get(System.getProperty("user.dir")).toFile())
            .redirectErrorStream(true)
            .also {
                it.environment()["COMPOSE_PROJECT_NAME"] = composeProject
                it.environment().putAll(extraEnvironment)
            }
            .start()
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            return CommandResult(
                exitCode = -1,
                output = "docker compose ${args.joinToString(" ")} timed out after ${timeout.seconds}s"
            )
        }
        val output = process.inputStream.bufferedReader().readText()
        return CommandResult(process.exitValue(), output)
    }

    private fun composeEnvironment(): Map<String, String> =
        buildMap {
            putOrCopyEnvironment("BANKING_LAB_TRACING_ENABLED", "false")
            putOrCopyEnvironment("BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED", "false")
            copyEnvironmentIfPresent("BANKING_LAB_POSTGRES_PORT")
            copyEnvironmentIfPresent("BANKING_LAB_CORE_BANKING_PORT")
            copyEnvironmentIfPresent("BANKING_LAB_PAYMENT_SERVICE_PORT")
            copyEnvironmentIfPresent("BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED")
            copyEnvironmentIfPresent("BANKING_LAB_DEV_SIMULATOR_TOKEN")
            copyEnvironmentIfPresent("BANKING_LAB_PAYMENT_CORE_BANKING_SERVICE_TOKEN")
        }

    private fun MutableMap<String, String>.putOrCopyEnvironment(name: String, value: String?) {
        if (value != null) {
            put(name, value)
        } else {
            copyEnvironmentIfPresent(name)
        }
    }

    private fun MutableMap<String, String>.copyEnvironmentIfPresent(name: String) {
        val value = System.getenv(name)
        if (!value.isNullOrBlank()) {
            put(name, value)
        }
    }

    private data class CoreTransactionSnapshot(
        val transactionType: String,
        val businessReferenceId: String,
        val requestedChannel: String
    )

    private data class CommandResult(
        val exitCode: Int,
        val output: String
    )
}
