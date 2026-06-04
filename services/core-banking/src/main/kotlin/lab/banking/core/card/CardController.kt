package lab.banking.core.card

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/cards")
class CardController(
    private val service: CardService
) {
    @PostMapping
    fun issue(@RequestBody command: IssueCardCommand): ResponseEntity<CardIssueResponse> {
        val response = service.issue(command)
        return ResponseEntity.status(if (response.replayed) HttpStatus.OK else HttpStatus.CREATED).body(response)
    }

    @GetMapping("/{cardId}")
    fun detail(@PathVariable cardId: String): ResponseEntity<CardDto> =
        ResponseEntity.ok(service.card(cardId))

    @PostMapping("/3ds-simulations")
    fun simulateThreeDs(@RequestBody command: ThreeDsSimulationCommand): ResponseEntity<ThreeDsSimulationDto> =
        ResponseEntity.ok(service.simulateThreeDs(command))

    @PostMapping("/authorizations")
    fun authorize(@RequestBody command: CardAuthorizationCommand): ResponseEntity<CardAuthorizationResponse> =
        ResponseEntity.ok(service.authorize(command))

    @PostMapping("/authorizations/{authorizationId}/captures")
    fun capture(
        @PathVariable authorizationId: String,
        @RequestBody command: CardCaptureCommand
    ): ResponseEntity<CardCaptureResponse> =
        ResponseEntity.ok(service.capture(command.copy(authorizationId = authorizationId)))

    @PostMapping("/authorizations/{authorizationId}/cancel")
    fun cancelAuthorization(
        @PathVariable authorizationId: String,
        @RequestBody command: CardCancelCommand
    ): ResponseEntity<CardAuthorizationResponse> =
        ResponseEntity.ok(service.cancelAuthorization(authorizationId, command))

    @PostMapping("/captures/{captureId}/reverse")
    fun reverseCapture(
        @PathVariable captureId: String,
        @RequestBody command: CardCancelCommand
    ): ResponseEntity<CardCaptureResponse> =
        ResponseEntity.ok(service.reverseCapture(captureId, command))

    @PostMapping("/{cardId}/loss-report")
    fun reportLost(
        @PathVariable cardId: String,
        @RequestBody command: CardLossReportCommand
    ): ResponseEntity<CardDto> =
        ResponseEntity.ok(service.reportLost(cardId, command))
}
