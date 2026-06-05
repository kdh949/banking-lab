package lab.banking.reporting.security

import java.time.Instant

data class ReportingPrincipal(
    val subject: String,
    val roles: Set<String>,
    val issuer: String? = null,
    val sessionId: String? = null,
    val authTime: Instant? = null,
    val issuedAt: Instant? = null,
    val authenticationMethods: Set<String> = emptySet(),
    val assuranceLevel: String? = null
) {
    fun hasAnyRole(allowed: Set<String>): Boolean =
        roles.any(allowed::contains)
}
