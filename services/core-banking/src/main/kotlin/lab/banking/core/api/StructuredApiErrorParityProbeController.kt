package lab.banking.core.api

import lab.banking.core.common.BankingLabDomainException
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Profile("api-error-parity")
@RestController
@RequestMapping("/api/parity/structured-errors")
class StructuredApiErrorParityProbeController {
    @GetMapping("/{code}")
    fun raise(@PathVariable code: String): Map<String, String> {
        throw when (code) {
            "POLICY_REASON_REQUIRED" -> WorkflowErrors.reasonRequired("staff access requires a business reason")
            "AUTHORIZATION_POLICY_VIOLATION" -> authorizationPolicyViolation()
            "MAKER_CHECKER_SELF_APPROVAL_REJECTED" -> WorkflowErrors.selfApprovalRejected()
            "REQUEST_VALIDATION_FAILED" -> WorkflowErrors.validation("synthetic parity request failed validation")
            "RESOURCE_NOT_FOUND" -> WorkflowErrors.notFound("synthetic parity resource not found")
            "WORKFLOW_STATE_VIOLATION" -> WorkflowErrors.stateViolation("synthetic workflow transition is not allowed")
            "INTERNAL_RUNTIME_ERROR" -> IllegalStateException("synthetic internal runtime parity failure")
            else -> WorkflowErrors.notFound("structured error probe not found: $code")
        }
    }

    private fun authorizationPolicyViolation(): BankingLabDomainException =
        BankingLabDomainException(
            code = "AUTHORIZATION_POLICY_VIOLATION",
            status = HttpStatus.FORBIDDEN,
            domain = "auth",
            policy = "RBAC_ABAC_REQUIRED",
            message = "actor is not allowed for this synthetic operation",
            causeText = "The actor role or business context does not satisfy the migration authorization policy.",
            fix = "Retry with an authorized synthetic actor, role, and business context."
        )
}
