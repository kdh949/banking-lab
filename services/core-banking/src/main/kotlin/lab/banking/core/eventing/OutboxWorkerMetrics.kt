package lab.banking.core.eventing

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class OutboxWorkerMetrics(
    registry: MeterRegistry,
    @param:Value("\${banking-lab.outbox.topic:banking.lab.domain-events}")
    private val topic: String,
    @param:Value("\${banking-lab.outbox.worker.client-id:core-banking-outbox-worker}")
    private val clientId: String
) {
    private val running = AtomicInteger(0)
    private val starts = counter(registry, "banking.lab.outbox.worker.starts", "Outbox worker successful starts")
    private val startFailures = counter(
        registry,
        "banking.lab.outbox.worker.start.failures",
        "Outbox worker failed start attempts"
    )
    private val stops = counter(registry, "banking.lab.outbox.worker.stops", "Outbox worker stops")
    private val batches = counter(registry, "banking.lab.outbox.worker.batches", "Outbox worker publish batches")
    private val attempted = counter(
        registry,
        "banking.lab.outbox.worker.events.attempted",
        "Outbox events selected for publish attempts"
    )
    private val published = counter(
        registry,
        "banking.lab.outbox.worker.events.published",
        "Outbox events published after broker acknowledgement"
    )
    private val failed = counter(
        registry,
        "banking.lab.outbox.worker.events.failed",
        "Outbox events left retryable after publish failure"
    )
    private val deadLettered = counter(
        registry,
        "banking.lab.outbox.worker.events.dead.lettered",
        "Outbox events moved to durable dead-letter state"
    )
    private val batchFailures = counter(
        registry,
        "banking.lab.outbox.worker.batch.failures",
        "Outbox worker publish loop failures"
    )

    init {
        Gauge
            .builder("banking.lab.outbox.worker.running", running) { it.get().toDouble() }
            .description("1 when the durable outbox worker is running, otherwise 0")
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

    fun recordBatch(result: KafkaOutboxPublishBatchResult) {
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
