package lab.banking.core.approval

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
@RequestMapping("/api/approvals")
class ApprovalController(
    private val approvals: PersistentApprovalService
) {
    @PostMapping
    fun submit(@RequestBody command: SubmitApprovalCommand): ResponseEntity<OperatorApproval> =
        ResponseEntity.status(HttpStatus.CREATED).body(approvals.submit(command))

    @GetMapping
    fun list(): List<OperatorApproval> =
        approvals.list()

    @GetMapping("/{approvalId}")
    fun approval(@PathVariable approvalId: String): OperatorApproval =
        approvals.approval(approvalId)

    @PostMapping("/{approvalId}/approve")
    @PreAuthorize("@bankingLabMethodSecurityPolicy.securityDisabled() or hasAnyRole('BRANCH_MANAGER','OPS_MANAGER','COMPLIANCE_MANAGER')")
    fun approve(
        @PathVariable approvalId: String,
        @RequestBody command: ApproveApprovalCommand
    ): OperatorApproval =
        approvals.approve(approvalId, command)

    @PostMapping("/{approvalId}/reject")
    @PreAuthorize("@bankingLabMethodSecurityPolicy.securityDisabled() or hasAnyRole('BRANCH_MANAGER','OPS_MANAGER','COMPLIANCE_MANAGER')")
    fun reject(
        @PathVariable approvalId: String,
        @RequestBody command: RejectApprovalCommand
    ): OperatorApproval =
        approvals.reject(approvalId, command)
}
