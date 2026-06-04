package lab.banking.core.opsec

import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import lab.banking.core.audit.AuditEventAppender
import lab.banking.core.common.BankingLabDomainException
import lab.banking.core.security.BankingLabAuthContext
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class OperationalSecurityService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val auditEvents: AuditEventAppender
) {
    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun exportAuditSegment(command: AuditWormExportCommand): AuditWormSegmentDto {
        BankingLabAuthContext.requireActor(command.exportedBy, command.exportedRole)
        requireNonBlank(command.exportedBy, "exportedBy")
        requireNonBlank(command.exportedRole, "exportedRole")
        requireNonBlank(command.reason, "reason")

        val previous = previousSegment()
        val events = auditEventsAfter(previous?.throughAuditEventId)
        if (events.isEmpty()) {
            throw opsecValidation(
                code = "AUDIT_WORM_NO_EVENTS",
                message = "no audit events are available for a new WORM segment",
                fix = "Append at least one synthetic audit event before exporting a sealed segment."
            )
        }

        val key = activeKey("AUDIT_WORM_ANCHOR")
        val segmentNo = (previous?.segmentNo ?: 0L) + 1L
        val payloadHash = payloadHash(events)
        val segmentHash = segmentHash(
            segmentNo = segmentNo,
            previousAnchorHash = previous?.segmentHash,
            segmentPayloadHash = payloadHash,
            signingKeyHash = key.syntheticKeyHash,
            eventCount = events.size
        )
        val segmentId = "WORM-${UUID.randomUUID().toString().uppercase()}"
        val storageUri = "worm://synthetic/audit/segments/$segmentId.json"
        jdbc.update(
            """
            INSERT INTO audit_worm_export_segments (
              segment_id, segment_no, from_audit_event_id, through_audit_event_id, audit_event_ids,
              event_count, previous_anchor_hash, segment_payload_hash, segment_hash, signing_key_id,
              exported_by, exported_role, export_reason, storage_uri, status, metadata_json
            )
            VALUES (
              :segmentId, :segmentNo, :fromAuditEventId, :throughAuditEventId, CAST(:auditEventIds AS jsonb),
              :eventCount, :previousAnchorHash, :segmentPayloadHash, :segmentHash, :signingKeyId,
              :exportedBy, :exportedRole, :exportReason, :storageUri, 'SEALED',
              '{"syntheticOnly":true,"wormSimulator":true}'::jsonb
            )
            """.trimIndent(),
            mapOf(
                "segmentId" to segmentId,
                "segmentNo" to segmentNo,
                "fromAuditEventId" to events.first().auditEventId,
                "throughAuditEventId" to events.last().auditEventId,
                "auditEventIds" to objectMapper.writeValueAsString(events.map { it.auditEventId }),
                "eventCount" to events.size,
                "previousAnchorHash" to previous?.segmentHash,
                "segmentPayloadHash" to payloadHash,
                "segmentHash" to segmentHash,
                "signingKeyId" to key.keyId,
                "exportedBy" to command.exportedBy,
                "exportedRole" to command.exportedRole,
                "exportReason" to command.reason,
                "storageUri" to storageUri
            )
        )
        auditEvents.append(
            eventType = "AUDIT_WORM_SEGMENT_EXPORTED",
            actorType = "STAFF",
            actorId = command.exportedBy,
            actorRole = command.exportedRole,
            screenId = "OPS-SEC-101",
            businessReferenceId = segmentId,
            reason = command.reason,
            payload = mapOf(
                "segmentId" to segmentId,
                "segmentNo" to segmentNo,
                "eventCount" to events.size,
                "signingKeyId" to key.keyId,
                "syntheticOnly" to true
            )
        )
        return segment(segmentId)
    }

    @Transactional(readOnly = true)
    fun verifyAuditSegments(): AuditWormVerificationResult {
        val segments = jdbc.query(
            """
            SELECT s.segment_id, s.segment_no, s.from_audit_event_id, s.through_audit_event_id,
                   s.audit_event_ids, s.event_count, s.previous_anchor_hash, s.segment_payload_hash,
                   s.segment_hash, s.signing_key_id, s.exported_by, s.exported_role, s.export_reason,
                   s.storage_uri, s.status, s.exported_at, k.synthetic_key_hash
            FROM audit_worm_export_segments s
            JOIN synthetic_kms_keys k ON k.key_id = s.signing_key_id
            ORDER BY s.segment_no ASC
            """.trimIndent(),
            emptyMap<String, Any?>()
        ) { rs, _ -> mapSegmentVerificationRow(rs) }
        val failures = mutableListOf<String>()
        var previousAnchor: String? = null
        segments.forEach { segment ->
            if (segment.previousAnchorHash != previousAnchor) {
                failures += "${segment.segmentId}: previous anchor mismatch"
            }
            val eventIds = objectMapper.readValue(segment.auditEventIdsJson, List::class.java)
                .mapNotNull { it?.toString() }
            if (eventIds.size != segment.eventCount) {
                failures += "${segment.segmentId}: event count does not match stored event id list"
            }
            val events = auditEventsByIds(eventIds)
            if (events.size != eventIds.size) {
                failures += "${segment.segmentId}: referenced audit event is missing"
            }
            val recalculatedPayloadHash = payloadHash(events)
            if (recalculatedPayloadHash != segment.segmentPayloadHash) {
                failures += "${segment.segmentId}: payload hash mismatch"
            }
            val recalculatedSegmentHash = segmentHash(
                segmentNo = segment.segmentNo,
                previousAnchorHash = segment.previousAnchorHash,
                segmentPayloadHash = recalculatedPayloadHash,
                signingKeyHash = segment.signingKeyHash,
                eventCount = segment.eventCount
            )
            if (recalculatedSegmentHash != segment.segmentHash) {
                failures += "${segment.segmentId}: segment hash mismatch"
            }
            previousAnchor = segment.segmentHash
        }
        return AuditWormVerificationResult(
            valid = failures.isEmpty(),
            checkedSegments = segments.size,
            failures = failures
        )
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun rotateSyntheticKey(command: RotateSyntheticKeyCommand): KmsRotationResult {
        BankingLabAuthContext.requireActor(command.rotatedBy, command.rotatedByRole)
        requireNonBlank(command.purpose, "purpose")
        requireNonBlank(command.rotatedBy, "rotatedBy")
        requireNonBlank(command.rotatedByRole, "rotatedByRole")
        requireNonBlank(command.reason, "reason")
        val oldKey = activeKey(command.purpose)
        val newVersion = oldKey.keyVersion + 1
        val newKeyId = "KMS-SYN-${command.purpose.replace("_", "-")}-V$newVersion"
        val newKeyHash = sha256("synthetic:${command.purpose}:$newVersion:${command.reason}:${oldKey.syntheticKeyHash}")
        jdbc.update(
            """
            UPDATE synthetic_kms_keys
            SET status = 'RETIRED', retired_at = now()
            WHERE key_id = :oldKeyId
            """.trimIndent(),
            mapOf("oldKeyId" to oldKey.keyId)
        )
        jdbc.update(
            """
            INSERT INTO synthetic_kms_keys (
              key_id, purpose, key_version, status, key_material_ref, synthetic_key_hash,
              rotated_from_key_id, metadata_json
            )
            VALUES (
              :keyId, :purpose, :keyVersion, 'ACTIVE', :keyMaterialRef, :syntheticKeyHash,
              :rotatedFromKeyId, '{"syntheticOnly":true,"material":"not-real-key-material"}'::jsonb
            )
            """.trimIndent(),
            mapOf(
                "keyId" to newKeyId,
                "purpose" to command.purpose,
                "keyVersion" to newVersion,
                "keyMaterialRef" to "synthetic://kms/${command.purpose.lowercase().replace("_", "-")}/v$newVersion",
                "syntheticKeyHash" to newKeyHash,
                "rotatedFromKeyId" to oldKey.keyId
            )
        )
        auditEvents.append(
            eventType = "SYNTHETIC_KMS_KEY_ROTATED",
            actorType = "STAFF",
            actorId = command.rotatedBy,
            actorRole = command.rotatedByRole,
            screenId = "OPS-SEC-102",
            businessReferenceId = newKeyId,
            reason = command.reason,
            payload = mapOf(
                "purpose" to command.purpose,
                "oldKeyId" to oldKey.keyId,
                "newKeyId" to newKeyId,
                "syntheticOnly" to true,
                "realKeyMaterialStored" to false
            )
        )
        return KmsRotationResult(
            oldKey = oldKey.copy(status = "RETIRED", retiredAt = OffsetDateTime.now(ZoneOffset.UTC)),
            newKey = key(newKeyId),
            auditWormVerification = verifyAuditSegments()
        )
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun requestBreakGlass(command: BreakGlassGrantCommand): BreakGlassGrantDto {
        BankingLabAuthContext.requireActor(command.operatorId, command.operatorRole)
        requireNonBlank(command.operatorId, "operatorId")
        requireNonBlank(command.operatorRole, "operatorRole")
        requireNonBlank(command.elevatedRole, "elevatedRole")
        requireNonBlank(command.reason, "reason")
        if (command.durationMinutes <= 0 || command.durationMinutes > 120) {
            throw opsecValidation(
                code = "BREAK_GLASS_DURATION_INVALID",
                message = "break-glass duration must be between 1 and 120 minutes",
                fix = "Use the shortest synthetic break-glass duration that can complete the emergency task."
            )
        }
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val expiresAt = now.plusMinutes(command.durationMinutes)
        val reviewCaseId = "BGR-${UUID.randomUUID().toString().uppercase()}"
        val grantId = "BG-${UUID.randomUUID().toString().uppercase()}"
        jdbc.update(
            """
            INSERT INTO break_glass_review_cases (
              review_case_id, status, opened_at, due_at, metadata_json
            )
            VALUES (
              :reviewCaseId, 'OPEN', :openedAt, :dueAt,
              '{"syntheticOnly":true,"postHocReviewRequired":true}'::jsonb
            )
            """.trimIndent(),
            mapOf(
                "reviewCaseId" to reviewCaseId,
                "openedAt" to now,
                "dueAt" to expiresAt.plusHours(24)
            )
        )
        val auditEventId = auditEvents.append(
            eventType = "BREAK_GLASS_GRANTED",
            actorType = "STAFF",
            actorId = command.operatorId,
            actorRole = command.operatorRole,
            screenId = "OPS-SEC-103",
            businessReferenceId = grantId,
            reason = command.reason,
            payload = mapOf(
                "grantId" to grantId,
                "elevatedRole" to command.elevatedRole,
                "expiresAt" to expiresAt.toString(),
                "reviewCaseId" to reviewCaseId,
                "syntheticOnly" to true
            )
        )
        jdbc.update(
            """
            INSERT INTO break_glass_grants (
              grant_id, operator_id, operator_role, elevated_role, reason, status,
              requested_at, expires_at, review_case_id, audit_event_id, metadata_json
            )
            VALUES (
              :grantId, :operatorId, :operatorRole, :elevatedRole, :reason, 'ACTIVE',
              :requestedAt, :expiresAt, :reviewCaseId, :auditEventId,
              '{"syntheticOnly":true,"breakGlassSimulator":true}'::jsonb
            )
            """.trimIndent(),
            mapOf(
                "grantId" to grantId,
                "operatorId" to command.operatorId,
                "operatorRole" to command.operatorRole,
                "elevatedRole" to command.elevatedRole,
                "reason" to command.reason,
                "requestedAt" to now,
                "expiresAt" to expiresAt,
                "reviewCaseId" to reviewCaseId,
                "auditEventId" to auditEventId
            )
        )
        return breakGlassGrant(grantId)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun expireElapsedBreakGlassGrants(command: ExpireBreakGlassGrantsCommand): ExpireBreakGlassGrantsResult {
        BankingLabAuthContext.requireActor(command.expiredBy, command.expiredByRole)
        requireNonBlank(command.expiredBy, "expiredBy")
        requireNonBlank(command.expiredByRole, "expiredByRole")
        requireNonBlank(command.reason, "reason")
        val expired = jdbc.update(
            """
            UPDATE break_glass_grants
            SET status = 'EXPIRED'
            WHERE status = 'ACTIVE'
              AND expires_at <= now()
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        auditEvents.append(
            eventType = "BREAK_GLASS_EXPIRE_SWEEP",
            actorType = "STAFF",
            actorId = command.expiredBy,
            actorRole = command.expiredByRole,
            screenId = "OPS-SEC-103",
            businessReferenceId = "BREAK-GLASS-EXPIRE-SWEEP",
            reason = command.reason,
            payload = mapOf("expiredCount" to expired, "syntheticOnly" to true)
        )
        return ExpireBreakGlassGrantsResult(expiredCount = expired)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun closeBreakGlassReview(reviewCaseId: String, command: CloseBreakGlassReviewCommand): BreakGlassGrantDto {
        BankingLabAuthContext.requireActor(command.reviewedBy, command.reviewedByRole)
        requireNonBlank(reviewCaseId, "reviewCaseId")
        requireNonBlank(command.reviewedBy, "reviewedBy")
        requireNonBlank(command.reviewedByRole, "reviewedByRole")
        requireNonBlank(command.reviewReason, "reviewReason")
        val grant = breakGlassGrantForUpdate(reviewCaseId)
        if (grant.operatorId == command.reviewedBy) {
            throw BankingLabDomainException(
                code = "MAKER_CHECKER_SELF_APPROVAL_REJECTED",
                status = HttpStatus.CONFLICT,
                domain = "opsec",
                policy = "BREAK_GLASS_POST_REVIEW_SEPARATION_OF_DUTIES",
                message = "break-glass operator cannot close the post-hoc review case",
                causeText = "The same synthetic operator requested emergency elevation and attempted to review it.",
                fix = "Assign a different synthetic compliance or operations manager to close the break-glass review.",
                details = mapOf("reviewCaseId" to reviewCaseId, "grantId" to grant.grantId)
            )
        }
        jdbc.update(
            """
            UPDATE break_glass_review_cases
            SET status = 'CLOSED',
                reviewer_id = :reviewerId,
                reviewer_role = :reviewerRole,
                review_reason = :reviewReason,
                reviewed_at = now()
            WHERE review_case_id = :reviewCaseId
              AND status = 'OPEN'
            """.trimIndent(),
            mapOf(
                "reviewerId" to command.reviewedBy,
                "reviewerRole" to command.reviewedByRole,
                "reviewReason" to command.reviewReason,
                "reviewCaseId" to reviewCaseId
            )
        )
        jdbc.update(
            """
            UPDATE break_glass_grants
            SET status = 'REVIEWED'
            WHERE review_case_id = :reviewCaseId
            """.trimIndent(),
            mapOf("reviewCaseId" to reviewCaseId)
        )
        auditEvents.append(
            eventType = "BREAK_GLASS_REVIEW_CLOSED",
            actorType = "STAFF",
            actorId = command.reviewedBy,
            actorRole = command.reviewedByRole,
            screenId = "OPS-SEC-103",
            businessReferenceId = grant.grantId,
            reason = command.reviewReason,
            payload = mapOf(
                "grantId" to grant.grantId,
                "reviewCaseId" to reviewCaseId,
                "operatorId" to grant.operatorId,
                "syntheticOnly" to true
            )
        )
        return breakGlassGrant(grant.grantId)
    }

    private fun auditEventsAfter(previousThroughAuditEventId: String?): List<AuditEventAnchorRow> {
        if (previousThroughAuditEventId == null) {
            return jdbc.query(
                """
                SELECT audit_event_id, payload_hash, previous_event_hash, created_at
                FROM audit_events
                ORDER BY created_at ASC, audit_event_id ASC
                """.trimIndent(),
                emptyMap<String, Any?>()
            ) { rs, _ -> mapAuditEventAnchor(rs) }
        }
        return jdbc.query(
            """
            WITH previous AS (
              SELECT created_at, audit_event_id
              FROM audit_events
              WHERE audit_event_id = :previousThroughAuditEventId
            )
            SELECT e.audit_event_id, e.payload_hash, e.previous_event_hash, e.created_at
            FROM audit_events e, previous p
            WHERE (e.created_at, e.audit_event_id) > (p.created_at, p.audit_event_id)
            ORDER BY e.created_at ASC, e.audit_event_id ASC
            """.trimIndent(),
            mapOf("previousThroughAuditEventId" to previousThroughAuditEventId)
        ) { rs, _ -> mapAuditEventAnchor(rs) }
    }

    private fun auditEventsByIds(eventIds: List<String>): List<AuditEventAnchorRow> {
        if (eventIds.isEmpty()) {
            return emptyList()
        }
        return jdbc.query(
            """
            SELECT audit_event_id, payload_hash, previous_event_hash, created_at
            FROM audit_events
            WHERE audit_event_id IN (:eventIds)
            ORDER BY created_at ASC, audit_event_id ASC
            """.trimIndent(),
            mapOf("eventIds" to eventIds)
        ) { rs, _ -> mapAuditEventAnchor(rs) }
    }

    private fun previousSegment(): SegmentPointer? =
        jdbc.query(
            """
            SELECT segment_no, segment_hash, through_audit_event_id
            FROM audit_worm_export_segments
            ORDER BY segment_no DESC
            LIMIT 1
            """.trimIndent(),
            emptyMap<String, Any?>()
        ) { rs, _ ->
            SegmentPointer(
                segmentNo = rs.getLong("segment_no"),
                segmentHash = rs.getString("segment_hash"),
                throughAuditEventId = rs.getString("through_audit_event_id")
            )
        }.firstOrNull()

    private fun activeKey(purpose: String): SyntheticKmsKeyDto =
        jdbc.query(
            """
            SELECT key_id, purpose, key_version, status, key_material_ref, synthetic_key_hash,
                   activated_at, retired_at, rotated_from_key_id
            FROM synthetic_kms_keys
            WHERE purpose = :purpose
              AND status = 'ACTIVE'
            FOR UPDATE
            """.trimIndent(),
            mapOf("purpose" to purpose),
            this::mapKmsKey
        ).firstOrNull()
            ?: throw opsecValidation(
                code = "SYNTHETIC_KMS_ACTIVE_KEY_MISSING",
                message = "active synthetic KMS key is missing for $purpose",
                fix = "Restore the seeded synthetic key or rotate from an active key before running the operation."
            )

    private fun key(keyId: String): SyntheticKmsKeyDto =
        jdbc.query(
            """
            SELECT key_id, purpose, key_version, status, key_material_ref, synthetic_key_hash,
                   activated_at, retired_at, rotated_from_key_id
            FROM synthetic_kms_keys
            WHERE key_id = :keyId
            """.trimIndent(),
            mapOf("keyId" to keyId),
            this::mapKmsKey
        ).firstOrNull()
            ?: throw opsecValidation(
                code = "SYNTHETIC_KMS_KEY_NOT_FOUND",
                message = "synthetic KMS key not found: $keyId",
                fix = "Use a key id generated by the synthetic KMS rotation service."
            )

    private fun segment(segmentId: String): AuditWormSegmentDto =
        jdbc.query(
            """
            SELECT segment_id, segment_no, from_audit_event_id, through_audit_event_id,
                   event_count, previous_anchor_hash, segment_payload_hash, segment_hash,
                   signing_key_id, exported_by, exported_role, export_reason, storage_uri,
                   status, exported_at
            FROM audit_worm_export_segments
            WHERE segment_id = :segmentId
            """.trimIndent(),
            mapOf("segmentId" to segmentId),
            this::mapSegment
        ).firstOrNull()
            ?: throw opsecValidation(
                code = "AUDIT_WORM_SEGMENT_NOT_FOUND",
                message = "audit WORM segment not found: $segmentId",
                fix = "Use a segment id returned by the export command."
            )

    private fun breakGlassGrant(grantId: String): BreakGlassGrantDto =
        jdbc.query(
            BREAK_GLASS_SELECT + " WHERE g.grant_id = :grantId",
            mapOf("grantId" to grantId),
            this::mapBreakGlassGrant
        ).firstOrNull()
            ?: throw opsecValidation(
                code = "BREAK_GLASS_GRANT_NOT_FOUND",
                message = "break-glass grant not found: $grantId",
                fix = "Use a grant id returned by the break-glass request command."
            )

    private fun breakGlassGrantForUpdate(reviewCaseId: String): BreakGlassGrantDto =
        jdbc.query(
            BREAK_GLASS_SELECT + " WHERE g.review_case_id = :reviewCaseId FOR UPDATE",
            mapOf("reviewCaseId" to reviewCaseId),
            this::mapBreakGlassGrant
        ).firstOrNull()
            ?: throw opsecValidation(
                code = "BREAK_GLASS_REVIEW_NOT_FOUND",
                message = "break-glass review case not found: $reviewCaseId",
                fix = "Use a review case id returned by the break-glass request command."
            )

    private fun payloadHash(events: List<AuditEventAnchorRow>): String =
        sha256(
            events.joinToString("|") {
                "${it.auditEventId}:${it.payloadHash}:${it.previousEventHash.orEmpty()}:${it.createdAt.toInstant()}"
            }
        )

    private fun segmentHash(
        segmentNo: Long,
        previousAnchorHash: String?,
        segmentPayloadHash: String,
        signingKeyHash: String,
        eventCount: Int
    ): String =
        sha256("$segmentNo:${previousAnchorHash.orEmpty()}:$segmentPayloadHash:$signingKeyHash:$eventCount")

    private fun mapAuditEventAnchor(rs: ResultSet): AuditEventAnchorRow =
        AuditEventAnchorRow(
            auditEventId = rs.getString("audit_event_id"),
            payloadHash = rs.getString("payload_hash"),
            previousEventHash = rs.getString("previous_event_hash"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
        )

    private fun mapSegment(rs: ResultSet, rowNum: Int): AuditWormSegmentDto =
        AuditWormSegmentDto(
            segmentId = rs.getString("segment_id"),
            segmentNo = rs.getLong("segment_no"),
            fromAuditEventId = rs.getString("from_audit_event_id"),
            throughAuditEventId = rs.getString("through_audit_event_id"),
            eventCount = rs.getInt("event_count"),
            previousAnchorHash = rs.getString("previous_anchor_hash"),
            segmentPayloadHash = rs.getString("segment_payload_hash"),
            segmentHash = rs.getString("segment_hash"),
            signingKeyId = rs.getString("signing_key_id"),
            exportedBy = rs.getString("exported_by"),
            exportedRole = rs.getString("exported_role"),
            exportReason = rs.getString("export_reason"),
            storageUri = rs.getString("storage_uri"),
            status = rs.getString("status"),
            exportedAt = rs.getObject("exported_at", OffsetDateTime::class.java)
        )

    private fun mapSegmentVerificationRow(rs: ResultSet): SegmentVerificationRow =
        SegmentVerificationRow(
            segmentId = rs.getString("segment_id"),
            segmentNo = rs.getLong("segment_no"),
            auditEventIdsJson = rs.getString("audit_event_ids"),
            eventCount = rs.getInt("event_count"),
            previousAnchorHash = rs.getString("previous_anchor_hash"),
            segmentPayloadHash = rs.getString("segment_payload_hash"),
            segmentHash = rs.getString("segment_hash"),
            signingKeyHash = rs.getString("synthetic_key_hash")
        )

    private fun mapKmsKey(rs: ResultSet, rowNum: Int): SyntheticKmsKeyDto =
        SyntheticKmsKeyDto(
            keyId = rs.getString("key_id"),
            purpose = rs.getString("purpose"),
            keyVersion = rs.getInt("key_version"),
            status = rs.getString("status"),
            keyMaterialRef = rs.getString("key_material_ref"),
            syntheticKeyHash = rs.getString("synthetic_key_hash"),
            activatedAt = rs.getObject("activated_at", OffsetDateTime::class.java),
            retiredAt = rs.getObject("retired_at", OffsetDateTime::class.java),
            rotatedFromKeyId = rs.getString("rotated_from_key_id")
        )

    private fun mapBreakGlassGrant(rs: ResultSet, rowNum: Int): BreakGlassGrantDto =
        BreakGlassGrantDto(
            grantId = rs.getString("grant_id"),
            operatorId = rs.getString("operator_id"),
            operatorRole = rs.getString("operator_role"),
            elevatedRole = rs.getString("elevated_role"),
            reason = rs.getString("reason"),
            status = rs.getString("grant_status"),
            requestedAt = rs.getObject("requested_at", OffsetDateTime::class.java),
            expiresAt = rs.getObject("expires_at", OffsetDateTime::class.java),
            reviewCase = BreakGlassReviewCaseDto(
                reviewCaseId = rs.getString("review_case_id"),
                status = rs.getString("review_status"),
                openedAt = rs.getObject("opened_at", OffsetDateTime::class.java),
                dueAt = rs.getObject("due_at", OffsetDateTime::class.java),
                reviewerId = rs.getString("reviewer_id"),
                reviewerRole = rs.getString("reviewer_role"),
                reviewReason = rs.getString("review_reason"),
                reviewedAt = rs.getObject("reviewed_at", OffsetDateTime::class.java)
            ),
            auditEventId = rs.getString("audit_event_id")
        )

    private fun requireNonBlank(value: String?, field: String) {
        if (value.isNullOrBlank()) {
            throw opsecValidation(
                code = "OPERATIONAL_SECURITY_VALIDATION_FAILED",
                message = "$field is required",
                fix = "Provide $field in the synthetic operational-security command."
            )
        }
    }

    private fun opsecValidation(code: String, message: String, fix: String): BankingLabDomainException =
        BankingLabDomainException(
            code = code,
            status = HttpStatus.BAD_REQUEST,
            domain = "opsec",
            invariant = "synthetic operational-security control must be explicit and auditable",
            message = message,
            causeText = "The H5 operational-security simulator rejected an incomplete or inconsistent command.",
            fix = fix
        )

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private data class AuditEventAnchorRow(
        val auditEventId: String,
        val payloadHash: String,
        val previousEventHash: String?,
        val createdAt: OffsetDateTime
    )

    private data class SegmentPointer(
        val segmentNo: Long,
        val segmentHash: String,
        val throughAuditEventId: String?
    )

    private data class SegmentVerificationRow(
        val segmentId: String,
        val segmentNo: Long,
        val auditEventIdsJson: String,
        val eventCount: Int,
        val previousAnchorHash: String?,
        val segmentPayloadHash: String,
        val segmentHash: String,
        val signingKeyHash: String
    )

    private companion object {
        const val BREAK_GLASS_SELECT = """
            SELECT g.grant_id, g.operator_id, g.operator_role, g.elevated_role,
                   g.reason, g.status AS grant_status, g.requested_at, g.expires_at,
                   g.review_case_id, g.audit_event_id,
                   r.status AS review_status, r.opened_at, r.due_at, r.reviewer_id,
                   r.reviewer_role, r.review_reason, r.reviewed_at
            FROM break_glass_grants g
            JOIN break_glass_review_cases r ON r.review_case_id = g.review_case_id
        """
    }
}
