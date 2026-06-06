package lab.banking.payment.eventing

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit
import lab.banking.payment.domain.PaymentOutboxRecord
import lab.banking.payment.persistence.PaymentRepository
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Service
class PaymentKafkaOutboxPublisher(
    private val repository: PaymentRepository,
    private val objectMapper: ObjectMapper,
    transactionManager: PlatformTransactionManager
) : PaymentOutboxPublisherPort {
    private val transactions = TransactionTemplate(transactionManager)

    override fun publishAvailable(config: PaymentKafkaPublisherConfig, limit: Int): PaymentKafkaPublishBatchResult {
        require(limit > 0) { "payment Kafka publish limit must be positive" }
        require(config.bootstrapServers.isNotBlank()) { "payment Kafka bootstrap servers must be configured" }
        require(config.topic.isNotBlank()) { "payment Kafka topic must be configured" }
        require(config.clientId.isNotBlank()) { "payment Kafka client id must be configured" }
        require(config.publishTimeoutMillis > 0) { "payment Kafka publish timeout must be positive" }
        require(config.deadLetterThreshold > 0) { "payment Kafka dead-letter threshold must be positive" }
        require(config.retryDelaySeconds >= 0) { "payment Kafka retry delay must be non-negative" }
        require(config.eventTypes.isNotEmpty()) { "payment Kafka event type allow-list must not be empty" }

        val results = mutableListOf<PaymentKafkaPublishResult>()
        while (results.size < limit) {
            val result = publishNext(config) ?: break
            results += result
        }
        return PaymentKafkaPublishBatchResult(
            attempted = results.size,
            published = results.count { it.status == "PUBLISHED" },
            failed = results.count { it.status == "FAILED" },
            deadLettered = results.count { it.status == "DEAD_LETTER" },
            results = results
        )
    }

    fun publishNext(config: PaymentKafkaPublisherConfig): PaymentKafkaPublishResult? =
        transactions.execute<PaymentKafkaPublishResult?> {
            val event = repository.findNextPublishableDomainOutboxForUpdate(config.eventTypes) ?: return@execute null
            publishLockedEvent(config, event)
        }

    private fun publishLockedEvent(
        config: PaymentKafkaPublisherConfig,
        event: PaymentOutboxRecord
    ): PaymentKafkaPublishResult {
        val producer = KafkaProducer<String, String>(producerProperties(config))
        return try {
            val record = ProducerRecord(
                config.topic,
                event.aggregateId,
                objectMapper.writeValueAsString(event.toKafkaEnvelope())
            )
            record.headers().add(header("outboxEventId", event.outboxEventId))
            record.headers().add(header("eventType", event.eventType))
            record.headers().add(header("aggregateId", event.aggregateId))
            record.headers().add(header("occurredAt", event.createdAt.toString()))
            record.headers().add(header("sourceService", "payment-service"))
            record.headers().add(header("syntheticOnly", "true"))

            val metadata = producer
                .send(record)
                .get(config.publishTimeoutMillis, TimeUnit.MILLISECONDS)
            repository.markOutboxPublished(event.outboxEventId)
            PaymentKafkaPublishResult(
                outboxEventId = event.outboxEventId,
                eventType = event.eventType,
                status = "PUBLISHED",
                topic = metadata.topic(),
                partition = metadata.partition(),
                offset = metadata.offset(),
                errorMessage = null
            )
        } catch (ex: Exception) {
            val nextRetryCount = event.retryCount + 1
            val deadLetter = nextRetryCount >= config.deadLetterThreshold
            val status = if (deadLetter) "DEAD_LETTER" else "FAILED"
            repository.markOutboxFailed(
                outboxEventId = event.outboxEventId,
                retryCount = nextRetryCount,
                errorMessage = summarize(ex),
                deadLetter = deadLetter,
                retryDelaySeconds = config.retryDelaySeconds
            )
            PaymentKafkaPublishResult(
                outboxEventId = event.outboxEventId,
                eventType = event.eventType,
                status = status,
                topic = config.topic,
                partition = null,
                offset = null,
                errorMessage = summarize(ex)
            )
        } finally {
            producer.close(Duration.ZERO)
        }
    }

    private fun producerProperties(config: PaymentKafkaPublisherConfig): Properties =
        Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers)
            put(ProducerConfig.CLIENT_ID_CONFIG, config.clientId)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
            put(ProducerConfig.RETRIES_CONFIG, "3")
            put(ProducerConfig.LINGER_MS_CONFIG, "0")
            put(ProducerConfig.MAX_BLOCK_MS_CONFIG, config.publishTimeoutMillis.toString())
            put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, config.publishTimeoutMillis.coerceAtLeast(1_000).toString())
            put(
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
                (config.publishTimeoutMillis.coerceAtLeast(1_000) + 1_000).toString()
            )
        }

    private fun PaymentOutboxRecord.toKafkaEnvelope(): PaymentOutboxKafkaEnvelope =
        PaymentOutboxKafkaEnvelope(
            outboxEventId = outboxEventId,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            occurredAt = createdAt.toString(),
            idempotencyKey = idempotencyKey,
            payload = payload,
            headers = mapOf(
                "syntheticOnly" to true,
                "sourceService" to "payment-service",
                "eventType" to eventType,
                "aggregateId" to aggregateId,
                "occurredAt" to createdAt.toString()
            )
        )

    private fun header(name: String, value: String): RecordHeader =
        RecordHeader(name, value.toByteArray(StandardCharsets.UTF_8))

    private fun summarize(ex: Exception): String =
        "${ex::class.simpleName}: ${ex.message ?: "payment Kafka publish failed"}".take(500)
}
