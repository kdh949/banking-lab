package lab.banking.core.statement

import java.time.LocalDate
import java.time.OffsetDateTime
import lab.banking.core.ledger.domain.PostingDirection

data class StatementLineDto(
    val transactionId: String,
    val transactionType: String,
    val businessDate: LocalDate,
    val postedAt: OffsetDateTime?,
    val accountId: String,
    val direction: PostingDirection,
    val amountMinor: Long,
    val signedAmountMinor: Long,
    val currency: String,
    val postingType: String,
    val requestedChannel: String,
    val reason: String?
)

data class CustomerStatementDto(
    val statementId: String? = null,
    val statementScope: String = "CONSOLIDATED",
    val accountId: String? = null,
    val customerId: String,
    val from: LocalDate,
    val to: LocalDate,
    val currency: String,
    val openingBalanceMinor: Long,
    val closingBalanceMinor: Long,
    val debitTotalMinor: Long,
    val creditTotalMinor: Long,
    val netAmountMinor: Long,
    val lineCount: Int,
    val lines: List<StatementLineDto>,
    val sourceLedgerHash: String? = null,
    val payloadHash: String? = null,
    val generatedAt: OffsetDateTime? = null,
    val maskingPolicy: String = "CUSTOMER_SELF",
    val syntheticOnly: Boolean = true
)

data class TransactionConfirmationPostingDto(
    val accountId: String,
    val customerId: String,
    val direction: PostingDirection,
    val amountMinor: Long,
    val signedAmountMinor: Long,
    val currency: String,
    val postingType: String
)

data class TransactionConfirmationDto(
    val confirmationId: String,
    val transactionId: String,
    val transactionType: String,
    val businessReferenceId: String,
    val businessDate: LocalDate,
    val status: String,
    val requestedBy: String,
    val requestedChannel: String,
    val postedAt: OffsetDateTime?,
    val originalTransactionId: String?,
    val currency: String,
    val totalDebitMinor: Long,
    val totalCreditMinor: Long,
    val balanced: Boolean,
    val postings: List<TransactionConfirmationPostingDto>,
    val syntheticOnly: Boolean = true
)

data class BalanceCertificateDto(
    val certificateId: String,
    val accountId: String,
    val customerId: String,
    val currency: String,
    val date: LocalDate,
    val balanceAsOfMinor: Long,
    val currentLedgerBalanceMinor: Long,
    val currentAvailableBalanceMinor: Long,
    val deterministicInputHash: String,
    val sourcePostingCount: Int,
    val sourceLastBusinessDate: LocalDate?,
    val sourceLedgerHash: String,
    val snapshotCreatedAt: OffsetDateTime,
    val lastViewedAt: OffsetDateTime,
    val syntheticOnly: Boolean = true
)

data class CustomerAccessHistoryItemDto(
    val auditEventId: String,
    val eventType: String,
    val actorType: String,
    val actorId: String,
    val actorRole: String,
    val screenId: String?,
    val businessReferenceId: String?,
    val accountId: String?,
    val reasonPresent: Boolean,
    val createdAt: OffsetDateTime
)

data class CustomerAccessHistoryDto(
    val customerId: String,
    val items: List<CustomerAccessHistoryItemDto>,
    val syntheticOnly: Boolean = true
)
