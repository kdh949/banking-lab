package lab.banking.core.temporal

import io.temporal.failure.ApplicationFailure
import io.temporal.workflow.QueryMethod
import io.temporal.workflow.SignalMethod
import io.temporal.workflow.Workflow
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod

enum class TemporalBankingCaseType {
    COMPLAINT_ANSWER,
    FDS_RELEASE,
    FDS_BLOCK,
    AML_CLOSURE,
    RECONCILIATION_ADJUSTMENT,
    ACCOUNT_HOLD,
    ACCOUNT_RELEASE
}

class TemporalBankingCaseInput() {
    var caseType: String = ""
    var businessReferenceId: String = ""
    var requestedBy: String = ""
    var requestedByRole: String = ""
    var reason: String = ""
    var payload: MutableMap<String, Any?> = linkedMapOf()

    constructor(
        caseType: TemporalBankingCaseType,
        businessReferenceId: String,
        requestedBy: String,
        requestedByRole: String,
        reason: String,
        payload: Map<String, Any?> = emptyMap()
    ) : this() {
        this.caseType = caseType.name
        this.businessReferenceId = businessReferenceId
        this.requestedBy = requestedBy
        this.requestedByRole = requestedByRole
        this.reason = reason
        this.payload = payload.toMutableMap()
    }
}

class TemporalApprovalSignal() {
    var approvedBy: String = ""
    var approvedByRole: String = ""
    var reason: String = ""

    constructor(approvedBy: String, approvedByRole: String, reason: String) : this() {
        this.approvedBy = approvedBy
        this.approvedByRole = approvedByRole
        this.reason = reason
    }
}

class TemporalRejectionSignal() {
    var rejectedBy: String = ""
    var rejectedByRole: String = ""
    var reason: String = ""

    constructor(rejectedBy: String, rejectedByRole: String, reason: String) : this() {
        this.rejectedBy = rejectedBy
        this.rejectedByRole = rejectedByRole
        this.reason = reason
    }
}

class TemporalBankingCaseResult() {
    var caseType: String = ""
    var businessReferenceId: String = ""
    var finalStatus: String = ""
    var requestedBy: String = ""
    var checkerId: String? = null
    var controlEffect: String = ""
    var checkpoints: MutableList<String> = mutableListOf()
    var syntheticOnly: Boolean = true
    var payload: MutableMap<String, Any?> = linkedMapOf()
}

@WorkflowInterface
interface BankingCaseTemporalWorkflow {
    @WorkflowMethod
    fun run(input: TemporalBankingCaseInput): TemporalBankingCaseResult

    @SignalMethod
    fun approve(signal: TemporalApprovalSignal)

    @SignalMethod
    fun reject(signal: TemporalRejectionSignal)

    @QueryMethod
    fun status(): String

    @QueryMethod
    fun checkpoints(): List<String>
}

class BankingCaseTemporalWorkflowImpl : BankingCaseTemporalWorkflow {
    private var currentStatus = "CREATED"
    private val checkpointLog = mutableListOf<String>()
    private var approval: TemporalApprovalSignal? = null
    private var rejection: TemporalRejectionSignal? = null

    override fun run(input: TemporalBankingCaseInput): TemporalBankingCaseResult {
        val caseType = parseCaseType(input.caseType)
        requireNonBlank(input.businessReferenceId, "businessReferenceId")
        requireNonBlank(input.requestedBy, "requestedBy")
        requireNonBlank(input.requestedByRole, "requestedByRole")
        requireNonBlank(input.reason, "reason")

        val attempt = Workflow.getInfo().attempt
        maybeInjectSyntheticTransientFailure(input, caseType, attempt)
        if (attempt > 1) {
            transition("RETRY_RECOVERED_ATTEMPT_$attempt", caseType)
        }
        transition("STARTED", caseType)
        transition("WAITING_APPROVAL", caseType)
        Workflow.await { approval != null || rejection != null }

        val rejected = rejection
        if (rejected != null) {
            requireNonBlank(rejected.rejectedBy, "rejectedBy")
            requireNonBlank(rejected.rejectedByRole, "rejectedByRole")
            transition("REJECTED", caseType)
            return result(input, caseType, "REJECTED", rejected.rejectedBy, "NO_EFFECT")
        }

        val approved = approval ?: throw nonRetryable("approval signal is missing", "WORKFLOW_STATE_VIOLATION")
        requireNonBlank(approved.approvedBy, "approvedBy")
        requireNonBlank(approved.approvedByRole, "approvedByRole")
        if (approved.approvedBy == input.requestedBy) {
            throw nonRetryable("maker cannot approve own Temporal workflow action", "MAKER_CHECKER_SELF_APPROVAL_REJECTED")
        }
        if (!setOf("BRANCH_MANAGER", "COMPLIANCE_MANAGER").contains(approved.approvedByRole)) {
            throw nonRetryable("checker role cannot approve Temporal workflow action", "AUTHORIZATION_POLICY_VIOLATION")
        }

        transition("COMPLETED", caseType)
        return result(input, caseType, "COMPLETED", approved.approvedBy, controlEffect(caseType))
    }

