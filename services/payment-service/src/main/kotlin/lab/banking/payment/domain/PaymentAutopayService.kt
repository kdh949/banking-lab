package lab.banking.payment.domain

import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID
import lab.banking.payment.persistence.PaymentRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentAutopayService(
    private val repository: PaymentRepository,
    private val paymentInstructionService: PaymentInstructionService,
    private val objectMapper: ObjectMapper
) {
    @Transactional
    fun createAgreement(request: CreateAutopayAgreementRequest): PaymentAutopayAgreementResponse {
        validateCreate(request)
        val requestHash = requestHash(
            "AUTOPAY_CREATE",
            request.customerId,
            request.debitAccountId,
            request.billerId,
            request.amountMinor.toString(),
            request.currency,
            request.frequency.name,
            request.nextRunOn.toString(),
            request.requestedBy,
            request.requestedChannel
        )
        replayAgreementIfPresent(request.idempotencyKey, "CREATE_AUTOPAY_AGREEMENT", requestHash)
            ?.let { return it }
        val biller = syntheticBiller(request.billerId)
        val agreementId = autopayAgreementId()
        repository.insertAutopayAgreement(
            agreementId = agreementId,
            customerId = request.customerId,
            debitAccountId = request.debitAccountId,
            biller = biller,
            amountMinor = request.amountMinor,
            currency = request.currency,
            frequency = request.frequency,
            nextRunOn = request.nextRunOn,
            createdBy = request.requestedBy,
            reason = request.reason
        )
        repository.insertAutopayStatusHistory(
            agreementId = agreementId,
            status = PaymentAutopayStatus.ACTIVE,
            actorId = request.requestedBy,
            reason = request.reason
        )
        val response = PaymentAutopayAgreementResponse(item = agreement(agreementId), replayed = false)
        repository.insertIdempotency(
            idempotencyKey = request.idempotencyKey,
            commandType = "CREATE_AUTOPAY_AGREEMENT",
            requestHash = requestHash,
            aggregateId = agreementId,
            response = response
        )
        return response
    }

    fun agreement(agreementId: String): PaymentAutopayAgreementDto {
        val record = repository.findAutopayAgreement(agreementId) ?: throw notFound(agreementId)
        return record.toDto()
    }

    @Transactional
    fun pauseAgreement(agreementId: String, request: PauseAutopayAgreementRequest): PaymentAutopayAgreementResponse {
        validateOperatorCommand(request.idempotencyKey, request.requestedBy, request.reason)
        val requestHash = requestHash("AUTOPAY_PAUSE", agreementId, request.requestedBy, request.reason)
        replayAgreementIfPresent(request.idempotencyKey, "PAUSE_AUTOPAY_AGREEMENT", requestHash)
            ?.let { return it }
        val current = repository.findAutopayAgreementForUpdate(agreementId) ?: throw notFound(agreementId)
        if (current.status == PaymentAutopayStatus.CANCELED) {
            throw invalidAutopayState(agreementId, current.status, "canceled autopay agreement cannot be paused")
        }
        if (current.status != PaymentAutopayStatus.PAUSED) {
            repository.updateAutopayStatus(agreementId, PaymentAutopayStatus.PAUSED)
            repository.insertAutopayStatusHistory(agreementId, PaymentAutopayStatus.PAUSED, request.requestedBy, request.reason)
        }
        return storeAgreementResponse(request.idempotencyKey, "PAUSE_AUTOPAY_AGREEMENT", requestHash, agreementId)
    }

    @Transactional
    fun resumeAgreement(agreementId: String, request: ResumeAutopayAgreementRequest): PaymentAutopayAgreementResponse {
        validateOperatorCommand(request.idempotencyKey, request.requestedBy, request.reason)
        val requestHash = requestHash("AUTOPAY_RESUME", agreementId, request.nextRunOn?.toString() ?: "", request.requestedBy, request.reason)
        replayAgreementIfPresent(request.idempotencyKey, "RESUME_AUTOPAY_AGREEMENT", requestHash)
            ?.let { return it }
        val current = repository.findAutopayAgreementForUpdate(agreementId) ?: throw notFound(agreementId)
        val nextRunOn = request.nextRunOn ?: current.nextRunOn
        if (current.status == PaymentAutopayStatus.CANCELED) {
            throw invalidAutopayState(agreementId, current.status, "canceled autopay agreement cannot be resumed")
        }
        if (current.status != PaymentAutopayStatus.ACTIVE || current.nextRunOn != nextRunOn) {
            repository.updateAutopayStatus(agreementId, PaymentAutopayStatus.ACTIVE, nextRunOn)
            repository.insertAutopayStatusHistory(agreementId, PaymentAutopayStatus.ACTIVE, request.requestedBy, request.reason)
        }
        return storeAgreementResponse(request.idempotencyKey, "RESUME_AUTOPAY_AGREEMENT", requestHash, agreementId)
    }

    @Transactional
    fun cancelAgreement(agreementId: String, request: CancelAutopayAgreementRequest): PaymentAutopayAgreementResponse {
        validateOperatorCommand(request.idempotencyKey, request.requestedBy, request.reason)
        val requestHash = requestHash("AUTOPAY_CANCEL", agreementId, request.requestedBy, request.reason)
        replayAgreementIfPresent(request.idempotencyKey, "CANCEL_AUTOPAY_AGREEMENT", requestHash)
            ?.let { return it }
        val current = repository.findAutopayAgreementForUpdate(agreementId) ?: throw notFound(agreementId)
        if (current.status != PaymentAutopayStatus.CANCELED) {
            repository.updateAutopayStatus(agreementId, PaymentAutopayStatus.CANCELED)
            repository.insertAutopayStatusHistory(agreementId, PaymentAutopayStatus.CANCELED, request.requestedBy, request.reason)
        }
        return storeAgreementResponse(request.idempotencyKey, "CANCEL_AUTOPAY_AGREEMENT", requestHash, agreementId)
    }

    @Transactional
    fun executeDue(request: ExecuteDueAutopayRequest): ExecuteDueAutopayResponse {
        validateExecuteDue(request)
        val requestHash = requestHash(
            "AUTOPAY_EXECUTE_DUE",
            request.businessDate.toString(),
            request.requestedBy,
            request.limit.toString()
        )
        replayExecuteDueIfPresent(request.idempotencyKey, "EXECUTE_DUE_AUTOPAY", requestHash)
            ?.let { return it }
        val executionIds = mutableListOf<String>()
        val dueAgreements = repository.findDueAutopayAgreementsForUpdate(request.businessDate, request.limit)
        dueAgreements.forEach { agreement ->
            val scheduledRunOn = agreement.nextRunOn
            val instruction = paymentInstructionService.createInstruction(
                CreatePaymentInstructionRequest(
                    customerId = agreement.customerId,
                    debitAccountId = agreement.debitAccountId,
                    billerId = agreement.billerId,
                    amountMinor = agreement.amountMinor,
                    currency = agreement.currency,
                    idempotencyKey = autopayInstructionIdempotencyKey(agreement.autopayAgreementId, scheduledRunOn),
                    requestedBy = request.requestedBy,
                    requestedChannel = "AUTOPAY",
                    reason = request.reason
                )
            )
            val executionId = autopayExecutionId()
            repository.insertAutopayExecution(
                executionId = executionId,
                agreementId = agreement.autopayAgreementId,
                scheduledRunOn = scheduledRunOn,
                paymentInstructionId = instruction.item.paymentInstructionId,
                idempotencyKey = autopayExecutionIdempotencyKey(agreement.autopayAgreementId, scheduledRunOn),
                requestedBy = request.requestedBy,
                reason = request.reason
            )
            repository.updateAutopayAfterExecution(
                agreementId = agreement.autopayAgreementId,
                lastRunOn = scheduledRunOn,
                nextRunOn = nextRunOn(scheduledRunOn, agreement.frequency)
            )
            repository.insertOutboxEvent(
                aggregateType = "payment_autopay_execution",
                aggregateId = executionId,
                eventType = "PaymentAutopayExecutionCreated",
                idempotencyKey = autopayExecutionIdempotencyKey(agreement.autopayAgreementId, scheduledRunOn),
                payload = mapOf(
                    "contractVersion" to "2026-06-05",
                    "autopayAgreementId" to agreement.autopayAgreementId,
                    "autopayExecutionId" to executionId,
                    "paymentInstructionId" to instruction.item.paymentInstructionId,
                    "scheduledRunOn" to scheduledRunOn.toString(),
                    "nextRunOn" to nextRunOn(scheduledRunOn, agreement.frequency).toString(),
                    "frequency" to agreement.frequency.name,
                    "syntheticOnly" to true,
                    "directLedgerWrite" to false,
                    "realPaymentNetworkUsed" to false
                )
            )
            executionIds += executionId
        }
        val response = ExecuteDueAutopayResponse(
            items = repository.findAutopayExecutionsByIds(executionIds).map { it.toDto() },
            executedCount = executionIds.size,
            replayed = false
        )
        repository.insertIdempotency(
            idempotencyKey = request.idempotencyKey,
            commandType = "EXECUTE_DUE_AUTOPAY",
            requestHash = requestHash,
            aggregateId = "AUTOPAY-DUE-${request.businessDate}",
            response = response
        )
        return response
    }

    private fun storeAgreementResponse(
        idempotencyKey: String,
        commandType: String,
        requestHash: String,
        agreementId: String
    ): PaymentAutopayAgreementResponse {
        val response = PaymentAutopayAgreementResponse(item = agreement(agreementId), replayed = false)
        repository.insertIdempotency(
            idempotencyKey = idempotencyKey,
            commandType = commandType,
            requestHash = requestHash,
            aggregateId = agreementId,
            response = response
        )
        return response
    }

    private fun validateCreate(request: CreateAutopayAgreementRequest) {
        validateOperatorCommand(request.idempotencyKey, request.requestedBy, request.reason)
        requireNonBlank(request.customerId, "customerId")
        requireNonBlank(request.debitAccountId, "debitAccountId")
        requireNonBlank(request.billerId, "billerId")
        requireNonBlank(request.currency, "currency")
        if (request.amountMinor <= 0) {
            throw autopayError(
                code = "PAYMENT_AUTOPAY_AMOUNT_INVALID",
                status = HttpStatus.BAD_REQUEST,
                invariant = "autopay amount must be positive",
                message = "autopay amount must be positive",
                cause = "Payment Service rejected a non-positive autopay amount before persistence.",
                fix = "Submit amountMinor greater than zero."
            )
        }
    }

    private fun validateExecuteDue(request: ExecuteDueAutopayRequest) {
        validateOperatorCommand(request.idempotencyKey, request.requestedBy, request.reason)
        if (request.limit <= 0) {
            throw autopayError(
                code = "PAYMENT_AUTOPAY_LIMIT_INVALID",
                status = HttpStatus.BAD_REQUEST,
                invariant = "autopay execution limit must be positive",
                message = "autopay execution limit must be positive",
                cause = "Payment Service cannot execute due autopay agreements with a non-positive batch limit.",
                fix = "Submit limit greater than zero."
            )
        }
    }

    private fun syntheticBiller(billerId: String): PaymentBillerRecord {
        val biller = repository.findBiller(billerId)
            ?: throw autopayError(
                code = "PAYMENT_SYNTHETIC_BILLER_REQUIRED",
                status = HttpStatus.NOT_FOUND,
                policy = "SYNTHETIC_ONLY_PAYMENT_SIMULATOR",
                message = "synthetic biller was not found",
                cause = "Autopay agreements can target only checked-in synthetic biller simulators.",
                fix = "Use one of the seeded SYN-BILLER-* identifiers or add a synthetic-only biller migration."
            )
        if (!biller.syntheticOnly || biller.networkKind != "SYNTHETIC_BILLER_SIMULATOR") {
            throw autopayError(
                code = "PAYMENT_REAL_NETWORK_FORBIDDEN",
                status = HttpStatus.FORBIDDEN,
                policy = "SYNTHETIC_ONLY_PAYMENT_SIMULATOR",
                message = "real payment network configuration is forbidden",
                cause = "Payment Service must not connect autopay to real billers, payment networks, or external financial institution APIs.",
                fix = "Keep biller networkKind set to SYNTHETIC_BILLER_SIMULATOR and syntheticOnly=true."
            )
        }
        return biller
    }

    private fun replayAgreementIfPresent(
        idempotencyKey: String,
        commandType: String,
        requestHash: String
    ): PaymentAutopayAgreementResponse? =
        replayJsonIfPresent(idempotencyKey, commandType, requestHash)
            ?.let { objectMapper.readValue(it, PaymentAutopayAgreementResponse::class.java).copy(replayed = true) }

    private fun replayExecuteDueIfPresent(
        idempotencyKey: String,
        commandType: String,
        requestHash: String
    ): ExecuteDueAutopayResponse? =
        replayJsonIfPresent(idempotencyKey, commandType, requestHash)
            ?.let { objectMapper.readValue(it, ExecuteDueAutopayResponse::class.java).copy(replayed = true) }

    private fun replayJsonIfPresent(
        idempotencyKey: String,
        commandType: String,
        requestHash: String
    ): String? {
        val existing = repository.findIdempotency(idempotencyKey) ?: return null
        if (existing.commandType != commandType || existing.requestHash != requestHash) {
            throw autopayError(
                code = "PAYMENT_IDEMPOTENCY_CONFLICT",
                status = HttpStatus.CONFLICT,
                invariant = "idempotent autopay command creates at most one business result",
                message = "idempotency key was reused with a different autopay command payload",
                cause = "The same idempotency key already exists for a different command type or request hash.",
                fix = "Replay the original payload or use a new idempotency key for a different autopay command.",
                details = mapOf("idempotencyKey" to idempotencyKey)
            )
        }
        return existing.responseJson
    }

    private fun PaymentAutopayAgreementRecord.toDto(): PaymentAutopayAgreementDto =
        PaymentAutopayAgreementDto(
            autopayAgreementId = autopayAgreementId,
            customerId = customerId,
            debitAccountId = debitAccountId,
            billerId = billerId,
            billerName = billerName,
            amountMinor = amountMinor,
            currency = currency,
            frequency = frequency,
            status = status,
            nextRunOn = nextRunOn,
            lastRunOn = lastRunOn,
            lastPaymentInstructionId = repository.latestAutopayPaymentInstructionId(autopayAgreementId),
            syntheticOnly = syntheticOnly,
            createdAt = createdAt,
            updatedAt = updatedAt
        )

    private fun PaymentAutopayExecutionRecord.toDto(): PaymentAutopayExecutionDto =
        PaymentAutopayExecutionDto(
            autopayExecutionId = autopayExecutionId,
            autopayAgreementId = autopayAgreementId,
            scheduledRunOn = scheduledRunOn,
            paymentInstructionId = paymentInstructionId,
            status = status,
            syntheticOnly = syntheticOnly,
            createdAt = createdAt
        )

    private fun nextRunOn(current: LocalDate, frequency: PaymentAutopayFrequency): LocalDate =
        when (frequency) {
            PaymentAutopayFrequency.DAILY -> current.plusDays(1)
            PaymentAutopayFrequency.WEEKLY -> current.plusWeeks(1)
            PaymentAutopayFrequency.MONTHLY -> current.plusMonths(1)
        }

    private fun validateOperatorCommand(idempotencyKey: String, requestedBy: String, reason: String) {
        requireNonBlank(idempotencyKey, "idempotencyKey")
        requireNonBlank(requestedBy, "requestedBy")
        requireNonBlank(reason, "reason")
    }

    private fun requireNonBlank(value: String, field: String) {
        if (value.isBlank()) {
            throw autopayError(
                code = "PAYMENT_REQUIRED_FIELD_MISSING",
                status = HttpStatus.BAD_REQUEST,
                message = "$field is required",
                cause = "Payment Service requires stable autopay command identity, actor, and synthetic target fields.",
                fix = "Populate $field before retrying the autopay command.",
                details = mapOf("field" to field)
            )
        }
    }

    private fun notFound(agreementId: String): PaymentDomainException =
        autopayError(
            code = "PAYMENT_AUTOPAY_NOT_FOUND",
            status = HttpStatus.NOT_FOUND,
            message = "autopay agreement was not found",
            cause = "No durable autopay agreement exists for the supplied id.",
            fix = "Check the autopay agreement id returned by the create agreement command.",
            details = mapOf("autopayAgreementId" to agreementId)
        )

    private fun invalidAutopayState(
        agreementId: String,
        status: PaymentAutopayStatus,
        message: String
    ): PaymentDomainException =
        autopayError(
            code = "PAYMENT_AUTOPAY_STATE_REJECTED",
            status = HttpStatus.CONFLICT,
            invariant = "autopay state transitions are durable and auditable",
            message = message,
            cause = "Payment Service rejected an unsafe autopay state transition.",
            fix = "Use the current autopay status and submit the next allowed command.",
            details = mapOf("autopayAgreementId" to agreementId, "status" to status.name)
        )

    private fun autopayError(
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

    private fun autopayAgreementId(): String = "APAY-${UUID.randomUUID().toString().uppercase()}"

    private fun autopayExecutionId(): String = "APEX-${UUID.randomUUID().toString().uppercase()}"

    private fun autopayInstructionIdempotencyKey(agreementId: String, scheduledRunOn: LocalDate): String =
        "PAY-AUTOPAY-INST-$agreementId-$scheduledRunOn"

    private fun autopayExecutionIdempotencyKey(agreementId: String, scheduledRunOn: LocalDate): String =
        "PAY-AUTOPAY-EXEC-$agreementId-$scheduledRunOn"

    private fun requestHash(vararg parts: String): String {
        val payload = parts.joinToString(separator = "|")
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
