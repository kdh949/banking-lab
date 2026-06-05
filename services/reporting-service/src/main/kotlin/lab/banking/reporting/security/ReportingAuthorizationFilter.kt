package lab.banking.reporting.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import lab.banking.reporting.api.ReportingApiError
import lab.banking.reporting.api.ReportingApiErrorEnvelope
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@ConditionalOnProperty(prefix = "banking-lab.security", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class ReportingAuthorizationFilter(
    @param:Value("\${banking-lab.security.enabled:true}")
    private val enabled: Boolean,
    private val decoder: ReportingTokenDecoder,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {
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
            deny(request, response, null, HttpStatus.FORBIDDEN, "reporting API route has no modeled role policy")
            return
        }
        val principal = decoder.decode(request.getHeader("Authorization"))
        if (principal == null) {
            deny(request, response, null, HttpStatus.UNAUTHORIZED, "authentication token is missing or invalid")
            return
        }
        if (!principal.hasAnyRole(allowedRoles)) {
            deny(request, response, principal, HttpStatus.FORBIDDEN, "actor role is not allowed for this reporting API route")
            return
        }
        request.setAttribute(PRINCIPAL_ATTRIBUTE, principal)
        filterChain.doFilter(request, response)
    }

    private fun requiresAuthorization(request: HttpServletRequest): Boolean {
        if (request.method.equals("OPTIONS", ignoreCase = true)) {
            return false
        }
        return request.requestURI.startsWith("/api/reports")
    }

    private fun allowedRoles(request: HttpServletRequest): Set<String> {
        val path = request.requestURI
        return if (path.startsWith("/api/reports")) {
            setOf("AUDITOR", "COMPLIANCE_MANAGER", "OPS_MANAGER", "REPORTING_ANALYST")
        } else {
            emptySet()
        }
    }

    private fun deny(
        request: HttpServletRequest,
        response: HttpServletResponse,
        principal: ReportingPrincipal?,
        status: HttpStatus,
        message: String
    ) {
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
        const val PRINCIPAL_ATTRIBUTE = "lab.banking.reporting.security.principal"
    }
}
