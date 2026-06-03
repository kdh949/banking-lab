package lab.banking.core.eventing

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.Properties
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.stereotype.Service

@Service
class KafkaInboxConsumer(
    private val outboxService: DurableOutboxService,
    private val objectMapper: ObjectMapper
) {
    fun consumeAvailable(config: KafkaInboxConsumerConfig, maxRecords: Int = 100): KafkaInboxConsumeBatchResult {
        require(maxRecords > 0) { "maxRecords must be positive" }
        val deadline = System.nanoTime() + Duration.ofMillis(config.pollTimeoutMillis).toNanos()
        val results = mutableListOf<KafkaInboxConsumeResult>()

        KafkaConsumer<String, String>(consumerProperties(config, maxRecords)).use { consumer ->
            consumer.subscribe(listOf(config.topic))
            while (results.size < maxRecords && System.nanoTime() < deadline) {
                val remainingMillis = Duration.ofNanos(deadline - System.nanoTime()).toMillis().coerceAtLeast(1)
                val records = consumer.poll(Duration.ofMillis(remainingMillis.coerceAtMost(250)))
                for (record in records) {
                    val envelope = objectMapper.readValue(record.value(), OutboxKafkaEnvelope::class.java)
                    val recorded = outboxService.recordInboxProcessed(
                        consumerName = config.consumerName,
                        sourceEventId = envelope.outboxEventId,
                        eventType = envelope.eventType,
                        payload = envelope.payload
                    )
                    results += KafkaInboxConsumeResult(
                        sourceEventId = envelope.outboxEventId,
                        eventType = envelope.eventType,
                        processedNow = recorded.processedNow
                    )
                    if (results.size == maxRecords) {
                        break
                    }
                }
            }
            if (results.isNotEmpty()) {
                consumer.commitSync()
            }
        }

        return KafkaInboxConsumeBatchResult(
            polled = results.size,
            processed = results.count { it.processedNow },
            duplicates = results.count { !it.processedNow },
            results = results
        )
    }

    private fun consumerProperties(config: KafkaInboxConsumerConfig, maxRecords: Int): Properties =
        Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers)
            put(ConsumerConfig.GROUP_ID_CONFIG, config.groupId)
            put(ConsumerConfig.CLIENT_ID_CONFIG, config.clientId)
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
            put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxRecords.toString())
        }
}
