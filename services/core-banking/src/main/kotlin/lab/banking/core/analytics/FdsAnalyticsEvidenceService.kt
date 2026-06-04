package lab.banking.core.analytics

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import lab.banking.core.audit.AuditEventAppender
import lab.banking.core.security.BankingLabAuthContext
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class FdsAnalyticsEvidenceService(
    private val objectMapper: ObjectMapper,
    private val auditEvents: AuditEventAppender,
    @param:Value("\${banking-lab.analytics.fds-evidence-path:docs/test-evidence/generated/fds-aml-analytics.json}")
    private val configuredEvidencePath: String
) {
    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun evidence(reason: String?): FdsAnalyticsEvidenceDto {
        val viewReason = reason?.takeIf { it.isNotBlank() }
            ?: throw WorkflowErrors.reasonRequired("FDS analytics evidence view requires a business reason")
        val actor = BankingLabAuthContext.get()
        if (actor != null && !actor.hasAnyRole(ALLOWED_ROLES)) {
            throw WorkflowErrors.authorizationViolation("actor role cannot view FDS analytics evidence")
        }
        val root = objectMapper.readTree(Files.readString(evidencePath()))
        validateControls(root)
        val results = root.path("results").map(::mapResult)
        if (results.any { !it.syntheticOnly }) {
            throw WorkflowErrors.stateViolation("FDS analytics artifact contains non-synthetic result rows")
        }
        val alertCounts = root.path("alertCounts")
            .fields()
            .asSequence()
            .associate { it.key to it.value.asInt() }
        val highestRisk = results.maxByOrNull { it.totalScore }
        val auditEventId = auditEvents.append(
            eventType = "FDS_ANALYTICS_EVIDENCE_VIEW",
            actorType = "STAFF",
            actorId = actor?.subject ?: "system",
            actorRole = actor?.roles?.sorted()?.firstOrNull(ALLOWED_ROLES::contains) ?: "SYSTEM",
            screenId = "FDS-301",
            businessReferenceId = "FDS-AML-ANALYTICS",
            reason = viewReason,
            payload = mapOf(
                "engine" to root.path("engine").asText(),
                "scoredTransactions" to results.size,
                "highRiskResults" to results.count { it.riskBand == "HIGH" },
                "syntheticOnly" to true
            )
        )
        return FdsAnalyticsEvidenceDto(
            engine = root.path("engine").asText(),
            generatedAt = root.path("generatedAt").asText(),
            controls = root.path("controls")
                .fields()
                .asSequence()
                .associate { it.key to it.value.asBoolean() },
            scoredTransactions = results.size,
            highRiskResults = results.count { it.riskBand == "HIGH" },
            alertCounts = alertCounts,
            highestRisk = highestRisk,
            auditEventId = auditEventId
        )
    }

    private fun evidencePath(): Path =
        generateSequence(Paths.get("").toAbsolutePath()) { it.parent }
            .map { it.resolve(configuredEvidencePath) }
            .firstOrNull(Files::isRegularFile)
            ?: throw WorkflowErrors.notFound("FDS analytics evidence artifact not found: $configuredEvidencePath")

    private fun validateControls(root: JsonNode) {
        val controls = root.path("controls")
        if (
            controls.path("realMoneyUsed").asBoolean(true) ||
            controls.path("realPiiUsed").asBoolean(true) ||
            controls.path("realBankNetworkUsed").asBoolean(true)
        ) {
            throw WorkflowErrors.stateViolation("FDS analytics artifact violates synthetic-only controls")
        }
        if (!controls.path("deterministicScoring").asBoolean(false) || !controls.path("duckdbMartGenerated").asBoolean(false)) {
            throw WorkflowErrors.stateViolation("FDS analytics artifact is missing deterministic DuckDB evidence")
        }
    }

    private fun mapResult(node: JsonNode): FdsAnalyticsResultDto =
        FdsAnalyticsResultDto(
            transactionId = node.path("transactionId").asText(),
            customerId = node.path("customerId").asText(),
            riskBand = node.path("riskBand").asText(),
            totalScore = node.path("totalScore").asInt(),
            alerts = node.path("alerts").map { it.asText() },
            syntheticOnly = node.path("syntheticOnly").asBoolean(false)
        )

    private companion object {
        val ALLOWED_ROLES = setOf("FDS_REVIEWER", "AML_REVIEWER", "COMPLIANCE_MANAGER", "AUDITOR")
    }
}
