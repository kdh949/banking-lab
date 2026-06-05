package lab.banking.payment.domain

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
