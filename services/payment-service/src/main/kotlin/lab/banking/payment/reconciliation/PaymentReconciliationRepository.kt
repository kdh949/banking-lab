
package lab.banking.payment.reconciliation

import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class PaymentReconciliationRepository(
    private val jdbc: NamedParameterJdbcTemplate
) {
    fun lockKeys(keys: Collection<String>) {
        keys.distinct().sorted().forEach { key ->
            jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))",
                mapOf("lockKey" to key)
            ) { _, _ -> Unit }
        }
    }

    fun findRunByIdempotencyKey(idempotencyKey: String): PaymentReconciliationRunRecord? =
        findRunBy(
            "WHERE idempotency_key = :idempotencyKey",
            mapOf("idempotencyKey" to idempotencyKey)
        )

    fun findRunByImportId(importId: String): PaymentReconciliationRunRecord? =
        findRunBy(
            "WHERE external_settlement_import_id = :importId",
            mapOf("importId" to importId)
        )

    fun findRun(runId: String): PaymentReconciliationRunRecord? =
        findRunBy(
            "WHERE payment_reconciliation_run_id = :runId",
            mapOf("runId" to runId)
        )

    fun findRunForUpdate(runId: String): PaymentReconciliationRunRecord? =
        findRunBy(
            "WHERE payment_reconciliation_run_id = :runId FOR UPDATE",
            mapOf("runId" to runId)
        )

    fun insertRun(record: PaymentReconciliationRunRecord) {
        jdbc.update(
            """
            INSERT INTO payment_reconciliation_runs (
              payment_reconciliation_run_id,
              external_settlement_import_id,
              business_date,
              expected_value_date,
              allowed_value_date_lag_days,
              sla_days,
              owner_id,
              status,
              idempotency_key,
              request_hash,
              requested_by,
              reason,
              external_line_count,
              ledger_evidence_count,
              matched_count,
              exception_count,
              claim_token,
              claim_expires_at,
              attempt_count,
              failure_message,
              synthetic_only,
              created_at,
              updated_at,
              completed_at
            ) VALUES (
              :runId,
              :importId,
              :businessDate,
              :expectedValueDate,
              :allowedValueDateLagDays,
              :slaDays,
              :ownerId,
              :status,
              :idempotencyKey,
              :requestHash,
              :requestedBy,
              :reason,
              0,
              0,
              0,
              0,
              :claimToken,
              :claimExpiresAt,
              1,
              NULL,
              true,
              :createdAt,
              :updatedAt,
              NULL
            )
            """.trimIndent(),
            mapOf(
                "runId" to record.paymentReconciliationRunId,
                "importId" to record.externalSettlementImportId,
                "businessDate" to record.businessDate,
                "expectedValueDate" to record.expectedValueDate,
                "allowedValueDateLagDays" to record.allowedValueDateLagDays,
                "slaDays" to record.slaDays,
                "ownerId" to record.ownerId,
                "status" to record.status.name,
                "idempotencyKey" to record.idempotencyKey,
                "requestHash" to record.requestHash,
                "requestedBy" to record.requestedBy,
                "reason" to record.reason,
                "claimToken" to record.claimToken,
                "claimExpiresAt" to record.claimExpiresAt,
                "createdAt" to record.createdAt,
                "updatedAt" to record.updatedAt
            )
        )
    }

    fun reclaimRun(runId: String, claimToken: String, claimExpiresAt: OffsetDateTime, updatedAt: OffsetDateTime) {
        jdbc.update(
            """
            UPDATE payment_reconciliation_runs
            SET status = 'EVIDENCE_PENDING',
                claim_token = :claimToken,
                claim_expires_at = :claimExpiresAt,
                attempt_count = attempt_count + 1,
                failure_message = NULL,
                completed_at = NULL,
                updated_at = :updatedAt
            WHERE payment_reconciliation_run_id = :runId
            """.trimIndent(),
            mapOf(
                "runId" to runId,
                "claimToken" to claimToken,
                "claimExpiresAt" to claimExpiresAt,
                "updatedAt" to updatedAt
            )
        )
    }

    fun markFailed(runId: String, claimToken: String, message: String, updatedAt: OffsetDateTime) {
        jdbc.update(
            """
            UPDATE payment_reconciliation_runs
            SET status = 'FAILED',
                claim_token = NULL,
                claim_expires_at = NULL,
                failure_message = :message,
                updated_at = :updatedAt,
                completed_at = :updatedAt
            WHERE payment_reconciliation_run_id = :runId
              AND claim_token = :claimToken
            """.trimIndent(),
            mapOf(
                "runId" to runId,
                "claimToken" to claimToken,
                "message" to message.take(500),
                "updatedAt" to updatedAt
            )
        )
    }

    fun deleteResults(runId: String) {
        jdbc.update(
            "DELETE FROM payment_reconciliation_results WHERE payment_reconciliation_run_id = :runId",
            mapOf("runId" to runId)
        )
    }

    fun insertResult(record: PaymentReconciliationResultRecord) {
        jdbc.update(
            """
            INSERT INTO payment_reconciliation_results (
              payment_reconciliation_result_id,
              payment_reconciliation_run_id,
              external_settlement_line_id,
              payment_instruction_id,
              ledger_transaction_id,
              mismatch_type,
              result_status,
              internal_amount_minor,
              ledger_amount_minor,
              external_amount_minor,
              currency,
              external_status,
              business_date,
              expected_value_date,
              actual_value_date,
              owner_id,
              detected_reason,
              detected_at,
              due_at,
              resolution,
              approval_id,
              resolved_at,
              synthetic_only
            ) VALUES (
              :resultId,
              :runId,
              :externalLineId,
              :paymentInstructionId,
              :ledgerTransactionId,
              :mismatchType,
              :resultStatus,
              :internalAmountMinor,
              :ledgerAmountMinor,
              :externalAmountMinor,
              :currency,
              :externalStatus,
              :businessDate,
              :expectedValueDate,
              :actualValueDate,
              :ownerId,
              :detectedReason,
              :detectedAt,
              :dueAt,
              :resolution,
              :approvalId,
              :resolvedAt,
              true
            )
            """.trimIndent(),
            mapOf(
                "resultId" to record.paymentReconciliationResultId,
                "runId" to record.paymentReconciliationRunId,
                "externalLineId" to record.externalSettlementLineId,
                "paymentInstructionId" to record.paymentInstructionId,
                "ledgerTransactionId" to record.ledgerTransactionId,
                "mismatchType" to record.mismatchType.name,
                "resultStatus" to record.resultStatus.name,
                "internalAmountMinor" to record.internalAmountMinor,
                "ledgerAmountMinor" to record.ledgerAmountMinor,
                "externalAmountMinor" to record.externalAmountMinor,
                "currency" to record.currency,
                "externalStatus" to record.externalStatus,
                "businessDate" to record.businessDate,
                "expectedValueDate" to record.expectedValueDate,
                "actualValueDate" to record.actualValueDate,
                "ownerId" to record.ownerId,
                "detectedReason" to record.detectedReason,
                "detectedAt" to record.detectedAt,
                "dueAt" to record.dueAt,
                "resolution" to record.resolution,
                "approvalId" to record.approvalId,
                "resolvedAt" to record.resolvedAt
            )
        )
    }

    fun finalizeRun(
        runId: String,
        claimToken: String,
        status: PaymentReconciliationRunStatus,
        externalLineCount: Int,
        ledgerEvidenceCount: Int,
        matchedCount: Int,
        exceptionCount: Int,
        completedAt: OffsetDateTime
    ) {
        val rows = jdbc.update(
            """
            UPDATE payment_reconciliation_runs
            SET status = :status,
                external_line_count = :externalLineCount,
                ledger_evidence_count = :ledgerEvidenceCount,
                matched_count = :matchedCount,
                exception_count = :exceptionCount,
                claim_token = NULL,
                claim_expires_at = NULL,
                failure_message = NULL,
                updated_at = :completedAt,
                completed_at = :completedAt
            WHERE payment_reconciliation_run_id = :runId
              AND claim_token = :claimToken
            """.trimIndent(),
            mapOf(
                "runId" to runId,
                "claimToken" to claimToken,
                "status" to status.name,
                "externalLineCount" to externalLineCount,
                "ledgerEvidenceCount" to ledgerEvidenceCount,
                "matchedCount" to matchedCount,
                "exceptionCount" to exceptionCount,
                "completedAt" to completedAt
            )
        )
        check(rows == 1) { "payment reconciliation run claim was lost before finalization" }
    }

    fun resultsForRun(runId: String): List<PaymentReconciliationResultRecord> =
        jdbc.query(
            resultSelect(
                "WHERE payment_reconciliation_run_id = :runId ORDER BY mismatch_type, payment_instruction_id, external_settlement_line_id NULLS LAST"
            ),
            mapOf("runId" to runId),
            this::mapResult
        )

    fun exceptions(
        ownerId: String?,
        status: PaymentReconciliationResultStatus?,
        overdueOnly: Boolean
    ): List<PaymentReconciliationResultRecord> =
        jdbc.query(
            resultSelect(
                """
                WHERE mismatch_type <> 'MATCHED'
                  AND (CAST(:ownerId AS text) IS NULL OR owner_id = :ownerId)
                  AND (CAST(:status AS text) IS NULL OR result_status = :status)
                  AND (:overdueOnly = false OR (due_at < now() AND result_status <> 'RESOLVED'))
                ORDER BY due_at NULLS LAST, detected_at, payment_reconciliation_result_id
                LIMIT 500
                """.trimIndent()
            ),
            mapOf(
                "ownerId" to ownerId,
                "status" to status?.name,
                "overdueOnly" to overdueOnly
            ),
            this::mapResult
        )

    fun insertAccessAudit(
        eventType: String,
        runId: String?,
        actorId: String,
        actorRoles: Set<String>,
        reason: String,
        resultCount: Int,
        createdAt: OffsetDateTime
    ): String {
        val auditId = "PRA-${java.util.UUID.randomUUID().toString().uppercase()}"
        jdbc.update(
            """
            INSERT INTO payment_reconciliation_access_audit_events (
              reconciliation_access_audit_id,
              event_type,
              payment_reconciliation_run_id,
              actor_id,
              actor_roles,
              reason,
              result_count,
              synthetic_only,
              created_at
            ) VALUES (
              :auditId,
              :eventType,
              :runId,
              :actorId,
              :actorRoles,
              :reason,
              :resultCount,
              true,
              :createdAt
            )
            """.trimIndent(),
            mapOf(
                "auditId" to auditId,
                "eventType" to eventType,
                "runId" to runId,
                "actorId" to actorId,
                "actorRoles" to actorRoles.sorted().joinToString(","),
                "reason" to reason,
                "resultCount" to resultCount,
                "createdAt" to createdAt
            )
        )
        return auditId
    }

    private fun findRunBy(clause: String, params: Map<String, Any?>): PaymentReconciliationRunRecord? =
        jdbc.query(
            """
            SELECT
              payment_reconciliation_run_id,
              external_settlement_import_id,
              business_date,
              expected_value_date,
              allowed_value_date_lag_days,
              sla_days,
              owner_id,
              status,
              idempotency_key,
              request_hash,
              requested_by,
              reason,
              external_line_count,
              ledger_evidence_count,
              matched_count,
              exception_count,
              claim_token,
              claim_expires_at,
              attempt_count,
              failure_message,
              synthetic_only,
              created_at,
              updated_at,
              completed_at
            FROM payment_reconciliation_runs
            $clause
            """.trimIndent(),
            params,
            this::mapRun
        ).firstOrNull()

    private fun resultSelect(clause: String): String =
        """
        SELECT
          payment_reconciliation_result_id,
          payment_reconciliation_run_id,
          external_settlement_line_id,
          payment_instruction_id,
          ledger_transaction_id,
          mismatch_type,
          result_status,
          internal_amount_minor,
          ledger_amount_minor,
          external_amount_minor,
          currency,
          external_status,
          business_date,
          expected_value_date,
          actual_value_date,
          owner_id,
          detected_reason,
          detected_at,
          due_at,
          resolution,
          approval_id,
          resolved_at,
          synthetic_only
        FROM payment_reconciliation_results
        $clause
        """.trimIndent()

    private fun mapRun(rs: ResultSet, rowNum: Int): PaymentReconciliationRunRecord =
        PaymentReconciliationRunRecord(
            paymentReconciliationRunId = rs.getString("payment_reconciliation_run_id"),
            externalSettlementImportId = rs.getString("external_settlement_import_id"),
            businessDate = rs.getObject("business_date", LocalDate::class.java),
            expectedValueDate = rs.getObject("expected_value_date", LocalDate::class.java),
            allowedValueDateLagDays = rs.getInt("allowed_value_date_lag_days"),
            slaDays = rs.getInt("sla_days"),
            ownerId = rs.getString("owner_id"),
            status = PaymentReconciliationRunStatus.valueOf(rs.getString("status")),
            idempotencyKey = rs.getString("idempotency_key"),
            requestHash = rs.getString("request_hash"),
            requestedBy = rs.getString("requested_by"),
            reason = rs.getString("reason"),
            externalLineCount = rs.getInt("external_line_count"),
            ledgerEvidenceCount = rs.getInt("ledger_evidence_count"),
            matchedCount = rs.getInt("matched_count"),
            exceptionCount = rs.getInt("exception_count"),
            claimToken = rs.getString("claim_token"),
            claimExpiresAt = rs.getObject("claim_expires_at", OffsetDateTime::class.java),
            attemptCount = rs.getInt("attempt_count"),
            failureMessage = rs.getString("failure_message"),
            syntheticOnly = rs.getBoolean("synthetic_only"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
            completedAt = rs.getObject("completed_at", OffsetDateTime::class.java)
        )

    private fun mapResult(rs: ResultSet, rowNum: Int): PaymentReconciliationResultRecord =
        PaymentReconciliationResultRecord(
            paymentReconciliationResultId = rs.getString("payment_reconciliation_result_id"),
            paymentReconciliationRunId = rs.getString("payment_reconciliation_run_id"),
            externalSettlementLineId = rs.getString("external_settlement_line_id"),
            paymentInstructionId = rs.getString("payment_instruction_id"),
            ledgerTransactionId = rs.getString("ledger_transaction_id"),
            mismatchType = PaymentReconciliationMismatchType.valueOf(rs.getString("mismatch_type")),
            resultStatus = PaymentReconciliationResultStatus.valueOf(rs.getString("result_status")),
            internalAmountMinor = rs.getObject("internal_amount_minor", java.lang.Long::class.java)?.toLong(),
            ledgerAmountMinor = rs.getObject("ledger_amount_minor", java.lang.Long::class.java)?.toLong(),
            externalAmountMinor = rs.getObject("external_amount_minor", java.lang.Long::class.java)?.toLong(),
            currency = rs.getString("currency"),
            externalStatus = rs.getString("external_status"),
            businessDate = rs.getObject("business_date", LocalDate::class.java),
            expectedValueDate = rs.getObject("expected_value_date", LocalDate::class.java),
            actualValueDate = rs.getObject("actual_value_date", LocalDate::class.java),
            ownerId = rs.getString("owner_id"),
            detectedReason = rs.getString("detected_reason"),
            detectedAt = rs.getObject("detected_at", OffsetDateTime::class.java),
            dueAt = rs.getObject("due_at", OffsetDateTime::class.java),
            resolution = rs.getString("resolution"),
            approvalId = rs.getString("approval_id"),
            resolvedAt = rs.getObject("resolved_at", OffsetDateTime::class.java),
            syntheticOnly = rs.getBoolean("synthetic_only")
        )
}
