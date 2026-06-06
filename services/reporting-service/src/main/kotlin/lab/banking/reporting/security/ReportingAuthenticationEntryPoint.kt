package lab.banking.reporting.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import lab.banking.reporting.api.ReportingApiError
import lab.banking.reporting.api.ReportingApiErrorEnvelope
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

@Component
class ReportingAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper
) : AuthenticationEntryPoint, AccessDeniedHandler {
    override fun commence(request: HttpServletRequest, response: HttpServletResponse, authException: AuthenticationException) {
        deny(request, response, HttpStatus.UNAUTHORIZED, "authentication token is missing or invalid")
    }

    override fun handle(request: HttpServletRequest, response: HttpServletResponse, accessDeniedException: AccessDeniedException) {
        deny(request, response, HttpStatus.FORBIDDEN, "actor role is not allowed for this reporting API route")
    }

    private fun deny(request: HttpServletRequest, response: HttpServletResponse, status: HttpStatus, message: String) {
        val requestId = request.getHeader("x-request-id") ?: "REQ-${UUID.randomUUID()}"
        response.status = status.value()
        response.contentType = "application/json"
        objectMapper.writeValue(
            response.outputStream,
            ReportingApiErrorEnvelope(
                ReportingApiError(
                    code = "REPORTING_AUTHORIZATION_POLICY_VIOLATION",
                    message = message,
                    statusCode = status.value(),
                    policy = "REPORTING_RBAC_ROUTE_POLICY",
                    cause = "The Keycloak/OIDC token did not satisfy the modeled reporting-service route policy.",
                    fix = "Retry with a valid synthetic Bearer token whose subject and role match the reporting route.",
                    requestId = requestId,
                    route = request.requestURI,
                    details = mapOf("method" to request.method, "syntheticOnly" to true)
                )
            )
        )
    }
}
