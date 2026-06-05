package lab.banking.notification.eventing

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class NotificationEventConsumerMetrics(
    registry: MeterRegistry,
    @param:Value("\${banking-lab.notification-service.event-consumer.topic:banking.lab.domain-events}")
    private val topic: String,
    @param:Value("\${banking-lab.notification-service.event-consumer.worker.client-id:notification-service-event-consumer}")
    private val clientId: String
) {
    private val running = AtomicInteger(0)
    private val starts = counter(registry, "banking.lab.notification.consumer.starts", "Notification consumer starts")
    private val startFailures = counter(
        registry,
        "banking.lab.notification.consumer.start.failures",
        "Notification consumer failed start attempts"
    )
    private val stops = counter(registry, "banking.lab.notification.consumer.stops", "Notification consumer stops")
    private val batches = counter(registry, "banking.lab.notification.consumer.batches", "Notification consume batches")
    private val polled = counter(
        registry,
        "banking.lab.notification.consumer.records.polled",
        "Kafka records polled by the notification consumer"
    )
    private val processed = counter(
        registry,
        "banking.lab.notification.consumer.records.processed",
        "Kafka records processed into new notification inbox entries"
    )
    private val duplicates = counter(
        registry,
        "banking.lab.notification.consumer.records.duplicates",
        "Kafka records absorbed by notification inbox idempotency"
    )
    private val skipped = counter(
        registry,
        "banking.lab.notification.consumer.records.skipped",
        "Kafka records skipped because their event type is not notification-routed"
    )
    private val createdDeliveries = counter(
        registry,
        "banking.lab.notification.consumer.deliveries.created",
        "Notification delivery requests created by Kafka consumption"
    )
    private val batchFailures = counter(
        registry,
        "banking.lab.notification.consumer.batch.failures",
        "Notification consumer batch failures"
    )

    init {
        Gauge
            .builder("banking.lab.notification.consumer.running", running) { it.get().toDouble() }
            .description("1 when the notification event consumer is running, otherwise 0")
            .tags(*tags())
            .register(registry)
    }

    fun recordStarted() {
        running.set(1)
        starts.increment()
    }

    fun recordStartFailed() {
        running.set(0)
        startFailures.increment()
    }

    fun recordStopped() {
        running.set(0)
        stops.increment()
    }

    fun recordBatch(result: NotificationKafkaConsumeBatchResult) {
        batches.increment()
        increment(polled, result.polled)
        increment(processed, result.processed)
        increment(duplicates, result.duplicates)
        increment(skipped, result.skipped)
        increment(createdDeliveries, result.createdDeliveries)
    }

    fun recordBatchFailure() {
        batchFailures.increment()
    }

    private fun counter(registry: MeterRegistry, name: String, description: String): Counter =
        Counter
            .builder(name)
            .description(description)
            .tags(*tags())
            .register(registry)

    private fun increment(counter: Counter, amount: Int) {
        if (amount > 0) {
            counter.increment(amount.toDouble())
        }
    }

    private fun tags(): Array<String> =
        arrayOf("topic", topic, "client_id", clientId)
}
