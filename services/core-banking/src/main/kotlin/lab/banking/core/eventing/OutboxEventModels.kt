package lab.banking.core.eventing

import java.time.OffsetDateTime

enum class OutboxEventStatus {
    PENDING,
    PUBLISHED,
    FAILED,
    DEAD_LETTER
}

data class CreateOutboxEventCommand(
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val idempotencyKey: String,
    val payload: Map<String, Any?>,
    val headers: Map<String, Any?> = mapOf("syntheticOnly" to true)
)

data class OutboxEventRecord(
    val outboxEventId: String,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val idempotencyKey: String,
    val payload: Map<String, Any?>,
    val headers: Map<String, Any?>,
    val status: OutboxEventStatus,
    val retryCount: Int,
    val nextRetryAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val publishedAt: OffsetDateTime?,
    val errorMessage: String?
)

data class InboxRecordResult(
    val consumerName: String,
    val sourceEventId: String,
    val processedNow: Boolean
)

data class OutboxKafkaEnvelope(
    val outboxEventId: String,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val occurredAt: String? = null,
    val sourceService: String = "core-banking-service",
    val schemaVersion: String? = null,
    val idempotencyKey: String,
    val payload: Map<String, Any?>,
    val headers: Map<String, Any?>
)

data class KafkaOutboxPublisherConfig(
    val bootstrapServers: String,
    val topic: String = "banking.lab.domain-events",
    val clientId: String = "core-banking-outbox-publisher",
    val publishTimeoutMillis: Long = 5_000,
    val deadLetterThreshold: Int = 3,
    val retryDelaySeconds: Long = 60,
    val crashAfterBrokerAckEventId: String? = null,
    val crashAfterBrokerAckExitCode: Int = 88
)

data class KafkaOutboxPublishResult(
    val outboxEventId: String,
    val status: OutboxEventStatus,
    val topic: String,
    val partition: Int?,
    val offset: Long?,
    val errorMessage: String?
)

data class KafkaOutboxPublishBatchResult(
    val attempted: Int,
    val published: Int,
    val failed: Int,
    val deadLettered: Int,
    val results: List<KafkaOutboxPublishResult>
)

data class KafkaInboxConsumerConfig(
    val bootstrapServers: String,
    val topic: String = "banking.lab.domain-events",
    val groupId: String,
    val consumerName: String,
    val clientId: String = "$consumerName-inbox-consumer",
    val pollTimeoutMillis: Long = 5_000
)

data class KafkaInboxConsumeResult(
    val sourceEventId: String,
    val eventType: String,
    val processedNow: Boolean
)

data class KafkaInboxConsumeBatchResult(
    val polled: Int,
    val processed: Int,
    val duplicates: Int,
    val results: List<KafkaInboxConsumeResult>
)
