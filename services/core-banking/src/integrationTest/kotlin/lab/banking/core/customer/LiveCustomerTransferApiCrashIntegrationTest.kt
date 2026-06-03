package lab.banking.core.customer

import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class LiveCustomerTransferApiCrashIntegrationTest {
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build()
    private val objectMapper = ObjectMapper()

    @Test
    fun `live customer transfer replays after API crash following durable ledger outbox commit`() {
        val composeProject = System.getenv("BANKING_LAB_LIVE_API_CRASH_COMPOSE_PROJECT")
        assumeTrue(
            !composeProject.isNullOrBlank(),
            "set BANKING_LAB_LIVE_API_CRASH_COMPOSE_PROJECT to run live API crash drill"
        )

        val coreService = System.getenv("BANKING_LAB_LIVE_API_CRASH_CORE_SERVICE") ?: "core-banking"
        val baseUrl = System.getenv("BANKING_LAB_LIVE_API_CRASH_BASE_URL")
            ?: "http://127.0.0.1:${System.getenv("BANKING_LAB_CORE_BANKING_PORT") ?: "8081"}"
        val databaseUrl = System.getenv("BANKING_LAB_LIVE_API_CRASH_DATABASE_URL")
            ?: "jdbc:postgresql://127.0.0.1:${System.getenv("BANKING_LAB_POSTGRES_PORT") ?: "5432"}/banking_lab"
        val databaseUser = System.getenv("BANKING_LAB_LIVE_API_CRASH_DATABASE_USER") ?: "banking_lab"
        val databasePassword = System.getenv("BANKING_LAB_LIVE_API_CRASH_DATABASE_PASSWORD") ?: "banking_lab"
        val idempotencyKey = "CWB-API-CRASH-${UUID.randomUUID().toString().uppercase()}"
        val amountMinor = 4_321L
        val requestBody = transferJson(idempotencyKey, amountMinor)
        val faultComposeEnv = composeEnvironment(idempotencyKey)
        val baseComposeEnv = composeEnvironment(crashAfterCommitIdempotencyKey = null)

        try {
            runDockerCompose(
                composeProject = composeProject,
                extraEnvironment = faultComposeEnv,
                "up",
                "-d",
                "--force-recreate",
                "--no-deps",
                coreService
            )
            waitForHealth(baseUrl, Duration.ofSeconds(90))
            val fromBalanceBefore = balance(databaseUrl, databaseUser, databasePassword, "ACC-SYN-001-001")
            val toBalanceBefore = balance(databaseUrl, databaseUser, databasePassword, "ACC-SYN-002-001")

            val firstAttempt = runCatching {
                postTransfer(baseUrl, requestBody, Duration.ofSeconds(20))
            }
            assertTrue(
                firstAttempt.isFailure || firstAttempt.getOrThrow().statusCode() !in 200..299,
                "first request must not complete successfully because core-banking halts after commit"
            )
            assertEquals(
                89,
                waitForContainerExit(composeProject, faultComposeEnv, coreService, Duration.ofSeconds(90))
            )

            val committed = waitForCommittedTransfer(
                databaseUrl = databaseUrl,
                databaseUser = databaseUser,
                databasePassword = databasePassword,
                idempotencyKey = idempotencyKey,
                timeout = Duration.ofSeconds(60)
            )
            assertEquals("POSTED", committed.transferStatus)
            assertEquals("PENDING", committed.outboxStatus)
            assertNotNull(committed.ledgerTransactionId)
            assertEquals(1, countByIdempotencyKey(databaseUrl, databaseUser, databasePassword, "ledger_transactions", idempotencyKey))
            assertEquals(1, countByIdempotencyKey(databaseUrl, databaseUser, databasePassword, "customer_transfer_results", idempotencyKey))
            assertEquals(1, countByIdempotencyKey(databaseUrl, databaseUser, databasePassword, "outbox_events", idempotencyKey))
            assertEquals(fromBalanceBefore - amountMinor, balance(databaseUrl, databaseUser, databasePassword, "ACC-SYN-001-001"))
            assertEquals(toBalanceBefore + amountMinor, balance(databaseUrl, databaseUser, databasePassword, "ACC-SYN-002-001"))

            runDockerCompose(
                composeProject = composeProject,
                extraEnvironment = baseComposeEnv,
                "up",
                "-d",
                "--force-recreate",
                "--no-deps",
                coreService
            )
            waitForHealth(baseUrl, Duration.ofSeconds(90))

            val replay = postTransfer(baseUrl, requestBody, Duration.ofSeconds(20))
            assertEquals(200, replay.statusCode())
            val replayJson = objectMapper.readTree(replay.body())
            assertTrue(replayJson.path("replayed").asBoolean())
            assertEquals(committed.ledgerTransactionId, replayJson.path("item").path("transactionId").asText())
            assertEquals(1, countByIdempotencyKey(databaseUrl, databaseUser, databasePassword, "ledger_transactions", idempotencyKey))
            assertEquals(1, countByIdempotencyKey(databaseUrl, databaseUser, databasePassword, "customer_transfer_results", idempotencyKey))
            assertEquals(1, countByIdempotencyKey(databaseUrl, databaseUser, databasePassword, "outbox_events", idempotencyKey))
            assertEquals(fromBalanceBefore - amountMinor, balance(databaseUrl, databaseUser, databasePassword, "ACC-SYN-001-001"))
            assertEquals(toBalanceBefore + amountMinor, balance(databaseUrl, databaseUser, databasePassword, "ACC-SYN-002-001"))
        } finally {
            runCatching {
                runDockerCompose(
                    composeProject = composeProject!!,
                    extraEnvironment = baseComposeEnv,
                    "up",
                    "-d",
                    "--force-recreate",
                    "--no-deps",
                    coreService
                )
            }
        }
    }

    private fun waitForHealth(baseUrl: String, timeout: Duration) {
        val deadline = System.nanoTime() + timeout.toNanos()
        var lastError: Throwable? = null
        while (System.nanoTime() < deadline) {
            try {
                val request = HttpRequest.newBuilder(URI.create("$baseUrl/health"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() == 200 && response.body().contains("\"syntheticOnly\":true")) {
                    return
                }
            } catch (error: Exception) {
                lastError = error
            }
            Thread.sleep(500)
        }
        fail<Unit>("core-banking health was not ready at $baseUrl: ${lastError?.message}")
    }

    private fun postTransfer(baseUrl: String, body: String, timeout: Duration): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("$baseUrl/api/customer/transfers"))
            .timeout(timeout)
            .header("content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun waitForCommittedTransfer(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        idempotencyKey: String,
        timeout: Duration
    ): CommittedTransfer {
        val deadline = System.nanoTime() + timeout.toNanos()
        var last: CommittedTransfer? = null
        while (System.nanoTime() < deadline) {
            last = queryCommittedTransfer(databaseUrl, databaseUser, databasePassword, idempotencyKey)
            if (last?.ledgerTransactionId != null && last.outboxStatus == "PENDING") {
                return last
            }
            Thread.sleep(500)
        }
        return fail("durable customer transfer commit was not visible for $idempotencyKey: $last")
    }

    private fun queryCommittedTransfer(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        idempotencyKey: String
    ): CommittedTransfer? =
        connection(databaseUrl, databaseUser, databasePassword).use { conn ->
            conn.prepareStatement(
                """
                SELECT ctr.status, ctr.ledger_transaction_id, ob.status AS outbox_status
                FROM customer_transfer_results ctr
                LEFT JOIN outbox_events ob
                  ON ob.idempotency_key = ctr.idempotency_key
                 AND ob.aggregate_id = ctr.ledger_transaction_id
                WHERE ctr.idempotency_key = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, idempotencyKey)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) {
                        null
                    } else {
                        CommittedTransfer(
                            transferStatus = rs.getString("status"),
                            ledgerTransactionId = rs.getString("ledger_transaction_id"),
                            outboxStatus = rs.getString("outbox_status")
                        )
                    }
                }
            }
        }

    private fun balance(databaseUrl: String, databaseUser: String, databasePassword: String, accountId: String): Long =
        connection(databaseUrl, databaseUser, databasePassword).use { conn ->
            conn.prepareStatement(
                """
                SELECT available_balance_minor
                FROM account_balance_projections
                WHERE account_id = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, accountId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getLong(1) else fail("balance not found for $accountId")
                }
            }
        }

    private fun countByIdempotencyKey(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        tableName: String,
        idempotencyKey: String
    ): Int {
        require(tableName in setOf("ledger_transactions", "customer_transfer_results", "outbox_events"))
        return connection(databaseUrl, databaseUser, databasePassword).use { conn ->
            conn.prepareStatement("SELECT count(*) FROM $tableName WHERE idempotency_key = ?").use { stmt ->
                stmt.setString(1, idempotencyKey)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }
    }

    private fun waitForContainerExit(
        composeProject: String,
        extraEnvironment: Map<String, String>,
        service: String,
        timeout: Duration
    ): Int {
        val containerId = runDockerComposeResult(
            composeProject = composeProject,
            extraEnvironment = extraEnvironment,
            args = listOf("ps", "-q", service),
            timeout = Duration.ofSeconds(10)
        ).output.trim()
        assertTrue(containerId.isNotBlank(), "container id for $service must exist")

        val deadline = System.nanoTime() + timeout.toNanos()
        var lastOutput = ""
        while (System.nanoTime() < deadline) {
            val result = runDockerCommandResult(
                listOf("inspect", "--format", "{{.State.Status}} {{.State.ExitCode}}", containerId),
                timeout = Duration.ofSeconds(5)
            )
            lastOutput = result.output.trim()
            val parts = lastOutput.split(" ")
            if (result.exitCode == 0 && parts.size >= 2 && parts[0] == "exited") {
                return parts[1].toInt()
            }
            Thread.sleep(500)
        }
        return fail("$service did not exit within ${timeout.seconds}s: $lastOutput")
    }

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

    private fun runDockerCommandResult(args: List<String>, timeout: Duration): CommandResult {
        val process = ProcessBuilder(listOf("docker") + args)
            .directory(java.nio.file.Paths.get(System.getProperty("user.dir")).toFile())
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            return CommandResult(
                exitCode = -1,
                output = "docker ${args.joinToString(" ")} timed out after ${timeout.seconds}s"
            )
        }
        val output = process.inputStream.bufferedReader().readText()
        return CommandResult(process.exitValue(), output)
    }

    private fun composeEnvironment(crashAfterCommitIdempotencyKey: String?): Map<String, String> =
        buildMap {
            put("BANKING_LAB_SYNTHETIC_SEED_ENABLED", "true")
            put("BANKING_LAB_SECURITY_ENABLED", "false")
            put("BANKING_LAB_TRACING_ENABLED", "false")
            put("BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED", "false")
            put("BANKING_LAB_CUSTOMER_TRANSFER_FAULT_CRASH_AFTER_COMMIT_IDEMPOTENCY_KEY", crashAfterCommitIdempotencyKey ?: "")
            put("BANKING_LAB_CUSTOMER_TRANSFER_FAULT_CRASH_AFTER_COMMIT_EXIT_CODE", "89")
            copyEnvironmentIfPresent("BANKING_LAB_POSTGRES_PORT")
            copyEnvironmentIfPresent("BANKING_LAB_CORE_BANKING_PORT")
        }

    private fun MutableMap<String, String>.copyEnvironmentIfPresent(name: String) {
        val value = System.getenv(name)
        if (!value.isNullOrBlank()) {
            put(name, value)
        }
    }

    private fun transferJson(idempotencyKey: String, amountMinor: Long): String =
        """
        {
          "customerId": "SYN-CUS-001",
          "fromAccountId": "ACC-SYN-001-001",
          "toAccountId": "ACC-SYN-002-001",
          "amountMinor": $amountMinor,
          "idempotencyKey": "$idempotencyKey",
          "requestedBy": "SYN-CUS-001",
          "reason": "Synthetic API crash after durable ledger outbox commit"
        }
        """.trimIndent()

    private fun connection(databaseUrl: String, databaseUser: String, databasePassword: String): Connection =
        DriverManager.getConnection(databaseUrl, databaseUser, databasePassword)

    private data class CommittedTransfer(
        val transferStatus: String,
        val ledgerTransactionId: String?,
        val outboxStatus: String?
    )

    private data class CommandResult(
        val exitCode: Int,
        val output: String
    )
}
