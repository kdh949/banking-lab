package lab.banking.core.eventing

interface OutboxPublisherPort {
    fun publishAvailable(config: KafkaOutboxPublisherConfig, limit: Int = 100): KafkaOutboxPublishBatchResult
}
