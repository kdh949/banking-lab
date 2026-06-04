package lab.banking.core.ledger.application

import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import lab.banking.core.approval.ApprovalBusinessTypes
import lab.banking.core.audit.AuditEventAppender
import lab.banking.core.common.BankingLabDomainException
import lab.banking.core.ledger.domain.BANK_LOAN_ASSET_ACCOUNT_ID
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
    private val objectMapper: ObjectMapper,
    private val auditEvents: AuditEventAppender
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
            enforcePostingLimits(
                accountId = command.accountId,
                channel = command.requestedChannel,
                businessDate = businessDate,
                amountMinor = command.amountMinor
            )
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
            enforcePostingLimits(
                accountId = command.fromAccountId,
                channel = command.requestedChannel,
                businessDate = businessDate,
                amountMinor = command.amountMinor
            )
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
                throw ledgerConflict(
                    code = "LEDGER_REVERSAL_POLICY_VIOLATION",
                    message = "reversal transactions cannot be reversed directly",
                    invariant = "reversal references original transaction"
                )
            }
            val existingReversal = findExistingReversal(command.originalTransactionId)
            if (existingReversal != null && existingReversal.idempotencyKey != command.idempotencyKey) {
                throw ledgerConflict(
                    code = "LEDGER_REVERSAL_POLICY_VIOLATION",
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
            ).also {
                releaseLimitUsageForReversal(original)
            }
        }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun adjustment(command: AdjustmentCommand): LedgerCommandResult =
        postLedgerCommand("ADJUSTMENT", command.idempotencyKey, command) {
            requirePositiveAmount(command.amountMinor)
            requireNonBlank(command.reason, "reason")
            requireNonBlank(command.businessReferenceId, "businessReferenceId")
            val businessReferenceId = command.businessReferenceId!!
            requireApprovedOperation(
                approvalId = command.approvalId,
                businessType = ApprovalBusinessTypes.RECONCILIATION_ADJUSTMENT,
                businessReferenceId = businessReferenceId,
                requestedBy = command.requestedBy
            )
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
            ).also { transactionId ->
                appendAuditEvent(
                    eventType = "COMMAND_EXECUTED",
                    actorId = command.requestedBy,
                    actorRole = "OPS_OPERATOR",
                    screenId = "OPS-201",
                    businessReferenceId = businessReferenceId,
                    accountId = command.accountId,
                    reason = command.reason,
                    payload = mapOf(
                        "ledgerTransactionId" to transactionId,
                        "approvalId" to command.approvalId,
                        "businessType" to ApprovalBusinessTypes.RECONCILIATION_ADJUSTMENT,
                        "syntheticOnly" to true
                    )
                )
            }
        }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun interestPosting(command: InterestPostingCommand): LedgerCommandResult =
        postLedgerCommand("INTEREST_POSTING", command.idempotencyKey, command) {
            requireNonBlank(command.reason, "reason")
            if (command.credits.isEmpty()) {
                throw ledgerValidation("credits must contain at least one account interest line")
            }
            val groupedCredits = command.credits
                .groupBy { it.accountId }
                .mapValues { (_, lines) -> lines.sumOf { it.amountMinor } }
                .filterValues { it > 0 }
                .toSortedMap()
            if (groupedCredits.isEmpty()) {
                throw ledgerValidation("credits must contain positive interest amountMinor values")
            }
            val businessDate = command.businessDate ?: LocalDate.now()
            ensureBusinessDateOpen(businessDate)
            ensureBankSuspenseAccount(command.currency)
            lockActiveAccounts(listOf(BANK_SUSPENSE_ACCOUNT_ID) + groupedCredits.keys)
            val totalInterestMinor = groupedCredits.values.sum()
            createPostedTransaction(
                transactionType = "INTEREST_POSTING",
                idempotencyKey = command.idempotencyKey,
                businessReferenceId = command.businessReferenceId,
                businessDate = businessDate,
                requestedBy = command.requestedBy,
                requestedChannel = command.requestedChannel,
                reason = command.reason,
                postings = listOf(
                    LedgerPostingInput(BANK_SUSPENSE_ACCOUNT_ID, PostingDirection.DEBIT, totalInterestMinor, command.currency, "INTEREST")
                ) + groupedCredits.map { (accountId, amountMinor) ->
                    LedgerPostingInput(accountId, PostingDirection.CREDIT, amountMinor, command.currency, "INTEREST")
                }
            )
        }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun feePosting(command: FeePostingCommand): LedgerCommandResult =
        postLedgerCommand("FEE_POSTING", command.idempotencyKey, command) {
            requireNonBlank(command.reason, "reason")
            if (command.charges.isEmpty()) {
                throw ledgerValidation("charges must contain at least one account fee line")
            }
            val groupedCharges = command.charges
                .groupBy { it.accountId }
                .mapValues { (_, lines) -> lines.sumOf { it.amountMinor } }
                .filterValues { it > 0 }
                .toSortedMap()
            if (groupedCharges.isEmpty()) {
                throw ledgerValidation("charges must contain positive fee amountMinor values")
            }
            val businessDate = command.businessDate ?: LocalDate.now()
            ensureBusinessDateOpen(businessDate)
            ensureBankSuspenseAccount(command.currency)
            val balances = lockActiveAccounts(groupedCharges.keys.toList() + BANK_SUSPENSE_ACCOUNT_ID)
            groupedCharges.forEach { (accountId, amountMinor) ->
                val balance = balances.getValue(accountId)
                if (balance.availableBalanceMinor < amountMinor) {
                    throw ledgerConflict(
                        code = "LEDGER_INSUFFICIENT_AVAILABLE_BALANCE",
                        message = "insufficient available balance for fee posting on $accountId",
                        invariant = "available_balance >= fee amount"
                    )
                }
            }
            val totalFeeMinor = groupedCharges.values.sum()
            createPostedTransaction(
                transactionType = "FEE_POSTING",
                idempotencyKey = command.idempotencyKey,
                businessReferenceId = command.businessReferenceId,
                businessDate = businessDate,
                requestedBy = command.requestedBy,
                requestedChannel = command.requestedChannel,
                reason = command.reason,
                postings = groupedCharges.map { (accountId, amountMinor) ->
                    LedgerPostingInput(accountId, PostingDirection.DEBIT, amountMinor, command.currency, "FEE")
                } + LedgerPostingInput(BANK_SUSPENSE_ACCOUNT_ID, PostingDirection.CREDIT, totalFeeMinor, command.currency, "FEE")
            )
        }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun disburseLoan(command: DisburseLoanCommand): LedgerCommandResult =
        postLedgerCommand("LOAN_DISBURSEMENT", command.idempotencyKey, command) {
            requirePositiveAmount(command.amountMinor)
            requireNonBlank(command.loanId, "loanId")
            requireNonBlank(command.applicationId, "applicationId")
            requireNonBlank(command.depositAccountId, "depositAccountId")
            requireNonBlank(command.reason, "reason")
            requireApprovedOperation(
                approvalId = command.approvalId,
                businessType = ApprovalBusinessTypes.LOAN_EXECUTION,
                businessReferenceId = command.applicationId,
                requestedBy = command.requestedBy
            )
            val businessDate = command.businessDate ?: LocalDate.now()
            ensureBusinessDateOpen(businessDate)
            ensureBankLoanAssetAccount(command.currency)
            lockActiveAccounts(listOf(BANK_LOAN_ASSET_ACCOUNT_ID, command.depositAccountId))
            createPostedTransaction(
                transactionType = "LOAN_DISBURSEMENT",
                idempotencyKey = command.idempotencyKey,
                businessReferenceId = command.loanId,
                businessDate = businessDate,
                requestedBy = command.requestedBy,
                requestedChannel = command.requestedChannel,
                reason = command.reason,
                postings = listOf(
                    LedgerPostingInput(BANK_LOAN_ASSET_ACCOUNT_ID, PostingDirection.DEBIT, command.amountMinor, command.currency, "LOAN_PRINCIPAL"),
                    LedgerPostingInput(command.depositAccountId, PostingDirection.CREDIT, command.amountMinor, command.currency, "LOAN_PRINCIPAL")
                )
            )
        }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun repayLoan(command: LoanRepaymentCommand): LedgerCommandResult =
        postLedgerCommand(if (command.prepayment) "LOAN_PREPAYMENT" else "LOAN_REPAYMENT", command.idempotencyKey, command) {
            requireNonBlank(command.loanId, "loanId")
            requireNonBlank(command.depositAccountId, "depositAccountId")
            requireNonBlank(command.reason, "reason")
            if (command.principalMinor < 0 || command.interestMinor < 0 || command.principalMinor + command.interestMinor <= 0) {
                throw ledgerValidation("loan repayment principalMinor and interestMinor must form a positive amount")
            }
            val businessDate = command.businessDate ?: LocalDate.now()
            ensureBusinessDateOpen(businessDate)
            ensureBankSuspenseAccount(command.currency)
            ensureBankLoanAssetAccount(command.currency)
            val totalMinor = command.principalMinor + command.interestMinor
            val balances = lockActiveAccounts(listOf(command.depositAccountId, BANK_LOAN_ASSET_ACCOUNT_ID, BANK_SUSPENSE_ACCOUNT_ID))
            val depositBalance = balances.getValue(command.depositAccountId)
            if (depositBalance.availableBalanceMinor < totalMinor) {
                throw ledgerConflict(
                    code = "LEDGER_INSUFFICIENT_AVAILABLE_BALANCE",
                    message = "insufficient available balance for loan repayment on ${command.depositAccountId}",
                    invariant = "available_balance >= loan repayment amount"
                )
            }
            val postings = mutableListOf(
                LedgerPostingInput(command.depositAccountId, PostingDirection.DEBIT, totalMinor, command.currency, "LOAN_REPAYMENT")
            )
            if (command.principalMinor > 0) {
                postings += LedgerPostingInput(BANK_LOAN_ASSET_ACCOUNT_ID, PostingDirection.CREDIT, command.principalMinor, command.currency, "LOAN_PRINCIPAL")
            }
            if (command.interestMinor > 0) {
                postings += LedgerPostingInput(BANK_SUSPENSE_ACCOUNT_ID, PostingDirection.CREDIT, command.interestMinor, command.currency, "LOAN_INTEREST")
            }
            createPostedTransaction(
                transactionType = if (command.prepayment) "LOAN_PREPAYMENT" else "LOAN_REPAYMENT",
                idempotencyKey = command.idempotencyKey,
                businessReferenceId = command.loanId,
                businessDate = businessDate,
                requestedBy = command.requestedBy,
                requestedChannel = command.requestedChannel,
                reason = command.reason,
                postings = postings
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

    @Transactional(readOnly = true)
    fun transaction(transactionId: String): LedgerTransactionDto? =
        findTransaction(transactionId)

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
                "INTEREST_POSTING" -> "InterestPosted"
                "FEE_POSTING" -> "FeePosted"
                "LOAN_DISBURSEMENT" -> "LoanDisbursed"
                "LOAN_REPAYMENT" -> "LoanRepaymentPosted"
                "LOAN_PREPAYMENT" -> "LoanPrepaymentPosted"
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

    private fun ensureBankLoanAssetAccount(currency: String) {
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
            VALUES (:accountId, 'BANK', 'LAB-000-000001', :currency, 'ACTIVE')
            ON CONFLICT (account_id) DO NOTHING
            """.trimIndent(),
            mapOf("accountId" to BANK_LOAN_ASSET_ACCOUNT_ID, "currency" to currency)
        )
        ensureBalanceProjection(BANK_LOAN_ASSET_ACCOUNT_ID)
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

    private fun enforcePostingLimits(accountId: String, channel: String, businessDate: LocalDate, amountMinor: Long) {
        val limits = accountLimitConfig(accountId, channel) ?: return
        val normalizedChannel = normalizeLimitChannel(channel)
        if (amountMinor > limits.singleTransferLimitMinor) {
            throw limitExceeded(
                limitKind = "PER_TRANSACTION",
                channel = normalizedChannel,
                configuredMinor = limits.singleTransferLimitMinor,
                attemptedMinor = amountMinor,
                remainingMinor = limits.singleTransferLimitMinor
            )
        }
        incrementLimitUsage(
            accountId = accountId,
            channel = normalizedChannel,
            businessDate = businessDate,
            periodKind = "DAILY",
            periodStartDate = businessDate,
            amountMinor = amountMinor,
            configuredLimitMinor = limits.dailyTransferLimitMinor
        )
        incrementLimitUsage(
            accountId = accountId,
            channel = normalizedChannel,
            businessDate = businessDate,
            periodKind = "MONTHLY",
            periodStartDate = businessDate.withDayOfMonth(1),
            amountMinor = amountMinor,
            configuredLimitMinor = limits.monthlyTransferLimitMinor
        )
    }

    private fun accountLimitConfig(accountId: String, channel: String): AccountLimitConfig? =
        jdbc.query(
            """
            SELECT daily_transfer_limit_minor, monthly_transfer_limit_minor, single_transfer_limit_minor,
                   customer_web_daily_transfer_limit_minor, customer_web_monthly_transfer_limit_minor,
                   customer_web_single_transfer_limit_minor,
                   staff_terminal_daily_transfer_limit_minor, staff_terminal_monthly_transfer_limit_minor,
                   staff_terminal_single_transfer_limit_minor,
                   atm_daily_withdrawal_limit_minor, atm_monthly_withdrawal_limit_minor,
                   atm_single_withdrawal_limit_minor
            FROM account_limits
            WHERE account_id = :accountId
            FOR UPDATE
            """.trimIndent(),
            mapOf("accountId" to accountId)
        ) { rs, _ ->
            val normalized = normalizeLimitChannel(channel)
            val defaultDaily = rs.getLong("daily_transfer_limit_minor")
            val defaultMonthly = rs.getLong("monthly_transfer_limit_minor")
            val defaultSingle = rs.getLong("single_transfer_limit_minor")
            when (normalized) {
                "CUSTOMER_WEB" -> AccountLimitConfig(
                    dailyTransferLimitMinor = nullableLong(rs, "customer_web_daily_transfer_limit_minor") ?: defaultDaily,
                    monthlyTransferLimitMinor = nullableLong(rs, "customer_web_monthly_transfer_limit_minor") ?: defaultMonthly,
                    singleTransferLimitMinor = nullableLong(rs, "customer_web_single_transfer_limit_minor") ?: defaultSingle
                )
                "STAFF_TERMINAL" -> AccountLimitConfig(
                    dailyTransferLimitMinor = nullableLong(rs, "staff_terminal_daily_transfer_limit_minor") ?: defaultDaily,
                    monthlyTransferLimitMinor = nullableLong(rs, "staff_terminal_monthly_transfer_limit_minor") ?: defaultMonthly,
                    singleTransferLimitMinor = nullableLong(rs, "staff_terminal_single_transfer_limit_minor") ?: defaultSingle
                )
                "ATM" -> AccountLimitConfig(
                    dailyTransferLimitMinor = nullableLong(rs, "atm_daily_withdrawal_limit_minor") ?: defaultDaily,
                    monthlyTransferLimitMinor = nullableLong(rs, "atm_monthly_withdrawal_limit_minor") ?: defaultMonthly,
                    singleTransferLimitMinor = nullableLong(rs, "atm_single_withdrawal_limit_minor") ?: defaultSingle
                )
                else -> AccountLimitConfig(defaultDaily, defaultMonthly, defaultSingle)
            }
        }.firstOrNull()

    private fun incrementLimitUsage(
        accountId: String,
        channel: String,
        businessDate: LocalDate,
        periodKind: String,
        periodStartDate: LocalDate,
        amountMinor: Long,
        configuredLimitMinor: Long
    ) {
        jdbc.update(
            """
            INSERT INTO limit_usage_counters (
              account_id, channel, business_date, period_kind, used_amount_minor
            )
            VALUES (:accountId, :channel, :periodStartDate, :periodKind, 0)
            ON CONFLICT (account_id, channel, period_kind, business_date) DO NOTHING
            """.trimIndent(),
            mapOf(
                "accountId" to accountId,
                "channel" to channel,
                "periodStartDate" to periodStartDate,
                "periodKind" to periodKind
            )
        )
        val usedAmountMinor = jdbc.queryForObject(
            """
            SELECT used_amount_minor
            FROM limit_usage_counters
            WHERE account_id = :accountId
              AND channel = :channel
              AND period_kind = :periodKind
              AND business_date = :periodStartDate
            FOR UPDATE
            """.trimIndent(),
            mapOf(
                "accountId" to accountId,
                "channel" to channel,
                "periodKind" to periodKind,
                "periodStartDate" to periodStartDate
            ),
            Long::class.java
        ) ?: 0L
        val remainingMinor = (configuredLimitMinor - usedAmountMinor).coerceAtLeast(0L)
        if (amountMinor > remainingMinor) {
            throw limitExceeded(
                limitKind = periodKind,
                channel = channel,
                configuredMinor = configuredLimitMinor,
                attemptedMinor = amountMinor,
                remainingMinor = remainingMinor,
                businessDate = businessDate
            )
        }
        jdbc.update(
            """
            UPDATE limit_usage_counters
            SET used_amount_minor = used_amount_minor + :amountMinor,
                version = version + 1,
                updated_at = now()
            WHERE account_id = :accountId
              AND channel = :channel
              AND period_kind = :periodKind
              AND business_date = :periodStartDate
            """.trimIndent(),
            mapOf(
                "accountId" to accountId,
                "channel" to channel,
                "periodKind" to periodKind,
                "periodStartDate" to periodStartDate,
                "amountMinor" to amountMinor
            )
        )
    }

    private fun releaseLimitUsageForReversal(original: LedgerTransactionDto) {
        if (original.transactionType !in setOf("WITHDRAWAL", "INTERNAL_TRANSFER")) {
            return
        }
        val channel = normalizeLimitChannel(original.requestedChannel)
        original.postings
            .filter { it.direction == PostingDirection.DEBIT && it.accountId != BANK_SUSPENSE_ACCOUNT_ID }
            .forEach { posting ->
                releaseLimitUsage(posting.accountId, channel, "DAILY", original.businessDate, posting.amountMinor)
                releaseLimitUsage(posting.accountId, channel, "MONTHLY", original.businessDate.withDayOfMonth(1), posting.amountMinor)
            }
    }

    private fun releaseLimitUsage(
        accountId: String,
        channel: String,
        periodKind: String,
        periodStartDate: LocalDate,
        amountMinor: Long
    ) {
        jdbc.update(
            """
            UPDATE limit_usage_counters
            SET used_amount_minor = GREATEST(0, used_amount_minor - :amountMinor),
                version = version + 1,
                updated_at = now()
            WHERE account_id = :accountId
              AND channel = :channel
              AND period_kind = :periodKind
              AND business_date = :periodStartDate
            """.trimIndent(),
            mapOf(
                "accountId" to accountId,
                "channel" to channel,
                "periodKind" to periodKind,
                "periodStartDate" to periodStartDate,
                "amountMinor" to amountMinor
            )
        )
    }

    private fun normalizeLimitChannel(channel: String): String {
        val normalized = channel.trim().uppercase()
        return when {
            normalized.contains("CUSTOMER") || normalized.contains("WEB") -> "CUSTOMER_WEB"
            normalized.contains("STAFF") || normalized.contains("BRANCH") -> "STAFF_TERMINAL"
            normalized.contains("ATM") -> "ATM"
            normalized.isBlank() -> "CORE_BANKING"
            else -> normalized
        }
    }

    private fun nullableLong(rs: ResultSet, column: String): Long? {
        val value = rs.getLong(column)
        return if (rs.wasNull()) null else value
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

    private fun requireApprovedOperation(
        approvalId: String?,
        businessType: String,
        businessReferenceId: String?,
        requestedBy: String
    ) {
        if (approvalId.isNullOrBlank()) {
            throw BankingLabDomainException(
                code = "REQUEST_VALIDATION_FAILED",
                status = HttpStatus.BAD_REQUEST,
                domain = "validation",
                policy = "MAKER_CHECKER_APPROVAL_REQUIRED",
                message = "approvalId is required for high-risk operation",
                causeText = "A high-risk banking command was submitted without a maker-checker approval reference.",
                fix = "Submit the operation for approval and retry with the approved approvalId."
            )
        }
        val approval = jdbc.query(
            """
            SELECT approval_id, business_type, business_reference_id, requested_by, status, approved_by
            FROM operator_approvals
            WHERE approval_id = :approvalId
            FOR UPDATE
            """.trimIndent(),
            mapOf("approvalId" to approvalId)
        ) { rs, _ ->
            ApprovalRecord(
                approvalId = rs.getString("approval_id"),
                businessType = rs.getString("business_type"),
                businessReferenceId = rs.getString("business_reference_id"),
                requestedBy = rs.getString("requested_by"),
                status = rs.getString("status"),
                approvedBy = rs.getString("approved_by")
            )
        }.firstOrNull() ?: throw BankingLabDomainException(
            code = "RESOURCE_NOT_FOUND",
            status = HttpStatus.NOT_FOUND,
            domain = "resource",
            policy = "MAKER_CHECKER_APPROVAL_REQUIRED",
            message = "approval not found: $approvalId",
            causeText = "The submitted approvalId does not exist in PostgreSQL approval state.",
            fix = "Use an existing synthetic approvalId created through the approval workflow."
        )
        if (approval.businessType != businessType || approval.businessReferenceId != businessReferenceId) {
            throw BankingLabDomainException(
                code = "WORKFLOW_STATE_VIOLATION",
                status = HttpStatus.CONFLICT,
                domain = "workflow",
                policy = "APPROVAL_TARGET_MATCH_REQUIRED",
                message = "approval does not match high-risk operation target",
                causeText = "The approved business type or reference does not match the submitted command.",
                fix = "Use an approval created for this exact business type and business reference."
            )
        }
        if (approval.status != "APPROVED") {
            throw BankingLabDomainException(
                code = "WORKFLOW_STATE_VIOLATION",
                status = HttpStatus.CONFLICT,
                domain = "workflow",
                policy = "APPROVAL_MUST_BE_APPROVED",
                message = "approval is not approved: ${approval.status}",
                causeText = "The high-risk operation was attempted before maker-checker approval completed.",
                fix = "Have an authorized checker approve the operation before execution."
            )
        }
        if (approval.requestedBy == approval.approvedBy || approval.approvedBy.isNullOrBlank()) {
            throw BankingLabDomainException(
                code = "MAKER_CHECKER_SELF_APPROVAL_REJECTED",
                status = HttpStatus.CONFLICT,
                domain = "maker-checker",
                policy = "MAKER_CHECKER_SEPARATION_OF_DUTIES",
                message = "maker and checker must be different users",
                causeText = "The approval record does not prove separation of duties.",
                fix = "Approve with a different checker actor who has the required approval role."
            )
        }
        if (approval.requestedBy != requestedBy) {
            throw BankingLabDomainException(
                code = "AUTHORIZATION_POLICY_VIOLATION",
                status = HttpStatus.FORBIDDEN,
                domain = "auth",
                policy = "APPROVAL_REQUESTER_MUST_EXECUTE",
                message = "approval requester must execute the high-risk operation",
                causeText = "The command actor does not match the maker recorded on the approval.",
                fix = "Retry as the original maker or request a new approval for this actor."
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

    private fun appendAuditEvent(
        eventType: String,
        actorId: String,
        actorRole: String,
        screenId: String,
        businessReferenceId: String,
        accountId: String?,
        reason: String?,
        payload: Map<String, Any?>
    ) {
        auditEvents.append(
            eventType = eventType,
            actorType = "STAFF",
            actorId = actorId,
            actorRole = actorRole,
            screenId = screenId,
            businessReferenceId = businessReferenceId,
            accountId = accountId,
            reason = reason,
            payload = payload
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
            "INTEREST_POSTING" -> "TX-INT"
            "FEE_POSTING" -> "TX-FEE"
            "LOAN_DISBURSEMENT" -> "TX-LOAN-DISB"
            "LOAN_REPAYMENT" -> "TX-LOAN-REPAY"
            "LOAN_PREPAYMENT" -> "TX-LOAN-PREPAY"
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

    private fun requireNonBlank(value: String?, field: String) {
        if (value.isNullOrBlank()) {
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

    private fun limitExceeded(
        limitKind: String,
        channel: String,
        configuredMinor: Long,
        attemptedMinor: Long,
        remainingMinor: Long,
        businessDate: LocalDate? = null
    ): BankingLabDomainException =
        BankingLabDomainException(
            code = "LIMIT_EXCEEDED",
            status = HttpStatus.CONFLICT,
            domain = "ledger",
            invariant = "used_amount(account, channel, period) <= configured limit",
            message = "transfer limit exceeded for $channel $limitKind",
            causeText = "The command would exceed the configured synthetic account transfer limit before ledger posting.",
            fix = "Reduce the amount, wait for the next period, reverse a counted transaction, or submit an approved limit change.",
            details = mapOf(
                "limitKind" to limitKind,
                "channel" to channel,
                "configuredMinor" to configuredMinor,
                "attemptedMinor" to attemptedMinor,
                "remainingMinor" to remainingMinor,
                "businessDate" to businessDate?.toString(),
                "syntheticOnly" to true
            )
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

    private data class ApprovalRecord(
        val approvalId: String,
        val businessType: String,
        val businessReferenceId: String,
        val requestedBy: String,
        val status: String,
        val approvedBy: String?
    )

    private data class AccountLimitConfig(
        val dailyTransferLimitMinor: Long,
        val monthlyTransferLimitMinor: Long,
        val singleTransferLimitMinor: Long
    )
}
