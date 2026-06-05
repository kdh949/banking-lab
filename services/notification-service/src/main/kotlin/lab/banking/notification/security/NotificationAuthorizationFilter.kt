package lab.banking.notification.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import lab.banking.notification.api.NotificationApiError
import lab.banking.notification.api.NotificationApiErrorEnvelope
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@ConditionalOnProperty(prefix = "banking-lab.security", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class NotificationAuthorizationFilter(
    @param:Value("\${banking-lab.security.enabled:true}")
    private val enabled: Boolean,
    private val decoder: NotificationTokenDecoder,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {
    private val deliveryRead = Regex("^/api/notifications/deliveries/[^/]+$")
    private val deliveryFailure = Regex("^/api/notifications/deliveries/[^/]+/failures$")
    private val deliveryDelivered = Regex("^/api/notifications/deliveries/[^/]+/delivered$")
    private val templateChangeRead = Regex("^/api/notifications/templates/change-requests/[^/]+$")
    private val templateChangeApprove = Regex("^/api/notifications/templates/change-requests/[^/]+/approve$")
    private val templateChangeReject = Regex("^/api/notifications/templates/change-requests/[^/]+/reject$")
    private val customerPreferences = Regex("^/api/notifications/customers/[^/]+/preferences$")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (!enabled || !requiresAuthorization(request)) {
            filterChain.doFilter(request, response)
            return
        }
        val allowedRoles = allowedRoles(request)
        if (allowedRoles.isEmpty()) {
            deny(request, response, null, HttpStatus.FORBIDDEN, "notification API route has no modeled role policy")
            return
        }
        val principal = decoder.decode(request.getHeader("Authorization"))
        if (principal == null) {
            deny(request, response, null, HttpStatus.UNAUTHORIZED, "authentication token is missing or invalid")
            return
        }
        if (!principal.hasAnyRole(allowedRoles)) {
            deny(request, response, principal, HttpStatus.FORBIDDEN, "actor role is not allowed for this notification API route")
            return
        }
        request.setAttribute(PRINCIPAL_ATTRIBUTE, principal)
        filterChain.doFilter(request, response)
    }

    private fun requiresAuthorization(request: HttpServletRequest): Boolean {
        if (request.method.equals("OPTIONS", ignoreCase = true)) {
            return false
        }
        return request.requestURI.startsWith("/api/notifications")
    }

    private fun allowedRoles(request: HttpServletRequest): Set<String> {
        val method = request.method.uppercase()
        val path = request.requestURI
        return when {
            path == "/api/notifications/events" && method == "POST" ->
                setOf("NOTIFICATION_SERVICE", "CORE_BANKING_SERVICE", "PAYMENT_SERVICE", "OUTBOX_WORKER", "OPS_OPERATOR")
            path == "/api/notifications/deliveries" && method == "GET" ->
                setOf("NOTIFICATION_SERVICE", "OPS_OPERATOR", "OPS_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER")
            deliveryRead.matches(path) && method == "GET" ->
                setOf("NOTIFICATION_SERVICE", "OPS_OPERATOR", "OPS_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER")
            deliveryFailure.matches(path) && method == "POST" -> setOf("NOTIFICATION_SERVICE", "OPS_OPERATOR")
            deliveryDelivered.matches(path) && method == "POST" -> setOf("NOTIFICATION_SERVICE", "OPS_OPERATOR")
            path == "/api/notifications/templates" && method == "GET" ->
                setOf("NOTIFICATION_SERVICE", "OPS_OPERATOR", "OPS_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER")
            path == "/api/notifications/templates/change-requests" && method == "POST" ->
                setOf("OPS_OPERATOR", "OPS_MANAGER", "COMPLIANCE_MANAGER")
            templateChangeRead.matches(path) && method == "GET" ->
                setOf("OPS_OPERATOR", "OPS_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER")
            templateChangeApprove.matches(path) && method == "POST" ->
                setOf("OPS_MANAGER", "COMPLIANCE_MANAGER")
            templateChangeReject.matches(path) && method == "POST" ->
                setOf("OPS_MANAGER", "COMPLIANCE_MANAGER")
            path == "/api/notifications/preferences" && method == "GET" ->
                setOf("NOTIFICATION_SERVICE", "OPS_OPERATOR", "OPS_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER")
            path == "/api/notifications/preferences" && method == "PUT" ->
                setOf("OPS_OPERATOR", "OPS_MANAGER", "COMPLIANCE_MANAGER")
            customerPreferences.matches(path) && method == "GET" -> setOf("CUSTOMER")
            customerPreferences.matches(path) && method == "PUT" -> setOf("CUSTOMER")
            else -> emptySet()
        }
    }

    private fun deny(
        request: HttpServletRequest,
        response: HttpServletResponse,
        principal: NotificationPrincipal?,
        status: HttpStatus,
        message: String
    ) {
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
                    details = mapOf(
                        "actor" to (principal?.subject ?: "ANONYMOUS"),
                        "roles" to (principal?.roles?.sorted() ?: emptyList<String>()),
                        "method" to request.method,
                        "syntheticOnly" to true
                    )
                )
            )
        )
    }

    companion object {
        const val PRINCIPAL_ATTRIBUTE = "notificationPrincipal"
    }
}
