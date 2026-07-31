
package lab.banking.payment.core

import java.time.LocalDate
import java.time.OffsetDateTime
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

data class CorePaymentLedgerEvidence(
    val ledgerTransactionId: String,
    val paymentInstructionId: String,
    val businessDate: LocalDate,
    val transactionType: String,
    val status: String,
    val currency: String,
    val amountMinor: Long,
    val totalDebitMinor: Long,
    val totalCreditMinor: Long,
    val postingCount: Int,
    val balanced: Boolean,
    val postedAt: OffsetDateTime?,
    val syntheticOnly: Boolean
)

interface CoreLedgerEvidenceClient {
    fun paymentPostings(businessDate: LocalDate, reason: String): List<CorePaymentLedgerEvidence>
}

@Component
@ConditionalOnProperty(
    prefix = "banking-lab.payment-service.core-banking",
    name = ["http-enabled"],
    havingValue = "false"
)
class DisabledCoreLedgerEvidenceClient : CoreLedgerEvidenceClient {
    override fun paymentPostings(businessDate: LocalDate, reason: String): List<CorePaymentLedgerEvidence> {
        throw IllegalStateException("core-banking ledger evidence HTTP client is disabled")
    }
}
