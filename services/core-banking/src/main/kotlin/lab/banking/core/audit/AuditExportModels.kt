package lab.banking.core.audit

import java.time.OffsetDateTime

data class AuditExportRequestCommand(
    val requestedBy: String,
    val requestedRole: String = "AUDITOR",
    val reason: String,
    val idempotencyKey: String,
    val exportFormat: String = "NDJSON"
)

data class AuditExportApproveCommand(
    val approvedBy: String,
    val approvedByRole: String = "COMPLIANCE_MANAGER",
    val reason: String
)

data class AuditExportRejectCommand(
    val rejectedBy: String,
    val rejectedByRole: String = "COMPLIANCE_MANAGER",
    val reason: String
)

data class AuditExportJobResponse(
    val item: AuditExportJobDto,
    val replayed: Boolean = false
)

data class AuditExportJobDto(
    val exportId: String,
    val status: String,
    val requestedBy: String,
    val requestedRole: String,
    val reason: String,
    val exportFormat: String,
    val approvalId: String,
    val requestedAt: OffsetDateTime,
    val approvedBy: String?,
    val approvedRole: String?,
    val approvedAt: OffsetDateTime?,
    val rejectedBy: String?,
    val rejectedRole: String?,
    val rejectedAt: OffsetDateTime?,
    val rejectReason: String?,
    val fromAuditEventId: String?,
    val throughAuditEventId: String?,
    val rowCount: Int,
    val hashChainStart: String?,
    val hashChainEnd: String?,
    val payloadSha256: String?,
    val storageUri: String?,
    val ledgerRowsMutated: Boolean,
    val syntheticOnly: Boolean,
    val file: AuditExportFileDto?
)

data class AuditExportFileDto(
    val fileId: String,
    val fileName: String,
    val format: String,
    val rowCount: Int,
    val sha256: String,
    val storageUri: String,
    val createdAt: OffsetDateTime,
    val syntheticOnly: Boolean
)
