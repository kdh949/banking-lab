package lab.banking.payment

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit
import lab.banking.payment.domain.CancelPaymentInstructionRequest
import lab.banking.payment.domain.CoreLedgerPaymentPostingCommand
import lab.banking.payment.domain.CoreLedgerPostingClient
import lab.banking.payment.domain.CoreLedgerPostingResult
import lab.banking.payment.domain.CreatePaymentInstructionRequest
import lab.banking.payment.domain.DispatchPaymentLedgerPostingRequest
import lab.banking.payment.domain.PaymentInstructionService
import lab.banking.payment.domain.PaymentOutboxDispatcherService
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
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.redpanda.RedpandaContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest(properties = ["banking-lab.payment-service.core-banking.http-enabled=false"])
@Testcontainers
class PaymentKafkaOutboxPublisherIntegrationTest {
    @Autowired
    lateinit var paymentInstructionService: PaymentInstructionService

    @Autowired
    lateinit var dispatcherService: PaymentOutboxDispatcherService

    @Autowired
    lateinit var coreLedgerPostingClient: PublisherCoreLedgerPostingClient

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
        coreLedgerPostingClient.reset()
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
        assertNotNull(envelope.occurredAt)
        assertEquals("PAY-KAFKA-CANCEL-001", envelope.idempotencyKey)
        assertEquals(true, envelope.headers["syntheticOnly"])
        assertEquals("payment-service", envelope.headers["sourceService"])
        assertEquals("PaymentInstructionCanceled", envelope.headers["eventType"])
        assertEquals(created.item.paymentInstructionId, envelope.headers["aggregateId"])
        assertEquals(envelope.occurredAt, envelope.headers["occurredAt"])
        assertEquals(created.item.paymentInstructionId, envelope.payload["paymentInstructionId"])
        assertEquals(true, envelope.payload["syntheticOnly"])
        assertEquals(false, envelope.payload["directLedgerWrite"])
    }

    @Test
    fun `Redpanda publisher emits payment failure retry and dead-letter lifecycle domain events`() {
        val topic = uniqueTopic("banking-lab-payment-lifecycle-events")
        createTopic(topic)
        val retryable = paymentInstructionService.createInstruction(sampleCreate("PAY-KAFKA-LIFECYCLE-RETRY-001"))
        coreLedgerPostingClient.failOnce("synthetic core ledger unavailable")
        val failedDispatch = dispatcherService.dispatchNextLedgerPosting(dispatchRequest(deadLetterThreshold = 3))
        val deadLettered = paymentInstructionService.createInstruction(sampleCreate("PAY-KAFKA-LIFECYCLE-DEAD-001"))
        coreLedgerPostingClient.failOnce("synthetic core ledger hard failure")
        val deadLetterDispatch = dispatcherService.dispatchNextLedgerPosting(dispatchRequest(deadLetterThreshold = 1))

        assertEquals("FAILED", failedDispatch.status)
        assertEquals("DEAD_LETTER", deadLetterDispatch.status)
        assertEquals(1, countRows("payment_outbox_events WHERE event_type = 'PaymentInstructionFailed' AND status = 'PENDING'"))
        assertEquals(1, countRows("payment_outbox_events WHERE event_type = 'PaymentInstructionRetryScheduled' AND status = 'PENDING'"))
        assertEquals(1, countRows("payment_outbox_events WHERE event_type = 'PaymentInstructionDeadLettered' AND status = 'PENDING'"))

        val result = publisher.publishAvailable(
            PaymentKafkaPublisherConfig(
                bootstrapServers = redpanda.bootstrapServers,
                topic = topic,
                clientId = "payment-kafka-lifecycle-publisher-${UUID.randomUUID()}"
            ),
            limit = 10
        )

        assertEquals(3, result.attempted)
        assertEquals(3, result.published)
        assertEquals(0, result.failed)
        assertEquals(0, result.deadLettered)
        assertEquals(
            setOf(
                "PaymentInstructionFailed",
                "PaymentInstructionRetryScheduled",
                "PaymentInstructionDeadLettered"
            ),
            result.results.map { it.eventType }.toSet()
        )
        assertEquals(1, countRows("payment_outbox_events WHERE event_type = 'PaymentInstructionFailed' AND status = 'PUBLISHED'"))
        assertEquals(
            1,
            countRows("payment_outbox_events WHERE event_type = 'PaymentInstructionRetryScheduled' AND status = 'PUBLISHED'")
        )
        assertEquals(
            1,
            countRows("payment_outbox_events WHERE event_type = 'PaymentInstructionDeadLettered' AND status = 'PUBLISHED'")
        )
        assertEquals(1, countRows("payment_outbox_events WHERE event_type = 'PaymentLedgerPostingRequested' AND status = 'FAILED'"))
        assertEquals(
            1,
            countRows("payment_outbox_events WHERE event_type = 'PaymentLedgerPostingRequested' AND status = 'DEAD_LETTER'")
        )

        val envelopes = consume(topic, "payment-kafka-lifecycle-publisher-test-${UUID.randomUUID()}", expectedCount = 3)
            .associateBy { it.eventType }
        val failedEnvelope = envelopes["PaymentInstructionFailed"] ?: error("missing PaymentInstructionFailed envelope")
        val retryEnvelope = envelopes["PaymentInstructionRetryScheduled"]
            ?: error("missing PaymentInstructionRetryScheduled envelope")
        val deadLetterEnvelope = envelopes["PaymentInstructionDeadLettered"]
            ?: error("missing PaymentInstructionDeadLettered envelope")

        assertLifecycleEnvelope(
            envelope = failedEnvelope,
            instructionId = retryable.item.paymentInstructionId,
            status = "FAILED",
            retryable = true,
            retryCount = 1
        )
        assertEquals("PAYMENT_LEDGER_DISPATCH_FAILED", failedEnvelope.payload["failureCode"])
        assertLifecycleEnvelope(
            envelope = retryEnvelope,
            instructionId = retryable.item.paymentInstructionId,
            status = "RETRY_SCHEDULED",
            retryable = null,
            retryCount = 1
        )
        assertNotNull(retryEnvelope.payload["nextRetryAt"])
        assertLifecycleEnvelope(
            envelope = deadLetterEnvelope,
            instructionId = deadLettered.item.paymentInstructionId,
            status = "DEAD_LETTER",
            retryable = false,
            retryCount = 1
        )
        assertEquals("PAYMENT_LEDGER_DISPATCH_DEAD_LETTER", deadLetterEnvelope.payload["failureCode"])
        assertEquals(1, payloadInt(deadLetterEnvelope, "deadLetterThreshold"))
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

    private fun dispatchRequest(deadLetterThreshold: Int): DispatchPaymentLedgerPostingRequest =
        DispatchPaymentLedgerPostingRequest(
            requestedBy = "payment-service-domain-event-publisher-test",
            reason = "Synthetic payment lifecycle publisher coverage",
            deadLetterThreshold = deadLetterThreshold
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
        return consume(topic, groupId, expectedCount = 1).single()
    }

    private fun consume(topic: String, groupId: String, expectedCount: Int): List<PaymentOutboxKafkaEnvelope> {
        val envelopes = mutableListOf<PaymentOutboxKafkaEnvelope>()
        KafkaConsumer<String, String>(consumerProperties(groupId)).use { consumer ->
            consumer.subscribe(listOf(topic))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (System.nanoTime() < deadline && envelopes.size < expectedCount) {
                val records = consumer.poll(Duration.ofMillis(250))
                if (!records.isEmpty) {
                    records.forEach { record ->
                        assertTrue(record.headers().any { it.key() == "syntheticOnly" })
                        assertTrue(record.headers().any { it.key() == "sourceService" })
                        assertTrue(record.headers().any { it.key() == "eventType" })
                        assertTrue(record.headers().any { it.key() == "aggregateId" })
                        assertTrue(record.headers().any { it.key() == "occurredAt" })
                        envelopes += objectMapper.readValue(record.value(), PaymentOutboxKafkaEnvelope::class.java)
                    }
                }
            }
        }
        if (envelopes.size != expectedCount) {
            throw AssertionError(
                "payment Kafka publisher produced ${envelopes.size} record(s) on $topic, expected $expectedCount"
            )
        }
        return envelopes
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

    private fun assertLifecycleEnvelope(
        envelope: PaymentOutboxKafkaEnvelope,
        instructionId: String,
        status: String,
        retryable: Boolean?,
        retryCount: Int
    ) {
        assertNotNull(envelope.outboxEventId)
        assertEquals("payment_instruction", envelope.aggregateType)
        assertEquals(instructionId, envelope.aggregateId)
        assertNotNull(envelope.occurredAt)
        assertEquals(true, envelope.headers["syntheticOnly"])
        assertEquals("payment-service", envelope.headers["sourceService"])
        assertEquals(envelope.eventType, envelope.headers["eventType"])
        assertEquals(instructionId, envelope.headers["aggregateId"])
        assertEquals(envelope.occurredAt, envelope.headers["occurredAt"])
        assertEquals(instructionId, envelope.payload["paymentInstructionId"])
        assertEquals(status, envelope.payload["status"])
        assertEquals(retryCount, payloadInt(envelope, "retryCount"))
        if (retryable != null) {
            assertEquals(retryable, envelope.payload["retryable"])
        }
        assertEquals(true, envelope.payload["syntheticOnly"])
        assertEquals(false, envelope.payload["directLedgerWrite"])
        assertEquals(false, envelope.payload["realPaymentNetworkUsed"])
        assertEquals(false, envelope.payload["realFinancialInstitutionApiUsed"])
    }

    private fun payloadInt(envelope: PaymentOutboxKafkaEnvelope, field: String): Int =
        (envelope.payload[field] as Number).toInt()

    private fun uniqueTopic(prefix: String): String =
        "$prefix-${UUID.randomUUID().toString().lowercase()}"

    @TestConfiguration
    class TestCoreLedgerPostingClientConfig {
        @Bean
        fun coreLedgerPostingClient(): PublisherCoreLedgerPostingClient = PublisherCoreLedgerPostingClient()
    }

    class PublisherCoreLedgerPostingClient : CoreLedgerPostingClient {
        val commands: MutableList<CoreLedgerPaymentPostingCommand> = mutableListOf()
        var nextResult: CoreLedgerPostingResult = CoreLedgerPostingResult("TX-PAY-KAFKA-PUBLISHER-DEFAULT")
        private var nextError: RuntimeException? = null

        override fun postBillPayment(command: CoreLedgerPaymentPostingCommand): CoreLedgerPostingResult {
            commands += command
            nextError?.let { error ->
                nextError = null
                throw error
            }
            return nextResult
        }

        fun failOnce(message: String) {
            nextError = IllegalStateException(message)
        }

        fun reset() {
            commands.clear()
            nextResult = CoreLedgerPostingResult("TX-PAY-KAFKA-PUBLISHER-DEFAULT")
            nextError = null
        }
    }

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
