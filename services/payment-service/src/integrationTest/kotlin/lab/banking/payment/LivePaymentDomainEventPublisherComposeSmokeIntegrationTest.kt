package lab.banking.payment

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit
import lab.banking.payment.eventing.PaymentOutboxKafkaEnvelope
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.TopicExistsException
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class LivePaymentDomainEventPublisherComposeSmokeIntegrationTest {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `live payment domain event publisher emits pending domain event from Compose worker`() {
        val composeProject = System.getenv("BANKING_LAB_LIVE_PAYMENT_DOMAIN_PUBLISHER_COMPOSE_PROJECT").orEmpty()
        assumeTrue(
            composeProject.isNotBlank(),
            "set BANKING_LAB_LIVE_PAYMENT_DOMAIN_PUBLISHER_COMPOSE_PROJECT to run live payment publisher drill"
        )

        val topic = System.getenv("BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_TOPIC")
            ?: "banking.lab.payment-domain-publisher-smoke"
        val databaseUrl = System.getenv("BANKING_LAB_LIVE_PAYMENT_DOMAIN_PUBLISHER_DATABASE_URL")
            ?: "jdbc:postgresql://127.0.0.1:${System.getenv("BANKING_LAB_POSTGRES_PORT") ?: "5432"}/banking_lab"
        val databaseUser = System.getenv("BANKING_LAB_LIVE_PAYMENT_DOMAIN_PUBLISHER_DATABASE_USER") ?: "banking_lab"
        val databasePassword = System.getenv("BANKING_LAB_LIVE_PAYMENT_DOMAIN_PUBLISHER_DATABASE_PASSWORD") ?: "banking_lab"
        val bootstrapServers = System.getenv("BANKING_LAB_LIVE_PAYMENT_DOMAIN_PUBLISHER_BOOTSTRAP_SERVERS")
            ?: "127.0.0.1:${System.getenv("BANKING_LAB_REDPANDA_PORT") ?: "9092"}"
        val publisherService = System.getenv("BANKING_LAB_LIVE_PAYMENT_DOMAIN_PUBLISHER_SERVICE")
            ?: "payment-domain-event-publisher"
        val composeEnv = composeEnvironment(topic)
        val paymentInstructionId = "PAY-LIVE-DOMAIN-${UUID.randomUUID().toString().uppercase()}"
        val domainOutboxEventId = "POB-LIVE-DOMAIN-${UUID.randomUUID().toString().uppercase()}"
        val ledgerOutboxEventId = "POB-LIVE-LEDGER-${UUID.randomUUID().toString().uppercase()}"
        val domainIdempotencyKey = "IDEMP-LIVE-DOMAIN-${UUID.randomUUID().toString().uppercase()}"
        val ledgerIdempotencyKey = "IDEMP-LIVE-LEDGER-${UUID.randomUUID().toString().uppercase()}"

        waitForPaymentOutboxSchema(databaseUrl, databaseUser, databasePassword, Duration.ofSeconds(90))
        waitForTopic(bootstrapServers, topic, Duration.ofSeconds(90))

        var publisherKilled = false
        try {
            runDockerCompose(composeProject, composeEnv, "kill", publisherService)
            publisherKilled = true

            insertPaymentOutboxEvent(
                databaseUrl = databaseUrl,
                databaseUser = databaseUser,
                databasePassword = databasePassword,
                outboxEventId = domainOutboxEventId,
                aggregateId = paymentInstructionId,
                eventType = "PaymentInstructionCanceled",
                idempotencyKey = domainIdempotencyKey,
                payloadJson = """
                {
                  "contractVersion": "2026-06-05",
                  "paymentInstructionId": "$paymentInstructionId",
                  "cancellationRequestId": "PCR-LIVE-DOMAIN-${UUID.randomUUID().toString().uppercase()}",
                  "syntheticOnly": true,
                  "directLedgerWrite": false,
                  "realPaymentNetworkUsed": false,
                  "drill": "compose-payment-domain-publisher"
                }
                """.trimIndent()
            )
            insertPaymentOutboxEvent(
                databaseUrl = databaseUrl,
                databaseUser = databaseUser,
                databasePassword = databasePassword,
                outboxEventId = ledgerOutboxEventId,
                aggregateId = "$paymentInstructionId-LEDGER",
                eventType = "PaymentLedgerPostingRequested",
                idempotencyKey = ledgerIdempotencyKey,
                payloadJson = """
                {
                  "contractVersion": "2026-06-05",
                  "paymentInstructionId": "$paymentInstructionId",
                  "syntheticOnly": true,
                  "directLedgerWrite": false,
                  "drill": "compose-payment-domain-publisher-ledger-boundary"
                }
                """.trimIndent()
            )
            assertEquals("PENDING", outboxStatus(databaseUrl, databaseUser, databasePassword, domainOutboxEventId))
            assertEquals("PENDING", outboxStatus(databaseUrl, databaseUser, databasePassword, ledgerOutboxEventId))

            runDockerCompose(
                composeProject,
                composeEnv,
                "up",
                "-d",
                "--force-recreate",
                "--no-deps",
                publisherService
            )
            publisherKilled = false

            waitForOutboxStatus(
                databaseUrl = databaseUrl,
                databaseUser = databaseUser,
                databasePassword = databasePassword,
                outboxEventId = domainOutboxEventId,
                expectedStatus = "PUBLISHED",
                timeout = Duration.ofSeconds(120)
            )
            assertNotNull(outboxPublishedAt(databaseUrl, databaseUser, databasePassword, domainOutboxEventId))
            assertEquals("PENDING", outboxStatus(databaseUrl, databaseUser, databasePassword, ledgerOutboxEventId))

            val envelope = waitForKafkaEnvelope(
                bootstrapServers = bootstrapServers,
                topic = topic,
                outboxEventId = domainOutboxEventId,
                timeout = Duration.ofSeconds(90)
            )
            assertEquals(domainOutboxEventId, envelope.outboxEventId)
            assertEquals("payment_instruction", envelope.aggregateType)
            assertEquals(paymentInstructionId, envelope.aggregateId)
            assertEquals("PaymentInstructionCanceled", envelope.eventType)
            assertEquals(domainIdempotencyKey, envelope.idempotencyKey)
            assertEquals(true, envelope.headers["syntheticOnly"])
            assertEquals("payment-service", envelope.headers["sourceService"])
            assertEquals(true, envelope.payload["syntheticOnly"])
            assertEquals(false, envelope.payload["directLedgerWrite"])

            val logLine = waitForPublisherBatchLog(
                composeProject = composeProject,
                composeEnv = composeEnv,
                publisherService = publisherService,
                topic = topic,
                timeout = Duration.ofSeconds(60)
            )
            assertTrue(logLine.contains("observability.payment.publisher"), logLine)
            assertTrue(logLine.contains("event=batch"), logLine)
            assertTrue(logLine.contains("topic=$topic"), logLine)
            assertTrue(logLine.contains("attempted=1"), logLine)
            assertTrue(logLine.contains("published=1"), logLine)
            assertTrue(logLine.contains("failed=0"), logLine)
            assertTrue(logLine.contains("deadLettered=0"), logLine)
            assertTrue(logLine.contains("syntheticOnly=true"), logLine)
        } finally {
            if (publisherKilled) {
                runCatching { runDockerCompose(composeProject, composeEnv, "up", "-d", "--no-deps", publisherService) }
            }
        }
    }

    private fun waitForPaymentOutboxSchema(
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
                    conn.prepareStatement("SELECT to_regclass('public.payment_outbox_events')").use { stmt ->
                        stmt.executeQuery().use { rs ->
                            if (rs.next() && rs.getString(1) == "payment_outbox_events") {
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
        fail<Unit>("payment_outbox_events schema was not ready: ${lastError?.message}")
    }

    private fun insertPaymentOutboxEvent(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        outboxEventId: String,
        aggregateId: String,
        eventType: String,
        idempotencyKey: String,
        payloadJson: String
    ) {
        connection(databaseUrl, databaseUser, databasePassword).use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO payment_outbox_events (
                  outbox_event_id,
                  aggregate_type,
                  aggregate_id,
                  event_type,
                  idempotency_key,
                  payload_json,
                  headers_json,
                  status
                )
                VALUES (?, 'payment_instruction', ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), 'PENDING')
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, outboxEventId)
                stmt.setString(2, aggregateId)
                stmt.setString(3, eventType)
                stmt.setString(4, idempotencyKey)
                stmt.setString(5, payloadJson)
                stmt.setString(6, """{"syntheticOnly":true,"source":"live-compose-payment-domain-publisher-drill"}""")
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
            conn.prepareStatement("SELECT $columnExpression FROM payment_outbox_events WHERE outbox_event_id = ?").use { stmt ->
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

    private fun waitForTopic(bootstrapServers: String, topic: String, timeout: Duration) {
        val deadline = System.nanoTime() + timeout.toNanos()
        var lastError: Throwable? = null
        while (System.nanoTime() < deadline) {
            try {
                createTopic(bootstrapServers, topic)
                return
            } catch (error: Exception) {
                lastError = error
            }
            Thread.sleep(500)
        }
        fail<Unit>("Kafka topic $topic was not ready: ${lastError?.message}")
    }

    private fun waitForKafkaEnvelope(
        bootstrapServers: String,
        topic: String,
        outboxEventId: String,
        timeout: Duration
    ): PaymentOutboxKafkaEnvelope {
        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ConsumerConfig.GROUP_ID_CONFIG, "live-payment-domain-publisher-${UUID.randomUUID()}")
            put(ConsumerConfig.CLIENT_ID_CONFIG, "live-payment-domain-publisher-consumer-${UUID.randomUUID()}")
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        }
        KafkaConsumer<String, String>(props).use { consumer ->
            consumer.subscribe(listOf(topic))
            val deadline = System.nanoTime() + timeout.toNanos()
            while (System.nanoTime() < deadline) {
                val records = consumer.poll(Duration.ofMillis(500))
                for (record in records) {
                    val headerOutboxId = record.headers().lastHeader("outboxEventId")?.value()?.toString(Charsets.UTF_8)
                    if (record.value().contains(outboxEventId) || headerOutboxId == outboxEventId) {
                        assertTrue(record.headers().any { it.key() == "syntheticOnly" })
                        return objectMapper.readValue(record.value())
                    }
                }
            }
        }
        return fail("payment domain event publisher did not produce a record for $outboxEventId on $topic")
    }

    private fun waitForPublisherBatchLog(
        composeProject: String,
        composeEnv: Map<String, String>,
        publisherService: String,
        topic: String,
        timeout: Duration
    ): String {
        val deadline = System.nanoTime() + timeout.toNanos()
        var lastOutput = ""
        while (System.nanoTime() < deadline) {
            val result = runDockerComposeResult(
                composeProject = composeProject,
                extraEnvironment = composeEnv,
                args = listOf("logs", "--no-color", "--tail=240", publisherService),
                timeout = Duration.ofSeconds(10)
            )
            lastOutput = result.output
            val matchingLine = lastOutput
                .lineSequence()
                .lastOrNull {
                    it.contains("observability.payment.publisher") &&
                        it.contains("event=batch") &&
                        it.contains("topic=$topic") &&
                        it.contains("published=1") &&
                        it.contains("syntheticOnly=true")
                }
            if (matchingLine != null) {
                return matchingLine
            }
            Thread.sleep(500)
        }
        return fail("payment publisher batch log for topic $topic was not found:\n$lastOutput")
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

    private fun composeEnvironment(topic: String): Map<String, String> =
        buildMap {
            put("BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_TOPIC", topic)
            put("BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_CLIENT_ID", "payment-compose-domain-event-publisher-smoke")
            put("BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_POLL_INTERVAL_MILLIS", "250")
            put("BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_TIMEOUT_MILLIS", "5000")
            put(
                "BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_EVENT_TYPES",
                "PaymentInstructionSettled,PaymentInstructionCanceled,PaymentAutopayExecutionCreated"
            )
            putOrCopyEnvironment("BANKING_LAB_TRACING_ENABLED", "false")
            putOrCopyEnvironment("BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED", "false")
            copyEnvironmentIfPresent("BANKING_LAB_POSTGRES_PORT")
            copyEnvironmentIfPresent("BANKING_LAB_REDPANDA_PORT")
            copyEnvironmentIfPresent("BANKING_LAB_REDPANDA_ADMIN_PORT")
            copyEnvironmentIfPresent("BANKING_LAB_REDPANDA_ADVERTISE_HOST")
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

    private data class CommandResult(
        val exitCode: Int,
        val output: String
    )
}
