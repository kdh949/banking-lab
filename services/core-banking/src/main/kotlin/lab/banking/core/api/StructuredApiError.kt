package lab.banking.core.api

data class StructuredApiErrorEnvelope(
    val error: StructuredApiError
)

data class StructuredApiError(
    val contractVersion: String = "2026-06-02",
    val code: String,
    val message: String,
    val statusCode: Int,
    val domain: String,
    val invariant: String? = null,
    val policy: String? = null,
    val cause: String,
    val fix: String,
    val requestId: String,
    val correlationId: String = requestId,
    val route: String? = null,
    val docs: String = "docs/migration/structured-api-error-contract.md",
    val syntheticOnly: Boolean = true
)
