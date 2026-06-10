package lab.banking.payment.eventing

data class PaymentOutboxKafkaEnvelope(
    val outboxEventId: String,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val occurredAt: String,
    val sourceService: String = "payment-service",
    val schemaVersion: String? = null,
    val idempotencyKey: String,
    val payload: Map<String, Any?>,
    val headers: Map<String, Any?> = emptyMap()
)

data class PaymentKafkaPublisherConfig(
    val bootstrapServers: String,
    val topic: String = "banking.lab.payment-events",
    val clientId: String = "payment-service-outbox-publisher",
    val publishTimeoutMillis: Long = 10_000,
    val deadLetterThreshold: Int = 3,
    val retryDelaySeconds: Int = 300,
    val eventTypes: Set<String> = PaymentOutboxPublisherPort.defaultEventTypes
)

data class PaymentKafkaPublishResult(
    val outboxEventId: String,
    val eventType: String,
    val status: String,
    val topic: String,
    val partition: Int?,
    val offset: Long?,
    val errorMessage: String?
)

data class PaymentKafkaPublishBatchResult(
    val attempted: Int,
    val published: Int,
    val failed: Int,
    val deadLettered: Int,
    val results: List<PaymentKafkaPublishResult>
)

interface PaymentOutboxPublisherPort {
    fun publishAvailable(config: PaymentKafkaPublisherConfig, limit: Int = 100): PaymentKafkaPublishBatchResult

    companion object {
        val defaultEventTypes = setOf(
            "PaymentInstructionSettled",
            "PaymentInstructionCanceled",
            "PaymentInstructionFailed",
            "PaymentInstructionRetryScheduled",
            "PaymentInstructionDeadLettered",
            "PaymentAutopayExecutionCreated"
        )
    }
}
