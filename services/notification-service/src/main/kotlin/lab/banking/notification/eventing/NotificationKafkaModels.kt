package lab.banking.notification.eventing

data class NotificationOutboxKafkaEnvelope(
    val outboxEventId: String,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val occurredAt: String? = null,
    val sourceService: String? = null,
    val schemaVersion: String? = null,
    val idempotencyKey: String,
    val payload: Map<String, Any?>,
    val headers: Map<String, Any?> = emptyMap()
)

data class NotificationKafkaConsumerConfig(
    val bootstrapServers: String,
    val topic: String = "banking.lab.domain-events",
    val groupId: String = "notification-service",
    val clientId: String = "notification-service-event-consumer",
    val pollTimeoutMillis: Long = 5_000,
    val defaultChannel: String = "SMS",
    val requestedBy: String = "notification-kafka-consumer",
    val supportedEventTypes: Set<String> = setOf(
        "LedgerTransactionPosted",
        "PaymentLedgerPostingRequested",
        "ComplaintAnswered"
    )
)

data class NotificationKafkaConsumeResult(
    val sourceEventId: String,
    val eventType: String,
    val processedNow: Boolean,
    val replayed: Boolean,
    val skipped: Boolean,
    val createdDeliveries: Int,
    val skipReason: String? = null
)

data class NotificationKafkaConsumeBatchResult(
    val polled: Int,
    val processed: Int,
    val duplicates: Int,
    val skipped: Int,
    val createdDeliveries: Int,
    val results: List<NotificationKafkaConsumeResult>
)

interface NotificationEventConsumerPort {
    fun consumeAvailable(
        config: NotificationKafkaConsumerConfig,
        maxRecords: Int
    ): NotificationKafkaConsumeBatchResult
}
