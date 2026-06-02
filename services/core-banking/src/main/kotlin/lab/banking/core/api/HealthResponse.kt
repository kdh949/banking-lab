package lab.banking.core.api

data class HealthResponse(
    val status: String,
    val syntheticOnly: Boolean,
    val auditHashChainValid: Boolean,
    val nodeReferenceRuntimeRetained: Boolean,
    val migrationTarget: String
)
