package lab.banking.core.admin

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class AdminPlatformControlSummary(
    val syntheticOnly: Boolean,
    val nodeReferenceRuntimeRetained: Boolean,
    val migrationTarget: String,
    val controls: List<AdminPlatformControl>
)

data class AdminPlatformControl(
    val controlId: String,
    val status: String,
    val evidence: String
)

data class AdminEvidenceCoverageResponse(
    val auditEventId: String,
    val generatedAt: String,
    val syntheticOnly: Boolean,
    val evidenceLinks: List<AdminEvidenceLink>,
    val featureCoverage: List<AdminFeatureCoverage>
)

data class AdminEvidenceLink(
    val evidenceId: String,
    val title: String,
    val path: String,
    val status: String,
    val controlArea: String
)

data class AdminFeatureCoverage(
    val featureId: String,
    val title: String,
    val screenId: String,
    val apiContract: String,
    val evidencePath: String,
    val status: String
)

data class AdminSystemStatusResponse(
    val auditEventId: String,
    val generatedAt: String,
    val syntheticOnly: Boolean,
    val services: List<AdminServiceStatus>,
    val batches: List<AdminBatchStatus>,
    val monitoringLinks: List<AdminMonitoringLink>
)

data class AdminServiceStatus(
    val serviceId: String,
    val displayName: String,
    val status: String,
    val evidence: String,
    val syntheticOnly: Boolean
)

data class AdminBatchStatus(
    val batchType: String,
    val latestReferenceId: String?,
    val businessDate: String?,
    val status: String,
    val itemCount: Long,
    val lastUpdatedAt: String?,
    val evidence: String
)

data class AdminMonitoringLink(
    val system: String,
    val url: String,
    val status: String,
    val evidence: String
)

@RestController
@RequestMapping("/api/admin/platform")
class AdminPlatformController(
    private val service: AdminPlatformService
) {
    @GetMapping("/summary")
    fun summary(): AdminPlatformControlSummary = service.summary()

    @GetMapping("/evidence-coverage")
    fun evidenceCoverage(@RequestParam(required = false) reason: String?): AdminEvidenceCoverageResponse =
        service.evidenceCoverage(reason)

    @GetMapping("/system-status")
    fun systemStatus(@RequestParam(required = false) reason: String?): AdminSystemStatusResponse =
        service.systemStatus(reason)
}
