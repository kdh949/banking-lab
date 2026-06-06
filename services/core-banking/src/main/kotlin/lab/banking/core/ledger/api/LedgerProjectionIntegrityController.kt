package lab.banking.core.ledger.api

import lab.banking.core.ledger.application.LedgerProjectionDriftRunCommand
import lab.banking.core.ledger.application.LedgerProjectionDriftRunResponse
import lab.banking.core.ledger.application.LedgerProjectionIntegrityService
import lab.banking.core.ledger.application.LedgerProjectionRebuildApproveCommand
import lab.banking.core.ledger.application.LedgerProjectionRebuildExecuteCommand
import lab.banking.core.ledger.application.LedgerProjectionRebuildRejectCommand
import lab.banking.core.ledger.application.LedgerProjectionRebuildRequestCommand
import lab.banking.core.ledger.application.LedgerProjectionRebuildRequestResponse
import lab.banking.core.ledger.application.LedgerProjectionRebuildReviewResponse
import lab.banking.core.ledger.application.LedgerProjectionRebuildRunResponse
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
@RequestMapping("/api/ops/ledger")
class LedgerProjectionIntegrityController(
    private val projectionIntegrityService: LedgerProjectionIntegrityService
) {
    @PostMapping("/projection-drift-runs")
    @PreAuthorize("@bankingLabMethodSecurityPolicy.securityDisabled() or hasAnyRole('OPS_OPERATOR','OPS_MANAGER','COMPLIANCE_MANAGER','AUDITOR')")
    fun startDriftRun(@RequestBody command: LedgerProjectionDriftRunCommand): ResponseEntity<LedgerProjectionDriftRunResponse> {
        val result = projectionIntegrityService.startDriftRun(command)
        return ResponseEntity.status(if (result.replayed) HttpStatus.OK else HttpStatus.CREATED).body(result)
    }

    @GetMapping("/projection-drift-runs/{runId}")
    @PreAuthorize("@bankingLabMethodSecurityPolicy.securityDisabled() or hasAnyRole('OPS_OPERATOR','OPS_MANAGER','COMPLIANCE_MANAGER','AUDITOR')")
    fun driftRun(@PathVariable runId: String): LedgerProjectionDriftRunResponse =
        projectionIntegrityService.driftRun(runId)

    @PostMapping("/projection-rebuild-requests")
    @PreAuthorize("@bankingLabMethodSecurityPolicy.securityDisabled() or hasAnyRole('OPS_OPERATOR','OPS_MANAGER')")
    fun requestRebuild(@RequestBody command: LedgerProjectionRebuildRequestCommand): ResponseEntity<LedgerProjectionRebuildRequestResponse> {
        val result = projectionIntegrityService.requestRebuild(command)
        return ResponseEntity.status(if (result.replayed) HttpStatus.OK else HttpStatus.CREATED).body(result)
    }

    @PostMapping("/projection-rebuild-requests/{requestId}/approve")
    @PreAuthorize("@bankingLabMethodSecurityPolicy.securityDisabled() or hasAnyRole('OPS_MANAGER','COMPLIANCE_MANAGER','BRANCH_MANAGER')")
    fun approveRebuildRequest(
        @PathVariable requestId: String,
        @RequestBody command: LedgerProjectionRebuildApproveCommand
    ): ResponseEntity<LedgerProjectionRebuildReviewResponse> {
        val result = projectionIntegrityService.approveRebuildRequest(requestId, command)
        return ResponseEntity.status(if (result.replayed) HttpStatus.OK else HttpStatus.CREATED).body(result)
    }

    @PostMapping("/projection-rebuild-requests/{requestId}/reject")
    @PreAuthorize("@bankingLabMethodSecurityPolicy.securityDisabled() or hasAnyRole('OPS_MANAGER','COMPLIANCE_MANAGER','BRANCH_MANAGER')")
    fun rejectRebuildRequest(
        @PathVariable requestId: String,
        @RequestBody command: LedgerProjectionRebuildRejectCommand
    ): ResponseEntity<LedgerProjectionRebuildReviewResponse> {
        val result = projectionIntegrityService.rejectRebuildRequest(requestId, command)
        return ResponseEntity.status(if (result.replayed) HttpStatus.OK else HttpStatus.CREATED).body(result)
    }

    @PostMapping("/projection-rebuild-requests/{requestId}/execute")
    @PreAuthorize("@bankingLabMethodSecurityPolicy.securityDisabled() or hasAnyRole('OPS_OPERATOR','OPS_MANAGER')")
    fun executeRebuild(
        @PathVariable requestId: String,
        @RequestBody command: LedgerProjectionRebuildExecuteCommand
    ): ResponseEntity<LedgerProjectionRebuildRunResponse> {
        val result = projectionIntegrityService.executeRebuild(requestId, command)
        return ResponseEntity.status(if (result.replayed) HttpStatus.OK else HttpStatus.CREATED).body(result)
    }

    @GetMapping("/projection-rebuild-runs/{runId}")
    @PreAuthorize("@bankingLabMethodSecurityPolicy.securityDisabled() or hasAnyRole('OPS_OPERATOR','OPS_MANAGER','COMPLIANCE_MANAGER','AUDITOR')")
    fun rebuildRun(@PathVariable runId: String): LedgerProjectionRebuildRunResponse =
        projectionIntegrityService.rebuildRun(runId)
}
