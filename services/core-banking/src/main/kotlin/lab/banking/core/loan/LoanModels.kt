package lab.banking.core.loan

import java.time.LocalDate
import java.time.OffsetDateTime
import lab.banking.core.approval.OperatorApproval
import lab.banking.core.ledger.domain.LedgerCommandResult

data class LoanProductDto(
    val productId: String,
    val productCode: String,
    val productName: String,
    val currency: String,
    val annualRateBps: Int,
    val termMonths: Int,
    val minimumAmountMinor: Long,
    val maximumAmountMinor: Long,
    val approvalThresholdMinor: Long,
    val status: String,
    val syntheticOnly: Boolean
)

data class LoanProductListResponse(
    val items: List<LoanProductDto>
)

data class LoanApplicationCommand(
    val customerId: String,
    val depositAccountId: String,
    val productId: String,
    val requestedAmountMinor: Long,
    val requestedTermMonths: Int? = null,
    val syntheticMonthlyIncomeMinor: Long,
    val syntheticMonthlyDebtMinor: Long,
    val syntheticCreditGrade: String,
    val syntheticRiskGrade: String,
    val requestedBy: String? = null,
    val requestedByRole: String? = null,
    val reason: String? = null,
    val idempotencyKey: String? = null
)

data class LoanApplicationDto(
    val applicationId: String,
    val customerId: String,
    val depositAccountId: String,
    val productId: String,
    val requestedAmountMinor: Long,
    val requestedTermMonths: Int,
    val syntheticCreditGrade: String,
    val syntheticRiskGrade: String,
    val underwritingScore: Int,
    val underwritingDecision: String,
    val status: String,
    val approvalId: String?,
    val requestedBy: String,
    val requestedRole: String,
    val reason: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val executedAt: OffsetDateTime?,
    val syntheticOnly: Boolean = true
)

data class LoanApplicationResponse(
    val item: LoanApplicationDto,
    val approval: OperatorApproval?,
    val replayed: Boolean
)

data class LoanScheduleItemDto(
    val scheduleId: String,
    val installmentNo: Int,
    val dueDate: LocalDate,
    val principalMinor: Long,
    val interestMinor: Long,
    val totalMinor: Long,
    val status: String,
    val ledgerTransactionId: String?,
    val paidAt: OffsetDateTime?
)

data class LoanDto(
    val loanId: String,
    val applicationId: String,
    val customerId: String,
    val depositAccountId: String,
    val productId: String,
    val principalMinor: Long,
    val outstandingPrincipalMinor: Long,
    val annualRateBps: Int,
    val termMonths: Int,
    val status: String,
    val disbursementTransactionId: String?,
    val nextDueDate: LocalDate?,
    val overdueDays: Int,
    val disbursedAt: OffsetDateTime?,
    val schedule: List<LoanScheduleItemDto>,
    val syntheticOnly: Boolean = true
)

data class LoanExecutionResponse(
    val application: LoanApplicationDto,
    val loan: LoanDto,
    val ledgerTransaction: LedgerCommandResult
)

data class LoanPaymentCommand(
    val principalMinor: Long? = null,
    val interestMinor: Long? = null,
    val businessDate: LocalDate? = null,
    val idempotencyKey: String? = null,
    val requestedBy: String? = null,
    val requestedChannel: String? = null,
    val reason: String? = null
)

data class LoanPaymentDto(
    val paymentId: String,
    val loanId: String,
    val paymentType: String,
    val principalMinor: Long,
    val interestMinor: Long,
    val totalMinor: Long,
    val businessDate: LocalDate,
    val idempotencyKey: String,
    val ledgerTransactionId: String,
    val requestedBy: String,
    val requestedChannel: String,
    val reason: String,
    val createdAt: OffsetDateTime
)

data class LoanPaymentResponse(
    val item: LoanPaymentDto,
    val loan: LoanDto,
    val ledgerTransaction: LedgerCommandResult,
    val replayed: Boolean
)

data class LoanAccrualCommand(
    val accrualDate: LocalDate,
    val requestedBy: String,
    val actorRole: String,
    val reason: String
)

data class LoanAccrualDto(
    val accrualId: String,
    val loanId: String,
    val accrualDate: LocalDate,
    val outstandingPrincipalMinor: Long,
    val annualRateBps: Int,
    val interestMinor: Long,
    val overdueDays: Int,
    val status: String,
    val createdAt: OffsetDateTime
)

data class LoanAccrualResponse(
    val item: LoanAccrualDto,
    val loan: LoanDto,
    val replayed: Boolean
)
