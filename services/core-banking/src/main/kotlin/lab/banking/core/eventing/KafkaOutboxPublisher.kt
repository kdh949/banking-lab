package lab.banking.core.eventing

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Service
class KafkaOutboxPublisher(
    private val repository: OutboxEventRepository,
    private val outboxService: DurableOutboxService,
    private val objectMapper: ObjectMapper,
    transactionManager: PlatformTransactionManager
) : OutboxPublisherPort {
    private val transactions = TransactionTemplate(transactionManager)

    override fun publishAvailable(config: KafkaOutboxPublisherConfig, limit: Int): KafkaOutboxPublishBatchResult {
        require(limit > 0) { "limit must be positive" }
        val results = mutableListOf<KafkaOutboxPublishResult>()
        while (results.size < limit) {
            val result = publishNext(config) ?: break
            results += result
        }
        return KafkaOutboxPublishBatchResult(
            attempted = results.size,
            published = results.count { it.status == OutboxEventStatus.PUBLISHED },
            failed = results.count { it.status == OutboxEventStatus.FAILED },
            deadLettered = results.count { it.status == OutboxEventStatus.DEAD_LETTER },
            results = results
        )
    }

    fun publishNext(config: KafkaOutboxPublisherConfig): KafkaOutboxPublishResult? =
        transactions.execute<KafkaOutboxPublishResult?> {
            val event = repository.findNextPublishableForUpdate() ?: return@execute null
            publishLockedEvent(config, event)
        }

    private fun publishLockedEvent(
        config: KafkaOutboxPublisherConfig,
        event: OutboxEventRecord
    ): KafkaOutboxPublishResult {
        val producer = KafkaProducer<String, String>(producerProperties(config))
        return try {
            val record = ProducerRecord(
                config.topic,
                event.aggregateId,
                objectMapper.writeValueAsString(event.toKafkaEnvelope())
            )
            record.headers().add(header("outboxEventId", event.outboxEventId))
            record.headers().add(header("eventType", event.eventType))
            record.headers().add(header("syntheticOnly", "true"))

            val metadata = producer
                .send(record)
                .get(config.publishTimeoutMillis, TimeUnit.MILLISECONDS)
            val published = outboxService.markPublished(event.outboxEventId)
            KafkaOutboxPublishResult(
                outboxEventId = published.outboxEventId,
                status = published.status,
                topic = metadata.topic(),
                partition = metadata.partition(),
                offset = metadata.offset(),
                errorMessage = null
            )
        } catch (ex: Exception) {
            val failed = outboxService.recordPublishFailure(
                outboxEventId = event.outboxEventId,
                errorMessage = summarize(ex),
                deadLetterThreshold = config.deadLetterThreshold,
                retryDelaySeconds = config.retryDelaySeconds
            )
            KafkaOutboxPublishResult(
                outboxEventId = failed.outboxEventId,
                status = failed.status,
                topic = config.topic,
                partition = null,
                offset = null,
                errorMessage = failed.errorMessage
            )
        } finally {
            producer.close(Duration.ZERO)
        }
    }

    private fun producerProperties(config: KafkaOutboxPublisherConfig): Properties =
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

    private fun OutboxEventRecord.toKafkaEnvelope(): OutboxKafkaEnvelope =
        OutboxKafkaEnvelope(
            outboxEventId = outboxEventId,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            idempotencyKey = idempotencyKey,
            payload = payload,
            headers = headers + ("syntheticOnly" to true)
        )

    private fun header(name: String, value: String): RecordHeader =
        RecordHeader(name, value.toByteArray(StandardCharsets.UTF_8))

    private fun summarize(ex: Exception): String =
        "${ex::class.simpleName}: ${ex.message ?: "Kafka publish failed"}".take(500)
}
