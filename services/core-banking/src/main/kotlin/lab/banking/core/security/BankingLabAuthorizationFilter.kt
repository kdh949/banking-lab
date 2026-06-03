package lab.banking.core.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import lab.banking.core.api.StructuredApiError
import lab.banking.core.api.StructuredApiErrorEnvelope
import lab.banking.core.audit.AuditEventAppender
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@ConditionalOnProperty(prefix = "banking-lab.security", name = ["enabled"], havingValue = "true")
class BankingLabAuthorizationFilter(
    @param:Value("\${banking-lab.security.enabled:false}")
    private val enabled: Boolean,
    @param:Value("\${banking-lab.cors.allowed-origins:http://localhost:3001,http://localhost:3002,http://localhost:3003,http://localhost:3004,http://localhost:3005,http://localhost:3006,http://127.0.0.1:3001,http://127.0.0.1:3002,http://127.0.0.1:3003,http://127.0.0.1:3004,http://127.0.0.1:3005,http://127.0.0.1:3006}")
    private val allowedCorsOrigins: String,
    private val decoder: BankingLabTokenDecoder,
    private val auditEvents: AuditEventAppender,
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
        val principal = decoder.decode(request.getHeader("Authorization"))
        if (principal == null) {
            deny(request, response, null, HttpStatus.UNAUTHORIZED, "authentication token is missing or invalid")
            return
        }
        val allowedRoles = allowedRoles(request)
        if (allowedRoles.isNotEmpty() && !principal.hasAnyRole(allowedRoles)) {
            deny(request, response, principal, HttpStatus.FORBIDDEN, "actor role is not allowed for this API route")
            return
        }
        if (customerOwnershipDenied(request, principal)) {
            deny(request, response, principal, HttpStatus.FORBIDDEN, "customer token cannot access another customer")
            return
        }
        try {
            BankingLabAuthContext.set(principal)
            filterChain.doFilter(request, response)
        } finally {
            BankingLabAuthContext.clear()
        }
    }

    private fun requiresAuthorization(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        if (request.method.equals("OPTIONS", ignoreCase = true)) {
            return false
        }
        return path.startsWith("/api/") && path != "/api/health"
    }

    private fun allowedRoles(request: HttpServletRequest): Set<String> {
        val method = request.method
        val path = request.requestURI
        return when {
            path.startsWith("/api/customer/") -> setOf("CUSTOMER")
            path == "/api/staff/pii/unmask" -> setOf("BRANCH_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER")
            path.startsWith("/api/audit/") -> setOf("AUDITOR", "COMPLIANCE_MANAGER")
            path.matches(Regex("^/api/staff/approvals/[^/]+/approve$")) -> setOf("BRANCH_MANAGER", "COMPLIANCE_MANAGER")
            path.startsWith("/api/staff/") -> setOf("BRANCH_STAFF", "BRANCH_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER", "FDS_REVIEWER", "AML_REVIEWER", "COMPLAINT_HANDLER")
            path.startsWith("/api/ops/") -> setOf("OPS_OPERATOR", "BRANCH_MANAGER")
            path.startsWith("/api/approvals/") && method == "POST" -> setOf("BRANCH_MANAGER", "COMPLIANCE_MANAGER")
            path.startsWith("/api/approvals") -> setOf("BRANCH_STAFF", "BRANCH_MANAGER", "COMPLIANCE_MANAGER", "OPS_OPERATOR", "FDS_REVIEWER", "AML_REVIEWER", "COMPLAINT_HANDLER")
            path.startsWith("/api/ledger/") -> setOf("CUSTOMER", "BRANCH_STAFF", "BRANCH_MANAGER", "OPS_OPERATOR")
            else -> emptySet()
        }
    }

    private fun customerOwnershipDenied(request: HttpServletRequest, principal: BankingLabPrincipal): Boolean {
        if (!request.requestURI.startsWith("/api/customer/") || !principal.roles.contains("CUSTOMER")) {
            return false
        }
        val requestedCustomerId = request.getParameter("customerId")?.takeIf { it.isNotBlank() }
            ?: return false
        return principal.customerId != requestedCustomerId
    }

    private fun deny(
        request: HttpServletRequest,
        response: HttpServletResponse,
        principal: BankingLabPrincipal?,
        status: HttpStatus,
        message: String
    ) {
        val requestId = request.getHeader("x-request-id") ?: "REQ-${UUID.randomUUID()}"
        appendDeniedAudit(request, principal, requestId, status, message)
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

    private fun appendDeniedAudit(
        request: HttpServletRequest,
        principal: BankingLabPrincipal?,
        requestId: String,
        status: HttpStatus,
        message: String
    ) {
        val payload = mapOf(
            "requestId" to requestId,
            "route" to request.requestURI,
            "method" to request.method,
            "statusCode" to status.value(),
            "message" to message,
            "syntheticOnly" to true
        )
        auditEvents.append(
            eventType = "AUTHORIZATION_DENIED",
            actorType = if (principal == null) "ANONYMOUS" else "STAFF",
            actorId = principal?.subject ?: "ANONYMOUS",
            actorRole = principal?.roles?.sorted()?.joinToString(",") ?: "ANONYMOUS",
            screenId = screenId(request.requestURI),
            businessReferenceId = request.requestURI,
            reason = "RBAC/ABAC denial",
            payload = payload
        )
    }

    private fun screenId(path: String): String =
        when {
            path.startsWith("/api/staff/approvals") -> "APR-201"
            path.startsWith("/api/staff/customers") -> "CST-002"
            path.startsWith("/api/staff/pii") -> "CST-002"
            path.startsWith("/api/ops/") -> "OPS-201"
            path.startsWith("/api/customer/") -> "CWB-101"
            else -> "API"
        }

}
