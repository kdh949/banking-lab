package lab.banking.payment.persistence

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import lab.banking.payment.domain.PaymentBillerRecord
import lab.banking.payment.domain.PaymentIdempotencyRecord
import lab.banking.payment.domain.PaymentInstructionRecord
import lab.banking.payment.domain.PaymentInstructionResponse
import lab.banking.payment.domain.PaymentInstructionStatus
import lab.banking.payment.domain.PaymentOutboxRecord
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class PaymentRepository(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    fun findBiller(billerId: String): PaymentBillerRecord? =
        jdbc.query(
            """
            SELECT biller_id, display_name, synthetic_only, network_kind
            FROM payment_billers
            WHERE biller_id = :billerId
            """.trimIndent(),
            mapOf("billerId" to billerId)
        ) { rs, _ ->
            PaymentBillerRecord(
                billerId = rs.getString("biller_id"),
                displayName = rs.getString("display_name"),
                syntheticOnly = rs.getBoolean("synthetic_only"),
                networkKind = rs.getString("network_kind")
            )
        }.firstOrNull()

    fun insertInstruction(
        instructionId: String,
        customerId: String,
        debitAccountId: String,
        biller: PaymentBillerRecord,
        amountMinor: Long,
        currency: String
    ) {
        jdbc.update(
            """
            INSERT INTO payment_instructions (
              payment_instruction_id, customer_id, debit_account_id, biller_id, biller_name,
              amount_minor, currency, status, synthetic_only
            )
            VALUES (
              :instructionId, :customerId, :debitAccountId, :billerId, :billerName,
              :amountMinor, :currency, 'POSTING_REQUESTED', true
            )
            """.trimIndent(),
            mapOf(
                "instructionId" to instructionId,
                "customerId" to customerId,
                "debitAccountId" to debitAccountId,
                "billerId" to biller.billerId,
                "billerName" to biller.displayName,
                "amountMinor" to amountMinor,
                "currency" to currency
            )
        )
    }

    fun insertAttempt(
        instructionId: String,
        attemptId: String,
        status: String,
        requestedBy: String,
        reason: String
    ) {
        jdbc.update(
            """
            INSERT INTO payment_attempts (
              payment_attempt_id, payment_instruction_id, attempt_no, status,
              requested_by, reason, synthetic_only
            )
            VALUES (
              :attemptId,
              :instructionId,
              COALESCE((SELECT max(attempt_no) + 1 FROM payment_attempts WHERE payment_instruction_id = :instructionId), 1),
              :status,
              :requestedBy,
              :reason,
              true
            )
            """.trimIndent(),
            mapOf(
                "attemptId" to attemptId,
                "instructionId" to instructionId,
                "status" to status,
                "requestedBy" to requestedBy,
                "reason" to reason
            )
        )
    }

    fun insertStatusHistory(
        instructionId: String,
        status: PaymentInstructionStatus,
        actorId: String,
        reason: String
    ) {
        jdbc.update(
            """
            INSERT INTO payment_status_history (
              payment_status_history_id, payment_instruction_id, status, actor_id, reason
            )
            VALUES (
              :historyId, :instructionId, :status, :actorId, :reason
            )
            """.trimIndent(),
            mapOf(
                "historyId" to "PHS-${UUID.randomUUID().toString().uppercase()}",
                "instructionId" to instructionId,
                "status" to status.name,
                "actorId" to actorId,
                "reason" to reason
            )
        )
    }

    fun insertOutboxEvent(
        aggregateId: String,
        eventType: String,
        idempotencyKey: String,
        payload: Map<String, Any?>
    ): String {
        val outboxEventId = "POB-${UUID.randomUUID().toString().uppercase()}"
        jdbc.update(
            """
            INSERT INTO payment_outbox_events (
              outbox_event_id, aggregate_type, aggregate_id, event_type, idempotency_key,
              payload_json, headers_json, status
            )
            VALUES (
              :outboxEventId, 'payment_instruction', :aggregateId, :eventType, :idempotencyKey,
              CAST(:payloadJson AS jsonb), '{}'::jsonb, 'PENDING'
            )
            """.trimIndent(),
            mapOf(
                "outboxEventId" to outboxEventId,
                "aggregateId" to aggregateId,
                "eventType" to eventType,
                "idempotencyKey" to idempotencyKey,
                "payloadJson" to objectMapper.writeValueAsString(payload)
            )
        )
        return outboxEventId
    }

    fun insertIdempotency(
        idempotencyKey: String,
        commandType: String,
        requestHash: String,
        aggregateId: String,
        response: PaymentInstructionResponse
    ) {
        jdbc.update(
            """
            INSERT INTO payment_idempotency_keys (
              idempotency_key, command_type, request_hash, aggregate_id, response_json
            )
            VALUES (
              :idempotencyKey, :commandType, :requestHash, :aggregateId, CAST(:responseJson AS jsonb)
            )
            """.trimIndent(),
            mapOf(
                "idempotencyKey" to idempotencyKey,
                "commandType" to commandType,
                "requestHash" to requestHash,
                "aggregateId" to aggregateId,
                "responseJson" to objectMapper.writeValueAsString(response)
            )
        )
    }

    fun findIdempotency(idempotencyKey: String): PaymentIdempotencyRecord? =
        jdbc.query(
            """
            SELECT idempotency_key, command_type, request_hash, aggregate_id, response_json::text AS response_json
            FROM payment_idempotency_keys
            WHERE idempotency_key = :idempotencyKey
            """.trimIndent(),
            mapOf("idempotencyKey" to idempotencyKey)
        ) { rs, _ ->
            PaymentIdempotencyRecord(
                idempotencyKey = rs.getString("idempotency_key"),
                commandType = rs.getString("command_type"),
                requestHash = rs.getString("request_hash"),
                aggregateId = rs.getString("aggregate_id"),
                responseJson = rs.getString("response_json")
            )
        }.firstOrNull()

    fun findInstruction(instructionId: String): PaymentInstructionRecord? =
        findInstructionBySql(
            """
            SELECT payment_instruction_id, customer_id, debit_account_id, biller_id, biller_name,
                   amount_minor, currency, status, ledger_transaction_id, synthetic_only, created_at, updated_at
            FROM payment_instructions
            WHERE payment_instruction_id = :instructionId
            """.trimIndent(),
            instructionId
        )

    fun findInstructionForUpdate(instructionId: String): PaymentInstructionRecord? =
        findInstructionBySql(
            """
            SELECT payment_instruction_id, customer_id, debit_account_id, biller_id, biller_name,
                   amount_minor, currency, status, ledger_transaction_id, synthetic_only, created_at, updated_at
            FROM payment_instructions
            WHERE payment_instruction_id = :instructionId
            FOR UPDATE
            """.trimIndent(),
            instructionId
        )

    fun updateSettlement(instructionId: String, ledgerTransactionId: String) {
        jdbc.update(
            """
            UPDATE payment_instructions
            SET status = 'SETTLED',
                ledger_transaction_id = :ledgerTransactionId,
                updated_at = now()
            WHERE payment_instruction_id = :instructionId
            """.trimIndent(),
            mapOf("instructionId" to instructionId, "ledgerTransactionId" to ledgerTransactionId)
        )
    }

    fun markLatestAttemptSettled(instructionId: String) {
        jdbc.update(
            """
            UPDATE payment_attempts
            SET status = 'SETTLED',
                completed_at = now()
            WHERE payment_attempt_id = (
              SELECT payment_attempt_id
              FROM payment_attempts
              WHERE payment_instruction_id = :instructionId
              ORDER BY attempt_no DESC
              LIMIT 1
            )
            """.trimIndent(),
            mapOf("instructionId" to instructionId)
        )
    }

    fun markLatestAttemptCanceled(instructionId: String) {
        jdbc.update(
            """
            UPDATE payment_attempts
            SET status = 'CANCELED',
                completed_at = now()
            WHERE payment_attempt_id = (
              SELECT payment_attempt_id
              FROM payment_attempts
              WHERE payment_instruction_id = :instructionId
              ORDER BY attempt_no DESC
              LIMIT 1
            )
            """.trimIndent(),
            mapOf("instructionId" to instructionId)
        )
    }

    fun updateStatus(instructionId: String, status: PaymentInstructionStatus) {
        jdbc.update(
            """
            UPDATE payment_instructions
            SET status = :status,
                updated_at = now()
            WHERE payment_instruction_id = :instructionId
            """.trimIndent(),
            mapOf("instructionId" to instructionId, "status" to status.name)
        )
    }

    fun latestOutboxEventId(instructionId: String): String? =
        jdbc.query(
            """
            SELECT outbox_event_id
            FROM payment_outbox_events
            WHERE aggregate_id = :instructionId
            ORDER BY created_at DESC, outbox_event_id DESC
            LIMIT 1
            """.trimIndent(),
            mapOf("instructionId" to instructionId)
        ) { rs, _ -> rs.getString("outbox_event_id") }.firstOrNull()

    fun findNextLedgerPostingOutboxForUpdate(): PaymentOutboxRecord? =
        jdbc.query(
            """
            SELECT outbox_event_id, aggregate_type, aggregate_id, event_type, idempotency_key,
                   payload_json::text AS payload_json, status, retry_count, error_message
            FROM payment_outbox_events
            WHERE event_type = 'PaymentLedgerPostingRequested'
              AND status IN ('PENDING', 'FAILED')
              AND (next_retry_at IS NULL OR next_retry_at <= now())
            ORDER BY created_at ASC, outbox_event_id ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """.trimIndent(),
            emptyMap<String, Any?>(),
            this::mapOutbox
        ).firstOrNull()

    fun markOutboxPublished(outboxEventId: String) {
        jdbc.update(
            """
            UPDATE payment_outbox_events
            SET status = 'PUBLISHED',
                published_at = now(),
                next_retry_at = NULL,
                error_message = NULL
            WHERE outbox_event_id = :outboxEventId
            """.trimIndent(),
            mapOf("outboxEventId" to outboxEventId)
        )
    }

    fun markOutboxFailed(
        outboxEventId: String,
        retryCount: Int,
        errorMessage: String,
        deadLetter: Boolean
    ) {
        jdbc.update(
            """
            UPDATE payment_outbox_events
            SET status = :status,
                retry_count = :retryCount,
                next_retry_at = CASE WHEN :deadLetter THEN NULL ELSE now() + interval '5 minutes' END,
                error_message = :errorMessage
            WHERE outbox_event_id = :outboxEventId
            """.trimIndent(),
            mapOf(
                "outboxEventId" to outboxEventId,
                "retryCount" to retryCount,
                "errorMessage" to errorMessage.take(500),
                "deadLetter" to deadLetter,
                "status" to if (deadLetter) "DEAD_LETTER" else "FAILED"
            )
        )
    }

    private fun findInstructionBySql(sql: String, instructionId: String): PaymentInstructionRecord? =
        jdbc.query(sql, mapOf("instructionId" to instructionId), this::mapInstruction).firstOrNull()

    private fun mapInstruction(rs: ResultSet, rowNum: Int): PaymentInstructionRecord =
        PaymentInstructionRecord(
            paymentInstructionId = rs.getString("payment_instruction_id"),
            customerId = rs.getString("customer_id"),
            debitAccountId = rs.getString("debit_account_id"),
            billerId = rs.getString("biller_id"),
            billerName = rs.getString("biller_name"),
            amountMinor = rs.getLong("amount_minor"),
            currency = rs.getString("currency"),
            status = PaymentInstructionStatus.valueOf(rs.getString("status")),
            ledgerTransactionId = rs.getString("ledger_transaction_id"),
            syntheticOnly = rs.getBoolean("synthetic_only"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java)
        )

    private fun mapOutbox(rs: ResultSet, rowNum: Int): PaymentOutboxRecord =
        PaymentOutboxRecord(
            outboxEventId = rs.getString("outbox_event_id"),
            aggregateType = rs.getString("aggregate_type"),
            aggregateId = rs.getString("aggregate_id"),
            eventType = rs.getString("event_type"),
            idempotencyKey = rs.getString("idempotency_key"),
            payload = objectMapper.readValue(
                rs.getString("payload_json"),
                object : TypeReference<Map<String, Any?>>() {}
            ),
            status = rs.getString("status"),
            retryCount = rs.getInt("retry_count"),
            errorMessage = rs.getString("error_message")
        )
}
