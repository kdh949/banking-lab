package lab.banking.core.audit

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/audit/exports")
class AuditExportController(
    private val service: AuditExportService
) {
    @PostMapping
    @PreAuthorize("@bankingLabMethodSecurityPolicy.securityDisabled() or hasAnyRole('AUDITOR','COMPLIANCE_MANAGER')")
    fun requestExport(@RequestBody command: AuditExportRequestCommand): ResponseEntity<AuditExportJobResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.requestExport(command))

    @PostMapping("/{exportId}/approve")
    @PreAuthorize("@bankingLabMethodSecurityPolicy.securityDisabled() or hasAnyRole('AUDITOR','COMPLIANCE_MANAGER')")
    fun approveExport(
        @PathVariable exportId: String,
        @RequestBody command: AuditExportApproveCommand
    ): AuditExportJobResponse =
        service.approveExport(exportId, command)

    @PostMapping("/{exportId}/reject")
    @PreAuthorize("@bankingLabMethodSecurityPolicy.securityDisabled() or hasAnyRole('AUDITOR','COMPLIANCE_MANAGER')")
    fun rejectExport(
        @PathVariable exportId: String,
        @RequestBody command: AuditExportRejectCommand
    ): AuditExportJobResponse =
        service.rejectExport(exportId, command)

    @GetMapping("/{exportId}")
    @PreAuthorize("@bankingLabMethodSecurityPolicy.securityDisabled() or hasAnyRole('AUDITOR','COMPLIANCE_MANAGER')")
    fun export(
        @PathVariable exportId: String,
        @RequestParam actorId: String,
        @RequestParam actorRole: String,
        @RequestParam reason: String?
    ): AuditExportJobResponse =
        service.viewExport(exportId, actorId, actorRole, reason)
}
