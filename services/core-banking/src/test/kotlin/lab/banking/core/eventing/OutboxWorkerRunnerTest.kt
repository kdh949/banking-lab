package lab.banking.core.eventing

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class OutboxWorkerRunnerTest {
    @Test
    fun `runOneBatch delegates configured publisher settings and records metrics`() {
        val registry = SimpleMeterRegistry()
        val publisher = RecordingOutboxPublisher(
            KafkaOutboxPublishBatchResult(
                attempted = 3,
                published = 2,
                failed = 1,
                deadLettered = 0,
                results = emptyList()
            )
        )
        val properties = OutboxWorkerProperties(
            bootstrapServers = "redpanda:9092",
            topic = "banking.lab.outbox.test",
            worker = OutboxWorkerProperties.Worker(
                enabled = true,
                clientId = "test-outbox-worker",
                batchSize = 7,
                pollIntervalMillis = 250,
                publishTimeoutMillis = 9_000,
                deadLetterThreshold = 5,
                retryDelaySeconds = 3
            ),
            fault = OutboxWorkerProperties.Fault(
                crashAfterBrokerAckEventId = "OBX-FAULT-001",
                crashAfterBrokerAckExitCode = 91
            )
        )
        val metrics = OutboxWorkerMetrics(registry, "banking.lab.outbox.test", "test-outbox-worker")
        val runner = OutboxWorkerRunner(publisher, properties, metrics)

        val result = runner.runOneBatch()

        assertEquals(3, result.attempted)
        assertEquals(7, publisher.lastLimit)
        assertEquals("redpanda:9092", publisher.lastConfig?.bootstrapServers)
        assertEquals("banking.lab.outbox.test", publisher.lastConfig?.topic)
        assertEquals("test-outbox-worker", publisher.lastConfig?.clientId)
        assertEquals(9_000, publisher.lastConfig?.publishTimeoutMillis)
        assertEquals(5, publisher.lastConfig?.deadLetterThreshold)
        assertEquals(3, publisher.lastConfig?.retryDelaySeconds)
        assertEquals("OBX-FAULT-001", publisher.lastConfig?.crashAfterBrokerAckEventId)
        assertEquals(91, publisher.lastConfig?.crashAfterBrokerAckExitCode)
        assertCounter(registry, "banking.lab.outbox.worker.batches", 1.0)
        assertCounter(registry, "banking.lab.outbox.worker.events.attempted", 3.0)
        assertCounter(registry, "banking.lab.outbox.worker.events.published", 2.0)
        assertCounter(registry, "banking.lab.outbox.worker.events.failed", 1.0)
    }

    @Test
    fun `start is inert when worker property is disabled`() {
        val registry = SimpleMeterRegistry()
        val runner = OutboxWorkerRunner(
            RecordingOutboxPublisher(KafkaOutboxPublishBatchResult(0, 0, 0, 0, emptyList())),
            OutboxWorkerProperties(worker = OutboxWorkerProperties.Worker(enabled = false)),
            OutboxWorkerMetrics(registry, "banking.lab.domain-events", "core-banking-outbox-worker")
        )

        runner.start()

        assertFalse(runner.isRunning)
        assertCounter(registry, "banking.lab.outbox.worker.starts", 0.0)
    }

    private fun assertCounter(registry: SimpleMeterRegistry, name: String, expected: Double) {
        assertEquals(
            expected,
            registry
                .get(name)
                .counter()
                .count()
        )
    }

    private class RecordingOutboxPublisher(
        private val result: KafkaOutboxPublishBatchResult
    ) : OutboxPublisherPort {
        var lastConfig: KafkaOutboxPublisherConfig? = null
        var lastLimit: Int? = null

        override fun publishAvailable(config: KafkaOutboxPublisherConfig, limit: Int): KafkaOutboxPublishBatchResult {
            lastConfig = config
            lastLimit = limit
            return result
        }
    }
}
