package lab.banking.core.temporal

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class TemporalWorkerMetrics(
    registry: MeterRegistry,
    @param:Value("\${banking-lab.temporal.namespace:default}")
    private val namespace: String,
    @param:Value("\${banking-lab.temporal.task-queue:banking-case-workflows}")
    private val taskQueue: String
) {
    private val running = AtomicInteger(0)
    private val starts = counter(registry, "banking.lab.temporal.worker.starts", "Temporal worker successful starts")
    private val startFailures = counter(
        registry,
        "banking.lab.temporal.worker.start.failures",
        "Temporal worker failed start attempts"
    )
    private val stops = counter(registry, "banking.lab.temporal.worker.stops", "Temporal worker stops")

    init {
        Gauge
            .builder("banking.lab.temporal.worker.running", running) { it.get().toDouble() }
            .description("1 when the banking case Temporal worker is running, otherwise 0")
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

    private fun counter(registry: MeterRegistry, name: String, description: String): Counter =
        Counter
            .builder(name)
            .description(description)
            .tags(*tags())
            .register(registry)

    private fun tags(): Array<String> =
        arrayOf("namespace", namespace, "task_queue", taskQueue)
}
