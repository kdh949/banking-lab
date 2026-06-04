package lab.banking.core.approval

import java.time.OffsetDateTime

enum class ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}

object ApprovalBusinessTypes {
    const val CUSTOMER_INFO_CHANGE = "CUSTOMER_INFO_CHANGE"
    const val TRANSFER_LIMIT_CHANGE = "TRANSFER_LIMIT_CHANGE"
    const val TRANSFER_LIMIT_INCREASE = "TRANSFER_LIMIT_INCREASE"
    const val ACCOUNT_HOLD = "ACCOUNT_HOLD"
    const val ACCOUNT_HOLD_RELEASE = "ACCOUNT_HOLD_RELEASE"
    const val CUSTOMER_KYC_REVIEW = "CUSTOMER_KYC_REVIEW"
    const val FEE_WAIVER = "FEE_WAIVER"
    const val TRANSACTION_CORRECTION = "TRANSACTION_CORRECTION"
    const val PRODUCT_PARAMETER_CHANGE = "PRODUCT_PARAMETER_CHANGE"
    const val FEE_POLICY_PARAMETER_CHANGE = "FEE_POLICY_PARAMETER_CHANGE"
    const val RECONCILIATION_PARAMETER_CHANGE = "RECONCILIATION_PARAMETER_CHANGE"
    const val AUDIT_PARAMETER_CHANGE = "AUDIT_PARAMETER_CHANGE"
    const val FDS_RULE_PARAMETER_CHANGE = "FDS_RULE_PARAMETER_CHANGE"
    const val SECURITY_POLICY_PARAMETER_CHANGE = "SECURITY_POLICY_PARAMETER_CHANGE"
    const val AUTHORIZATION_PARAMETER_CHANGE = "AUTHORIZATION_PARAMETER_CHANGE"
    const val FDS_RELEASE = "FDS_RELEASE"
    const val FDS_BLOCK = "FDS_BLOCK"
    const val AML_CASE_CLOSE = "AML_CASE_CLOSE"
    const val RECONCILIATION_ADJUSTMENT = "RECONCILIATION_ADJUSTMENT"
    const val EOD_CLOSING = "EOD_CLOSING"
    const val LOAN_EXECUTION = "LOAN_EXECUTION"
    const val COMPLAINT_ANSWER_SEND = "COMPLAINT_ANSWER_SEND"
    const val ROLE_GRANT = "ROLE_GRANT"
    const val BULK_UNMASK_APPROVAL = "BULK_UNMASK_APPROVAL"

    val highRisk: Set<String> = setOf(
        CUSTOMER_INFO_CHANGE,
        TRANSFER_LIMIT_CHANGE,
        TRANSFER_LIMIT_INCREASE,
        ACCOUNT_HOLD,
        ACCOUNT_HOLD_RELEASE,
        CUSTOMER_KYC_REVIEW,
        FEE_WAIVER,
        TRANSACTION_CORRECTION,
        PRODUCT_PARAMETER_CHANGE,
        FEE_POLICY_PARAMETER_CHANGE,
        RECONCILIATION_PARAMETER_CHANGE,
        AUDIT_PARAMETER_CHANGE,
        FDS_RULE_PARAMETER_CHANGE,
        SECURITY_POLICY_PARAMETER_CHANGE,
        AUTHORIZATION_PARAMETER_CHANGE,
        FDS_RELEASE,
        FDS_BLOCK,
        AML_CASE_CLOSE,
        RECONCILIATION_ADJUSTMENT,
        EOD_CLOSING,
        LOAN_EXECUTION,
        COMPLAINT_ANSWER_SEND,
        ROLE_GRANT,
        BULK_UNMASK_APPROVAL
    )
}

data class SubmitApprovalCommand(
    val businessType: String,
    val businessReferenceId: String,
    val requestedBy: String,
    val requestReason: String?,
    val requestedByRole: String = "BRANCH_STAFF",
    val beforeSnapshot: Map<String, Any?>? = null,
    val afterSnapshot: Map<String, Any?>? = null,
    val screenId: String? = null
)

data class ApproveApprovalCommand(
    val approvedBy: String,
    val approvedByRole: String = "BRANCH_MANAGER",
    val screenId: String? = null
)

data class RejectApprovalCommand(
    val rejectedBy: String,
    val rejectedByRole: String = "BRANCH_MANAGER",
    val rejectReason: String,
    val screenId: String? = null
)

data class OperatorApproval(
    val approvalId: String,
    val businessType: String,
    val businessReferenceId: String,
    val requestedBy: String,
    val requestedAt: OffsetDateTime,
    val requestReason: String,
    val beforeSnapshot: Map<String, Any?>?,
    val afterSnapshot: Map<String, Any?>?,
    val status: ApprovalStatus,
    val approvedBy: String? = null,
    val approvedAt: OffsetDateTime? = null,
    val rejectedBy: String? = null,
    val rejectedAt: OffsetDateTime? = null,
    val rejectReason: String? = null,
    val auditEventId: String? = null
)

data class ApprovalAuditEvent(
    val auditEventId: String,
    val eventType: String,
    val actorId: String,
    val actorRole: String,
    val screenId: String?,
    val businessReferenceId: String,
    val reason: String?,
    val payload: Map<String, Any?>,
    val createdAt: OffsetDateTime
)

interface ApprovalServicePort {
    fun submit(command: SubmitApprovalCommand): OperatorApproval
    fun approve(approvalId: String, command: ApproveApprovalCommand): OperatorApproval
    fun reject(approvalId: String, command: RejectApprovalCommand): OperatorApproval
    fun approval(approvalId: String): OperatorApproval
    fun list(): List<OperatorApproval>
    fun auditEvents(): List<ApprovalAuditEvent>
}
