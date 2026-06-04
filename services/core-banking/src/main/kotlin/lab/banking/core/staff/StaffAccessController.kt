package lab.banking.core.staff

import lab.banking.core.approval.ApproveApprovalCommand
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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

    @PostMapping("/pii/unmask")
    fun unmask(@RequestBody command: PiiUnmaskCommand): StaffUnmaskResponse =
        staffAccessService.unmaskCustomer(command)

    @PostMapping("/customers/{customerId}/change-requests")
    fun requestCustomerInfoChange(
        @PathVariable customerId: String,
        @RequestBody command: CustomerInfoChangeCommand
    ): ResponseEntity<CustomerInfoChangeResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(staffAccessService.requestCustomerInfoChange(customerId, command))

    @PostMapping("/accounts/{accountId}/hold-requests")
    fun requestAccountHold(
        @PathVariable accountId: String,
        @RequestBody command: AccountHoldRequestCommand
    ): ResponseEntity<AccountHoldRequestResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(staffAccessService.requestAccountHold(accountId, command))

    @PostMapping("/accounts/{accountId}/hold-release-requests")
    fun requestAccountHoldRelease(
        @PathVariable accountId: String,
        @RequestBody command: AccountHoldReleaseRequestCommand
    ): ResponseEntity<AccountHoldRequestResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(staffAccessService.requestAccountHoldRelease(accountId, command))

    @PostMapping("/approvals/{approvalId}/approve")
    fun approveStaffRequest(
        @PathVariable approvalId: String,
        @RequestBody command: ApproveApprovalCommand
    ): StaffApprovalExecutionResponse =
        staffAccessService.approveStaffRequest(approvalId, command)
}
