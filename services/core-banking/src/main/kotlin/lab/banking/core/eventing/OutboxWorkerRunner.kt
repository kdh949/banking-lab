package lab.banking.core.eventing

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "banking-lab.outbox.worker", name = ["enabled"], havingValue = "true")
class OutboxWorkerRunner(
    private val publisher: OutboxPublisherPort,
    private val properties: OutboxWorkerProperties,
    private val metrics: OutboxWorkerMetrics
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
                Thread(runnable, "core-banking-outbox-worker").apply {
                    isDaemon = false
                }
            }
            executor = createdExecutor
            metrics.recordStarted()
            logger.info(
                "observability.outbox.worker event=started topic={} clientId={} batchSize={} pollIntervalMillis={} syntheticOnly=true",
                properties.topic,
                properties.worker.clientId,
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
                "observability.outbox.worker event=stopped topic={} clientId={} syntheticOnly=true",
                properties.topic,
                properties.worker.clientId
            )
        }
    }

    override fun isRunning(): Boolean =
        running.get()

    fun runOneBatch(): KafkaOutboxPublishBatchResult {
        val result = publisher.publishAvailable(properties.publisherConfig(), properties.worker.batchSize)
        metrics.recordBatch(result)
        if (result.attempted > 0) {
            logger.info(
                "observability.outbox.worker event=batch topic={} clientId={} attempted={} published={} failed={} deadLettered={} syntheticOnly=true",
                properties.topic,
                properties.worker.clientId,
                result.attempted,
                result.published,
                result.failed,
                result.deadLettered
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
                "observability.outbox.worker event=batch-failed topic={} clientId={} error={} syntheticOnly=true",
                properties.topic,
                properties.worker.clientId,
                error.message ?: error::class.simpleName,
                error
            )
        }
    }

    private fun validateWorkerConfig() {
        require(properties.worker.batchSize > 0) { "banking-lab.outbox.worker.batch-size must be positive" }
        require(properties.worker.pollIntervalMillis > 0) {
            "banking-lab.outbox.worker.poll-interval-millis must be positive"
        }
        require(properties.worker.publishTimeoutMillis > 0) {
            "banking-lab.outbox.worker.publish-timeout-millis must be positive"
        }
        require(properties.worker.deadLetterThreshold > 0) {
            "banking-lab.outbox.worker.dead-letter-threshold must be positive"
        }
        require(properties.worker.retryDelaySeconds >= 0) {
            "banking-lab.outbox.worker.retry-delay-seconds must be non-negative"
        }
    }
}
