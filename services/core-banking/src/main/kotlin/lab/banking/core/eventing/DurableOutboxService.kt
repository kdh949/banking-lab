package lab.banking.core.eventing

import lab.banking.core.workflow.WorkflowErrors
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DurableOutboxService(private val repository: OutboxEventRepository) {
    fun enqueue(command: CreateOutboxEventCommand): OutboxEventRecord {
        requireNonBlank(command.aggregateType, "aggregateType")
        requireNonBlank(command.aggregateId, "aggregateId")
        requireNonBlank(command.eventType, "eventType")
        requireNonBlank(command.idempotencyKey, "idempotencyKey")
        return repository.insertPending(command)
    }

    fun event(outboxEventId: String): OutboxEventRecord =
        repository.find(outboxEventId)

    @Transactional
    fun markPublished(outboxEventId: String): OutboxEventRecord {
        val event = repository.findForUpdate(outboxEventId)
        if (event.status == OutboxEventStatus.DEAD_LETTER) {
            throw WorkflowErrors.stateViolation("dead-letter outbox event cannot be published: $outboxEventId")
        }
        repository.markPublished(outboxEventId)
        return repository.find(outboxEventId)
    }

    @Transactional
    fun recordPublishFailure(
        outboxEventId: String,
        errorMessage: String,
        deadLetterThreshold: Int = 3,
        retryDelaySeconds: Long = 60
    ): OutboxEventRecord {
        if (deadLetterThreshold <= 0) {
            throw WorkflowErrors.validation("deadLetterThreshold must be positive")
        }
        val event = repository.findForUpdate(outboxEventId)
        if (event.status == OutboxEventStatus.PUBLISHED || event.status == OutboxEventStatus.DEAD_LETTER) {
            throw WorkflowErrors.stateViolation("terminal outbox event cannot record publish failure: ${event.status}")
        }
        val nextRetryCount = event.retryCount + 1
        if (nextRetryCount >= deadLetterThreshold) {
            repository.markDeadLetter(outboxEventId, nextRetryCount, errorMessage)
        } else {
            repository.markFailed(outboxEventId, nextRetryCount, errorMessage, retryDelaySeconds)
        }
        return repository.find(outboxEventId)
    }

    fun recordInboxProcessed(
        consumerName: String,
        sourceEventId: String,
        eventType: String,
        payload: Map<String, Any?>
    ): InboxRecordResult {
        requireNonBlank(consumerName, "consumerName")
        requireNonBlank(sourceEventId, "sourceEventId")
        requireNonBlank(eventType, "eventType")
        return repository.recordInboxProcessed(consumerName, sourceEventId, eventType, payload)
    }

    private fun requireNonBlank(value: String?, field: String) {
        if (value.isNullOrBlank()) {
            throw WorkflowErrors.validation("$field is required")
        }
    }
}
