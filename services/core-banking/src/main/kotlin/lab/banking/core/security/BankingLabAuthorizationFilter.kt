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
    @param:Value("\${banking-lab.cors.allowed-origins:http://localhost:3001,http://localhost:3002,http://localhost:3003,http://localhost:3004,http://localhost:3005,http://localhost:3006,http://localhost:3007,http://127.0.0.1:3001,http://127.0.0.1:3002,http://127.0.0.1:3003,http://127.0.0.1:3004,http://127.0.0.1:3005,http://127.0.0.1:3006,http://127.0.0.1:3007}")
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
            path.startsWith("/api/customers/") -> setOf("CUSTOMER", "BRANCH_STAFF", "BRANCH_MANAGER", "CALL_CENTER_MANAGER", "OPS_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER")
            path.startsWith("/api/transactions/") -> setOf("CUSTOMER", "BRANCH_STAFF", "BRANCH_MANAGER", "CALL_CENTER_MANAGER", "OPS_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER")
            path.startsWith("/api/accounts/") -> setOf("CUSTOMER", "BRANCH_STAFF", "BRANCH_MANAGER", "CALL_CENTER_MANAGER", "OPS_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER")
            path.startsWith("/api/products/") -> setOf("CUSTOMER", "BRANCH_STAFF", "BRANCH_MANAGER", "OPS_OPERATOR", "OPS_MANAGER", "COMPLIANCE_MANAGER")
            path.startsWith("/api/fees/") -> setOf("CUSTOMER", "BRANCH_STAFF", "BRANCH_MANAGER", "OPS_OPERATOR", "OPS_MANAGER", "COMPLIANCE_MANAGER")
            path.startsWith("/api/loans/") -> setOf("CUSTOMER", "BRANCH_STAFF", "BRANCH_MANAGER", "CALL_CENTER_MANAGER", "OPS_OPERATOR", "OPS_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER")
            path.startsWith("/api/cards/") || path == "/api/cards" -> setOf("CUSTOMER", "BRANCH_STAFF", "BRANCH_MANAGER", "OPS_OPERATOR", "OPS_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER")
            path == "/api/fds/analytics" -> setOf("FDS_REVIEWER", "AML_REVIEWER", "COMPLIANCE_MANAGER", "AUDITOR")
            path == "/api/staff/pii/unmask" -> setOf("BRANCH_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER")
            path.startsWith("/api/audit/") -> setOf("AUDITOR", "COMPLIANCE_MANAGER")
            path.matches(Regex("^/api/staff/approvals/[^/]+/approve$")) -> setOf("BRANCH_MANAGER", "OPS_MANAGER", "COMPLIANCE_MANAGER")
            path.matches(Regex("^/api/staff/approvals/[^/]+/reject$")) -> setOf("BRANCH_MANAGER", "OPS_MANAGER", "COMPLIANCE_MANAGER")
            path.startsWith("/api/staff/") -> setOf("BRANCH_STAFF", "BRANCH_MANAGER", "CALL_CENTER_MANAGER", "OPS_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER", "FDS_REVIEWER", "AML_REVIEWER", "COMPLAINT_HANDLER")
            path.startsWith("/api/ops/") -> setOf("OPS_OPERATOR", "OPS_MANAGER", "BRANCH_MANAGER")
            path.startsWith("/api/admin/") -> setOf("COMPLIANCE_MANAGER", "PASSKEY_RECOVERY_ADMIN")
            path.startsWith("/api/approvals/") && method == "POST" -> setOf("BRANCH_MANAGER", "COMPLIANCE_MANAGER")
            path.startsWith("/api/approvals") -> setOf("BRANCH_STAFF", "BRANCH_MANAGER", "CALL_CENTER_MANAGER", "OPS_MANAGER", "COMPLIANCE_MANAGER", "OPS_OPERATOR", "FDS_REVIEWER", "AML_REVIEWER", "COMPLAINT_HANDLER")
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
            path.startsWith("/api/products/deposits") -> "PRD-101"
            path.startsWith("/api/staff/products/deposits") -> "PRD-102"
            path.startsWith("/api/fees/policies") -> "FEE-101"
            path.startsWith("/api/loans/products") -> "CWB-501"
            path.startsWith("/api/loans/applications") -> "CWB-501"
            path.startsWith("/api/loans/") && path.contains("repayments") -> "CWB-503"
            path.startsWith("/api/loans/") && path.contains("prepayments") -> "CWB-504"
            path.startsWith("/api/loans/") && path.contains("accruals") -> "LON-103"
            path.startsWith("/api/loans/") -> "CWB-502"
            path.startsWith("/api/cards/3ds") -> "CWB-605"
            path.startsWith("/api/cards/authorizations") && path.contains("captures") -> "CWB-603"
            path.startsWith("/api/cards/authorizations") && path.contains("cancel") -> "CWB-604"
            path.startsWith("/api/cards/authorizations") -> "CWB-602"
            path.startsWith("/api/cards/") && path.contains("loss-report") -> "CWB-606"
            path.startsWith("/api/cards") -> "CWB-601"
            path.startsWith("/api/staff/fee-policies") -> "FEE-103"
            path.startsWith("/api/ops/interest-accruals") -> "OPS-401"
            path.startsWith("/api/ops/interest-posting-batches") -> "OPS-402"
            path.startsWith("/api/ops/fee-posting-batches") -> "OPS-403"
            path.startsWith("/api/ops/parameters/reconciliation") -> "OPS-301"
            path.startsWith("/api/ops/eod") -> "OPS-101"
            path.startsWith("/api/staff/audit-parameters") -> "AUD-201"
            path.startsWith("/api/staff/fds-parameters") -> "FDS-301"
            path == "/api/fds/analytics" -> "FDS-301"
            path.startsWith("/api/admin/platform/security-parameters") -> "ADM-201"
            path.startsWith("/api/admin/platform/authorization-parameters") -> "ADM-301"
            path.startsWith("/api/customers/") && path.contains("access-history") -> "CWB-401"
            path.startsWith("/api/customers/") && path.contains("statements") -> "CWB-103"
            path.startsWith("/api/transactions/") && path.contains("confirmation") -> "LED-102"
            path.startsWith("/api/accounts/") && path.contains("balance-certificate") -> "ACC-102"
            path.startsWith("/api/staff/transactions") && path.contains("correction") -> "LED-103"
            path.startsWith("/api/staff/accounts") && path.contains("fee-waiver") -> "FEE-102"
            path.startsWith("/api/staff/accounts") && path.contains("limit-change") -> "LIM-102"
            path.startsWith("/api/staff/accounts") && path.contains("hold-release") -> "ACC-104"
            path.startsWith("/api/staff/accounts") && path.contains("hold") -> "ACC-103"
            path.startsWith("/api/staff/customers") && path.contains("kyc-review") -> "KYC-101"
            path.startsWith("/api/staff/customers") && path.contains("transfer-limits") -> "LIM-101"
            path.startsWith("/api/staff/customers") -> "CST-002"
            path.startsWith("/api/staff/pii") -> "CST-002"
            path.startsWith("/api/ops/") -> "OPS-201"
            path.startsWith("/api/admin/") -> "ADM-101"
            path.startsWith("/api/customer/") -> "CWB-101"
            else -> "API"
        }

}
