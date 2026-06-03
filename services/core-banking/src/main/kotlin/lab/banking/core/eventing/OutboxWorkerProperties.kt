package lab.banking.core.eventing

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "banking-lab.outbox")
data class OutboxWorkerProperties(
    val bootstrapServers: String = "127.0.0.1:9092",
    val topic: String = "banking.lab.domain-events",
    val worker: Worker = Worker(),
    val fault: Fault = Fault()
) {
    data class Worker(
        val enabled: Boolean = false,
        val clientId: String = "core-banking-outbox-worker",
        val batchSize: Int = 100,
        val pollIntervalMillis: Long = 1_000,
        val publishTimeoutMillis: Long = 5_000,
        val deadLetterThreshold: Int = 3,
        val retryDelaySeconds: Long = 60
    )

    data class Fault(
        val crashAfterBrokerAckEventId: String? = null,
        val crashAfterBrokerAckExitCode: Int = 88
    )

    fun publisherConfig(): KafkaOutboxPublisherConfig =
        KafkaOutboxPublisherConfig(
            bootstrapServers = bootstrapServers,
            topic = topic,
            clientId = worker.clientId,
            publishTimeoutMillis = worker.publishTimeoutMillis,
            deadLetterThreshold = worker.deadLetterThreshold,
            retryDelaySeconds = worker.retryDelaySeconds,
            crashAfterBrokerAckEventId = fault.crashAfterBrokerAckEventId?.takeIf { it.isNotBlank() },
            crashAfterBrokerAckExitCode = fault.crashAfterBrokerAckExitCode
        )
}
