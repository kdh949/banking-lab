package lab.banking.core.account

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
@RequestMapping("/api/staff/accounts/opening-requests")
class AccountOpeningController(
    private val service: AccountOpeningService
) {
    @PostMapping
    @PreAuthorize("hasAnyRole('BRANCH_STAFF','BRANCH_MANAGER','COMPLIANCE_MANAGER')")
    fun requestOpening(@RequestBody command: AccountOpeningRequestCommand): ResponseEntity<AccountOpeningRequestResponse> {
        val response = service.requestOpening(command)
        return ResponseEntity.status(if (response.replayed) HttpStatus.OK else HttpStatus.CREATED).body(response)
    }

    @GetMapping("/{requestId}")
    @PreAuthorize("hasAnyRole('BRANCH_STAFF','BRANCH_MANAGER','OPS_MANAGER','AUDITOR','COMPLIANCE_MANAGER')")
    fun request(@PathVariable requestId: String): ResponseEntity<AccountOpeningRequestResponse> =
        ResponseEntity.ok(service.openingRequest(requestId))

    @PostMapping("/{requestId}/approve")
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER','COMPLIANCE_MANAGER')")
    fun approve(
        @PathVariable requestId: String,
        @RequestBody command: AccountOpeningApproveCommand
    ): ResponseEntity<AccountOpeningReviewResponse> {
        val response = service.approveOpening(requestId, command)
        return ResponseEntity.status(if (response.replayed) HttpStatus.OK else HttpStatus.CREATED).body(response)
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER','COMPLIANCE_MANAGER')")
    fun reject(
        @PathVariable requestId: String,
        @RequestBody command: AccountOpeningRejectCommand
    ): ResponseEntity<AccountOpeningReviewResponse> {
        val response = service.rejectOpening(requestId, command)
        return ResponseEntity.status(if (response.replayed) HttpStatus.OK else HttpStatus.CREATED).body(response)
    }

    @PostMapping("/{requestId}/execute")
    @PreAuthorize("hasAnyRole('BRANCH_STAFF','BRANCH_MANAGER','OPS_MANAGER')")
    fun execute(
        @PathVariable requestId: String,
        @RequestBody command: AccountOpeningExecuteCommand
    ): ResponseEntity<AccountOpeningExecuteResponse> {
        val response = service.executeOpening(requestId, command)
        return ResponseEntity.status(if (response.replayed) HttpStatus.OK else HttpStatus.CREATED).body(response)
    }
}
