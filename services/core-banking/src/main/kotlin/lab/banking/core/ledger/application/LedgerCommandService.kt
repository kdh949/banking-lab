package lab.banking.core.ledger.application

import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import lab.banking.core.common.BankingLabDomainException
import lab.banking.core.ledger.domain.BANK_SUSPENSE_ACCOUNT_ID
import lab.banking.core.ledger.domain.DailyClosingDto
import lab.banking.core.ledger.domain.DailyClosingResult
import lab.banking.core.ledger.domain.LedgerCommandResult
import lab.banking.core.ledger.domain.LedgerInvariants
import lab.banking.core.ledger.domain.LedgerPostingDto
import lab.banking.core.ledger.domain.LedgerPostingInput
import lab.banking.core.ledger.domain.LedgerTransactionDto
import lab.banking.core.ledger.domain.PostingDirection
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.ResultSetExtractor
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class LedgerCommandService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun deposit(command: DepositCommand): LedgerCommandResult =
        postLedgerCommand("DEPOSIT", command.idempotencyKey, command) {
            requirePositiveAmount(command.amountMinor)
            val businessDate = command.businessDate ?: LocalDate.now()
            ensureBusinessDateOpen(businessDate)
            ensureBankSuspenseAccount(command.currency)
            lockActiveAccounts(listOf(BANK_SUSPENSE_ACCOUNT_ID, command.accountId))
            createPostedTransaction(
                transactionType = "DEPOSIT",
                idempotencyKey = command.idempotencyKey,
                businessReferenceId = command.businessReferenceId,
                businessDate = businessDate,
                requestedBy = command.requestedBy,
                requestedChannel = command.requestedChannel,
                reason = command.reason,
                postings = listOf(
                    LedgerPostingInput(BANK_SUSPENSE_ACCOUNT_ID, PostingDirection.DEBIT, command.amountMinor, command.currency),
                    LedgerPostingInput(command.accountId, PostingDirection.CREDIT, command.amountMinor, command.currency)
                )
            )
        }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun withdraw(command: WithdrawalCommand): LedgerCommandResult =
        withdrawSerializable(command)

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun withdrawRepeatableReadForIsolationTest(command: WithdrawalCommand): LedgerCommandResult =
        withdrawSerializable(command)

    private fun withdrawSerializable(command: WithdrawalCommand): LedgerCommandResult =
        postLedgerCommand("WITHDRAWAL", command.idempotencyKey, command) {
            requirePositiveAmount(command.amountMinor)
            val businessDate = command.businessDate ?: LocalDate.now()
            ensureBusinessDateOpen(businessDate)
            ensureBankSuspenseAccount(command.currency)
            val balances = lockActiveAccounts(listOf(command.accountId, BANK_SUSPENSE_ACCOUNT_ID))
            val fromBalance = balances.getValue(command.accountId)
            if (fromBalance.availableBalanceMinor < command.amountMinor) {
                throw ledgerConflict(
                    code = "LEDGER_INSUFFICIENT_AVAILABLE_BALANCE",
                    message = "insufficient available balance for ${command.accountId}",
                    invariant = "available_balance >= withdrawal amount"
                )
            }
            createPostedTransaction(
                transactionType = "WITHDRAWAL",
                idempotencyKey = command.idempotencyKey,
                businessReferenceId = command.businessReferenceId,
                businessDate = businessDate,
                requestedBy = command.requestedBy,
                requestedChannel = command.requestedChannel,
                reason = command.reason,
                postings = listOf(
                    LedgerPostingInput(command.accountId, PostingDirection.DEBIT, command.amountMinor, command.currency),
                    LedgerPostingInput(BANK_SUSPENSE_ACCOUNT_ID, PostingDirection.CREDIT, command.amountMinor, command.currency)
                )
            )
        }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun internalTransfer(command: InternalTransferCommand): LedgerCommandResult =
        postLedgerCommand("INTERNAL_TRANSFER", command.idempotencyKey, command) {
            requirePositiveAmount(command.amountMinor)
            if (command.fromAccountId == command.toAccountId) {
                throw ledgerValidation("fromAccountId and toAccountId must differ")
            }
            val businessDate = command.businessDate ?: LocalDate.now()
            ensureBusinessDateOpen(businessDate)
            val balances = lockActiveAccounts(listOf(command.fromAccountId, command.toAccountId))
            val fromBalance = balances.getValue(command.fromAccountId)
            if (fromBalance.availableBalanceMinor < command.amountMinor) {
                throw ledgerConflict(
                    code = "LEDGER_INSUFFICIENT_AVAILABLE_BALANCE",
                    message = "insufficient available balance for ${command.fromAccountId}",
                    invariant = "available_balance >= transfer amount"
                )
            }
            createPostedTransaction(
                transactionType = "INTERNAL_TRANSFER",
                idempotencyKey = command.idempotencyKey,
                businessReferenceId = command.businessReferenceId,
                businessDate = businessDate,
                requestedBy = command.requestedBy,
                requestedChannel = command.requestedChannel,
                reason = command.reason,
                postings = listOf(
                    LedgerPostingInput(command.fromAccountId, PostingDirection.DEBIT, command.amountMinor, command.currency),
                    LedgerPostingInput(command.toAccountId, PostingDirection.CREDIT, command.amountMinor, command.currency)
                )
            )
        }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun reverseTransaction(command: ReversalCommand): LedgerCommandResult =
        postLedgerCommand("REVERSAL", command.idempotencyKey, command) {
            val businessDate = command.businessDate ?: LocalDate.now()
            ensureBusinessDateOpen(businessDate)
            val original = findTransaction(command.originalTransactionId)
                ?: throw ledgerNotFound("original transaction not found: ${command.originalTransactionId}")
            if (original.transactionType == "REVERSAL") {
                throw ledgerValidation("reversal transactions cannot be reversed directly")
            }
            val existingReversal = findExistingReversal(command.originalTransactionId)
            if (existingReversal != null && existingReversal.idempotencyKey != command.idempotencyKey) {
                throw ledgerConflict(
                    code = "LEDGER_ALREADY_REVERSED",
                    message = "transaction already reversed: ${command.originalTransactionId}",
                    invariant = "one reversal per original transaction"
                )
            }
            val reversalPostings = original.postings.map {
                LedgerPostingInput(
                    accountId = it.accountId,
                    direction = if (it.direction == PostingDirection.DEBIT) PostingDirection.CREDIT else PostingDirection.DEBIT,
                    amountMinor = it.amountMinor,
                    currency = it.currency,
                    postingType = "REVERSAL"
                )
            }
            lockActiveAccounts(reversalPostings.map { it.accountId })
            createPostedTransaction(
                transactionType = "REVERSAL",
                idempotencyKey = command.idempotencyKey,
                businessReferenceId = command.businessReferenceId ?: "${original.businessReferenceId}-REV",
                businessDate = businessDate,
                requestedBy = command.requestedBy,
                requestedChannel = command.requestedChannel,
                reason = command.reason,
                originalTransactionId = command.originalTransactionId,
                postings = reversalPostings
            )
        }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun adjustment(command: AdjustmentCommand): LedgerCommandResult =
        postLedgerCommand("ADJUSTMENT", command.idempotencyKey, command) {
            requirePositiveAmount(command.amountMinor)
            val businessDate = command.businessDate ?: LocalDate.now()
            ensureBusinessDateOpen(businessDate)
            ensureBankSuspenseAccount(command.currency)
            lockActiveAccounts(listOf(command.accountId, BANK_SUSPENSE_ACCOUNT_ID))
            val accountPosting = LedgerPostingInput(
                accountId = command.accountId,
                direction = command.direction,
                amountMinor = command.amountMinor,
                currency = command.currency,
                postingType = "ADJUSTMENT"
            )
            val suspensePosting = LedgerPostingInput(
                accountId = BANK_SUSPENSE_ACCOUNT_ID,
                direction = if (command.direction == PostingDirection.CREDIT) PostingDirection.DEBIT else PostingDirection.CREDIT,
                amountMinor = command.amountMinor,
                currency = command.currency,
                postingType = "ADJUSTMENT"
            )
            createPostedTransaction(
                transactionType = "ADJUSTMENT",
                idempotencyKey = command.idempotencyKey,
                businessReferenceId = command.businessReferenceId,
                businessDate = businessDate,
                requestedBy = command.requestedBy,
                requestedChannel = command.requestedChannel,
                reason = command.reason,
                postings = if (command.direction == PostingDirection.CREDIT) {
                    listOf(suspensePosting, accountPosting)
                } else {
                    listOf(accountPosting, suspensePosting)
                }
            )
        }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun closeBusinessDay(command: DailyClosingCommand): DailyClosingResult {
        val commandHash = commandHash(command)
        acquireIdempotencyLock(command.idempotencyKey)
        val existing = findIdempotency(command.idempotencyKey)
        if (existing != null) {
            ensureSameCommandHash(existing, commandHash)
            return readDailyClosingResult(existing.responseJson, replayed = true)
        }
        insertIdempotency(command.idempotencyKey, "DAILY_CLOSING", commandHash)
        jdbc.update(
            """
            INSERT INTO daily_closings (business_date, status, closed_by, closed_at, ledger_total_hash)
            VALUES (:businessDate, 'CLOSED', :closedBy, now(), :ledgerTotalHash)
            ON CONFLICT (business_date) DO UPDATE SET
              status = 'CLOSED',
              closed_by = EXCLUDED.closed_by,
              closed_at = EXCLUDED.closed_at,
              ledger_total_hash = EXCLUDED.ledger_total_hash
            """.trimIndent(),
            mapOf(
                "businessDate" to command.businessDate,
                "closedBy" to command.requestedBy,
                "ledgerTotalHash" to sha256("closed:${command.businessDate}")
            )
        )
        val result = DailyClosingResult(
            item = DailyClosingDto(command.businessDate, "CLOSED", command.requestedBy),
            replayed = false
        )
        updateIdempotencyResponse(command.idempotencyKey, null, result)
        insertOutboxEvent(
            aggregateType = "DailyClosing",
            aggregateId = command.businessDate.toString(),
            eventType = "DailyClosingCompleted",
            idempotencyKey = command.idempotencyKey,
            payload = result
        )
        return result
    }

    fun balance(accountId: String): AccountBalance {
        ensureBalanceProjection(accountId)
        return jdbc.queryForObject(
            """
            SELECT account_id, currency, ledger_balance_minor, available_balance_minor, hold_amount_minor
            FROM account_balance_projections
            WHERE account_id = :accountId
            """.trimIndent(),
            mapOf("accountId" to accountId),
            ::mapBalance
        ) ?: throw ledgerNotFound("account balance not found: $accountId")
    }

    fun countTransactionsByIdempotencyKey(idempotencyKey: String): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM ledger_transactions WHERE idempotency_key = :idempotencyKey",
            mapOf("idempotencyKey" to idempotencyKey),
            Int::class.java
        ) ?: 0

    private fun postLedgerCommand(commandType: String, idempotencyKey: String, command: Any, create: () -> String): LedgerCommandResult {
        val commandHash = commandHash(command)
        acquireIdempotencyLock(idempotencyKey)
        val existing = findIdempotency(idempotencyKey)
        if (existing != null) {
            ensureSameCommandHash(existing, commandHash)
            val transactionId = existing.ledgerTransactionId
                ?: throw ledgerConflict("IDEMPOTENCY_RECORD_INCOMPLETE", "idempotency record has no ledger transaction", "idempotency record points at one business result")
            return LedgerCommandResult(findTransaction(transactionId) ?: throw ledgerNotFound("ledger transaction not found: $transactionId"), replayed = true)
        }
        insertIdempotency(idempotencyKey, commandType, commandHash)
        val transactionId = create()
        val result = LedgerCommandResult(findTransaction(transactionId) ?: throw ledgerNotFound("ledger transaction not found: $transactionId"), replayed = false)
        updateIdempotencyResponse(idempotencyKey, transactionId, result)
        insertOutboxEvent(
            aggregateType = "LedgerTransaction",
            aggregateId = transactionId,
            eventType = when (commandType) {
                "REVERSAL" -> "LedgerTransactionReversed"
                "ADJUSTMENT" -> "AdjustmentPosted"
                else -> "LedgerTransactionPosted"
            },
            idempotencyKey = idempotencyKey,
            payload = result
        )
        return result
    }

    private fun createPostedTransaction(
        transactionType: String,
        idempotencyKey: String,
        businessReferenceId: String?,
        businessDate: LocalDate,
        requestedBy: String,
        requestedChannel: String,
        reason: String?,
        postings: List<LedgerPostingInput>,
        originalTransactionId: String? = null
    ): String {
        val transactionId = nextTransactionId(transactionType)
        LedgerInvariants.requireBalanced(transactionId, postings)
        jdbc.update(
            """
            INSERT INTO ledger_transactions (
              ledger_transaction_id, transaction_type, business_reference_id, idempotency_key, business_date,
              status, requested_by, requested_channel, posted_at, original_transaction_id, reason, metadata_json
            )
            VALUES (
              :id, :transactionType, :businessReferenceId, :idempotencyKey, :businessDate,
              'POSTED', :requestedBy, :requestedChannel, now(), :originalTransactionId, :reason, CAST(:metadata AS jsonb)
            )
            """.trimIndent(),
            mapOf(
                "id" to transactionId,
                "transactionType" to transactionType,
                "businessReferenceId" to (businessReferenceId ?: transactionId),
                "idempotencyKey" to idempotencyKey,
                "businessDate" to businessDate,
                "requestedBy" to requestedBy,
                "requestedChannel" to requestedChannel,
                "originalTransactionId" to originalTransactionId,
                "reason" to reason,
                "metadata" to objectMapper.writeValueAsString(mapOf("syntheticOnly" to true, "reason" to reason))
            )
        )
        postings.forEachIndexed { index, posting ->
            val postingId = "$transactionId-P${(index + 1).toString().padStart(3, '0')}"
            jdbc.update(
                """
                INSERT INTO ledger_postings (
                  ledger_posting_id, ledger_transaction_id, account_id, currency, direction, amount_minor, posting_type
                )
                VALUES (:postingId, :transactionId, :accountId, :currency, :direction, :amountMinor, :postingType)
                """.trimIndent(),
                mapOf(
                    "postingId" to postingId,
                    "transactionId" to transactionId,
                    "accountId" to posting.accountId,
                    "currency" to posting.currency,
                    "direction" to posting.direction.name,
                    "amountMinor" to posting.amountMinor,
                    "postingType" to posting.postingType
                )
            )
            updateBalanceProjection(posting, postingId)
        }
        return transactionId
    }

    private fun updateBalanceProjection(posting: LedgerPostingInput, postingId: String) {
        val signedAmount = LedgerInvariants.signedAmount(posting)
        jdbc.update(
            """
            UPDATE account_balance_projections
            SET ledger_balance_minor = ledger_balance_minor + :signedAmount,
                available_balance_minor = ledger_balance_minor + :signedAmount - hold_amount_minor,
                last_posting_id = :postingId,
                version = version + 1,
                updated_at = now()
            WHERE account_id = :accountId
              AND currency = :currency
            """.trimIndent(),
            mapOf(
                "signedAmount" to signedAmount,
                "postingId" to postingId,
                "accountId" to posting.accountId,
                "currency" to posting.currency
            )
        )
    }

    private fun lockActiveAccounts(accountIds: List<String>): Map<String, AccountBalance> {
        val uniqueIds = accountIds.distinct().sorted()
        val accounts = jdbc.query(
            """
            SELECT account_id, status
            FROM accounts
            WHERE account_id IN (:accountIds)
            ORDER BY account_id
            FOR UPDATE
            """.trimIndent(),
            mapOf("accountIds" to uniqueIds)
        ) { rs, _ -> rs.getString("account_id") to rs.getString("status") }
            .toMap()
        val missing = uniqueIds.filterNot { accounts.containsKey(it) }
        if (missing.isNotEmpty()) {
            throw ledgerNotFound("account not found: ${missing.joinToString(", ")}")
        }
        val inactive = accounts.entries.firstOrNull { it.value != "ACTIVE" }
        if (inactive != null) {
            throw ledgerConflict("LEDGER_ACCOUNT_NOT_ACTIVE", "account is not active: ${inactive.key}", "posting account must be active")
        }
        uniqueIds.forEach(::ensureBalanceProjection)
        return jdbc.query(
            """
            SELECT account_id, currency, ledger_balance_minor, available_balance_minor, hold_amount_minor
            FROM account_balance_projections
            WHERE account_id IN (:accountIds)
            ORDER BY account_id
            FOR UPDATE
            """.trimIndent(),
            mapOf("accountIds" to uniqueIds),
            ::mapBalance
        ).associateBy { it.accountId }
    }

    private fun ensureBalanceProjection(accountId: String) {
        jdbc.update(
            """
            INSERT INTO account_balance_projections (account_id, currency, ledger_balance_minor, available_balance_minor)
            SELECT account_id, currency, 0, 0
            FROM accounts
            WHERE account_id = :accountId
            ON CONFLICT (account_id, currency) DO NOTHING
            """.trimIndent(),
            mapOf("accountId" to accountId)
        )
    }

    private fun ensureBankSuspenseAccount(currency: String) {
        jdbc.update(
            """
            INSERT INTO customers (customer_id, customer_name, customer_grade, risk_grade)
            VALUES ('BANK', 'Synthetic Bank Suspense', 'SYSTEM', 'LOW')
            ON CONFLICT (customer_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO accounts (account_id, customer_id, account_no, currency, status)
            VALUES (:accountId, 'BANK', 'LAB-000-000000', :currency, 'ACTIVE')
            ON CONFLICT (account_id) DO NOTHING
            """.trimIndent(),
            mapOf("accountId" to BANK_SUSPENSE_ACCOUNT_ID, "currency" to currency)
        )
        ensureBalanceProjection(BANK_SUSPENSE_ACCOUNT_ID)
    }

    private fun ensureBusinessDateOpen(businessDate: LocalDate) {
        val closedCount = jdbc.queryForObject(
            "SELECT count(*) FROM daily_closings WHERE business_date = :businessDate AND status = 'CLOSED'",
            mapOf("businessDate" to businessDate),
            Int::class.java
        ) ?: 0
        if (closedCount > 0) {
            throw ledgerConflict(
                code = "LEDGER_CLOSED_DAY_IMMUTABLE",
                message = "business day is closed: $businessDate",
                invariant = "closed business dates reject direct posting"
            )
        }
    }

    private fun findTransaction(transactionId: String): LedgerTransactionDto? {
        val transactions = jdbc.query(
            """
            SELECT ledger_transaction_id, transaction_type, business_reference_id, idempotency_key, business_date,
                   status, requested_by, requested_channel, posted_at, original_transaction_id
            FROM ledger_transactions
            WHERE ledger_transaction_id = :transactionId
            """.trimIndent(),
            mapOf("transactionId" to transactionId)
        ) { rs, _ -> mapTransaction(rs, emptyList()) }
        val transaction = transactions.firstOrNull() ?: return null
        val postings = jdbc.query(
            """
            SELECT ledger_posting_id, ledger_transaction_id, account_id, currency, direction, amount_minor, posting_type, created_at
            FROM ledger_postings
            WHERE ledger_transaction_id = :transactionId
            ORDER BY ledger_posting_id
            """.trimIndent(),
            mapOf("transactionId" to transactionId),
            ::mapPosting
        )
        return transaction.copy(postings = postings)
    }

    private fun findExistingReversal(originalTransactionId: String): LedgerTransactionDto? =
        jdbc.query(
            """
            SELECT ledger_transaction_id
            FROM ledger_transactions
            WHERE original_transaction_id = :originalTransactionId
              AND transaction_type = 'REVERSAL'
            ORDER BY created_at
            LIMIT 1
            """.trimIndent(),
            mapOf("originalTransactionId" to originalTransactionId)
        ) { rs, _ -> rs.getString("ledger_transaction_id") }
            .firstOrNull()
            ?.let(::findTransaction)

    private fun acquireIdempotencyLock(idempotencyKey: String) {
        requireNonBlank(idempotencyKey, "idempotencyKey")
        jdbc.query(
            "SELECT pg_advisory_xact_lock(hashtext(:idempotencyKey))",
            mapOf("idempotencyKey" to idempotencyKey),
            ResultSetExtractor<Unit> { }
        )
    }

    private fun findIdempotency(idempotencyKey: String): IdempotencyRecord? =
        jdbc.query(
            """
            SELECT idempotency_key, command_type, command_hash, ledger_transaction_id, response_json
            FROM idempotency_keys
            WHERE idempotency_key = :idempotencyKey
            FOR UPDATE
            """.trimIndent(),
            mapOf("idempotencyKey" to idempotencyKey)
        ) { rs, _ ->
            IdempotencyRecord(
                idempotencyKey = rs.getString("idempotency_key"),
                commandType = rs.getString("command_type"),
                commandHash = rs.getString("command_hash"),
                ledgerTransactionId = rs.getString("ledger_transaction_id"),
                responseJson = rs.getString("response_json")
            )
        }.firstOrNull()

    private fun ensureSameCommandHash(existing: IdempotencyRecord, commandHash: String) {
        if (existing.commandHash != commandHash) {
            throw BankingLabDomainException(
                code = "IDEMPOTENCY_KEY_CONFLICT",
                status = HttpStatus.CONFLICT,
                domain = "idempotency",
                invariant = "same idempotency key must represent the same command hash",
                message = "idempotency key was reused with a different command",
                causeText = "A client retried with the same idempotency key but changed the command payload.",
                fix = "Retry with the original payload or use a new idempotency key for a distinct command."
            )
        }
    }

    private fun insertIdempotency(idempotencyKey: String, commandType: String, commandHash: String) {
        jdbc.update(
            """
            INSERT INTO idempotency_keys (idempotency_key, command_type, command_hash, expires_at)
            VALUES (:idempotencyKey, :commandType, :commandHash, now() + interval '30 days')
            """.trimIndent(),
            mapOf(
                "idempotencyKey" to idempotencyKey,
                "commandType" to commandType,
                "commandHash" to commandHash
            )
        )
    }

    private fun updateIdempotencyResponse(idempotencyKey: String, ledgerTransactionId: String?, response: Any) {
        val responseJson = objectMapper.writeValueAsString(response)
        jdbc.update(
            """
            UPDATE idempotency_keys
            SET ledger_transaction_id = :ledgerTransactionId,
                response_hash = :responseHash,
                response_json = CAST(:responseJson AS jsonb)
            WHERE idempotency_key = :idempotencyKey
            """.trimIndent(),
            mapOf(
                "ledgerTransactionId" to ledgerTransactionId,
                "responseHash" to sha256(responseJson),
                "responseJson" to responseJson,
                "idempotencyKey" to idempotencyKey
            )
        )
    }

    private fun insertOutboxEvent(
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        idempotencyKey: String,
        payload: Any
    ) {
        jdbc.update(
            """
            INSERT INTO outbox_events (
              outbox_event_id, aggregate_type, aggregate_id, event_type, idempotency_key,
              payload_json, headers_json, status
            )
            VALUES (:eventId, :aggregateType, :aggregateId, :eventType, :idempotencyKey,
                    CAST(:payload AS jsonb), CAST(:headers AS jsonb), 'PENDING')
            ON CONFLICT (aggregate_id, event_type, idempotency_key) DO NOTHING
            """.trimIndent(),
            mapOf(
                "eventId" to "EVT-${UUID.randomUUID()}",
                "aggregateType" to aggregateType,
                "aggregateId" to aggregateId,
                "eventType" to eventType,
                "idempotencyKey" to idempotencyKey,
                "payload" to objectMapper.writeValueAsString(payload),
                "headers" to objectMapper.writeValueAsString(mapOf("syntheticOnly" to true))
            )
        )
    }

    private fun readDailyClosingResult(responseJson: String?, replayed: Boolean): DailyClosingResult {
        val stored = responseJson?.let { objectMapper.readValue(it, DailyClosingResult::class.java) }
            ?: throw ledgerConflict("IDEMPOTENCY_RECORD_INCOMPLETE", "idempotency record has no response", "idempotency record points at one business result")
        return stored.copy(replayed = replayed)
    }

    private fun mapTransaction(rs: ResultSet, postings: List<LedgerPostingDto>): LedgerTransactionDto =
        LedgerTransactionDto(
            id = rs.getString("ledger_transaction_id"),
            transactionType = rs.getString("transaction_type"),
            businessReferenceId = rs.getString("business_reference_id"),
            idempotencyKey = rs.getString("idempotency_key"),
            businessDate = rs.getObject("business_date", LocalDate::class.java),
            status = rs.getString("status"),
            requestedBy = rs.getString("requested_by"),
            requestedChannel = rs.getString("requested_channel"),
            postedAt = rs.getObject("posted_at", OffsetDateTime::class.java),
            originalTransactionId = rs.getString("original_transaction_id"),
            postings = postings
        )

    private fun mapPosting(rs: ResultSet, rowNum: Int): LedgerPostingDto =
        LedgerPostingDto(
            id = rs.getString("ledger_posting_id"),
            ledgerTransactionId = rs.getString("ledger_transaction_id"),
            accountId = rs.getString("account_id"),
            currency = rs.getString("currency").trim(),
            direction = PostingDirection.valueOf(rs.getString("direction")),
            amountMinor = rs.getLong("amount_minor"),
            postingType = rs.getString("posting_type"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
        )

    private fun mapBalance(rs: ResultSet, rowNum: Int): AccountBalance =
        AccountBalance(
            accountId = rs.getString("account_id"),
            currency = rs.getString("currency").trim(),
            ledgerBalanceMinor = rs.getLong("ledger_balance_minor"),
            availableBalanceMinor = rs.getLong("available_balance_minor"),
            holdAmountMinor = rs.getLong("hold_amount_minor")
        )

    private fun nextTransactionId(transactionType: String): String =
        when (transactionType) {
            "DEPOSIT" -> "TX-DEP"
            "WITHDRAWAL" -> "TX-WDR"
            "INTERNAL_TRANSFER" -> "TX-TRF"
            "REVERSAL" -> "TX-REV"
            "ADJUSTMENT" -> "TX-ADJ"
            else -> "TX-LED"
        } + "-${UUID.randomUUID().toString().uppercase()}"

    private fun commandHash(command: Any): String = sha256(objectMapper.writeValueAsString(command))

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun requirePositiveAmount(amountMinor: Long) {
        if (amountMinor <= 0) {
            throw ledgerValidation("amountMinor must be a positive integer minor-unit value")
        }
    }

    private fun requireNonBlank(value: String, field: String) {
        if (value.isBlank()) {
            throw ledgerValidation("$field is required")
        }
    }

    private fun ledgerValidation(message: String): BankingLabDomainException =
        BankingLabDomainException(
            code = "LEDGER_COMMAND_VALIDATION_FAILED",
            status = HttpStatus.BAD_REQUEST,
            domain = "ledger",
            message = message,
            causeText = "The ledger command failed precondition validation before posting.",
            fix = "Send a valid command body with positive amount, active accounts, and idempotency key."
        )

    private fun ledgerNotFound(message: String): BankingLabDomainException =
        BankingLabDomainException(
            code = "LEDGER_RESOURCE_NOT_FOUND",
            status = HttpStatus.NOT_FOUND,
            domain = "ledger",
            message = message,
            causeText = "The requested ledger resource does not exist in PostgreSQL.",
            fix = "Check the account or transaction identifier against the synthetic dataset."
        )

    private fun ledgerConflict(code: String, message: String, invariant: String): BankingLabDomainException =
        BankingLabDomainException(
            code = code,
            status = HttpStatus.CONFLICT,
            domain = "ledger",
            invariant = invariant,
            message = message,
            causeText = "The command would violate a banking ledger invariant.",
            fix = "Use a reversal, adjustment, open business date, or a command amount within available balance."
        )

    data class AccountBalance(
        val accountId: String,
        val currency: String,
        val ledgerBalanceMinor: Long,
        val availableBalanceMinor: Long,
        val holdAmountMinor: Long
    )

    private data class IdempotencyRecord(
        val idempotencyKey: String,
        val commandType: String,
        val commandHash: String,
        val ledgerTransactionId: String?,
        val responseJson: String?
    )
}
