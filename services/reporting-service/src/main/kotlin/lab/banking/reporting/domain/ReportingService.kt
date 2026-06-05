package lab.banking.reporting.domain

import java.util.UUID
import lab.banking.reporting.persistence.ReportingRepository
import lab.banking.reporting.security.ReportingPrincipal
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class ReportingService(
    private val repository: ReportingRepository,
    @param:Value("\${banking-lab.reporting-service.artifact-root:reports/synthetic}")
    private val artifactRoot: String
) {
    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun catalog(reason: String?, principal: ReportingPrincipal): ReportCatalogResponse {
        val viewReason = requireReason(reason)
        val items = repository.reportDefinitions()
        val auditEventId = appendAudit(
            eventType = "REPORT_CATALOG_VIEW",
            principal = principal,
            reason = viewReason,
            reportType = null,
            artifactId = null,
            payload = mapOf(
                "reportCount" to items.size,
                "syntheticOnly" to true
            )
        )
        return ReportCatalogResponse(auditEventId = auditEventId, items = items)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun generate(command: GenerateReportCommand, principal: ReportingPrincipal): GenerateReportResponse {
        val reportType = requireField(command.reportType, "reportType")
        val reason = requireReason(command.reason)
        val idempotencyKey = requireField(command.idempotencyKey, "idempotencyKey")
        val requestedBy = command.requestedBy?.takeIf { it.isNotBlank() } ?: principal.subject
        val requestedRole = command.requestedByRole?.takeIf { it.isNotBlank() }
            ?: principal.roles.sorted().firstOrNull(ALLOWED_GENERATE_ROLES::contains)
            ?: principal.roles.sorted().first()
        requireActor(principal, requestedBy, requestedRole)
        val definition = repository.reportDefinition(reportType)
            ?: throw ReportingErrors.validation("unsupported reportType: $reportType")

        repository.existingArtifact(requestedBy, idempotencyKey)?.let { existing ->
            appendAudit(
                eventType = "REPORT_GENERATE_REPLAYED",
                principal = principal,
                reason = reason,
                reportType = existing.reportType,
                artifactId = existing.artifactId,
                payload = mapOf(
                    "idempotencyKey" to idempotencyKey,
                    "artifactId" to existing.artifactId,
                    "syntheticOnly" to true
                )
            )
            return GenerateReportResponse(item = existing, replayed = true)
        }

        val artifactId = "RPT-${UUID.randomUUID().toString().uppercase()}"
        val artifact = repository.insertArtifact(
            artifactId = artifactId,
            reportType = definition.reportType,
            requestedBy = requestedBy,
            requestedRole = requestedRole,
            reason = reason,
            idempotencyKey = idempotencyKey,
            artifactPath = "${artifactRoot.trimEnd('/')}/${definition.reportType.lowercase()}-$artifactId.json",
            sourceReferences = definition.sourceSystems
        )
        appendAudit(
            eventType = "REPORT_GENERATED",
            principal = principal,
            reason = reason,
            reportType = artifact.reportType,
            artifactId = artifact.artifactId,
            payload = mapOf(
                "artifactId" to artifact.artifactId,
                "reportType" to artifact.reportType,
                "maskedByDefault" to artifact.maskedByDefault,
                "sourceReferenceCount" to artifact.sourceReferences.size,
                "syntheticOnly" to true
            )
        )
        return GenerateReportResponse(item = artifact, replayed = false)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun artifacts(reason: String?, reportType: String?, principal: ReportingPrincipal): ReportArtifactListResponse {
        val viewReason = requireReason(reason)
        val items = repository.artifacts(reportType)
        val auditEventId = appendAudit(
            eventType = "REPORT_ARTIFACT_LIST_VIEW",
            principal = principal,
            reason = viewReason,
            reportType = reportType,
            artifactId = null,
            payload = mapOf(
                "reportType" to reportType,
                "artifactCount" to items.size,
                "syntheticOnly" to true
            )
        )
        return ReportArtifactListResponse(auditEventId = auditEventId, items = items)
    }

    private fun appendAudit(
        eventType: String,
        principal: ReportingPrincipal,
        reason: String,
        reportType: String?,
        artifactId: String?,
        payload: Map<String, Any?>
    ): String {
        val auditEventId = "RPA-${UUID.randomUUID().toString().uppercase()}"
        repository.appendAudit(
            ReportingAccessAuditEvent(
                auditEventId = auditEventId,
                eventType = eventType,
                actorId = principal.subject,
                actorRole = principal.roles.sorted().firstOrNull(ALLOWED_GENERATE_ROLES::contains)
                    ?: principal.roles.sorted().first(),
                reason = reason,
                reportType = reportType,
                artifactId = artifactId,
                payload = payload
            )
        )
        return auditEventId
    }

    private fun requireActor(principal: ReportingPrincipal, requestedBy: String, requestedRole: String) {
        if (principal.subject != requestedBy) {
            throw ReportingErrors.authorization("authenticated actor does not match requestedBy")
        }
        if (requestedRole !in principal.roles) {
            throw ReportingErrors.authorization("authenticated roles do not include requestedByRole")
        }
    }

    private fun requireReason(value: String?): String =
        value?.takeIf { it.isNotBlank() } ?: throw ReportingErrors.reasonRequired()

    private fun requireField(value: String?, field: String): String =
        value?.takeIf { it.isNotBlank() } ?: throw ReportingErrors.validation("$field is required")

    private companion object {
        val ALLOWED_GENERATE_ROLES = setOf("AUDITOR", "COMPLIANCE_MANAGER", "OPS_MANAGER", "REPORTING_ANALYST")
    }
}
