package lab.banking.payment.security

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.Base64
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

object PaymentJwtClaims {
    fun toPrincipal(claims: Map<String, Any?>, fallbackRoles: Set<String> = emptySet()): PaymentPrincipal? {
        val subject = subjectFromClaims(claims) ?: return null
        val roles = rolesFromClaims(claims) + fallbackRoles
        if (roles.isEmpty()) {
            return null
        }
        return PaymentPrincipal(
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
        resourceAccess?.values?.filterIsInstance<Map<*, *>>()?.forEach { roles += stringSet(it["roles"]) }
        roles += claims["scope"]?.toString()?.split(" ")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        return roles
    }

    fun subjectFromClaims(claims: Map<String, Any?>): String? =
        claims["preferred_username"]?.toString()?.takeIf { it.isNotBlank() }
            ?: claims["sub"]?.toString()?.takeIf { it.isNotBlank() }

    fun customerIdFromClaims(claims: Map<String, Any?>): String? =
        claims["customerId"]?.toString()
            ?: claims["customer_id"]?.toString()
            ?: claims["banking_lab_customer_id"]?.toString()

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

@Component
class PaymentJwtAuthenticationConverter : Converter<Jwt, AbstractAuthenticationToken> {
    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val authorities = PaymentJwtClaims.rolesFromClaims(jwt.claims)
            .flatMap { listOf(SimpleGrantedAuthority("ROLE_$it"), SimpleGrantedAuthority("SCOPE_$it")) }
            .toSet()
        return JwtAuthenticationToken(jwt, authorities, PaymentJwtClaims.subjectFromClaims(jwt.claims) ?: jwt.subject)
    }
}

class PaymentResourceServerJwtDecoder(
    private val objectMapper: ObjectMapper,
    jwksUri: String,
    expectedIssuer: String,
    private val expectedAudience: String,
    private val simulatorTokensEnabled: Boolean,
    private val devSimulatorTokenEnabled: Boolean
) : JwtDecoder {
    private val signedDecoder: JwtDecoder? =
        jwksUri.takeIf { it.isNotBlank() }?.let { NimbusJwtDecoder.withJwkSetUri(it).build() }
    private val allowedIssuers = expectedIssuer.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()

    override fun decode(token: String): Jwt {
        if (token.startsWith("lab.")) {
            return decodeSimulatorToken(token)
        }
        val jwt = signedDecoder?.decode(token)
            ?: throw BadJwtException("JWKS URI is not configured for signed JWT verification")
        validateClaims(jwt.claims)
        return jwt
    }

    private fun decodeSimulatorToken(token: String): Jwt {
        if (!simulatorTokensEnabled || !devSimulatorTokenEnabled) {
            throw BadJwtException("simulator tokens require explicit dev/test double opt-in")
        }
        val parts = token.split(".")
        if (parts.size != 3 || parts[0] != "lab" || parts[2] != "sig") {
            throw BadJwtException("simulator token format is invalid")
        }
        @Suppress("UNCHECKED_CAST")
        val claims = runCatching {
            objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map::class.java) as Map<String, Any?>
        }.getOrElse { throw BadJwtException("simulator token payload is invalid") }
        validateClaims(claims, requireExpiry = false)
        val now = Instant.now()
        return Jwt.withTokenValue(token)
            .header("alg", "lab-simulator")
            .issuer(claims["iss"]?.toString())
            .subject(PaymentJwtClaims.subjectFromClaims(claims))
            .issuedAt(PaymentJwtClaims.epochInstant(claims["iat"]) ?: now)
            .expiresAt(PaymentJwtClaims.epochInstant(claims["exp"]) ?: now.plusSeconds(300))
            .claims { it.putAll(normalizedRegisteredClaims(claims)) }
            .build()
    }

    private fun validateClaims(claims: Map<String, Any?>, requireExpiry: Boolean = true) {
        if (claims["active"] == false) {
            throw BadJwtException("token is inactive")
        }
        if (allowedIssuers.isNotEmpty() && claims["iss"]?.toString() !in allowedIssuers) {
            throw BadJwtException("token issuer is not allowed")
        }
        if (expectedAudience.isNotBlank() && !PaymentJwtClaims.stringSet(claims["aud"]).contains(expectedAudience)) {
            throw BadJwtException("token audience is not allowed")
        }
        if (PaymentJwtClaims.subjectFromClaims(claims) == null) {
            throw BadJwtException("token subject is required")
        }
        if (PaymentJwtClaims.rolesFromClaims(claims).isEmpty()) {
            throw BadJwtException("token role claim is required")
        }
        if (requireExpiry && PaymentJwtClaims.epochInstant(claims["exp"]) == null) {
            throw BadJwtException("token expiry is required")
        }
    }

    private fun normalizedRegisteredClaims(claims: Map<String, Any?>): Map<String, Any?> =
        claims.mapValues { (key, value) ->
            when (key) {
                "iat", "exp", "nbf" -> PaymentJwtClaims.epochInstant(value) ?: value
                else -> value
            }
        }
}
