package lab.banking.core.customer

import java.time.OffsetDateTime

data class CustomerOnboardingCheckDto(
    val checkId: String,
    val customerId: String,
    val checkType: String,
    val status: String,
    val riskLevel: String,
    val evidence: Map<String, Any?>,
    val createdAt: OffsetDateTime,
    val syntheticOnly: Boolean = true
)

data class CustomerProfileDto(
    val customerId: String,
    val authSubject: String,
    val username: String,
    val maskedCustomerName: String,
    val maskedPhone: String?,
    val maskedAddress: String?,
    val customerGrade: String,
    val riskGrade: String,
    val kycStatus: String,
    val onboardingStatus: String,
    val duplicateCheckStatus: String,
    val nextRequiredAction: String,
    val lastLoginAt: OffsetDateTime?,
    val authIdentityStatus: String,
    val onboardingChecks: List<CustomerOnboardingCheckDto>,
    val syntheticOnly: Boolean = true,
    val maskingPolicy: String = "CUSTOMER_SELF"
)

data class CustomerSelfServiceAccountOpeningCommand(
    val idempotencyKey: String,
    val productCode: String = "SYNTHETIC_DEPOSIT",
    val accountAlias: String? = null,
    val currency: String = "KRW",
    val syntheticInitialDepositAmountMinor: Long = 0,
    val termsAccepted: Boolean
)

data class CustomerSelfServiceAccountOpeningRequestDto(
    val requestId: String,
    val idempotencyKey: String,
    val status: String,
    val customerId: String,
    val approvalId: String?,
    val approvalStatus: String?,
    val staffAccountOpeningRequestId: String?,
    val requestedProductCode: String,
    val requestedAccountAlias: String?,
    val requestedCurrency: String,
    val requestedInitialDepositAmountMinor: Long,
    val generatedAccountId: String?,
    val generatedMaskedAccountNo: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val syntheticOnly: Boolean = true
)

data class CustomerSelfServiceAccountOpeningRequestResponse(
    val item: CustomerSelfServiceAccountOpeningRequestDto,
    val replayed: Boolean,
    val syntheticOnly: Boolean = true
)

data class CustomerSelfServiceAccountOpeningRequestListResponse(
    val items: List<CustomerSelfServiceAccountOpeningRequestDto>,
    val syntheticOnly: Boolean = true
)

data class Customer360Dto(
    val profile: CustomerProfileDto,
    val kycSummary: Customer360StatusSummaryDto,
    val accountSummary: Customer360AccountSummaryDto,
    val accounts: List<Customer360AccountDto>,
    val loanSummary: Customer360StatusSummaryDto,
    val cardSummary: Customer360StatusSummaryDto,
    val complaintSummary: Customer360StatusSummaryDto,
    val paymentSummary: Customer360StatusSummaryDto,
    val notificationSummary: Customer360StatusSummaryDto,
    val recentLedgerActivity: List<CustomerRecentLedgerActivityDto>,
    val accessHistorySummary: Customer360AccessHistorySummaryDto,
    val availableActions: List<Customer360AvailableActionDto>,
    val sourceWatermarks: List<Customer360SourceWatermarkDto>,
    val syntheticOnly: Boolean = true,
    val maskingPolicy: String = "CUSTOMER_SELF"
)

data class Customer360AccountDto(
    val accountId: String,
    val maskedAccountNo: String,
    val status: String,
    val currency: String,
    val ledgerBalanceMinor: Long,
    val availableBalanceMinor: Long,
    val holdAmountMinor: Long,
    val openedAt: OffsetDateTime?,
    val statementActions: List<CustomerStatementActionDto>,
    val syntheticOnly: Boolean = true
)

data class Customer360AccountSummaryDto(
    val totalAccounts: Int,
    val activeAccounts: Int,
    val totalLedgerBalanceMinor: Long,
    val totalAvailableBalanceMinor: Long,
    val totalHoldAmountMinor: Long,
    val currency: String
)

data class Customer360StatusSummaryDto(
    val totalCount: Int,
    val statusCounts: Map<String, Int>,
    val source: String,
    val syntheticOnly: Boolean = true
)

data class Customer360AccessHistorySummaryDto(
    val totalEvents: Int,
    val recentEventTypes: List<String>,
    val lastAccessAt: OffsetDateTime?
)

data class Customer360AvailableActionDto(
    val actionType: String,
    val enabled: Boolean,
    val reason: String? = null,
    val href: String? = null
)

data class Customer360SourceWatermarkDto(
    val source: String,
    val lastUpdatedAt: OffsetDateTime?,
    val rowCount: Int
)

data class CustomerStatementArtifactDto(
    val statementId: String,
    val customerId: String,
    val accountId: String?,
    val from: java.time.LocalDate,
    val to: java.time.LocalDate,
    val statementScope: String,
    val sourceLedgerHash: String,
    val payloadHash: String,
    val createdAt: OffsetDateTime,
    val lastViewedAt: OffsetDateTime,
    val syntheticOnly: Boolean = true
)

data class CustomerStatementArtifactListResponse(
    val items: List<CustomerStatementArtifactDto>,
    val syntheticOnly: Boolean = true
)
