package lab.banking.core.admin

import java.time.OffsetDateTime
import lab.banking.core.audit.AuditEventAppender
import lab.banking.core.config.BankingLabProperties
import lab.banking.core.security.BankingLabAuthContext
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class AdminPlatformService(
    private val properties: BankingLabProperties,
    private val auditEvents: AuditEventAppender,
    private val jdbc: NamedParameterJdbcTemplate
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
    fun systemStatus(reason: String?): AdminSystemStatusResponse {
        val viewReason = requireReason(reason, "admin system status view requires a business reason")
        val actor = adminActor("actor role cannot view admin system status")
        val services = serviceStatuses()
        val batches = batchStatuses()
        val monitoringLinks = monitoringLinks()
        val auditEventId = auditEvents.append(
            eventType = "ADMIN_SYSTEM_STATUS_VIEW",
            actorType = "STAFF",
            actorId = actor.actorId,
            actorRole = actor.actorRole,
            screenId = "ADM-601",
            businessReferenceId = "ADMIN_SYSTEM_STATUS",
            reason = viewReason,
            payload = mapOf(
                "serviceIds" to services.map { it.serviceId },
                "batchTypes" to batches.map { it.batchType },
                "monitoringSystems" to monitoringLinks.map { it.system },
                "syntheticOnly" to true
            )
        )
        return AdminSystemStatusResponse(
            auditEventId = auditEventId,
            generatedAt = OffsetDateTime.now().toString(),
            syntheticOnly = true,
            services = services,
            batches = batches,
            monitoringLinks = monitoringLinks
        )
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun evidenceCoverage(reason: String?): AdminEvidenceCoverageResponse {
        val viewReason = requireReason(reason, "admin evidence coverage view requires a business reason")
        val actor = adminActor("actor role cannot view admin evidence coverage")
        val evidenceLinks = evidenceLinks()
        val featureCoverage = featureCoverage()
        val auditEventId = auditEvents.append(
            eventType = "ADMIN_EVIDENCE_COVERAGE_VIEW",
            actorType = "STAFF",
            actorId = actor.actorId,
            actorRole = actor.actorRole,
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

    private fun serviceStatuses(): List<AdminServiceStatus> = listOf(
        AdminServiceStatus(
            serviceId = "CORE_BANKING",
            displayName = "Core Banking Spring API",
            status = "AVAILABLE",
            evidence = "GET /health",
            syntheticOnly = true
        ),
        AdminServiceStatus(
            serviceId = "POSTGRESQL",
            displayName = "PostgreSQL Ledger Store",
            status = "AVAILABLE",
            evidence = "Flyway migrations applied through target stack",
            syntheticOnly = true
        ),
        AdminServiceStatus(
            serviceId = "REDPANDA",
            displayName = "Redpanda Event Broker",
            status = "CONFIGURED",
            evidence = "infra/docker-compose and outbox worker evidence",
            syntheticOnly = true
        ),
        AdminServiceStatus(
            serviceId = "TEMPORAL",
            displayName = "Temporal Workflow Runtime",
            status = "CONFIGURED",
            evidence = "docs/test-evidence/temporal-live-worker-smoke.md",
            syntheticOnly = true
        ),
        AdminServiceStatus(
            serviceId = "KEYCLOAK",
            displayName = "Keycloak Identity Provider",
            status = "CONFIGURED",
            evidence = "docs/test-evidence/keycloak-live-realm-smoke.md",
            syntheticOnly = true
        ),
        AdminServiceStatus(
            serviceId = "OBSERVABILITY",
            displayName = "Prometheus Grafana Loki Tempo",
            status = "CONFIGURED",
            evidence = "docs/test-evidence/observability-stack-smoke.md",
            syntheticOnly = true
        )
    )

    private fun batchStatuses(): List<AdminBatchStatus> = listOf(
        latestBatchStatus(
            batchType = "EOD_CLOSING",
            tableName = "daily_closings",
            referenceExpression = "business_date::text",
            businessDateExpression = "business_date::text",
            updatedExpression = "COALESCE(closed_at, created_at)",
            evidence = "docs/test-evidence/eod-closing-pipeline.md"
        ),
        latestBatchStatus(
            batchType = "EOD_STEPS",
            tableName = "eod_closing_steps",
            referenceExpression = "business_date::text || ':' || step",
            businessDateExpression = "business_date::text",
            updatedExpression = "COALESCE(finished_at, started_at)",
            evidence = "docs/test-evidence/eod-closing-pipeline.md"
        ),
        latestBatchStatus(
            batchType = "INTEREST_POSTING",
            tableName = "interest_posting_batches",
            referenceExpression = "batch_id",
            businessDateExpression = "business_date::text",
            updatedExpression = "posted_at",
            evidence = "docs/test-evidence/eod-closing-pipeline.md"
        ),
        latestBatchStatus(
            batchType = "FEE_POSTING",
            tableName = "fee_posting_batches",
            referenceExpression = "batch_id",
            businessDateExpression = "business_date::text",
            updatedExpression = "posted_at",
            evidence = "docs/test-evidence/eod-closing-pipeline.md"
        ),
        latestBatchStatus(
            batchType = "OUTBOX_DELIVERY",
            tableName = "outbox_events",
            referenceExpression = "outbox_event_id",
            businessDateExpression = "created_at::date::text",
            updatedExpression = "COALESCE(published_at, next_retry_at, created_at)",
            evidence = "docs/test-evidence/outbox-worker-failure-drill.md"
        )
    )

    private fun latestBatchStatus(
        batchType: String,
        tableName: String,
        referenceExpression: String,
        businessDateExpression: String,
        updatedExpression: String,
        evidence: String
    ): AdminBatchStatus {
        val itemCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM $tableName",
            emptyMap<String, Any?>(),
            Long::class.java
        ) ?: 0L
        val latest = jdbc.query(
            """
            SELECT
              $referenceExpression AS reference_id,
              $businessDateExpression AS business_date,
              status,
              $updatedExpression AS updated_at
            FROM $tableName
            ORDER BY $updatedExpression DESC NULLS LAST, reference_id DESC
            LIMIT 1
            """.trimIndent(),
            emptyMap<String, Any?>()
        ) { rs, _ ->
            AdminBatchStatus(
                batchType = batchType,
                latestReferenceId = rs.getString("reference_id"),
                businessDate = rs.getString("business_date"),
                status = rs.getString("status"),
                itemCount = itemCount,
                lastUpdatedAt = rs.getObject("updated_at", OffsetDateTime::class.java)?.toString(),
                evidence = evidence
            )
        }.firstOrNull()
        return latest ?: AdminBatchStatus(
            batchType = batchType,
            latestReferenceId = null,
            businessDate = null,
            status = "NO_RUN",
            itemCount = itemCount,
            lastUpdatedAt = null,
            evidence = evidence
        )
    }

    private fun monitoringLinks(): List<AdminMonitoringLink> = listOf(
        AdminMonitoringLink(
            system = "PROMETHEUS",
            url = "http://localhost:9090",
            status = "LOCAL_PROFILE",
            evidence = "infra/prometheus/prometheus.yml"
        ),
        AdminMonitoringLink(
            system = "GRAFANA",
            url = "http://localhost:3000",
            status = "LOCAL_PROFILE",
            evidence = "infra/grafana/provisioning"
        ),
        AdminMonitoringLink(
            system = "LOKI",
            url = "http://localhost:3100",
            status = "LOCAL_PROFILE",
            evidence = "infra/loki"
        ),
        AdminMonitoringLink(
            system = "TEMPO",
            url = "http://localhost:3200",
            status = "LOCAL_PROFILE",
            evidence = "infra/tempo"
        )
    )

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

    private fun requireReason(value: String?, message: String): String =
        value?.takeIf { it.isNotBlank() } ?: throw WorkflowErrors.reasonRequired(message)

    private fun adminActor(message: String): AdminActor {
        val actor = BankingLabAuthContext.get()
        if (actor != null && !actor.hasAnyRole(ALLOWED_ROLES)) {
            throw WorkflowErrors.authorizationViolation(message)
        }
        return AdminActor(
            actorId = actor?.subject ?: "system",
            actorRole = actor?.roles?.sorted()?.firstOrNull(ALLOWED_ROLES::contains) ?: "SYSTEM"
        )
    }

    private data class AdminActor(val actorId: String, val actorRole: String)

    private companion object {
        val ALLOWED_ROLES = setOf("COMPLIANCE_MANAGER", "PASSKEY_RECOVERY_ADMIN")
    }
}
