
package lab.banking.core.ledger.evidence

import java.time.LocalDate
import java.time.OffsetDateTime

data class PaymentLedgerEvidenceDto(
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

data class PaymentLedgerEvidenceResponse(
    val items: List<PaymentLedgerEvidenceDto>,
    val auditEventId: String,
    val syntheticOnly: Boolean
)
