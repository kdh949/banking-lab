package lab.banking.reporting.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import lab.banking.reporting.domain.ReportArtifactDto
import lab.banking.reporting.domain.ReportDefinitionDto
import lab.banking.reporting.domain.ReportingAccessAuditEvent
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ReportingRepository(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    fun reportDefinitions(): List<ReportDefinitionDto> =
        jdbc.query(
            """
            SELECT report_type, title, category, default_masking_policy, sensitive,
                   source_systems, synthetic_only
            FROM report_definitions
            ORDER BY report_type
            """.trimIndent(),
            emptyMap<String, Any?>()
        ) { rs, _ -> mapDefinition(rs) }

    fun reportDefinition(reportType: String): ReportDefinitionDto? =
        jdbc.query(
            """
            SELECT report_type, title, category, default_masking_policy, sensitive,
                   source_systems, synthetic_only
            FROM report_definitions
            WHERE report_type = :reportType
            """.trimIndent(),
            mapOf("reportType" to reportType)
        ) { rs, _ -> mapDefinition(rs) }.firstOrNull()

    fun existingArtifact(requestedBy: String, idempotencyKey: String): ReportArtifactDto? =
        jdbc.query(
            artifactSql("WHERE requested_by = :requestedBy AND idempotency_key = :idempotencyKey"),
            mapOf("requestedBy" to requestedBy, "idempotencyKey" to idempotencyKey)
        ) { rs, _ -> mapArtifact(rs) }.firstOrNull()

    fun insertArtifact(
        artifactId: String,
        reportType: String,
        requestedBy: String,
        requestedRole: String,
        reason: String,
        idempotencyKey: String,
        artifactPath: String,
        artifactContent: Map<String, Any?>,
        contentSha256: String,
        retentionPolicy: String,
        retentionUntil: LocalDate,
        exportFormat: String,
        sourceReferences: List<String>
    ): ReportArtifactDto {
        jdbc.update(
            """
            INSERT INTO report_artifacts (
              artifact_id, report_type, requested_by, requested_role, reason,
              idempotency_key, status, artifact_path, source_references,
              artifact_content, content_sha256, retention_policy, retention_until,
              export_format, masked_by_default, synthetic_only
            ) VALUES (
              :artifactId, :reportType, :requestedBy, :requestedRole, :reason,
              :idempotencyKey, 'GENERATED', :artifactPath, CAST(:sourceReferences AS jsonb),
              CAST(:artifactContent AS jsonb), :contentSha256, :retentionPolicy, :retentionUntil,
              :exportFormat, true, true
            )
            """.trimIndent(),
            mapOf(
                "artifactId" to artifactId,
                "reportType" to reportType,
                "requestedBy" to requestedBy,
                "requestedRole" to requestedRole,
                "reason" to reason,
                "idempotencyKey" to idempotencyKey,
                "artifactPath" to artifactPath,
                "artifactContent" to objectMapper.writeValueAsString(artifactContent),
                "contentSha256" to contentSha256,
                "retentionPolicy" to retentionPolicy,
                "retentionUntil" to retentionUntil,
                "exportFormat" to exportFormat,
                "sourceReferences" to objectMapper.writeValueAsString(sourceReferences)
            )
        )
        return artifact(artifactId)
    }

    fun artifact(artifactId: String): ReportArtifactDto =
        jdbc.query(
            artifactSql("WHERE artifact_id = :artifactId"),
            mapOf("artifactId" to artifactId)
        ) { rs, _ -> mapArtifact(rs) }.first()

    fun artifactOrNull(artifactId: String): ReportArtifactDto? =
        jdbc.query(
            artifactSql("WHERE artifact_id = :artifactId"),
            mapOf("artifactId" to artifactId)
        ) { rs, _ -> mapArtifact(rs) }.firstOrNull()

    fun artifacts(reportType: String?): List<ReportArtifactDto> =
        jdbc.query(
            artifactSql(if (reportType.isNullOrBlank()) "" else "WHERE report_type = :reportType") + " ORDER BY generated_at DESC",
            if (reportType.isNullOrBlank()) emptyMap<String, Any?>() else mapOf("reportType" to reportType)
        ) { rs, _ -> mapArtifact(rs) }

    fun expiredGeneratedArtifacts(sweepDate: LocalDate): List<ReportArtifactDto> =
        jdbc.query(
            artifactSql("WHERE status = 'GENERATED' AND retention_until < :sweepDate") + " ORDER BY retention_until, artifact_id",
            mapOf("sweepDate" to sweepDate)
        ) { rs, _ -> mapArtifact(rs) }

    fun expireArtifacts(artifactIds: List<String>): Int {
        if (artifactIds.isEmpty()) {
            return 0
        }
        return jdbc.update(
            """
            UPDATE report_artifacts
            SET status = 'EXPIRED',
                expired_at = now(),
                retention_action = 'SYNTHETIC_RETENTION_EXPIRED'
            WHERE artifact_id IN (:artifactIds)
              AND status = 'GENERATED'
            """.trimIndent(),
            mapOf("artifactIds" to artifactIds)
        )
    }

    fun appendAudit(event: ReportingAccessAuditEvent) {
        jdbc.update(
            """
            INSERT INTO reporting_access_audit_events (
              audit_event_id, event_type, actor_id, actor_role, reason,
              report_type, artifact_id, payload_json
            ) VALUES (
              :auditEventId, :eventType, :actorId, :actorRole, :reason,
              :reportType, :artifactId, CAST(:payloadJson AS jsonb)
            )
            """.trimIndent(),
            mapOf(
                "auditEventId" to event.auditEventId,
                "eventType" to event.eventType,
                "actorId" to event.actorId,
                "actorRole" to event.actorRole,
                "reason" to event.reason,
                "reportType" to event.reportType,
                "artifactId" to event.artifactId,
                "payloadJson" to objectMapper.writeValueAsString(event.payload)
            )
        )
    }

    private fun artifactSql(whereClause: String): String =
        """
        SELECT artifact_id, report_type, requested_by, requested_role, reason,
               status, artifact_path, artifact_content, content_sha256,
               retention_policy, retention_until, export_format,
               source_references, masked_by_default,
               synthetic_only, generated_at
        FROM report_artifacts
        $whereClause
        """.trimIndent()

    private fun mapDefinition(rs: ResultSet): ReportDefinitionDto =
        ReportDefinitionDto(
            reportType = rs.getString("report_type"),
            title = rs.getString("title"),
            category = rs.getString("category"),
            defaultMaskingPolicy = rs.getString("default_masking_policy"),
            sensitive = rs.getBoolean("sensitive"),
            sourceSystems = readStringList(rs.getString("source_systems")),
            syntheticOnly = rs.getBoolean("synthetic_only")
        )

    private fun mapArtifact(rs: ResultSet): ReportArtifactDto =
        ReportArtifactDto(
            artifactId = rs.getString("artifact_id"),
            reportType = rs.getString("report_type"),
            requestedBy = rs.getString("requested_by"),
            requestedRole = rs.getString("requested_role"),
            reason = rs.getString("reason"),
            status = rs.getString("status"),
            artifactPath = rs.getString("artifact_path"),
            artifactContent = readMap(rs.getString("artifact_content")),
            contentSha256 = rs.getString("content_sha256"),
            retentionPolicy = rs.getString("retention_policy"),
            retentionUntil = rs.getObject("retention_until", LocalDate::class.java),
            exportFormat = rs.getString("export_format"),
            sourceReferences = readStringList(rs.getString("source_references")),
            maskedByDefault = rs.getBoolean("masked_by_default"),
            syntheticOnly = rs.getBoolean("synthetic_only"),
            generatedAt = rs.getObject("generated_at", OffsetDateTime::class.java)
        )

    private fun readStringList(value: String): List<String> =
        objectMapper.readValue(value, Array<String>::class.java).toList()

    private fun readMap(value: String): Map<String, Any?> =
        objectMapper.readValue(value, object : TypeReference<Map<String, Any?>>() {})
}
