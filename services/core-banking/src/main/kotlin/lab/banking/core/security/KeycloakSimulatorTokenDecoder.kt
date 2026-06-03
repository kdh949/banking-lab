package lab.banking.core.security

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Base64
import org.springframework.stereotype.Component

@Component
class KeycloakSimulatorTokenDecoder(
    private val objectMapper: ObjectMapper
) {
    fun decode(authorizationHeader: String?): BankingLabPrincipal? {
        val token = authorizationHeader
            ?.takeIf { it.startsWith("Bearer ") }
            ?.removePrefix("Bearer ")
            ?.trim()
            ?: return null
        return decodeToken(token)
    }

    fun decodeToken(token: String): BankingLabPrincipal? {
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
        val roles = when (val rawRoles = payload["roles"]) {
            is Collection<*> -> rawRoles.mapNotNull { it?.toString() }.filter { it.isNotBlank() }.toSet()
            is String -> rawRoles.split(" ", ",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
            else -> emptySet()
        }
        if (roles.isEmpty()) {
            return null
        }
        return BankingLabPrincipal(
            subject = subject,
            roles = roles,
            customerId = payload["customerId"]?.toString(),
            issuer = payload["iss"]?.toString()
        )
    }
}
