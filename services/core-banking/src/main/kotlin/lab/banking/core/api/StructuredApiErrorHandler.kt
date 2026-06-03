package lab.banking.core.api

import jakarta.servlet.http.HttpServletRequest
import java.util.UUID
import lab.banking.core.common.BankingLabDomainException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.servlet.resource.NoResourceFoundException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class StructuredApiErrorHandler {
    @ExceptionHandler(BankingLabDomainException::class)
    fun domain(error: BankingLabDomainException, request: HttpServletRequest): ResponseEntity<StructuredApiErrorEnvelope> =
        ResponseEntity.status(error.status).body(
            StructuredApiErrorEnvelope(
                error = StructuredApiError(
                    code = error.code,
                    message = error.message,
                    statusCode = error.status.value(),
                    domain = error.domain,
                    invariant = error.invariant,
                    policy = error.policy,
                    cause = error.causeText,
                    fix = error.fix,
                    requestId = requestId(request),
                    route = request.requestURI
                )
            )
        )

    @ExceptionHandler(ResponseStatusException::class)
    fun responseStatus(error: ResponseStatusException, request: HttpServletRequest): ResponseEntity<StructuredApiErrorEnvelope> {
        val statusCode = error.statusCode.value()
        return ResponseEntity.status(statusCode).body(
            StructuredApiErrorEnvelope(
                error = StructuredApiError(
                    code = if (statusCode == 404) "RESOURCE_NOT_FOUND" else "REQUEST_VALIDATION_FAILED",
                    message = error.reason ?: error.message ?: "request validation failed",
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

    @ExceptionHandler(NoResourceFoundException::class)
    fun noResource(error: NoResourceFoundException, request: HttpServletRequest): ResponseEntity<StructuredApiErrorEnvelope> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            StructuredApiErrorEnvelope(
                error = StructuredApiError(
                    code = "RESOURCE_NOT_FOUND",
                    message = error.message ?: "resource not found",
                    statusCode = 404,
                    domain = "resource",
                    cause = "The Spring Boot migration scaffold could not find a matching route or static resource.",
                    fix = "Check the API route, screen manifest, and structured error contract.",
                    requestId = requestId(request),
                    route = request.requestURI
                )
            )
        )

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
