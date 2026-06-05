package lab.banking.payment.domain

import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.util.UUID
import lab.banking.payment.persistence.PaymentRepository
import lab.banking.payment.security.PaymentPrincipal
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

    @Transactional
    fun requestCancellationApproval(
        instructionId: String,
        request: RequestPaymentCancellationApprovalRequest,
        principal: PaymentPrincipal?
    ): PaymentCancellationRequestResponse {
        val makerId = actorIdFromRequest(request.requestedBy, principal)
        val makerRole = correctionMakerRole(principal)
        validateIdempotentCommand(
            idempotencyKey = request.idempotencyKey,
            requestedBy = makerId,
            reason = request.reason
        )
        val requestHash = requestHash("REQUEST_CANCEL_APPROVAL", instructionId, makerId, request.reason)
        replayCancellationIfPresent(
            request.idempotencyKey,
            "REQUEST_PAYMENT_CANCELLATION_APPROVAL",
            requestHash
        )?.let { return it }

        val current = repository.findInstructionForUpdate(instructionId) ?: throw notFound(instructionId)
        if (current.status != PaymentInstructionStatus.POSTING_REQUESTED) {
            throw invalidState(instructionId, current.status, "only pre-settlement payment instructions can be submitted for staff cancellation")
        }
        repository.findPendingCancellationRequest(instructionId)?.let { pending ->
            throw paymentError(
                code = "PAYMENT_CANCELLATION_REQUEST_ALREADY_PENDING",
                status = HttpStatus.CONFLICT,
                policy = "PAYMENT_MAKER_CHECKER_REQUIRED",
                message = "payment cancellation request is already pending approval",
                cause = "Staff payment corrections must progress through one open maker-checker request at a time.",
                fix = "Approve, reject, or query the existing cancellation request before creating another one.",
                details = mapOf("paymentInstructionId" to instructionId, "cancellationRequestId" to pending.cancellationRequestId)
            )
        }

        val cancellationRequestId = paymentCancellationRequestId()
        repository.insertCancellationRequest(
            cancellationRequestId = cancellationRequestId,
            instructionId = instructionId,
            makerId = makerId,
            makerRole = makerRole,
            makerReason = request.reason
        )

        val response = cancellationResponse(cancellationRequestId, replayed = false)
        repository.insertIdempotency(
            idempotencyKey = request.idempotencyKey,
            commandType = "REQUEST_PAYMENT_CANCELLATION_APPROVAL",
            requestHash = requestHash,
            aggregateId = cancellationRequestId,
            response = response
        )
        return response
    }

    @Transactional
    fun approveCancellationRequest(
        cancellationRequestId: String,
        request: ReviewPaymentCancellationRequest,
        principal: PaymentPrincipal?
    ): PaymentCancellationRequestResponse {
        val checkerId = actorIdFromRequest(request.requestedBy, principal)
        val checkerRole = correctionCheckerRole(principal)
        validateIdempotentCommand(
            idempotencyKey = request.idempotencyKey,
            requestedBy = checkerId,
            reason = request.reason
        )
        val requestHash = requestHash("APPROVE_CANCEL_APPROVAL", cancellationRequestId, checkerId, request.reason)
        replayCancellationIfPresent(
            request.idempotencyKey,
            "APPROVE_PAYMENT_CANCELLATION_APPROVAL",
            requestHash
        )?.let { return it }

        val correction = repository.findCancellationRequestForUpdate(cancellationRequestId)
            ?: throw cancellationRequestNotFound(cancellationRequestId)
        if (correction.status != PaymentCancellationRequestStatus.PENDING) {
            throw cancellationRequestTerminal(cancellationRequestId, correction.status)
        }
        if (correction.makerId == checkerId) {
            throw paymentError(
                code = "PAYMENT_MAKER_CHECKER_SEPARATION_REQUIRED",
                status = HttpStatus.FORBIDDEN,
                policy = "PAYMENT_MAKER_CHECKER_REQUIRED",
                message = "payment cancellation maker cannot approve their own request",
                cause = "High-risk staff payment corrections require independent checker approval.",
                fix = "Use a different authorized checker account for the approval decision.",
                details = mapOf("cancellationRequestId" to cancellationRequestId, "makerId" to correction.makerId)
            )
        }

        val current = repository.findInstructionForUpdate(correction.paymentInstructionId)
            ?: throw notFound(correction.paymentInstructionId)
        if (current.status == PaymentInstructionStatus.SETTLED) {
            throw invalidState(correction.paymentInstructionId, current.status, "settled payment instruction cannot be staff-canceled")
        }
        if (current.status !in setOf(PaymentInstructionStatus.POSTING_REQUESTED, PaymentInstructionStatus.CANCELED)) {
            throw invalidState(correction.paymentInstructionId, current.status, "payment instruction status cannot be staff-canceled")
        }
        if (current.status == PaymentInstructionStatus.POSTING_REQUESTED) {
            repository.updateStatus(correction.paymentInstructionId, PaymentInstructionStatus.CANCELED)
            repository.markLatestAttemptCanceled(correction.paymentInstructionId)
            repository.insertStatusHistory(
                instructionId = correction.paymentInstructionId,
                status = PaymentInstructionStatus.CANCELED,
                actorId = checkerId,
                reason = request.reason
            )
            repository.insertOutboxEvent(
                aggregateId = correction.paymentInstructionId,
                eventType = "PaymentInstructionCanceled",
                idempotencyKey = request.idempotencyKey,
                payload = mapOf(
                    "contractVersion" to "2026-06-05",
                    "paymentInstructionId" to correction.paymentInstructionId,
                    "cancellationRequestId" to cancellationRequestId,
                    "syntheticOnly" to true,
                    "directLedgerWrite" to false,
                    "makerCheckerApproved" to true
                )
            )
        }
        repository.decideCancellationRequest(
            requestId = cancellationRequestId,
            status = PaymentCancellationRequestStatus.APPROVED,
            checkerId = checkerId,
            checkerRole = checkerRole,
            checkerReason = request.reason
        )

        val response = cancellationResponse(cancellationRequestId, replayed = false)
        repository.insertIdempotency(
            idempotencyKey = request.idempotencyKey,
            commandType = "APPROVE_PAYMENT_CANCELLATION_APPROVAL",
            requestHash = requestHash,
            aggregateId = cancellationRequestId,
            response = response
        )
        return response
    }

    @Transactional
    fun rejectCancellationRequest(
        cancellationRequestId: String,
        request: ReviewPaymentCancellationRequest,
        principal: PaymentPrincipal?
    ): PaymentCancellationRequestResponse {
        val checkerId = actorIdFromRequest(request.requestedBy, principal)
        val checkerRole = correctionCheckerRole(principal)
        validateIdempotentCommand(
            idempotencyKey = request.idempotencyKey,
            requestedBy = checkerId,
            reason = request.reason
        )
        val requestHash = requestHash("REJECT_CANCEL_APPROVAL", cancellationRequestId, checkerId, request.reason)
        replayCancellationIfPresent(
            request.idempotencyKey,
            "REJECT_PAYMENT_CANCELLATION_APPROVAL",
            requestHash
        )?.let { return it }

        val correction = repository.findCancellationRequestForUpdate(cancellationRequestId)
            ?: throw cancellationRequestNotFound(cancellationRequestId)
        if (correction.status != PaymentCancellationRequestStatus.PENDING) {
            throw cancellationRequestTerminal(cancellationRequestId, correction.status)
        }
        if (correction.makerId == checkerId) {
            throw paymentError(
                code = "PAYMENT_MAKER_CHECKER_SEPARATION_REQUIRED",
                status = HttpStatus.FORBIDDEN,
                policy = "PAYMENT_MAKER_CHECKER_REQUIRED",
                message = "payment cancellation maker cannot reject their own request",
                cause = "High-risk staff payment corrections require independent checker review.",
                fix = "Use a different authorized checker account for the rejection decision.",
                details = mapOf("cancellationRequestId" to cancellationRequestId, "makerId" to correction.makerId)
            )
        }
        repository.decideCancellationRequest(
            requestId = cancellationRequestId,
            status = PaymentCancellationRequestStatus.REJECTED,
            checkerId = checkerId,
            checkerRole = checkerRole,
            checkerReason = request.reason
        )

        val response = cancellationResponse(cancellationRequestId, replayed = false)
        repository.insertIdempotency(
            idempotencyKey = request.idempotencyKey,
            commandType = "REJECT_PAYMENT_CANCELLATION_APPROVAL",
            requestHash = requestHash,
            aggregateId = cancellationRequestId,
            response = response
        )
        return response
    }

    fun instruction(instructionId: String): PaymentInstructionDto {
        val record = repository.findInstruction(instructionId) ?: throw notFound(instructionId)
        return record.toDto(repository.latestOutboxEventId(instructionId))
    }

    @Transactional
    fun instructionRead(
        instructionId: String,
        reason: String?,
        principal: PaymentPrincipal?
    ): PaymentInstructionResponse {
        val record = repository.findInstruction(instructionId) ?: throw notFound(instructionId)
        val auditEventId = staffPaymentReadRole(principal)?.let { actorRole ->
            val trimmedReason = reason?.trim()?.takeIf { it.isNotBlank() } ?: throw paymentError(
                code = "PAYMENT_LOOKUP_REASON_REQUIRED",
                status = HttpStatus.BAD_REQUEST,
                policy = "PAYMENT_STAFF_REASON_REQUIRED",
                message = "payment instruction lookup reason is required",
                cause = "Staff, ops, audit, and compliance payment lookups must include a business reason before access is audited.",
                fix = "Pass a non-blank reason query parameter from PAY-101 or the equivalent staff payment inquiry screen.",
                details = mapOf("paymentInstructionId" to instructionId, "screenId" to "PAY-101")
            )
            repository.insertPaymentInstructionViewAudit(
                instruction = record,
                actorId = principal?.subject ?: "unknown-payment-reader",
                actorRole = actorRole,
                actorRoles = principal?.roles ?: emptySet(),
                reason = trimmedReason
            )
        }
        return PaymentInstructionResponse(
            item = record.toDto(repository.latestOutboxEventId(instructionId)),
            replayed = false,
            auditEventId = auditEventId
        )
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

    private fun replayCancellationIfPresent(
        idempotencyKey: String,
        commandType: String,
        requestHash: String
    ): PaymentCancellationRequestResponse? {
        val existing = repository.findIdempotency(idempotencyKey) ?: return null
        if (existing.commandType != commandType || existing.requestHash != requestHash) {
            throw paymentError(
                code = "PAYMENT_IDEMPOTENCY_CONFLICT",
                status = HttpStatus.CONFLICT,
                invariant = "idempotent payment correction command creates at most one business result",
                message = "idempotency key was reused with a different payment correction payload",
                cause = "The same idempotency key already exists for a different command type or request hash.",
                fix = "Replay the original payload or use a new idempotency key for a different payment correction command.",
                details = mapOf("idempotencyKey" to idempotencyKey)
            )
        }
        return objectMapper
            .readValue(existing.responseJson, PaymentCancellationRequestResponse::class.java)
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

    private fun staffPaymentReadRole(principal: PaymentPrincipal?): String? {
        if (principal == null) {
            return null
        }
        return staffPaymentReadRoles.firstOrNull(principal.roles::contains)
    }

    private fun actorIdFromRequest(requestedBy: String, principal: PaymentPrincipal?): String {
        requireNonBlank(requestedBy, "requestedBy")
        if (principal != null && principal.subject != requestedBy) {
            throw paymentError(
                code = "PAYMENT_ACTOR_BINDING_MISMATCH",
                status = HttpStatus.FORBIDDEN,
                policy = "PAYMENT_ACTOR_BINDING",
                message = "payment correction requestedBy must match the authenticated actor",
                cause = "Payment correction maker/checker commands bind the request body actor to the bearer-token subject.",
                fix = "Use the authenticated subject as requestedBy before retrying.",
                details = mapOf("requestedBy" to requestedBy, "authenticatedSubject" to principal.subject)
            )
        }
        return requestedBy
    }

    private fun correctionMakerRole(principal: PaymentPrincipal?): String =
        correctionMakerRoles.firstOrNull { principal?.roles?.contains(it) == true } ?: "PAYMENT_CORRECTION_MAKER"

    private fun correctionCheckerRole(principal: PaymentPrincipal?): String =
        correctionCheckerRoles.firstOrNull { principal?.roles?.contains(it) == true } ?: "PAYMENT_CORRECTION_CHECKER"

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

    private fun PaymentCancellationRequestRecord.toDto(): PaymentCancellationRequestDto =
        PaymentCancellationRequestDto(
            cancellationRequestId = cancellationRequestId,
            paymentInstructionId = paymentInstructionId,
            status = status,
            makerId = makerId,
            makerRole = makerRole,
            makerReason = makerReason,
            checkerId = checkerId,
            checkerRole = checkerRole,
            checkerReason = checkerReason,
            syntheticOnly = syntheticOnly,
            createdAt = createdAt,
            updatedAt = updatedAt,
            decidedAt = decidedAt
        )

    private fun cancellationResponse(requestId: String, replayed: Boolean): PaymentCancellationRequestResponse {
        val correction = repository.findCancellationRequest(requestId) ?: throw cancellationRequestNotFound(requestId)
        val instruction = repository.findInstruction(correction.paymentInstructionId)
        return PaymentCancellationRequestResponse(
            item = correction.toDto(),
            instruction = instruction?.toDto(repository.latestOutboxEventId(correction.paymentInstructionId)),
            replayed = replayed
        )
    }

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

    private fun cancellationRequestNotFound(requestId: String): PaymentDomainException =
        paymentError(
            code = "PAYMENT_CANCELLATION_REQUEST_NOT_FOUND",
            status = HttpStatus.NOT_FOUND,
            message = "payment cancellation request was not found",
            cause = "No durable payment cancellation maker-checker request exists for the supplied id.",
            fix = "Check the PCR-* request id returned by the maker request command.",
            details = mapOf("cancellationRequestId" to requestId)
        )

    private fun cancellationRequestTerminal(
        requestId: String,
        status: PaymentCancellationRequestStatus
    ): PaymentDomainException =
        paymentError(
            code = "PAYMENT_CANCELLATION_REQUEST_TERMINAL",
            status = HttpStatus.CONFLICT,
            policy = "PAYMENT_MAKER_CHECKER_REQUIRED",
            message = "payment cancellation request is already terminal",
            cause = "Payment cancellation approval requests are append-only and can be approved or rejected once.",
            fix = "Create a new correction request if another correction is still required.",
            details = mapOf("cancellationRequestId" to requestId, "status" to status.name)
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

    private fun paymentCancellationRequestId(): String = "PCR-${UUID.randomUUID().toString().uppercase()}"

    private fun requestHash(vararg parts: String): String {
        val payload = parts.joinToString(separator = "|")
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    companion object {
        private val staffPaymentReadRoles = listOf(
            "BRANCH_STAFF",
            "BRANCH_MANAGER",
            "OPS_OPERATOR",
            "OPS_MANAGER",
            "AUDITOR",
            "COMPLIANCE_MANAGER"
        )
        private val correctionMakerRoles = listOf(
            "BRANCH_STAFF",
            "BRANCH_MANAGER",
            "OPS_OPERATOR",
            "OPS_MANAGER",
            "COMPLIANCE_MANAGER"
        )
        private val correctionCheckerRoles = listOf(
            "BRANCH_MANAGER",
            "OPS_MANAGER",
            "COMPLIANCE_MANAGER"
        )
    }
}
