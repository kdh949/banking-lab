package lab.banking.core.audit

import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import lab.banking.core.approval.ApprovalBusinessTypes
import lab.banking.core.approval.ApproveApprovalCommand
import lab.banking.core.approval.PersistentApprovalService
import lab.banking.core.approval.RejectApprovalCommand
import lab.banking.core.approval.SubmitApprovalCommand
import lab.banking.core.common.BankingLabDomainException
import lab.banking.core.security.BankingLabAuthContext
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class AuditExportService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val auditEvents: AuditEventAppender,
    private val approvals: PersistentApprovalService
) {
    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun requestExport(command: AuditExportRequestCommand): AuditExportJobResponse {
        BankingLabAuthContext.requireActor(command.requestedBy, command.requestedRole)
        validateRequester(command.requestedBy, command.requestedRole)
        requireReason(command.reason)
        requireNonBlank(command.idempotencyKey, "idempotencyKey")
        val format = normalizeFormat(command.exportFormat)
        val commandHash = requestCommandHash(command, format)
        findByIdempotencyKey(command.idempotencyKey)?.let { existing ->
            if (existing.commandHash != commandHash) {
                throw idempotencyConflict()
            }
            return AuditExportJobResponse(job(existing.exportId), replayed = true)
        }

        val exportId = "AEX-${UUID.randomUUID().toString().uppercase()}"
        val approval = approvals.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.AUDIT_EXPORT,
                businessReferenceId = exportId,
                requestedBy = command.requestedBy,
                requestedByRole = command.requestedRole,
                requestReason = command.reason,
                beforeSnapshot = mapOf("status" to "NOT_REQUESTED", "syntheticOnly" to true),
                afterSnapshot = mapOf(
                    "status" to "PENDING_APPROVAL",
                    "exportFormat" to format,
                    "ledgerRowsMutated" to false,
                    "syntheticOnly" to true
                ),
                screenId = SCREEN_ID
            )
        )
        jdbc.update(
            """
            INSERT INTO audit_export_jobs (
              export_id, idempotency_key, command_hash, status, requested_by, requested_role,
              reason, export_format, approval_id, metadata_json
            )
            VALUES (
              :exportId, :idempotencyKey, :commandHash, 'PENDING_APPROVAL', :requestedBy, :requestedRole,
              :reason, :exportFormat, :approvalId,
              '{"syntheticOnly":true,"makerCheckerRequired":true,"ledgerRowsMutated":false}'::jsonb
            )
            """.trimIndent(),
            mapOf(
                "exportId" to exportId,
                "idempotencyKey" to command.idempotencyKey,
                "commandHash" to commandHash,
                "requestedBy" to command.requestedBy,
                "requestedRole" to command.requestedRole,
                "reason" to command.reason,
                "exportFormat" to format,
                "approvalId" to approval.approvalId
            )
        )
        return AuditExportJobResponse(job(exportId), replayed = false)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun approveExport(exportId: String, command: AuditExportApproveCommand): AuditExportJobResponse {
        BankingLabAuthContext.requireActor(command.approvedBy, command.approvedByRole)
        validateRequester(command.approvedBy, command.approvedByRole)
        requireReason(command.reason)
        val current = jobForUpdate(exportId)
        if (current.status == "EXPORTED") {
            return AuditExportJobResponse(job(exportId), replayed = true)
        }
        if (current.status != "PENDING_APPROVAL") {
            throw WorkflowErrors.stateViolation("audit export can only be approved from PENDING_APPROVAL")
        }
        approvals.approve(
            current.approvalId,
            ApproveApprovalCommand(
                approvedBy = command.approvedBy,
                approvedByRole = command.approvedByRole,
                screenId = SCREEN_ID
            )
        )

        val rows = auditRows()
        val content = rows.joinToString(separator = "\n", postfix = "\n") { row ->
            objectMapper.writeValueAsString(row.toExportMap())
        }
        val contentHash = sha256(content)
        val fileId = "AEF-${UUID.randomUUID().toString().uppercase()}"
        val fileName = "$exportId.jsonl"
        val storageUri = "local://synthetic/audit-exports/$fileName"
        jdbc.update(
            """
            INSERT INTO audit_export_files (
              file_id, export_id, file_name, format, row_count, sha256, storage_uri, content_jsonl
            )
            VALUES (
              :fileId, :exportId, :fileName, 'NDJSON', :rowCount, :sha256, :storageUri, :contentJsonl
            )
            """.trimIndent(),
            mapOf(
                "fileId" to fileId,
                "exportId" to exportId,
                "fileName" to fileName,
                "rowCount" to rows.size,
                "sha256" to contentHash,
                "storageUri" to storageUri,
                "contentJsonl" to content
            )
        )
        jdbc.update(
            """
            UPDATE audit_export_jobs
            SET status = 'EXPORTED',
                approved_by = :approvedBy,
                approved_role = :approvedRole,
                approved_at = now(),
                from_audit_event_id = :fromAuditEventId,
                through_audit_event_id = :throughAuditEventId,
                row_count = :rowCount,
                hash_chain_start = :hashChainStart,
                hash_chain_end = :hashChainEnd,
                payload_sha256 = :payloadSha256,
                storage_uri = :storageUri,
                metadata_json = metadata_json || CAST(:metadataJson AS jsonb)
            WHERE export_id = :exportId
            """.trimIndent(),
            mapOf(
                "exportId" to exportId,
                "approvedBy" to command.approvedBy,
                "approvedRole" to command.approvedByRole,
                "fromAuditEventId" to rows.firstOrNull()?.auditEventId,
                "throughAuditEventId" to rows.lastOrNull()?.auditEventId,
                "rowCount" to rows.size,
                "hashChainStart" to (rows.firstOrNull()?.previousEventHash ?: "GENESIS"),
                "hashChainEnd" to rows.lastOrNull()?.payloadHash,
                "payloadSha256" to contentHash,
                "storageUri" to storageUri,
                "metadataJson" to objectMapper.writeValueAsString(
                    mapOf(
                        "approvedReason" to command.reason,
                        "fileId" to fileId,
                        "hashAlgorithm" to "SHA-256",
                        "ledgerRowsMutated" to false,
                        "syntheticOnly" to true
                    )
                )
            )
        )
        auditEvents.append(
            eventType = "AUDIT_EXPORT_COMPLETED",
            actorType = "STAFF",
            actorId = command.approvedBy,
            actorRole = command.approvedByRole,
            screenId = SCREEN_ID,
            businessReferenceId = exportId,
            reason = command.reason,
            payload = mapOf(
                "exportId" to exportId,
                "rowCount" to rows.size,
                "sha256" to contentHash,
                "storageUri" to storageUri,
                "ledgerRowsMutated" to false,
                "syntheticOnly" to true
            )
        )
        return AuditExportJobResponse(job(exportId), replayed = false)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun rejectExport(exportId: String, command: AuditExportRejectCommand): AuditExportJobResponse {
        BankingLabAuthContext.requireActor(command.rejectedBy, command.rejectedByRole)
        validateRequester(command.rejectedBy, command.rejectedByRole)
        requireReason(command.reason)
        val current = jobForUpdate(exportId)
        if (current.status != "PENDING_APPROVAL") {
            throw WorkflowErrors.stateViolation("audit export can only be rejected from PENDING_APPROVAL")
        }
        approvals.reject(
            current.approvalId,
            RejectApprovalCommand(
                rejectedBy = command.rejectedBy,
                rejectedByRole = command.rejectedByRole,
                rejectReason = command.reason,
                screenId = SCREEN_ID
            )
        )
        jdbc.update(
            """
            UPDATE audit_export_jobs
            SET status = 'REJECTED',
                rejected_by = :rejectedBy,
                rejected_role = :rejectedRole,
                rejected_at = now(),
                reject_reason = :rejectReason
            WHERE export_id = :exportId
            """.trimIndent(),
            mapOf(
                "exportId" to exportId,
                "rejectedBy" to command.rejectedBy,
                "rejectedRole" to command.rejectedByRole,
                "rejectReason" to command.reason
            )
        )
        auditEvents.append(
            eventType = "AUDIT_EXPORT_REJECTED",
            actorType = "STAFF",
            actorId = command.rejectedBy,
            actorRole = command.rejectedByRole,
            screenId = SCREEN_ID,
            businessReferenceId = exportId,
            reason = command.reason,
            payload = mapOf("exportId" to exportId, "ledgerRowsMutated" to false, "syntheticOnly" to true)
        )
        return AuditExportJobResponse(job(exportId), replayed = false)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun viewExport(exportId: String, actorId: String, actorRole: String, reason: String?): AuditExportJobResponse {
        BankingLabAuthContext.requireActor(actorId, actorRole)
        validateRequester(actorId, actorRole)
        requireReason(reason)
        val dto = job(exportId)
        auditEvents.append(
            eventType = "AUDIT_EXPORT_VIEWED",
            actorType = "STAFF",
            actorId = actorId,
            actorRole = actorRole,
            screenId = SCREEN_ID,
            businessReferenceId = exportId,
            reason = reason,
            payload = mapOf(
                "exportId" to exportId,
                "status" to dto.status,
                "ledgerRowsMutated" to false,
                "syntheticOnly" to true
            )
        )
        return AuditExportJobResponse(dto, replayed = false)
    }

    private fun job(exportId: String): AuditExportJobDto =
        jdbc.query(
            jobSql("WHERE j.export_id = :exportId"),
            mapOf("exportId" to exportId)
        ) { rs, _ -> mapJob(rs) }.singleOrNull()
            ?: throw WorkflowErrors.notFound("audit export job not found: $exportId")

    private fun jobForUpdate(exportId: String): AuditExportJobDto =
        jdbc.query(
            jobSql("WHERE j.export_id = :exportId FOR UPDATE OF j"),
            mapOf("exportId" to exportId)
        ) { rs, _ -> mapJob(rs) }.singleOrNull()
            ?: throw WorkflowErrors.notFound("audit export job not found: $exportId")

    private fun findByIdempotencyKey(idempotencyKey: String): AuditExportJobRecord? =
        jdbc.query(
            """
            SELECT export_id, command_hash
            FROM audit_export_jobs
            WHERE idempotency_key = :idempotencyKey
            """.trimIndent(),
            mapOf("idempotencyKey" to idempotencyKey)
        ) { rs, _ -> AuditExportJobRecord(rs.getString("export_id"), rs.getString("command_hash")) }
            .singleOrNull()

    private fun auditRows(): List<AuditExportEventRow> =
        jdbc.query(
            """
            SELECT audit_event_id, event_type, actor_type, actor_id, actor_role,
                   screen_id, business_reference_id, customer_id, account_id, reason,
                   payload_hash, previous_event_hash, created_at
            FROM audit_events
            ORDER BY created_at ASC, audit_event_id ASC
            """.trimIndent(),
            emptyMap<String, Any?>()
        ) { rs, _ -> mapAuditRow(rs) }

    private fun jobSql(where: String): String =
        """
        SELECT j.export_id, j.status, j.requested_by, j.requested_role, j.reason, j.export_format,
               j.approval_id, j.requested_at, j.approved_by, j.approved_role, j.approved_at,
               j.rejected_by, j.rejected_role, j.rejected_at, j.reject_reason,
               j.from_audit_event_id, j.through_audit_event_id, j.row_count,
               j.hash_chain_start, j.hash_chain_end, j.payload_sha256, j.storage_uri,
               j.ledger_rows_mutated, j.synthetic_only,
               f.file_id, f.file_name, f.format AS file_format, f.row_count AS file_row_count,
               f.sha256 AS file_sha256, f.storage_uri AS file_storage_uri, f.created_at AS file_created_at,
               f.synthetic_only AS file_synthetic_only
        FROM audit_export_jobs j
        LEFT JOIN audit_export_files f ON f.export_id = j.export_id
        $where
        """.trimIndent()

    private fun mapJob(rs: ResultSet): AuditExportJobDto {
        val fileId = rs.getString("file_id")
        return AuditExportJobDto(
            exportId = rs.getString("export_id"),
            status = rs.getString("status"),
            requestedBy = rs.getString("requested_by"),
            requestedRole = rs.getString("requested_role"),
            reason = rs.getString("reason"),
            exportFormat = rs.getString("export_format"),
            approvalId = rs.getString("approval_id"),
            requestedAt = rs.getObject("requested_at", OffsetDateTime::class.java),
            approvedBy = rs.getString("approved_by"),
            approvedRole = rs.getString("approved_role"),
            approvedAt = rs.getObject("approved_at", OffsetDateTime::class.java),
            rejectedBy = rs.getString("rejected_by"),
            rejectedRole = rs.getString("rejected_role"),
            rejectedAt = rs.getObject("rejected_at", OffsetDateTime::class.java),
            rejectReason = rs.getString("reject_reason"),
            fromAuditEventId = rs.getString("from_audit_event_id"),
            throughAuditEventId = rs.getString("through_audit_event_id"),
            rowCount = rs.getInt("row_count"),
            hashChainStart = rs.getString("hash_chain_start"),
            hashChainEnd = rs.getString("hash_chain_end"),
            payloadSha256 = rs.getString("payload_sha256"),
            storageUri = rs.getString("storage_uri"),
            ledgerRowsMutated = rs.getBoolean("ledger_rows_mutated"),
            syntheticOnly = rs.getBoolean("synthetic_only"),
            file = if (fileId == null) {
                null
            } else {
                AuditExportFileDto(
                    fileId = fileId,
                    fileName = rs.getString("file_name"),
                    format = rs.getString("file_format"),
                    rowCount = rs.getInt("file_row_count"),
                    sha256 = rs.getString("file_sha256"),
                    storageUri = rs.getString("file_storage_uri"),
                    createdAt = rs.getObject("file_created_at", OffsetDateTime::class.java),
                    syntheticOnly = rs.getBoolean("file_synthetic_only")
                )
            }
        )
    }

    private fun mapAuditRow(rs: ResultSet): AuditExportEventRow =
        AuditExportEventRow(
            auditEventId = rs.getString("audit_event_id"),
            eventType = rs.getString("event_type"),
            actorType = rs.getString("actor_type"),
            actorId = rs.getString("actor_id"),
            actorRole = rs.getString("actor_role"),
            screenId = rs.getString("screen_id"),
            businessReferenceId = rs.getString("business_reference_id"),
            customerId = rs.getString("customer_id"),
            accountId = rs.getString("account_id"),
            reason = rs.getString("reason"),
            payloadHash = rs.getString("payload_hash"),
            previousEventHash = rs.getString("previous_event_hash"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
        )

    private fun requestCommandHash(command: AuditExportRequestCommand, format: String): String =
        sha256(
            objectMapper.writeValueAsString(
                mapOf(
                    "requestedBy" to command.requestedBy,
                    "requestedRole" to command.requestedRole,
                    "reason" to command.reason,
                    "exportFormat" to format
                )
            )
        )

    private fun normalizeFormat(format: String): String {
        val normalized = format.trim().uppercase()
        if (normalized != "NDJSON") {
            throw WorkflowErrors.validation("audit export format must be NDJSON")
        }
        return normalized
    }

    private fun validateRequester(actorId: String?, actorRole: String?) {
        requireNonBlank(actorId, "actorId")
        requireNonBlank(actorRole, "actorRole")
        if (actorRole !in ALLOWED_ROLES) {
            throw WorkflowErrors.authorizationViolation("audit export is limited to auditor or compliance roles")
        }
    }

    private fun requireReason(reason: String?) {
        if (reason.isNullOrBlank()) {
            throw WorkflowErrors.reasonRequired("audit export requires a business reason")
        }
    }

    private fun requireNonBlank(value: String?, field: String) {
        if (value.isNullOrBlank()) {
            throw WorkflowErrors.validation("$field is required")
        }
    }

    private fun idempotencyConflict(): BankingLabDomainException =
        BankingLabDomainException(
            code = "IDEMPOTENCY_KEY_CONFLICT",
            status = HttpStatus.CONFLICT,
            domain = "idempotency",
            invariant = "externally retried command must replay the same result",
            message = "idempotency key was reused for a different audit export command",
            causeText = "The submitted audit export command hash does not match the existing idempotency key.",
            fix = "Retry with the original request body or generate a new idempotency key for a different export."
        )

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object {
        const val SCREEN_ID = "AUD-501"
        val ALLOWED_ROLES = setOf("AUDITOR", "COMPLIANCE_MANAGER")
    }
}

private data class AuditExportJobRecord(
    val exportId: String,
    val commandHash: String
)

private data class AuditExportEventRow(
    val auditEventId: String,
    val eventType: String,
    val actorType: String,
    val actorId: String,
    val actorRole: String,
    val screenId: String?,
    val businessReferenceId: String?,
    val customerId: String?,
    val accountId: String?,
    val reason: String?,
    val payloadHash: String,
    val previousEventHash: String?,
    val createdAt: OffsetDateTime
) {
    fun toExportMap(): Map<String, Any?> =
        linkedMapOf(
            "auditEventId" to auditEventId,
            "eventType" to eventType,
            "actorType" to actorType,
            "actorId" to actorId,
            "actorRole" to actorRole,
            "screenId" to screenId,
            "businessReferenceId" to businessReferenceId,
            "customerId" to customerId,
            "accountId" to accountId,
            "reason" to reason,
            "payloadHash" to payloadHash,
            "previousEventHash" to previousEventHash,
            "createdAt" to createdAt.toString(),
            "syntheticOnly" to true
        )
}
