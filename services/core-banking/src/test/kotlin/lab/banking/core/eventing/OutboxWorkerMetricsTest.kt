package lab.banking.core.eventing

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OutboxWorkerMetricsTest {
    @Test
    fun `Outbox worker metrics expose running state lifecycle counters and publish counters`() {
        val registry = SimpleMeterRegistry()
        val metrics = OutboxWorkerMetrics(registry, "banking.lab.domain-events", "core-banking-outbox-worker")

        assertGauge(registry, "banking.lab.outbox.worker.running", 0.0)

        metrics.recordStarted()
        assertGauge(registry, "banking.lab.outbox.worker.running", 1.0)
        assertCounter(registry, "banking.lab.outbox.worker.starts", 1.0)

        metrics.recordBatch(
            KafkaOutboxPublishBatchResult(
                attempted = 4,
                published = 2,
                failed = 1,
                deadLettered = 1,
                results = emptyList()
            )
        )
        assertCounter(registry, "banking.lab.outbox.worker.batches", 1.0)
        assertCounter(registry, "banking.lab.outbox.worker.events.attempted", 4.0)
        assertCounter(registry, "banking.lab.outbox.worker.events.published", 2.0)
        assertCounter(registry, "banking.lab.outbox.worker.events.failed", 1.0)
        assertCounter(registry, "banking.lab.outbox.worker.events.dead.lettered", 1.0)

        metrics.recordBatchFailure()
        assertCounter(registry, "banking.lab.outbox.worker.batch.failures", 1.0)

        metrics.recordStartFailed()
        assertGauge(registry, "banking.lab.outbox.worker.running", 0.0)
        assertCounter(registry, "banking.lab.outbox.worker.start.failures", 1.0)

        metrics.recordStarted()
        metrics.recordStopped()
        assertGauge(registry, "banking.lab.outbox.worker.running", 0.0)
        assertCounter(registry, "banking.lab.outbox.worker.stops", 1.0)
    }

    private fun assertGauge(registry: SimpleMeterRegistry, name: String, expected: Double) {
        assertEquals(
            expected,
            registry
                .get(name)
                .tags("topic", "banking.lab.domain-events", "client_id", "core-banking-outbox-worker")
                .gauge()
                .value()
        )
    }

    private fun assertCounter(registry: SimpleMeterRegistry, name: String, expected: Double) {
        assertEquals(
            expected,
            registry
                .get(name)
                .tags("topic", "banking.lab.domain-events", "client_id", "core-banking-outbox-worker")
                .counter()
                .count()
        )
    }
}
