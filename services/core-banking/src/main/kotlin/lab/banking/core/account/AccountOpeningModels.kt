package lab.banking.core.account

import java.time.LocalDate
import java.time.OffsetDateTime
import lab.banking.core.approval.OperatorApproval

data class AccountOpeningRequestCommand(
    val requestedBy: String,
    val requestedByRole: String = "BRANCH_STAFF",
    val reason: String,
    val idempotencyKey: String,
    val customerId: String,
    val productCode: String = "SYNTHETIC_DEPOSIT",
    val accountAlias: String? = null,
    val currency: String = "KRW",
    val dailyTransferLimitMinor: Long = 100_000_000,
    val singleTransferLimitMinor: Long = 50_000_000,
    val initialDepositAmountMinor: Long = 0,
    val initialDepositIdempotencyKey: String? = null,
    val businessDate: LocalDate? = null
)

data class AccountOpeningApproveCommand(
    val approvedBy: String,
    val approvedByRole: String = "BRANCH_MANAGER",
    val screenId: String? = "ACC-202"
)

data class AccountOpeningRejectCommand(
    val rejectedBy: String,
    val rejectedByRole: String = "BRANCH_MANAGER",
    val rejectReason: String,
    val screenId: String? = "ACC-202"
)

data class AccountOpeningExecuteCommand(
    val executedBy: String,
    val executedByRole: String = "BRANCH_STAFF",
    val reason: String,
    val idempotencyKey: String
)

data class AccountOpeningRequestDto(
    val requestId: String,
    val idempotencyKey: String,
    val status: String,
    val requestedBy: String,
    val requestedByRole: String,
    val reason: String,
    val approvalId: String,
    val customerId: String,
    val requestedProductCode: String,
    val requestedAccountAlias: String?,
    val requestedCurrency: String,
    val requestedDailyTransferLimitMinor: Long,
    val requestedSingleTransferLimitMinor: Long,
    val requestedInitialDepositAmountMinor: Long,
    val requestedInitialDepositIdempotencyKey: String?,
    val requestedBusinessDate: LocalDate?,
    val generatedAccountId: String?,
    val generatedMaskedAccountNo: String?,
    val approvedBy: String?,
    val approvedAt: OffsetDateTime?,
    val rejectedBy: String?,
    val rejectedAt: OffsetDateTime?,
    val rejectReason: String?,
    val executedBy: String?,
    val executedByRole: String?,
    val executedAt: OffsetDateTime?,
    val initialDepositLedgerTransactionId: String?,
    val syntheticOnly: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

data class CreatedSyntheticAccountDto(
    val customerId: String,
    val accountId: String,
    val maskedAccountNo: String,
    val currency: String,
    val ledgerBalanceMinor: Long,
    val availableBalanceMinor: Long,
    val initialDepositLedgerTransactionId: String?
)

data class AccountOpeningRequestResponse(
    val item: AccountOpeningRequestDto,
    val approval: OperatorApproval,
    val replayed: Boolean,
    val syntheticOnly: Boolean = true
)

data class AccountOpeningReviewResponse(
    val item: AccountOpeningRequestDto,
    val approval: OperatorApproval,
    val replayed: Boolean,
    val syntheticOnly: Boolean = true
)

data class AccountOpeningExecuteResponse(
    val item: AccountOpeningRequestDto,
    val approval: OperatorApproval,
    val account: CreatedSyntheticAccountDto?,
    val replayed: Boolean,
    val syntheticOnly: Boolean = true
)
