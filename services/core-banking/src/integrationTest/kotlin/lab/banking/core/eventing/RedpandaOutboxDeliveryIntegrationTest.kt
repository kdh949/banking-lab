package lab.banking.core.eventing

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Paths
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
class RedpandaOutboxDeliveryIntegrationTest {
    @Autowired
    lateinit var outboxService: DurableOutboxService

    @Autowired
    lateinit var outboxRepository: OutboxEventRepository

    @Autowired
    lateinit var kafkaOutboxPublisher: KafkaOutboxPublisher

    @Autowired
    lateinit var kafkaInboxConsumer: KafkaInboxConsumer

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              inbox_events,
              outbox_events
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
    }

    @Test
    fun `pending outbox event is published to Redpanda and consumed idempotently`() {
        val topic = uniqueTopic("banking-lab-outbox")
        createTopic(topic)
        val pending = outboxService.enqueue(outboxCommand("TX-KAFKA-001", "IDEMP-KAFKA-001"))

        val publish = kafkaOutboxPublisher.publishAvailable(
            KafkaOutboxPublisherConfig(
                bootstrapServers = redpanda.bootstrapServers,
                topic = topic,
                publishTimeoutMillis = 10_000
            ),
            limit = 1
        )

        assertEquals(1, publish.attempted)
        assertEquals(1, publish.published)
        assertEquals(0, publish.failed)
        assertEquals(OutboxEventStatus.PUBLISHED, outboxService.event(pending.outboxEventId).status)
        assertNotNull(outboxService.event(pending.outboxEventId).publishedAt)

        val firstConsume = kafkaInboxConsumer.consumeAvailable(
            KafkaInboxConsumerConfig(
                bootstrapServers = redpanda.bootstrapServers,
                topic = topic,
                groupId = uniqueGroup("ledger-projection"),
                consumerName = "ledger-projection",
                pollTimeoutMillis = 10_000
            ),
            maxRecords = 1
        )

        assertEquals(1, firstConsume.polled)
        assertEquals(1, firstConsume.processed)
        assertEquals(0, firstConsume.duplicates)
        assertEquals(1, outboxRepository.countInboxRows("ledger-projection", pending.outboxEventId))

        val replayConsume = kafkaInboxConsumer.consumeAvailable(
            KafkaInboxConsumerConfig(
                bootstrapServers = redpanda.bootstrapServers,
                topic = topic,
                groupId = uniqueGroup("ledger-projection-replay"),
                consumerName = "ledger-projection",
                pollTimeoutMillis = 10_000
            ),
            maxRecords = 1
        )

        assertEquals(1, replayConsume.polled)
        assertEquals(0, replayConsume.processed)
        assertEquals(1, replayConsume.duplicates)
        assertEquals(1, outboxRepository.countInboxRows("ledger-projection", pending.outboxEventId))
    }

    @Test
    fun `broker publish failures are retried then moved to dead letter durably`() {
        val pending = outboxService.enqueue(outboxCommand("TX-KAFKA-DLQ", "IDEMP-KAFKA-DLQ"))
        val failureConfig = KafkaOutboxPublisherConfig(
            bootstrapServers = "127.0.0.1:1",
            topic = uniqueTopic("banking-lab-outbox-failure"),
            publishTimeoutMillis = 500,
            deadLetterThreshold = 2,
            retryDelaySeconds = 0
        )

        val firstAttempt = kafkaOutboxPublisher.publishAvailable(failureConfig, limit = 1)
        assertEquals(1, firstAttempt.attempted)
        assertEquals(1, firstAttempt.failed)
        assertEquals(OutboxEventStatus.FAILED, outboxService.event(pending.outboxEventId).status)
        assertEquals(1, outboxService.event(pending.outboxEventId).retryCount)

        val secondAttempt = kafkaOutboxPublisher.publishAvailable(failureConfig, limit = 1)
        assertEquals(1, secondAttempt.attempted)
        assertEquals(1, secondAttempt.deadLettered)
        assertEquals(OutboxEventStatus.DEAD_LETTER, outboxService.event(pending.outboxEventId).status)
        assertEquals(2, outboxService.event(pending.outboxEventId).retryCount)
        assertNotNull(outboxService.event(pending.outboxEventId).errorMessage)
    }

    @Test
    fun `worker crash after broker ack is recovered by outbox replay and inbox idempotency`() {
        val topic = uniqueTopic("banking-lab-outbox-crash")
        createTopic(topic)
        val pending = outboxService.enqueue(outboxCommand("TX-KAFKA-CRASH", "IDEMP-KAFKA-CRASH"))

        simulateWorkerCrashAfterBrokerAck(topic, pending)
        val stillPending = outboxService.event(pending.outboxEventId)
        assertEquals(OutboxEventStatus.PENDING, stillPending.status)
        assertNull(stillPending.publishedAt)

        val replayPublish = kafkaOutboxPublisher.publishAvailable(
            KafkaOutboxPublisherConfig(
                bootstrapServers = redpanda.bootstrapServers,
                topic = topic,
                publishTimeoutMillis = 10_000
            ),
            limit = 1
        )
        assertEquals(1, replayPublish.attempted)
        assertEquals(1, replayPublish.published)
        assertEquals(OutboxEventStatus.PUBLISHED, outboxService.event(pending.outboxEventId).status)

        val consume = kafkaInboxConsumer.consumeAvailable(
            KafkaInboxConsumerConfig(
                bootstrapServers = redpanda.bootstrapServers,
                topic = topic,
                groupId = uniqueGroup("ledger-projection-crash-replay"),
                consumerName = "ledger-projection",
                pollTimeoutMillis = 10_000
            ),
            maxRecords = 2
        )

        assertEquals(2, consume.polled)
        assertEquals(1, consume.processed)
        assertEquals(1, consume.duplicates)
        assertEquals(
            listOf(pending.outboxEventId, pending.outboxEventId),
            consume.results.map { it.sourceEventId }
        )
        assertEquals(1, outboxRepository.countInboxRows("ledger-projection", pending.outboxEventId))
    }

    private fun outboxCommand(aggregateId: String, idempotencyKey: String): CreateOutboxEventCommand =
        CreateOutboxEventCommand(
            aggregateType = "LedgerTransaction",
            aggregateId = aggregateId,
            eventType = "LedgerTransactionPosted",
            idempotencyKey = idempotencyKey,
            payload = mapOf("ledgerTransactionId" to aggregateId, "syntheticOnly" to true)
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

    private fun simulateWorkerCrashAfterBrokerAck(topic: String, event: OutboxEventRecord) {
        KafkaProducer<String, String>(producerProperties()).use { producer ->
            val envelope = OutboxKafkaEnvelope(
                outboxEventId = event.outboxEventId,
                aggregateType = event.aggregateType,
                aggregateId = event.aggregateId,
                eventType = event.eventType,
                idempotencyKey = event.idempotencyKey,
                payload = event.payload,
                headers = event.headers + ("syntheticOnly" to true)
            )
            producer.send(
                ProducerRecord(
                    topic,
                    event.aggregateId,
                    objectMapper.writeValueAsString(envelope)
                )
            ).get(10, TimeUnit.SECONDS)
            producer.flush()
        }
    }

    private fun producerProperties(): Properties =
        Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, redpanda.bootstrapServers)
            put(ProducerConfig.CLIENT_ID_CONFIG, "core-banking-outbox-crash-drill-${UUID.randomUUID()}")
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
            put(ProducerConfig.LINGER_MS_CONFIG, "0")
            put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "10000")
            put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "10000")
            put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "11000")
        }

    private fun uniqueTopic(prefix: String): String =
        "$prefix-${UUID.randomUUID().toString().lowercase()}"

    private fun uniqueGroup(prefix: String): String =
        "$prefix-${UUID.randomUUID().toString().lowercase()}"

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        @Container
        @JvmStatic
        val redpanda = RedpandaContainer(
            DockerImageName.parse("docker.redpanda.com/redpandadata/redpanda:v24.3.7")
        )

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
