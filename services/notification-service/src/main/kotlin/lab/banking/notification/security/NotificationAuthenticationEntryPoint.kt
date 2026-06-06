package lab.banking.notification.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import lab.banking.notification.api.NotificationApiError
import lab.banking.notification.api.NotificationApiErrorEnvelope
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

@Component
class NotificationAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper
) : AuthenticationEntryPoint, AccessDeniedHandler {
    override fun commence(request: HttpServletRequest, response: HttpServletResponse, authException: AuthenticationException) {
        deny(request, response, HttpStatus.UNAUTHORIZED, "authentication token is missing or invalid")
    }

    override fun handle(request: HttpServletRequest, response: HttpServletResponse, accessDeniedException: AccessDeniedException) {
        deny(request, response, HttpStatus.FORBIDDEN, "actor role is not allowed for this notification API route")
    }

    private fun deny(request: HttpServletRequest, response: HttpServletResponse, status: HttpStatus, message: String) {
        val requestId = request.getHeader("x-request-id") ?: "REQ-${UUID.randomUUID()}"
        response.status = status.value()
        response.contentType = "application/json"
        objectMapper.writeValue(
            response.outputStream,
            NotificationApiErrorEnvelope(
                NotificationApiError(
                    code = "NOTIFICATION_AUTHORIZATION_POLICY_VIOLATION",
                    message = message,
                    statusCode = status.value(),
                    policy = "NOTIFICATION_RBAC_ROUTE_POLICY",
                    cause = "The Keycloak/OIDC token did not satisfy the modeled notification-service route policy.",
                    fix = "Retry with a valid synthetic Bearer token whose signature, subject, role, and notification route context match the route.",
                    requestId = requestId,
                    route = request.requestURI,
                    details = mapOf("method" to request.method, "syntheticOnly" to true)
                )
            )
        )
    }
}
