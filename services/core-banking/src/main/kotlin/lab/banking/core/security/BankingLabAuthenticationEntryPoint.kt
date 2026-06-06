package lab.banking.core.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import lab.banking.core.api.StructuredApiError
import lab.banking.core.api.StructuredApiErrorEnvelope
import lab.banking.core.audit.AuditEventAppender
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

@Component
class BankingLabAuthenticationEntryPoint(
    @param:Value("\${banking-lab.cors.allowed-origins:http://localhost:3001,http://localhost:3002,http://localhost:3003,http://localhost:3004,http://localhost:3005,http://localhost:3006,http://localhost:3007,http://127.0.0.1:3001,http://127.0.0.1:3002,http://127.0.0.1:3003,http://127.0.0.1:3004,http://127.0.0.1:3005,http://127.0.0.1:3006,http://127.0.0.1:3007}")
    private val allowedCorsOrigins: String,
    private val auditEvents: AuditEventAppender,
    private val objectMapper: ObjectMapper
) : AuthenticationEntryPoint, AccessDeniedHandler {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        deny(request, response, HttpStatus.UNAUTHORIZED, "authentication token is missing or invalid")
    }

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException
    ) {
        deny(request, response, HttpStatus.FORBIDDEN, "actor role is not allowed for this API route")
    }

    private fun deny(
        request: HttpServletRequest,
        response: HttpServletResponse,
        status: HttpStatus,
        message: String
    ) {
        val requestId = request.getHeader("x-request-id") ?: "REQ-${UUID.randomUUID()}"
        auditEvents.append(
            eventType = "AUTHORIZATION_DENIED",
            actorType = "ANONYMOUS",
            actorId = "ANONYMOUS",
            actorRole = "ANONYMOUS",
            screenId = "API",
            businessReferenceId = request.requestURI,
            reason = "OAuth2 Resource Server denial",
            payload = mapOf(
                "requestId" to requestId,
                "route" to request.requestURI,
                "method" to request.method,
                "statusCode" to status.value(),
                "code" to "AUTHORIZATION_POLICY_VIOLATION",
                "policy" to "RBAC_ABAC_POLICY_REQUIRED",
                "message" to message,
                "syntheticOnly" to true
            )
        )
        response.status = status.value()
        response.contentType = "application/json"
        applyCorsHeaders(request, response)
        objectMapper.writeValue(
            response.outputStream,
            StructuredApiErrorEnvelope(
                error = StructuredApiError(
                    code = "AUTHORIZATION_POLICY_VIOLATION",
                    message = message,
                    statusCode = status.value(),
                    domain = "auth",
                    policy = "RBAC_ABAC_POLICY_REQUIRED",
                    cause = "The Keycloak/OIDC token did not satisfy the modeled route policy.",
                    fix = "Retry with a valid synthetic Bearer token whose signature, subject, role, and ownership context match the route.",
                    requestId = requestId,
                    route = request.requestURI
                )
            )
        )
    }

    private fun applyCorsHeaders(request: HttpServletRequest, response: HttpServletResponse) {
        val origin = request.getHeader("Origin") ?: return
        val allowed = allowedCorsOrigins
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        if (origin !in allowed) {
            return
        }
        response.setHeader("Access-Control-Allow-Origin", origin)
        response.addHeader("Vary", "Origin")
        response.setHeader("Access-Control-Expose-Headers", "x-request-id")
    }
}
