package lab.banking.notification

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit
import lab.banking.notification.eventing.NotificationKafkaConsumer
import lab.banking.notification.eventing.NotificationKafkaConsumerConfig
import lab.banking.notification.eventing.NotificationOutboxKafkaEnvelope
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.redpanda.RedpandaContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@Testcontainers
class NotificationKafkaConsumerIntegrationTest {
    @Autowired
    lateinit var notificationKafkaConsumer: NotificationKafkaConsumer

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              notification_dead_letters,
              notification_delivery_attempts,
              notification_delivery_requests,
              notification_inbox_events
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
    }

    @Test
    fun `Redpanda outbox replay creates one masked notification delivery`() {
        val topic = uniqueTopic("banking-lab-notification")
        createTopic(topic)
        val envelope = paymentEnvelope("OBX-NOTIF-KAFKA-001")
        produce(topic, envelope)
        produce(topic, envelope)

        val result = notificationKafkaConsumer.consumeAvailable(
            consumerConfig(topic, uniqueGroup("notification-service")),
            maxRecords = 2
        )

        assertEquals(2, result.polled)
        assertEquals(1, result.processed)
        assertEquals(1, result.duplicates)
        assertEquals(1, result.createdDeliveries)
        assertEquals(1, countRows("notification_inbox_events"))
        assertEquals(1, countRows("notification_delivery_requests"))
        assertEquals(1, countRows("notification_delivery_attempts WHERE status = 'PENDING'"))

        val maskedMessage = singleString(
            "SELECT masked_message FROM notification_delivery_requests WHERE source_event_id = :sourceEventId",
            mapOf("sourceEventId" to "OBX-NOTIF-KAFKA-001")
        )
        val maskedPayload = singleString(
            "SELECT masked_payload_json::text FROM notification_delivery_requests WHERE source_event_id = :sourceEventId",
            mapOf("sourceEventId" to "OBX-NOTIF-KAFKA-001")
        )
        assertTrue(maskedMessage.contains("ACC-***0001"))
        assertFalse(maskedMessage.contains("ACC-TEST-0001"))
        assertFalse(maskedPayload.contains("ACC-TEST-0001"))
        assertFalse(maskedPayload.contains("010-1234-5678"))
    }

    @Test
    fun `unsupported synthetic Redpanda events are skipped without delivery side effects`() {
        val topic = uniqueTopic("banking-lab-notification-skip")
        createTopic(topic)
        produce(
            topic,
            NotificationOutboxKafkaEnvelope(
                outboxEventId = "OBX-NOTIF-SKIP-001",
                aggregateType = "DepositProduct",
                aggregateId = "PRD-SYN-001",
                eventType = "DepositInterestAccrued",
                idempotencyKey = "IDEMP-NOTIF-SKIP-001",
                payload = mapOf(
                    "productId" to "PRD-SYN-001",
                    "syntheticOnly" to true
                ),
                headers = mapOf("syntheticOnly" to true)
            )
        )

        val result = notificationKafkaConsumer.consumeAvailable(
            consumerConfig(topic, uniqueGroup("notification-service-skip")),
            maxRecords = 1
        )

        assertEquals(1, result.polled)
        assertEquals(0, result.processed)
        assertEquals(1, result.skipped)
        assertEquals("unsupported event type for notification routing", result.results.single().skipReason)
        assertEquals(0, countRows("notification_inbox_events"))
        assertEquals(0, countRows("notification_delivery_requests"))
    }

    private fun paymentEnvelope(outboxEventId: String): NotificationOutboxKafkaEnvelope =
        NotificationOutboxKafkaEnvelope(
            outboxEventId = outboxEventId,
            aggregateType = "PaymentInstruction",
            aggregateId = "PAY-NOTIF-KAFKA-001",
            eventType = "PaymentLedgerPostingRequested",
            idempotencyKey = "IDEMP-NOTIF-KAFKA-001",
            payload = mapOf(
                "contractVersion" to "2026-06-05",
                "paymentInstructionId" to "PAY-NOTIF-KAFKA-001",
                "customerId" to "CUS-NOTIF-KAFKA-001",
                "debitAccountId" to "ACC-TEST-0001",
                "amountMinor" to 45_000,
                "currency" to "KRW",
                "phone" to "010-1234-5678",
                "syntheticOnly" to true
            ),
            headers = mapOf("syntheticOnly" to true)
        )

    private fun consumerConfig(topic: String, groupId: String): NotificationKafkaConsumerConfig =
        NotificationKafkaConsumerConfig(
            bootstrapServers = redpanda.bootstrapServers,
            topic = topic,
            groupId = groupId,
            clientId = "$groupId-client",
            pollTimeoutMillis = 10_000
        )

    private fun createTopic(topic: String) {
        val props = Properties().apply {
            put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, redpanda.bootstrapServers)
        }
        AdminClient.create(props).use { admin ->
            admin.createTopics(listOf(NewTopic(topic, 1, 1.toShort())))
                .all()
                .get(10, TimeUnit.SECONDS)
        }
    }

    private fun produce(topic: String, envelope: NotificationOutboxKafkaEnvelope) {
        KafkaProducer<String, String>(producerProperties()).use { producer ->
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

    private fun producerProperties(): Properties =
        Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, redpanda.bootstrapServers)
            put(ProducerConfig.CLIENT_ID_CONFIG, "notification-kafka-test-${UUID.randomUUID()}")
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
            put(ProducerConfig.LINGER_MS_CONFIG, "0")
            put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "10000")
            put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "10000")
            put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "11000")
        }

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $tableExpression", emptyMap<String, Any?>(), Int::class.java) ?: 0

    private fun singleString(sql: String, params: Map<String, Any?>): String =
        jdbc.queryForObject(sql, params, String::class.java) ?: ""

    private fun uniqueTopic(prefix: String): String =
        "$prefix-${UUID.randomUUID().toString().lowercase()}"

    private fun uniqueGroup(prefix: String): String =
        "$prefix-${UUID.randomUUID().toString().lowercase()}"

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        @Container
        @JvmStatic
        val redpanda = RedpandaContainer(
            DockerImageName.parse("docker.redpanda.com/redpandadata/redpanda:v24.3.7")
        )

        @DynamicPropertySource
        @JvmStatic
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
