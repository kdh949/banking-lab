
package lab.banking.payment.reconciliation

import java.time.LocalDate
import java.time.OffsetDateTime

enum class PaymentReconciliationRunStatus {
    EVIDENCE_PENDING,
    MATCHED,
    EXCEPTIONS_OPEN,
    FAILED
}

enum class PaymentReconciliationMismatchType {
    MATCHED,
    MISSING_PAYMENT,
    MISSING_LEDGER,
    MISSING_EXTERNAL,
    AMOUNT_MISMATCH,
    STATUS_MISMATCH,
    DUPLICATE_EXTERNAL,
    VALUE_DATE_MISMATCH,
    LATE_SETTLEMENT
}

enum class PaymentReconciliationResultStatus {
    MATCHED,
    OPEN,
    INVESTIGATING,
    RESOLVED
}

data class CreatePaymentReconciliationRunRequest(
    val externalSettlementImportId: String,
    val expectedValueDate: LocalDate,
    val allowedValueDateLagDays: Int = 0,
    val slaDays: Int = 2,
    val ownerId: String,
    val idempotencyKey: String,
    val requestedBy: String,
    val reason: String
)

data class PaymentReconciliationRunDto(
    val paymentReconciliationRunId: String,
    val externalSettlementImportId: String,
    val businessDate: LocalDate,
    val expectedValueDate: LocalDate,
    val allowedValueDateLagDays: Int,
    val slaDays: Int,
    val ownerId: String,
    val status: PaymentReconciliationRunStatus,
    val externalLineCount: Int,
    val ledgerEvidenceCount: Int,
    val matchedCount: Int,
    val exceptionCount: Int,
    val attemptCount: Int,
    val failureMessage: String?,
    val syntheticOnly: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val completedAt: OffsetDateTime?
)

data class PaymentReconciliationResultDto(
    val paymentReconciliationResultId: String,
    val paymentReconciliationRunId: String,
    val externalSettlementLineId: String?,
    val paymentInstructionId: String,
    val ledgerTransactionId: String?,
    val mismatchType: PaymentReconciliationMismatchType,
    val resultStatus: PaymentReconciliationResultStatus,
    val internalAmountMinor: Long?,
    val ledgerAmountMinor: Long?,
    val externalAmountMinor: Long?,
    val currency: String?,
    val externalStatus: String?,
    val businessDate: LocalDate,
    val expectedValueDate: LocalDate,
    val actualValueDate: LocalDate?,
    val ownerId: String,
    val detectedReason: String,
    val detectedAt: OffsetDateTime,
    val dueAt: OffsetDateTime?,
    val agingDays: Long,
    val overdue: Boolean,
    val resolution: String?,
    val approvalId: String?,
    val resolvedAt: OffsetDateTime?,
    val syntheticOnly: Boolean
)

data class PaymentReconciliationRunResponse(
    val item: PaymentReconciliationRunDto,
    val results: List<PaymentReconciliationResultDto>,
    val replayed: Boolean,
    val auditEventId: String?,
    val syntheticOnly: Boolean
)

data class PaymentReconciliationExceptionListResponse(
    val items: List<PaymentReconciliationResultDto>,
    val auditEventId: String,
    val syntheticOnly: Boolean
)

data class PaymentReconciliationRunRecord(
    val paymentReconciliationRunId: String,
    val externalSettlementImportId: String,
    val businessDate: LocalDate,
    val expectedValueDate: LocalDate,
    val allowedValueDateLagDays: Int,
    val slaDays: Int,
    val ownerId: String,
    val status: PaymentReconciliationRunStatus,
    val idempotencyKey: String,
    val requestHash: String,
    val requestedBy: String,
    val reason: String,
    val externalLineCount: Int,
    val ledgerEvidenceCount: Int,
    val matchedCount: Int,
    val exceptionCount: Int,
    val claimToken: String?,
    val claimExpiresAt: OffsetDateTime?,
    val attemptCount: Int,
    val failureMessage: String?,
    val syntheticOnly: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val completedAt: OffsetDateTime?
)

data class PaymentReconciliationResultRecord(
    val paymentReconciliationResultId: String,
    val paymentReconciliationRunId: String,
    val externalSettlementLineId: String?,
    val paymentInstructionId: String,
    val ledgerTransactionId: String?,
    val mismatchType: PaymentReconciliationMismatchType,
    val resultStatus: PaymentReconciliationResultStatus,
    val internalAmountMinor: Long?,
    val ledgerAmountMinor: Long?,
    val externalAmountMinor: Long?,
    val currency: String?,
    val externalStatus: String?,
    val businessDate: LocalDate,
    val expectedValueDate: LocalDate,
    val actualValueDate: LocalDate?,
    val ownerId: String,
    val detectedReason: String,
    val detectedAt: OffsetDateTime,
    val dueAt: OffsetDateTime?,
    val resolution: String?,
    val approvalId: String?,
    val resolvedAt: OffsetDateTime?,
    val syntheticOnly: Boolean
)
