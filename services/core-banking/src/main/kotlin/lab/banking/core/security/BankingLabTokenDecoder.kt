package lab.banking.core.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class BankingLabTokenDecoder(
    private val signedJwtJwksTokenDecoder: SignedJwtJwksTokenDecoder,
    private val simulatorTokenDecoder: KeycloakSimulatorTokenDecoder,
    @param:Value("\${banking-lab.security.simulator-tokens-enabled:true}")
    private val simulatorTokensEnabled: Boolean
) {
    fun decode(authorizationHeader: String?): BankingLabPrincipal? {
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
        if (simulatorTokensEnabled) {
            simulatorTokenDecoder.decodeToken(token)
        } else {
            null
        }
}
