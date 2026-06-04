package lab.banking.core.product

import java.time.LocalDate
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/fees/policies")
class FeePolicyController(
    private val service: FeePolicyService
) {
    @GetMapping
    fun list(@RequestParam(required = false) asOf: LocalDate?): ResponseEntity<FeePolicyListResponse> =
        ResponseEntity.ok(service.listFeePolicies(asOf ?: LocalDate.now()))

    @GetMapping("/{policyId}")
    fun detail(
        @PathVariable policyId: String,
        @RequestParam(required = false) asOf: LocalDate?
    ): ResponseEntity<FeePolicyDto> =
        ResponseEntity.ok(service.feePolicy(policyId, asOf ?: LocalDate.now()))
}

@RestController
@RequestMapping("/api/staff/fees")
class StaffFeePolicyInquiryController(
    private val service: FeePolicyService
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) accountId: String?,
        @RequestParam(required = false) reason: String?,
        @RequestParam(required = false) asOf: LocalDate?
    ): ResponseEntity<StaffFeePolicyListResponse> =
        ResponseEntity.ok(service.staffFeeInquiry(accountId, reason, asOf ?: LocalDate.now()))
}

@RestController
@RequestMapping("/api/staff/fee-policies")
class StaffFeePolicyController(
    private val service: FeePolicyService
) {
    @PostMapping("/{policyId}/change-requests")
    fun requestPolicyChange(
        @PathVariable policyId: String,
        @RequestBody command: FeePolicyChangeRequestCommand
    ): ResponseEntity<FeePolicyChangeRequestResponse> =
        ResponseEntity.ok(service.requestPolicyChange(policyId, command))
}

@RestController
@RequestMapping("/api/ops")
class OpsFeePostingController(
    private val service: FeePolicyService
) {
    @PostMapping("/fee-posting-batches")
    fun postFeeBatch(@RequestBody command: FeePostingBatchCommand): ResponseEntity<FeePostingBatchResponse> =
        ResponseEntity.ok(service.postFeeBatch(command))
}
