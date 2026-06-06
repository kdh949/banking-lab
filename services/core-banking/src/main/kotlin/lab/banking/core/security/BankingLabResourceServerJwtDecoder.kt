package lab.banking.core.security

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.Base64
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

class BankingLabResourceServerJwtDecoder(
    private val objectMapper: ObjectMapper,
    private val jwksUri: String,
    expectedIssuer: String,
    private val expectedAudience: String,
    private val simulatorTokensEnabled: Boolean,
    private val devSimulatorTokenEnabled: Boolean
) : JwtDecoder {
    private val signedDecoder: JwtDecoder? =
        jwksUri.takeIf { it.isNotBlank() }?.let { NimbusJwtDecoder.withJwkSetUri(it).build() }
    private val allowedIssuers = expectedIssuer
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()

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
        val claims = runCatching {
            @Suppress("UNCHECKED_CAST")
            objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map::class.java) as Map<String, Any?>
        }.getOrElse { throw BadJwtException("simulator token payload is invalid") }
        validateClaims(claims, requireExpiry = false)
        val now = Instant.now()
        return Jwt.withTokenValue(token)
            .header("alg", "lab-simulator")
            .issuer(claims["iss"]?.toString())
            .subject(BankingLabJwtClaims.subjectFromClaims(claims))
            .issuedAt(BankingLabJwtClaims.epochInstant(claims["iat"]) ?: now)
            .expiresAt(BankingLabJwtClaims.epochInstant(claims["exp"]) ?: now.plusSeconds(300))
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
        if (expectedAudience.isNotBlank() && !BankingLabJwtClaims.stringSet(claims["aud"]).contains(expectedAudience)) {
            throw BadJwtException("token audience is not allowed")
        }
        if (BankingLabJwtClaims.subjectFromClaims(claims) == null) {
            throw BadJwtException("token subject is required")
        }
        if (BankingLabJwtClaims.rolesFromClaims(claims).isEmpty()) {
            throw BadJwtException("token role claim is required")
        }
        if (requireExpiry && BankingLabJwtClaims.epochInstant(claims["exp"]) == null) {
            throw BadJwtException("token expiry is required")
        }
    }

    private fun normalizedRegisteredClaims(claims: Map<String, Any?>): Map<String, Any?> =
        claims.mapValues { (key, value) ->
            when (key) {
                "iat", "exp", "nbf" -> BankingLabJwtClaims.epochInstant(value) ?: value
                else -> value
            }
        }
}
