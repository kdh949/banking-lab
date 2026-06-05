package lab.banking.reporting.eventing

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
    prefix = "banking-lab.reporting-service.domain-event-publisher.worker",
    name = ["enabled"],
    havingValue = "true"
)
class ReportingDomainEventPublisherWorker(
    private val publisher: ReportingOutboxPublisherPort,
    private val properties: ReportingDomainEventPublisherProperties
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
                Thread(runnable, "reporting-service-domain-event-publisher").apply {
                    isDaemon = false
                }
            }
            executor = createdExecutor
            logger.info(
                "observability.reporting.publisher event=started topic={} clientId={} batchSize={} pollIntervalMillis={} syntheticOnly=true",
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
            throw error
        }
    }

    override fun stop() {
        val wasRunning = running.getAndSet(false)
        executor?.shutdownNow()
        executor = null
        if (wasRunning) {
            logger.info(
                "observability.reporting.publisher event=stopped topic={} clientId={} syntheticOnly=true",
                properties.topic,
                properties.worker.clientId
            )
        }
    }

    override fun isRunning(): Boolean =
        running.get()

    fun runOneBatch(): ReportingKafkaPublishBatchResult {
        val result = publisher.publishAvailable(properties.publisherConfig(), properties.worker.batchSize)
        if (result.attempted > 0) {
            logger.info(
                "observability.reporting.publisher event=batch topic={} clientId={} attempted={} published={} failed={} deadLettered={} syntheticOnly=true",
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
            logger.error(
                "observability.reporting.publisher event=batch-failed topic={} clientId={} error={} syntheticOnly=true",
                properties.topic,
                properties.worker.clientId,
                error.message ?: error::class.simpleName,
                error
            )
        }
    }

    private fun validateWorkerConfig() {
        require(properties.bootstrapServers.isNotBlank()) {
            "banking-lab.reporting-service.domain-event-publisher.bootstrap-servers must be configured"
        }
        require(properties.topic.isNotBlank()) {
            "banking-lab.reporting-service.domain-event-publisher.topic must be configured"
        }
        require(properties.worker.clientId.isNotBlank()) {
            "banking-lab.reporting-service.domain-event-publisher.worker.client-id must be configured"
        }
        require(properties.worker.batchSize > 0) {
            "banking-lab.reporting-service.domain-event-publisher.worker.batch-size must be positive"
        }
        require(properties.worker.pollIntervalMillis > 0) {
            "banking-lab.reporting-service.domain-event-publisher.worker.poll-interval-millis must be positive"
        }
        require(properties.publish.timeoutMillis > 0) {
            "banking-lab.reporting-service.domain-event-publisher.publish.timeout-millis must be positive"
        }
        require(properties.publish.deadLetterThreshold > 0) {
            "banking-lab.reporting-service.domain-event-publisher.publish.dead-letter-threshold must be positive"
        }
        require(properties.publish.retryDelaySeconds >= 0) {
            "banking-lab.reporting-service.domain-event-publisher.publish.retry-delay-seconds must be non-negative"
        }
        require(properties.publish.eventTypes.isNotEmpty()) {
            "banking-lab.reporting-service.domain-event-publisher.publish.event-types must not be empty"
        }
    }
}
