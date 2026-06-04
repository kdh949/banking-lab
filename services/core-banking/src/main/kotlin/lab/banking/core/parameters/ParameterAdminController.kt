package lab.banking.core.parameters

import java.time.LocalDate
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ParameterAdminController(
    private val service: ParameterAdminService
) {
    @GetMapping("/api/ops/parameters/reconciliation")
    fun reconciliationParameters(
        @RequestParam(required = false) reason: String?,
        @RequestParam(required = false) asOf: LocalDate?
    ): ParameterListResponse =
        service.parameters("reconciliation", reason, asOf ?: LocalDate.now())

    @GetMapping("/api/ops/parameters/reconciliation/history")
    fun reconciliationHistory(@RequestParam(required = false) reason: String?): ParameterHistoryResponse =
        service.history("reconciliation", reason)

    @PostMapping("/api/ops/parameters/reconciliation/change-requests")
    fun requestReconciliationChange(@RequestBody command: ParameterChangeRequestCommand): ResponseEntity<ParameterChangeRequestResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.requestChange("reconciliation", command))

    @GetMapping("/api/staff/audit-parameters")
    fun auditParameters(
        @RequestParam(required = false) reason: String?,
        @RequestParam(required = false) asOf: LocalDate?
    ): ParameterListResponse =
        service.parameters("audit", reason, asOf ?: LocalDate.now())

    @GetMapping("/api/staff/audit-parameters/history")
    fun auditHistory(@RequestParam(required = false) reason: String?): ParameterHistoryResponse =
        service.history("audit", reason)

    @PostMapping("/api/staff/audit-parameters/change-requests")
    fun requestAuditChange(@RequestBody command: ParameterChangeRequestCommand): ResponseEntity<ParameterChangeRequestResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.requestChange("audit", command))

    @GetMapping("/api/staff/fds-parameters")
    fun fdsParameters(
        @RequestParam(required = false) reason: String?,
        @RequestParam(required = false) asOf: LocalDate?
    ): ParameterListResponse =
        service.parameters("fds", reason, asOf ?: LocalDate.now())

    @GetMapping("/api/staff/fds-parameters/history")
    fun fdsHistory(@RequestParam(required = false) reason: String?): ParameterHistoryResponse =
        service.history("fds", reason)

    @PostMapping("/api/staff/fds-parameters/change-requests")
    fun requestFdsChange(@RequestBody command: ParameterChangeRequestCommand): ResponseEntity<ParameterChangeRequestResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.requestChange("fds", command))

    @GetMapping("/api/admin/platform/security-parameters")
    fun securityParameters(
        @RequestParam(required = false) reason: String?,
        @RequestParam(required = false) asOf: LocalDate?
    ): ParameterListResponse =
        service.parameters("security", reason, asOf ?: LocalDate.now())

    @GetMapping("/api/admin/platform/security-parameters/history")
    fun securityHistory(@RequestParam(required = false) reason: String?): ParameterHistoryResponse =
        service.history("security", reason)

    @PostMapping("/api/admin/platform/security-parameters/change-requests")
    fun requestSecurityChange(@RequestBody command: ParameterChangeRequestCommand): ResponseEntity<ParameterChangeRequestResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.requestChange("security", command))

    @GetMapping("/api/admin/platform/authorization-parameters")
    fun authorizationParameters(
        @RequestParam(required = false) reason: String?,
        @RequestParam(required = false) asOf: LocalDate?
    ): ParameterListResponse =
        service.parameters("authorization", reason, asOf ?: LocalDate.now())

    @GetMapping("/api/admin/platform/authorization-parameters/history")
    fun authorizationHistory(@RequestParam(required = false) reason: String?): ParameterHistoryResponse =
        service.history("authorization", reason)

    @PostMapping("/api/admin/platform/authorization-parameters/change-requests")
    fun requestAuthorizationChange(@RequestBody command: ParameterChangeRequestCommand): ResponseEntity<ParameterChangeRequestResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.requestChange("authorization", command))
}
