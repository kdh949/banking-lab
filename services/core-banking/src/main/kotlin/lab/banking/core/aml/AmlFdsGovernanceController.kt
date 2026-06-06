package lab.banking.core.aml

import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/aml/governance")
class AmlFdsGovernanceController(
    private val governanceService: AmlFdsGovernanceService
) {
    @PostMapping("/screen/customers/{customerId}")
    fun screenCustomer(
        @PathVariable customerId: String,
        @RequestBody command: SanctionsScreenCustomerCommand
    ): SanctionsScreeningResultDto =
        governanceService.screenCustomer(customerId, command)

    @PostMapping("/screen/transfers")
    fun screenTransfer(@RequestBody command: SanctionsScreenTransferCommand): SanctionsScreeningResultDto =
        governanceService.screenTransfer(command)

    @PostMapping("/hits/{hitId}/false-positive-dispositions")
    @PreAuthorize("@bankingLabMethodSecurityPolicy.securityDisabled() or hasAnyRole('AML_REVIEWER','COMPLIANCE_MANAGER')")
    fun falsePositiveDisposition(
        @PathVariable hitId: String,
        @RequestBody command: FalsePositiveDispositionCommand
    ): SanctionsScreeningHitDto =
        governanceService.falsePositiveDisposition(hitId, command)

    @GetMapping("/model-card")
    fun modelCard(): AmlModelCardDto =
        governanceService.activeModelCard()
}
