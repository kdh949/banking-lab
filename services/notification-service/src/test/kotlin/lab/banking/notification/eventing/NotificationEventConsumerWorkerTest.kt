package lab.banking.notification.eventing

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class NotificationEventConsumerWorkerTest {
    @Test
    fun `runOneBatch delegates configured consumer settings and records metrics`() {
        val registry = SimpleMeterRegistry()
        val consumer = RecordingConsumer(
            NotificationKafkaConsumeBatchResult(
                polled = 3,
                processed = 2,
                duplicates = 1,
                skipped = 0,
                createdDeliveries = 2,
                results = emptyList()
            )
        )
        val properties = NotificationEventConsumerProperties(
            bootstrapServers = "redpanda:9092",
            topic = "banking.lab.notification.test",
            worker = NotificationEventConsumerProperties.Worker(
                enabled = true,
                clientId = "notification-consumer-test",
                groupId = "notification-service-test",
                batchSize = 7,
                pollIntervalMillis = 250,
                pollTimeoutMillis = 9_000
            ),
            routing = NotificationEventConsumerProperties.Routing(
                defaultChannel = "PUSH",
                requestedBy = "notification-worker-test",
                supportedEventTypes = setOf("PaymentLedgerPostingRequested")
            )
        )
        val metrics = NotificationEventConsumerMetrics(registry, "banking.lab.notification.test", "notification-consumer-test")
        val worker = NotificationEventConsumerWorker(consumer, properties, metrics)

        val result = worker.runOneBatch()

        assertEquals(3, result.polled)
        assertEquals(7, consumer.lastLimit)
        assertEquals("redpanda:9092", consumer.lastConfig?.bootstrapServers)
        assertEquals("banking.lab.notification.test", consumer.lastConfig?.topic)
        assertEquals("notification-consumer-test", consumer.lastConfig?.clientId)
        assertEquals("notification-service-test", consumer.lastConfig?.groupId)
        assertEquals(9_000, consumer.lastConfig?.pollTimeoutMillis)
        assertEquals("PUSH", consumer.lastConfig?.defaultChannel)
        assertEquals("notification-worker-test", consumer.lastConfig?.requestedBy)
        assertEquals(setOf("PaymentLedgerPostingRequested"), consumer.lastConfig?.supportedEventTypes)
        assertCounter(registry, "banking.lab.notification.consumer.batches", 1.0)
        assertCounter(registry, "banking.lab.notification.consumer.records.polled", 3.0)
        assertCounter(registry, "banking.lab.notification.consumer.records.processed", 2.0)
        assertCounter(registry, "banking.lab.notification.consumer.records.duplicates", 1.0)
        assertCounter(registry, "banking.lab.notification.consumer.deliveries.created", 2.0)
    }

    @Test
    fun `start is inert when worker property is disabled`() {
        val registry = SimpleMeterRegistry()
        val worker = NotificationEventConsumerWorker(
            RecordingConsumer(NotificationKafkaConsumeBatchResult(0, 0, 0, 0, 0, emptyList())),
            NotificationEventConsumerProperties(worker = NotificationEventConsumerProperties.Worker(enabled = false)),
            NotificationEventConsumerMetrics(registry, "banking.lab.domain-events", "notification-service-event-consumer")
        )

        worker.start()

        assertFalse(worker.isRunning)
        assertCounter(registry, "banking.lab.notification.consumer.starts", 0.0)
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

    private class RecordingConsumer(
        private val result: NotificationKafkaConsumeBatchResult
    ) : NotificationEventConsumerPort {
        var lastConfig: NotificationKafkaConsumerConfig? = null
        var lastLimit: Int? = null

        override fun consumeAvailable(
            config: NotificationKafkaConsumerConfig,
            maxRecords: Int
        ): NotificationKafkaConsumeBatchResult {
            lastConfig = config
            lastLimit = maxRecords
            return result
        }
    }
}
