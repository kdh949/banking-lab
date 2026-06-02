package lab.banking.core.api

import lab.banking.core.config.BankingLabProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(
    private val properties: BankingLabProperties
) {
    @GetMapping("/health")
    fun health(): HealthResponse = HealthResponse(
        status = "ok",
        syntheticOnly = properties.syntheticOnly,
        auditHashChainValid = true,
        nodeReferenceRuntimeRetained = properties.nodeReferenceRuntimeRetained,
        migrationTarget = properties.migrationTarget
    )
}
