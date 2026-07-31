package lab.banking.core.auth

import java.time.OffsetDateTime

data class CustomerSignupCommand(
    val idempotencyKey: String,
    val username: String,
    val password: String,
    val syntheticCustomerName: String,
    val syntheticPhone: String = "010-0000-0000",
    val syntheticAddress: String = "Synthetic self-service address",
    val customerGrade: String = "STANDARD",
    val riskGrade: String = "LOW",
    val sourceOfFundsCode: String = "SELF_SERVICE_SYNTHETIC",
    val transactionPurposeCode: String = "DAILY_BANKING"
)

data class CustomerLoginCommand(
    val username: String,
    val password: String
)

data class CustomerAuthResponse(
    val customer: CustomerAuthCustomerDto,
    val session: CustomerAuthSessionDto,
    val bearerToken: String,
    val tokenType: String = "Bearer",
    val expiresAt: OffsetDateTime,
    val replayed: Boolean,
    val onboardingStatus: String = "UNKNOWN",
    val duplicateCheckStatus: String = "UNKNOWN",
    val nextRequiredAction: String = "UNKNOWN",
    val syntheticOnly: Boolean = true
)

data class CustomerAuthCustomerDto(
    val customerId: String,
    val authSubject: String,
    val username: String,
    val kycStatus: String,
    val onboardingStatus: String = "UNKNOWN",
    val duplicateCheckStatus: String = "UNKNOWN",
    val nextRequiredAction: String = "UNKNOWN",
    val syntheticOnly: Boolean = true
)

data class CustomerAuthSessionDto(
    val subject: String,
    val customerId: String,
    val roles: List<String>,
    val issuer: String,
    val sessionId: String,
    val authTime: OffsetDateTime,
    val issuedAt: OffsetDateTime,
    val syntheticOnly: Boolean = true
)
