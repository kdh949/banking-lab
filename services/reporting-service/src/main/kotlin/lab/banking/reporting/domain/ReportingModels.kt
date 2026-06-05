package lab.banking.reporting.domain

import java.time.LocalDate
import java.time.OffsetDateTime
import org.springframework.http.HttpStatus

data class ReportDefinitionDto(
    val reportType: String,
    val title: String,
    val category: String,
    val defaultMaskingPolicy: String,
    val sensitive: Boolean,
    val sourceSystems: List<String>,
    val syntheticOnly: Boolean
)

data class ReportCatalogResponse(
    val auditEventId: String,
    val items: List<ReportDefinitionDto>,
    val syntheticOnly: Boolean = true
)

data class GenerateReportCommand(
    val reportType: String,
    val requestedBy: String? = null,
    val requestedByRole: String? = null,
    val reason: String? = null,
    val idempotencyKey: String? = null
)

data class ReportArtifactDto(
    val artifactId: String,
    val reportType: String,
    val requestedBy: String,
    val requestedRole: String,
    val reason: String,
    val status: String,
    val artifactPath: String,
    val artifactContent: Map<String, Any?>,
    val contentSha256: String,
    val retentionPolicy: String,
    val retentionUntil: LocalDate?,
    val exportFormat: String,
    val sourceReferences: List<String>,
    val maskedByDefault: Boolean,
    val syntheticOnly: Boolean,
    val generatedAt: OffsetDateTime?
)

data class GenerateReportResponse(
    val item: ReportArtifactDto,
    val replayed: Boolean
)

data class ReportArtifactListResponse(
    val auditEventId: String,
    val items: List<ReportArtifactDto>,
    val syntheticOnly: Boolean = true
)

data class ReportArtifactExportResponse(
    val auditEventId: String,
    val packageName: String,
    val contentSha256: String,
    val exportFormat: String,
    val item: ReportArtifactDto,
    val packageContent: Map<String, Any?>,
    val syntheticOnly: Boolean = true
)

data class ReportingAccessAuditEvent(
    val auditEventId: String,
    val eventType: String,
    val actorId: String,
    val actorRole: String,
    val reason: String,
    val reportType: String?,
    val artifactId: String?,
    val payload: Map<String, Any?>
)

class ReportingDomainException(
    val code: String,
    val status: HttpStatus,
    val policy: String? = null,
    override val message: String,
    val causeText: String,
    val fix: String,
    val details: Map<String, Any?>? = null
) : RuntimeException(message)

object ReportingErrors {
    fun reasonRequired(message: String = "reporting access requires a business reason"): ReportingDomainException =
        ReportingDomainException(
            code = "REPORTING_POLICY_REASON_REQUIRED",
            status = HttpStatus.BAD_REQUEST,
            policy = "REASON_REQUIRED",
            message = message,
            causeText = "A reporting read or generation request was submitted without an explicit business reason.",
            fix = "Retry with a non-empty reason tied to the report category, audit review, or operations review."
        )

    fun validation(message: String): ReportingDomainException =
        ReportingDomainException(
            code = "REPORTING_REQUEST_VALIDATION_FAILED",
            status = HttpStatus.BAD_REQUEST,
            message = message,
            causeText = "The reporting request is missing a required field or references an unsupported synthetic report.",
            fix = "Correct the request payload according to the reporting-service API contract."
        )

    fun authorization(message: String): ReportingDomainException =
        ReportingDomainException(
            code = "REPORTING_AUTHORIZATION_POLICY_VIOLATION",
            status = HttpStatus.FORBIDDEN,
            policy = "REPORTING_RBAC_ROUTE_POLICY",
            message = message,
            causeText = "The actor role or request actor does not satisfy the modeled reporting-service policy.",
            fix = "Retry with an authorized synthetic role and matching actor context."
        )

    fun notFound(message: String): ReportingDomainException =
        ReportingDomainException(
            code = "REPORTING_RESOURCE_NOT_FOUND",
            status = HttpStatus.NOT_FOUND,
            message = message,
            causeText = "The requested synthetic reporting artifact does not exist.",
            fix = "Retry with an artifact id returned by the reporting artifact list API."
        )
}
