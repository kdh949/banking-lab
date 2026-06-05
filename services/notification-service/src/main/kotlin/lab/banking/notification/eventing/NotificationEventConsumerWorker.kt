package lab.banking.notification.eventing

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "banking-lab.notification-service.event-consumer.worker",
    name = ["enabled"],
    havingValue = "true"
)
class NotificationEventConsumerWorker(
    private val consumer: NotificationEventConsumerPort,
    private val properties: NotificationEventConsumerProperties,
    private val metrics: NotificationEventConsumerMetrics
) : SmartLifecycle {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)
    private var executor: ScheduledExecutorService? = null

    override fun start() {
        if (!properties.worker.enabled || !running.compareAndSet(false, true)) {
            return
        }
        try {
            validateWorkerConfig()
            val createdExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "notification-service-event-consumer").apply {
                    isDaemon = false
                }
            }
            executor = createdExecutor
            metrics.recordStarted()
            logger.info(
                "observability.notification.consumer event=started topic={} clientId={} groupId={} batchSize={} pollIntervalMillis={} syntheticOnly=true",
                properties.topic,
                properties.worker.clientId,
                properties.worker.groupId,
                properties.worker.batchSize,
                properties.worker.pollIntervalMillis
            )
            createdExecutor.scheduleWithFixedDelay(
                this::runScheduledBatch,
                0,
                properties.worker.pollIntervalMillis,
                TimeUnit.MILLISECONDS
            )
        } catch (error: RuntimeException) {
            executor?.shutdownNow()
            executor = null
            running.set(false)
            metrics.recordStartFailed()
            throw error
        }
    }

    override fun stop() {
        val wasRunning = running.getAndSet(false)
        executor?.shutdownNow()
        executor = null
        if (wasRunning) {
            metrics.recordStopped()
            logger.info(
                "observability.notification.consumer event=stopped topic={} clientId={} syntheticOnly=true",
                properties.topic,
                properties.worker.clientId
            )
        }
    }

    override fun isRunning(): Boolean =
        running.get()

    fun runOneBatch(): NotificationKafkaConsumeBatchResult {
        val result = consumer.consumeAvailable(properties.consumerConfig(), properties.worker.batchSize)
        metrics.recordBatch(result)
        if (result.polled > 0) {
            logger.info(
                "observability.notification.consumer event=batch topic={} clientId={} polled={} processed={} duplicates={} skipped={} deliveries={} syntheticOnly=true",
                properties.topic,
                properties.worker.clientId,
                result.polled,
                result.processed,
                result.duplicates,
                result.skipped,
                result.createdDeliveries
            )
        }
        return result
    }

    private fun runScheduledBatch() {
        try {
            runOneBatch()
        } catch (error: RuntimeException) {
            metrics.recordBatchFailure()
            logger.error(
                "observability.notification.consumer event=batch-failed topic={} clientId={} error={} syntheticOnly=true",
                properties.topic,
                properties.worker.clientId,
                error.message ?: error::class.simpleName,
                error
            )
        }
    }

    private fun validateWorkerConfig() {
        require(properties.bootstrapServers.isNotBlank()) {
            "banking-lab.notification-service.event-consumer.bootstrap-servers must be configured"
        }
        require(properties.topic.isNotBlank()) {
            "banking-lab.notification-service.event-consumer.topic must be configured"
        }
        require(properties.worker.clientId.isNotBlank()) {
            "banking-lab.notification-service.event-consumer.worker.client-id must be configured"
        }
        require(properties.worker.groupId.isNotBlank()) {
            "banking-lab.notification-service.event-consumer.worker.group-id must be configured"
        }
        require(properties.worker.batchSize > 0) {
            "banking-lab.notification-service.event-consumer.worker.batch-size must be positive"
        }
        require(properties.worker.pollIntervalMillis > 0) {
            "banking-lab.notification-service.event-consumer.worker.poll-interval-millis must be positive"
        }
        require(properties.worker.pollTimeoutMillis > 0) {
            "banking-lab.notification-service.event-consumer.worker.poll-timeout-millis must be positive"
        }
        require(properties.routing.defaultChannel.isNotBlank()) {
            "banking-lab.notification-service.event-consumer.routing.default-channel must be configured"
        }
        require(properties.routing.requestedBy.isNotBlank()) {
            "banking-lab.notification-service.event-consumer.routing.requested-by must be configured"
        }
        require(properties.routing.supportedEventTypes.isNotEmpty()) {
            "banking-lab.notification-service.event-consumer.routing.supported-event-types must not be empty"
        }
    }
}
