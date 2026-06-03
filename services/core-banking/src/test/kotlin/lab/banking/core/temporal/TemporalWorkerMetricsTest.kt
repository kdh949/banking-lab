package lab.banking.core.temporal

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TemporalWorkerMetricsTest {
    @Test
    fun `Temporal worker metrics expose running state and lifecycle counters`() {
        val registry = SimpleMeterRegistry()
        val metrics = TemporalWorkerMetrics(registry, "default", "banking-case-workflows")

        assertGauge(registry, "banking.lab.temporal.worker.running", 0.0)

        metrics.recordStarted()
        assertGauge(registry, "banking.lab.temporal.worker.running", 1.0)
        assertCounter(registry, "banking.lab.temporal.worker.starts", 1.0)

        metrics.recordStartFailed()
        assertGauge(registry, "banking.lab.temporal.worker.running", 0.0)
        assertCounter(registry, "banking.lab.temporal.worker.start.failures", 1.0)

        metrics.recordStopped()
        assertGauge(registry, "banking.lab.temporal.worker.running", 0.0)
        assertCounter(registry, "banking.lab.temporal.worker.stops", 1.0)
    }

    private fun assertGauge(registry: SimpleMeterRegistry, name: String, expected: Double) {
        assertEquals(
            expected,
            registry
                .get(name)
                .tags("namespace", "default", "task_queue", "banking-case-workflows")
                .gauge()
                .value()
        )
    }

    private fun assertCounter(registry: SimpleMeterRegistry, name: String, expected: Double) {
        assertEquals(
            expected,
            registry
                .get(name)
                .tags("namespace", "default", "task_queue", "banking-case-workflows")
                .counter()
                .count()
        )
    }
}
