package lab.banking.core.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

@Component
class BankingLabTokenDecoder(
    private val signedJwtJwksTokenDecoder: SignedJwtJwksTokenDecoder,
    private val simulatorTokenDecoder: KeycloakSimulatorTokenDecoder,
    @param:Value("\${banking-lab.security.simulator-tokens-enabled:false}")
    private val simulatorTokensEnabled: Boolean,
    @param:Value("\${banking-lab.security.dev-simulator-token-enabled:false}")
    private val devSimulatorTokenEnabled: Boolean
) {
    fun decode(authorizationHeader: String?): BankingLabPrincipal? {
        principalFromSecurityContext()?.let { return it }
        val token = authorizationHeader
            ?.takeIf { it.startsWith("Bearer ") }
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return signedJwtJwksTokenDecoder.decodeToken(token)
            ?: simulatorPrincipal(token)
    }

    private fun simulatorPrincipal(token: String): BankingLabPrincipal? =
        if (simulatorTokensEnabled && devSimulatorTokenEnabled) {
            simulatorTokenDecoder.decodeToken(token)
        } else {
            null
        }

    private fun principalFromSecurityContext(): BankingLabPrincipal? {
        val authentication = SecurityContextHolder.getContext().authentication
            ?.takeIf { it.isAuthenticated }
            ?: return null
        val jwt = jwtFrom(authentication) ?: return null
        val fallbackRoles = authentication.authorities
            .mapNotNull { authority ->
                authority.authority
                    .removePrefix("ROLE_")
                    .removePrefix("SCOPE_")
                    .takeIf { it.isNotBlank() }
            }
            .toSet()
        return BankingLabJwtClaims.toPrincipal(jwt.claims, fallbackRoles)
    }

    private fun jwtFrom(authentication: Authentication): Jwt? =
        when (authentication) {
            is JwtAuthenticationToken -> authentication.token
            else -> authentication.principal as? Jwt
        }
}
