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
