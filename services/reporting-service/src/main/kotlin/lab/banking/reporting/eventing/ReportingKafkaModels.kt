package lab.banking.reporting.eventing

data class ReportingOutboxRecord(
    val outboxEventId: String,
    val eventType: String,
    val aggregateType: String,
    val aggregateId: String,
    val idempotencyKey: String?,
    val payload: Map<String, Any?>,
    val retryCount: Int = 0,
    val errorMessage: String? = null
)

data class ReportingOutboxKafkaEnvelope(
    val outboxEventId: String,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val idempotencyKey: String?,
    val payload: Map<String, Any?>,
    val headers: Map<String, Any?> = emptyMap()
)

data class ReportingKafkaPublisherConfig(
    val bootstrapServers: String,
    val topic: String = "banking.lab.reporting-events",
    val clientId: String = "reporting-service-domain-event-publisher",
    val publishTimeoutMillis: Long = 5_000,
    val deadLetterThreshold: Int = 3,
    val retryDelaySeconds: Int = 60,
    val eventTypes: Set<String> = ReportingOutboxPublisherPort.defaultEventTypes
)

data class ReportingKafkaPublishResult(
    val outboxEventId: String,
    val eventType: String,
    val status: String,
    val topic: String,
    val partition: Int?,
    val offset: Long?,
    val errorMessage: String?
)

data class ReportingKafkaPublishBatchResult(
    val attempted: Int,
    val published: Int,
    val failed: Int,
    val deadLettered: Int,
    val results: List<ReportingKafkaPublishResult>
)

interface ReportingOutboxPublisherPort {
    fun publishAvailable(config: ReportingKafkaPublisherConfig, limit: Int = 100): ReportingKafkaPublishBatchResult

    companion object {
        val defaultEventTypes = setOf(
            "ReportArtifactGenerated",
            "ReportArtifactExported",
            "ReportRetentionSweepCompleted"
        )
    }
}
