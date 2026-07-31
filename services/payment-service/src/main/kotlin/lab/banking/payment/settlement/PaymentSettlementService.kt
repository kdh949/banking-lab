package lab.banking.payment.settlement

import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.UUID
import lab.banking.payment.domain.PaymentDomainException
import lab.banking.payment.security.PaymentPrincipal
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentSettlementService(
    private val repository: PaymentSettlementRepository,
    private val csvParser: ExternalSettlementCsvParser
) {
    @Transactional
    fun importExternalCsv(
        request: ImportExternalSettlementCsvRequest,
        principal: PaymentPrincipal?
    ): ExternalSettlementImportResponse {
        val actorId = actorIdFromRequest(request.requestedBy, principal)
        validateImportRequest(request, actorId)

        val fileBytes = request.csvContent.toByteArray(StandardCharsets.UTF_8)
        if (fileBytes.size > MAX_FILE_BYTES) {
            throw paymentError(
                code = "PAYMENT_EXTERNAL_SETTLEMENT_FILE_TOO_LARGE",
                status = HttpStatus.PAYLOAD_TOO_LARGE,
                invariant = "external settlement import size is bounded",
                message = "external settlement CSV exceeds the one-megabyte portfolio limit",
                cause = "The import endpoint bounds memory use and reviewable evidence size.",
                fix = "Split the synthetic external file into smaller business-date imports.",
                details = mapOf("fileByteSize" to fileBytes.size, "maximumFileByteSize" to MAX_FILE_BYTES)
            )
        }

        val fileSha256 = sha256(fileBytes)
        val requestHash = hashParts(
            "IMPORT_EXTERNAL_SETTLEMENT_CSV",
            request.originalFileName,
            request.institutionCode,
            request.businessDate.toString(),
            fileSha256,
            actorId,
            request.reason
        )
        repository.lockKeys(
            listOf(
                "external-settlement-idempotency:${request.idempotencyKey}",
                "external-settlement-file:$fileSha256"
            )
        )

        repository.findImportByIdempotencyKey(request.idempotencyKey)?.let { existing ->
            requireMatchingRequest(
                existing.requestHash,
                requestHash,
                request.idempotencyKey,
                "IMPORT_EXTERNAL_SETTLEMENT_CSV"
            )
            return importResponse(existing.externalSettlementImportId, replayed = true)
        }

        repository.findImportByFileSha256(fileSha256)?.let { existing ->
            throw paymentError(
                code = "PAYMENT_EXTERNAL_SETTLEMENT_FILE_DUPLICATE",
                status = HttpStatus.CONFLICT,
                invariant = "an external settlement file hash is imported once",
                message = "the same external settlement file has already been imported",
                cause = "The SHA-256 digest matches a previously persisted independent external file.",
                fix = "Replay the original idempotency key or submit a genuinely different external file.",
                details = mapOf(
                    "fileSha256" to fileSha256,
                    "existingExternalSettlementImportId" to existing.externalSettlementImportId
                )
            )
        }

        val parsedLines = try {
            csvParser.parse(request.csvContent, request.businessDate)
        } catch (error: ExternalSettlementCsvException) {
            throw paymentError(
                code = "PAYMENT_EXTERNAL_SETTLEMENT_CSV_INVALID",
                status = HttpStatus.BAD_REQUEST,
                invariant = "external settlement staging rows preserve a validated canonical CSV shape",
                message = error.message,
                cause = "The independent external CSV failed header, type, synthetic-boundary, or date validation.",
                fix = "Use the documented external settlement CSV header and valid ISO dates, amounts, and statuses.",
                details = mapOf(
                    "lineNumber" to error.lineNumber,
                    "field" to error.field
                )
            )
        }

        if (parsedLines.size > MAX_LINE_COUNT) {
            throw paymentError(
                code = "PAYMENT_EXTERNAL_SETTLEMENT_LINE_LIMIT_EXCEEDED",
                status = HttpStatus.BAD_REQUEST,
                invariant = "external settlement imports have a bounded line count",
                message = "external settlement CSV exceeds the line-count limit",
                cause = "The portfolio import keeps each evidence fixture bounded and inspectable.",
                fix = "Split the synthetic external file into smaller imports.",
                details = mapOf("lineCount" to parsedLines.size, "maximumLineCount" to MAX_LINE_COUNT)
            )
        }

        val now = OffsetDateTime.now()
        val importId = "ESI-${UUID.randomUUID().toString().uppercase()}"
        val acceptedLineCount = parsedLines.count { it.status == ExternalSettlementLineStatus.ACCEPTED }
        val rejectedLineCount = parsedLines.size - acceptedLineCount

        repository.insertImport(
            ExternalSettlementImportRecord(
                externalSettlementImportId = importId,
                originalFileName = request.originalFileName,
                institutionCode = request.institutionCode,
                fileSha256 = fileSha256,
                fileByteSize = fileBytes.size.toLong(),
                businessDate = request.businessDate,
                status = ExternalSettlementImportStatus.IMPORTED,
                lineCount = parsedLines.size,
                acceptedLineCount = acceptedLineCount,
                rejectedLineCount = rejectedLineCount,
                requestedBy = actorId,
                reason = request.reason,
                idempotencyKey = request.idempotencyKey,
                requestHash = requestHash,
                syntheticOnly = true,
                receivedAt = now
            )
        )
        parsedLines.forEach { line ->
            repository.insertLine(
                ExternalSettlementLineRecord(
                    externalSettlementLineId = "ESL-${UUID.randomUUID().toString().uppercase()}",
                    externalSettlementImportId = importId,
                    lineNumber = line.lineNumber,
                    externalReference = line.externalReference,
                    paymentInstructionId = line.paymentInstructionId,
                    billerId = line.billerId,
                    amountMinor = line.amountMinor,
                    currency = line.currency,
                    businessDate = line.businessDate,
                    valueDate = line.valueDate,
                    status = line.status,
                    rawLine = line.rawLine,
                    syntheticOnly = true,
                    createdAt = now
                )
            )
        }

        return importResponse(importId, replayed = false)
    }

    @Transactional(readOnly = true)
    fun externalImport(importId: String): ExternalSettlementImportResponse =
        importResponse(importId, replayed = false)

    @Transactional
    fun createBatchRun(
        request: CreateSettlementBatchRunRequest,
        principal: PaymentPrincipal?
    ): SettlementBatchRunResponse {
        val actorId = actorIdFromRequest(request.requestedBy, principal)
        validateBatchRequest(request, actorId)
        val requestHash = hashParts(
            "CREATE_SETTLEMENT_BATCH_RUN",
            request.externalSettlementImportId,
            request.feeRateBps.toString(),
            request.vatRateBps.toString(),
            request.cutoffAt.toInstant().toString(),
            actorId,
            request.reason
        )
        repository.lockKeys(
            listOf(
                "settlement-batch-idempotency:${request.idempotencyKey}",
                "settlement-import:${request.externalSettlementImportId}"
            )
        )

        repository.findBatchRunByIdempotencyKey(request.idempotencyKey)?.let { existing ->
            requireMatchingRequest(
                existing.requestHash,
                requestHash,
                request.idempotencyKey,
                "CREATE_SETTLEMENT_BATCH_RUN"
            )
            return batchRunResponse(existing.settlementBatchRunId, replayed = true)
        }

        val imported = repository.findImport(request.externalSettlementImportId)
            ?: throw paymentError(
                code = "PAYMENT_EXTERNAL_SETTLEMENT_IMPORT_NOT_FOUND",
                status = HttpStatus.NOT_FOUND,
                message = "external settlement import was not found",
                cause = "The requested batch run does not reference a persisted independent external file.",
                fix = "Import the external CSV first and use the returned ESI-* identifier.",
                details = mapOf("externalSettlementImportId" to request.externalSettlementImportId)
            )

        repository.findBatchRunByImportId(imported.externalSettlementImportId)?.let { existing ->
            throw paymentError(
                code = "PAYMENT_SETTLEMENT_IMPORT_ALREADY_BATCHED",
                status = HttpStatus.CONFLICT,
                invariant = "an external settlement import is included in one batch run",
                message = "external settlement import already has a batch run",
                cause = "A different idempotency key attempted to batch an import that already produced durable positions.",
                fix = "Replay the original batch-run idempotency key or inspect the existing SBR-* result.",
                details = mapOf(
                    "externalSettlementImportId" to imported.externalSettlementImportId,
                    "existingSettlementBatchRunId" to existing.settlementBatchRunId
                )
            )
        }

        if (request.cutoffAt.toLocalDate().isBefore(imported.businessDate)) {
            throw paymentError(
                code = "PAYMENT_SETTLEMENT_CUTOFF_INVALID",
                status = HttpStatus.BAD_REQUEST,
                invariant = "settlement batch cutoff cannot precede the imported business date",
                message = "cutoffAt cannot be before the external settlement business date",
                cause = "The requested batch cutoff predates the independent clearing file.",
                fix = "Use a cutoff timestamp on or after the import business date.",
                details = mapOf(
                    "businessDate" to imported.businessDate.toString(),
                    "cutoffAt" to request.cutoffAt.toString()
                )
            )
        }

        val acceptedLines = repository.acceptedLinesForImport(imported.externalSettlementImportId)
        if (acceptedLines.isEmpty()) {
            throw paymentError(
                code = "PAYMENT_SETTLEMENT_NO_ACCEPTED_LINES",
                status = HttpStatus.CONFLICT,
                invariant = "settlement batches contain accepted external clearing lines",
                message = "external settlement import has no accepted lines to batch",
                cause = "Rejected and returned external rows are preserved for later reconciliation but are not payable positions.",
                fix = "Import at least one ACCEPTED row or investigate the rejected external file.",
                details = mapOf("externalSettlementImportId" to imported.externalSettlementImportId)
            )
        }

        val now = OffsetDateTime.now()
        val batchRunId = "SBR-${UUID.randomUUID().toString().uppercase()}"
        repository.insertBatchRun(
            SettlementBatchRunRecord(
                settlementBatchRunId = batchRunId,
                externalSettlementImportId = imported.externalSettlementImportId,
                feeRateBps = request.feeRateBps,
                vatRateBps = request.vatRateBps,
                cutoffAt = request.cutoffAt,
                idempotencyKey = request.idempotencyKey,
                requestHash = requestHash,
                requestedBy = actorId,
                reason = request.reason,
                syntheticOnly = true,
                createdAt = now
            )
        )

        val groups = acceptedLines.groupBy {
            SettlementBatchKey(
                billerId = it.billerId,
                currency = it.currency,
                businessDate = it.businessDate,
                valueDate = it.valueDate
            )
        }
        groups.entries
            .sortedWith(
                compareBy(
                    { it.key.billerId },
                    { it.key.currency },
                    { it.key.valueDate },
                    { it.key.businessDate }
                )
            )
            .forEach { (key, lines) ->
                val grossAmountMinor = checkedSum(lines)
                val feeAmountMinor = applyBasisPoints(grossAmountMinor, request.feeRateBps)
                val vatAmountMinor = applyBasisPoints(feeAmountMinor, request.vatRateBps)
                val adjustmentAmountMinor = 0L
                val netAmountMinor = try {
                    Math.addExact(
                        Math.subtractExact(
                            Math.subtractExact(grossAmountMinor, feeAmountMinor),
                            vatAmountMinor
                        ),
                        adjustmentAmountMinor
                    )
                } catch (_: ArithmeticException) {
                    throw amountOverflow(imported.externalSettlementImportId)
                }
                if (netAmountMinor < 0) {
                    throw paymentError(
                        code = "PAYMENT_SETTLEMENT_NET_AMOUNT_INVALID",
                        status = HttpStatus.CONFLICT,
                        invariant = "gross minus fee minus VAT plus adjustments cannot be negative",
                        message = "settlement batch net amount is negative",
                        cause = "The configured fee and VAT rates exceed the accepted external gross position.",
                        fix = "Use bounded fee and VAT rates that produce a non-negative minor-unit net amount.",
                        details = mapOf(
                            "billerId" to key.billerId,
                            "grossAmountMinor" to grossAmountMinor,
                            "feeAmountMinor" to feeAmountMinor,
                            "vatAmountMinor" to vatAmountMinor
                        )
                    )
                }

                val batchId = "STB-${UUID.randomUUID().toString().uppercase()}"
                repository.insertBatch(
                    SettlementBatchRecord(
                        settlementBatchId = batchId,
                        settlementBatchRunId = batchRunId,
                        externalSettlementImportId = imported.externalSettlementImportId,
                        billerId = key.billerId,
                        currency = key.currency,
                        businessDate = key.businessDate,
                        valueDate = key.valueDate,
                        cutoffAt = request.cutoffAt,
                        grossAmountMinor = grossAmountMinor,
                        feeRateBps = request.feeRateBps,
                        feeAmountMinor = feeAmountMinor,
                        vatRateBps = request.vatRateBps,
                        vatAmountMinor = vatAmountMinor,
                        adjustmentAmountMinor = adjustmentAmountMinor,
                        netAmountMinor = netAmountMinor,
                        status = SettlementBatchStatus.INCLUDED_IN_BATCH,
                        externalPayoutReference = null,
                        syntheticOnly = true,
                        createdAt = now
                    )
                )
                lines.forEach { repository.insertBatchItem(batchId, it) }
            }

        repository.markImportBatched(imported.externalSettlementImportId)
        return batchRunResponse(batchRunId, replayed = false)
    }

    @Transactional(readOnly = true)
    fun batchRun(batchRunId: String): SettlementBatchRunResponse =
        batchRunResponse(batchRunId, replayed = false)

    private fun importResponse(importId: String, replayed: Boolean): ExternalSettlementImportResponse {
        val record = repository.findImport(importId)
            ?: throw paymentError(
                code = "PAYMENT_EXTERNAL_SETTLEMENT_IMPORT_NOT_FOUND",
                status = HttpStatus.NOT_FOUND,
                message = "external settlement import was not found",
                cause = "No independent external file import exists for the supplied identifier.",
                fix = "Use an ESI-* identifier returned by the import endpoint.",
                details = mapOf("externalSettlementImportId" to importId)
            )
        val lines = repository.linesForImport(importId).map { it.toDto() }
        return ExternalSettlementImportResponse(
            item = record.toDto(lines),
            replayed = replayed
        )
    }

    private fun batchRunResponse(batchRunId: String, replayed: Boolean): SettlementBatchRunResponse {
        val record = repository.findBatchRun(batchRunId)
            ?: throw paymentError(
                code = "PAYMENT_SETTLEMENT_BATCH_RUN_NOT_FOUND",
                status = HttpStatus.NOT_FOUND,
                message = "settlement batch run was not found",
                cause = "No durable batch run exists for the supplied identifier.",
                fix = "Use an SBR-* identifier returned by the batch-run endpoint.",
                details = mapOf("settlementBatchRunId" to batchRunId)
            )
        return SettlementBatchRunResponse(
            settlementBatchRunId = record.settlementBatchRunId,
            externalSettlementImportId = record.externalSettlementImportId,
            items = repository.batchesForRun(batchRunId).map { (batch, itemCount) ->
                batch.toDto(itemCount)
            },
            replayed = replayed,
            syntheticOnly = true
        )
    }

    private fun checkedSum(lines: List<ExternalSettlementLineRecord>): Long =
        try {
            lines.fold(0L) { total, line -> Math.addExact(total, line.amountMinor) }
        } catch (_: ArithmeticException) {
            throw amountOverflow(lines.first().externalSettlementImportId)
        }

    private fun applyBasisPoints(amountMinor: Long, rateBps: Int): Long =
        BigDecimal.valueOf(amountMinor)
            .multiply(BigDecimal.valueOf(rateBps.toLong()))
            .divide(BigDecimal.valueOf(BASIS_POINT_DENOMINATOR), 0, RoundingMode.HALF_UP)
            .longValueExact()

    private fun amountOverflow(importId: String): PaymentDomainException =
        paymentError(
            code = "PAYMENT_SETTLEMENT_AMOUNT_OVERFLOW",
            status = HttpStatus.CONFLICT,
            invariant = "settlement position minor-unit arithmetic must fit signed 64-bit storage",
            message = "settlement batch amount overflowed minor-unit storage",
            cause = "Accepted external line amounts could not be safely aggregated.",
            fix = "Split the external file or correct the synthetic amounts before retrying.",
            details = mapOf("externalSettlementImportId" to importId)
        )

    private fun validateImportRequest(request: ImportExternalSettlementCsvRequest, actorId: String) {
        requireNonBlank(request.originalFileName, "originalFileName")
        if (
            request.originalFileName.length > 255 ||
            request.originalFileName.contains('/') ||
            request.originalFileName.contains('\\') ||
            !request.originalFileName.endsWith(".csv", ignoreCase = true)
        ) {
            throw paymentError(
                code = "PAYMENT_EXTERNAL_SETTLEMENT_FILE_NAME_INVALID",
                status = HttpStatus.BAD_REQUEST,
                message = "originalFileName must be a plain .csv file name",
                cause = "External import provenance stores a bounded file name without directory traversal.",
                fix = "Provide a file name such as synthetic-clearing-2026-07-31.csv.",
                details = mapOf("originalFileName" to request.originalFileName)
            )
        }
        requireNonBlank(request.institutionCode, "institutionCode")
        if (!request.institutionCode.matches(Regex("^[A-Z0-9][A-Z0-9_-]{2,39}$"))) {
            throw paymentError(
                code = "PAYMENT_EXTERNAL_SETTLEMENT_INSTITUTION_INVALID",
                status = HttpStatus.BAD_REQUEST,
                message = "institutionCode has an invalid synthetic identifier format",
                cause = "The import source must be attributable to a bounded synthetic institution code.",
                fix = "Use three to forty uppercase letters, digits, underscores, or hyphens.",
                details = mapOf("institutionCode" to request.institutionCode)
            )
        }
        requireNonBlank(request.idempotencyKey, "idempotencyKey")
        requireNonBlank(actorId, "requestedBy")
        requireNonBlank(request.reason, "reason")
    }

    private fun validateBatchRequest(request: CreateSettlementBatchRunRequest, actorId: String) {
        requireNonBlank(request.externalSettlementImportId, "externalSettlementImportId")
        if (!request.externalSettlementImportId.startsWith("ESI-")) {
            throw paymentError(
                code = "PAYMENT_EXTERNAL_SETTLEMENT_IMPORT_ID_INVALID",
                status = HttpStatus.BAD_REQUEST,
                message = "externalSettlementImportId must start with ESI-",
                cause = "Settlement batch runs accept only durable external import identifiers.",
                fix = "Use the ESI-* identifier returned by the import endpoint."
            )
        }
        if (request.feeRateBps !in 0..MAX_RATE_BPS) {
            throw invalidRate("feeRateBps", request.feeRateBps)
        }
        if (request.vatRateBps !in 0..MAX_RATE_BPS) {
            throw invalidRate("vatRateBps", request.vatRateBps)
        }
        requireNonBlank(request.idempotencyKey, "idempotencyKey")
        requireNonBlank(actorId, "requestedBy")
        requireNonBlank(request.reason, "reason")
    }

    private fun invalidRate(field: String, value: Int): PaymentDomainException =
        paymentError(
            code = "PAYMENT_SETTLEMENT_RATE_INVALID",
            status = HttpStatus.BAD_REQUEST,
            invariant = "settlement fee and VAT rates are bounded basis-point values",
            message = "$field must be between 0 and $MAX_RATE_BPS",
            cause = "The settlement calculator rejected an out-of-range basis-point rate.",
            fix = "Use a rate between 0 and 10000 basis points.",
            details = mapOf("field" to field, "value" to value)
        )

    private fun actorIdFromRequest(requestedBy: String, principal: PaymentPrincipal?): String {
        requireNonBlank(requestedBy, "requestedBy")
        if (principal != null && principal.subject != requestedBy) {
            throw paymentError(
                code = "PAYMENT_ACTOR_BINDING_MISMATCH",
                status = HttpStatus.FORBIDDEN,
                policy = "PAYMENT_ACTOR_BINDING",
                message = "settlement requestedBy must match the authenticated actor",
                cause = "External import and settlement batch commands bind the request actor to the bearer-token subject.",
                fix = "Use the authenticated subject as requestedBy before retrying.",
                details = mapOf(
                    "requestedBy" to requestedBy,
                    "authenticatedSubject" to principal.subject
                )
            )
        }
        return requestedBy
    }

    private fun requireMatchingRequest(
        existingRequestHash: String,
        requestHash: String,
        idempotencyKey: String,
        commandType: String
    ) {
        if (existingRequestHash != requestHash) {
            throw paymentError(
                code = "PAYMENT_SETTLEMENT_IDEMPOTENCY_CONFLICT",
                status = HttpStatus.CONFLICT,
                invariant = "an external settlement idempotency key creates one business result",
                message = "idempotency key was reused with a different settlement payload",
                cause = "The same settlement idempotency key already exists with a different request hash.",
                fix = "Replay the original payload or use a new idempotency key.",
                details = mapOf(
                    "idempotencyKey" to idempotencyKey,
                    "commandType" to commandType
                )
            )
        }
    }

    private fun requireNonBlank(value: String, field: String) {
        if (value.isBlank()) {
            throw paymentError(
                code = "PAYMENT_REQUIRED_FIELD_MISSING",
                status = HttpStatus.BAD_REQUEST,
                message = "$field is required",
                cause = "Settlement commands require stable provenance, actor, and idempotency fields.",
                fix = "Populate $field before retrying.",
                details = mapOf("field" to field)
            )
        }
    }

    private fun hashParts(vararg values: String): String =
        sha256(values.joinToString("\u001F").toByteArray(StandardCharsets.UTF_8))

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun ExternalSettlementImportRecord.toDto(
        lines: List<ExternalSettlementLineDto>
    ): ExternalSettlementImportDto =
        ExternalSettlementImportDto(
            externalSettlementImportId = externalSettlementImportId,
            originalFileName = originalFileName,
            institutionCode = institutionCode,
            fileSha256 = fileSha256,
            fileByteSize = fileByteSize,
            businessDate = businessDate,
            status = status,
            lineCount = lineCount,
            acceptedLineCount = acceptedLineCount,
            rejectedLineCount = rejectedLineCount,
            requestedBy = requestedBy,
            reason = reason,
            lines = lines,
            syntheticOnly = syntheticOnly,
            receivedAt = receivedAt
        )

    private fun ExternalSettlementLineRecord.toDto(): ExternalSettlementLineDto =
        ExternalSettlementLineDto(
            externalSettlementLineId = externalSettlementLineId,
            lineNumber = lineNumber,
            externalReference = externalReference,
            paymentInstructionId = paymentInstructionId,
            billerId = billerId,
            amountMinor = amountMinor,
            currency = currency,
            businessDate = businessDate,
            valueDate = valueDate,
            status = status,
            rawLine = rawLine,
            syntheticOnly = syntheticOnly,
            createdAt = createdAt
        )

    private fun SettlementBatchRecord.toDto(itemCount: Int): SettlementBatchDto =
        SettlementBatchDto(
            settlementBatchId = settlementBatchId,
            settlementBatchRunId = settlementBatchRunId,
            externalSettlementImportId = externalSettlementImportId,
            billerId = billerId,
            currency = currency,
            businessDate = businessDate,
            valueDate = valueDate,
            cutoffAt = cutoffAt,
            grossAmountMinor = grossAmountMinor,
            feeRateBps = feeRateBps,
            feeAmountMinor = feeAmountMinor,
            vatRateBps = vatRateBps,
            vatAmountMinor = vatAmountMinor,
            adjustmentAmountMinor = adjustmentAmountMinor,
            netAmountMinor = netAmountMinor,
            itemCount = itemCount,
            status = status,
            externalPayoutReference = externalPayoutReference,
            syntheticOnly = syntheticOnly,
            createdAt = createdAt
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

    companion object {
        private const val BASIS_POINT_DENOMINATOR = 10_000L
        private const val MAX_RATE_BPS = 10_000
        private const val MAX_FILE_BYTES = 1_000_000
        private const val MAX_LINE_COUNT = 10_000
    }
}
