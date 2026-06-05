package lab.banking.reporting.security

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.Base64
import org.springframework.stereotype.Component

@Component
class ReportingKeycloakSimulatorTokenDecoder(
    private val objectMapper: ObjectMapper
) {
    fun decodeToken(token: String): ReportingPrincipal? {
        val parts = token.split(".")
        if (parts.size != 3 || parts[0] != "lab" || parts[2] != "sig") {
            return null
        }
        val payload = runCatching {
            val decoded = Base64.getUrlDecoder().decode(parts[1])
            objectMapper.readValue(decoded, Map::class.java)
        }.getOrNull() ?: return null
        if (payload["active"] == false) {
            return null
        }
        val subject = payload["sub"]?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val roles = stringSet(payload["roles"])
        if (roles.isEmpty()) {
            return null
        }
        return ReportingPrincipal(
            subject = subject,
            roles = roles,
            issuer = payload["iss"]?.toString(),
            sessionId = payload["sid"]?.toString()?.takeIf { it.isNotBlank() },
            authTime = epochInstant(payload["auth_time"]),
            issuedAt = epochInstant(payload["iat"]),
            authenticationMethods = stringSet(payload["amr"]),
            assuranceLevel = payload["acr"]?.toString()
        )
    }

    private fun stringSet(value: Any?): Set<String> =
        when (value) {
            is Collection<*> -> value.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }.toSet()
            is String -> value.split(" ", ",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
            else -> emptySet()
        }

    private fun epochInstant(value: Any?): Instant? =
        when (value) {
            is Number -> Instant.ofEpochSecond(value.toLong())
            is String -> value.toLongOrNull()?.let(Instant::ofEpochSecond)
            else -> null
        }
}
