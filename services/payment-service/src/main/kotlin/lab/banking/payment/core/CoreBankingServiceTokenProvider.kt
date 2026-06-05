package lab.banking.payment.core

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Clock
import java.time.Instant
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

@Component
class CoreBankingServiceTokenProvider(
    @param:Value("\${banking-lab.payment-service.core-banking.service-token:}")
    private val staticServiceToken: String,
    @param:Value("\${banking-lab.payment-service.core-banking.client-credentials.token-url:}")
    private val tokenUrl: String,
    @param:Value("\${banking-lab.payment-service.core-banking.client-credentials.client-id:payment-service-api}")
    private val clientId: String,
    @param:Value("\${banking-lab.payment-service.core-banking.client-credentials.client-secret:}")
    private val clientSecret: String,
    @param:Value("\${banking-lab.payment-service.core-banking.client-credentials.refresh-skew-seconds:30}")
    private val refreshSkewSeconds: Long,
    restClientBuilder: RestClient.Builder
) {
    private val tokenClient: RestClient = restClientBuilder.build()
    private val clock: Clock = Clock.systemUTC()

    @Volatile
    private var cachedToken: CachedToken? = null

    fun bearerToken(): String? {
        staticServiceToken.trim().takeIf { it.isNotBlank() }?.let { return it }
        if (tokenUrl.isBlank() && clientSecret.isBlank()) {
            return null
        }
        require(tokenUrl.isNotBlank()) { "core-banking client-credentials token-url must be configured" }
        require(clientId.isNotBlank()) { "core-banking client-credentials client-id must be configured" }
        require(clientSecret.isNotBlank()) { "core-banking client-credentials client-secret must be configured" }

        cachedToken?.takeIf { it.isUsable(clock.instant()) }?.let { return it.accessToken }
        return synchronized(this) {
            cachedToken?.takeIf { it.isUsable(clock.instant()) }?.accessToken
                ?: fetchClientCredentialsToken().also { cachedToken = it }.accessToken
        }
    }

    private fun fetchClientCredentialsToken(): CachedToken {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "client_credentials")
            add("client_id", clientId)
            add("client_secret", clientSecret)
        }
        val response = tokenClient
            .post()
            .uri(tokenUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(ClientCredentialsTokenResponse::class.java)
            ?: throw IllegalStateException("Keycloak client-credentials token endpoint returned no response")
        val token = response.accessToken.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Keycloak client-credentials token response did not contain access_token")
        val expiresIn = response.expiresIn?.takeIf { it > 0 } ?: 60
        val usableSeconds = (expiresIn - refreshSkewSeconds).coerceAtLeast(1)
        return CachedToken(token, clock.instant().plusSeconds(usableSeconds))
    }

    private data class ClientCredentialsTokenResponse(
        @param:JsonProperty("access_token")
        val accessToken: String = "",
        @param:JsonProperty("expires_in")
        val expiresIn: Long? = null
    )

    private data class CachedToken(
        val accessToken: String,
        val usableUntil: Instant
    ) {
        fun isUsable(now: Instant): Boolean = now.isBefore(usableUntil)
    }
}
