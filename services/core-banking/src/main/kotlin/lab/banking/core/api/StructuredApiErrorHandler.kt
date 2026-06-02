package lab.banking.core.api

import jakarta.servlet.http.HttpServletRequest
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class StructuredApiErrorHandler {
    @ExceptionHandler(ResponseStatusException::class)
    fun responseStatus(error: ResponseStatusException, request: HttpServletRequest): ResponseEntity<StructuredApiErrorEnvelope> {
        val statusCode = error.statusCode.value()
        return ResponseEntity.status(statusCode).body(
            StructuredApiErrorEnvelope(
                error = StructuredApiError(
                    code = if (statusCode == 404) "RESOURCE_NOT_FOUND" else "REQUEST_VALIDATION_FAILED",
                    message = error.reason ?: error.message,
                    statusCode = statusCode,
                    domain = if (statusCode == 404) "resource" else "validation",
                    cause = "The Spring Boot migration scaffold rejected the request before domain execution.",
                    fix = "Check the OpenAPI contract, screen manifest, and structured error contract.",
                    requestId = requestId(request),
                    route = request.requestURI
                )
            )
        )
    }

    @ExceptionHandler(Exception::class)
    fun internal(error: Exception, request: HttpServletRequest): ResponseEntity<StructuredApiErrorEnvelope> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            StructuredApiErrorEnvelope(
                error = StructuredApiError(
                    code = "INTERNAL_RUNTIME_ERROR",
                    message = error.message ?: "unexpected Spring Boot migration error",
                    statusCode = 500,
                    domain = "runtime",
                    cause = "The Kotlin/Spring Boot scaffold raised an unmapped error.",
                    fix = "Map the failure to a ledger, audit, masking, maker-checker, workflow, validation, or resource error before parity is considered complete.",
                    requestId = requestId(request),
                    route = request.requestURI
                )
            )
        )

    private fun requestId(request: HttpServletRequest): String =
        request.getHeader("x-request-id") ?: "REQ-${UUID.randomUUID()}"
}
