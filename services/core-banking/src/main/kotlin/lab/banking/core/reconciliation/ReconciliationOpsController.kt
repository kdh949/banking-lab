package lab.banking.core.reconciliation

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/ops/reconciliation-items")
class ReconciliationOpsController(
    private val reconciliationOpsService: ReconciliationOpsService
) {
    @GetMapping
    fun list(): Map<String, List<ReconciliationItemDto>> =
        mapOf("items" to reconciliationOpsService.list())

    @GetMapping("/{itemId}")
    fun item(@PathVariable itemId: String): ReconciliationItemDto =
        reconciliationOpsService.find(itemId)

    @PostMapping("/{itemId}/adjustment-requests")
    fun requestAdjustment(
        @PathVariable itemId: String,
        @RequestBody command: ReconciliationAdjustmentCommand
    ): ResponseEntity<ReconciliationAdjustmentRequestResponse> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(reconciliationOpsService.requestAdjustment(itemId, command))
}
