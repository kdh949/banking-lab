package lab.banking.payment.persistence

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import lab.banking.payment.domain.PaymentAutopayAgreementRecord
import lab.banking.payment.domain.PaymentAutopayExecutionRecord
import lab.banking.payment.domain.PaymentAutopayFrequency
import lab.banking.payment.domain.PaymentAutopayStatus
import lab.banking.payment.domain.PaymentBillerRecord
import lab.banking.payment.domain.PaymentCancellationRequestRecord
import lab.banking.payment.domain.PaymentCancellationRequestStatus
import lab.banking.payment.domain.PaymentIdempotencyRecord
import lab.banking.payment.domain.PaymentInstructionRecord
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
        payload: Map<String, Any?>,
        aggregateType: String = "payment_instruction"
    ): String {
        val outboxEventId = "POB-${UUID.randomUUID().toString().uppercase()}"
        jdbc.update(
            """
            INSERT INTO payment_outbox_events (
              outbox_event_id, aggregate_type, aggregate_id, event_type, idempotency_key,
              payload_json, headers_json, status
            )
            VALUES (
              :outboxEventId, :aggregateType, :aggregateId, :eventType, :idempotencyKey,
              CAST(:payloadJson AS jsonb), '{}'::jsonb, 'PENDING'
            )
            """.trimIndent(),
            mapOf(
                "outboxEventId" to outboxEventId,
                "aggregateType" to aggregateType,
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
        response: Any
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

    fun insertPaymentInstructionViewAudit(
        instruction: PaymentInstructionRecord,
        actorId: String,
        actorRole: String,
        actorRoles: Set<String>,
        reason: String,
        screenId: String = "PAY-101"
    ): String {
        val auditEventId = "PAU-${UUID.randomUUID().toString().uppercase()}"
        jdbc.update(
            """
            INSERT INTO payment_access_audit_events (
              audit_event_id, event_type, actor_id, actor_role, actor_roles, screen_id,
              payment_instruction_id, customer_id, debit_account_id, reason, pii_access,
              masking_policy, synthetic_only
            )
            VALUES (
              :auditEventId, 'PAYMENT_INSTRUCTION_VIEW', :actorId, :actorRole, CAST(:actorRolesJson AS jsonb), :screenId,
              :paymentInstructionId, :customerId, :debitAccountId, :reason, true,
              'ACCOUNT_PII', true
            )
            """.trimIndent(),
            mapOf(
                "auditEventId" to auditEventId,
                "actorId" to actorId,
                "actorRole" to actorRole,
                "actorRolesJson" to objectMapper.writeValueAsString(actorRoles.sorted()),
                "screenId" to screenId,
                "paymentInstructionId" to instruction.paymentInstructionId,
                "customerId" to instruction.customerId,
                "debitAccountId" to instruction.debitAccountId,
                "reason" to reason
            )
        )
        return auditEventId
    }

    fun insertCancellationRequest(
        cancellationRequestId: String,
        instructionId: String,
        makerId: String,
        makerRole: String,
        makerReason: String
    ) {
        jdbc.update(
            """
            INSERT INTO payment_cancellation_requests (
              cancellation_request_id, payment_instruction_id, status,
              maker_id, maker_role, maker_reason, synthetic_only
            )
            VALUES (
              :cancellationRequestId, :instructionId, 'PENDING',
              :makerId, :makerRole, :makerReason, true
            )
            """.trimIndent(),
            mapOf(
                "cancellationRequestId" to cancellationRequestId,
                "instructionId" to instructionId,
                "makerId" to makerId,
                "makerRole" to makerRole,
                "makerReason" to makerReason
            )
        )
    }

    fun findCancellationRequest(requestId: String): PaymentCancellationRequestRecord? =
        findCancellationRequestBySql(
            """
            SELECT cancellation_request_id, payment_instruction_id, status,
                   maker_id, maker_role, maker_reason,
                   checker_id, checker_role, checker_reason,
                   synthetic_only, created_at, updated_at, decided_at
            FROM payment_cancellation_requests
            WHERE cancellation_request_id = :requestId
            """.trimIndent(),
            requestId
        )

    fun findCancellationRequestForUpdate(requestId: String): PaymentCancellationRequestRecord? =
        findCancellationRequestBySql(
            """
            SELECT cancellation_request_id, payment_instruction_id, status,
                   maker_id, maker_role, maker_reason,
                   checker_id, checker_role, checker_reason,
                   synthetic_only, created_at, updated_at, decided_at
            FROM payment_cancellation_requests
            WHERE cancellation_request_id = :requestId
            FOR UPDATE
            """.trimIndent(),
            requestId
        )

    fun findPendingCancellationRequest(instructionId: String): PaymentCancellationRequestRecord? =
        jdbc.query(
            """
            SELECT cancellation_request_id, payment_instruction_id, status,
                   maker_id, maker_role, maker_reason,
                   checker_id, checker_role, checker_reason,
                   synthetic_only, created_at, updated_at, decided_at
            FROM payment_cancellation_requests
            WHERE payment_instruction_id = :instructionId
              AND status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT 1
            """.trimIndent(),
            mapOf("instructionId" to instructionId),
            this::mapCancellationRequest
        ).firstOrNull()

    fun decideCancellationRequest(
        requestId: String,
        status: PaymentCancellationRequestStatus,
        checkerId: String,
        checkerRole: String,
        checkerReason: String
    ) {
        jdbc.update(
            """
            UPDATE payment_cancellation_requests
            SET status = :status,
                checker_id = :checkerId,
                checker_role = :checkerRole,
                checker_reason = :checkerReason,
                decided_at = now(),
                updated_at = now()
            WHERE cancellation_request_id = :requestId
            """.trimIndent(),
            mapOf(
                "requestId" to requestId,
                "status" to status.name,
                "checkerId" to checkerId,
                "checkerRole" to checkerRole,
                "checkerReason" to checkerReason
            )
        )
    }

    fun insertAutopayAgreement(
        agreementId: String,
        customerId: String,
        debitAccountId: String,
        biller: PaymentBillerRecord,
        amountMinor: Long,
        currency: String,
        frequency: PaymentAutopayFrequency,
        nextRunOn: LocalDate,
        createdBy: String,
        reason: String
    ) {
        jdbc.update(
            """
            INSERT INTO payment_autopay_agreements (
              autopay_agreement_id, customer_id, debit_account_id, biller_id, biller_name,
              amount_minor, currency, frequency, status, next_run_on, synthetic_only,
              created_by, created_reason
            )
            VALUES (
              :agreementId, :customerId, :debitAccountId, :billerId, :billerName,
              :amountMinor, :currency, :frequency, 'ACTIVE', :nextRunOn, true,
              :createdBy, :reason
            )
            """.trimIndent(),
            mapOf(
                "agreementId" to agreementId,
                "customerId" to customerId,
                "debitAccountId" to debitAccountId,
                "billerId" to biller.billerId,
                "billerName" to biller.displayName,
                "amountMinor" to amountMinor,
                "currency" to currency,
                "frequency" to frequency.name,
                "nextRunOn" to nextRunOn,
                "createdBy" to createdBy,
                "reason" to reason
            )
        )
    }

    fun insertAutopayStatusHistory(
        agreementId: String,
        status: PaymentAutopayStatus,
        actorId: String,
        reason: String
    ) {
        jdbc.update(
            """
            INSERT INTO payment_autopay_status_history (
              autopay_status_history_id, autopay_agreement_id, status, actor_id, reason
            )
            VALUES (:historyId, :agreementId, :status, :actorId, :reason)
            """.trimIndent(),
            mapOf(
                "historyId" to "PAH-${UUID.randomUUID().toString().uppercase()}",
                "agreementId" to agreementId,
                "status" to status.name,
                "actorId" to actorId,
                "reason" to reason
            )
        )
    }

    fun findAutopayAgreement(agreementId: String): PaymentAutopayAgreementRecord? =
        findAutopayAgreementBySql(
            """
            SELECT autopay_agreement_id, customer_id, debit_account_id, biller_id, biller_name,
                   amount_minor, currency, frequency, status, next_run_on, last_run_on,
                   synthetic_only, created_at, updated_at
            FROM payment_autopay_agreements
            WHERE autopay_agreement_id = :agreementId
            """.trimIndent(),
            mapOf("agreementId" to agreementId)
        )

    fun findAutopayAgreementForUpdate(agreementId: String): PaymentAutopayAgreementRecord? =
        findAutopayAgreementBySql(
            """
            SELECT autopay_agreement_id, customer_id, debit_account_id, biller_id, biller_name,
                   amount_minor, currency, frequency, status, next_run_on, last_run_on,
                   synthetic_only, created_at, updated_at
            FROM payment_autopay_agreements
            WHERE autopay_agreement_id = :agreementId
            FOR UPDATE
            """.trimIndent(),
            mapOf("agreementId" to agreementId)
        )

    fun updateAutopayStatus(
        agreementId: String,
        status: PaymentAutopayStatus,
        nextRunOn: LocalDate? = null
    ) {
        jdbc.update(
            """
            UPDATE payment_autopay_agreements
            SET status = :status,
                next_run_on = COALESCE(:nextRunOn, next_run_on),
                updated_at = now()
            WHERE autopay_agreement_id = :agreementId
            """.trimIndent(),
            mapOf("agreementId" to agreementId, "status" to status.name, "nextRunOn" to nextRunOn)
        )
    }

    fun findDueAutopayAgreementsForUpdate(businessDate: LocalDate, limit: Int): List<PaymentAutopayAgreementRecord> =
        jdbc.query(
            """
            SELECT autopay_agreement_id, customer_id, debit_account_id, biller_id, biller_name,
                   amount_minor, currency, frequency, status, next_run_on, last_run_on,
                   synthetic_only, created_at, updated_at
            FROM payment_autopay_agreements
            WHERE status = 'ACTIVE'
              AND next_run_on <= :businessDate
            ORDER BY next_run_on ASC, autopay_agreement_id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """.trimIndent(),
            mapOf("businessDate" to businessDate, "limit" to limit),
            this::mapAutopayAgreement
        )

    fun insertAutopayExecution(
        executionId: String,
        agreementId: String,
        scheduledRunOn: LocalDate,
        paymentInstructionId: String,
        idempotencyKey: String,
        requestedBy: String,
        reason: String
    ) {
        jdbc.update(
            """
            INSERT INTO payment_autopay_executions (
              autopay_execution_id, autopay_agreement_id, scheduled_run_on,
              payment_instruction_id, status, idempotency_key, requested_by, reason, synthetic_only
            )
            VALUES (
              :executionId, :agreementId, :scheduledRunOn,
              :paymentInstructionId, 'INSTRUCTION_CREATED', :idempotencyKey, :requestedBy, :reason, true
            )
            """.trimIndent(),
            mapOf(
                "executionId" to executionId,
                "agreementId" to agreementId,
                "scheduledRunOn" to scheduledRunOn,
                "paymentInstructionId" to paymentInstructionId,
                "idempotencyKey" to idempotencyKey,
                "requestedBy" to requestedBy,
                "reason" to reason
            )
        )
    }

    fun updateAutopayAfterExecution(
        agreementId: String,
        lastRunOn: LocalDate,
        nextRunOn: LocalDate
    ) {
        jdbc.update(
            """
            UPDATE payment_autopay_agreements
            SET last_run_on = :lastRunOn,
                next_run_on = :nextRunOn,
                updated_at = now()
            WHERE autopay_agreement_id = :agreementId
            """.trimIndent(),
            mapOf("agreementId" to agreementId, "lastRunOn" to lastRunOn, "nextRunOn" to nextRunOn)
        )
    }

    fun latestAutopayPaymentInstructionId(agreementId: String): String? =
        jdbc.query(
            """
            SELECT payment_instruction_id
            FROM payment_autopay_executions
            WHERE autopay_agreement_id = :agreementId
            ORDER BY scheduled_run_on DESC, created_at DESC, autopay_execution_id DESC
            LIMIT 1
            """.trimIndent(),
            mapOf("agreementId" to agreementId)
        ) { rs, _ -> rs.getString("payment_instruction_id") }.firstOrNull()

    fun findAutopayExecutionsByIds(executionIds: List<String>): List<PaymentAutopayExecutionRecord> {
        if (executionIds.isEmpty()) {
            return emptyList()
        }
        return jdbc.query(
            """
            SELECT autopay_execution_id, autopay_agreement_id, scheduled_run_on,
                   payment_instruction_id, status, synthetic_only, created_at
            FROM payment_autopay_executions
            WHERE autopay_execution_id IN (:executionIds)
            ORDER BY created_at ASC, autopay_execution_id ASC
            """.trimIndent(),
            mapOf("executionIds" to executionIds),
            this::mapAutopayExecution
        )
    }

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

    private fun findCancellationRequestBySql(sql: String, requestId: String): PaymentCancellationRequestRecord? =
        jdbc.query(sql, mapOf("requestId" to requestId), this::mapCancellationRequest).firstOrNull()

    private fun findAutopayAgreementBySql(sql: String, params: Map<String, Any?>): PaymentAutopayAgreementRecord? =
        jdbc.query(sql, params, this::mapAutopayAgreement).firstOrNull()

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

    private fun mapCancellationRequest(rs: ResultSet, rowNum: Int): PaymentCancellationRequestRecord =
        PaymentCancellationRequestRecord(
            cancellationRequestId = rs.getString("cancellation_request_id"),
            paymentInstructionId = rs.getString("payment_instruction_id"),
            status = PaymentCancellationRequestStatus.valueOf(rs.getString("status")),
            makerId = rs.getString("maker_id"),
            makerRole = rs.getString("maker_role"),
            makerReason = rs.getString("maker_reason"),
            checkerId = rs.getString("checker_id"),
            checkerRole = rs.getString("checker_role"),
            checkerReason = rs.getString("checker_reason"),
            syntheticOnly = rs.getBoolean("synthetic_only"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
            decidedAt = rs.getObject("decided_at", OffsetDateTime::class.java)
        )

    private fun mapAutopayAgreement(rs: ResultSet, rowNum: Int): PaymentAutopayAgreementRecord =
        PaymentAutopayAgreementRecord(
            autopayAgreementId = rs.getString("autopay_agreement_id"),
            customerId = rs.getString("customer_id"),
            debitAccountId = rs.getString("debit_account_id"),
            billerId = rs.getString("biller_id"),
            billerName = rs.getString("biller_name"),
            amountMinor = rs.getLong("amount_minor"),
            currency = rs.getString("currency"),
            frequency = PaymentAutopayFrequency.valueOf(rs.getString("frequency")),
            status = PaymentAutopayStatus.valueOf(rs.getString("status")),
            nextRunOn = rs.getObject("next_run_on", LocalDate::class.java),
            lastRunOn = rs.getObject("last_run_on", LocalDate::class.java),
            syntheticOnly = rs.getBoolean("synthetic_only"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java)
        )

    private fun mapAutopayExecution(rs: ResultSet, rowNum: Int): PaymentAutopayExecutionRecord =
        PaymentAutopayExecutionRecord(
            autopayExecutionId = rs.getString("autopay_execution_id"),
            autopayAgreementId = rs.getString("autopay_agreement_id"),
            scheduledRunOn = rs.getObject("scheduled_run_on", LocalDate::class.java),
            paymentInstructionId = rs.getString("payment_instruction_id"),
            status = rs.getString("status"),
            syntheticOnly = rs.getBoolean("synthetic_only"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
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
