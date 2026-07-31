package lab.banking.payment.core

import lab.banking.payment.domain.CoreLedgerPaymentPostingCommand
import lab.banking.payment.domain.CoreLedgerPostingClient
import lab.banking.payment.domain.CoreLedgerPostingResult
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
class HttpCoreLedgerPostingClient(
    @param:Value("\${banking-lab.payment-service.core-banking.base-url:http://localhost:8081}")
    private val baseUrl: String,
    private val tokenProvider: CoreBankingServiceTokenProvider,
    restClientBuilder: RestClient.Builder
) : CoreLedgerPostingClient {
    private val restClient: RestClient = restClientBuilder.baseUrl(baseUrl).build()

    override fun postBillPayment(command: CoreLedgerPaymentPostingCommand): CoreLedgerPostingResult {
        val response = restClient
            .post()
            .uri("/api/ledger/payment-postings")
            .headers { headers ->
                val bearerToken = tokenProvider.bearerToken()
                if (!bearerToken.isNullOrBlank()) {
                    headers.setBearerAuth(bearerToken)
                }
            }
            .body(command)
            .retrieve()
            .body(CoreLedgerCommandResponse::class.java)
            ?: throw IllegalStateException("core-banking ledger posting returned no response")
        val transactionId = response.value.id.takeIf { it.startsWith("TX-") }
            ?: throw IllegalStateException("core-banking ledger posting returned an invalid transaction id")
        return CoreLedgerPostingResult(ledgerTransactionId = transactionId)
    }

    private data class CoreLedgerCommandResponse(
        val value: CoreLedgerTransactionValue
    )

    private data class CoreLedgerTransactionValue(
        val id: String
    )
}

@Component
@ConditionalOnProperty(
    prefix = "banking-lab.payment-service.core-banking",
    name = ["http-enabled"],
    havingValue = "false"
)
class DisabledCoreLedgerPostingClient : CoreLedgerPostingClient {
    @Suppress("UNUSED_PARAMETER")
    override fun postBillPayment(command: CoreLedgerPaymentPostingCommand): CoreLedgerPostingResult {
        throw IllegalStateException("core-banking HTTP ledger posting is disabled by configuration")
    }
}
