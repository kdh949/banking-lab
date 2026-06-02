package lab.banking.core.workflow

import java.time.OffsetDateTime

enum class WorkflowInstanceStatus {
    STARTED,
    RUNNING,
    WAITING_APPROVAL,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class StartWorkflowCommand(
    val workflowType: String,
    val businessReferenceId: String,
    val startedBy: String,
    val eventType: String = "WORKFLOW_STARTED",
    val payload: Map<String, Any?> = emptyMap()
)

data class TransitionWorkflowCommand(
    val workflowInstanceId: String,
    val nextStatus: WorkflowInstanceStatus,
    val eventType: String,
    val actorId: String?,
    val payload: Map<String, Any?> = emptyMap()
)

data class WorkflowInstanceRecord(
    val workflowInstanceId: String,
    val workflowType: String,
    val businessReferenceId: String,
    val temporalWorkflowId: String?,
    val temporalRunId: String?,
    val status: WorkflowInstanceStatus,
    val startedBy: String,
    val startedAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

data class WorkflowEventRecord(
    val workflowEventId: String,
    val workflowInstanceId: String,
    val eventType: String,
    val actorId: String?,
    val payload: Map<String, Any?>,
    val createdAt: OffsetDateTime
)
