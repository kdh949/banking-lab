package lab.banking.payment.security

import java.lang.reflect.Type
import lab.banking.payment.domain.CancelAutopayAgreementRequest
import lab.banking.payment.domain.CancelPaymentInstructionRequest
import lab.banking.payment.domain.CreateAutopayAgreementRequest
import lab.banking.payment.domain.CreatePaymentInstructionRequest
import lab.banking.payment.domain.PauseAutopayAgreementRequest
import lab.banking.payment.domain.ResumeAutopayAgreementRequest
import org.springframework.core.MethodParameter
import org.springframework.http.HttpInputMessage
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter

@RestControllerAdvice
class PaymentCustomerRequestBindingAdvice(
    private val accountOwnershipVerifier: PaymentCustomerAccountOwnershipVerifier
) : RequestBodyAdviceAdapter() {
    override fun supports(
        methodParameter: MethodParameter,
        targetType: Type,
        converterType: Class<out HttpMessageConverter<*>>
    ): Boolean = targetType in customerRequestTypes

    override fun afterBodyRead(
        body: Any,
        inputMessage: HttpInputMessage,
        parameter: MethodParameter,
        targetType: Type,
        converterType: Class<out HttpMessageConverter<*>>
    ): Any {
        val servletRequest = (inputMessage as? ServletServerHttpRequest)?.servletRequest ?: return body
        val principal = servletRequest.getAttribute(PaymentAuthorizationFilter.PRINCIPAL_ATTRIBUTE) as? PaymentPrincipal
            ?: return body
        if (!principal.roles.contains("CUSTOMER")) {
            return body
        }
        val actor = principal.requirePaymentCustomer()
        return when (body) {
            is CreatePaymentInstructionRequest -> {
                accountOwnershipVerifier.requireOwned(
                    customerId = actor.customerId,
                    accountId = body.debitAccountId,
                    authorizationHeader = servletRequest.getHeader("Authorization")
                )
                body.copy(
                    customerId = actor.customerId,
                    requestedBy = actor.subject,
                    requestedChannel = "CUSTOMER_WEB"
                )
            }
            is CancelPaymentInstructionRequest -> body.copy(requestedBy = actor.subject)
            is CreateAutopayAgreementRequest -> {
                accountOwnershipVerifier.requireOwned(
                    customerId = actor.customerId,
                    accountId = body.debitAccountId,
                    authorizationHeader = servletRequest.getHeader("Authorization")
                )
                body.copy(
                    customerId = actor.customerId,
                    requestedBy = actor.subject,
                    requestedChannel = "CUSTOMER_WEB"
                )
            }
            is PauseAutopayAgreementRequest -> body.copy(requestedBy = actor.subject)
            is ResumeAutopayAgreementRequest -> body.copy(requestedBy = actor.subject)
            is CancelAutopayAgreementRequest -> body.copy(requestedBy = actor.subject)
            else -> body
        }
    }

    private companion object {
        val customerRequestTypes: Set<Type> = setOf(
            CreatePaymentInstructionRequest::class.java,
            CancelPaymentInstructionRequest::class.java,
            CreateAutopayAgreementRequest::class.java,
            PauseAutopayAgreementRequest::class.java,
            ResumeAutopayAgreementRequest::class.java,
            CancelAutopayAgreementRequest::class.java
        )
    }
}
