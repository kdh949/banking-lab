package lab.banking.reporting.domain

import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
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
    private val objectMapper: ObjectMapper,
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
        val artifactPath = "${artifactRoot.trimEnd('/')}/${definition.reportType.lowercase()}-$artifactId.json"
        val artifactContent = renderArtifactContent(
            definition = definition,
            artifactId = artifactId,
            artifactPath = artifactPath,
            requestedBy = requestedBy,
            requestedRole = requestedRole
        )
        val artifact = repository.insertArtifact(
            artifactId = artifactId,
            reportType = definition.reportType,
            requestedBy = requestedBy,
            requestedRole = requestedRole,
            reason = reason,
            idempotencyKey = idempotencyKey,
            artifactPath = artifactPath,
            artifactContent = artifactContent,
            contentSha256 = sha256(artifactContent),
            retentionPolicy = RETENTION_POLICY,
            retentionUntil = LocalDate.now(ZoneOffset.UTC).plusYears(RETENTION_YEARS),
            exportFormat = EXPORT_FORMAT,
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
                "contentSha256" to artifact.contentSha256,
                "retentionPolicy" to artifact.retentionPolicy,
                "sourceReferenceCount" to artifact.sourceReferences.size,
                "syntheticOnly" to true
            )
        )
        appendOutbox(
            eventType = "ReportArtifactGenerated",
            aggregateId = artifact.artifactId,
            idempotencyKey = "report-generated:$idempotencyKey",
            payload = mapOf(
                "artifactId" to artifact.artifactId,
                "reportType" to artifact.reportType,
                "contentSha256" to artifact.contentSha256,
                "retentionPolicy" to artifact.retentionPolicy,
                "syntheticOnly" to true,
                "ledgerRowsMutated" to false
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

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun exportArtifact(artifactId: String, reason: String?, principal: ReportingPrincipal): ReportArtifactExportResponse {
        val viewReason = requireReason(reason)
        val artifact = repository.artifactOrNull(requireField(artifactId, "artifactId"))
            ?: throw ReportingErrors.notFound("report artifact not found: $artifactId")
        if (artifact.status != "GENERATED") {
            throw ReportingErrors.validation("only GENERATED report artifacts can be exported")
        }
        val packageName = "${artifact.artifactId}-${artifact.reportType.lowercase()}.$EXPORT_FORMAT_EXTENSION"
        val packageContent = exportPackageContent(artifact, packageName)
        val auditEventId = appendAudit(
            eventType = "REPORT_ARTIFACT_EXPORTED",
            principal = principal,
            reason = viewReason,
            reportType = artifact.reportType,
            artifactId = artifact.artifactId,
            payload = mapOf(
                "artifactId" to artifact.artifactId,
                "packageName" to packageName,
                "contentSha256" to artifact.contentSha256,
                "exportFormat" to artifact.exportFormat,
                "syntheticOnly" to true
            )
        )
        appendOutbox(
            eventType = "ReportArtifactExported",
            aggregateId = artifact.artifactId,
            idempotencyKey = null,
            payload = mapOf(
                "artifactId" to artifact.artifactId,
                "reportType" to artifact.reportType,
                "packageName" to packageName,
                "contentSha256" to artifact.contentSha256,
                "syntheticOnly" to true,
                "ledgerRowsMutated" to false
            )
        )
        return ReportArtifactExportResponse(
            auditEventId = auditEventId,
            packageName = packageName,
            contentSha256 = artifact.contentSha256,
            exportFormat = artifact.exportFormat,
            item = artifact,
            packageContent = packageContent
        )
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun runRetentionSweep(command: RunReportRetentionSweepCommand, principal: ReportingPrincipal): ReportRetentionSweepResponse {
        val reason = requireReason(command.reason)
        val requestedBy = command.requestedBy?.takeIf { it.isNotBlank() } ?: principal.subject
        val requestedRole = command.requestedByRole?.takeIf { it.isNotBlank() }
            ?: principal.roles.sorted().firstOrNull(ALLOWED_RETENTION_ROLES::contains)
            ?: principal.roles.sorted().first()
        requireActor(principal, requestedBy, requestedRole)
        if (requestedRole !in ALLOWED_RETENTION_ROLES) {
            throw ReportingErrors.authorization("requestedByRole is not allowed to run retention sweeps")
        }
        val sweepDate = command.sweepDate ?: LocalDate.now(ZoneOffset.UTC)
        val expiredArtifacts = repository.expiredGeneratedArtifacts(sweepDate)
        val expiredArtifactIds = expiredArtifacts.map { it.artifactId }
        val expiredCount = repository.expireArtifacts(expiredArtifactIds)
        val auditEventId = appendAudit(
            eventType = "REPORT_RETENTION_SWEEP_RUN",
            principal = principal,
            reason = reason,
            reportType = null,
            artifactId = null,
            payload = mapOf(
                "sweepDate" to sweepDate.toString(),
                "expiredCount" to expiredCount,
                "expiredArtifactIds" to expiredArtifactIds,
                "ledgerRowsMutated" to false,
                "syntheticOnly" to true
            )
        )
        appendOutbox(
            eventType = "ReportRetentionSweepCompleted",
            aggregateType = "REPORT_RETENTION_SWEEP",
            aggregateId = "REPORT_RETENTION_SWEEP-${sweepDate}",
            idempotencyKey = null,
            payload = mapOf(
                "sweepDate" to sweepDate.toString(),
                "expiredCount" to expiredCount,
                "expiredArtifactIds" to expiredArtifactIds,
                "syntheticOnly" to true,
                "ledgerRowsMutated" to false
            )
        )
        return ReportRetentionSweepResponse(
            auditEventId = auditEventId,
            sweepDate = sweepDate,
            expiredCount = expiredCount,
            expiredArtifactIds = expiredArtifactIds
        )
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

    private fun appendOutbox(
        eventType: String,
        aggregateType: String = "REPORT_ARTIFACT",
        aggregateId: String,
        idempotencyKey: String?,
        payload: Map<String, Any?>
    ) {
        repository.appendOutboxEvent(
            outboxEventId = "RPO-${UUID.randomUUID().toString().uppercase()}",
            eventType = eventType,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            idempotencyKey = idempotencyKey,
            payload = payload
        )
    }

    private fun renderArtifactContent(
        definition: ReportDefinitionDto,
        artifactId: String,
        artifactPath: String,
        requestedBy: String,
        requestedRole: String
    ): Map<String, Any?> =
        linkedMapOf(
            "schemaVersion" to 1,
            "artifactId" to artifactId,
            "reportType" to definition.reportType,
            "title" to definition.title,
            "category" to definition.category,
            "artifactPath" to artifactPath,
            "syntheticOnly" to true,
            "maskedByDefault" to true,
            "generatedAt" to OffsetDateTime.now(ZoneOffset.UTC).toString(),
            "requestedByMasked" to maskIdentifier(requestedBy),
            "requestedRole" to requestedRole,
            "sourceSnapshot" to linkedMapOf(
                "sourceSystems" to definition.sourceSystems,
                "defaultMaskingPolicy" to definition.defaultMaskingPolicy,
                "sensitive" to definition.sensitive,
                "syntheticOnly" to definition.syntheticOnly
            ),
            "controls" to linkedMapOf(
                "syntheticOnly" to true,
                "maskedByDefault" to true,
                "realPiiUsed" to false,
                "realMoneyUsed" to false,
                "externalFilingSubmitted" to false,
                "ledgerRowsMutated" to false,
                "reasonCapturedInMetadata" to true
            ),
            "sections" to renderedSections(definition)
        )

    private fun renderedSections(definition: ReportDefinitionDto): List<Map<String, Any?>> =
        when (definition.reportType) {
            "AUDIT_SUMMARY" -> listOf(
                linkedMapOf(
                    "sectionId" to "audit-control-summary",
                    "title" to "Synthetic audit control summary",
                    "metrics" to listOf(
                        metric("maskingPolicy", definition.defaultMaskingPolicy),
                        metric("reasonRequired", true),
                        metric("accessAuditEvents", listOf("REPORT_CATALOG_VIEW", "REPORT_GENERATED", "REPORT_ARTIFACT_LIST_VIEW"))
                    )
                )
            )
            "OPERATIONS_DAILY" -> listOf(
                linkedMapOf(
                    "sectionId" to "operations-readiness-summary",
                    "title" to "Synthetic operations readiness summary",
                    "metrics" to listOf(
                        metric("outboxVisibility", "metadata-lineage"),
                        metric("workflowVisibility", "metadata-lineage"),
                        metric("ledgerCommandTransactionHeld", false)
                    )
                )
            )
            "EVIDENCE_COVERAGE" -> listOf(
                linkedMapOf(
                    "sectionId" to "evidence-coverage-summary",
                    "title" to "Synthetic evidence coverage summary",
                    "metrics" to listOf(
                        metric("coverageMatrixPath", "docs/implementation-coverage-matrix.md"),
                        metric("evidenceRoot", "docs/test-evidence"),
                        metric("generatedFromTargetStackSources", true)
                    )
                )
            )
            else -> listOf(
                linkedMapOf(
                    "sectionId" to "source-lineage-summary",
                    "title" to "Synthetic source lineage summary",
                    "metrics" to definition.sourceSystems.map { metric(it, "metadata-lineage") }
                )
            )
        }

    private fun exportPackageContent(artifact: ReportArtifactDto, packageName: String): Map<String, Any?> =
        linkedMapOf(
            "schemaVersion" to 1,
            "packageName" to packageName,
            "artifactId" to artifact.artifactId,
            "reportType" to artifact.reportType,
            "exportFormat" to artifact.exportFormat,
            "contentSha256" to artifact.contentSha256,
            "exportedAt" to OffsetDateTime.now(ZoneOffset.UTC).toString(),
            "retentionPolicy" to artifact.retentionPolicy,
            "retentionUntil" to artifact.retentionUntil?.toString(),
            "syntheticOnly" to true,
            "maskedByDefault" to artifact.maskedByDefault,
            "controls" to linkedMapOf(
                "realPiiUsed" to false,
                "realMoneyUsed" to false,
                "externalFilingSubmitted" to false,
                "ledgerRowsMutated" to false,
                "downloadSimulationOnly" to true
            ),
            "artifactContent" to artifact.artifactContent
        )

    private fun metric(name: String, value: Any?): Map<String, Any?> =
        linkedMapOf("name" to name, "value" to value)

    private fun maskIdentifier(value: String): String =
        when {
            value.length <= 2 -> "**"
            value.length <= 6 -> "${value.take(2)}***"
            else -> "${value.take(3)}***${value.takeLast(2)}"
        }

    private fun sha256(value: Map<String, Any?>): String =
        MessageDigest.getInstance("SHA-256")
            .digest(objectMapper.writeValueAsBytes(value))
            .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

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
        val ALLOWED_RETENTION_ROLES = setOf("COMPLIANCE_MANAGER", "OPS_MANAGER", "REPORTING_ANALYST")
        const val EXPORT_FORMAT = "JSON"
        const val EXPORT_FORMAT_EXTENSION = "json"
        const val RETENTION_POLICY = "SYNTHETIC_7Y"
        const val RETENTION_YEARS = 7L
    }
}
