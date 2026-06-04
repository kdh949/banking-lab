package lab.banking.core.temporal

import io.temporal.workflow.Workflow
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod

class EndOfDayClosingWorkflowInput() {
    var businessDate: String = ""
    var idempotencyKey: String = ""
    var requestedBy: String = ""
    var requestedByRole: String = ""
    var reason: String = ""
    var feePolicyId: String? = null
    var externalMode: String = "MATCHED"

    constructor(
        businessDate: String,
        idempotencyKey: String,
        requestedBy: String,
        requestedByRole: String,
        reason: String,
        feePolicyId: String?,
        externalMode: String
    ) : this() {
        this.businessDate = businessDate
        this.idempotencyKey = idempotencyKey
        this.requestedBy = requestedBy
        this.requestedByRole = requestedByRole
        this.reason = reason
        this.feePolicyId = feePolicyId
        this.externalMode = externalMode
    }
}

class EndOfDayClosingWorkflowResult() {
    var businessDate: String = ""
    var finalStatus: String = ""
    var checkpoints: MutableList<String> = mutableListOf()
    var syntheticOnly: Boolean = true
}

@WorkflowInterface
interface EndOfDayClosingWorkflow {
    @WorkflowMethod
    fun run(input: EndOfDayClosingWorkflowInput): EndOfDayClosingWorkflowResult
}

class EndOfDayClosingWorkflowImpl : EndOfDayClosingWorkflow {
    override fun run(input: EndOfDayClosingWorkflowInput): EndOfDayClosingWorkflowResult {
        require(input.businessDate.isNotBlank()) { "businessDate is required" }
        require(input.idempotencyKey.isNotBlank()) { "idempotencyKey is required" }
        require(input.requestedBy.isNotBlank()) { "requestedBy is required" }
        require(input.reason.isNotBlank()) { "reason is required" }
        val checkpoints = mutableListOf<String>()
        listOf("INTEREST_ACCRUAL", "INTEREST_POSTING", "FEE_POSTING", "RECONCILIATION", "DAILY_CLOSING").forEach {
            checkpoints += it
            Workflow.sideEffect(String::class.java) { it }
        }
        return EndOfDayClosingWorkflowResult().also {
            it.businessDate = input.businessDate
            it.finalStatus = "COMPLETED"
            it.checkpoints = checkpoints
            it.syntheticOnly = true
        }
    }
}
