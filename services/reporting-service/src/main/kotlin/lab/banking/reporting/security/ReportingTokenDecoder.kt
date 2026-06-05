package lab.banking.reporting.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ReportingTokenDecoder(
    private val signedJwtJwksTokenDecoder: ReportingSignedJwtJwksTokenDecoder,
    private val simulatorTokenDecoder: ReportingKeycloakSimulatorTokenDecoder,
    @param:Value("\${banking-lab.security.simulator-tokens-enabled:false}")
    private val simulatorTokensEnabled: Boolean,
    @param:Value("\${banking-lab.security.dev-simulator-token-enabled:false}")
    private val devSimulatorTokenEnabled: Boolean
) {
    fun decode(authorizationHeader: String?): ReportingPrincipal? {
        val token = authorizationHeader
            ?.takeIf { it.startsWith("Bearer ") }
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return signedJwtJwksTokenDecoder.decodeToken(token)
            ?: simulatorPrincipal(token)
    }

    private fun simulatorPrincipal(token: String): ReportingPrincipal? =
        if (simulatorTokensEnabled && devSimulatorTokenEnabled) {
            simulatorTokenDecoder.decodeToken(token)
        } else {
            null
        }
}
