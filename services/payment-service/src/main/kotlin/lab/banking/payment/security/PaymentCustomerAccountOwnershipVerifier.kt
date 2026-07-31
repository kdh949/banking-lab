package lab.banking.payment.security

import lab.banking.payment.domain.PaymentDomainException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@Component
class PaymentCustomerAccountOwnershipVerifier(
    @param:Value("\${banking-lab.payment-service.customer-account-ownership.enabled:true}")
    private val enabled: Boolean,
    @param:Value("\${banking-lab.payment-service.core-banking.base-url:http://localhost:8081}")
    baseUrl: String,
    restClientBuilder: RestClient.Builder
) {
    private val restClient = restClientBuilder.baseUrl(baseUrl).build()

    fun requireOwned(customerId: String, accountId: String, authorizationHeader: String?) {
        if (!enabled) {
            return
        }
        val bearer = authorizationHeader?.trim()?.takeIf { it.startsWith("Bearer ") }
            ?: throw PaymentDomainException(
                code = "PAYMENT_CUSTOMER_AUTHORIZATION_REQUIRED",
                status = HttpStatus.UNAUTHORIZED,
                policy = "PAYMENT_CUSTOMER_ACCOUNT_OWNERSHIP",
                message = "customer authorization is required for debit account validation",
                causeText = "Payment Service cannot prove account ownership without the authenticated customer bearer token.",
                fix = "Retry the customer payment request with a valid Bearer token."
            )
        try {
            restClient.get()
                .uri { builder ->
                    builder
                        .path("/api/customer/accounts/{accountId}/detail")
                        .queryParam("customerId", customerId)
                        .build(accountId)
                }
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .retrieve()
                .toBodilessEntity()
        } catch (error: RestClientResponseException) {
            if (error.statusCode.value() == 404) {
                throw debitAccountNotFound(accountId)
            }
            throw ownershipServiceUnavailable(accountId, error)
        } catch (error: ResourceAccessException) {
            throw ownershipServiceUnavailable(accountId, error)
        }
    }

    private fun debitAccountNotFound(accountId: String): PaymentDomainException =
        PaymentDomainException(
            code = "PAYMENT_DEBIT_ACCOUNT_NOT_FOUND",
            status = HttpStatus.NOT_FOUND,
            invariant = "payment debit account must belong to the authenticated customer",
            policy = "PAYMENT_CUSTOMER_ACCOUNT_OWNERSHIP",
            message = "customer debit account was not found",
            causeText = "Core Banking did not confirm that the requested debit account belongs to the authenticated customer.",
            fix = "Select an active synthetic account owned by the authenticated customer.",
            details = mapOf("debitAccountId" to accountId)
        )

    private fun ownershipServiceUnavailable(accountId: String, error: Exception): PaymentDomainException =
        PaymentDomainException(
            code = "PAYMENT_ACCOUNT_OWNERSHIP_CHECK_UNAVAILABLE",
            status = HttpStatus.SERVICE_UNAVAILABLE,
            invariant = "payment creation must fail closed when account ownership cannot be verified",
            policy = "PAYMENT_CUSTOMER_ACCOUNT_OWNERSHIP",
            message = "debit account ownership verification is temporarily unavailable",
            causeText = "Payment Service could not complete the synthetic Core Banking ownership check: ${error.javaClass.simpleName}.",
            fix = "Restore the Core Banking account API and retry with the same idempotency key.",
            details = mapOf("debitAccountId" to accountId)
        )
}
