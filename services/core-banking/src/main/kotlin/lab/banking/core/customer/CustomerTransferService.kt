package lab.banking.core.customer

import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import lab.banking.core.audit.AuditEventAppender
import lab.banking.core.ledger.application.InternalTransferCommand
import lab.banking.core.ledger.application.LedgerCommandService
import lab.banking.core.ledger.domain.LedgerCommandResult
import lab.banking.core.security.BankingLabAuthContext
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.dao.TransientDataAccessException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@Service
class CustomerTransferService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val ledgerCommandService: LedgerCommandService,
    private val transactionManager: PlatformTransactionManager,
    private val objectMapper: ObjectMapper,
    private val auditEvents: AuditEventAppender
) {
    fun transfer(command: CustomerTransferCommand): CustomerTransferResponse =
        runSerializableCustomerCommand {
            transferInTransaction(command)
        }

    private fun transferInTransaction(command: CustomerTransferCommand): CustomerTransferResponse {
        val customerId = command.customerId?.takeIf { it.isNotBlank() }
            ?: command.requestedBy?.takeIf { it.isNotBlank() }
            ?: throw WorkflowErrors.validation("customerId is required for customer transfer")
        BankingLabAuthContext.requireCustomerOwnership(customerId)
        ensureActiveSourceAccountOwned(customerId, command.fromAccountId)
        val commandHash = commandHash(command, customerId)

        findTransferResultByIdempotencyKey(command.idempotencyKey)?.let { existing ->
            ensureSameCommandHash(existing, commandHash)
            return CustomerTransferResponse(item = existing.toDto(), replayed = true)
        }

        if (command.amountMinor <= 0) {
            val failed = persistFailedTransfer(
                command = command,
                customerId = customerId,
                commandHash = commandHash,
                failureCode = "REQUEST_VALIDATION_FAILED",
                message = "amountMinor must be a positive integer minor-unit value"
            )
            return CustomerTransferResponse(item = failed.toDto(), replayed = false)
        }

        if (shouldHoldForFds(command)) {
            val held = persistHeldTransfer(command, customerId, commandHash)
            return CustomerTransferResponse(item = held.toDto(), replayed = false)
        }

        val result = ledgerCommandService.internalTransfer(
            InternalTransferCommand(
                fromAccountId = command.fromAccountId,
                toAccountId = command.toAccountId,
                amountMinor = command.amountMinor,
                idempotencyKey = command.idempotencyKey,
                requestedBy = command.requestedBy ?: customerId,
                requestedChannel = "CUSTOMER_WEB",
                businessDate = command.businessDate,
                reason = command.reason,
                currency = command.currency,
                businessReferenceId = command.businessReferenceId
            )
        )
        val posted = persistPostedTransfer(command, customerId, commandHash, result)
        return CustomerTransferResponse(item = posted.toDto(), replayed = result.replayed)
    }

    @Transactional(readOnly = true)
    fun transactionHistory(customerId: String, accountId: String): CustomerTransactionHistoryResponse {
        if (customerId.isBlank()) {
            throw WorkflowErrors.validation("customerId is required")
        }
        if (accountId.isBlank()) {
            throw WorkflowErrors.validation("accountId is required")
        }
        BankingLabAuthContext.requireCustomerOwnership(customerId)
        ensureAccountOwned(customerId, accountId, activeOnly = false)

        val transactions = jdbc.query(
            """
            SELECT lt.ledger_transaction_id, lt.transaction_type, lt.status, lt.business_date,
                   lt.posted_at, lp.account_id, lp.direction, lp.amount_minor, lp.currency,
                   lt.requested_channel, lt.reason
            FROM ledger_transactions lt
            JOIN ledger_postings lp
              ON lp.ledger_transaction_id = lt.ledger_transaction_id
            WHERE lp.account_id = :accountId
            ORDER BY lt.posted_at NULLS LAST, lt.ledger_transaction_id, lp.ledger_posting_id
            """.trimIndent(),
            mapOf("accountId" to accountId),
            this::mapCustomerTransaction
        )
        return CustomerTransactionHistoryResponse(items = transactions)
    }

    @Transactional(readOnly = true)
    fun transferStatuses(customerId: String): CustomerTransferStatusResponse {
        if (customerId.isBlank()) {
            throw WorkflowErrors.validation("customerId is required")
        }
        BankingLabAuthContext.requireCustomerOwnership(customerId)

        val statuses = jdbc.query(
            """
            SELECT *
            FROM (
              SELECT ctr.result_id, ctr.idempotency_key, ctr.customer_id,
                     ctr.from_account_id, ctr.to_account_id, ctr.amount_minor,
                     ctr.currency, ctr.status AS transfer_result_status,
                     ctr.ledger_transaction_id, ctr.fds_case_id,
                     f.transfer_reference_id, f.status AS case_status,
                     COALESCE(f.transfer_status, ctr.status) AS transfer_status,
                     f.risk_score, ctr.failure_code, ctr.message,
                     ctr.business_date, ctr.created_at
              FROM customer_transfer_results ctr
              LEFT JOIN fds_cases f
                ON f.fds_case_id = ctr.fds_case_id
              WHERE ctr.customer_id = :customerId
              UNION ALL
              SELECT NULL::text AS result_id, f.transfer_idempotency_key AS idempotency_key,
                     f.customer_id, f.from_account_id, f.to_account_id, f.amount_minor,
                     'KRW'::char(3) AS currency, f.transfer_status AS transfer_result_status,
                     NULL::text AS ledger_transaction_id, f.fds_case_id,
                     f.transfer_reference_id, f.status AS case_status,
                     f.transfer_status, f.risk_score, NULL::text AS failure_code,
                     'Transfer held for FDS review' AS message,
                     f.business_date, f.created_at
              FROM fds_cases f
              WHERE f.customer_id = :customerId
                AND f.transfer_status IS NOT NULL
                AND NOT EXISTS (
                  SELECT 1
                  FROM customer_transfer_results ctr
                  WHERE ctr.fds_case_id = f.fds_case_id
                )
            ) transfer_rows
            ORDER BY created_at, COALESCE(result_id, fds_case_id)
            """.trimIndent(),
            mapOf("customerId" to customerId),
            this::mapTransferStatus
        )
        return CustomerTransferStatusResponse(items = statuses)
    }

    private fun ensureActiveSourceAccountOwned(customerId: String, accountId: String) {
        ensureAccountOwned(customerId, accountId, activeOnly = true)
    }

    private fun shouldHoldForFds(command: CustomerTransferCommand): Boolean =
        command.amountMinor >= FDS_HIGH_AMOUNT_THRESHOLD_MINOR

    private fun <T> runSerializableCustomerCommand(operation: () -> T): T {
        var attempt = 1
        while (true) {
            try {
                val template = TransactionTemplate(transactionManager).apply {
                    isolationLevel = TransactionDefinition.ISOLATION_SERIALIZABLE
                }
                return template.execute { operation() }
                    ?: error("serializable customer command returned no result")
            } catch (error: RuntimeException) {
                if (attempt >= SERIALIZABLE_CUSTOMER_COMMAND_MAX_ATTEMPTS || !isRetryableSerializationFailure(error)) {
                    throw error
                }
                Thread.sleep(75L * attempt * attempt)
                attempt += 1
            }
        }
    }

    private fun isRetryableSerializationFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is TransientDataAccessException || current is PessimisticLockingFailureException) {
                return true
            }
            val message = current.message.orEmpty()
            if (
                message.contains("could not serialize access", ignoreCase = true) ||
                message.contains("SQLSTATE 40001", ignoreCase = true) ||
                message.contains("deadlock detected", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun ensureAccountOwned(customerId: String, accountId: String, activeOnly: Boolean) {
        val statusPredicate = if (activeOnly) "AND status = 'ACTIVE'" else ""
        val owned = jdbc.queryForObject(
            """
            SELECT count(*)
            FROM accounts
            WHERE customer_id = :customerId
              AND account_id = :accountId
              $statusPredicate
            """.trimIndent(),
            mapOf("customerId" to customerId, "accountId" to accountId),
            Int::class.java
        ) ?: 0
        if (owned == 0) {
            throw WorkflowErrors.authorizationViolation("customer transfer source account is not owned by the authenticated customer")
        }
    }

    private fun findTransferResultByIdempotencyKey(idempotencyKey: String): CustomerTransferResultRecord? =
        jdbc.query(
            """
            SELECT result_id, idempotency_key, command_hash, customer_id, from_account_id,
                   to_account_id, amount_minor, currency, status, ledger_transaction_id,
                   fds_case_id, failure_code, message, requested_by, requested_channel,
                   business_reference_id, business_date, created_at
            FROM customer_transfer_results
            WHERE idempotency_key = :idempotencyKey
            """.trimIndent(),
            mapOf("idempotencyKey" to idempotencyKey),
            this::mapTransferResult
        ).firstOrNull()

    private fun persistPostedTransfer(
        command: CustomerTransferCommand,
        customerId: String,
        commandHash: String,
        result: LedgerCommandResult
    ): CustomerTransferResultRecord {
        val existing = findTransferResultByIdempotencyKey(command.idempotencyKey)
        if (existing != null) {
            ensureSameCommandHash(existing, commandHash)
            return existing
        }
        return insertTransferResult(
            command = command,
            customerId = customerId,
            commandHash = commandHash,
            status = "POSTED",
            ledgerTransactionId = result.value.id,
            fdsCaseId = null,
            failureCode = null,
            message = "Transfer posted to ledger"
        )
    }

    private fun persistHeldTransfer(
        command: CustomerTransferCommand,
        customerId: String,
        commandHash: String
    ): CustomerTransferResultRecord {
        val resultId = nextId("TRR")
        val caseId = nextId("FDS")
        val businessDate = command.businessDate ?: LocalDate.now()
        val transferReferenceId = "TR-$resultId"
        val alertsJson = objectMapper.writeValueAsString(
            listOf(
                mapOf(
                    "ruleId" to "FDS-RULE-UNUSUAL-AMOUNT",
                    "message" to "Synthetic high amount threshold"
                )
            )
        )
        jdbc.update(
            """
            INSERT INTO fds_cases (
              fds_case_id, transfer_reference_id, customer_id, status, risk_score,
              alerts_json, owner_id, from_account_id, to_account_id, amount_minor,
              transfer_idempotency_key, requested_by, business_date, transfer_status
            )
            VALUES (
              :caseId, :transferReferenceId, :customerId, 'HELD', :riskScore,
              CAST(:alertsJson AS jsonb), NULL, :fromAccountId, :toAccountId, :amountMinor,
              :idempotencyKey, :requestedBy, :businessDate, 'HELD'
            )
            """.trimIndent(),
            mapOf(
                "caseId" to caseId,
                "transferReferenceId" to transferReferenceId,
                "customerId" to customerId,
                "riskScore" to 820,
                "alertsJson" to alertsJson,
                "fromAccountId" to command.fromAccountId,
                "toAccountId" to command.toAccountId,
                "amountMinor" to command.amountMinor,
                "idempotencyKey" to command.idempotencyKey,
                "requestedBy" to (command.requestedBy ?: customerId),
                "businessDate" to businessDate
            )
        )
        appendFdsTimeline(caseId, "FDS_HELD", "REQUESTED", "HELD", "FDS_ENGINE", "Synthetic customer transfer held")
        val held = insertTransferResult(
            command = command,
            customerId = customerId,
            commandHash = commandHash,
            resultId = resultId,
            status = "HELD",
            ledgerTransactionId = null,
            fdsCaseId = caseId,
            failureCode = null,
            message = "Transfer held for FDS review"
        )
        appendCustomerTransferAudit(
            eventType = "COMMAND_REQUESTED",
            customerId = customerId,
            accountId = command.fromAccountId,
            businessReferenceId = resultId,
            reason = "Customer transfer held for FDS review",
            payload = mapOf(
                "idempotencyKey" to command.idempotencyKey,
                "amountMinor" to command.amountMinor,
                "caseId" to caseId,
                "syntheticOnly" to true
            )
        )
        return held
    }

    private fun persistFailedTransfer(
        command: CustomerTransferCommand,
        customerId: String,
        commandHash: String,
        failureCode: String,
        message: String
    ): CustomerTransferResultRecord {
        val failed = insertTransferResult(
            command = command,
            customerId = customerId,
            commandHash = commandHash,
            status = "FAILED",
            ledgerTransactionId = null,
            fdsCaseId = null,
            failureCode = failureCode,
            message = message
        )
        appendCustomerTransferAudit(
            eventType = "COMMAND_REJECTED",
            customerId = customerId,
            accountId = command.fromAccountId,
            businessReferenceId = failed.resultId,
            reason = command.reason,
            payload = mapOf(
                "idempotencyKey" to command.idempotencyKey,
                "amountMinor" to command.amountMinor,
                "failureCode" to failureCode,
                "syntheticOnly" to true
            )
        )
        return failed
    }

    private fun insertTransferResult(
        command: CustomerTransferCommand,
        customerId: String,
        commandHash: String,
        status: String,
        ledgerTransactionId: String?,
        fdsCaseId: String?,
        failureCode: String?,
        message: String,
        resultId: String = nextId("TRR")
    ): CustomerTransferResultRecord {
        val businessDate = command.businessDate ?: LocalDate.now()
        jdbc.update(
            """
            INSERT INTO customer_transfer_results (
              result_id, idempotency_key, command_hash, customer_id,
              from_account_id, to_account_id, amount_minor, currency, status,
              ledger_transaction_id, fds_case_id, failure_code, message,
              requested_by, requested_channel, business_reference_id, business_date
            )
            VALUES (
              :resultId, :idempotencyKey, :commandHash, :customerId,
              :fromAccountId, :toAccountId, :amountMinor, :currency, :status,
              :ledgerTransactionId, :fdsCaseId, :failureCode, :message,
              :requestedBy, 'CUSTOMER_WEB', :businessReferenceId, :businessDate
            )
            """.trimIndent(),
            mapOf(
                "resultId" to resultId,
                "idempotencyKey" to command.idempotencyKey,
                "commandHash" to commandHash,
                "customerId" to customerId,
                "fromAccountId" to command.fromAccountId,
                "toAccountId" to command.toAccountId,
                "amountMinor" to command.amountMinor,
                "currency" to command.currency,
                "status" to status,
                "ledgerTransactionId" to ledgerTransactionId,
                "fdsCaseId" to fdsCaseId,
                "failureCode" to failureCode,
                "message" to message,
                "requestedBy" to (command.requestedBy ?: customerId),
                "businessReferenceId" to command.businessReferenceId,
                "businessDate" to businessDate
            )
        )
        return findTransferResultByIdempotencyKey(command.idempotencyKey)
            ?: error("customer transfer result was not inserted")
    }

    private fun appendFdsTimeline(
        caseId: String,
        eventType: String,
        fromStatus: String?,
        toStatus: String,
        actorId: String,
        note: String
    ) {
        jdbc.update(
            """
            INSERT INTO fds_case_timeline (
              fds_timeline_id, fds_case_id, event_type, from_status,
              to_status, actor_id, note, payload_json
            )
            VALUES (
              :timelineId, :caseId, :eventType, :fromStatus,
              :toStatus, :actorId, :note, '{}'::jsonb
            )
            """.trimIndent(),
            mapOf(
                "timelineId" to nextId("FDT"),
                "caseId" to caseId,
                "eventType" to eventType,
                "fromStatus" to fromStatus,
                "toStatus" to toStatus,
                "actorId" to actorId,
                "note" to note
            )
        )
    }

    private fun appendCustomerTransferAudit(
        eventType: String,
        customerId: String,
        accountId: String,
        businessReferenceId: String,
        reason: String?,
        payload: Map<String, Any?>
    ) {
        auditEvents.append(
            eventType = eventType,
            actorType = "CUSTOMER",
            actorId = customerId,
            actorRole = "CUSTOMER",
            screenId = "CWB-201",
            businessReferenceId = businessReferenceId,
            customerId = customerId,
            accountId = accountId,
            reason = reason,
            payload = payload
        )
    }

    private fun mapCustomerTransaction(rs: ResultSet, rowNum: Int): CustomerTransactionDto =
        CustomerTransactionDto(
            transactionId = rs.getString("ledger_transaction_id"),
            transactionType = rs.getString("transaction_type"),
            status = rs.getString("status"),
            businessDate = rs.getObject("business_date", LocalDate::class.java),
            postedAt = rs.getObject("posted_at", OffsetDateTime::class.java),
            accountId = rs.getString("account_id"),
            direction = rs.getString("direction"),
            amountMinor = rs.getLong("amount_minor"),
            currency = rs.getString("currency"),
            requestedChannel = rs.getString("requested_channel"),
            reason = rs.getString("reason")
        )

    private fun mapTransferStatus(rs: ResultSet, rowNum: Int): CustomerTransferStatusDto =
        CustomerTransferStatusDto(
            resultId = rs.getString("result_id"),
            transactionId = rs.getString("ledger_transaction_id"),
            caseId = rs.getString("fds_case_id"),
            transferReferenceId = rs.getString("transfer_reference_id"),
            customerId = rs.getString("customer_id"),
            status = rs.getString("transfer_result_status"),
            caseStatus = rs.getString("case_status"),
            transferStatus = rs.getString("transfer_status"),
            fromAccountId = rs.getString("from_account_id"),
            toAccountId = rs.getString("to_account_id"),
            amountMinor = (rs.getObject("amount_minor") as Number?)?.toLong(),
            currency = rs.getString("currency"),
            businessDate = rs.getObject("business_date", LocalDate::class.java),
            riskScore = nullableInt(rs, "risk_score"),
            idempotencyKey = rs.getString("idempotency_key"),
            failureCode = rs.getString("failure_code"),
            message = rs.getString("message")
        )

    private fun mapTransferResult(rs: ResultSet, rowNum: Int): CustomerTransferResultRecord =
        CustomerTransferResultRecord(
            resultId = rs.getString("result_id"),
            idempotencyKey = rs.getString("idempotency_key"),
            commandHash = rs.getString("command_hash"),
            customerId = rs.getString("customer_id"),
            fromAccountId = rs.getString("from_account_id"),
            toAccountId = rs.getString("to_account_id"),
            amountMinor = rs.getLong("amount_minor"),
            currency = rs.getString("currency"),
            status = rs.getString("status"),
            ledgerTransactionId = rs.getString("ledger_transaction_id"),
            fdsCaseId = rs.getString("fds_case_id"),
            failureCode = rs.getString("failure_code"),
            message = rs.getString("message"),
            requestedBy = rs.getString("requested_by"),
            requestedChannel = rs.getString("requested_channel"),
            businessReferenceId = rs.getString("business_reference_id"),
            businessDate = rs.getObject("business_date", LocalDate::class.java),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
        )

    private fun ensureSameCommandHash(existing: CustomerTransferResultRecord, commandHash: String) {
        if (existing.commandHash != commandHash) {
            throw WorkflowErrors.validation("idempotency key was reused with a different customer transfer command")
        }
    }

    private fun commandHash(command: CustomerTransferCommand, customerId: String): String =
        sha256(
            objectMapper.writeValueAsString(
                linkedMapOf(
                    "customerId" to customerId,
                    "fromAccountId" to command.fromAccountId,
                    "toAccountId" to command.toAccountId,
                    "amountMinor" to command.amountMinor,
                    "currency" to command.currency,
                    "requestedBy" to (command.requestedBy ?: customerId),
                    "businessDate" to command.businessDate?.toString(),
                    "reason" to command.reason,
                    "businessReferenceId" to command.businessReferenceId
                )
            )
        )

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun nextId(prefix: String): String =
        "$prefix-${UUID.randomUUID().toString().uppercase()}"

    private fun nullableInt(rs: ResultSet, column: String): Int? {
        val value = rs.getInt(column)
        return if (rs.wasNull()) null else value
    }

    private companion object {
        const val SERIALIZABLE_CUSTOMER_COMMAND_MAX_ATTEMPTS = 5
        const val FDS_HIGH_AMOUNT_THRESHOLD_MINOR = 5_000_000L
    }
}

private data class CustomerTransferResultRecord(
    val resultId: String,
    val idempotencyKey: String,
    val commandHash: String,
    val customerId: String,
    val fromAccountId: String,
    val toAccountId: String,
    val amountMinor: Long,
    val currency: String,
    val status: String,
    val ledgerTransactionId: String?,
    val fdsCaseId: String?,
    val failureCode: String?,
    val message: String,
    val requestedBy: String,
    val requestedChannel: String,
    val businessReferenceId: String?,
    val businessDate: LocalDate,
    val createdAt: OffsetDateTime
) {
    fun toDto(): CustomerTransferDto =
        CustomerTransferDto(
            resultId = resultId,
            transactionId = ledgerTransactionId,
            status = status,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amountMinor = amountMinor,
            currency = currency,
            idempotencyKey = idempotencyKey,
            caseId = fdsCaseId,
            failureCode = failureCode,
            message = message
        )
}
