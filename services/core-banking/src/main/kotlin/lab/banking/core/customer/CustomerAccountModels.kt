package lab.banking.core.customer

import java.time.LocalDate
import java.time.OffsetDateTime

data class CustomerAccountDetailDto(
    val customerId: String,
    val accountId: String,
    val maskedAccountNo: String,
    val status: String,
    val currency: String,
    val ledgerBalanceMinor: Long,
    val availableBalanceMinor: Long,
    val holdAmountMinor: Long,
    val openedAt: OffsetDateTime? = null,
    val limits: CustomerAccountLimitsDto? = null,
    val holds: List<CustomerAccountHoldDto> = emptyList(),
    val recentTransactions: List<CustomerRecentLedgerActivityDto> = emptyList(),
    val statementActions: List<CustomerStatementActionDto> = emptyList(),
    val syntheticOnly: Boolean = true,
    val maskingPolicy: String = "CUSTOMER_SELF"
)

data class CustomerAccountListItemDto(
    val customerId: String,
    val accountId: String,
    val maskedAccountNo: String,
    val status: String,
    val currency: String,
    val ledgerBalanceMinor: Long,
    val availableBalanceMinor: Long,
    val holdAmountMinor: Long,
    val syntheticOnly: Boolean = true
)

data class CustomerAccountListResponse(
    val items: List<CustomerAccountListItemDto>,
    val syntheticOnly: Boolean = true
)

data class InternalRecipientAccountDto(
    val accountId: String,
    val maskedAccountNo: String,
    val status: String,
    val currency: String,
    val recipientLabel: String,
    val internalOnly: Boolean = true,
    val syntheticOnly: Boolean = true
)

data class InternalRecipientLookupResponse(
    val item: InternalRecipientAccountDto,
    val syntheticOnly: Boolean = true
)

data class CustomerAccountLimitsDto(
    val dailyTransferLimitMinor: Long,
    val singleTransferLimitMinor: Long,
    val updatedAt: OffsetDateTime?
)

data class CustomerAccountHoldDto(
    val holdId: String,
    val holdAmountMinor: Long,
    val reasonCode: String,
    val status: String,
    val approvalId: String?,
    val createdAt: OffsetDateTime
)

data class CustomerRecentLedgerActivityDto(
    val transactionId: String,
    val transactionType: String,
    val businessDate: LocalDate,
    val postedAt: OffsetDateTime?,
    val accountId: String,
    val maskedAccountNo: String?,
    val direction: String,
    val amountMinor: Long,
    val signedAmountMinor: Long,
    val currency: String,
    val postingType: String,
    val requestedChannel: String
)

data class CustomerStatementActionDto(
    val actionType: String,
    val href: String,
    val ownershipEnforced: Boolean = true,
    val syntheticOnly: Boolean = true
)
