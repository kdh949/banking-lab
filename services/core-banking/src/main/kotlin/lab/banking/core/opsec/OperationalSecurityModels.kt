package lab.banking.core.opsec

import java.time.OffsetDateTime

data class AuditWormExportCommand(
    val exportedBy: String,
    val exportedRole: String = "OPS_MANAGER",
    val reason: String
)

data class AuditWormSegmentDto(
    val segmentId: String,
    val segmentNo: Long,
    val fromAuditEventId: String?,
    val throughAuditEventId: String?,
    val eventCount: Int,
    val previousAnchorHash: String?,
    val segmentPayloadHash: String,
    val segmentHash: String,
    val signingKeyId: String,
    val exportedBy: String,
    val exportedRole: String,
    val exportReason: String,
    val storageUri: String,
    val status: String,
    val exportedAt: OffsetDateTime,
    val syntheticOnly: Boolean = true
)

data class AuditWormVerificationResult(
    val valid: Boolean,
    val checkedSegments: Int,
    val failures: List<String>,
    val syntheticOnly: Boolean = true
)

data class RotateSyntheticKeyCommand(
    val purpose: String,
    val rotatedBy: String,
    val rotatedByRole: String = "OPS_MANAGER",
    val reason: String
)

data class SyntheticKmsKeyDto(
    val keyId: String,
    val purpose: String,
    val keyVersion: Int,
    val status: String,
    val keyMaterialRef: String,
    val syntheticKeyHash: String,
    val activatedAt: OffsetDateTime,
    val retiredAt: OffsetDateTime?,
    val rotatedFromKeyId: String?,
    val syntheticOnly: Boolean = true
)

data class KmsRotationResult(
    val oldKey: SyntheticKmsKeyDto,
    val newKey: SyntheticKmsKeyDto,
    val auditWormVerification: AuditWormVerificationResult,
    val syntheticOnly: Boolean = true
)

data class BreakGlassGrantCommand(
    val operatorId: String,
    val operatorRole: String = "OPS_MANAGER",
    val elevatedRole: String,
    val reason: String,
    val durationMinutes: Long = 60
)

data class BreakGlassGrantDto(
    val grantId: String,
    val operatorId: String,
    val operatorRole: String,
    val elevatedRole: String,
    val reason: String,
    val status: String,
    val requestedAt: OffsetDateTime,
    val expiresAt: OffsetDateTime,
    val reviewCase: BreakGlassReviewCaseDto,
    val auditEventId: String?,
    val syntheticOnly: Boolean = true
)

data class BreakGlassReviewCaseDto(
    val reviewCaseId: String,
    val status: String,
    val openedAt: OffsetDateTime,
    val dueAt: OffsetDateTime,
    val reviewerId: String?,
    val reviewerRole: String?,
    val reviewReason: String?,
    val reviewedAt: OffsetDateTime?,
    val syntheticOnly: Boolean = true
)

data class ExpireBreakGlassGrantsCommand(
    val expiredBy: String,
    val expiredByRole: String = "OPS_MANAGER",
    val reason: String
)

data class ExpireBreakGlassGrantsResult(
    val expiredCount: Int,
    val syntheticOnly: Boolean = true
)

data class CloseBreakGlassReviewCommand(
    val reviewedBy: String,
    val reviewedByRole: String = "COMPLIANCE_MANAGER",
    val reviewReason: String
)
