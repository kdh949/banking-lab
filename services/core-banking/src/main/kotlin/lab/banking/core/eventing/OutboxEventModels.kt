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
