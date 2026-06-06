package lab.banking.notification.eventing

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.Properties
import lab.banking.notification.domain.ConsumeNotificationEventRequest
import lab.banking.notification.domain.NotificationDeliveryService
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.stereotype.Service

@Service
class NotificationKafkaConsumer(
    private val notificationDeliveryService: NotificationDeliveryService,
    private val objectMapper: ObjectMapper
) : NotificationEventConsumerPort {
    override fun consumeAvailable(
        config: NotificationKafkaConsumerConfig,
        maxRecords: Int
    ): NotificationKafkaConsumeBatchResult {
        require(maxRecords > 0) { "maxRecords must be positive" }
        require(config.pollTimeoutMillis > 0) { "pollTimeoutMillis must be positive" }
        val deadline = System.nanoTime() + Duration.ofMillis(config.pollTimeoutMillis).toNanos()
        val results = mutableListOf<NotificationKafkaConsumeResult>()

        KafkaConsumer<String, String>(consumerProperties(config, maxRecords)).use { consumer ->
            consumer.subscribe(listOf(config.topic))
            while (results.size < maxRecords && System.nanoTime() < deadline) {
                val remainingMillis = Duration.ofNanos(deadline - System.nanoTime()).toMillis().coerceAtLeast(1)
                val records = consumer.poll(Duration.ofMillis(remainingMillis.coerceAtMost(250)))
                for (record in records) {
                    val envelope = objectMapper.readValue(record.value(), NotificationOutboxKafkaEnvelope::class.java)
                    results += consumeEnvelope(config, envelope)
                    if (results.size == maxRecords) {
                        break
                    }
                }
            }
            if (results.isNotEmpty()) {
                consumer.commitSync()
            }
        }

        return NotificationKafkaConsumeBatchResult(
            polled = results.size,
            processed = results.count { it.processedNow },
            duplicates = results.count { it.replayed },
            skipped = results.count { it.skipped },
            createdDeliveries = results.sumOf { it.createdDeliveries },
            results = results
        )
    }

    private fun consumeEnvelope(
        config: NotificationKafkaConsumerConfig,
        envelope: NotificationOutboxKafkaEnvelope
    ): NotificationKafkaConsumeResult {
        requireEnvelopeMetadata(envelope)
        requireSyntheticOnly(envelope)
        if (envelope.eventType !in config.supportedEventTypes) {
            return NotificationKafkaConsumeResult(
                sourceEventId = envelope.outboxEventId,
                eventType = envelope.eventType,
                processedNow = false,
                replayed = false,
                skipped = true,
                createdDeliveries = 0,
                skipReason = "unsupported event type for notification routing"
            )
        }

        val response = notificationDeliveryService.consumeEvent(
            ConsumeNotificationEventRequest(
                sourceEventId = envelope.outboxEventId,
                eventType = envelope.eventType,
                recipientId = recipientId(envelope),
                channel = channel(envelope, config.defaultChannel),
                payload = normalizedPayload(envelope),
                requestedBy = config.requestedBy
            )
        )
        return NotificationKafkaConsumeResult(
            sourceEventId = envelope.outboxEventId,
            eventType = envelope.eventType,
            processedNow = !response.replayed,
            replayed = response.replayed,
            skipped = false,
            createdDeliveries = if (response.replayed) 0 else response.items.size
        )
    }

    private fun consumerProperties(config: NotificationKafkaConsumerConfig, maxRecords: Int): Properties =
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

    private fun requireSyntheticOnly(envelope: NotificationOutboxKafkaEnvelope) {
        val syntheticOnly = booleanValue(envelope.payload["syntheticOnly"])
            ?: booleanValue(envelope.headers["syntheticOnly"])
        require(syntheticOnly == true) {
            "notification kafka consumer accepts only synthetic-only outbox events"
        }
    }

    private fun requireEnvelopeMetadata(envelope: NotificationOutboxKafkaEnvelope) {
        require(firstString(envelope.headers["sourceService"]) != null) {
            "notification kafka consumer requires sourceService envelope metadata"
        }
        require(firstString(envelope.headers["eventType"], envelope.eventType) == envelope.eventType) {
            "notification kafka consumer requires matching eventType envelope metadata"
        }
        require(firstString(envelope.headers["aggregateId"], envelope.aggregateId) == envelope.aggregateId) {
            "notification kafka consumer requires matching aggregateId envelope metadata"
        }
        require(firstString(envelope.occurredAt, envelope.headers["occurredAt"]) != null) {
            "notification kafka consumer requires occurredAt envelope metadata"
        }
    }

    private fun recipientId(envelope: NotificationOutboxKafkaEnvelope): String =
        firstString(
            envelope.payload["recipientId"],
            envelope.headers["recipientId"],
            envelope.payload["customerId"],
            envelope.payload["staffId"],
            envelope.payload["accountId"],
            envelope.aggregateId
        ) ?: error("notification kafka event recipient cannot be derived")

    private fun channel(envelope: NotificationOutboxKafkaEnvelope, defaultChannel: String): String =
        firstString(
            envelope.payload["notificationChannel"],
            envelope.headers["notificationChannel"],
            envelope.payload["channel"],
            envelope.headers["channel"],
            defaultChannel
        ) ?: defaultChannel

    private fun normalizedPayload(envelope: NotificationOutboxKafkaEnvelope): Map<String, Any?> {
        val normalized = linkedMapOf<String, Any?>()
        normalized.putAll(envelope.payload)
        normalized.putIfAbsent("sourceEventId", envelope.outboxEventId)
        normalized.putIfAbsent("aggregateId", envelope.aggregateId)
        normalized.putIfAbsent("idempotencyKey", envelope.idempotencyKey)
        firstString(normalized["ledgerTransactionId"], normalized["paymentInstructionId"], envelope.aggregateId)
            ?.let { normalized.putIfAbsent("transactionId", it) }
        firstString(
            normalized["accountNo"],
            normalized["accountNumber"],
            normalized["debitAccountId"],
            normalized["accountId"]
        )?.let { normalized.putIfAbsent("accountNo", it) }
        return normalized
    }

    private fun firstString(vararg values: Any?): String? =
        values.firstNotNullOfOrNull { value ->
            value?.toString()?.takeIf { it.isNotBlank() }
        }

    private fun booleanValue(value: Any?): Boolean? =
        when (value) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            else -> null
        }
}
