package lab.banking.payment.security

import lab.banking.payment.domain.PaymentDomainException
import org.springframework.http.HttpStatus

data class PaymentCustomerActor(
    val subject: String,
    val customerId: String
)

fun PaymentPrincipal?.requirePaymentCustomer(): PaymentCustomerActor {
    val principal = this ?: throw PaymentDomainException(
        code = "PAYMENT_CUSTOMER_IDENTITY_REQUIRED",
        status = HttpStatus.UNAUTHORIZED,
        policy = "PAYMENT_CUSTOMER_OWNERSHIP",
        message = "authenticated customer identity is required",
        causeText = "Customer payment commands must be bound to a validated bearer-token principal.",
        fix = "Authenticate with a customer token before retrying the payment command."
    )
    if (!principal.roles.contains("CUSTOMER")) {
        throw PaymentDomainException(
            code = "PAYMENT_CUSTOMER_IDENTITY_REQUIRED",
            status = HttpStatus.FORBIDDEN,
            policy = "PAYMENT_CUSTOMER_OWNERSHIP",
            message = "customer role is required for this payment command",
            causeText = "The authenticated principal is not a customer principal.",
            fix = "Use the customer payment route with a CUSTOMER bearer token."
        )
    }
    val customerId = principal.customerId?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw PaymentDomainException(
            code = "PAYMENT_CUSTOMER_CLAIM_REQUIRED",
            status = HttpStatus.FORBIDDEN,
            policy = "PAYMENT_CUSTOMER_OWNERSHIP",
            message = "customer token is missing its customer ownership claim",
            causeText = "Payment ownership cannot be evaluated without the token customerId claim.",
            fix = "Issue a customer token that carries the mapped synthetic customer id."
        )
    return PaymentCustomerActor(subject = principal.subject, customerId = customerId)
}
