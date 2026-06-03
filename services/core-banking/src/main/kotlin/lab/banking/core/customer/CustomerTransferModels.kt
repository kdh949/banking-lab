package lab.banking.core.customer

import java.time.LocalDate
import java.time.OffsetDateTime

data class CustomerTransferCommand(
    val customerId: String? = null,
    val fromAccountId: String,
    val toAccountId: String,
    val amountMinor: Long,
    val idempotencyKey: String,
    val requestedBy: String? = null,
    val businessDate: LocalDate? = null,
    val reason: String? = null,
    val currency: String = "KRW",
    val businessReferenceId: String? = null
)

data class CustomerTransferDto(
    val resultId: String?,
    val transactionId: String?,
    val status: String,
    val fromAccountId: String,
    val toAccountId: String,
    val amountMinor: Long,
    val currency: String,
    val idempotencyKey: String,
    val caseId: String? = null,
    val failureCode: String? = null,
    val message: String? = null
)

data class CustomerTransferResponse(
    val item: CustomerTransferDto,
    val replayed: Boolean
)

data class CustomerTransactionDto(
    val transactionId: String,
    val transactionType: String,
    val status: String,
    val businessDate: LocalDate,
    val postedAt: OffsetDateTime?,
    val accountId: String,
    val direction: String,
    val amountMinor: Long,
    val currency: String,
    val requestedChannel: String,
    val reason: String?
)

data class CustomerTransactionHistoryResponse(
    val items: List<CustomerTransactionDto>
)

data class CustomerTransferStatusDto(
    val resultId: String?,
    val transactionId: String?,
    val caseId: String?,
    val transferReferenceId: String?,
    val customerId: String,
    val status: String,
    val caseStatus: String?,
    val transferStatus: String?,
    val fromAccountId: String?,
    val toAccountId: String?,
    val amountMinor: Long?,
    val currency: String?,
    val businessDate: LocalDate?,
    val riskScore: Int?,
    val idempotencyKey: String?,
    val failureCode: String?,
    val message: String?
)

data class CustomerTransferStatusResponse(
    val items: List<CustomerTransferStatusDto>
)
