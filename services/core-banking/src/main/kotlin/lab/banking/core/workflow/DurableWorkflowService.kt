package lab.banking.core.workflow

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DurableWorkflowService(private val repository: DurableWorkflowRepository) {
    @Transactional
    fun start(command: StartWorkflowCommand): WorkflowInstanceRecord {
        requireNonBlank(command.workflowType, "workflowType")
        requireNonBlank(command.businessReferenceId, "businessReferenceId")
        requireNonBlank(command.startedBy, "startedBy")
        requireNonBlank(command.eventType, "eventType")
        return repository.insert(command)
    }

    fun instance(workflowInstanceId: String): WorkflowInstanceRecord =
        repository.find(workflowInstanceId)

    fun events(workflowInstanceId: String): List<WorkflowEventRecord> =
        repository.events(workflowInstanceId)

    @Transactional
    fun transition(command: TransitionWorkflowCommand): WorkflowInstanceRecord {
        requireNonBlank(command.workflowInstanceId, "workflowInstanceId")
        requireNonBlank(command.eventType, "eventType")
        val current = repository.findForUpdate(command.workflowInstanceId)
        requireTransition(current.status, command.nextStatus)
        repository.updateStatus(command.workflowInstanceId, command.nextStatus)
        repository.appendEvent(
            workflowInstanceId = command.workflowInstanceId,
            eventType = command.eventType,
            actorId = command.actorId,
            payload = command.payload + mapOf(
                "fromStatus" to current.status.name,
                "toStatus" to command.nextStatus.name
            )
        )
        return repository.find(command.workflowInstanceId)
    }

    private fun requireTransition(current: WorkflowInstanceStatus, next: WorkflowInstanceStatus) {
        val allowed = mapOf(
            WorkflowInstanceStatus.STARTED to setOf(
                WorkflowInstanceStatus.RUNNING,
                WorkflowInstanceStatus.WAITING_APPROVAL,
                WorkflowInstanceStatus.FAILED,
                WorkflowInstanceStatus.CANCELLED
            ),
            WorkflowInstanceStatus.RUNNING to setOf(
                WorkflowInstanceStatus.WAITING_APPROVAL,
                WorkflowInstanceStatus.COMPLETED,
                WorkflowInstanceStatus.FAILED,
                WorkflowInstanceStatus.CANCELLED
            ),
            WorkflowInstanceStatus.WAITING_APPROVAL to setOf(
                WorkflowInstanceStatus.RUNNING,
                WorkflowInstanceStatus.COMPLETED,
                WorkflowInstanceStatus.FAILED,
                WorkflowInstanceStatus.CANCELLED
            )
        )
        if (!allowed.getOrDefault(current, emptySet()).contains(next)) {
            throw WorkflowErrors.stateViolation("invalid durable workflow transition $current -> $next")
        }
    }

    private fun requireNonBlank(value: String?, field: String) {
        if (value.isNullOrBlank()) {
            throw WorkflowErrors.validation("$field is required")
        }
    }
}
