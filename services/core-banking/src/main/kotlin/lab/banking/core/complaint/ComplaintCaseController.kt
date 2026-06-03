package lab.banking.core.complaint

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

@RestController
@RequestMapping("/api/staff/complaints")
class ComplaintCaseController(
    private val complaintCaseService: ComplaintCaseService
) {
    @GetMapping
    fun list(): List<ComplaintCaseDto> =
        complaintCaseService.list()

    @GetMapping("/{caseId}")
    fun case(@PathVariable caseId: String): ComplaintCaseDto =
        complaintCaseService.find(caseId)

    @PostMapping("/{caseId}/answer-drafts")
    fun draftAnswer(
        @PathVariable caseId: String,
        @RequestBody command: ComplaintAnswerDraftCommand
    ): ComplaintAnswerDraftResponse =
        complaintCaseService.draftAnswer(caseId, command)
}

@RestController
@RequestMapping("/api/customer/complaints")
class CustomerComplaintController(
    private val complaintCaseService: ComplaintCaseService
) {
    @GetMapping
    fun list(@RequestParam customerId: String): CustomerComplaintListResponse =
        complaintCaseService.listCustomerComplaints(customerId)

    @PostMapping
    fun create(@RequestBody command: CustomerComplaintEntryCommand): ResponseEntity<CustomerComplaintEntryResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(complaintCaseService.createCustomerComplaint(command))

    @PostMapping("/{caseId}/confirm")
    fun confirm(
        @PathVariable caseId: String,
        @RequestBody command: CustomerComplaintConfirmCommand
    ): CustomerComplaintConfirmResponse =
        complaintCaseService.confirmCustomerComplaint(caseId, command)
}
