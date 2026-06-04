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

data class DailyClosingCommand(
    val businessDate: LocalDate,
    val idempotencyKey: String,
    val requestedBy: String = "SYSTEM"
)
