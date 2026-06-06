package lab.banking.core.customer

data class CustomerAccountDetailDto(
    val customerId: String,
    val accountId: String,
    val maskedAccountNo: String,
    val status: String,
    val currency: String,
    val ledgerBalanceMinor: Long,
    val availableBalanceMinor: Long,
    val holdAmountMinor: Long
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