    override fun approve(signal: TemporalApprovalSignal) {
        approval = signal
    }

    override fun reject(signal: TemporalRejectionSignal) {
        rejection = signal
    }

    override fun status(): String =
        currentStatus

    override fun checkpoints(): List<String> =
        checkpointLog.toList()

    private fun transition(nextStatus: String, caseType: TemporalBankingCaseType) {
        currentStatus = nextStatus
        checkpointLog += "${caseType.name}:$nextStatus"
    }

    private fun result(
        input: TemporalBankingCaseInput,
        caseType: TemporalBankingCaseType,
        finalStatus: String,
        checkerId: String?,
        controlEffect: String
    ): TemporalBankingCaseResult =
        TemporalBankingCaseResult().also {
            it.caseType = caseType.name
            it.businessReferenceId = input.businessReferenceId
            it.finalStatus = finalStatus
            it.requestedBy = input.requestedBy
            it.checkerId = checkerId
            it.controlEffect = controlEffect
            it.checkpoints = checkpointLog.toMutableList()
            it.syntheticOnly = true
            it.payload = (input.payload + mapOf("syntheticOnly" to true)).toMutableMap()
        }

    private fun parseCaseType(value: String): TemporalBankingCaseType =
        runCatching { TemporalBankingCaseType.valueOf(value) }
            .getOrElse { throw nonRetryable("unsupported Temporal banking case type: $value", "WORKFLOW_VALIDATION_FAILED") }

    private fun controlEffect(caseType: TemporalBankingCaseType): String =
        when (caseType) {
            TemporalBankingCaseType.COMPLAINT_ANSWER -> "CUSTOMER_ANSWER_VISIBLE"
            TemporalBankingCaseType.FDS_RELEASE -> "LEDGER_TRANSFER_HANDOFF"
            TemporalBankingCaseType.FDS_BLOCK -> "NO_LEDGER_POSTING"
            TemporalBankingCaseType.AML_CLOSURE -> "STR_SIMULATION_CLOSURE"
            TemporalBankingCaseType.RECONCILIATION_ADJUSTMENT -> "BALANCED_ADJUSTMENT_HANDOFF"
            TemporalBankingCaseType.ACCOUNT_HOLD -> "AVAILABLE_BALANCE_HOLD"
            TemporalBankingCaseType.ACCOUNT_RELEASE -> "HOLD_RELEASE_HANDOFF"
        }

    private fun requireNonBlank(value: String?, field: String) {
        if (value.isNullOrBlank()) {
            throw nonRetryable("$field is required", "WORKFLOW_VALIDATION_FAILED")
        }
    }

    private fun maybeInjectSyntheticTransientFailure(
        input: TemporalBankingCaseInput,
        caseType: TemporalBankingCaseType,
        attempt: Int
    ) {
        val failuresBeforeApproval = when (val value = input.payload["syntheticTransientFailuresBeforeApproval"]) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }
        if (failuresBeforeApproval > 0 && attempt <= failuresBeforeApproval) {
            transition("TRANSIENT_FAILURE_INJECTED_ATTEMPT_$attempt", caseType)
            throw ApplicationFailure.newFailure(
                "synthetic transient Temporal failure drill before approval",
                "SYNTHETIC_TRANSIENT_WORKFLOW_FAILURE"
            )
        }
    }

    private fun nonRetryable(message: String, type: String): ApplicationFailure =
        ApplicationFailure.newNonRetryableFailure(message, type)
}
