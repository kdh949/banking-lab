package lab.banking.notification.api

import jakarta.servlet.http.HttpServletRequest
import java.util.UUID
import lab.banking.notification.domain.NotificationDomainException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

data class NotificationApiErrorEnvelope(
    val error: NotificationApiError
)

data class NotificationApiError(
    val contractVersion: String = "2026-06-05",
    val code: String,
    val message: String,
    val statusCode: Int,
    val domain: String = "notification",
    val invariant: String? = null,
    val policy: String? = null,
    val cause: String,
    val fix: String,
    val requestId: String,
    val correlationId: String = requestId,
    val route: String? = null,
    val docs: String = "docs/codex/goal-mode/full-platform-completion/features/17-notification-service.md",
    val syntheticOnly: Boolean = true,
    val details: Map<String, Any?>? = null
)

@RestControllerAdvice
class NotificationApiErrorHandler {
    @ExceptionHandler(NotificationDomainException::class)
    fun domain(error: NotificationDomainException, request: HttpServletRequest): ResponseEntity<NotificationApiErrorEnvelope> =
        ResponseEntity.status(error.status).body(
            NotificationApiErrorEnvelope(
                NotificationApiError(
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
    fun responseStatus(error: ResponseStatusException, request: HttpServletRequest): ResponseEntity<NotificationApiErrorEnvelope> {
        val statusCode = error.statusCode.value()
        return ResponseEntity.status(statusCode).body(
            NotificationApiErrorEnvelope(
                NotificationApiError(
                    code = if (statusCode == 404) "NOTIFICATION_RESOURCE_NOT_FOUND" else "NOTIFICATION_REQUEST_VALIDATION_FAILED",
                    message = error.reason ?: error.message ?: "notification request validation failed",
                    statusCode = statusCode,
                    cause = "The notification-service API rejected the request before delivery domain execution.",
                    fix = "Check the notification-service OpenAPI contract and synthetic provider boundary.",
                    requestId = requestId(request),
                    route = request.requestURI
                )
            )
        )
    }

    @ExceptionHandler(Exception::class)
    fun internal(error: Exception, request: HttpServletRequest): ResponseEntity<NotificationApiErrorEnvelope> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            NotificationApiErrorEnvelope(
                NotificationApiError(
                    code = "NOTIFICATION_INTERNAL_RUNTIME_ERROR",
                    message = error.message ?: "unexpected notification-service error",
                    statusCode = 500,
                    cause = "The notification-service raised an unmapped runtime error.",
                    fix = "Map the failure to a notification invariant, idempotency, masking, retry, dead-letter, or synthetic-only error before claiming parity.",
                    requestId = requestId(request),
                    route = request.requestURI
                )
            )
        )

    private fun requestId(request: HttpServletRequest): String =
        request.getHeader("x-request-id") ?: "REQ-${UUID.randomUUID()}"
}
