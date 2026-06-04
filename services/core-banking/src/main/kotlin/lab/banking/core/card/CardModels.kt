package lab.banking.core.card

import java.time.LocalDate
import java.time.OffsetDateTime
import lab.banking.core.ledger.domain.LedgerCommandResult

data class IssueCardCommand(
    val customerId: String,
    val accountId: String,
    val panToken: String,
    val panLast4: String,
    val dailyLimitMinor: Long,
    val monthlyLimitMinor: Long,
    val singleLimitMinor: Long,
    val requestedBy: String? = null,
    val requestedByRole: String? = null,
    val reason: String? = null,
    val idempotencyKey: String? = null
)

data class CardDto(
    val cardId: String,
    val customerId: String,
    val accountId: String,
    val panToken: String,
    val panLast4: String,
    val status: String,
    val dailyLimitMinor: Long,
    val monthlyLimitMinor: Long,
    val singleLimitMinor: Long,
    val createdAt: OffsetDateTime,
    val syntheticOnly: Boolean = true
)

data class CardIssueResponse(
    val item: CardDto,
    val replayed: Boolean
)

data class ThreeDsSimulationCommand(
    val cardId: String,
    val amountMinor: Long,
    val idempotencyKey: String
)

data class ThreeDsSimulationDto(
    val authenticationId: String,
    val cardId: String,
    val amountMinor: Long,
    val status: String,
    val createdAt: OffsetDateTime,
    val syntheticOnly: Boolean = true
)

data class CardAuthorizationCommand(
    val cardId: String,
    val amountMinor: Long,
    val merchantName: String,
    val businessDate: LocalDate? = null,
    val threeDsAuthenticationId: String? = null,
    val requestedBy: String? = null,
    val requestedChannel: String? = null,
    val reason: String? = null,
    val idempotencyKey: String? = null,
    val currency: String = "KRW"
)

data class CardAuthorizationDto(
    val authorizationId: String,
    val cardId: String,
    val accountId: String,
    val amountMinor: Long,
    val currency: String,
    val merchantName: String,
    val businessDate: LocalDate,
    val status: String,
    val holdId: String?,
    val threeDsAuthenticationId: String?,
    val createdAt: OffsetDateTime
)

data class CardAuthorizationResponse(
    val item: CardAuthorizationDto,
    val replayed: Boolean
)

data class CardCaptureCommand(
    val authorizationId: String = "",
    val businessDate: LocalDate? = null,
    val requestedBy: String? = null,
    val requestedChannel: String? = null,
    val reason: String? = null,
    val idempotencyKey: String? = null
)

data class CardCaptureDto(
    val captureId: String,
    val authorizationId: String,
    val cardId: String,
    val amountMinor: Long,
    val currency: String,
    val ledgerTransactionId: String,
    val status: String,
    val createdAt: OffsetDateTime,
    val reversedAt: OffsetDateTime?
)

data class CardCaptureResponse(
    val item: CardCaptureDto,
    val ledgerTransaction: LedgerCommandResult,
    val replayed: Boolean
)

data class CardCancelCommand(
    val requestedBy: String? = null,
    val requestedChannel: String? = null,
    val reason: String? = null,
    val idempotencyKey: String? = null
)

data class CardLossReportCommand(
    val requestedBy: String? = null,
    val requestedByRole: String? = null,
    val reason: String? = null
)
