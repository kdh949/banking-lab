
package lab.banking.payment.reconciliation

import java.security.MessageDigest
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import lab.banking.payment.core.CoreLedgerEvidenceClient
import lab.banking.payment.core.CorePaymentLedgerEvidence
import lab.banking.payment.domain.PaymentDomainException
import lab.banking.payment.domain.PaymentInstructionRecord
import lab.banking.payment.domain.PaymentInstructionStatus
import lab.banking.payment.persistence.PaymentRepository
import lab.banking.payment.security.PaymentPrincipal
import lab.banking.payment.settlement.ExternalSettlementLineRecord
import lab.banking.payment.settlement.ExternalSettlementLineStatus
import lab.banking.payment.settlement.PaymentSettlementRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

@Service
class PaymentReconciliationService(
    private val repository: PaymentReconciliationRepository,
    private val settlementRepository: PaymentSettlementRepository,
    private val paymentRepository: PaymentRepository,
    private val coreLedgerEvidenceClient: CoreLedgerEvidenceClient,
    transactionManager: PlatformTransactionManager
) {
    private val transaction = TransactionTemplate(transactionManager).apply {
        isolationLevel = TransactionDefinition.ISOLATION_SERIALIZABLE
    }

    fun createRun(
        request: CreatePaymentReconciliationRunRequest,
        principal: PaymentPrincipal?
    ): PaymentReconciliationRunResponse {
        val actorId = actorIdFromRequest(request.requestedBy, principal)
        validateRequest(request, actorId)
        val requestHash = hashParts(
            "CREATE_PAYMENT_RECONCILIATION_RUN",
            request.externalSettlementImportId,
            request.expectedValueDate.toString(),
            request.allowedValueDateLagDays.toString(),
            request.slaDays.toString(),
            request.ownerId,
            actorId,
            request.reason
        )
        val preparation = inTransaction {
            prepareRun(request, actorId, requestHash)
        }
        if (preparation.replayed) {
            return runResponse(preparation.runId, replayed = true, auditEventId = null)
        }

        val evidence = try {
            coreLedgerEvidenceClient.paymentPostings(preparation.businessDate, request.reason)
                .also { validateEvidence(preparation.businessDate, it) }
        } catch (error: RuntimeException) {
            inTransaction {
                repository.markFailed(
                    runId = preparation.runId,
                    claimToken = preparation.claimToken,
                    message = error.message ?: error.javaClass.simpleName,
                    updatedAt = OffsetDateTime.now()
                )
            }
            throw paymentError(
                code = "PAYMENT_LEDGER_EVIDENCE_UNAVAILABLE",
                status = HttpStatus.SERVICE_UNAVAILABLE,
                invariant = "three-way reconciliation uses core-banking ledger evidence outside the payment database transaction",
                message = "core-banking ledger evidence is unavailable",
                cause = "The reconciliation run was reserved, but the read-only core ledger evidence call failed before local finalization.",
                fix = "Restore the synthetic core-banking evidence endpoint and replay the same idempotency key.",
                details = mapOf(
                    "paymentReconciliationRunId" to preparation.runId,
                    "businessDate" to preparation.businessDate.toString()
                )
            )
        }

        return inTransaction {
            finalizeRun(preparation, evidence)
        }
    }

    fun run(
        runId: String,
        reason: String?,
        principal: PaymentPrincipal?
    ): PaymentReconciliationRunResponse =
        inTransaction {
            val response = runResponse(runId, replayed = false, auditEventId = null)
            val auditId = auditRead(
                eventType = "PAYMENT_RECONCILIATION_RUN_VIEW",
                runId = runId,
                reason = reason,
                principal = principal,
                resultCount = response.results.size
            )
            response.copy(auditEventId = auditId)
        }

    fun exceptions(
        ownerId: String?,
        status: PaymentReconciliationResultStatus?,
        overdueOnly: Boolean,
        reason: String?,
        principal: PaymentPrincipal?
    ): PaymentReconciliationExceptionListResponse =
        inTransaction {
            val records = repository.exceptions(ownerId, status, overdueOnly)
            val auditId = auditRead(
                eventType = "PAYMENT_RECONCILIATION_EXCEPTION_LIST_VIEW",
                runId = null,
                reason = reason,
                principal = principal,
                resultCount = records.size
            )
            PaymentReconciliationExceptionListResponse(
                items = records.map(::resultDto),
                auditEventId = auditId,
                syntheticOnly = true
            )
        }

    private fun prepareRun(
        request: CreatePaymentReconciliationRunRequest,
        actorId: String,
        requestHash: String
    ): PreparedRun {
        repository.lockKeys(
            listOf(
                "payment-reconciliation-idempotency:${request.idempotencyKey}",
                "payment-reconciliation-import:${request.externalSettlementImportId}"
            )
        )
        val now = OffsetDateTime.now()
        repository.findRunByIdempotencyKey(request.idempotencyKey)?.let { existing ->
            requireMatchingRequest(existing, requestHash)
            if (existing.status in terminalStatuses) {
                return PreparedRun(
                    runId = existing.paymentReconciliationRunId,
                    businessDate = existing.businessDate,
                    claimToken = "",
                    replayed = true
                )
            }
            if (
                existing.status == PaymentReconciliationRunStatus.EVIDENCE_PENDING &&
                existing.claimExpiresAt?.isAfter(now) == true
            ) {
                throw paymentError(
                    code = "PAYMENT_RECONCILIATION_IN_PROGRESS",
                    status = HttpStatus.CONFLICT,
                    invariant = "one worker owns a reconciliation evidence claim at a time",
                    message = "payment reconciliation run is already collecting ledger evidence",
                    cause = "A concurrent replay reached the same idempotent run before its short evidence claim expired.",
                    fix = "Retry the same idempotency key after the current claim completes or expires.",
                    details = mapOf(
                        "paymentReconciliationRunId" to existing.paymentReconciliationRunId,
                        "claimExpiresAt" to existing.claimExpiresAt.toString()
                    )
                )
            }
            val claimToken = claimToken()
            repository.reclaimRun(
                runId = existing.paymentReconciliationRunId,
                claimToken = claimToken,
                claimExpiresAt = now.plusMinutes(CLAIM_MINUTES),
                updatedAt = now
            )
            return PreparedRun(
                runId = existing.paymentReconciliationRunId,
                businessDate = existing.businessDate,
                claimToken = claimToken,
                replayed = false
            )
        }

        repository.findRunByImportId(request.externalSettlementImportId)?.let { existing ->
            throw paymentError(
                code = "PAYMENT_RECONCILIATION_IMPORT_ALREADY_USED",
                status = HttpStatus.CONFLICT,
                invariant = "one authoritative three-way reconciliation run is created per external import",
                message = "external settlement import already has a reconciliation run",
                cause = "A different idempotency key attempted to reconcile the same immutable external file twice.",
                fix = "Replay the original idempotency key or inspect the existing PRR-* run.",
                details = mapOf(
                    "externalSettlementImportId" to request.externalSettlementImportId,
                    "existingPaymentReconciliationRunId" to existing.paymentReconciliationRunId
                )
            )
        }

        val imported = settlementRepository.findImport(request.externalSettlementImportId)
            ?: throw paymentError(
                code = "PAYMENT_EXTERNAL_SETTLEMENT_IMPORT_NOT_FOUND",
                status = HttpStatus.NOT_FOUND,
                message = "external settlement import was not found",
                cause = "Three-way reconciliation requires a persisted independent ESI-* source file.",
                fix = "Import the external CSV first and retry with the returned identifier.",
                details = mapOf("externalSettlementImportId" to request.externalSettlementImportId)
            )
        if (request.expectedValueDate.isBefore(imported.businessDate)) {
            throw paymentError(
                code = "PAYMENT_RECONCILIATION_VALUE_DATE_INVALID",
                status = HttpStatus.BAD_REQUEST,
                invariant = "expected value date cannot precede the external file business date",
                message = "expectedValueDate cannot be before the external settlement business date",
                cause = "The requested reconciliation date model is earlier than its immutable source file.",
                fix = "Use an expected value date on or after the imported business date.",
                details = mapOf(
                    "businessDate" to imported.businessDate.toString(),
                    "expectedValueDate" to request.expectedValueDate.toString()
                )
            )
        }

        val claimToken = claimToken()
        val runId = "PRR-${UUID.randomUUID().toString().uppercase()}"
        repository.insertRun(
            PaymentReconciliationRunRecord(
                paymentReconciliationRunId = runId,
                externalSettlementImportId = imported.externalSettlementImportId,
                businessDate = imported.businessDate,
                expectedValueDate = request.expectedValueDate,
                allowedValueDateLagDays = request.allowedValueDateLagDays,
                slaDays = request.slaDays,
                ownerId = request.ownerId,
                status = PaymentReconciliationRunStatus.EVIDENCE_PENDING,
                idempotencyKey = request.idempotencyKey,
                requestHash = requestHash,
                requestedBy = actorId,
                reason = request.reason,
                externalLineCount = 0,
                ledgerEvidenceCount = 0,
                matchedCount = 0,
                exceptionCount = 0,
                claimToken = claimToken,
                claimExpiresAt = now.plusMinutes(CLAIM_MINUTES),
                attemptCount = 1,
                failureMessage = null,
                syntheticOnly = true,
                createdAt = now,
                updatedAt = now,
                completedAt = null
            )
        )
        return PreparedRun(
            runId = runId,
            businessDate = imported.businessDate,
            claimToken = claimToken,
            replayed = false
        )
    }

    private fun finalizeRun(
        preparation: PreparedRun,
        evidence: List<CorePaymentLedgerEvidence>
    ): PaymentReconciliationRunResponse {
        val run = repository.findRunForUpdate(preparation.runId)
            ?: throw runNotFound(preparation.runId)
        if (run.status in terminalStatuses) {
            return runResponse(run.paymentReconciliationRunId, replayed = true, auditEventId = null)
        }
        if (run.claimToken != preparation.claimToken) {
            throw paymentError(
                code = "PAYMENT_RECONCILIATION_CLAIM_LOST",
                status = HttpStatus.CONFLICT,
                invariant = "only the active evidence claimant can finalize a reconciliation run",
                message = "payment reconciliation evidence claim was lost",
                cause = "Another retry reclaimed the run after the prior claim expired.",
                fix = "Replay the same idempotency key so the active claimant can complete the run.",
                details = mapOf("paymentReconciliationRunId" to run.paymentReconciliationRunId)
            )
        }

        val externalLines = settlementRepository.linesForImport(run.externalSettlementImportId)
        val externalGroups = externalLines.groupBy { it.paymentInstructionId }
        val ledgerGroups = evidence.groupBy { it.paymentInstructionId }
        val now = OffsetDateTime.now()
        val results = mutableListOf<PaymentReconciliationResultRecord>()

        externalLines.forEach { line ->
            val payment = paymentRepository.findInstruction(line.paymentInstructionId)
            val ledgerGroup = ledgerGroups[line.paymentInstructionId].orEmpty()
            val classification = classifyExternalLine(
                line = line,
                duplicateCount = externalGroups[line.paymentInstructionId].orEmpty().size,
                payment = payment,
                ledgerGroup = ledgerGroup,
                expectedValueDate = run.expectedValueDate,
                allowedValueDateLagDays = run.allowedValueDateLagDays
            )
            results += resultRecord(
                run = run,
                line = line,
                payment = payment,
                ledger = ledgerGroup.firstOrNull(),
                mismatchType = classification.first,
                detectedReason = classification.second,
                detectedAt = now
            )
        }

        ledgerGroups
            .filterKeys { it !in externalGroups }
            .toSortedMap()
            .forEach { (paymentInstructionId, ledgerGroup) ->
                val ledger = ledgerGroup.first()
                val payment = paymentRepository.findInstruction(paymentInstructionId)
                val mismatch = when {
                    payment == null -> PaymentReconciliationMismatchType.MISSING_PAYMENT to
                        "Core ledger evidence has no corresponding payment instruction in Payment Service"
                    ledgerGroup.size > 1 -> PaymentReconciliationMismatchType.STATUS_MISMATCH to
                        "Core-banking returned multiple ledger transactions without an external clearing row"
                    else -> PaymentReconciliationMismatchType.MISSING_EXTERNAL to
                        "Ledger-posted payment is absent from the independent external settlement file"
                }
                results += resultRecord(
                    run = run,
                    line = null,
                    payment = payment,
                    ledger = ledger,
                    mismatchType = mismatch.first,
                    detectedReason = mismatch.second,
                    detectedAt = now
                )
            }

        repository.deleteResults(run.paymentReconciliationRunId)
        results.forEach(repository::insertResult)
        val matchedCount = results.count { it.mismatchType == PaymentReconciliationMismatchType.MATCHED }
        val exceptionCount = results.size - matchedCount
        val status = if (exceptionCount == 0) {
            PaymentReconciliationRunStatus.MATCHED
        } else {
            PaymentReconciliationRunStatus.EXCEPTIONS_OPEN
        }
        repository.finalizeRun(
            runId = run.paymentReconciliationRunId,
            claimToken = preparation.claimToken,
            status = status,
            externalLineCount = externalLines.size,
            ledgerEvidenceCount = evidence.size,
            matchedCount = matchedCount,
            exceptionCount = exceptionCount,
            completedAt = now
        )
        return runResponse(run.paymentReconciliationRunId, replayed = false, auditEventId = null)
    }

    private fun classifyExternalLine(
        line: ExternalSettlementLineRecord,
        duplicateCount: Int,
        payment: PaymentInstructionRecord?,
        ledgerGroup: List<CorePaymentLedgerEvidence>,
        expectedValueDate: LocalDate,
        allowedValueDateLagDays: Int
    ): Pair<PaymentReconciliationMismatchType, String> {
        if (duplicateCount > 1) {
            return PaymentReconciliationMismatchType.DUPLICATE_EXTERNAL to
                "Independent external file contains multiple rows for the same payment instruction"
        }
        if (payment == null) {
            return PaymentReconciliationMismatchType.MISSING_PAYMENT to
                "External clearing row has no corresponding payment instruction"
        }
        if (ledgerGroup.isEmpty()) {
            return PaymentReconciliationMismatchType.MISSING_LEDGER to
                "Payment instruction and external row exist, but core-banking returned no bill-payment ledger evidence"
        }
        if (ledgerGroup.size > 1) {
            return PaymentReconciliationMismatchType.STATUS_MISMATCH to
                "Core-banking returned multiple bill-payment ledger transactions for one payment instruction"
        }
        val ledger = ledgerGroup.single()
        if (
            payment.status != PaymentInstructionStatus.LEDGER_POSTED ||
            payment.ledgerTransactionId.isNullOrBlank() ||
            payment.ledgerTransactionId != ledger.ledgerTransactionId ||
            ledger.transactionType != "BILL_PAYMENT" ||
            ledger.status != "POSTED" ||
            !ledger.balanced ||
            ledger.postingCount < 2
        ) {
            return PaymentReconciliationMismatchType.STATUS_MISMATCH to
                "Payment instruction state and core ledger posting status or balance evidence do not agree"
        }
        if (line.status != ExternalSettlementLineStatus.ACCEPTED) {
            return PaymentReconciliationMismatchType.STATUS_MISMATCH to
                "External clearing status is ${line.status} instead of ACCEPTED"
        }
        if (payment.currency != ledger.currency || payment.currency != line.currency) {
            return PaymentReconciliationMismatchType.STATUS_MISMATCH to
                "Payment, ledger, and external clearing currencies do not agree"
        }
        if (payment.amountMinor != ledger.amountMinor || payment.amountMinor != line.amountMinor) {
            return PaymentReconciliationMismatchType.AMOUNT_MISMATCH to
                "Payment, ledger, and external clearing minor-unit amounts do not agree"
        }
        val latestAllowedValueDate = expectedValueDate.plusDays(allowedValueDateLagDays.toLong())
        if (line.valueDate.isAfter(latestAllowedValueDate)) {
            return PaymentReconciliationMismatchType.LATE_SETTLEMENT to
                "External value date exceeds the configured allowed settlement lag"
        }
        if (line.valueDate != expectedValueDate) {
            return PaymentReconciliationMismatchType.VALUE_DATE_MISMATCH to
                "External value date differs from the expected value date"
        }
        return PaymentReconciliationMismatchType.MATCHED to
            "Payment instruction, balanced core ledger evidence, and independent external clearing row match"
    }

    private fun resultRecord(
        run: PaymentReconciliationRunRecord,
        line: ExternalSettlementLineRecord?,
        payment: PaymentInstructionRecord?,
        ledger: CorePaymentLedgerEvidence?,
        mismatchType: PaymentReconciliationMismatchType,
        detectedReason: String,
        detectedAt: OffsetDateTime
    ): PaymentReconciliationResultRecord {
        val matched = mismatchType == PaymentReconciliationMismatchType.MATCHED
        return PaymentReconciliationResultRecord(
            paymentReconciliationResultId = "PRC-${UUID.randomUUID().toString().uppercase()}",
            paymentReconciliationRunId = run.paymentReconciliationRunId,
            externalSettlementLineId = line?.externalSettlementLineId,
            paymentInstructionId = line?.paymentInstructionId ?: ledger?.paymentInstructionId
                ?: payment?.paymentInstructionId
                ?: error("reconciliation result requires a payment instruction reference"),
            ledgerTransactionId = ledger?.ledgerTransactionId ?: payment?.ledgerTransactionId,
            mismatchType = mismatchType,
            resultStatus = if (matched) PaymentReconciliationResultStatus.MATCHED else PaymentReconciliationResultStatus.OPEN,
            internalAmountMinor = payment?.amountMinor,
            ledgerAmountMinor = ledger?.amountMinor,
            externalAmountMinor = line?.amountMinor,
            currency = line?.currency ?: payment?.currency ?: ledger?.currency,
            externalStatus = line?.status?.name,
            businessDate = line?.businessDate ?: ledger?.businessDate ?: run.businessDate,
            expectedValueDate = run.expectedValueDate,
            actualValueDate = line?.valueDate,
            ownerId = run.ownerId,
            detectedReason = detectedReason,
            detectedAt = detectedAt,
            dueAt = if (matched) null else detectedAt.plusDays(run.slaDays.toLong()),
            resolution = null,
            approvalId = null,
            resolvedAt = null,
            syntheticOnly = true
        )
    }

    private fun runResponse(
        runId: String,
        replayed: Boolean,
        auditEventId: String?
    ): PaymentReconciliationRunResponse {
        val record = repository.findRun(runId) ?: throw runNotFound(runId)
        return PaymentReconciliationRunResponse(
            item = record.toDto(),
            results = repository.resultsForRun(runId).map(::resultDto),
            replayed = replayed,
            auditEventId = auditEventId,
            syntheticOnly = true
        )
    }

    private fun PaymentReconciliationRunRecord.toDto(): PaymentReconciliationRunDto =
        PaymentReconciliationRunDto(
            paymentReconciliationRunId = paymentReconciliationRunId,
            externalSettlementImportId = externalSettlementImportId,
            businessDate = businessDate,
            expectedValueDate = expectedValueDate,
            allowedValueDateLagDays = allowedValueDateLagDays,
            slaDays = slaDays,
            ownerId = ownerId,
            status = status,
            externalLineCount = externalLineCount,
            ledgerEvidenceCount = ledgerEvidenceCount,
            matchedCount = matchedCount,
            exceptionCount = exceptionCount,
            attemptCount = attemptCount,
            failureMessage = failureMessage,
            syntheticOnly = syntheticOnly,
            createdAt = createdAt,
            updatedAt = updatedAt,
            completedAt = completedAt
        )

    private fun resultDto(record: PaymentReconciliationResultRecord): PaymentReconciliationResultDto {
        val today = LocalDate.now(ZoneOffset.UTC)
        val agingDays = if (record.mismatchType == PaymentReconciliationMismatchType.MATCHED) {
            0L
        } else {
            ChronoUnit.DAYS.between(record.detectedAt.toLocalDate(), today).coerceAtLeast(0L)
        }
        val overdue = record.resultStatus != PaymentReconciliationResultStatus.RESOLVED &&
            record.dueAt?.isBefore(OffsetDateTime.now()) == true
        return PaymentReconciliationResultDto(
            paymentReconciliationResultId = record.paymentReconciliationResultId,
            paymentReconciliationRunId = record.paymentReconciliationRunId,
            externalSettlementLineId = record.externalSettlementLineId,
            paymentInstructionId = record.paymentInstructionId,
            ledgerTransactionId = record.ledgerTransactionId,
            mismatchType = record.mismatchType,
            resultStatus = record.resultStatus,
            internalAmountMinor = record.internalAmountMinor,
            ledgerAmountMinor = record.ledgerAmountMinor,
            externalAmountMinor = record.externalAmountMinor,
            currency = record.currency,
            externalStatus = record.externalStatus,
            businessDate = record.businessDate,
            expectedValueDate = record.expectedValueDate,
            actualValueDate = record.actualValueDate,
            ownerId = record.ownerId,
            detectedReason = record.detectedReason,
            detectedAt = record.detectedAt,
            dueAt = record.dueAt,
            agingDays = agingDays,
            overdue = overdue,
            resolution = record.resolution,
            approvalId = record.approvalId,
            resolvedAt = record.resolvedAt,
            syntheticOnly = record.syntheticOnly
        )
    }

    private fun auditRead(
        eventType: String,
        runId: String?,
        reason: String?,
        principal: PaymentPrincipal?,
        resultCount: Int
    ): String {
        val trimmedReason = reason?.trim()?.takeIf { it.isNotBlank() }
            ?: throw paymentError(
                code = "PAYMENT_RECONCILIATION_READ_REASON_REQUIRED",
                status = HttpStatus.BAD_REQUEST,
                policy = "PAYMENT_RECONCILIATION_REASON_REQUIRED",
                message = "reconciliation read reason is required",
                cause = "Operations, audit, and compliance reconciliation reads must leave durable reason evidence.",
                fix = "Supply a non-blank reason query parameter.",
                details = mapOf("paymentReconciliationRunId" to runId)
            )
        return repository.insertAccessAudit(
            eventType = eventType,
            runId = runId,
            actorId = principal?.subject ?: "SYSTEM",
            actorRoles = principal?.roles ?: setOf("SYSTEM"),
            reason = trimmedReason,
            resultCount = resultCount,
            createdAt = OffsetDateTime.now()
        )
    }

    private fun validateRequest(request: CreatePaymentReconciliationRunRequest, actorId: String) {
        requireText(request.externalSettlementImportId, "externalSettlementImportId")
        requireText(request.ownerId, "ownerId")
        requireText(request.idempotencyKey, "idempotencyKey")
        requireText(actorId, "requestedBy")
        requireText(request.reason, "reason")
        if (request.allowedValueDateLagDays !in 0..30) {
            throw validationError("allowedValueDateLagDays", "must be between 0 and 30")
        }
        if (request.slaDays !in 1..30) {
            throw validationError("slaDays", "must be between 1 and 30")
        }
    }

    private fun validateEvidence(businessDate: LocalDate, evidence: List<CorePaymentLedgerEvidence>) {
        if (evidence.size > MAX_LEDGER_EVIDENCE) {
            throw IllegalStateException("core-banking ledger evidence exceeds the bounded reconciliation limit")
        }
        evidence.forEach { item ->
            if (!item.syntheticOnly || item.businessDate != businessDate) {
                throw IllegalStateException("core-banking ledger evidence does not match the synthetic business-date boundary")
            }
        }
    }

    private fun actorIdFromRequest(requestedBy: String, principal: PaymentPrincipal?): String {
        requireText(requestedBy, "requestedBy")
        if (principal != null && principal.subject != requestedBy) {
            throw paymentError(
                code = "PAYMENT_ACTOR_BINDING_MISMATCH",
                status = HttpStatus.FORBIDDEN,
                policy = "PAYMENT_ACTOR_BINDING",
                message = "reconciliation requestedBy must match the authenticated actor",
                cause = "The request body attempted to attribute an operations command to another subject.",
                fix = "Use the bearer-token subject as requestedBy.",
                details = mapOf(
                    "requestedBy" to requestedBy,
                    "authenticatedSubject" to principal.subject
                )
            )
        }
        return principal?.subject ?: requestedBy
    }

    private fun requireMatchingRequest(existing: PaymentReconciliationRunRecord, requestHash: String) {
        if (existing.requestHash != requestHash) {
            throw paymentError(
                code = "PAYMENT_RECONCILIATION_IDEMPOTENCY_CONFLICT",
                status = HttpStatus.CONFLICT,
                invariant = "one reconciliation idempotency key represents one immutable request",
                message = "reconciliation idempotency key was reused with a different payload",
                cause = "The stored reconciliation request hash differs from the replayed command.",
                fix = "Replay the original payload or use a new idempotency key.",
                details = mapOf("idempotencyKey" to existing.idempotencyKey)
            )
        }
    }

    private fun requireText(value: String, field: String) {
        if (value.isBlank()) {
            throw validationError(field, "is required")
        }
    }

    private fun validationError(field: String, message: String): PaymentDomainException =
        paymentError(
            code = "PAYMENT_RECONCILIATION_REQUEST_INVALID",
            status = HttpStatus.BAD_REQUEST,
            message = "$field $message",
            cause = "The reconciliation command failed bounded input validation.",
            fix = "Correct $field according to the payment-service OpenAPI contract.",
            details = mapOf("field" to field)
        )

    private fun runNotFound(runId: String): PaymentDomainException =
        paymentError(
            code = "PAYMENT_RECONCILIATION_RUN_NOT_FOUND",
            status = HttpStatus.NOT_FOUND,
            message = "payment reconciliation run was not found",
            cause = "No PRR-* reconciliation run exists for the supplied identifier.",
            fix = "Use the identifier returned by the reconciliation run command.",
            details = mapOf("paymentReconciliationRunId" to runId)
        )

    private fun hashParts(vararg values: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(values.joinToString("\u001f").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun claimToken(): String = "PRC-${UUID.randomUUID().toString().uppercase()}"

    private fun <T> inTransaction(operation: () -> T): T =
        transaction.execute { operation() }
            ?: error("payment reconciliation transaction returned no result")

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

    private data class PreparedRun(
        val runId: String,
        val businessDate: LocalDate,
        val claimToken: String,
        val replayed: Boolean
    )

    companion object {
        private const val CLAIM_MINUTES = 2L
        private const val MAX_LEDGER_EVIDENCE = 10000
        private val terminalStatuses = setOf(
            PaymentReconciliationRunStatus.MATCHED,
            PaymentReconciliationRunStatus.EXCEPTIONS_OPEN
        )
    }
}
