package lab.banking.core.onboarding

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/staff/customers/onboarding-requests")
class CustomerOnboardingController(
    private val customerOnboardingService: CustomerOnboardingService
) {
    @PostMapping
    @PreAuthorize("@bankingLabMethodSecurityPolicy.securityDisabled() or hasAnyRole('BRANCH_STAFF','BRANCH_MANAGER','COMPLIANCE_MANAGER')")
    fun requestOnboarding(
        @RequestBody command: CustomerOnboardingRequestCommand
    ): ResponseEntity<CustomerOnboardingRequestResponse> {
        val result = customerOnboardingService.requestOnboarding(command)
        return ResponseEntity.status(if (result.replayed) HttpStatus.OK else HttpStatus.CREATED).body(result)
    }

    @GetMapping("/{requestId}")
    @PreAuthorize("@bankingLabMethodSecurityPolicy.securityDisabled() or hasAnyRole('BRANCH_STAFF','BRANCH_MANAGER','OPS_MANAGER','AUDITOR','COMPLIANCE_MANAGER')")
    fun onboardingRequest(@PathVariable requestId: String): CustomerOnboardingRequestResponse =
        customerOnboardingService.onboardingRequest(requestId)

    @PostMapping("/{requestId}/approve")
    @PreAuthorize("@bankingLabMethodSecurityPolicy.securityDisabled() or hasAnyRole('BRANCH_MANAGER','COMPLIANCE_MANAGER')")
    fun approveOnboarding(
        @PathVariable requestId: String,
        @RequestBody command: CustomerOnboardingApproveCommand
    ): ResponseEntity<CustomerOnboardingReviewResponse> {
        val result = customerOnboardingService.approveOnboarding(requestId, command)
        return ResponseEntity.status(if (result.replayed) HttpStatus.OK else HttpStatus.CREATED).body(result)
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("@bankingLabMethodSecurityPolicy.securityDisabled() or hasAnyRole('BRANCH_MANAGER','COMPLIANCE_MANAGER')")
    fun rejectOnboarding(
        @PathVariable requestId: String,
        @RequestBody command: CustomerOnboardingRejectCommand
    ): ResponseEntity<CustomerOnboardingReviewResponse> {
        val result = customerOnboardingService.rejectOnboarding(requestId, command)
        return ResponseEntity.status(if (result.replayed) HttpStatus.OK else HttpStatus.CREATED).body(result)
    }

    @PostMapping("/{requestId}/execute")
    @PreAuthorize("@bankingLabMethodSecurityPolicy.securityDisabled() or hasAnyRole('BRANCH_STAFF','BRANCH_MANAGER','OPS_MANAGER')")
    fun executeOnboarding(
        @PathVariable requestId: String,
        @RequestBody command: CustomerOnboardingExecuteCommand
    ): ResponseEntity<CustomerOnboardingExecuteResponse> {
        val result = customerOnboardingService.executeOnboarding(requestId, command)
        return ResponseEntity.status(if (result.replayed) HttpStatus.OK else HttpStatus.CREATED).body(result)
    }
}
