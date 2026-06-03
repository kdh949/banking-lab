package lab.banking.core.eventing

import io.micrometer.tracing.Span
import io.micrometer.tracing.Tracer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component

@Component
class OutboxWorkerTraceLogger(
    private val tracerProvider: ObjectProvider<Tracer>
) {
    private val log = LoggerFactory.getLogger(OutboxWorkerTraceLogger::class.java)

    constructor() : this(object : ObjectProvider<Tracer> {
        override fun getIfAvailable(): Tracer? = null
    })

    fun recordBatch(topic: String, clientId: String, result: KafkaOutboxPublishBatchResult) {
        if (result.attempted == 0) {
            return
        }
        recordOutboxEvent(
            event = "batch",
            topic = topic,
            clientId = clientId,
            attempted = result.attempted,
            published = result.published,
            failed = result.failed,
            deadLettered = result.deadLettered,
            outboxEventIds = result.results.map { it.outboxEventId },
            errorType = "none",
            errorMessage = "none"
        )
    }

    fun recordBatchFailure(topic: String, clientId: String, error: RuntimeException) {
        recordOutboxEvent(
            event = "batch-failed",
            topic = topic,
            clientId = clientId,
            attempted = 0,
            published = 0,
            failed = 0,
            deadLettered = 0,
            outboxEventIds = emptyList(),
            errorType = error::class.java.simpleName,
            errorMessage = (error.message ?: "outbox batch failed").take(500)
        )
    }

    private fun recordOutboxEvent(
        event: String,
        topic: String,
        clientId: String,
        attempted: Int,
        published: Int,
        failed: Int,
        deadLettered: Int,
        outboxEventIds: List<String>,
        errorType: String,
        errorMessage: String
    ) {
        val tracer = tracerProvider.ifAvailable
        if (tracer == null) {
            logOutboxEvent(
                event = event,
                topic = topic,
                clientId = clientId,
                attempted = attempted,
                published = published,
                failed = failed,
                deadLettered = deadLettered,
                outboxEventIds = outboxEventIds,
                errorType = errorType,
                errorMessage = errorMessage,
                span = null
            )
            return
        }

        val span = tracer.nextSpan()
            .name("banking-lab.outbox.worker.$event")
            .tag("synthetic.only", "true")
            .tag("outbox.worker.event", event)
            .tag("outbox.topic", topic)
            .tag("outbox.client_id", clientId)
            .tag("outbox.attempted", attempted.toString())
            .tag("outbox.published", published.toString())
            .tag("outbox.failed", failed.toString())
            .tag("outbox.dead_lettered", deadLettered.toString())
            .tag("outbox.error_type", errorType)
            .start()
        try {
            tracer.withSpan(span).use {
                logOutboxEvent(
                    event = event,
                    topic = topic,
                    clientId = clientId,
                    attempted = attempted,
                    published = published,
                    failed = failed,
                    deadLettered = deadLettered,
                    outboxEventIds = outboxEventIds,
                    errorType = errorType,
                    errorMessage = errorMessage,
                    span = span
                )
            }
        } catch (ex: Throwable) {
            span.error(ex)
            throw ex
        } finally {
            span.end()
        }
    }

    private fun logOutboxEvent(
        event: String,
        topic: String,
        clientId: String,
        attempted: Int,
        published: Int,
        failed: Int,
        deadLettered: Int,
        outboxEventIds: List<String>,
        errorType: String,
        errorMessage: String,
        span: Span?
    ) {
        val context = span?.context()
        log.info(
            "observability.outbox.worker event={} topic={} clientId={} attempted={} published={} failed={} deadLettered={} outboxEventIds={} errorType={} errorMessage={} traceId={} spanId={} syntheticOnly=true",
            event,
            topic,
            clientId,
            attempted,
            published,
            failed,
            deadLettered,
            outboxEventIds.joinToString(",").ifBlank { "none" },
            errorType,
            errorMessage,
            context?.traceId() ?: "unavailable",
            context?.spanId() ?: "unavailable"
        )
    }
}
