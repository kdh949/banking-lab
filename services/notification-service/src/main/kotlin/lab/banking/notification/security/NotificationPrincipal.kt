package lab.banking.notification.security

import java.time.Instant

data class NotificationPrincipal(
    val subject: String,
    val roles: Set<String>,
    val customerId: String? = null,
    val issuer: String? = null,
    val sessionId: String? = null,
    val authTime: Instant? = null,
    val issuedAt: Instant? = null,
    val authenticationMethods: Set<String> = emptySet(),
    val assuranceLevel: String? = null,
    val deviceFingerprint: String? = null
) {
    fun hasAnyRole(allowed: Set<String>): Boolean =
        roles.any(allowed::contains)
}
