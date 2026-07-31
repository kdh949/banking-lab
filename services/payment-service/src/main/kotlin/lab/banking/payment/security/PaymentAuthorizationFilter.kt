package lab.banking.payment.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import lab.banking.payment.api.PaymentApiError
import lab.banking.payment.api.PaymentApiErrorEnvelope
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@ConditionalOnProperty(prefix = "banking-lab.security", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class PaymentAuthorizationFilter(
    @param:Value("\${banking-lab.security.enabled:true}")
    private val enabled: Boolean,
    private val decoder: PaymentTokenDecoder,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {
    private val instructionRead = Regex("^/api/payments/instructions/[^/]+$")
    private val instructionLedgerPosting = Regex("^/api/payments/instructions/[^/]+/(ledger-postings|settlements)$")
    private val instructionCancel = Regex("^/api/payments/instructions/[^/]+/cancel$")
    private val instructionCancellationRequest = Regex("^/api/payments/instructions/[^/]+/cancellation-requests$")
    private val cancellationRequestReview = Regex("^/api/payments/cancellation-requests/[^/]+/(approve|reject)$")
    private val autopayRead = Regex("^/api/payments/autopay/agreements/[^/]+$")
    private val autopayCommand = Regex("^/api/payments/autopay/agreements/[^/]+/(pause|resume|cancel)$")
    private val paymentSettlementImport = Regex("^/api/payments/settlement/imports/[^/]+$")
    private val paymentSettlementBatchRun = Regex("^/api/payments/settlement/batch-runs/[^/]+$")

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
            deny(request, response, null, HttpStatus.FORBIDDEN, "payment API route has no modeled role policy")
            return
        }
        val principal = decoder.decode(request.getHeader("Authorization"))
        if (principal == null) {
            deny(request, response, null, HttpStatus.UNAUTHORIZED, "authentication token is missing or invalid")
            return
        }
        if (!principal.hasAnyRole(allowedRoles)) {
            deny(request, response, principal, HttpStatus.FORBIDDEN, "actor role is not allowed for this payment API route")
            return
        }
        request.setAttribute(PRINCIPAL_ATTRIBUTE, principal)
        filterChain.doFilter(request, response)
    }

    private fun requiresAuthorization(request: HttpServletRequest): Boolean {
        if (request.method.equals("OPTIONS", ignoreCase = true)) {
            return false
        }
        return request.requestURI.startsWith("/api/payments")
    }

    private fun allowedRoles(request: HttpServletRequest): Set<String> {
        val method = request.method.uppercase()
        val path = request.requestURI
        return when {
            path == "/api/payments/instructions" && method == "POST" -> setOf("CUSTOMER")
            instructionRead.matches(path) && method == "GET" ->
                setOf("CUSTOMER", "BRANCH_STAFF", "BRANCH_MANAGER", "OPS_OPERATOR", "OPS_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER")
            instructionLedgerPosting.matches(path) && method == "POST" -> setOf("PAYMENT_SERVICE", "OPS_OPERATOR")
            instructionCancel.matches(path) && method == "POST" -> setOf("CUSTOMER")
            instructionCancellationRequest.matches(path) && method == "POST" ->
                setOf("BRANCH_STAFF", "BRANCH_MANAGER", "OPS_OPERATOR", "OPS_MANAGER", "COMPLIANCE_MANAGER")
            cancellationRequestReview.matches(path) && method == "POST" -> setOf("BRANCH_MANAGER", "OPS_MANAGER", "COMPLIANCE_MANAGER")
            path == "/api/payments/outbox/ledger-postings/dispatch-next" && method == "POST" -> setOf("PAYMENT_SERVICE", "OPS_OPERATOR", "OPS_MANAGER")
            path == "/api/payments/autopay/agreements" && method == "POST" -> setOf("CUSTOMER")
            autopayRead.matches(path) && method == "GET" ->
                setOf("CUSTOMER", "BRANCH_STAFF", "BRANCH_MANAGER", "OPS_OPERATOR", "OPS_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER")
            autopayCommand.matches(path) && method == "POST" -> setOf("CUSTOMER")
            path == "/api/payments/autopay/executions/due" && method == "POST" -> setOf("PAYMENT_SERVICE", "OPS_OPERATOR", "OPS_MANAGER")
            path == "/api/payments/settlement/imports" && method == "POST" -> setOf("OPS_OPERATOR", "OPS_MANAGER")
            paymentSettlementImport.matches(path) && method == "GET" ->
                setOf("OPS_OPERATOR", "OPS_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER")
            path == "/api/payments/settlement/batch-runs" && method == "POST" -> setOf("OPS_OPERATOR", "OPS_MANAGER")
            paymentSettlementBatchRun.matches(path) && method == "GET" ->
                setOf("OPS_OPERATOR", "OPS_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER")
            else -> emptySet()
        }
    }

    private fun deny(
        request: HttpServletRequest,
        response: HttpServletResponse,
        principal: PaymentPrincipal?,
        status: HttpStatus,
        message: String
    ) {
        val requestId = request.getHeader("x-request-id") ?: "REQ-${UUID.randomUUID()}"
        response.status = status.value()
        response.contentType = "application/json"
        objectMapper.writeValue(
            response.outputStream,
            PaymentApiErrorEnvelope(
                PaymentApiError(
                    code = "PAYMENT_AUTHORIZATION_POLICY_VIOLATION",
                    message = message,
                    statusCode = status.value(),
                    policy = "PAYMENT_RBAC_ROUTE_POLICY",
                    cause = "The Keycloak/OIDC token did not satisfy the modeled payment-service route policy.",
                    fix = "Retry with a valid synthetic Bearer token whose signature, subject, role, and payment route context match the route.",
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
        const val PRINCIPAL_ATTRIBUTE = "lab.banking.payment.security.principal"
    }
}
