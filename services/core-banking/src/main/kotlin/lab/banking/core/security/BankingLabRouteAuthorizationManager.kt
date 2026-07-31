package lab.banking.core.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

@Component
class BankingLabRouteAuthorizationManager {
    private val approvalApprove = Regex("^/api/staff/approvals/[^/]+/approve$")
    private val approvalReject = Regex("^/api/staff/approvals/[^/]+/reject$")

    fun allowedRoles(request: HttpServletRequest): Set<String> {
        val method = request.method
        val path = request.requestURI
        return when {
            path.startsWith("/api/customer/") -> setOf("CUSTOMER")
            path.startsWith("/api/customers/") -> STAFF_CUSTOMER_AND_CONTROL_ROLES
            path.startsWith("/api/transactions/") -> STAFF_CUSTOMER_AND_CONTROL_ROLES
            path.startsWith("/api/accounts/") -> STAFF_CUSTOMER_AND_CONTROL_ROLES
            path.startsWith("/api/products/") -> setOf("CUSTOMER", "BRANCH_STAFF", "BRANCH_MANAGER", "OPS_OPERATOR", "OPS_MANAGER", "COMPLIANCE_MANAGER")
            path.startsWith("/api/fees/") -> setOf("CUSTOMER", "BRANCH_STAFF", "BRANCH_MANAGER", "OPS_OPERATOR", "OPS_MANAGER", "COMPLIANCE_MANAGER")
            path.startsWith("/api/loans/") -> setOf("CUSTOMER", "BRANCH_STAFF", "BRANCH_MANAGER", "CALL_CENTER_MANAGER", "OPS_OPERATOR", "OPS_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER")
            path.startsWith("/api/cards/") || path == "/api/cards" -> setOf("CUSTOMER", "BRANCH_STAFF", "BRANCH_MANAGER", "OPS_OPERATOR", "OPS_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER")
            path == "/api/fds/analytics" -> setOf("FDS_REVIEWER", "AML_REVIEWER", "COMPLIANCE_MANAGER", "AUDITOR")
            path.startsWith("/api/aml/governance") -> setOf("AML_REVIEWER", "COMPLIANCE_MANAGER", "AUDITOR")
            path == "/api/staff/pii/unmask" -> setOf("BRANCH_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER")
            path.startsWith("/api/staff/operations/retry-queue") -> setOf("OPS_MANAGER", "COMPLIANCE_MANAGER", "AUDITOR")
            path.startsWith("/api/staff/workflows/") && path.endsWith("/timeline") -> setOf("BRANCH_STAFF", "BRANCH_MANAGER", "CALL_CENTER_AGENT", "CALL_CENTER_MANAGER", "OPS_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER", "FDS_REVIEWER", "AML_REVIEWER", "COMPLAINT_HANDLER")
            path.startsWith("/api/audit/exports") -> setOf("AUDITOR", "COMPLIANCE_MANAGER")
            path.startsWith("/api/audit/") -> setOf("AUDITOR", "COMPLIANCE_MANAGER")
            approvalApprove.matches(path) -> setOf("BRANCH_MANAGER", "OPS_MANAGER", "COMPLIANCE_MANAGER", "CALL_CENTER_MANAGER", "COMPLAINT_HANDLER")
            approvalReject.matches(path) -> setOf("BRANCH_MANAGER", "OPS_MANAGER", "COMPLIANCE_MANAGER", "CALL_CENTER_MANAGER", "COMPLAINT_HANDLER")
            path.startsWith("/api/staff/call-center/") -> setOf("CALL_CENTER_AGENT", "CALL_CENTER_MANAGER", "BRANCH_STAFF", "BRANCH_MANAGER", "COMPLAINT_HANDLER", "COMPLIANCE_MANAGER", "AUDITOR")
            path.startsWith("/api/staff/") -> setOf("BRANCH_STAFF", "BRANCH_MANAGER", "CALL_CENTER_AGENT", "CALL_CENTER_MANAGER", "OPS_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER", "FDS_REVIEWER", "AML_REVIEWER", "COMPLAINT_HANDLER")
            path.startsWith("/api/ops/security") -> setOf("OPS_MANAGER", "COMPLIANCE_MANAGER", "AUDITOR")
            path.startsWith("/api/ops/ledger/projection-drift-runs") -> setOf("OPS_OPERATOR", "OPS_MANAGER", "COMPLIANCE_MANAGER", "AUDITOR")
            path.startsWith("/api/ops/ledger/projection-rebuild-runs") -> setOf("OPS_OPERATOR", "OPS_MANAGER", "COMPLIANCE_MANAGER", "AUDITOR")
            path.startsWith("/api/ops/ledger/projection-rebuild-requests") && path.endsWith("/approve") -> setOf("OPS_MANAGER", "COMPLIANCE_MANAGER", "BRANCH_MANAGER")
            path.startsWith("/api/ops/ledger/projection-rebuild-requests") && path.endsWith("/reject") -> setOf("OPS_MANAGER", "COMPLIANCE_MANAGER", "BRANCH_MANAGER")
            path.startsWith("/api/ops/ledger/projection-rebuild-requests") -> setOf("OPS_OPERATOR", "OPS_MANAGER")
            path.startsWith("/api/ops/") -> setOf("OPS_OPERATOR", "OPS_MANAGER", "BRANCH_MANAGER")
            path.startsWith("/api/admin/") -> setOf("COMPLIANCE_MANAGER", "PASSKEY_RECOVERY_ADMIN")
            path.startsWith("/api/auth/session") -> ALL_INTERACTIVE_ROLES
            path.startsWith("/api/approvals/") && method == "POST" -> setOf("BRANCH_MANAGER", "COMPLIANCE_MANAGER")
            path.startsWith("/api/approvals") -> setOf("BRANCH_STAFF", "BRANCH_MANAGER", "CALL_CENTER_MANAGER", "OPS_MANAGER", "COMPLIANCE_MANAGER", "OPS_OPERATOR", "FDS_REVIEWER", "AML_REVIEWER", "COMPLAINT_HANDLER")
            path == "/api/ledger/payment-postings/evidence" && method == "GET" -> setOf("PAYMENT_SERVICE", "OPS_OPERATOR", "OPS_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER")
            path == "/api/ledger/payment-postings" -> setOf("PAYMENT_SERVICE", "OPS_OPERATOR")
            path.startsWith("/api/ledger/") -> setOf("CUSTOMER", "BRANCH_STAFF", "BRANCH_MANAGER", "OPS_OPERATOR")
            else -> emptySet()
        }
    }

    companion object {
        private val STAFF_CUSTOMER_AND_CONTROL_ROLES = setOf(
            "CUSTOMER",
            "BRANCH_STAFF",
            "BRANCH_MANAGER",
            "CALL_CENTER_AGENT",
            "CALL_CENTER_MANAGER",
            "OPS_MANAGER",
            "AUDITOR",
            "COMPLIANCE_MANAGER"
        )
        private val ALL_INTERACTIVE_ROLES = setOf(
            "CUSTOMER",
            "BRANCH_STAFF",
            "BRANCH_MANAGER",
            "CALL_CENTER_AGENT",
            "CALL_CENTER_MANAGER",
            "OPS_OPERATOR",
            "OPS_MANAGER",
            "AUDITOR",
            "COMPLIANCE_MANAGER",
            "FDS_REVIEWER",
            "AML_REVIEWER",
            "COMPLAINT_HANDLER",
            "PASSKEY_RECOVERY_ADMIN"
        )
    }
}
