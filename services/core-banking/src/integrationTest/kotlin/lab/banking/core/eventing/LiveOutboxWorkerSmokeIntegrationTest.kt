package lab.banking.core.eventing

import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.TopicExistsException
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class LiveOutboxWorkerSmokeIntegrationTest {
    @Test
    fun `live outbox worker publishes pending event after Compose worker container restart`() {
        val composeProject = System.getenv("BANKING_LAB_LIVE_OUTBOX_COMPOSE_PROJECT")
        assumeTrue(
            !composeProject.isNullOrBlank(),
            "set BANKING_LAB_LIVE_OUTBOX_COMPOSE_PROJECT to run live outbox worker container drill"
        )

        val topic = System.getenv("BANKING_LAB_OUTBOX_TOPIC") ?: "banking.lab.domain-events"
        val databaseUrl = System.getenv("BANKING_LAB_LIVE_OUTBOX_DATABASE_URL")
            ?: "jdbc:postgresql://127.0.0.1:${System.getenv("BANKING_LAB_POSTGRES_PORT") ?: "5432"}/banking_lab"
        val databaseUser = System.getenv("BANKING_LAB_LIVE_OUTBOX_DATABASE_USER") ?: "banking_lab"
        val databasePassword = System.getenv("BANKING_LAB_LIVE_OUTBOX_DATABASE_PASSWORD") ?: "banking_lab"
        val bootstrapServers = System.getenv("BANKING_LAB_LIVE_OUTBOX_BOOTSTRAP_SERVERS")
            ?: "127.0.0.1:${System.getenv("BANKING_LAB_REDPANDA_PORT") ?: "9092"}"
        val workerService = System.getenv("BANKING_LAB_LIVE_OUTBOX_WORKER_SERVICE") ?: "core-banking-outbox-worker"
        val composeEnv = composeEnvironment(topic)
        val outboxEventId = "OBX-LIVE-${UUID.randomUUID().toString().uppercase()}"
        val aggregateId = "TX-LIVE-OUTBOX-${UUID.randomUUID().toString().uppercase()}"
        val idempotencyKey = "IDEMP-LIVE-OUTBOX-${UUID.randomUUID().toString().uppercase()}"

        waitForOutboxSchema(databaseUrl, databaseUser, databasePassword, Duration.ofSeconds(60))
        createTopic(bootstrapServers, topic)

        var workerKilled = false
        try {
            runDockerCompose(composeProject, composeEnv, "kill", workerService)
            workerKilled = true

            insertPendingOutboxEvent(
                databaseUrl = databaseUrl,
                databaseUser = databaseUser,
                databasePassword = databasePassword,
                outboxEventId = outboxEventId,
                aggregateId = aggregateId,
                idempotencyKey = idempotencyKey
            )
            assertEquals(
                "PENDING",
                outboxStatus(databaseUrl, databaseUser, databasePassword, outboxEventId)
            )

            runDockerCompose(composeProject, composeEnv, "up", "-d", "--no-deps", workerService)
            workerKilled = false

            waitForOutboxStatus(
                databaseUrl = databaseUrl,
                databaseUser = databaseUser,
                databasePassword = databasePassword,
                outboxEventId = outboxEventId,
                expectedStatus = "PUBLISHED",
                timeout = Duration.ofSeconds(90)
            )

            val publishedAt = outboxPublishedAt(databaseUrl, databaseUser, databasePassword, outboxEventId)
            assertNotNull(publishedAt)
            val kafkaValue = waitForKafkaRecords(
                bootstrapServers,
                topic,
                outboxEventId,
                expectedCount = 1,
                timeout = Duration.ofSeconds(60)
            ).first()
            assertTrue(kafkaValue.contains(outboxEventId))
            assertTrue(kafkaValue.contains("\"syntheticOnly\":true"))
        } finally {
            if (workerKilled) {
                runCatching { runDockerCompose(composeProject!!, composeEnv, "up", "-d", "--no-deps", workerService) }
            }
        }
    }

    @Test
    fun `live outbox worker replays after broker ack crash before published mark`() {
        val composeProject = System.getenv("BANKING_LAB_LIVE_OUTBOX_COMPOSE_PROJECT")
        assumeTrue(
            !composeProject.isNullOrBlank(),
            "set BANKING_LAB_LIVE_OUTBOX_COMPOSE_PROJECT to run live outbox post-ack crash drill"
        )

        val topic = System.getenv("BANKING_LAB_OUTBOX_TOPIC") ?: "banking.lab.domain-events"
        val databaseUrl = System.getenv("BANKING_LAB_LIVE_OUTBOX_DATABASE_URL")
            ?: "jdbc:postgresql://127.0.0.1:${System.getenv("BANKING_LAB_POSTGRES_PORT") ?: "5432"}/banking_lab"
        val databaseUser = System.getenv("BANKING_LAB_LIVE_OUTBOX_DATABASE_USER") ?: "banking_lab"
        val databasePassword = System.getenv("BANKING_LAB_LIVE_OUTBOX_DATABASE_PASSWORD") ?: "banking_lab"
        val bootstrapServers = System.getenv("BANKING_LAB_LIVE_OUTBOX_BOOTSTRAP_SERVERS")
            ?: "127.0.0.1:${System.getenv("BANKING_LAB_REDPANDA_PORT") ?: "9092"}"
        val workerService = System.getenv("BANKING_LAB_LIVE_OUTBOX_WORKER_SERVICE") ?: "core-banking-outbox-worker"
        val outboxEventId = "OBX-ACK-CRASH-${UUID.randomUUID().toString().uppercase()}"
        val aggregateId = "TX-LIVE-OUTBOX-ACK-CRASH-${UUID.randomUUID().toString().uppercase()}"
        val idempotencyKey = "IDEMP-LIVE-OUTBOX-ACK-CRASH-${UUID.randomUUID().toString().uppercase()}"
        val baseComposeEnv = composeEnvironment(topic)
        val faultComposeEnv = composeEnvironment(topic, crashAfterAckEventId = outboxEventId)

        waitForOutboxSchema(databaseUrl, databaseUser, databasePassword, Duration.ofSeconds(60))
        createTopic(bootstrapServers, topic)

        try {
            runDockerCompose(composeProject, baseComposeEnv, "kill", workerService)

            insertPendingOutboxEvent(
                databaseUrl = databaseUrl,
                databaseUser = databaseUser,
                databasePassword = databasePassword,
                outboxEventId = outboxEventId,
                aggregateId = aggregateId,
                idempotencyKey = idempotencyKey,
                drill = "compose-outbox-worker-post-ack-crash"
            )
            assertEquals(
                "PENDING",
                outboxStatus(databaseUrl, databaseUser, databasePassword, outboxEventId)
            )

            runDockerCompose(
                composeProject,
                faultComposeEnv,
                "up",
                "-d",
                "--force-recreate",
                "--no-deps",
                workerService
            )
            assertEquals(
                88,
                waitForWorkerContainerExit(composeProject, faultComposeEnv, workerService, Duration.ofSeconds(90))
            )
            assertEquals(
                "PENDING",
                outboxStatus(databaseUrl, databaseUser, databasePassword, outboxEventId)
            )
            assertNull(outboxPublishedAt(databaseUrl, databaseUser, databasePassword, outboxEventId))
            val firstBrokerRecord = waitForKafkaRecords(
                bootstrapServers,
                topic,
                outboxEventId,
                expectedCount = 1,
                timeout = Duration.ofSeconds(60)
            ).first()
            assertTrue(firstBrokerRecord.contains(outboxEventId))

            runDockerCompose(
                composeProject,
                baseComposeEnv,
                "up",
                "-d",
                "--force-recreate",
                "--no-deps",
                workerService
            )
            waitForOutboxStatus(
                databaseUrl = databaseUrl,
                databaseUser = databaseUser,
                databasePassword = databasePassword,
                outboxEventId = outboxEventId,
                expectedStatus = "PUBLISHED",
                timeout = Duration.ofSeconds(90)
            )
            assertNotNull(outboxPublishedAt(databaseUrl, databaseUser, databasePassword, outboxEventId))
            val replayedRecords = waitForKafkaRecords(
                bootstrapServers,
                topic,
                outboxEventId,
                expectedCount = 2,
                timeout = Duration.ofSeconds(60)
            )
            assertEquals(2, replayedRecords.size)
        } finally {
            runCatching {
                runDockerCompose(
                    composeProject!!,
                    baseComposeEnv,
                    "up",
                    "-d",
                    "--force-recreate",
                    "--no-deps",
                    workerService
                )
            }
        }
    }

    private fun waitForOutboxSchema(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        timeout: Duration
    ) {
        val deadline = System.nanoTime() + timeout.toNanos()
        var lastError: Throwable? = null
        while (System.nanoTime() < deadline) {
            try {
                connection(databaseUrl, databaseUser, databasePassword).use { conn ->
                    conn.prepareStatement("SELECT to_regclass('public.outbox_events')").use { stmt ->
                        stmt.executeQuery().use { rs ->
                            if (rs.next() && rs.getString(1) == "outbox_events") {
                                return
                            }
                        }
                    }
                }
            } catch (error: RuntimeException) {
                lastError = error
            }
            Thread.sleep(500)
        }
        fail<Unit>("outbox_events schema was not ready: ${lastError?.message}")
    }

    private fun insertPendingOutboxEvent(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        outboxEventId: String,
        aggregateId: String,
        idempotencyKey: String,
        drill: String = "compose-outbox-worker-restart"
    ) {
        connection(databaseUrl, databaseUser, databasePassword).use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO outbox_events (
                  outbox_event_id,
                  aggregate_type,
                  aggregate_id,
                  event_type,
                  idempotency_key,
                  payload_json,
                  headers_json,
                  status
                )
                VALUES (?, 'LedgerTransaction', ?, 'LedgerTransactionPosted', ?, CAST(? AS jsonb), CAST(? AS jsonb), 'PENDING')
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, outboxEventId)
                stmt.setString(2, aggregateId)
                stmt.setString(3, idempotencyKey)
                stmt.setString(
                    4,
                    """{"ledgerTransactionId":"$aggregateId","syntheticOnly":true,"drill":"$drill"}"""
                )
                stmt.setString(
                    5,
                    """{"syntheticOnly":true,"source":"live-compose-outbox-worker-drill"}"""
                )
                stmt.executeUpdate()
            }
        }
    }

    private fun waitForOutboxStatus(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        outboxEventId: String,
        expectedStatus: String,
        timeout: Duration
    ) {
        val deadline = System.nanoTime() + timeout.toNanos()
        var lastStatus: String? = null
        while (System.nanoTime() < deadline) {
            lastStatus = outboxStatus(databaseUrl, databaseUser, databasePassword, outboxEventId)
            if (lastStatus == expectedStatus) {
                return
            }
            Thread.sleep(500)
        }
        assertEquals(expectedStatus, lastStatus)
    }

    private fun outboxStatus(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        outboxEventId: String
    ): String? =
        queryOutboxValue(databaseUrl, databaseUser, databasePassword, outboxEventId, "status")

    private fun outboxPublishedAt(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        outboxEventId: String
    ): String? =
        queryOutboxValue(databaseUrl, databaseUser, databasePassword, outboxEventId, "published_at::text")

    private fun queryOutboxValue(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        outboxEventId: String,
        columnExpression: String
    ): String? =
        connection(databaseUrl, databaseUser, databasePassword).use { conn ->
            conn.prepareStatement("SELECT $columnExpression FROM outbox_events WHERE outbox_event_id = ?").use { stmt ->
                stmt.setString(1, outboxEventId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        rs.getString(1)
                    } else {
                        null
                    }
                }
            }
        }

    private fun createTopic(bootstrapServers: String, topic: String) {
        val props = Properties().apply {
            put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        }
        AdminClient.create(props).use { admin ->
            try {
                admin.createTopics(listOf(NewTopic(topic, 1, 1.toShort())))
                    .all()
                    .get(10, TimeUnit.SECONDS)
            } catch (error: Exception) {
                if (error.cause !is TopicExistsException) {
                    throw error
                }
            }
        }
    }

    private fun waitForKafkaRecords(
        bootstrapServers: String,
        topic: String,
        outboxEventId: String,
        expectedCount: Int,
        timeout: Duration
    ): List<String> {
        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ConsumerConfig.GROUP_ID_CONFIG, "live-outbox-worker-drill-${UUID.randomUUID()}")
            put(ConsumerConfig.CLIENT_ID_CONFIG, "live-outbox-worker-drill-consumer-${UUID.randomUUID()}")
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        }
        KafkaConsumer<String, String>(props).use { consumer ->
            consumer.subscribe(listOf(topic))
            val deadline = System.nanoTime() + timeout.toNanos()
            val matches = mutableListOf<String>()
            while (System.nanoTime() < deadline) {
                val records = consumer.poll(Duration.ofMillis(500))
                for (record in records) {
                    if (
                        record.value().contains(outboxEventId) ||
                        record.headers().lastHeader("outboxEventId")?.value()?.toString(Charsets.UTF_8) == outboxEventId
                    ) {
                        matches += record.value()
                    }
                }
                if (matches.size >= expectedCount) {
                    return matches
                }
            }
        }
        return fail("Expected $expectedCount Kafka records for $outboxEventId from $topic")
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

    private fun waitForWorkerContainerExit(
        composeProject: String,
        extraEnvironment: Map<String, String>,
        workerService: String,
        timeout: Duration
    ): Int {
        val containerId = runDockerComposeResult(
            composeProject = composeProject,
            extraEnvironment = extraEnvironment,
            args = listOf("ps", "-q", workerService),
            timeout = Duration.ofSeconds(10)
        ).output.trim()
        assertTrue(containerId.isNotBlank(), "container id for $workerService must exist")

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
        return fail("$workerService did not exit within ${timeout.seconds}s: $lastOutput")
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

    private fun composeEnvironment(topic: String, crashAfterAckEventId: String? = null): Map<String, String> =
        buildMap {
            put("BANKING_LAB_OUTBOX_TOPIC", topic)
            put("BANKING_LAB_OUTBOX_FAULT_CRASH_AFTER_ACK_EVENT_ID", crashAfterAckEventId ?: "")
            put("BANKING_LAB_OUTBOX_FAULT_CRASH_AFTER_ACK_EXIT_CODE", "88")
            copyEnvironmentIfPresent("BANKING_LAB_POSTGRES_PORT")
            copyEnvironmentIfPresent("BANKING_LAB_REDPANDA_PORT")
            copyEnvironmentIfPresent("BANKING_LAB_REDPANDA_ADMIN_PORT")
            copyEnvironmentIfPresent("BANKING_LAB_REDPANDA_ADVERTISE_HOST")
            copyEnvironmentIfPresent("BANKING_LAB_TRACING_ENABLED")
            copyEnvironmentIfPresent("BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED")
            copyEnvironmentIfPresent("BANKING_LAB_OTLP_TRACES_ENDPOINT")
        }

    private fun MutableMap<String, String>.copyEnvironmentIfPresent(name: String) {
        val value = System.getenv(name)
        if (!value.isNullOrBlank()) {
            put(name, value)
        }
    }

    private data class CommandResult(
        val exitCode: Int,
        val output: String
    )
}
