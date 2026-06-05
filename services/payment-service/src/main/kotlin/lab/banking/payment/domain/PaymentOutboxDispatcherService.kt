package lab.banking.payment.domain

import lab.banking.payment.persistence.PaymentRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentOutboxDispatcherService(
    private val repository: PaymentRepository,
    private val paymentInstructionService: PaymentInstructionService,
    private val coreLedgerPostingClient: CoreLedgerPostingClient
) {
    @Transactional
    fun dispatchNextLedgerPosting(request: DispatchPaymentLedgerPostingRequest): PaymentOutboxDispatchResponse {
        validateRequest(request)
        val event = repository.findNextLedgerPostingOutboxForUpdate()
            ?: return PaymentOutboxDispatchResponse(
                outboxEventId = null,
                paymentInstructionId = null,
                ledgerTransactionId = null,
                status = "NO_PENDING_EVENT",
                retryCount = 0
            )
        return try {
            val command = event.toCoreCommand(request)
            val result = coreLedgerPostingClient.postBillPayment(command)
            paymentInstructionService.recordSettlement(
                event.aggregateId,
                RecordPaymentSettlementRequest(
                    ledgerTransactionId = result.ledgerTransactionId,
                    idempotencyKey = settlementIdempotencyKey(event.outboxEventId),
                    requestedBy = request.requestedBy,
                    reason = request.reason
                )
            )
            repository.markOutboxPublished(event.outboxEventId)
            PaymentOutboxDispatchResponse(
                outboxEventId = event.outboxEventId,
                paymentInstructionId = event.aggregateId,
                ledgerTransactionId = result.ledgerTransactionId,
                status = "PUBLISHED",
                retryCount = event.retryCount
            )
        } catch (error: RuntimeException) {
            val retryCount = event.retryCount + 1
            val deadLetter = retryCount >= request.deadLetterThreshold
            repository.markOutboxFailed(
                outboxEventId = event.outboxEventId,
                retryCount = retryCount,
                errorMessage = error.message ?: error.javaClass.simpleName,
                deadLetter = deadLetter
            )
            PaymentOutboxDispatchResponse(
                outboxEventId = event.outboxEventId,
                paymentInstructionId = event.aggregateId,
                ledgerTransactionId = null,
                status = if (deadLetter) "DEAD_LETTER" else "FAILED",
                retryCount = retryCount
            )
        }
    }

    private fun validateRequest(request: DispatchPaymentLedgerPostingRequest) {
        requireNonBlank(request.requestedBy, "requestedBy")
        requireNonBlank(request.reason, "reason")
        if (request.deadLetterThreshold <= 0) {
            throw paymentError(
                code = "PAYMENT_OUTBOX_THRESHOLD_INVALID",
                status = HttpStatus.BAD_REQUEST,
                invariant = "payment outbox dead-letter threshold must be positive",
                message = "deadLetterThreshold must be positive",
                cause = "Payment Service cannot evaluate retry/dead-letter policy with a non-positive threshold.",
                fix = "Submit deadLetterThreshold greater than zero."
            )
        }
    }

    private fun PaymentOutboxRecord.toCoreCommand(request: DispatchPaymentLedgerPostingRequest): CoreLedgerPaymentPostingCommand =
        CoreLedgerPaymentPostingCommand(
            paymentInstructionId = requirePayloadString("paymentInstructionId"),
            debitAccountId = requirePayloadString("debitAccountId"),
            syntheticBillerId = requirePayloadString("syntheticBillerId"),
            amountMinor = requirePayloadLong("amountMinor"),
            idempotencyKey = ledgerIdempotencyKey(outboxEventId),
            requestedBy = request.requestedBy,
            reason = request.reason,
            currency = requirePayloadString("currency")
        )

    private fun PaymentOutboxRecord.requirePayloadString(field: String): String {
        val value = payload[field]?.toString()?.takeIf { it.isNotBlank() }
            ?: throw paymentError(
                code = "PAYMENT_OUTBOX_PAYLOAD_INVALID",
                status = HttpStatus.CONFLICT,
                invariant = "payment outbox event must contain a complete ledger command payload",
                message = "payment outbox payload is missing $field",
                cause = "A durable payment outbox event cannot be dispatched to core-banking without $field.",
                fix = "Regenerate the synthetic payment instruction event or quarantine the malformed event."
            )
        return value
    }

    private fun PaymentOutboxRecord.requirePayloadLong(field: String): Long {
        val raw = payload[field]
        val value = when (raw) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull()
            else -> null
        }
        if (value == null || value <= 0) {
            throw paymentError(
                code = "PAYMENT_OUTBOX_PAYLOAD_INVALID",
                status = HttpStatus.CONFLICT,
                invariant = "payment outbox event must contain a complete ledger command payload",
                message = "payment outbox payload has invalid $field",
                cause = "A durable payment outbox event cannot be dispatched to core-banking without a positive $field.",
                fix = "Regenerate the synthetic payment instruction event or quarantine the malformed event."
            )
        }
        return value
    }

    private fun requireNonBlank(value: String, field: String) {
        if (value.isBlank()) {
            throw paymentError(
                code = "PAYMENT_REQUIRED_FIELD_MISSING",
                status = HttpStatus.BAD_REQUEST,
                message = "$field is required",
                cause = "Payment outbox dispatch requires a stable actor and reason.",
                fix = "Populate $field before retrying the dispatch command.",
                details = mapOf("field" to field)
            )
        }
    }

    private fun ledgerIdempotencyKey(outboxEventId: String): String = "PAY-LEDGER-$outboxEventId"

    private fun settlementIdempotencyKey(outboxEventId: String): String = "PAY-SETTLEMENT-$outboxEventId"

    private fun paymentError(
        code: String,
        status: HttpStatus,
        invariant: String? = null,
        message: String,
        cause: String,
        fix: String,
        details: Map<String, Any?>? = null
    ): PaymentDomainException =
        PaymentDomainException(
            code = code,
            status = status,
            invariant = invariant,
            message = message,
            causeText = cause,
            fix = fix,
            details = details
        )
}
