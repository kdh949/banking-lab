package lab.banking.reporting.eventing

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit
import lab.banking.reporting.persistence.ReportingRepository
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Service
class ReportingKafkaOutboxPublisher(
    private val repository: ReportingRepository,
    private val objectMapper: ObjectMapper,
    transactionManager: PlatformTransactionManager
) : ReportingOutboxPublisherPort {
    private val transactions = TransactionTemplate(transactionManager)

    override fun publishAvailable(config: ReportingKafkaPublisherConfig, limit: Int): ReportingKafkaPublishBatchResult {
        require(limit > 0) { "reporting Kafka publish limit must be positive" }
        require(config.bootstrapServers.isNotBlank()) { "reporting Kafka bootstrap servers must be configured" }
        require(config.topic.isNotBlank()) { "reporting Kafka topic must be configured" }
        require(config.clientId.isNotBlank()) { "reporting Kafka client id must be configured" }
        require(config.publishTimeoutMillis > 0) { "reporting Kafka publish timeout must be positive" }
        require(config.eventTypes.isNotEmpty()) { "reporting Kafka event type allow-list must not be empty" }

        val results = mutableListOf<ReportingKafkaPublishResult>()
        while (results.size < limit) {
            val result = publishNext(config) ?: break
            results += result
        }
        return ReportingKafkaPublishBatchResult(
            attempted = results.size,
            published = results.count { it.status == "PUBLISHED" },
            failed = results.count { it.status == "FAILED" },
            results = results
        )
    }

    fun publishNext(config: ReportingKafkaPublisherConfig): ReportingKafkaPublishResult? =
        transactions.execute<ReportingKafkaPublishResult?> {
            val event = repository.findNextPublishableOutboxForUpdate(config.eventTypes) ?: return@execute null
            publishLockedEvent(config, event)
        }

    private fun publishLockedEvent(
        config: ReportingKafkaPublisherConfig,
        event: ReportingOutboxRecord
    ): ReportingKafkaPublishResult {
        val producer = KafkaProducer<String, String>(producerProperties(config))
        return try {
            val record = ProducerRecord(
                config.topic,
                event.aggregateId,
                objectMapper.writeValueAsString(event.toKafkaEnvelope())
            )
            record.headers().add(header("outboxEventId", event.outboxEventId))
            record.headers().add(header("eventType", event.eventType))
            record.headers().add(header("sourceService", "reporting-service"))
            record.headers().add(header("syntheticOnly", "true"))

            val metadata = producer
                .send(record)
                .get(config.publishTimeoutMillis, TimeUnit.MILLISECONDS)
            repository.markOutboxPublished(event.outboxEventId)
            ReportingKafkaPublishResult(
                outboxEventId = event.outboxEventId,
                eventType = event.eventType,
                status = "PUBLISHED",
                topic = metadata.topic(),
                partition = metadata.partition(),
                offset = metadata.offset(),
                errorMessage = null
            )
        } catch (ex: Exception) {
            repository.markOutboxFailed(event.outboxEventId)
            ReportingKafkaPublishResult(
                outboxEventId = event.outboxEventId,
                eventType = event.eventType,
                status = "FAILED",
                topic = config.topic,
                partition = null,
                offset = null,
                errorMessage = summarize(ex)
            )
        } finally {
            producer.close(Duration.ZERO)
        }
    }

    private fun producerProperties(config: ReportingKafkaPublisherConfig): Properties =
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

    private fun ReportingOutboxRecord.toKafkaEnvelope(): ReportingOutboxKafkaEnvelope =
        ReportingOutboxKafkaEnvelope(
            outboxEventId = outboxEventId,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            idempotencyKey = idempotencyKey,
            payload = payload,
            headers = mapOf(
                "syntheticOnly" to true,
                "sourceService" to "reporting-service"
            )
        )

    private fun header(name: String, value: String): RecordHeader =
        RecordHeader(name, value.toByteArray(StandardCharsets.UTF_8))

    private fun summarize(ex: Exception): String =
        "${ex::class.simpleName}: ${ex.message ?: "reporting Kafka publish failed"}".take(500)
}
