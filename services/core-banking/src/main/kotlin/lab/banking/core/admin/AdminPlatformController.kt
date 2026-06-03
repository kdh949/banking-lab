package lab.banking.core.admin

import lab.banking.core.config.BankingLabProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
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

@RestController
@RequestMapping("/api/admin/platform")
class AdminPlatformController(
    private val properties: BankingLabProperties
) {
    @GetMapping("/summary")
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
}
