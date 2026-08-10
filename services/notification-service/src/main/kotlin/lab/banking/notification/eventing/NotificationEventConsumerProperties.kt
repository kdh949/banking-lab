package lab.banking.notification.eventing

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "banking-lab.notification-service.event-consumer")
data class NotificationEventConsumerProperties(
    val bootstrapServers: String = "127.0.0.1:9092",
    val topic: String = "banking.lab.domain-events",
    val worker: Worker = Worker(),
    val routing: Routing = Routing()
) {
    data class Worker(
        val enabled: Boolean = false,
        val clientId: String = "notification-service-event-consumer",
        val groupId: String = "notification-service",
        val batchSize: Int = 100,
        val pollIntervalMillis: Long = 1_000,
        val pollTimeoutMillis: Long = 5_000
    )

    data class Routing(
        val defaultChannel: String = "SMS",
        val requestedBy: String = "notification-kafka-consumer",
        val supportedEventTypes: Set<String> = setOf(
            "LedgerTransactionPosted",
            "PaymentLedgerPostingRequested",
            "ComplaintAnswered",
            "CustomerTransferStatusChanged"
        )
    )

    fun consumerConfig(): NotificationKafkaConsumerConfig =
        NotificationKafkaConsumerConfig(
            bootstrapServers = bootstrapServers,
            topic = topic,
            groupId = worker.groupId,
            clientId = worker.clientId,
            pollTimeoutMillis = worker.pollTimeoutMillis,
            defaultChannel = routing.defaultChannel,
            requestedBy = routing.requestedBy,
            supportedEventTypes = routing.supportedEventTypes
        )
}
