package lab.banking.payment.domain

import java.time.LocalDate
import java.time.OffsetDateTime
import org.springframework.http.HttpStatus

enum class PaymentInstructionStatus {
    POSTING_REQUESTED,
    SETTLED,
    CANCELED,
    FAILED
}

data class CreatePaymentInstructionRequest(
    val customerId: String,
    val debitAccountId: String,
    val billerId: String,
    val amountMinor: Long,
    val currency: String = "KRW",
    val idempotencyKey: String,
    val requestedBy: String,
    val requestedChannel: String = "CUSTOMER_WEB",
    val reason: String? = null
)

data class RecordPaymentSettlementRequest(
    val ledgerTransactionId: String,
    val idempotencyKey: String,
    val requestedBy: String,
    val reason: String
)

data class CancelPaymentInstructionRequest(
    val idempotencyKey: String,
    val requestedBy: String,
    val reason: String
)

data class RequestPaymentCancellationApprovalRequest(
    val idempotencyKey: String,
    val requestedBy: String,
    val reason: String
)

data class ReviewPaymentCancellationRequest(
    val idempotencyKey: String,
    val requestedBy: String,
    val reason: String
)

data class PaymentInstructionDto(
    val paymentInstructionId: String,
    val customerId: String,
    val debitAccountId: String,
    val billerId: String,
    val billerName: String,
    val amountMinor: Long,
    val currency: String,
    val status: PaymentInstructionStatus,
    val ledgerTransactionId: String?,
    val lastOutboxEventId: String?,
    val syntheticOnly: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

data class PaymentInstructionResponse(
    val item: PaymentInstructionDto,
    val replayed: Boolean,
    val auditEventId: String? = null
)

enum class PaymentCancellationRequestStatus {
    PENDING,
    APPROVED,
    REJECTED
}

data class PaymentCancellationRequestDto(
    val cancellationRequestId: String,
    val paymentInstructionId: String,
    val status: PaymentCancellationRequestStatus,
    val makerId: String,
    val makerRole: String,
    val makerReason: String,
    val checkerId: String?,
    val checkerRole: String?,
    val checkerReason: String?,
    val syntheticOnly: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val decidedAt: OffsetDateTime?
)

data class PaymentCancellationRequestResponse(
    val item: PaymentCancellationRequestDto,
    val instruction: PaymentInstructionDto?,
    val replayed: Boolean
)

enum class PaymentAutopayStatus {
    ACTIVE,
    PAUSED,
    CANCELED
}

enum class PaymentAutopayFrequency {
    DAILY,
    WEEKLY,
    MONTHLY
}

data class CreateAutopayAgreementRequest(
    val customerId: String,
    val debitAccountId: String,
    val billerId: String,
    val amountMinor: Long,
    val currency: String = "KRW",
    val frequency: PaymentAutopayFrequency,
    val nextRunOn: LocalDate,
    val idempotencyKey: String,
    val requestedBy: String,
    val requestedChannel: String = "CUSTOMER_WEB",
    val reason: String
)

data class PauseAutopayAgreementRequest(
    val idempotencyKey: String,
    val requestedBy: String,
    val reason: String
)

data class ResumeAutopayAgreementRequest(
    val idempotencyKey: String,
    val requestedBy: String,
    val reason: String,
    val nextRunOn: LocalDate? = null
)

data class CancelAutopayAgreementRequest(
    val idempotencyKey: String,
    val requestedBy: String,
    val reason: String
)

data class ExecuteDueAutopayRequest(
    val businessDate: LocalDate,
    val idempotencyKey: String,
    val requestedBy: String,
    val reason: String,
    val limit: Int = 50
)

data class PaymentAutopayAgreementDto(
    val autopayAgreementId: String,
    val customerId: String,
    val debitAccountId: String,
    val billerId: String,
    val billerName: String,
    val amountMinor: Long,
    val currency: String,
    val frequency: PaymentAutopayFrequency,
    val status: PaymentAutopayStatus,
    val nextRunOn: LocalDate,
    val lastRunOn: LocalDate?,
    val lastPaymentInstructionId: String?,
    val syntheticOnly: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

data class PaymentAutopayAgreementResponse(
    val item: PaymentAutopayAgreementDto,
    val replayed: Boolean
)

data class PaymentAutopayExecutionDto(
    val autopayExecutionId: String,
    val autopayAgreementId: String,
    val scheduledRunOn: LocalDate,
    val paymentInstructionId: String,
    val status: String,
    val syntheticOnly: Boolean,
    val createdAt: OffsetDateTime
)

data class ExecuteDueAutopayResponse(
    val items: List<PaymentAutopayExecutionDto>,
    val executedCount: Int,
    val replayed: Boolean
)

data class DispatchPaymentLedgerPostingRequest(
    val requestedBy: String,
    val reason: String,
    val deadLetterThreshold: Int = 3
)

data class PaymentOutboxDispatchResponse(
    val outboxEventId: String?,
    val paymentInstructionId: String?,
    val ledgerTransactionId: String?,
    val status: String,
    val retryCount: Int,
    val syntheticOnly: Boolean = true
)

data class CoreLedgerPaymentPostingCommand(
    val paymentInstructionId: String,
    val debitAccountId: String,
    val syntheticBillerId: String,
    val amountMinor: Long,
    val idempotencyKey: String,
    val requestedBy: String,
    val requestedChannel: String = "PAYMENT_SERVICE",
    val reason: String,
    val currency: String = "KRW"
)

data class CoreLedgerPostingResult(
    val ledgerTransactionId: String
)

interface CoreLedgerPostingClient {
    fun postBillPayment(command: CoreLedgerPaymentPostingCommand): CoreLedgerPostingResult
}

data class PaymentBillerRecord(
    val billerId: String,
    val displayName: String,
    val syntheticOnly: Boolean,
    val networkKind: String
)

data class PaymentInstructionRecord(
    val paymentInstructionId: String,
    val customerId: String,
    val debitAccountId: String,
    val billerId: String,
    val billerName: String,
    val amountMinor: Long,
    val currency: String,
    val status: PaymentInstructionStatus,
    val ledgerTransactionId: String?,
    val syntheticOnly: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

data class PaymentCancellationRequestRecord(
    val cancellationRequestId: String,
    val paymentInstructionId: String,
    val status: PaymentCancellationRequestStatus,
    val makerId: String,
    val makerRole: String,
    val makerReason: String,
    val checkerId: String?,
    val checkerRole: String?,
    val checkerReason: String?,
    val syntheticOnly: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val decidedAt: OffsetDateTime?
)

data class PaymentAutopayAgreementRecord(
    val autopayAgreementId: String,
    val customerId: String,
    val debitAccountId: String,
    val billerId: String,
    val billerName: String,
    val amountMinor: Long,
    val currency: String,
    val frequency: PaymentAutopayFrequency,
    val status: PaymentAutopayStatus,
    val nextRunOn: LocalDate,
    val lastRunOn: LocalDate?,
    val syntheticOnly: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

data class PaymentAutopayExecutionRecord(
    val autopayExecutionId: String,
    val autopayAgreementId: String,
    val scheduledRunOn: LocalDate,
    val paymentInstructionId: String,
    val status: String,
    val syntheticOnly: Boolean,
    val createdAt: OffsetDateTime
)

data class PaymentOutboxRecord(
    val outboxEventId: String,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val idempotencyKey: String,
    val payload: Map<String, Any?>,
    val status: String,
    val retryCount: Int,
    val errorMessage: String?
)

data class PaymentIdempotencyRecord(
    val idempotencyKey: String,
    val commandType: String,
    val requestHash: String,
    val aggregateId: String,
    val responseJson: String
)

class PaymentDomainException(
    val code: String,
    val status: HttpStatus,
    val invariant: String? = null,
    val policy: String? = null,
    override val message: String,
    val causeText: String,
    val fix: String,
    val details: Map<String, Any?>? = null
) : RuntimeException(message)
