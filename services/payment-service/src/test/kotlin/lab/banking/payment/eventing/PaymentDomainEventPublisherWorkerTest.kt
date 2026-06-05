package lab.banking.payment.eventing

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class PaymentDomainEventPublisherWorkerTest {
    @Test
    fun `runOneBatch delegates configured publisher settings and records metrics`() {
        val registry = SimpleMeterRegistry()
        val publisher = RecordingPublisher(
            PaymentKafkaPublishBatchResult(
                attempted = 3,
                published = 2,
                failed = 1,
                deadLettered = 0,
                results = emptyList()
            )
        )
        val properties = PaymentDomainEventPublisherProperties(
            bootstrapServers = "redpanda:9092",
            topic = "banking.lab.payment.test",
            worker = PaymentDomainEventPublisherProperties.Worker(
                enabled = true,
                clientId = "payment-publisher-test",
                batchSize = 7,
                pollIntervalMillis = 250
            ),
            publish = PaymentDomainEventPublisherProperties.Publish(
                timeoutMillis = 9_000,
                deadLetterThreshold = 5,
                retryDelaySeconds = 45,
                eventTypes = setOf("PaymentInstructionCanceled")
            )
        )
        val metrics = PaymentDomainEventPublisherMetrics(registry, "banking.lab.payment.test", "payment-publisher-test")
        val worker = PaymentDomainEventPublisherWorker(publisher, properties, metrics)

        val result = worker.runOneBatch()

        assertEquals(3, result.attempted)
        assertEquals(7, publisher.lastLimit)
        assertEquals("redpanda:9092", publisher.lastConfig?.bootstrapServers)
        assertEquals("banking.lab.payment.test", publisher.lastConfig?.topic)
        assertEquals("payment-publisher-test", publisher.lastConfig?.clientId)
        assertEquals(9_000, publisher.lastConfig?.publishTimeoutMillis)
        assertEquals(5, publisher.lastConfig?.deadLetterThreshold)
        assertEquals(45, publisher.lastConfig?.retryDelaySeconds)
        assertEquals(setOf("PaymentInstructionCanceled"), publisher.lastConfig?.eventTypes)
        assertCounter(registry, "banking.lab.payment.publisher.batches", 1.0)
        assertCounter(registry, "banking.lab.payment.publisher.events.attempted", 3.0)
        assertCounter(registry, "banking.lab.payment.publisher.events.published", 2.0)
        assertCounter(registry, "banking.lab.payment.publisher.events.failed", 1.0)
    }

    @Test
    fun `start is inert when worker property is disabled`() {
        val registry = SimpleMeterRegistry()
        val worker = PaymentDomainEventPublisherWorker(
            RecordingPublisher(PaymentKafkaPublishBatchResult(0, 0, 0, 0, emptyList())),
            PaymentDomainEventPublisherProperties(worker = PaymentDomainEventPublisherProperties.Worker(enabled = false)),
            PaymentDomainEventPublisherMetrics(registry, "banking.lab.payment-events", "payment-service-domain-event-publisher")
        )

        worker.start()

        assertFalse(worker.isRunning)
        assertCounter(registry, "banking.lab.payment.publisher.starts", 0.0)
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

    private class RecordingPublisher(
        private val result: PaymentKafkaPublishBatchResult
    ) : PaymentOutboxPublisherPort {
        var lastConfig: PaymentKafkaPublisherConfig? = null
        var lastLimit: Int? = null

        override fun publishAvailable(config: PaymentKafkaPublisherConfig, limit: Int): PaymentKafkaPublishBatchResult {
            lastConfig = config
            lastLimit = limit
            return result
        }
    }
}
