package lab.banking.core.aml

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import lab.banking.core.audit.AuditEventAppender
import lab.banking.core.common.BankingLabDomainException
import lab.banking.core.security.BankingLabAuthContext
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class AmlFdsGovernanceService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val auditEvents: AuditEventAppender,
    private val amlCases: AmlCaseService
) {
    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun screenCustomer(customerId: String, command: SanctionsScreenCustomerCommand): SanctionsScreeningResultDto {
        BankingLabAuthContext.requireActor(command.actorId, command.actorRole)
        requireNonBlank(command.reason, "reason")
        val customerName = jdbc.queryForObject(
            "SELECT customer_name FROM customers WHERE customer_id = :customerId",
            mapOf("customerId" to customerId),
            String::class.java
        ) ?: throw WorkflowErrors.notFound("customer not found: $customerId")
        return screen(
            customerId = customerId,
            transferReferenceId = null,
            value = customerName,
            matchType = "CUSTOMER_ONBOARDING",
            actorId = command.actorId,
            actorRole = command.actorRole,
            reason = command.reason
        )
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun screenTransfer(command: SanctionsScreenTransferCommand): SanctionsScreeningResultDto {
        BankingLabAuthContext.requireActor(command.actorId, command.actorRole)
        requireNonBlank(command.customerId, "customerId")
        requireNonBlank(command.transferReferenceId, "transferReferenceId")
        requireNonBlank(command.counterpartyName, "counterpartyName")
        requireNonBlank(command.reason, "reason")
        if (!customerExists(command.customerId)) {
            throw WorkflowErrors.notFound("customer not found: ${command.customerId}")
        }
        return screen(
            customerId = command.customerId,
            transferReferenceId = command.transferReferenceId,
            value = command.counterpartyName,
            matchType = "TRANSFER_COUNTERPARTY",
            actorId = command.actorId,
            actorRole = command.actorRole,
            reason = command.reason
        )
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun falsePositiveDisposition(hitId: String, command: FalsePositiveDispositionCommand): SanctionsScreeningHitDto {
        BankingLabAuthContext.requireActor(command.dispositionBy, command.dispositionByRole)
        requireNonBlank(command.reason, "reason")
        requireNonBlank(command.approvedBy, "approvedBy")
        requireNonBlank(command.approvedByRole, "approvedByRole")
        if (command.approvedByRole != "COMPLIANCE_MANAGER") {
            throw WorkflowErrors.authorizationViolation("sanctions false-positive disposition requires compliance manager approval")
        }
        if (command.dispositionBy == command.approvedBy) {
            throw BankingLabDomainException(
                code = "MAKER_CHECKER_SELF_APPROVAL_REJECTED",
                status = HttpStatus.CONFLICT,
                domain = "aml",
                policy = "SANCTIONS_FALSE_POSITIVE_DISPOSITION_SEPARATION_OF_DUTIES",
                message = "false-positive disposition approver must differ from the reviewer",
                causeText = "The same synthetic actor attempted to review and approve a sanctions false-positive disposition.",
                fix = "Use a different synthetic compliance approver for the disposition.",
                details = mapOf("hitId" to hitId)
            )
        }
        val hit = findHitForUpdate(hitId)
        if (hit.status != "OPEN") {
            throw WorkflowErrors.stateViolation("sanctions hit is not open: ${hit.status}")
        }
        jdbc.update(
            """
            UPDATE sanctions_screening_hits
            SET status = 'DISPOSITIONED',
                disposition = 'FALSE_POSITIVE',
                disposition_reason = :reason,
                disposition_by = :dispositionBy,
                approved_by = :approvedBy,
                dispositioned_at = now()
            WHERE hit_id = :hitId
            """.trimIndent(),
            mapOf(
                "hitId" to hitId,
                "reason" to command.reason,
                "dispositionBy" to command.dispositionBy,
                "approvedBy" to command.approvedBy
            )
        )
        auditEvents.append(
            eventType = "SANCTIONS_FALSE_POSITIVE_DISPOSITIONED",
            actorType = "STAFF",
            actorId = command.dispositionBy,
            actorRole = command.dispositionByRole,
            screenId = "AML-301",
            businessReferenceId = hitId,
            customerId = hit.customerId,
            reason = command.reason,
            payload = mapOf(
                "hitId" to hitId,
                "approvedBy" to command.approvedBy,
                "approvedByRole" to command.approvedByRole,
                "disposition" to "FALSE_POSITIVE",
                "syntheticOnly" to true
            )
        )
        return findHit(hitId)
    }

    @Transactional(readOnly = true)
    fun activeModelCard(): AmlModelCardDto =
        jdbc.query(
            """
            SELECT model_version_id, model_name, version_label, status,
                   features_json::text AS features_json,
                   score_distribution_json::text AS score_distribution_json,
                   drift_check_json::text AS drift_check_json,
                   explainability_json::text AS explainability_json,
                   training_data_boundary, created_at
            FROM aml_model_versions
            WHERE status = 'ACTIVE'
            ORDER BY created_at DESC
            LIMIT 1
            """.trimIndent(),
            emptyMap<String, Any?>(),
            this::mapModelCard
        ).firstOrNull() ?: throw WorkflowErrors.notFound("active AML model card not found")

    private fun screen(
        customerId: String,
        transferReferenceId: String?,
        value: String,
        matchType: String,
        actorId: String,
        actorRole: String,
        reason: String
    ): SanctionsScreeningResultDto {
        val matches = watchlistMatches(value)
        if (matches.isEmpty()) {
            auditEvents.append(
                eventType = "SANCTIONS_SCREENING_CLEAR",
                actorType = "STAFF",
                actorId = actorId,
                actorRole = actorRole,
                screenId = "AML-301",
                businessReferenceId = transferReferenceId ?: customerId,
                customerId = customerId,
                reason = reason,
                payload = mapOf("matchType" to matchType, "syntheticOnly" to true)
            )
            return SanctionsScreeningResultDto(hits = emptyList(), amlCase = null)
        }
        val topRisk = matches.maxOf { it.riskScore }
        val caseId = "AML-SAN-${UUID.randomUUID().toString().uppercase()}"
        val alerts = matches.map {
            mapOf(
                "ruleId" to "AML-${it.listType}-SCREENING",
                "message" to "Synthetic ${it.listType} screening hit for ${it.displayName}"
            )
        }
        jdbc.update(
            """
            INSERT INTO aml_cases (
              aml_case_id, customer_id, status, risk_score, alerts_json, str_simulation_json
            )
            VALUES (
              :caseId, :customerId, 'OPEN', :riskScore, CAST(:alertsJson AS jsonb),
              '{"reported":false,"disposition":"SANCTIONS_SCREENING_HIT"}'::jsonb
            )
            """.trimIndent(),
            mapOf(
                "caseId" to caseId,
                "customerId" to customerId,
                "riskScore" to topRisk,
                "alertsJson" to objectMapper.writeValueAsString(alerts)
            )
        )
        val hitIds = matches.map { entry ->
            val hitId = "SHT-${UUID.randomUUID().toString().uppercase()}"
            jdbc.update(
                """
                INSERT INTO sanctions_screening_hits (
                  hit_id, watchlist_entry_id, customer_id, transfer_reference_id, aml_case_id,
                  match_type, matched_value, risk_score, status, metadata_json
                )
                VALUES (
                  :hitId, :watchlistEntryId, :customerId, :transferReferenceId, :amlCaseId,
                  :matchType, :matchedValue, :riskScore, 'OPEN',
                  '{"syntheticOnly":true,"realSanctionsData":false}'::jsonb
                )
                """.trimIndent(),
                mapOf(
                    "hitId" to hitId,
                    "watchlistEntryId" to entry.watchlistEntryId,
                    "customerId" to customerId,
                    "transferReferenceId" to transferReferenceId,
                    "amlCaseId" to caseId,
                    "matchType" to matchType,
                    "matchedValue" to value,
                    "riskScore" to entry.riskScore
                )
            )
            hitId
        }
        auditEvents.append(
            eventType = "SANCTIONS_SCREENING_HIT",
            actorType = "STAFF",
            actorId = actorId,
            actorRole = actorRole,
            screenId = "AML-301",
            businessReferenceId = caseId,
            customerId = customerId,
            reason = reason,
            payload = mapOf(
                "caseId" to caseId,
                "hitCount" to hitIds.size,
                "matchType" to matchType,
                "syntheticOnly" to true,
                "realSanctionsData" to false
            )
        )
        return SanctionsScreeningResultDto(
            hits = hitIds.map(::findHit),
            amlCase = amlCases.find(caseId)
        )
    }

    private fun watchlistMatches(value: String): List<WatchlistEntry> {
        val normalized = normalize(value)
        return jdbc.query(
            """
            SELECT watchlist_entry_id, list_type, display_name, normalized_name, risk_score
            FROM synthetic_watchlist_entries
            WHERE status = 'ACTIVE'
              AND normalized_name = :normalized
            ORDER BY risk_score DESC
            """.trimIndent(),
            mapOf("normalized" to normalized)
        ) { rs, _ ->
            WatchlistEntry(
                watchlistEntryId = rs.getString("watchlist_entry_id"),
                listType = rs.getString("list_type"),
                displayName = rs.getString("display_name"),
                normalizedName = rs.getString("normalized_name"),
                riskScore = rs.getInt("risk_score")
            )
        }
    }

    private fun findHit(hitId: String): SanctionsScreeningHitDto =
        jdbc.query(HIT_SQL + " WHERE h.hit_id = :hitId", mapOf("hitId" to hitId), this::mapHit)
            .firstOrNull() ?: throw WorkflowErrors.notFound("sanctions screening hit not found: $hitId")

    private fun findHitForUpdate(hitId: String): SanctionsScreeningHitDto =
        jdbc.query(HIT_SQL + " WHERE h.hit_id = :hitId FOR UPDATE", mapOf("hitId" to hitId), this::mapHit)
            .firstOrNull() ?: throw WorkflowErrors.notFound("sanctions screening hit not found: $hitId")

    private fun customerExists(customerId: String): Boolean =
        (jdbc.queryForObject(
            "SELECT count(*) FROM customers WHERE customer_id = :customerId",
            mapOf("customerId" to customerId),
            Int::class.java
        ) ?: 0) > 0

    private fun mapHit(rs: ResultSet, rowNum: Int): SanctionsScreeningHitDto =
        SanctionsScreeningHitDto(
            hitId = rs.getString("hit_id"),
            watchlistEntryId = rs.getString("watchlist_entry_id"),
            listType = rs.getString("list_type"),
            displayName = rs.getString("display_name"),
            customerId = rs.getString("customer_id"),
            transferReferenceId = rs.getString("transfer_reference_id"),
            amlCaseId = rs.getString("aml_case_id"),
            matchType = rs.getString("match_type"),
            matchedValue = rs.getString("matched_value"),
            riskScore = rs.getInt("risk_score"),
            status = rs.getString("status"),
            disposition = rs.getString("disposition"),
            dispositionReason = rs.getString("disposition_reason"),
            dispositionBy = rs.getString("disposition_by"),
            approvedBy = rs.getString("approved_by"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            dispositionedAt = rs.getObject("dispositioned_at", OffsetDateTime::class.java)
        )

    private fun mapModelCard(rs: ResultSet, rowNum: Int): AmlModelCardDto =
        AmlModelCardDto(
            modelVersionId = rs.getString("model_version_id"),
            modelName = rs.getString("model_name"),
            versionLabel = rs.getString("version_label"),
            status = rs.getString("status"),
            features = objectMapper.readValue(rs.getString("features_json"), List::class.java)
                .mapNotNull { it?.toString() },
            scoreDistribution = objectMapper.readValue(rs.getString("score_distribution_json"), Map::class.java)
                .mapKeys { it.key.toString() }
                .mapValues { (it.value as Number).toInt() },
            driftCheck = objectMapper.readValue(rs.getString("drift_check_json"), Map::class.java)
                .mapKeys { it.key.toString() },
            explainability = objectMapper.readValue(rs.getString("explainability_json"), Map::class.java)
                .mapKeys { it.key.toString() },
            trainingDataBoundary = rs.getString("training_data_boundary"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
        )

    private fun requireNonBlank(value: String?, field: String) {
        if (value.isNullOrBlank()) {
            throw WorkflowErrors.validation("$field is required")
        }
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private data class WatchlistEntry(
        val watchlistEntryId: String,
        val listType: String,
        val displayName: String,
        val normalizedName: String,
        val riskScore: Int
    )

    private companion object {
        const val HIT_SQL = """
            SELECT h.hit_id, h.watchlist_entry_id, w.list_type, w.display_name,
                   h.customer_id, h.transfer_reference_id, h.aml_case_id,
                   h.match_type, h.matched_value, h.risk_score, h.status,
                   h.disposition, h.disposition_reason, h.disposition_by,
                   h.approved_by, h.created_at, h.dispositioned_at
            FROM sanctions_screening_hits h
            JOIN synthetic_watchlist_entries w ON w.watchlist_entry_id = h.watchlist_entry_id
        """
    }
}
