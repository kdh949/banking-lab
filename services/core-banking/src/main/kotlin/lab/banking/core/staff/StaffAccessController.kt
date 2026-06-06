package lab.banking.core.staff

import lab.banking.core.approval.ApproveApprovalCommand
import lab.banking.core.approval.RejectApprovalCommand
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/staff")
class StaffAccessController(
    private val staffAccessService: StaffAccessService
) {
    @GetMapping("/customers/search")
    fun searchCustomers(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) reason: String?
    ): StaffAccessListResponse<MaskedCustomerDto> =
        staffAccessService.searchCustomers(query.orEmpty(), reason)

    @GetMapping("/customers/{customerId}/detail")
    fun customerDetail(
        @PathVariable customerId: String,
        @RequestParam(required = false) reason: String?
    ): StaffAccessItemResponse<StaffCustomerDetailDto> =
        staffAccessService.customerDetail(customerId, reason)

    @GetMapping("/accounts/search")
    fun searchAccounts(
        @RequestParam(required = false) customerId: String?,
        @RequestParam(required = false) accountId: String?,
        @RequestParam(required = false) reason: String?
    ): StaffAccessListResponse<StaffAccountDto> =
        staffAccessService.searchAccounts(customerId, accountId, reason)

    @GetMapping("/transactions/search")
    fun searchTransactions(
        @RequestParam(required = false) accountId: String?,
        @RequestParam(required = false) reason: String?
    ): StaffAccessListResponse<StaffTransactionDto> =
        staffAccessService.searchTransactions(accountId, reason)

    @GetMapping("/customers/{customerId}/transfer-limits")
    fun transferLimits(
        @PathVariable customerId: String,
        @RequestParam(required = false) reason: String?
    ): StaffAccessListResponse<StaffTransferLimitDto> =
        staffAccessService.transferLimits(customerId, reason)

    @GetMapping("/operations/retry-queue")
    fun operationalRetryQueue(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) reason: String?
    ): StaffAccessListResponse<OperationalRetryQueueItemDto> =
        staffAccessService.operationalRetryQueue(status, reason)

    @GetMapping("/workflows/{businessReferenceId}/timeline")
    fun workflowTimeline(
        @PathVariable businessReferenceId: String,
        @RequestParam(required = false) reason: String?
    ): StaffAccessListResponse<StaffWorkflowTimelineEntryDto> =
        staffAccessService.workflowTimeline(businessReferenceId, reason)

    @PostMapping("/pii/unmask")
    @PreAuthorize("@bankingLabMethodSecurityPolicy.securityDisabled() or hasAnyRole('BRANCH_MANAGER','AUDITOR','COMPLIANCE_MANAGER')")
    fun unmask(@RequestBody command: PiiUnmaskCommand): StaffUnmaskResponse =
        staffAccessService.unmaskCustomer(command)

    @PostMapping("/customers/{customerId}/change-requests")
    fun requestCustomerInfoChange(
        @PathVariable customerId: String,
        @RequestBody command: CustomerInfoChangeCommand
    ): ResponseEntity<CustomerInfoChangeResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(staffAccessService.requestCustomerInfoChange(customerId, command))

    @PostMapping("/accounts/{accountId}/hold-requests")
    @PreAuthorize("@bankingLabMethodSecurityPolicy.securityDisabled() or hasAnyRole('BRANCH_STAFF','BRANCH_MANAGER','OPS_MANAGER','COMPLIANCE_MANAGER')")
    fun requestAccountHold(
        @PathVariable accountId: String,
        @RequestBody command: AccountHoldRequestCommand
    ): ResponseEntity<AccountHoldRequestResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(staffAccessService.requestAccountHold(accountId, command))

    @PostMapping("/accounts/{accountId}/hold-release-requests")
    @PreAuthorize("@bankingLabMethodSecurityPolicy.securityDisabled() or hasAnyRole('BRANCH_STAFF','BRANCH_MANAGER','OPS_MANAGER','COMPLIANCE_MANAGER')")
    fun requestAccountHoldRelease(
        @PathVariable accountId: String,
        @RequestBody command: AccountHoldReleaseRequestCommand
    ): ResponseEntity<AccountHoldRequestResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(staffAccessService.requestAccountHoldRelease(accountId, command))

    @PostMapping("/accounts/{accountId}/limit-change-requests")
    @PreAuthorize("@bankingLabMethodSecurityPolicy.securityDisabled() or hasAnyRole('BRANCH_STAFF','BRANCH_MANAGER','OPS_MANAGER','COMPLIANCE_MANAGER')")
    fun requestTransferLimitChange(
        @PathVariable accountId: String,
        @RequestBody command: TransferLimitChangeRequestCommand
    ): ResponseEntity<TransferLimitChangeRequestResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(staffAccessService.requestTransferLimitChange(accountId, command))

    @PostMapping("/customers/{customerId}/kyc-review-requests")
    fun requestCustomerKycReview(
        @PathVariable customerId: String,
        @RequestBody command: CustomerKycReviewRequestCommand
    ): ResponseEntity<CustomerKycReviewRequestResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(staffAccessService.requestCustomerKycReview(customerId, command))

    @PostMapping("/accounts/{accountId}/fee-waiver-requests")
    fun requestFeeWaiver(
        @PathVariable accountId: String,
        @RequestBody command: FeeWaiverRequestCommand
    ): ResponseEntity<FeeWaiverRequestResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(staffAccessService.requestFeeWaiver(accountId, command))

    @PostMapping("/transactions/{transactionId}/correction-requests")
    @PreAuthorize("@bankingLabMethodSecurityPolicy.securityDisabled() or hasAnyRole('BRANCH_STAFF','BRANCH_MANAGER','OPS_MANAGER','COMPLIANCE_MANAGER')")
    fun requestTransactionCorrection(
        @PathVariable transactionId: String,
        @RequestBody command: TransactionCorrectionRequestCommand
    ): ResponseEntity<TransactionCorrectionRequestResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(staffAccessService.requestTransactionCorrection(transactionId, command))

    @PostMapping("/approvals/{approvalId}/approve")
    fun approveStaffRequest(
        @PathVariable approvalId: String,
        @RequestBody command: ApproveApprovalCommand
    ): StaffApprovalExecutionResponse =
        staffAccessService.approveStaffRequest(approvalId, command)

    @PostMapping("/approvals/{approvalId}/reject")
    fun rejectStaffRequest(
        @PathVariable approvalId: String,
        @RequestBody command: RejectApprovalCommand
    ): StaffApprovalRejectionResponse =
        staffAccessService.rejectStaffRequest(approvalId, command)
}
