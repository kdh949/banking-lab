package lab.banking.notification

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit
import lab.banking.notification.eventing.NotificationOutboxKafkaEnvelope
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.TopicExistsException
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class LiveNotificationConsumerComposeSmokeIntegrationTest {
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @Test
    fun `live notification event consumer writes masked delivery from Compose Redpanda record`() {
        val composeProject = System.getenv("BANKING_LAB_LIVE_NOTIFICATION_COMPOSE_PROJECT")
        assumeTrue(
            !composeProject.isNullOrBlank(),
            "set BANKING_LAB_LIVE_NOTIFICATION_COMPOSE_PROJECT to run live notification consumer drill"
        )

        val topic = System.getenv("BANKING_LAB_NOTIFICATION_EVENT_CONSUMER_TOPIC")
            ?: "banking.lab.domain-events"
        val databaseUrl = System.getenv("BANKING_LAB_LIVE_NOTIFICATION_DATABASE_URL")
            ?: "jdbc:postgresql://127.0.0.1:${System.getenv("BANKING_LAB_POSTGRES_PORT") ?: "5432"}/banking_lab"
        val databaseUser = System.getenv("BANKING_LAB_LIVE_NOTIFICATION_DATABASE_USER") ?: "banking_lab"
        val databasePassword = System.getenv("BANKING_LAB_LIVE_NOTIFICATION_DATABASE_PASSWORD") ?: "banking_lab"
        val bootstrapServers = System.getenv("BANKING_LAB_LIVE_NOTIFICATION_BOOTSTRAP_SERVERS")
            ?: "127.0.0.1:${System.getenv("BANKING_LAB_REDPANDA_PORT") ?: "9092"}"
        val consumerService = System.getenv("BANKING_LAB_LIVE_NOTIFICATION_CONSUMER_SERVICE")
            ?: "notification-event-consumer"
        val sourceEventId = "OBX-LIVE-NOTIF-${UUID.randomUUID().toString().uppercase()}"

        waitForNotificationSchema(databaseUrl, databaseUser, databasePassword, Duration.ofSeconds(90))
        createTopic(bootstrapServers, topic)

        val envelope = paymentEnvelope(sourceEventId)
        produce(bootstrapServers, topic, envelope)
        produce(bootstrapServers, topic, envelope)

        waitForDeliveryCount(
            databaseUrl = databaseUrl,
            databaseUser = databaseUser,
            databasePassword = databasePassword,
            sourceEventId = sourceEventId,
            expectedCount = 1,
            timeout = Duration.ofSeconds(120)
        )

        assertEquals(1, countRows(databaseUrl, databaseUser, databasePassword, "notification_inbox_events", sourceEventId))
        assertEquals(
            1,
            countRows(databaseUrl, databaseUser, databasePassword, "notification_delivery_requests", sourceEventId)
        )
        assertEquals(
            1,
            countAttempts(databaseUrl, databaseUser, databasePassword, sourceEventId, "PENDING")
        )
        assertEquals(
            "notification-compose-event-consumer",
            singleString(
                databaseUrl,
                databaseUser,
                databasePassword,
                """
                SELECT a.requested_by
                FROM notification_delivery_attempts a
                JOIN notification_delivery_requests r
                  ON r.delivery_request_id = a.delivery_request_id
                WHERE r.source_event_id = ?
                ORDER BY a.requested_at DESC
                LIMIT 1
                """.trimIndent(),
                sourceEventId
            )
        )

        val maskedMessage = singleString(
            databaseUrl,
            databaseUser,
            databasePassword,
            "SELECT masked_message FROM notification_delivery_requests WHERE source_event_id = ?",
            sourceEventId
        )
        val maskedPayload = singleString(
            databaseUrl,
            databaseUser,
            databasePassword,
            "SELECT masked_payload_json::text FROM notification_delivery_requests WHERE source_event_id = ?",
            sourceEventId
        )
        assertTrue(maskedMessage.contains("ACC-***7777"), maskedMessage)
        assertFalse(maskedMessage.contains("ACC-LIVE-7777"), maskedMessage)
        assertFalse(maskedPayload.contains("ACC-LIVE-7777"), maskedPayload)
        assertFalse(maskedPayload.contains("010-9999-7777"), maskedPayload)

        val logLine = waitForConsumerBatchLog(
            composeProject = composeProject!!,
            consumerService = consumerService,
            timeout = Duration.ofSeconds(60)
        )
        assertTrue(logLine.contains("observability.notification.consumer"), logLine)
        assertTrue(logLine.contains("event=batch"), logLine)
        assertTrue(logLine.contains("processed=1"), logLine)
        assertTrue(logLine.contains("duplicates=1"), logLine)
        assertTrue(logLine.contains("deliveries=1"), logLine)
        assertTrue(logLine.contains("syntheticOnly=true"), logLine)
    }

    private fun paymentEnvelope(outboxEventId: String): NotificationOutboxKafkaEnvelope =
        NotificationOutboxKafkaEnvelope(
            outboxEventId = outboxEventId,
            aggregateType = "PaymentInstruction",
            aggregateId = "PAY-LIVE-NOTIF-001",
            eventType = "PaymentLedgerPostingRequested",
            occurredAt = "2026-06-06T00:00:00Z",
            idempotencyKey = "IDEMP-LIVE-NOTIF-${UUID.randomUUID().toString().uppercase()}",
            payload = mapOf(
                "contractVersion" to "2026-06-05",
                "paymentInstructionId" to "PAY-LIVE-NOTIF-001",
                "customerId" to "CUS-LIVE-NOTIF-001",
                "debitAccountId" to "ACC-LIVE-7777",
                "amountMinor" to 77_000,
                "currency" to "KRW",
                "phone" to "010-9999-7777",
                "syntheticOnly" to true
            ),
            headers = mapOf(
                "syntheticOnly" to true,
                "sourceService" to "payment-service",
                "eventType" to "PaymentLedgerPostingRequested",
                "aggregateId" to "PAY-LIVE-NOTIF-001",
                "occurredAt" to "2026-06-06T00:00:00Z"
            )
        )

    private fun waitForNotificationSchema(
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
                    conn.prepareStatement("SELECT to_regclass('public.notification_delivery_requests')").use { stmt ->
                        stmt.executeQuery().use { rs ->
                            if (rs.next() && rs.getString(1) == "notification_delivery_requests") {
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
        fail<Unit>("notification schema was not ready: ${lastError?.message}")
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

    private fun produce(
        bootstrapServers: String,
        topic: String,
        envelope: NotificationOutboxKafkaEnvelope
    ) {
        KafkaProducer<String, String>(producerProperties(bootstrapServers)).use { producer ->
            producer.send(
                ProducerRecord(
                    topic,
                    envelope.aggregateId,
                    objectMapper.writeValueAsString(envelope)
                )
            ).get(10, TimeUnit.SECONDS)
            producer.flush()
        }
    }

    private fun producerProperties(bootstrapServers: String): Properties =
        Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ProducerConfig.CLIENT_ID_CONFIG, "live-notification-consumer-smoke-${UUID.randomUUID()}")
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
            put(ProducerConfig.LINGER_MS_CONFIG, "0")
            put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "10000")
            put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "10000")
            put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "11000")
        }

    private fun waitForDeliveryCount(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        sourceEventId: String,
        expectedCount: Int,
        timeout: Duration
    ) {
        val deadline = System.nanoTime() + timeout.toNanos()
        var lastCount = 0
        while (System.nanoTime() < deadline) {
            lastCount = countRows(databaseUrl, databaseUser, databasePassword, "notification_delivery_requests", sourceEventId)
            if (lastCount == expectedCount) {
                return
            }
            Thread.sleep(500)
        }
        assertEquals(expectedCount, lastCount)
    }

    private fun countRows(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        tableName: String,
        sourceEventId: String
    ): Int =
        connection(databaseUrl, databaseUser, databasePassword).use { conn ->
            conn.prepareStatement("SELECT count(*) FROM $tableName WHERE source_event_id = ?").use { stmt ->
                stmt.setString(1, sourceEventId)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }

    private fun countAttempts(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        sourceEventId: String,
        status: String
    ): Int =
        connection(databaseUrl, databaseUser, databasePassword).use { conn ->
            conn.prepareStatement(
                """
                SELECT count(*)
                FROM notification_delivery_attempts a
                JOIN notification_delivery_requests r
                  ON r.delivery_request_id = a.delivery_request_id
                WHERE r.source_event_id = ?
                  AND a.status = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, sourceEventId)
                stmt.setString(2, status)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }

    private fun singleString(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        sql: String,
        sourceEventId: String
    ): String =
        connection(databaseUrl, databaseUser, databasePassword).use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, sourceEventId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        rs.getString(1)
                    } else {
                        fail("query returned no row for $sourceEventId")
                    }
                }
            }
        }

    private fun connection(databaseUrl: String, databaseUser: String, databasePassword: String): Connection =
        DriverManager.getConnection(databaseUrl, databaseUser, databasePassword)

    private fun waitForConsumerBatchLog(
        composeProject: String,
        consumerService: String,
        timeout: Duration
    ): String {
        val deadline = System.nanoTime() + timeout.toNanos()
        var lastOutput = ""
        while (System.nanoTime() < deadline) {
            val result = runDockerComposeResult(
                composeProject = composeProject,
                args = listOf("logs", "--no-color", "--tail=240", consumerService),
                timeout = Duration.ofSeconds(10)
            )
            lastOutput = result.output
            val matchingLine = lastOutput
                .lineSequence()
                .lastOrNull {
                    it.contains("observability.notification.consumer") &&
                        it.contains("event=batch") &&
                        it.contains("syntheticOnly=true")
                }
            if (matchingLine != null) {
                return matchingLine
            }
            Thread.sleep(500)
        }
        return fail("notification consumer batch log was not found:\n$lastOutput")
    }

    private fun runDockerComposeResult(
        composeProject: String,
        args: List<String>,
        timeout: Duration
    ): CommandResult {
        val process = ProcessBuilder(listOf("docker", "compose") + args)
            .directory(java.nio.file.Paths.get(System.getProperty("user.dir")).toFile())
            .redirectErrorStream(true)
            .also {
                it.environment()["COMPOSE_PROJECT_NAME"] = composeProject
                copyEnvironmentIfPresent(it.environment(), "BANKING_LAB_POSTGRES_PORT")
                copyEnvironmentIfPresent(it.environment(), "BANKING_LAB_REDPANDA_PORT")
                copyEnvironmentIfPresent(it.environment(), "BANKING_LAB_REDPANDA_ADMIN_PORT")
                copyEnvironmentIfPresent(it.environment(), "BANKING_LAB_NOTIFICATION_EVENT_CONSUMER_TOPIC")
            }
            .start()
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            return CommandResult(
                exitCode = -1,
                output = "docker compose ${args.joinToString(" ")} timed out after ${timeout.seconds}s"
            )
        }
        return CommandResult(process.exitValue(), process.inputStream.bufferedReader().readText())
    }

    private fun copyEnvironmentIfPresent(environment: MutableMap<String, String>, name: String) {
        val value = System.getenv(name)
        if (!value.isNullOrBlank()) {
            environment[name] = value
        }
    }

    private data class CommandResult(
        val exitCode: Int,
        val output: String
    )
}
