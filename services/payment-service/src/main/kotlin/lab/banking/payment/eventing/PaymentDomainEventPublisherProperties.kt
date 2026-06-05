package lab.banking.payment.eventing

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "banking-lab.payment-service.domain-event-publisher")
data class PaymentDomainEventPublisherProperties(
    val bootstrapServers: String = "127.0.0.1:9092",
    val topic: String = "banking.lab.payment-events",
    val worker: Worker = Worker(),
    val publish: Publish = Publish()
) {
    data class Worker(
        val enabled: Boolean = false,
        val clientId: String = "payment-service-domain-event-publisher",
        val batchSize: Int = 100,
        val pollIntervalMillis: Long = 1_000
    )

    data class Publish(
        val timeoutMillis: Long = 5_000,
        val deadLetterThreshold: Int = 3,
        val retryDelaySeconds: Int = 60,
        val eventTypes: Set<String> = PaymentOutboxPublisherPort.defaultEventTypes
    )

    fun publisherConfig(): PaymentKafkaPublisherConfig =
        PaymentKafkaPublisherConfig(
            bootstrapServers = bootstrapServers,
            topic = topic,
            clientId = worker.clientId,
            publishTimeoutMillis = publish.timeoutMillis,
            deadLetterThreshold = publish.deadLetterThreshold,
            retryDelaySeconds = publish.retryDelaySeconds,
            eventTypes = publish.eventTypes
        )
}
