package lab.banking.payment.domain

import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.util.UUID
import lab.banking.payment.persistence.PaymentRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentInstructionService(
    private val repository: PaymentRepository,
    private val objectMapper: ObjectMapper
) {
    @Transactional
    fun createInstruction(request: CreatePaymentInstructionRequest): PaymentInstructionResponse {
        validateCreate(request)
        val requestHash = requestHash(
            "CREATE",
            request.customerId,
            request.debitAccountId,
            request.billerId,
            request.amountMinor.toString(),
            request.currency,
            request.requestedBy,
            request.requestedChannel
        )
        replayIfPresent(request.idempotencyKey, "CREATE_PAYMENT_INSTRUCTION", requestHash)?.let { return it }

        val biller = repository.findBiller(request.billerId)
            ?: throw paymentError(
                code = "PAYMENT_SYNTHETIC_BILLER_REQUIRED",
                status = HttpStatus.NOT_FOUND,
                policy = "SYNTHETIC_ONLY_PAYMENT_SIMULATOR",
                message = "synthetic biller was not found",
                cause = "Payment instructions can target only checked-in synthetic biller simulators.",
                fix = "Use one of the seeded SYN-BILLER-* identifiers or add a synthetic-only biller migration."
            )
        if (!biller.syntheticOnly || biller.networkKind != "SYNTHETIC_BILLER_SIMULATOR") {
            throw paymentError(
                code = "PAYMENT_REAL_NETWORK_FORBIDDEN",
                status = HttpStatus.FORBIDDEN,
                policy = "SYNTHETIC_ONLY_PAYMENT_SIMULATOR",
                message = "real payment network configuration is forbidden",
                cause = "Payment Service must not connect to real billers, payment networks, or external financial institution APIs.",
                fix = "Keep biller networkKind set to SYNTHETIC_BILLER_SIMULATOR and syntheticOnly=true."
            )
        }

        val instructionId = paymentInstructionId()
        repository.insertInstruction(
            instructionId = instructionId,
            customerId = request.customerId,
            debitAccountId = request.debitAccountId,
            biller = biller,
            amountMinor = request.amountMinor,
            currency = request.currency
        )
        repository.insertAttempt(
            instructionId = instructionId,
            attemptId = paymentAttemptId(),
            status = "POSTING_REQUESTED",
            requestedBy = request.requestedBy,
            reason = request.reason ?: "Synthetic customer bill payment request"
        )
        repository.insertStatusHistory(
            instructionId = instructionId,
            status = PaymentInstructionStatus.POSTING_REQUESTED,
            actorId = request.requestedBy,
            reason = request.reason ?: "Synthetic customer bill payment request"
        )
        repository.insertOutboxEvent(
            aggregateId = instructionId,
            eventType = "PaymentLedgerPostingRequested",
            idempotencyKey = request.idempotencyKey,
            payload = mapOf(
                "contractVersion" to "2026-06-05",
                "ledgerCommandContract" to "core-banking.ledger.posting-request.v1",
                "coreLedgerCommandKind" to "BILL_PAYMENT",
                "paymentInstructionId" to instructionId,
                "customerId" to request.customerId,
                "debitAccountId" to request.debitAccountId,
                "syntheticBillerId" to request.billerId,
                "amountMinor" to request.amountMinor,
                "currency" to request.currency,
                "requestedBy" to request.requestedBy,
                "requestedChannel" to request.requestedChannel,
                "syntheticOnly" to true,
                "directLedgerWrite" to false,
                "realPaymentNetworkUsed" to false,
                "realFinancialInstitutionApiUsed" to false
            )
        )

        val response = PaymentInstructionResponse(item = instruction(instructionId), replayed = false)
        repository.insertIdempotency(
            idempotencyKey = request.idempotencyKey,
            commandType = "CREATE_PAYMENT_INSTRUCTION",
            requestHash = requestHash,
            aggregateId = instructionId,
            response = response
        )
        return response
    }

    fun instruction(instructionId: String): PaymentInstructionDto {
        val record = repository.findInstruction(instructionId) ?: throw notFound(instructionId)
        return record.toDto(repository.latestOutboxEventId(instructionId))
    }

    @Transactional
    fun recordSettlement(
        instructionId: String,
        request: RecordPaymentSettlementRequest
    ): PaymentInstructionResponse {
        validateIdempotentCommand(
            idempotencyKey = request.idempotencyKey,
            requestedBy = request.requestedBy,
            reason = request.reason
        )
        if (!request.ledgerTransactionId.startsWith("TX-")) {
            throw paymentError(
                code = "PAYMENT_LEDGER_REFERENCE_INVALID",
                status = HttpStatus.BAD_REQUEST,
                invariant = "successful payment settlement must reference a core-banking ledger transaction",
                message = "ledgerTransactionId must be a synthetic core-banking transaction id",
                cause = "Payment Service records settlement only after core-banking posts a ledger transaction.",
                fix = "Pass the TX-* transaction id returned by core-banking ledger posting."
            )
        }
        val requestHash = requestHash("SETTLE", instructionId, request.ledgerTransactionId, request.requestedBy)
        replayIfPresent(request.idempotencyKey, "RECORD_PAYMENT_SETTLEMENT", requestHash)?.let { return it }

        val current = repository.findInstructionForUpdate(instructionId) ?: throw notFound(instructionId)
        if (current.status == PaymentInstructionStatus.CANCELED) {
            throw invalidState(instructionId, current.status, "canceled payment instruction cannot be settled")
        }
        if (current.status == PaymentInstructionStatus.SETTLED && current.ledgerTransactionId != request.ledgerTransactionId) {
            throw invalidState(instructionId, current.status, "settled payment instruction cannot change ledger transaction reference")
        }
        if (current.status != PaymentInstructionStatus.SETTLED) {
            repository.updateSettlement(
                instructionId = instructionId,
                ledgerTransactionId = request.ledgerTransactionId
            )
            repository.markLatestAttemptSettled(instructionId)
            repository.insertStatusHistory(
                instructionId = instructionId,
                status = PaymentInstructionStatus.SETTLED,
                actorId = request.requestedBy,
                reason = request.reason
            )
            repository.insertOutboxEvent(
                aggregateId = instructionId,
                eventType = "PaymentInstructionSettled",
                idempotencyKey = request.idempotencyKey,
                payload = mapOf(
                    "contractVersion" to "2026-06-05",
                    "paymentInstructionId" to instructionId,
                    "ledgerTransactionId" to request.ledgerTransactionId,
                    "syntheticOnly" to true,
                    "directLedgerWrite" to false
                )
            )
        }

        val response = PaymentInstructionResponse(item = instruction(instructionId), replayed = false)
        repository.insertIdempotency(
            idempotencyKey = request.idempotencyKey,
            commandType = "RECORD_PAYMENT_SETTLEMENT",
            requestHash = requestHash,
            aggregateId = instructionId,
            response = response
        )
        return response
    }

    @Transactional
    fun cancelInstruction(
        instructionId: String,
        request: CancelPaymentInstructionRequest
    ): PaymentInstructionResponse {
        validateIdempotentCommand(
            idempotencyKey = request.idempotencyKey,
            requestedBy = request.requestedBy,
            reason = request.reason
        )
        val requestHash = requestHash("CANCEL", instructionId, request.requestedBy, request.reason)
        replayIfPresent(request.idempotencyKey, "CANCEL_PAYMENT_INSTRUCTION", requestHash)?.let { return it }

        val current = repository.findInstructionForUpdate(instructionId) ?: throw notFound(instructionId)
        if (current.status == PaymentInstructionStatus.SETTLED) {
            throw invalidState(instructionId, current.status, "settled payment instruction cannot be canceled")
        }
        if (current.status != PaymentInstructionStatus.CANCELED) {
            repository.updateStatus(instructionId, PaymentInstructionStatus.CANCELED)
            repository.markLatestAttemptCanceled(instructionId)
            repository.insertStatusHistory(
                instructionId = instructionId,
                status = PaymentInstructionStatus.CANCELED,
                actorId = request.requestedBy,
                reason = request.reason
            )
            repository.insertOutboxEvent(
                aggregateId = instructionId,
                eventType = "PaymentInstructionCanceled",
                idempotencyKey = request.idempotencyKey,
                payload = mapOf(
                    "contractVersion" to "2026-06-05",
                    "paymentInstructionId" to instructionId,
                    "syntheticOnly" to true,
                    "directLedgerWrite" to false
                )
            )
        }

        val response = PaymentInstructionResponse(item = instruction(instructionId), replayed = false)
        repository.insertIdempotency(
            idempotencyKey = request.idempotencyKey,
            commandType = "CANCEL_PAYMENT_INSTRUCTION",
            requestHash = requestHash,
            aggregateId = instructionId,
            response = response
        )
        return response
    }

    private fun replayIfPresent(
        idempotencyKey: String,
        commandType: String,
        requestHash: String
    ): PaymentInstructionResponse? {
        val existing = repository.findIdempotency(idempotencyKey) ?: return null
        if (existing.commandType != commandType || existing.requestHash != requestHash) {
            throw paymentError(
                code = "PAYMENT_IDEMPOTENCY_CONFLICT",
                status = HttpStatus.CONFLICT,
                invariant = "idempotent payment command creates at most one business result",
                message = "idempotency key was reused with a different payment command payload",
                cause = "The same idempotency key already exists for a different command type or request hash.",
                fix = "Replay the original payload or use a new idempotency key for a different payment command.",
                details = mapOf("idempotencyKey" to idempotencyKey)
            )
        }
        return objectMapper
            .readValue(existing.responseJson, PaymentInstructionResponse::class.java)
            .copy(replayed = true)
    }

    private fun validateCreate(request: CreatePaymentInstructionRequest) {
        validateIdempotentCommand(
            idempotencyKey = request.idempotencyKey,
            requestedBy = request.requestedBy,
            reason = request.reason ?: "Synthetic customer bill payment request"
        )
        requireNonBlank(request.customerId, "customerId")
        requireNonBlank(request.debitAccountId, "debitAccountId")
        requireNonBlank(request.billerId, "billerId")
        requireNonBlank(request.currency, "currency")
        if (request.amountMinor <= 0) {
            throw paymentError(
                code = "PAYMENT_AMOUNT_INVALID",
                status = HttpStatus.BAD_REQUEST,
                invariant = "payment amount must be positive",
                message = "payment amount must be positive",
                cause = "Payment Service rejected a non-positive amount before persistence.",
                fix = "Submit amountMinor greater than zero."
            )
        }
    }

    private fun validateIdempotentCommand(idempotencyKey: String, requestedBy: String, reason: String) {
        requireNonBlank(idempotencyKey, "idempotencyKey")
        requireNonBlank(requestedBy, "requestedBy")
        requireNonBlank(reason, "reason")
    }

    private fun requireNonBlank(value: String, field: String) {
        if (value.isBlank()) {
            throw paymentError(
                code = "PAYMENT_REQUIRED_FIELD_MISSING",
                status = HttpStatus.BAD_REQUEST,
                message = "$field is required",
                cause = "Payment Service requires stable command identity, actor, and synthetic target fields.",
                fix = "Populate $field before retrying the payment command.",
                details = mapOf("field" to field)
            )
        }
    }

    private fun PaymentInstructionRecord.toDto(lastOutboxEventId: String?): PaymentInstructionDto =
        PaymentInstructionDto(
            paymentInstructionId = paymentInstructionId,
            customerId = customerId,
            debitAccountId = debitAccountId,
            billerId = billerId,
            billerName = billerName,
            amountMinor = amountMinor,
            currency = currency,
            status = status,
            ledgerTransactionId = ledgerTransactionId,
            lastOutboxEventId = lastOutboxEventId,
            syntheticOnly = syntheticOnly,
            createdAt = createdAt,
            updatedAt = updatedAt
        )

    private fun notFound(instructionId: String): PaymentDomainException =
        paymentError(
            code = "PAYMENT_INSTRUCTION_NOT_FOUND",
            status = HttpStatus.NOT_FOUND,
            message = "payment instruction was not found",
            cause = "No durable payment instruction exists for the supplied id.",
            fix = "Check the instruction id returned by the create instruction command.",
            details = mapOf("paymentInstructionId" to instructionId)
        )

    private fun invalidState(
        instructionId: String,
        status: PaymentInstructionStatus,
        message: String
    ): PaymentDomainException =
        paymentError(
            code = "PAYMENT_STATE_TRANSITION_REJECTED",
            status = HttpStatus.CONFLICT,
            invariant = "payment status transitions are append-only and auditable",
            message = message,
            cause = "Payment Service rejected an unsafe status transition.",
            fix = "Use the current payment status and submit the next allowed command.",
            details = mapOf("paymentInstructionId" to instructionId, "status" to status.name)
        )

    private fun paymentError(
        code: String,
        status: HttpStatus,
        invariant: String? = null,
        policy: String? = null,
        message: String,
        cause: String,
        fix: String,
        details: Map<String, Any?>? = null
    ): PaymentDomainException =
        PaymentDomainException(
            code = code,
            status = status,
            invariant = invariant,
            policy = policy,
            message = message,
            causeText = cause,
            fix = fix,
            details = details
        )

    private fun paymentInstructionId(): String = "PAY-${UUID.randomUUID().toString().uppercase()}"

    private fun paymentAttemptId(): String = "PAT-${UUID.randomUUID().toString().uppercase()}"

    private fun requestHash(vararg parts: String): String {
        val payload = parts.joinToString(separator = "|")
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
