package lab.banking.core.auth

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import lab.banking.core.common.BankingLabDomainException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

@Component
class SyntheticCustomerAuthTokenIssuer(
    private val objectMapper: ObjectMapper,
    @param:Value("\${banking-lab.security.customer-auth.synthetic-token-issuer-enabled:false}")
    private val issuerEnabled: Boolean,
    @param:Value("\${banking-lab.security.simulator-tokens-enabled:false}")
    private val simulatorTokensEnabled: Boolean,
    @param:Value("\${banking-lab.security.dev-simulator-token-enabled:false}")
    private val devSimulatorTokenEnabled: Boolean,
    @param:Value("\${banking-lab.security.customer-auth.token-ttl-seconds:3600}")
    private val tokenTtlSeconds: Long,
    @param:Value("\${banking-lab.security.jwt.audience:}")
    private val expectedAudience: String
) {
    fun issue(authSubject: String, username: String, customerId: String): IssuedSyntheticCustomerToken {
        requireIssuerEnabled()
        val issuedAt = OffsetDateTime.now(ZoneOffset.UTC)
        val expiresAt = issuedAt.plus(Duration.ofSeconds(tokenTtlSeconds.coerceAtLeast(60)))
        val sessionId = "SYN-SES-${UUID.randomUUID().toString().uppercase()}"
        val payload = linkedMapOf<String, Any?>(
            "iss" to ISSUER,
            "sub" to authSubject,
            "aud" to expectedAudience.takeIf { it.isNotBlank() },
            "preferred_username" to username,
            "roles" to listOf("CUSTOMER"),
            "customerId" to customerId,
            "sid" to sessionId,
            "deviceFingerprint" to syntheticDeviceFingerprint(customerId),
            "auth_time" to issuedAt.toEpochSecond(),
            "iat" to issuedAt.toEpochSecond(),
            "exp" to expiresAt.toEpochSecond(),
            "amr" to listOf("pwd", "synthetic"),
            "acr" to "urn:banking-lab:synthetic-auth",
            "active" to true,
            "syntheticOnly" to true,
            "realKeycloakToken" to false
        )
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(objectMapper.writeValueAsBytes(payload))
        return IssuedSyntheticCustomerToken(
            bearerToken = "lab.$encoded.sig",
            expiresAt = expiresAt,
            session = CustomerAuthSessionDto(
                subject = username,
                customerId = customerId,
                roles = listOf("CUSTOMER"),
                issuer = ISSUER,
                sessionId = sessionId,
                authTime = issuedAt,
                issuedAt = issuedAt
            )
        )
    }

    fun requireIssuerEnabled() {
        if (issuerEnabled && simulatorTokensEnabled && devSimulatorTokenEnabled) {
            return
        }
        throw BankingLabDomainException(
            code = "SYNTHETIC_CUSTOMER_AUTH_DISABLED",
            status = HttpStatus.FORBIDDEN,
            domain = "auth",
            policy = "SYNTHETIC_CUSTOMER_AUTH_DEV_TEST_ONLY",
            message = "synthetic customer auth token issuance is disabled",
            causeText = "Customer signup/login can issue lab-format bearer tokens only when the explicit dev/test synthetic-auth and simulator-token settings are enabled.",
            fix = "Enable banking-lab.security.customer-auth.synthetic-token-issuer-enabled together with simulator token decoding in a dev or test profile."
        )
    }

    companion object {
        const val ISSUER = "banking-lab-synthetic-customer-auth"

        fun syntheticDeviceFingerprint(customerId: String): String =
            "SYN-DEVICE-$customerId"
    }
}

data class IssuedSyntheticCustomerToken(
    val bearerToken: String,
    val expiresAt: OffsetDateTime,
    val session: CustomerAuthSessionDto
)
