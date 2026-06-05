package lab.banking.notification.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class NotificationTokenDecoder(
    private val signedJwtJwksTokenDecoder: NotificationSignedJwtJwksTokenDecoder,
    private val simulatorTokenDecoder: NotificationKeycloakSimulatorTokenDecoder,
    @param:Value("\${banking-lab.security.simulator-tokens-enabled:false}")
    private val simulatorTokensEnabled: Boolean,
    @param:Value("\${banking-lab.security.dev-simulator-token-enabled:false}")
    private val devSimulatorTokenEnabled: Boolean
) {
    fun decode(authorizationHeader: String?): NotificationPrincipal? {
        val token = authorizationHeader
            ?.takeIf { it.startsWith("Bearer ") }
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return signedJwtJwksTokenDecoder.decodeToken(token)
            ?: simulatorPrincipal(token)
    }

    private fun simulatorPrincipal(token: String): NotificationPrincipal? =
        if (simulatorTokensEnabled && devSimulatorTokenEnabled) {
            simulatorTokenDecoder.decodeToken(token)
        } else {
            null
        }
}
