package lab.banking.payment

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit
import lab.banking.payment.domain.CancelPaymentInstructionRequest
import lab.banking.payment.domain.CreatePaymentInstructionRequest
import lab.banking.payment.domain.PaymentInstructionService
import lab.banking.payment.eventing.PaymentKafkaPublisherConfig
import lab.banking.payment.eventing.PaymentOutboxKafkaEnvelope
import lab.banking.payment.eventing.PaymentOutboxPublisherPort
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
class PaymentKafkaOutboxPublisherIntegrationTest {
    @Autowired
    lateinit var paymentInstructionService: PaymentInstructionService

    @Autowired
    lateinit var publisher: PaymentOutboxPublisherPort

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              payment_cancellation_requests,
              payment_autopay_executions,
              payment_autopay_status_history,
              payment_autopay_agreements,
              payment_outbox_events,
              payment_status_history,
              payment_attempts,
              payment_idempotency_keys,
              payment_instructions
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
    }

    @Test
    fun `Redpanda publisher emits payment cancellation domain event without publishing ledger command events`() {
        val topic = uniqueTopic("banking-lab-payment-events")
        createTopic(topic)
        val created = paymentInstructionService.createInstruction(sampleCreate("PAY-KAFKA-CREATE-001"))
        paymentInstructionService.cancelInstruction(
            created.item.paymentInstructionId,
            CancelPaymentInstructionRequest(
                idempotencyKey = "PAY-KAFKA-CANCEL-001",
                requestedBy = "customer01",
                reason = "Synthetic customer cancellation for payment Kafka publisher"
            )
        )

        val result = publisher.publishAvailable(
            PaymentKafkaPublisherConfig(
                bootstrapServers = redpanda.bootstrapServers,
                topic = topic,
                clientId = "payment-kafka-publisher-${UUID.randomUUID()}"
            ),
            limit = 5
        )

        assertEquals(1, result.attempted)
        assertEquals(1, result.published)
        assertEquals(0, result.failed)
        assertEquals("PaymentInstructionCanceled", result.results.single().eventType)
        assertEquals(1, countRows("payment_outbox_events WHERE event_type = 'PaymentInstructionCanceled' AND status = 'PUBLISHED'"))
        assertEquals(1, countRows("payment_outbox_events WHERE event_type = 'PaymentLedgerPostingRequested' AND status = 'PENDING'"))

        val envelope = consumeSingle(topic, "payment-kafka-publisher-test-${UUID.randomUUID()}")
        assertNotNull(envelope.outboxEventId)
        assertEquals("payment_instruction", envelope.aggregateType)
        assertEquals(created.item.paymentInstructionId, envelope.aggregateId)
        assertEquals("PaymentInstructionCanceled", envelope.eventType)
        assertEquals("PAY-KAFKA-CANCEL-001", envelope.idempotencyKey)
        assertEquals(true, envelope.headers["syntheticOnly"])
        assertEquals("payment-service", envelope.headers["sourceService"])
        assertEquals(created.item.paymentInstructionId, envelope.payload["paymentInstructionId"])
        assertEquals(true, envelope.payload["syntheticOnly"])
        assertEquals(false, envelope.payload["directLedgerWrite"])
    }

    private fun sampleCreate(idempotencyKey: String): CreatePaymentInstructionRequest =
        CreatePaymentInstructionRequest(
            customerId = "CUS-PAY-KAFKA-001",
            debitAccountId = "ACC-PAY-KAFKA-001",
            billerId = "SYN-BILLER-UTIL-001",
            amountMinor = 33_000,
            currency = "KRW",
            idempotencyKey = idempotencyKey,
            requestedBy = "customer01",
            requestedChannel = "CUSTOMER_WEB",
            reason = "Synthetic utility bill payment for Kafka publisher"
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

    private fun consumeSingle(topic: String, groupId: String): PaymentOutboxKafkaEnvelope {
        KafkaConsumer<String, String>(consumerProperties(groupId)).use { consumer ->
            consumer.subscribe(listOf(topic))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (System.nanoTime() < deadline) {
                val records = consumer.poll(Duration.ofMillis(250))
                if (!records.isEmpty) {
                    assertEquals(1, records.count())
                    val record = records.first()
                    assertTrue(record.headers().any { it.key() == "syntheticOnly" })
                    return objectMapper.readValue(record.value(), PaymentOutboxKafkaEnvelope::class.java)
                }
            }
        }
        throw AssertionError("payment Kafka publisher did not produce a record on $topic")
    }

    private fun consumerProperties(groupId: String): Properties =
        Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, redpanda.bootstrapServers)
            put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
            put(ConsumerConfig.CLIENT_ID_CONFIG, "$groupId-client")
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        }

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $tableExpression", emptyMap<String, Any?>(), Int::class.java) ?: 0

    private fun uniqueTopic(prefix: String): String =
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
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
