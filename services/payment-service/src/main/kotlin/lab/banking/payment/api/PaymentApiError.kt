package lab.banking.payment.api

import jakarta.servlet.http.HttpServletRequest
import java.util.UUID
import lab.banking.payment.domain.PaymentDomainException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

data class PaymentApiErrorEnvelope(
    val error: PaymentApiError
)

data class PaymentApiError(
    val contractVersion: String = "2026-06-05",
    val code: String,
    val message: String,
    val statusCode: Int,
    val domain: String = "payment",
    val invariant: String? = null,
    val policy: String? = null,
    val cause: String,
    val fix: String,
    val requestId: String,
    val correlationId: String = requestId,
    val route: String? = null,
    val docs: String = "docs/codex/goal-mode/full-platform-completion/features/10-payment-service.md",
    val syntheticOnly: Boolean = true,
    val details: Map<String, Any?>? = null
)

@RestControllerAdvice
class PaymentApiErrorHandler {
    @ExceptionHandler(PaymentDomainException::class)
    fun domain(error: PaymentDomainException, request: HttpServletRequest): ResponseEntity<PaymentApiErrorEnvelope> =
        ResponseEntity.status(error.status).body(
            PaymentApiErrorEnvelope(
                PaymentApiError(
                    code = error.code,
                    message = error.message,
                    statusCode = error.status.value(),
                    invariant = error.invariant,
                    policy = error.policy,
                    cause = error.causeText,
                    fix = error.fix,
                    requestId = requestId(request),
                    route = request.requestURI,
                    details = error.details
                )
            )
        )

    @ExceptionHandler(ResponseStatusException::class)
    fun responseStatus(error: ResponseStatusException, request: HttpServletRequest): ResponseEntity<PaymentApiErrorEnvelope> {
        val statusCode = error.statusCode.value()
        return ResponseEntity.status(statusCode).body(
            PaymentApiErrorEnvelope(
                PaymentApiError(
                    code = if (statusCode == 404) "PAYMENT_RESOURCE_NOT_FOUND" else "PAYMENT_REQUEST_VALIDATION_FAILED",
                    message = error.reason ?: error.message ?: "payment request validation failed",
                    statusCode = statusCode,
                    cause = "The payment-service API rejected the request before payment domain execution.",
                    fix = "Check the payment-service OpenAPI contract and synthetic-only payment command fields.",
                    requestId = requestId(request),
                    route = request.requestURI
                )
            )
        )
    }

    @ExceptionHandler(Exception::class)
    fun internal(error: Exception, request: HttpServletRequest): ResponseEntity<PaymentApiErrorEnvelope> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            PaymentApiErrorEnvelope(
                PaymentApiError(
                    code = "PAYMENT_INTERNAL_RUNTIME_ERROR",
                    message = error.message ?: "unexpected payment-service error",
                    statusCode = 500,
                    cause = "The payment-service raised an unmapped runtime error.",
                    fix = "Map the failure to a payment invariant, idempotency, synthetic-only, outbox, or validation error before claiming parity.",
                    requestId = requestId(request),
                    route = request.requestURI
                )
            )
        )

    private fun requestId(request: HttpServletRequest): String =
        request.getHeader("x-request-id") ?: "REQ-${UUID.randomUUID()}"
}
