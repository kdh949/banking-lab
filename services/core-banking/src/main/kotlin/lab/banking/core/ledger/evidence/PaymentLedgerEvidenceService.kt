
package lab.banking.core.ledger.evidence

import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import lab.banking.core.audit.AuditEventAppender
import lab.banking.core.security.BankingLabAuthContext
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentLedgerEvidenceService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val auditEvents: AuditEventAppender
) {
    @Transactional
    fun forBusinessDate(businessDate: LocalDate, reason: String): PaymentLedgerEvidenceResponse {
        if (reason.isBlank()) {
            throw WorkflowErrors.reasonRequired("payment ledger evidence access requires a business reason")
        }
        val items = jdbc.query(
            """
            SELECT
              lt.ledger_transaction_id,
              lt.business_reference_id AS payment_instruction_id,
              lt.business_date,
              lt.transaction_type,
              lt.status,
              lt.posted_at,
              MIN(BTRIM(lp.currency)) AS currency,
              COUNT(DISTINCT BTRIM(lp.currency)) AS currency_count,
              COUNT(*) AS posting_count,
              COALESCE(SUM(CASE WHEN lp.direction = 'DEBIT' THEN lp.amount_minor ELSE 0 END), 0) AS total_debit_minor,
              COALESCE(SUM(CASE WHEN lp.direction = 'CREDIT' THEN lp.amount_minor ELSE 0 END), 0) AS total_credit_minor
            FROM ledger_transactions lt
            JOIN ledger_postings lp
              ON lp.ledger_transaction_id = lt.ledger_transaction_id
            WHERE lt.transaction_type = 'BILL_PAYMENT'
              AND lt.business_date = :businessDate
            GROUP BY
              lt.ledger_transaction_id,
              lt.business_reference_id,
              lt.business_date,
              lt.transaction_type,
              lt.status,
              lt.posted_at
            ORDER BY lt.ledger_transaction_id
            LIMIT 10000
            """.trimIndent(),
            mapOf("businessDate" to businessDate),
            this::mapEvidence
        )
        val principal = BankingLabAuthContext.get()
        val actorType = when {
            principal == null -> "SYSTEM"
            principal.roles.contains("PAYMENT_SERVICE") -> "SERVICE"
            else -> "STAFF"
        }
        val auditEventId = auditEvents.append(
            eventType = "PAYMENT_LEDGER_EVIDENCE_VIEW",
            actorType = actorType,
            actorId = principal?.subject ?: "SYSTEM",
            actorRole = principal?.roles?.sorted()?.joinToString(",") ?: "SYSTEM",
            screenId = "PAY-REC-LEDGER",
            businessReferenceId = businessDate.toString(),
            reason = reason,
            payload = mapOf(
                "businessDate" to businessDate.toString(),
                "evidenceCount" to items.size,
                "balancedEvidenceCount" to items.count { it.balanced },
                "syntheticOnly" to true
            )
        )
        return PaymentLedgerEvidenceResponse(
            items = items,
            auditEventId = auditEventId,
            syntheticOnly = true
        )
    }

    private fun mapEvidence(rs: ResultSet, rowNum: Int): PaymentLedgerEvidenceDto {
        val debit = rs.getLong("total_debit_minor")
        val credit = rs.getLong("total_credit_minor")
        val postingCount = rs.getInt("posting_count")
        val currencyCount = rs.getInt("currency_count")
        return PaymentLedgerEvidenceDto(
            ledgerTransactionId = rs.getString("ledger_transaction_id"),
            paymentInstructionId = rs.getString("payment_instruction_id"),
            businessDate = rs.getObject("business_date", LocalDate::class.java),
            transactionType = rs.getString("transaction_type"),
            status = rs.getString("status"),
            currency = rs.getString("currency"),
            amountMinor = debit,
            totalDebitMinor = debit,
            totalCreditMinor = credit,
            postingCount = postingCount,
            balanced = currencyCount == 1 && postingCount >= 2 && debit == credit,
            postedAt = rs.getObject("posted_at", OffsetDateTime::class.java),
            syntheticOnly = true
        )
    }
}
