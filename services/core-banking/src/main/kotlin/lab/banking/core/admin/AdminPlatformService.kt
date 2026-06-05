package lab.banking.core.admin

import java.time.OffsetDateTime
import lab.banking.core.audit.AuditEventAppender
import lab.banking.core.config.BankingLabProperties
import lab.banking.core.security.BankingLabAuthContext
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class AdminPlatformService(
    private val properties: BankingLabProperties,
    private val auditEvents: AuditEventAppender
) {
    fun summary(): AdminPlatformControlSummary = AdminPlatformControlSummary(
        syntheticOnly = properties.syntheticOnly,
        nodeReferenceRuntimeRetained = properties.nodeReferenceRuntimeRetained,
        migrationTarget = properties.migrationTarget,
        controls = listOf(
            AdminPlatformControl(
                controlId = "SYNTHETIC_ONLY",
                status = if (properties.syntheticOnly) "PASS" else "FAIL",
                evidence = "banking-lab.synthetic-only"
            ),
            AdminPlatformControl(
                controlId = "TARGET_STACK",
                status = if (properties.migrationTarget == "kotlin-spring-boot") "PASS" else "REVIEW",
                evidence = "banking-lab.migration-target"
            ),
            AdminPlatformControl(
                controlId = "NODE_REFERENCE_BOUNDARY",
                status = if (properties.nodeReferenceRuntimeRetained) "BLOCKED" else "READY_FOR_REVIEW",
                evidence = "docs/migration/node-retirement-gate.json"
            )
        )
    )

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun evidenceCoverage(reason: String?): AdminEvidenceCoverageResponse {
        val viewReason = reason?.takeIf { it.isNotBlank() }
            ?: throw WorkflowErrors.reasonRequired("admin evidence coverage view requires a business reason")
        val actor = BankingLabAuthContext.get()
        if (actor != null && !actor.hasAnyRole(ALLOWED_ROLES)) {
            throw WorkflowErrors.authorizationViolation("actor role cannot view admin evidence coverage")
        }
        val evidenceLinks = evidenceLinks()
        val featureCoverage = featureCoverage()
        val auditEventId = auditEvents.append(
            eventType = "ADMIN_EVIDENCE_COVERAGE_VIEW",
            actorType = "STAFF",
            actorId = actor?.subject ?: "system",
            actorRole = actor?.roles?.sorted()?.firstOrNull(ALLOWED_ROLES::contains) ?: "SYSTEM",
            screenId = "ADM-501",
            businessReferenceId = "ADMIN_EVIDENCE_COVERAGE",
            reason = viewReason,
            payload = mapOf(
                "evidenceIds" to evidenceLinks.map { it.evidenceId },
                "featureIds" to featureCoverage.map { it.featureId },
                "evidenceCount" to evidenceLinks.size,
                "featureCount" to featureCoverage.size,
                "syntheticOnly" to true
            )
        )
        return AdminEvidenceCoverageResponse(
            auditEventId = auditEventId,
            generatedAt = OffsetDateTime.now().toString(),
            syntheticOnly = true,
            evidenceLinks = evidenceLinks,
            featureCoverage = featureCoverage
        )
    }

    private fun evidenceLinks(): List<AdminEvidenceLink> = listOf(
        AdminEvidenceLink(
            evidenceId = "IMPLEMENTATION_COVERAGE_MATRIX",
            title = "Implementation Coverage Matrix",
            path = "docs/implementation-coverage-matrix.md",
            status = "TRACKED",
            controlArea = "feature-coverage"
        ),
        AdminEvidenceLink(
            evidenceId = "API_BACKED_CHANNEL_SMOKE",
            title = "API-backed Channel Smoke",
            path = "docs/test-evidence/api-backed-channel-smoke.md",
            status = "TRACKED",
            controlArea = "channel-evidence"
        ),
        AdminEvidenceLink(
            evidenceId = "PARAMETER_ADMIN_APIS",
            title = "Parameter Admin API Evidence",
            path = "docs/test-evidence/parameter-admin-apis.md",
            status = "TRACKED",
            controlArea = "parameter-controls"
        ),
        AdminEvidenceLink(
            evidenceId = "KEYCLOAK_LIVE_REALM_SMOKE",
            title = "Keycloak Live Realm Smoke",
            path = "docs/test-evidence/keycloak-live-realm-smoke.md",
            status = "TRACKED",
            controlArea = "identity"
        ),
        AdminEvidenceLink(
            evidenceId = "NODE_RETIREMENT_GATE",
            title = "Node Retirement Gate",
            path = "docs/migration/node-retirement-gate.json",
            status = if (properties.nodeReferenceRuntimeRetained) "BLOCKED" else "READY_FOR_REVIEW",
            controlArea = "migration-boundary"
        ),
        AdminEvidenceLink(
            evidenceId = "ADMIN_PLATFORM_EVIDENCE_COVERAGE",
            title = "Admin Platform Evidence Coverage",
            path = "docs/test-evidence/admin-platform-evidence-coverage.md",
            status = "TRACKED",
            controlArea = "admin-evidence"
        )
    )

    private fun featureCoverage(): List<AdminFeatureCoverage> = listOf(
        AdminFeatureCoverage(
            featureId = "ADMIN_PLATFORM_SUMMARY",
            title = "Platform Control Dashboard",
            screenId = "ADM-101",
            apiContract = "GET /api/admin/platform/summary",
            evidencePath = "docs/test-evidence/api-backed-channel-smoke.md",
            status = "API_BACKED"
        ),
        AdminFeatureCoverage(
            featureId = "ADMIN_SECURITY_PARAMETERS",
            title = "Security Policy Parameters",
            screenId = "ADM-201",
            apiContract = "GET /api/admin/platform/security-parameters",
            evidencePath = "docs/test-evidence/parameter-admin-apis.md",
            status = "API_BACKED"
        ),
        AdminFeatureCoverage(
            featureId = "ADMIN_AUTHORIZATION_PARAMETERS",
            title = "Menu and Role Parameters",
            screenId = "ADM-301",
            apiContract = "GET /api/admin/platform/authorization-parameters",
            evidencePath = "docs/test-evidence/parameter-admin-apis.md",
            status = "API_BACKED"
        ),
        AdminFeatureCoverage(
            featureId = "ADMIN_NOTIFICATION_TEMPLATES",
            title = "Notification Template Approval",
            screenId = "ADM-401",
            apiContract = "GET /api/notifications/templates",
            evidencePath = "docs/test-evidence/notification-service.md",
            status = "API_BACKED"
        ),
        AdminFeatureCoverage(
            featureId = "ADMIN_NOTIFICATION_PREFERENCES",
            title = "Notification Preference Management",
            screenId = "ADM-402",
            apiContract = "GET /api/notifications/preferences",
            evidencePath = "docs/test-evidence/notification-service.md",
            status = "API_BACKED"
        ),
        AdminFeatureCoverage(
            featureId = "ADMIN_EVIDENCE_COVERAGE",
            title = "Platform Evidence Coverage",
            screenId = "ADM-501",
            apiContract = "GET /api/admin/platform/evidence-coverage",
            evidencePath = "docs/test-evidence/admin-platform-evidence-coverage.md",
            status = "API_BACKED"
        )
    )

    private companion object {
        val ALLOWED_ROLES = setOf("COMPLIANCE_MANAGER", "PASSKEY_RECOVERY_ADMIN")
    }
}
