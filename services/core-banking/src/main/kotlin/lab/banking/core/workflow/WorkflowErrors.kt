package lab.banking.core.workflow

import lab.banking.core.common.BankingLabDomainException
import org.springframework.http.HttpStatus

object WorkflowErrors {
    fun reasonRequired(message: String = "high-risk operation requires a business reason"): BankingLabDomainException =
        BankingLabDomainException(
            code = "POLICY_REASON_REQUIRED",
            status = HttpStatus.BAD_REQUEST,
            domain = "audit",
            policy = "REASON_REQUIRED",
            message = message,
            causeText = "A sensitive or high-risk banking operation was requested without an explicit business reason.",
            fix = "Retry with a non-empty reason tied to the customer, account, case, approval, or reconciliation context."
        )

    fun validation(message: String): BankingLabDomainException =
        BankingLabDomainException(
            code = "REQUEST_VALIDATION_FAILED",
            status = HttpStatus.BAD_REQUEST,
            domain = "validation",
            message = message,
            causeText = "The request is missing a required field or contains an invalid command value.",
            fix = "Correct the request payload according to the API contract and workflow manifest."
        )

    fun notFound(message: String): BankingLabDomainException =
        BankingLabDomainException(
            code = "RESOURCE_NOT_FOUND",
            status = HttpStatus.NOT_FOUND,
            domain = "resource",
            message = message,
            causeText = "The requested synthetic resource does not exist in the current lab state.",
            fix = "Use a seeded synthetic identifier or create the resource through the modeled workflow first."
        )

    fun selfApprovalRejected(): BankingLabDomainException =
        BankingLabDomainException(
            code = "MAKER_CHECKER_SELF_APPROVAL_REJECTED",
            status = HttpStatus.CONFLICT,
            domain = "maker-checker",
            policy = "MAKER_CHECKER_SEPARATION_OF_DUTIES",
            message = "maker and checker must be different users",
            causeText = "The requester attempted to approve the same high-risk operation.",
            fix = "Submit approval with a different checker actor who has the required approval role."
        )

    fun stateViolation(message: String): BankingLabDomainException =
        BankingLabDomainException(
            code = "WORKFLOW_STATE_VIOLATION",
            status = HttpStatus.CONFLICT,
            domain = "workflow",
            policy = "VALID_WORKFLOW_TRANSITION_REQUIRED",
            message = message,
            causeText = "The requested workflow transition is not valid from the current state.",
            fix = "Inspect the case or approval timeline and retry with an allowed state transition."
        )
}
