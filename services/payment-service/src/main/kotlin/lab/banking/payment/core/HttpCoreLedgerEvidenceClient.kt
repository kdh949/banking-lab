
package lab.banking.payment.core

import java.time.LocalDate
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
@ConditionalOnProperty(
    prefix = "banking-lab.payment-service.core-banking",
    name = ["http-enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class HttpCoreLedgerEvidenceClient(
    @param:Value("\${banking-lab.payment-service.core-banking.base-url:http://localhost:8081}")
    private val baseUrl: String,
    private val tokenProvider: CoreBankingServiceTokenProvider,
    restClientBuilder: RestClient.Builder
) : CoreLedgerEvidenceClient {
    private val restClient = restClientBuilder.baseUrl(baseUrl).build()

    override fun paymentPostings(businessDate: LocalDate, reason: String): List<CorePaymentLedgerEvidence> {
        val response = restClient
            .get()
            .uri { builder ->
                builder
                    .path("/api/ledger/payment-postings/evidence")
                    .queryParam("businessDate", businessDate)
                    .queryParam("reason", reason)
                    .build()
            }
            .headers { headers ->
                val bearerToken = tokenProvider.bearerToken()
                if (!bearerToken.isNullOrBlank()) {
                    headers.setBearerAuth(bearerToken)
                }
            }
            .retrieve()
            .body(CorePaymentLedgerEvidenceResponse::class.java)
            ?: throw IllegalStateException("core-banking payment ledger evidence returned no response")
        if (!response.syntheticOnly || response.items.any { !it.syntheticOnly }) {
            throw IllegalStateException("core-banking payment ledger evidence crossed the synthetic-only boundary")
        }
        return response.items
    }

    private data class CorePaymentLedgerEvidenceResponse(
        val items: List<CorePaymentLedgerEvidence>,
        val syntheticOnly: Boolean
    )
}
