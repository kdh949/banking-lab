package lab.banking.payment.settlement

import java.time.LocalDate
import java.time.OffsetDateTime

enum class ExternalSettlementImportStatus {
    IMPORTED,
    BATCHED
}

enum class ExternalSettlementLineStatus {
    ACCEPTED,
    REJECTED,
    RETURNED
}

enum class SettlementBatchStatus {
    INCLUDED_IN_BATCH
}

data class ImportExternalSettlementCsvRequest(
    val originalFileName: String,
    val institutionCode: String,
    val businessDate: LocalDate,
    val csvContent: String,
    val idempotencyKey: String,
    val requestedBy: String,
    val reason: String
)

data class CreateSettlementBatchRunRequest(
    val externalSettlementImportId: String,
    val feeRateBps: Int,
    val vatRateBps: Int = 1_000,
    val cutoffAt: OffsetDateTime,
    val idempotencyKey: String,
    val requestedBy: String,
    val reason: String
)

data class ExternalSettlementLineDto(
    val externalSettlementLineId: String,
    val lineNumber: Int,
    val externalReference: String,
    val paymentInstructionId: String,
    val billerId: String,
    val amountMinor: Long,
    val currency: String,
    val businessDate: LocalDate,
    val valueDate: LocalDate,
    val status: ExternalSettlementLineStatus,
    val rawLine: String,
    val syntheticOnly: Boolean,
    val createdAt: OffsetDateTime
)

data class ExternalSettlementImportDto(
    val externalSettlementImportId: String,
    val originalFileName: String,
    val institutionCode: String,
    val fileSha256: String,
    val fileByteSize: Long,
    val businessDate: LocalDate,
    val status: ExternalSettlementImportStatus,
    val lineCount: Int,
    val acceptedLineCount: Int,
    val rejectedLineCount: Int,
    val requestedBy: String,
    val reason: String,
    val lines: List<ExternalSettlementLineDto>,
    val syntheticOnly: Boolean,
    val receivedAt: OffsetDateTime
)

data class ExternalSettlementImportResponse(
    val item: ExternalSettlementImportDto,
    val replayed: Boolean
)

data class SettlementBatchDto(
    val settlementBatchId: String,
    val settlementBatchRunId: String,
    val externalSettlementImportId: String,
    val billerId: String,
    val currency: String,
    val businessDate: LocalDate,
    val valueDate: LocalDate,
    val cutoffAt: OffsetDateTime,
    val grossAmountMinor: Long,
    val feeRateBps: Int,
    val feeAmountMinor: Long,
    val vatRateBps: Int,
    val vatAmountMinor: Long,
    val adjustmentAmountMinor: Long,
    val netAmountMinor: Long,
    val itemCount: Int,
    val status: SettlementBatchStatus,
    val externalPayoutReference: String?,
    val syntheticOnly: Boolean,
    val createdAt: OffsetDateTime
)

data class SettlementBatchRunResponse(
    val settlementBatchRunId: String,
    val externalSettlementImportId: String,
    val items: List<SettlementBatchDto>,
    val replayed: Boolean,
    val syntheticOnly: Boolean
)

data class ParsedExternalSettlementLine(
    val lineNumber: Int,
    val externalReference: String,
    val paymentInstructionId: String,
    val billerId: String,
    val amountMinor: Long,
    val currency: String,
    val businessDate: LocalDate,
    val valueDate: LocalDate,
    val status: ExternalSettlementLineStatus,
    val rawLine: String
)

data class ExternalSettlementImportRecord(
    val externalSettlementImportId: String,
    val originalFileName: String,
    val institutionCode: String,
    val fileSha256: String,
    val fileByteSize: Long,
    val businessDate: LocalDate,
    val status: ExternalSettlementImportStatus,
    val lineCount: Int,
    val acceptedLineCount: Int,
    val rejectedLineCount: Int,
    val requestedBy: String,
    val reason: String,
    val idempotencyKey: String,
    val requestHash: String,
    val syntheticOnly: Boolean,
    val receivedAt: OffsetDateTime
)

data class ExternalSettlementLineRecord(
    val externalSettlementLineId: String,
    val externalSettlementImportId: String,
    val lineNumber: Int,
    val externalReference: String,
    val paymentInstructionId: String,
    val billerId: String,
    val amountMinor: Long,
    val currency: String,
    val businessDate: LocalDate,
    val valueDate: LocalDate,
    val status: ExternalSettlementLineStatus,
    val rawLine: String,
    val syntheticOnly: Boolean,
    val createdAt: OffsetDateTime
)

data class SettlementBatchRunRecord(
    val settlementBatchRunId: String,
    val externalSettlementImportId: String,
    val feeRateBps: Int,
    val vatRateBps: Int,
    val cutoffAt: OffsetDateTime,
    val idempotencyKey: String,
    val requestHash: String,
    val requestedBy: String,
    val reason: String,
    val syntheticOnly: Boolean,
    val createdAt: OffsetDateTime
)

data class SettlementBatchRecord(
    val settlementBatchId: String,
    val settlementBatchRunId: String,
    val externalSettlementImportId: String,
    val billerId: String,
    val currency: String,
    val businessDate: LocalDate,
    val valueDate: LocalDate,
    val cutoffAt: OffsetDateTime,
    val grossAmountMinor: Long,
    val feeRateBps: Int,
    val feeAmountMinor: Long,
    val vatRateBps: Int,
    val vatAmountMinor: Long,
    val adjustmentAmountMinor: Long,
    val netAmountMinor: Long,
    val status: SettlementBatchStatus,
    val externalPayoutReference: String?,
    val syntheticOnly: Boolean,
    val createdAt: OffsetDateTime
)

data class SettlementBatchKey(
    val billerId: String,
    val currency: String,
    val businessDate: LocalDate,
    val valueDate: LocalDate
)

class ExternalSettlementCsvException(
    val lineNumber: Int?,
    val field: String?,
    override val message: String
) : RuntimeException(message)
