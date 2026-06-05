package lab.banking.payment.eventing

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class PaymentDomainEventPublisherMetrics(
    registry: MeterRegistry,
    @param:Value("\${banking-lab.payment-service.domain-event-publisher.topic:banking.lab.payment-events}")
    private val topic: String,
    @param:Value("\${banking-lab.payment-service.domain-event-publisher.worker.client-id:payment-service-domain-event-publisher}")
    private val clientId: String
) {
    private val running = AtomicInteger(0)
    private val starts = counter(registry, "banking.lab.payment.publisher.starts", "Payment domain event publisher starts")
    private val startFailures = counter(
        registry,
        "banking.lab.payment.publisher.start.failures",
        "Payment domain event publisher failed start attempts"
    )
    private val stops = counter(registry, "banking.lab.payment.publisher.stops", "Payment domain event publisher stops")
    private val batches = counter(registry, "banking.lab.payment.publisher.batches", "Payment domain event publish batches")
    private val attempted = counter(
        registry,
        "banking.lab.payment.publisher.events.attempted",
        "Payment domain events selected for publish attempts"
    )
    private val published = counter(
        registry,
        "banking.lab.payment.publisher.events.published",
        "Payment domain events published after broker acknowledgement"
    )
    private val failed = counter(
        registry,
        "banking.lab.payment.publisher.events.failed",
        "Payment domain events left retryable after publish failure"
    )
    private val deadLettered = counter(
        registry,
        "banking.lab.payment.publisher.events.dead.lettered",
        "Payment domain events moved to durable dead-letter state"
    )
    private val batchFailures = counter(
        registry,
        "banking.lab.payment.publisher.batch.failures",
        "Payment domain event publisher batch failures"
    )

    init {
        Gauge
            .builder("banking.lab.payment.publisher.running", running) { it.get().toDouble() }
            .description("1 when the payment domain event publisher is running, otherwise 0")
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

    fun recordBatch(result: PaymentKafkaPublishBatchResult) {
        batches.increment()
        increment(attempted, result.attempted)
        increment(published, result.published)
        increment(failed, result.failed)
        increment(deadLettered, result.deadLettered)
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
