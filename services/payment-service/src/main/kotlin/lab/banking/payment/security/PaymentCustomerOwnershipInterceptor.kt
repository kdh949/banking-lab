package lab.banking.payment.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import lab.banking.payment.domain.PaymentDomainException
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Component
class PaymentCustomerOwnershipInterceptor(
    private val jdbc: NamedParameterJdbcTemplate
) : HandlerInterceptor {
    private val instructionRead = Regex("^/api/payments/instructions/([^/]+)$")
    private val instructionCancel = Regex("^/api/payments/instructions/([^/]+)/cancel$")
    private val autopayRead = Regex("^/api/payments/autopay/agreements/([^/]+)$")
    private val autopayCommand = Regex("^/api/payments/autopay/agreements/([^/]+)/(pause|resume|cancel)$")

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val principal = request.getAttribute(PaymentAuthorizationFilter.PRINCIPAL_ATTRIBUTE) as? PaymentPrincipal
            ?: return true
        if (!principal.roles.contains("CUSTOMER")) {
            return true
        }
        val actor = principal.requirePaymentCustomer()
        val method = request.method.uppercase()
        val path = request.requestURI

        when {
            method == "GET" && instructionRead.matches(path) ->
                requireInstructionOwned(instructionRead.matchEntire(path)!!.groupValues[1], actor.customerId)
            method == "POST" && instructionCancel.matches(path) ->
                requireInstructionOwned(instructionCancel.matchEntire(path)!!.groupValues[1], actor.customerId)
            method == "GET" && autopayRead.matches(path) ->
                requireAutopayOwned(autopayRead.matchEntire(path)!!.groupValues[1], actor.customerId)
            method == "POST" && autopayCommand.matches(path) ->
                requireAutopayOwned(autopayCommand.matchEntire(path)!!.groupValues[1], actor.customerId)
        }
        return true
    }

    private fun requireInstructionOwned(instructionId: String, customerId: String) {
        if (!owned("payment_instructions", "payment_instruction_id", instructionId, customerId)) {
            throw concealedNotFound(
                code = "PAYMENT_INSTRUCTION_NOT_FOUND",
                message = "payment instruction was not found",
                referenceName = "paymentInstructionId",
                referenceId = instructionId
            )
        }
    }

    private fun requireAutopayOwned(agreementId: String, customerId: String) {
        val ownedAndBound = jdbc.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1
              FROM payment_autopay_agreements
              WHERE autopay_agreement_id = :id
                AND customer_id = :customerId
                AND customer_identity_bound = true
            )
            """.trimIndent(),
            mapOf("id" to agreementId, "customerId" to customerId),
            Boolean::class.java
        ) == true
        if (!ownedAndBound) {
            throw concealedNotFound(
                code = "PAYMENT_AUTOPAY_NOT_FOUND",
                message = "autopay agreement was not found",
                referenceName = "autopayAgreementId",
                referenceId = agreementId
            )
        }
    }

    private fun owned(table: String, idColumn: String, id: String, customerId: String): Boolean =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM $table WHERE $idColumn = :id AND customer_id = :customerId)",
            mapOf("id" to id, "customerId" to customerId),
            Boolean::class.java
        ) == true

    private fun concealedNotFound(
        code: String,
        message: String,
        referenceName: String,
        referenceId: String
    ): PaymentDomainException =
        PaymentDomainException(
            code = code,
            status = HttpStatus.NOT_FOUND,
            invariant = "customer payment resources are visible only to their owner",
            policy = "PAYMENT_CUSTOMER_OWNERSHIP",
            message = message,
            causeText = "The resource does not exist or is not owned by the authenticated customer.",
            fix = "Use a payment resource created by the authenticated synthetic customer.",
            details = mapOf(referenceName to referenceId)
        )
}

@Configuration
class PaymentCustomerOwnershipWebMvcConfig(
    private val interceptor: PaymentCustomerOwnershipInterceptor
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/payments/**")
    }
}
