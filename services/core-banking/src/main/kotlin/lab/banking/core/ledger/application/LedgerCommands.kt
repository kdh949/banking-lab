package lab.banking.core.ledger.application

import java.time.LocalDate
import lab.banking.core.ledger.domain.PostingDirection

data class DepositCommand(
    val accountId: String,
    val amountMinor: Long,
    val idempotencyKey: String,
    val requestedBy: String = "SYSTEM",
    val requestedChannel: String = "CORE_BANKING",
    val businessDate: LocalDate? = null,
    val reason: String? = null,
    val currency: String = "KRW",
    val businessReferenceId: String? = null
)

data class WithdrawalCommand(
    val accountId: String,
    val amountMinor: Long,
    val idempotencyKey: String,
    val requestedBy: String = "SYSTEM",
    val requestedChannel: String = "CORE_BANKING",
    val businessDate: LocalDate? = null,
    val reason: String? = null,
    val currency: String = "KRW",
    val businessReferenceId: String? = null
)

data class InternalTransferCommand(
    val fromAccountId: String,
    val toAccountId: String,
    val amountMinor: Long,
    val idempotencyKey: String,
    val requestedBy: String = "SYSTEM",
    val requestedChannel: String = "CORE_BANKING",
    val businessDate: LocalDate? = null,
    val reason: String? = null,
    val currency: String = "KRW",
    val businessReferenceId: String? = null
)

data class ReversalCommand(
    val originalTransactionId: String,
    val idempotencyKey: String,
    val requestedBy: String = "SYSTEM",
    val requestedChannel: String = "CORE_BANKING",
    val businessDate: LocalDate? = null,
    val reason: String? = null,
    val businessReferenceId: String? = null
)

data class AdjustmentCommand(
    val accountId: String,
    val direction: PostingDirection = PostingDirection.CREDIT,
    val amountMinor: Long,
    val idempotencyKey: String,
    val requestedBy: String = "SYSTEM",
    val requestedChannel: String = "CORE_BANKING",
    val businessDate: LocalDate? = null,
    val reason: String? = null,
    val currency: String = "KRW",
    val businessReferenceId: String? = null,
    val approvalId: String? = null
)

data class InterestPostingCredit(
    val accountId: String,
    val amountMinor: Long
)

data class InterestPostingCommand(
    val credits: List<InterestPostingCredit>,
    val idempotencyKey: String,
    val requestedBy: String = "SYSTEM",
    val requestedChannel: String = "CORE_BANKING",
    val businessDate: LocalDate? = null,
    val reason: String? = null,
    val currency: String = "KRW",
    val businessReferenceId: String? = null
)

data class FeePostingCharge(
    val accountId: String,
    val amountMinor: Long
)

data class FeePostingCommand(
    val charges: List<FeePostingCharge>,
    val idempotencyKey: String,
    val requestedBy: String = "SYSTEM",
    val requestedChannel: String = "CORE_BANKING",
    val businessDate: LocalDate? = null,
    val reason: String? = null,
    val currency: String = "KRW",
    val businessReferenceId: String? = null
)

data class DisburseLoanCommand(
    val loanId: String,
    val applicationId: String,
    val depositAccountId: String,
    val amountMinor: Long,
    val idempotencyKey: String,
    val approvalId: String,
    val requestedBy: String,
    val requestedChannel: String = "LOAN_SERVICE",
    val businessDate: LocalDate? = null,
    val reason: String,
    val currency: String = "KRW"
)

data class LoanRepaymentCommand(
    val loanId: String,
    val depositAccountId: String,
    val principalMinor: Long,
    val interestMinor: Long,
    val idempotencyKey: String,
    val requestedBy: String,
    val requestedChannel: String = "LOAN_SERVICE",
    val businessDate: LocalDate? = null,
    val reason: String,
    val currency: String = "KRW",
    val prepayment: Boolean = false
)

data class CardCaptureCommand(
    val authorizationId: String,
    val cardId: String,
    val accountId: String,
    val amountMinor: Long,
    val idempotencyKey: String,
    val requestedBy: String,
    val requestedChannel: String = "CARD_SERVICE",
    val businessDate: LocalDate? = null,
    val reason: String,
    val currency: String = "KRW"
)

data class BillPaymentCommand(
    val paymentInstructionId: String,
    val debitAccountId: String,
    val syntheticBillerId: String,
    val amountMinor: Long,
    val idempotencyKey: String,
    val requestedBy: String,
    val requestedChannel: String = "PAYMENT_SERVICE",
    val businessDate: LocalDate? = null,
    val reason: String,
    val currency: String = "KRW"
)

data class DailyClosingCommand(
    val businessDate: LocalDate,
    val idempotencyKey: String,
    val requestedBy: String = "SYSTEM"
)
