
package lab.banking.core.ledger.evidence

import java.time.LocalDate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/ledger/payment-postings")
class PaymentLedgerEvidenceController(
    private val service: PaymentLedgerEvidenceService
) {
    @GetMapping("/evidence")
    fun evidence(
        @RequestParam businessDate: LocalDate,
        @RequestParam reason: String
    ): PaymentLedgerEvidenceResponse =
        service.forBusinessDate(businessDate, reason)
}
