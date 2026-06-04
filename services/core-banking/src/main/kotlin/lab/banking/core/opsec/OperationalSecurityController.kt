package lab.banking.core.opsec

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/ops/security")
class OperationalSecurityController(
    private val operationalSecurityService: OperationalSecurityService
) {
    @PostMapping("/audit-exports")
    fun exportAuditSegment(@RequestBody command: AuditWormExportCommand): ResponseEntity<AuditWormSegmentDto> =
        ResponseEntity.status(HttpStatus.CREATED).body(operationalSecurityService.exportAuditSegment(command))

    @GetMapping("/audit-exports/verification")
    fun verifyAuditSegments(): AuditWormVerificationResult =
        operationalSecurityService.verifyAuditSegments()

    @PostMapping("/kms/rotate")
    fun rotateSyntheticKey(@RequestBody command: RotateSyntheticKeyCommand): ResponseEntity<KmsRotationResult> =
        ResponseEntity.status(HttpStatus.CREATED).body(operationalSecurityService.rotateSyntheticKey(command))

    @PostMapping("/break-glass")
    fun requestBreakGlass(@RequestBody command: BreakGlassGrantCommand): ResponseEntity<BreakGlassGrantDto> =
        ResponseEntity.status(HttpStatus.CREATED).body(operationalSecurityService.requestBreakGlass(command))

    @PostMapping("/break-glass/expire-elapsed")
    fun expireElapsedBreakGlassGrants(
        @RequestBody command: ExpireBreakGlassGrantsCommand
    ): ExpireBreakGlassGrantsResult =
        operationalSecurityService.expireElapsedBreakGlassGrants(command)

    @PostMapping("/break-glass/reviews/{reviewCaseId}/close")
    fun closeBreakGlassReview(
        @PathVariable reviewCaseId: String,
        @RequestBody command: CloseBreakGlassReviewCommand
    ): BreakGlassGrantDto =
        operationalSecurityService.closeBreakGlassReview(reviewCaseId, command)
}
