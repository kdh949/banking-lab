package lab.banking.reporting.api

import jakarta.servlet.http.HttpServletRequest
import java.util.UUID
import lab.banking.reporting.domain.ReportingDomainException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

data class ReportingApiErrorEnvelope(
    val error: ReportingApiError
)

data class ReportingApiError(
    val contractVersion: String = "2026-06-05",
    val code: String,
    val message: String,
    val statusCode: Int,
    val domain: String = "reporting",
    val policy: String? = null,
    val cause: String,
    val fix: String,
    val requestId: String,
    val correlationId: String = requestId,
    val route: String? = null,
    val docs: String = "docs/codex/goal-mode/full-platform-completion/supporting/03-reporting-service.md",
    val syntheticOnly: Boolean = true,
    val details: Map<String, Any?>? = null
)

@RestControllerAdvice
class ReportingApiErrorHandler {
    @ExceptionHandler(ReportingDomainException::class)
    fun domain(error: ReportingDomainException, request: HttpServletRequest): ResponseEntity<ReportingApiErrorEnvelope> =
        ResponseEntity.status(error.status).body(
            ReportingApiErrorEnvelope(
                ReportingApiError(
                    code = error.code,
                    message = error.message,
                    statusCode = error.status.value(),
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
    fun responseStatus(error: ResponseStatusException, request: HttpServletRequest): ResponseEntity<ReportingApiErrorEnvelope> {
        val statusCode = error.statusCode.value()
        return ResponseEntity.status(statusCode).body(
            ReportingApiErrorEnvelope(
                ReportingApiError(
                    code = if (statusCode == 404) "REPORTING_RESOURCE_NOT_FOUND" else "REPORTING_REQUEST_VALIDATION_FAILED",
                    message = error.reason ?: error.message ?: "reporting request validation failed",
                    statusCode = statusCode,
                    cause = "The reporting-service API rejected the request before reporting domain execution.",
                    fix = "Check the reporting-service API contract and synthetic report fields.",
                    requestId = requestId(request),
                    route = request.requestURI
                )
            )
        )
    }

    @ExceptionHandler(Exception::class)
    fun internal(error: Exception, request: HttpServletRequest): ResponseEntity<ReportingApiErrorEnvelope> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ReportingApiErrorEnvelope(
                ReportingApiError(
                    code = "REPORTING_INTERNAL_RUNTIME_ERROR",
                    message = error.message ?: "unexpected reporting-service error",
                    statusCode = 500,
                    cause = "The reporting-service raised an unmapped runtime error.",
                    fix = "Map the failure to a reporting invariant, masking, audit, authorization, or validation error.",
                    requestId = requestId(request),
                    route = request.requestURI
                )
            )
        )

    private fun requestId(request: HttpServletRequest): String =
        request.getHeader("x-request-id") ?: "REQ-${UUID.randomUUID()}"
}
