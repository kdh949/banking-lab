package lab.banking.core.customer

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/customer")
class CustomerSelfServiceController(
    private val customerSelfServiceService: CustomerSelfServiceService
) {
    @GetMapping("/me")
    fun profile(): CustomerProfileDto =
        customerSelfServiceService.profile()

    @PostMapping("/account-opening-requests")
    fun requestAccountOpening(
        @RequestBody command: CustomerSelfServiceAccountOpeningCommand
    ): ResponseEntity<CustomerSelfServiceAccountOpeningRequestResponse> {
        val result = customerSelfServiceService.requestAccountOpening(command)
        return ResponseEntity.status(if (result.replayed) HttpStatus.OK else HttpStatus.CREATED).body(result)
    }

    @GetMapping("/account-opening-requests")
    fun accountOpeningRequests(): CustomerSelfServiceAccountOpeningRequestListResponse =
        customerSelfServiceService.accountOpeningRequests()

    @GetMapping("/360")
    fun customer360(): Customer360Dto =
        customerSelfServiceService.customer360()

    @GetMapping("/statements/artifacts")
    fun statementArtifacts(): CustomerStatementArtifactListResponse =
        customerSelfServiceService.statementArtifacts()
}
