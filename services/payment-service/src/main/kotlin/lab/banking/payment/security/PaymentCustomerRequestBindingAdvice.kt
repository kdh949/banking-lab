package lab.banking.payment.security

import jakarta.servlet.http.HttpServletRequest
import lab.banking.payment.domain.CancelAutopayAgreementRequest
import lab.banking.payment.domain.CancelPaymentInstructionRequest
import lab.banking.payment.domain.CreateAutopayAgreementRequest
import lab.banking.payment.domain.CreatePaymentInstructionRequest
import lab.banking.payment.domain.PauseAutopayAgreementRequest
import lab.banking.payment.domain.ResumeAutopayAgreementRequest
import org.springframework.stereotype.Component

@Component
class PaymentCustomerRequestBinder(
    private val accountOwnershipVerifier: PaymentCustomerAccountOwnershipVerifier
) {
    fun bindCreateInstruction(
        request: CreatePaymentInstructionRequest,
        servletRequest: HttpServletRequest
    ): CreatePaymentInstructionRequest {
        val actor = customerActor(servletRequest)
        accountOwnershipVerifier.requireOwned(
            customerId = actor.customerId,
            accountId = request.debitAccountId,
            authorizationHeader = servletRequest.getHeader("Authorization")
        )
        return request.copy(
            customerId = actor.customerId,
            requestedBy = actor.subject,
            requestedChannel = "CUSTOMER_WEB"
        )
    }

    fun bindCancelInstruction(
        request: CancelPaymentInstructionRequest,
        servletRequest: HttpServletRequest
    ): CancelPaymentInstructionRequest =
        request.copy(requestedBy = customerActor(servletRequest).subject)

    fun bindCreateAutopayAgreement(
        request: CreateAutopayAgreementRequest,
        servletRequest: HttpServletRequest
    ): CreateAutopayAgreementRequest {
        val actor = customerActor(servletRequest)
        accountOwnershipVerifier.requireOwned(
            customerId = actor.customerId,
            accountId = request.debitAccountId,
            authorizationHeader = servletRequest.getHeader("Authorization")
        )
        return request.copy(
            customerId = actor.customerId,
            requestedBy = actor.subject,
            requestedChannel = "CUSTOMER_WEB"
        )
    }

    fun bindPauseAutopayAgreement(
        request: PauseAutopayAgreementRequest,
        servletRequest: HttpServletRequest
    ): PauseAutopayAgreementRequest =
        request.copy(requestedBy = customerActor(servletRequest).subject)

    fun bindResumeAutopayAgreement(
        request: ResumeAutopayAgreementRequest,
        servletRequest: HttpServletRequest
    ): ResumeAutopayAgreementRequest =
        request.copy(requestedBy = customerActor(servletRequest).subject)

    fun bindCancelAutopayAgreement(
        request: CancelAutopayAgreementRequest,
        servletRequest: HttpServletRequest
    ): CancelAutopayAgreementRequest =
        request.copy(requestedBy = customerActor(servletRequest).subject)

    private fun customerActor(servletRequest: HttpServletRequest): PaymentCustomerActor =
        (servletRequest.getAttribute(PaymentAuthorizationFilter.PRINCIPAL_ATTRIBUTE) as? PaymentPrincipal)
            .requirePaymentCustomer()
}
