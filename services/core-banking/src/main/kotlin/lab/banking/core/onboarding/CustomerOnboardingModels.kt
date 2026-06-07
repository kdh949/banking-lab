package lab.banking.core.onboarding

import java.time.OffsetDateTime
import lab.banking.core.approval.OperatorApproval

data class CustomerOnboardingRequestCommand(
    val requestedBy: String,
    val requestedByRole: String = "BRANCH_STAFF",
    val reason: String,
    val idempotencyKey: String,
    val customerName: String,
    val customerPhone: String,
    val customerAddress: String,
    val customerGrade: String = "STANDARD",
    val riskGrade: String = "LOW",
    val sourceOfFundsCode: String = "SALARY",
    val transactionPurposeCode: String = "DAILY_BANKING",
    val username: String,
    val temporaryPassword: String
)

data class CustomerOnboardingApproveCommand(
    val approvedBy: String,
    val approvedByRole: String = "BRANCH_MANAGER",
    val screenId: String? = "CST-202"
)

data class CustomerOnboardingRejectCommand(
    val rejectedBy: String,
    val rejectedByRole: String = "BRANCH_MANAGER",
    val rejectReason: String,
    val screenId: String? = "CST-202"
)

data class CustomerOnboardingExecuteCommand(
    val executedBy: String,
    val executedByRole: String = "BRANCH_STAFF",
    val reason: String,
    val idempotencyKey: String
)

data class CustomerOnboardingRequestDto(
    val requestId: String,
    val idempotencyKey: String,
    val status: String,
    val requestedBy: String,
    val requestedByRole: String,
    val reason: String,
    val approvalId: String,
    val requestedCustomerName: String,
    val requestedCustomerPhone: String,
    val requestedCustomerAddress: String,
    val requestedCustomerGrade: String,
    val requestedRiskGrade: String,
    val requestedSourceOfFundsCode: String,
    val requestedTransactionPurposeCode: String,
    val requestedUsername: String,
    val generatedCustomerId: String?,
    val generatedAuthSubject: String?,
    val approvedBy: String?,
    val approvedAt: OffsetDateTime?,
    val rejectedBy: String?,
    val rejectedAt: OffsetDateTime?,
    val rejectReason: String?,
    val executedBy: String?,
    val executedByRole: String?,
    val executedAt: OffsetDateTime?,
    val syntheticOnly: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

data class CreatedSyntheticCustomerDto(
    val customerId: String,
    val authSubject: String,
    val username: String,
    val kycStatus: String,
    val syntheticOnly: Boolean = true
)

data class CustomerOnboardingRequestResponse(
    val item: CustomerOnboardingRequestDto,
    val approval: OperatorApproval,
    val replayed: Boolean,
    val syntheticOnly: Boolean = true
)

data class CustomerOnboardingReviewResponse(
    val item: CustomerOnboardingRequestDto,
    val approval: OperatorApproval,
    val replayed: Boolean,
    val syntheticOnly: Boolean = true
)

data class CustomerOnboardingExecuteResponse(
    val item: CustomerOnboardingRequestDto,
    val approval: OperatorApproval,
    val customer: CreatedSyntheticCustomerDto?,
    val replayed: Boolean,
    val syntheticOnly: Boolean = true
)
