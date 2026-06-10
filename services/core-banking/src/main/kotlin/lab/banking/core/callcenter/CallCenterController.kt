package lab.banking.core.callcenter

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/staff/call-center")
class CallCenterController(
    private val callCenterService: CallCenterService
) {
    @GetMapping("/customers/search")
    fun searchCallCenterCustomers(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) reason: String?
    ): CallCenterCustomerSearchResponse =
        callCenterService.searchCustomers(query.orEmpty(), reason)

    @PostMapping("/interactions")
    fun startCallCenterInteraction(
        @RequestBody command: StartCallCenterInteractionCommand
    ): ResponseEntity<CallCenterInteractionResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(callCenterService.startInteraction(command))

    @GetMapping("/interactions/{interactionId}")
    fun callCenterInteraction(
        @PathVariable interactionId: String,
        @RequestParam(required = false) reason: String?
    ): CallCenterInteractionResponse =
        callCenterService.interaction(interactionId, reason)

    @PostMapping("/interactions/{interactionId}/notes")
    fun addCallCenterNote(
        @PathVariable interactionId: String,
        @RequestBody command: CallCenterNoteCommand
    ): ResponseEntity<CallCenterNoteResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(callCenterService.addNote(interactionId, command))

    @PostMapping("/interactions/{interactionId}/aftercall-tasks")
    fun createCallCenterAftercallTask(
        @PathVariable interactionId: String,
        @RequestBody command: CallCenterAftercallTaskCommand
    ): ResponseEntity<CallCenterAftercallTaskResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(callCenterService.createAftercallTask(interactionId, command))

    @PostMapping("/interactions/{interactionId}/escalations")
    fun escalateCallCenterInteraction(
        @PathVariable interactionId: String,
        @RequestBody command: CallCenterEscalationCommand
    ): ResponseEntity<CallCenterEscalationResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(callCenterService.escalate(interactionId, command))

    @PostMapping("/interactions/{interactionId}/close")
    fun closeCallCenterInteraction(
        @PathVariable interactionId: String,
        @RequestBody command: CloseCallCenterInteractionCommand
    ): CallCenterInteractionResponse =
        callCenterService.closeInteraction(interactionId, command)

    @GetMapping("/customers/{customerId}/history")
    fun callCenterCustomerHistory(
        @PathVariable customerId: String,
        @RequestParam(required = false) reason: String?
    ): CallCenterInteractionListResponse =
        callCenterService.customerHistory(customerId, reason)
}

