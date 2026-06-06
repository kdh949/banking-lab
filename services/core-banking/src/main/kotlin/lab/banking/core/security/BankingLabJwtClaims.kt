package lab.banking.core.security

import java.time.Instant

object BankingLabJwtClaims {
    fun toPrincipal(claims: Map<String, Any?>, fallbackRoles: Set<String> = emptySet()): BankingLabPrincipal? {
        val subject = subjectFromClaims(claims) ?: return null
        val roles = rolesFromClaims(claims) + fallbackRoles
        if (roles.isEmpty()) {
            return null
        }
        return BankingLabPrincipal(
            subject = subject,
            roles = roles,
            customerId = customerIdFromClaims(claims),
            issuer = claims["iss"]?.toString(),
            sessionId = claims["sid"]?.toString()?.takeIf { it.isNotBlank() },
            authTime = epochInstant(claims["auth_time"]),
            issuedAt = epochInstant(claims["iat"]),
            authenticationMethods = stringSet(claims["amr"]),
            assuranceLevel = claims["acr"]?.toString(),
            deviceFingerprint = claims["deviceFingerprint"]?.toString()
                ?: claims["device_fingerprint"]?.toString()
        )
    }

    fun rolesFromClaims(claims: Map<String, Any?>): Set<String> {
        val roles = linkedSetOf<String>()
        roles += stringSet(claims["roles"])
        val realmAccess = claims["realm_access"] as? Map<*, *>
        roles += stringSet(realmAccess?.get("roles"))
        val resourceAccess = claims["resource_access"] as? Map<*, *>
        resourceAccess
            ?.values
            ?.filterIsInstance<Map<*, *>>()
            ?.forEach { roles += stringSet(it["roles"]) }
        roles += claims["scope"]
            ?.toString()
            ?.split(" ")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        return roles
    }

    fun customerIdFromClaims(claims: Map<String, Any?>): String? =
        claims["customerId"]?.toString()
            ?: claims["customer_id"]?.toString()
            ?: claims["banking_lab_customer_id"]?.toString()

    fun subjectFromClaims(claims: Map<String, Any?>): String? =
        claims["preferred_username"]?.toString()?.takeIf { it.isNotBlank() }
            ?: claims["sub"]?.toString()?.takeIf { it.isNotBlank() }

    fun stringSet(value: Any?): Set<String> =
        when (value) {
            is Collection<*> -> value.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }.toSet()
            is String -> value.split(" ", ",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
            else -> emptySet()
        }

    fun epochInstant(value: Any?): Instant? =
        when (value) {
            is Instant -> value
            is Number -> Instant.ofEpochSecond(value.toLong())
            is String -> value.toLongOrNull()?.let(Instant::ofEpochSecond)
            else -> null
        }
}
