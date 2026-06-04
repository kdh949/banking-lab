package lab.banking.core.ledger.domain

import java.time.LocalDate
import java.time.OffsetDateTime

const val BANK_SUSPENSE_ACCOUNT_ID = "BANK-SUSPENSE"
const val BANK_LOAN_ASSET_ACCOUNT_ID = "BANK-LOAN-ASSET"
const val BANK_CARD_CLEARING_ACCOUNT_ID = "BANK-CARD-CLEARING"
const val BANK_SETTLEMENT_ACCOUNT_ID = "BANK-SETTLEMENT"
const val BANK_INTEREST_EXPENSE_ACCOUNT_ID = "BANK-INTEREST-EXPENSE"
const val BANK_FEE_INCOME_ACCOUNT_ID = "BANK-FEE-INCOME"
const val BANK_LOAN_INTEREST_INCOME_ACCOUNT_ID = "BANK-LOAN-INTEREST-INCOME"

enum class PostingDirection {
    DEBIT,
    CREDIT
}

data class LedgerPostingInput(
    val accountId: String,
    val direction: PostingDirection,
    val amountMinor: Long,
    val currency: String = "KRW",
    val postingType: String = "PRINCIPAL"
)

data class LedgerPostingDto(
    val id: String,
    val ledgerTransactionId: String,
    val accountId: String,
    val currency: String,
    val direction: PostingDirection,
    val amountMinor: Long,
    val postingType: String,
    val createdAt: OffsetDateTime? = null
)

data class LedgerTransactionDto(
    val id: String,
    val transactionType: String,
    val businessReferenceId: String,
    val idempotencyKey: String,
    val businessDate: LocalDate,
    val status: String,
    val requestedBy: String,
    val requestedChannel: String,
    val postedAt: OffsetDateTime? = null,
    val originalTransactionId: String? = null,
    val postings: List<LedgerPostingDto>
)

data class LedgerCommandResult(
    val value: LedgerTransactionDto,
    val replayed: Boolean
)

data class DailyClosingDto(
    val businessDate: LocalDate,
    val status: String,
    val closedBy: String
)

data class DailyClosingResult(
    val item: DailyClosingDto,
    val replayed: Boolean
)

object LedgerInvariants {
    fun signedAmount(posting: LedgerPostingInput): Long =
        when (posting.direction) {
            PostingDirection.DEBIT -> -posting.amountMinor
            PostingDirection.CREDIT -> posting.amountMinor
        }

    fun requireBalanced(transactionId: String, postings: List<LedgerPostingInput>) {
        require(postings.size >= 2) { "ledger transaction must contain at least two postings" }
        val totals = postings.groupBy { it.currency }
            .mapValues { (_, currencyPostings) -> currencyPostings.sumOf { signedAmount(it) } }
        val unbalanced = totals.entries.firstOrNull { it.value != 0L }
        require(unbalanced == null) {
            "ledger transaction $transactionId is not balanced for ${unbalanced?.key}: ${unbalanced?.value}"
        }
        postings.forEach { posting ->
            require(posting.accountId.isNotBlank()) { "posting.accountId is required" }
            require(posting.amountMinor > 0) { "amountMinor must be a positive integer minor-unit value" }
        }
    }
}
