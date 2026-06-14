package lab.banking.core.statement

import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import lab.banking.core.audit.AuditEventAppender
import lab.banking.core.ledger.domain.PostingDirection
import lab.banking.core.security.BankingLabAuthContext
import lab.banking.core.security.BankingLabPrincipal
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class StatementService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val auditEvents: AuditEventAppender
) {
    @Transactional
    fun customerStatement(customerId: String, from: LocalDate, to: LocalDate, reason: String?): CustomerStatementDto =
        buildStatement(
            customerId = customerId,
            accountId = null,
            from = from,
            to = to,
            reason = reason,
            statementScope = "CONSOLIDATED",
            screenId = "CWB-103"
        )

    @Transactional
    fun customerConsolidatedStatement(from: LocalDate, to: LocalDate): CustomerStatementDto {
        val customerId = currentCustomerId()
        return buildStatement(
            customerId = customerId,
            accountId = null,
            from = from,
            to = to,
            reason = null,
            statementScope = "CONSOLIDATED",
            screenId = "CWB-106"
        )
    }

    @Transactional
    fun customerAccountStatement(accountId: String, from: LocalDate, to: LocalDate): CustomerStatementDto {
        val customerId = currentCustomerId()
        val account = accountRow(accountId)
        if (account.customerId != customerId) {
            throw WorkflowErrors.authorizationViolation("customer token cannot access another customer's account statement")
        }
        return buildStatement(
            customerId = customerId,
            accountId = accountId,
            from = from,
            to = to,
            reason = null,
            statementScope = "ACCOUNT",
            screenId = "CWB-105"
        )
    }

    private fun buildStatement(
        customerId: String,
        accountId: String?,
        from: LocalDate,
        to: LocalDate,
        reason: String?,
        statementScope: String,
        screenId: String
    ): CustomerStatementDto {
        requireDateRange(from, to)
        val actor = authorizeCustomerOrStaffForCustomer(customerId, reason)
        val lines = jdbc.query(
            """
            SELECT lt.ledger_transaction_id, lt.transaction_type, lt.business_date, lt.posted_at,
                   lp.account_id, lp.direction, lp.amount_minor, lp.currency, lp.posting_type,
                   lt.requested_channel, lt.reason
            FROM ledger_transactions lt
            JOIN ledger_postings lp
              ON lp.ledger_transaction_id = lt.ledger_transaction_id
            JOIN accounts a
              ON a.account_id = lp.account_id
            WHERE a.customer_id = :customerId
              AND (CAST(:accountId AS text) IS NULL OR lp.account_id = :accountId)
              AND lt.business_date >= :from
              AND lt.business_date <= :to
            ORDER BY lt.business_date, lt.posted_at NULLS LAST, lt.ledger_transaction_id, lp.ledger_posting_id
            """.trimIndent(),
            mapOf("customerId" to customerId, "accountId" to accountId, "from" to from, "to" to to),
            this::mapStatementLine
        )
        val currency = lines.firstOrNull()?.currency ?: primaryCurrency(customerId)
        val openingBalance = if (accountId == null) {
            customerSignedPostingSum(customerId, toExclusive = from)
        } else {
            accountSignedPostingSum(accountId, toExclusive = from)
        }
        val closingBalance = if (accountId == null) {
            customerSignedPostingSum(customerId, toInclusive = to)
        } else {
            accountSignedPostingSum(accountId, toInclusive = to)
        }
        val debitTotal = lines.filter { it.direction == PostingDirection.DEBIT }.sumOf { it.amountMinor }
        val creditTotal = lines.filter { it.direction == PostingDirection.CREDIT }.sumOf { it.amountMinor }
        val netAmount = lines.sumOf { it.signedAmountMinor }
        val sourceLedgerHash = statementSourceLedgerHash(
            customerId = customerId,
            accountId = accountId,
            from = from,
            to = to,
            openingBalanceMinor = openingBalance,
            closingBalanceMinor = closingBalance,
            lines = lines
        )
        val payloadHash = statementPayloadHash(
            customerId = customerId,
            accountId = accountId,
            from = from,
            to = to,
            statementScope = statementScope,
            currency = currency,
            openingBalanceMinor = openingBalance,
            closingBalanceMinor = closingBalance,
            debitTotalMinor = debitTotal,
            creditTotalMinor = creditTotal,
            netAmountMinor = netAmount,
            lineCount = lines.size,
            sourceLedgerHash = sourceLedgerHash
        )
        val statementId = deterministicId(
            "STMT",
            "$customerId:${accountId ?: "ALL"}:$from:$to:$statementScope:$sourceLedgerHash:$payloadHash"
        )
        val statement = CustomerStatementDto(
            statementId = statementId,
            statementScope = statementScope,
            accountId = accountId,
            customerId = customerId,
            from = from,
            to = to,
            currency = currency,
            openingBalanceMinor = openingBalance,
            closingBalanceMinor = closingBalance,
            debitTotalMinor = debitTotal,
            creditTotalMinor = creditTotal,
            netAmountMinor = netAmount,
            lineCount = lines.size,
            lines = lines,
            sourceLedgerHash = sourceLedgerHash,
            payloadHash = payloadHash
        )
        appendReadAudit(
            actor = actor,
            eventType = statementViewEventType(screenId),
            screenId = screenId,
            businessReferenceId = statementId,
            customerId = customerId,
            accountId = accountId,
            reason = reason,
            payload = mapOf(
                "statementId" to statementId,
                "statementScope" to statementScope,
                "from" to from.toString(),
                "to" to to.toString(),
                "lineCount" to statement.lineCount,
                "sourceLedgerHash" to sourceLedgerHash,
                "payloadHash" to payloadHash,
                "maskingPolicy" to statement.maskingPolicy,
                "syntheticOnly" to true
            )
        )
        val snapshot = persistStatementArtifactSnapshot(statement)
        return statement.copy(generatedAt = snapshot.createdAt)
    }

    private fun statementViewEventType(screenId: String): String = when (screenId) {
        "CWB-105" -> "CUSTOMER_ACCOUNT_STATEMENT_VIEW"
        "CWB-106" -> "CUSTOMER_CONSOLIDATED_STATEMENT_VIEW"
        else -> "STATEMENT_VIEW"
    }

    @Transactional
    fun transactionConfirmation(transactionId: String, reason: String?): TransactionConfirmationDto {
        if (transactionId.isBlank()) {
            throw WorkflowErrors.validation("transactionId is required")
        }
        val rows = transactionRows(transactionId)
        if (rows.isEmpty()) {
            throw WorkflowErrors.notFound("ledger transaction not found: $transactionId")
        }
        val actor = authorizeCustomerOrStaffForTransaction(rows, reason)
        val first = rows.first()
        val postings = rows.map {
            TransactionConfirmationPostingDto(
                accountId = it.accountId,
                customerId = it.customerId,
                direction = it.direction,
                amountMinor = it.amountMinor,
                signedAmountMinor = signedAmount(it.direction, it.amountMinor),
                currency = it.currency,
                postingType = it.postingType
            )
        }
        val totalDebit = postings.filter { it.direction == PostingDirection.DEBIT }.sumOf { it.amountMinor }
        val totalCredit = postings.filter { it.direction == PostingDirection.CREDIT }.sumOf { it.amountMinor }
        val confirmation = TransactionConfirmationDto(
            confirmationId = deterministicId("TXCONF", "$transactionId:${first.businessDate}:$totalDebit:$totalCredit"),
            transactionId = transactionId,
            transactionType = first.transactionType,
            businessReferenceId = first.businessReferenceId,
            businessDate = first.businessDate,
            status = first.status,
            requestedBy = first.requestedBy,
            requestedChannel = first.requestedChannel,
            postedAt = first.postedAt,
            originalTransactionId = first.originalTransactionId,
            currency = postings.firstOrNull()?.currency ?: "KRW",
            totalDebitMinor = totalDebit,
            totalCreditMinor = totalCredit,
            balanced = totalDebit == totalCredit,
            postings = postings
        )
        appendReadAudit(
            actor = actor,
            eventType = "TRANSACTION_CONFIRMATION_VIEW",
            screenId = "LED-102",
            businessReferenceId = transactionId,
            customerId = actor.customerIdForAudit,
            accountId = null,
            reason = reason,
            payload = mapOf(
                "transactionId" to transactionId,
                "balanced" to confirmation.balanced,
                "syntheticOnly" to true
            )
        )
        return confirmation
    }

    @Transactional
    fun balanceCertificate(accountId: String, date: LocalDate, reason: String?): BalanceCertificateDto {
        if (accountId.isBlank()) {
            throw WorkflowErrors.validation("accountId is required")
        }
        val account = accountRow(accountId)
        val actor = authorizeCustomerOrStaffForCustomer(account.customerId, reason)
        val balanceAsOf = accountSignedPostingSum(accountId, date)
        val source = balanceCertificateSource(accountId, date)
        val hash = sha256("${account.accountId}:${account.customerId}:$date:$balanceAsOf:${account.currency}:${source.ledgerHash}")
        val certificateId = "BALCERT-${hash.take(16).uppercase()}"
        val auditEventId = appendReadAudit(
            actor = actor,
            eventType = "BALANCE_CERTIFICATE_VIEW",
            screenId = "ACC-102",
            businessReferenceId = certificateId,
            customerId = account.customerId,
            accountId = account.accountId,
            reason = reason,
            payload = mapOf(
                "date" to date.toString(),
                "certificateId" to certificateId,
                "sourcePostingCount" to source.postingCount,
                "sourceLedgerHash" to source.ledgerHash,
                "syntheticOnly" to true
            )
        )
        val snapshot = persistBalanceCertificateSnapshot(
            certificateId = certificateId,
            accountId = account.accountId,
            customerId = account.customerId,
            currency = account.currency,
            date = date,
            balanceAsOfMinor = balanceAsOf,
            currentLedgerBalanceMinor = account.currentLedgerBalanceMinor,
            currentAvailableBalanceMinor = account.currentAvailableBalanceMinor,
            deterministicInputHash = hash,
            source = source,
            auditEventId = auditEventId
        )
        return snapshot.toDto()
    }

    @Transactional
    fun accessHistory(customerId: String, reason: String?): CustomerAccessHistoryDto {
        val actor = authorizeCustomerOrStaffForCustomer(customerId, reason)
        val items = jdbc.query(
            """
            SELECT audit_event_id, event_type, actor_type, actor_id, actor_role, screen_id,
                   business_reference_id, account_id, reason, created_at
            FROM audit_events
            WHERE customer_id = :customerId
            ORDER BY created_at DESC, audit_event_id DESC
            LIMIT 50
            """.trimIndent(),
            mapOf("customerId" to customerId),
            this::mapAccessHistory
        )
        appendReadAudit(
            actor = actor,
            eventType = "ACCESS_HISTORY_VIEW",
            screenId = "CWB-401",
            businessReferenceId = customerId,
            customerId = customerId,
            accountId = null,
            reason = reason,
            payload = mapOf("resultCount" to items.size, "syntheticOnly" to true)
        )
        return CustomerAccessHistoryDto(customerId = customerId, items = items)
    }

    private fun authorizeCustomerOrStaffForCustomer(customerId: String, reason: String?): ReadActor {
        requireCustomerExists(customerId)
        val principal = BankingLabAuthContext.get() ?: return ReadActor("SYSTEM", "SYSTEM", "SYSTEM", null)
        if (principal.roles.contains("CUSTOMER")) {
            BankingLabAuthContext.requireCustomerOwnership(customerId)
            return ReadActor("CUSTOMER", principal.subject, "CUSTOMER", customerId)
        }
        requireStaff(principal, reason)
        return ReadActor("STAFF", principal.subject, principal.roles.sorted().joinToString(","), customerId)
    }

    private fun authorizeCustomerOrStaffForTransaction(rows: List<TransactionRow>, reason: String?): ReadActor {
        val principal = BankingLabAuthContext.get() ?: return ReadActor("SYSTEM", "SYSTEM", "SYSTEM", null)
        if (principal.roles.contains("CUSTOMER")) {
            val customerId = principal.customerId ?: throw WorkflowErrors.authorizationViolation("customer token is missing customerId")
            if (rows.none { it.customerId == customerId }) {
                throw WorkflowErrors.authorizationViolation("customer token cannot access another customer's transaction")
            }
            return ReadActor("CUSTOMER", principal.subject, "CUSTOMER", customerId)
        }
        requireStaff(principal, reason)
        return ReadActor("STAFF", principal.subject, principal.roles.sorted().joinToString(","), rows.firstOrNull { it.customerId != "BANK" }?.customerId)
    }

    private fun requireStaff(principal: BankingLabPrincipal, reason: String?) {
        if (!principal.hasAnyRole(STAFF_READ_ROLES)) {
            throw WorkflowErrors.authorizationViolation("actor role cannot read statement artifacts")
        }
        if (reason.isNullOrBlank()) {
            throw WorkflowErrors.reasonRequired("staff statement access requires a business reason")
        }
    }

    private fun requireDateRange(from: LocalDate, to: LocalDate) {
        if (to.isBefore(from)) {
            throw WorkflowErrors.validation("to must be on or after from")
        }
    }

    private fun mapStatementLine(rs: ResultSet, rowNum: Int): StatementLineDto {
        val direction = PostingDirection.valueOf(rs.getString("direction"))
        val amount = rs.getLong("amount_minor")
        return StatementLineDto(
            transactionId = rs.getString("ledger_transaction_id"),
            transactionType = rs.getString("transaction_type"),
            businessDate = rs.getObject("business_date", LocalDate::class.java),
            postedAt = rs.getObject("posted_at", OffsetDateTime::class.java),
            accountId = rs.getString("account_id"),
            direction = direction,
            amountMinor = amount,
            signedAmountMinor = signedAmount(direction, amount),
            currency = rs.getString("currency").trim(),
            postingType = rs.getString("posting_type"),
            requestedChannel = rs.getString("requested_channel"),
            reason = rs.getString("reason")
        )
    }

    private fun transactionRows(transactionId: String): List<TransactionRow> =
        jdbc.query(
            """
            SELECT lt.ledger_transaction_id, lt.transaction_type, lt.business_reference_id,
                   lt.business_date, lt.status, lt.requested_by, lt.requested_channel,
                   lt.posted_at, lt.original_transaction_id,
                   a.customer_id, lp.account_id, lp.direction, lp.amount_minor, lp.currency, lp.posting_type
            FROM ledger_transactions lt
            JOIN ledger_postings lp
              ON lp.ledger_transaction_id = lt.ledger_transaction_id
            JOIN accounts a
              ON a.account_id = lp.account_id
            WHERE lt.ledger_transaction_id = :transactionId
            ORDER BY lp.ledger_posting_id
            """.trimIndent(),
            mapOf("transactionId" to transactionId),
            this::mapTransactionRow
        )

    private fun mapTransactionRow(rs: ResultSet, rowNum: Int): TransactionRow =
        TransactionRow(
            transactionId = rs.getString("ledger_transaction_id"),
            transactionType = rs.getString("transaction_type"),
            businessReferenceId = rs.getString("business_reference_id"),
            businessDate = rs.getObject("business_date", LocalDate::class.java),
            status = rs.getString("status"),
            requestedBy = rs.getString("requested_by"),
            requestedChannel = rs.getString("requested_channel"),
            postedAt = rs.getObject("posted_at", OffsetDateTime::class.java),
            originalTransactionId = rs.getString("original_transaction_id"),
            customerId = rs.getString("customer_id"),
            accountId = rs.getString("account_id"),
            direction = PostingDirection.valueOf(rs.getString("direction")),
            amountMinor = rs.getLong("amount_minor"),
            currency = rs.getString("currency").trim(),
            postingType = rs.getString("posting_type")
        )

    private fun accountRow(accountId: String): AccountRow =
        jdbc.queryForObject(
            """
            SELECT a.account_id, a.customer_id, a.currency,
                   COALESCE(p.ledger_balance_minor, 0) AS ledger_balance_minor,
                   COALESCE(p.available_balance_minor, 0) AS available_balance_minor
            FROM accounts a
            LEFT JOIN account_balance_projections p
              ON p.account_id = a.account_id
             AND p.currency = a.currency
            WHERE a.account_id = :accountId
            """.trimIndent(),
            mapOf("accountId" to accountId),
            this::mapAccountRow
        ) ?: throw WorkflowErrors.notFound("account not found: $accountId")

    private fun mapAccountRow(rs: ResultSet, rowNum: Int): AccountRow =
        AccountRow(
            accountId = rs.getString("account_id"),
            customerId = rs.getString("customer_id"),
            currency = rs.getString("currency").trim(),
            currentLedgerBalanceMinor = rs.getLong("ledger_balance_minor"),
            currentAvailableBalanceMinor = rs.getLong("available_balance_minor")
        )

    private fun mapAccessHistory(rs: ResultSet, rowNum: Int): CustomerAccessHistoryItemDto =
        CustomerAccessHistoryItemDto(
            auditEventId = rs.getString("audit_event_id"),
            eventType = rs.getString("event_type"),
            actorType = rs.getString("actor_type"),
            actorId = rs.getString("actor_id"),
            actorRole = rs.getString("actor_role"),
            screenId = rs.getString("screen_id"),
            businessReferenceId = rs.getString("business_reference_id"),
            accountId = rs.getString("account_id"),
            reasonPresent = !rs.getString("reason").isNullOrBlank(),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
        )

    private fun customerSignedPostingSum(customerId: String, toExclusive: LocalDate? = null, toInclusive: LocalDate? = null): Long =
        jdbc.queryForObject(
            """
            SELECT COALESCE(SUM(CASE WHEN lp.direction = 'DEBIT' THEN -lp.amount_minor ELSE lp.amount_minor END), 0)
            FROM ledger_postings lp
            JOIN ledger_transactions lt
              ON lt.ledger_transaction_id = lp.ledger_transaction_id
            JOIN accounts a
              ON a.account_id = lp.account_id
            WHERE a.customer_id = :customerId
              AND (CAST(:toExclusive AS date) IS NULL OR lt.business_date < :toExclusive)
              AND (CAST(:toInclusive AS date) IS NULL OR lt.business_date <= :toInclusive)
            """.trimIndent(),
            mapOf("customerId" to customerId, "toExclusive" to toExclusive, "toInclusive" to toInclusive),
            Long::class.java
        ) ?: 0L

    private fun accountSignedPostingSum(accountId: String, date: LocalDate): Long =
        jdbc.queryForObject(
            """
            SELECT COALESCE(SUM(CASE WHEN lp.direction = 'DEBIT' THEN -lp.amount_minor ELSE lp.amount_minor END), 0)
            FROM ledger_postings lp
            JOIN ledger_transactions lt
              ON lt.ledger_transaction_id = lp.ledger_transaction_id
            WHERE lp.account_id = :accountId
              AND lt.business_date <= :date
            """.trimIndent(),
            mapOf("accountId" to accountId, "date" to date),
            Long::class.java
        ) ?: 0L

    private fun accountSignedPostingSum(
        accountId: String,
        toExclusive: LocalDate? = null,
        toInclusive: LocalDate? = null
    ): Long =
        jdbc.queryForObject(
            """
            SELECT COALESCE(SUM(CASE WHEN lp.direction = 'DEBIT' THEN -lp.amount_minor ELSE lp.amount_minor END), 0)
            FROM ledger_postings lp
            JOIN ledger_transactions lt
              ON lt.ledger_transaction_id = lp.ledger_transaction_id
            WHERE lp.account_id = :accountId
              AND (CAST(:toExclusive AS date) IS NULL OR lt.business_date < :toExclusive)
              AND (CAST(:toInclusive AS date) IS NULL OR lt.business_date <= :toInclusive)
            """.trimIndent(),
            mapOf("accountId" to accountId, "toExclusive" to toExclusive, "toInclusive" to toInclusive),
            Long::class.java
        ) ?: 0L

    private fun balanceCertificateSource(accountId: String, date: LocalDate): BalanceCertificateSource {
        val rows = jdbc.query(
            """
            SELECT lp.ledger_posting_id,
                   lp.ledger_transaction_id,
                   lt.business_date,
                   lp.direction,
                   lp.amount_minor,
                   lp.currency,
                   lp.posting_type
            FROM ledger_postings lp
            JOIN ledger_transactions lt
              ON lt.ledger_transaction_id = lp.ledger_transaction_id
            WHERE lp.account_id = :accountId
              AND lt.business_date <= :date
            ORDER BY lt.business_date, lp.ledger_transaction_id, lp.ledger_posting_id
            """.trimIndent(),
            mapOf("accountId" to accountId, "date" to date)
        ) { rs, _ ->
            CertificateSourcePosting(
                ledgerPostingId = rs.getString("ledger_posting_id"),
                ledgerTransactionId = rs.getString("ledger_transaction_id"),
                businessDate = rs.getObject("business_date", LocalDate::class.java),
                direction = rs.getString("direction"),
                amountMinor = rs.getLong("amount_minor"),
                currency = rs.getString("currency").trim(),
                postingType = rs.getString("posting_type")
            )
        }
        val fingerprint = rows.joinToString("\n") {
            listOf(
                it.ledgerPostingId,
                it.ledgerTransactionId,
                it.businessDate,
                it.direction,
                it.amountMinor,
                it.currency,
                it.postingType
            ).joinToString("|")
        }.ifEmpty { "no-postings:$accountId:$date" }
        return BalanceCertificateSource(
            postingCount = rows.size,
            lastBusinessDate = rows.maxOfOrNull { it.businessDate },
            ledgerHash = sha256(fingerprint)
        )
    }

    private fun persistBalanceCertificateSnapshot(
        certificateId: String,
        accountId: String,
        customerId: String,
        currency: String,
        date: LocalDate,
        balanceAsOfMinor: Long,
        currentLedgerBalanceMinor: Long,
        currentAvailableBalanceMinor: Long,
        deterministicInputHash: String,
        source: BalanceCertificateSource,
        auditEventId: String
    ): BalanceCertificateSnapshot =
        jdbc.queryForObject(
            """
            INSERT INTO balance_certificate_snapshots (
              certificate_id,
              account_id,
              customer_id,
              currency,
              as_of_date,
              balance_as_of_minor,
              current_ledger_balance_minor,
              current_available_balance_minor,
              deterministic_input_hash,
              source_posting_count,
              source_last_business_date,
              source_ledger_hash,
              first_audit_event_id,
              last_audit_event_id,
              synthetic_only
            )
            VALUES (
              :certificateId,
              :accountId,
              :customerId,
              :currency,
              :asOfDate,
              :balanceAsOfMinor,
              :currentLedgerBalanceMinor,
              :currentAvailableBalanceMinor,
              :deterministicInputHash,
              :sourcePostingCount,
              :sourceLastBusinessDate,
              :sourceLedgerHash,
              :auditEventId,
              :auditEventId,
              true
            )
            ON CONFLICT (certificate_id) DO UPDATE
            SET last_viewed_at = now(),
                last_audit_event_id = EXCLUDED.last_audit_event_id
            RETURNING certificate_id,
                      account_id,
                      customer_id,
                      currency,
                      as_of_date,
                      balance_as_of_minor,
                      current_ledger_balance_minor,
                      current_available_balance_minor,
                      deterministic_input_hash,
                      source_posting_count,
                      source_last_business_date,
                      source_ledger_hash,
                      synthetic_only,
                      created_at,
                      last_viewed_at
            """.trimIndent(),
            mapOf(
                "certificateId" to certificateId,
                "accountId" to accountId,
                "customerId" to customerId,
                "currency" to currency,
                "asOfDate" to date,
                "balanceAsOfMinor" to balanceAsOfMinor,
                "currentLedgerBalanceMinor" to currentLedgerBalanceMinor,
                "currentAvailableBalanceMinor" to currentAvailableBalanceMinor,
                "deterministicInputHash" to deterministicInputHash,
                "sourcePostingCount" to source.postingCount,
                "sourceLastBusinessDate" to source.lastBusinessDate,
                "sourceLedgerHash" to source.ledgerHash,
                "auditEventId" to auditEventId
            )
        ) { rs, _ ->
            BalanceCertificateSnapshot(
                certificateId = rs.getString("certificate_id"),
                accountId = rs.getString("account_id"),
                customerId = rs.getString("customer_id"),
                currency = rs.getString("currency").trim(),
                date = rs.getObject("as_of_date", LocalDate::class.java),
                balanceAsOfMinor = rs.getLong("balance_as_of_minor"),
                currentLedgerBalanceMinor = rs.getLong("current_ledger_balance_minor"),
                currentAvailableBalanceMinor = rs.getLong("current_available_balance_minor"),
                deterministicInputHash = rs.getString("deterministic_input_hash"),
                sourcePostingCount = rs.getInt("source_posting_count"),
                sourceLastBusinessDate = rs.getObject("source_last_business_date", LocalDate::class.java),
                sourceLedgerHash = rs.getString("source_ledger_hash"),
                syntheticOnly = rs.getBoolean("synthetic_only"),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
                lastViewedAt = rs.getObject("last_viewed_at", OffsetDateTime::class.java)
            )
        } ?: error("balance certificate snapshot was not returned")

    private fun primaryCurrency(customerId: String): String =
        jdbc.queryForObject(
            "SELECT currency FROM accounts WHERE customer_id = :customerId ORDER BY account_id LIMIT 1",
            mapOf("customerId" to customerId),
            String::class.java
        )?.trim() ?: "KRW"

    private fun requireCustomerExists(customerId: String) {
        val count = jdbc.queryForObject(
            "SELECT count(*) FROM customers WHERE customer_id = :customerId",
            mapOf("customerId" to customerId),
            Int::class.java
        ) ?: 0
        if (count == 0) {
            throw WorkflowErrors.notFound("customer not found: $customerId")
        }
    }

    private fun currentCustomerId(): String {
        val principal = BankingLabAuthContext.get()
            ?: throw WorkflowErrors.authorizationViolation("customer token is required")
        if (!principal.roles.contains("CUSTOMER")) {
            throw WorkflowErrors.authorizationViolation("CUSTOMER role is required")
        }
        return principal.customerId?.takeIf { it.isNotBlank() }
            ?: throw WorkflowErrors.authorizationViolation("customer token is missing customerId")
    }

    private fun statementSourceLedgerHash(
        customerId: String,
        accountId: String?,
        from: LocalDate,
        to: LocalDate,
        openingBalanceMinor: Long,
        closingBalanceMinor: Long,
        lines: List<StatementLineDto>
    ): String {
        val fingerprint = lines.joinToString("\n") {
            listOf(
                it.transactionId,
                it.transactionType,
                it.businessDate,
                it.postedAt,
                it.accountId,
                it.direction,
                it.amountMinor,
                it.currency,
                it.postingType,
                it.requestedChannel
            ).joinToString("|")
        }.ifEmpty { "no-postings:$customerId:${accountId ?: "ALL"}:$from:$to" }
        return sha256("$customerId:${accountId ?: "ALL"}:$from:$to:$openingBalanceMinor:$closingBalanceMinor:$fingerprint")
    }

    private fun statementPayloadHash(
        customerId: String,
        accountId: String?,
        from: LocalDate,
        to: LocalDate,
        statementScope: String,
        currency: String,
        openingBalanceMinor: Long,
        closingBalanceMinor: Long,
        debitTotalMinor: Long,
        creditTotalMinor: Long,
        netAmountMinor: Long,
        lineCount: Int,
        sourceLedgerHash: String
    ): String =
        sha256(
            objectMapper.writeValueAsString(
                mapOf(
                    "customerId" to customerId,
                    "accountId" to accountId,
                    "from" to from.toString(),
                    "to" to to.toString(),
                    "statementScope" to statementScope,
                    "currency" to currency,
                    "openingBalanceMinor" to openingBalanceMinor,
                    "closingBalanceMinor" to closingBalanceMinor,
                    "debitTotalMinor" to debitTotalMinor,
                    "creditTotalMinor" to creditTotalMinor,
                    "netAmountMinor" to netAmountMinor,
                    "lineCount" to lineCount,
                    "sourceLedgerHash" to sourceLedgerHash,
                    "maskingPolicy" to "CUSTOMER_SELF",
                    "syntheticOnly" to true
                ).toSortedMap()
            )
        )

    private fun persistStatementArtifactSnapshot(statement: CustomerStatementDto): StatementArtifactSnapshot =
        jdbc.queryForObject(
            """
            INSERT INTO statement_artifact_snapshots (
              statement_id,
              customer_id,
              account_id,
              from_date,
              to_date,
              statement_scope,
              source_ledger_hash,
              payload_hash,
              snapshot_json,
              synthetic_only
            )
            VALUES (
              :statementId,
              :customerId,
              :accountId,
              :fromDate,
              :toDate,
              :statementScope,
              :sourceLedgerHash,
              :payloadHash,
              CAST(:snapshotJson AS jsonb),
              true
            )
            ON CONFLICT (statement_id) DO UPDATE
            SET last_viewed_at = now()
            RETURNING created_at, last_viewed_at
            """.trimIndent(),
            mapOf(
                "statementId" to statement.statementId,
                "customerId" to statement.customerId,
                "accountId" to statement.accountId,
                "fromDate" to statement.from,
                "toDate" to statement.to,
                "statementScope" to statement.statementScope,
                "sourceLedgerHash" to statement.sourceLedgerHash,
                "payloadHash" to statement.payloadHash,
                "snapshotJson" to objectMapper.writeValueAsString(
                    mapOf(
                        "statementId" to statement.statementId,
                        "customerId" to statement.customerId,
                        "accountId" to statement.accountId,
                        "from" to statement.from.toString(),
                        "to" to statement.to.toString(),
                        "statementScope" to statement.statementScope,
                        "currency" to statement.currency,
                        "openingBalanceMinor" to statement.openingBalanceMinor,
                        "closingBalanceMinor" to statement.closingBalanceMinor,
                        "debitTotalMinor" to statement.debitTotalMinor,
                        "creditTotalMinor" to statement.creditTotalMinor,
                        "netAmountMinor" to statement.netAmountMinor,
                        "lineCount" to statement.lineCount,
                        "sourceLedgerHash" to statement.sourceLedgerHash,
                        "payloadHash" to statement.payloadHash,
                        "maskingPolicy" to statement.maskingPolicy,
                        "syntheticOnly" to true
                    )
                )
            )
        ) { rs, _ ->
            StatementArtifactSnapshot(
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
                lastViewedAt = rs.getObject("last_viewed_at", OffsetDateTime::class.java)
            )
        } ?: error("statement artifact snapshot was not returned")

    private fun appendReadAudit(
        actor: ReadActor,
        eventType: String,
        screenId: String,
        businessReferenceId: String?,
        customerId: String?,
        accountId: String?,
        reason: String?,
        payload: Map<String, Any?>
    ): String =
        auditEvents.append(
            eventType = eventType,
            actorType = actor.actorType,
            actorId = actor.actorId,
            actorRole = actor.actorRole,
            screenId = screenId,
            businessReferenceId = businessReferenceId,
            customerId = customerId,
            accountId = accountId,
            reason = reason,
            payload = payload
        )

    private fun signedAmount(direction: PostingDirection, amountMinor: Long): Long =
        if (direction == PostingDirection.DEBIT) -amountMinor else amountMinor

    private fun deterministicId(prefix: String, input: String): String =
        "$prefix-${sha256(input).take(16).uppercase()}"

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private data class ReadActor(
        val actorType: String,
        val actorId: String,
        val actorRole: String,
        val customerIdForAudit: String?
    )

    private data class TransactionRow(
        val transactionId: String,
        val transactionType: String,
        val businessReferenceId: String,
        val businessDate: LocalDate,
        val status: String,
        val requestedBy: String,
        val requestedChannel: String,
        val postedAt: OffsetDateTime?,
        val originalTransactionId: String?,
        val customerId: String,
        val accountId: String,
        val direction: PostingDirection,
        val amountMinor: Long,
        val currency: String,
        val postingType: String
    )

    private data class AccountRow(
        val accountId: String,
        val customerId: String,
        val currency: String,
        val currentLedgerBalanceMinor: Long,
        val currentAvailableBalanceMinor: Long
    )

    private data class CertificateSourcePosting(
        val ledgerPostingId: String,
        val ledgerTransactionId: String,
        val businessDate: LocalDate,
        val direction: String,
        val amountMinor: Long,
        val currency: String,
        val postingType: String
    )

    private data class BalanceCertificateSource(
        val postingCount: Int,
        val lastBusinessDate: LocalDate?,
        val ledgerHash: String
    )

    private data class BalanceCertificateSnapshot(
        val certificateId: String,
        val accountId: String,
        val customerId: String,
        val currency: String,
        val date: LocalDate,
        val balanceAsOfMinor: Long,
        val currentLedgerBalanceMinor: Long,
        val currentAvailableBalanceMinor: Long,
        val deterministicInputHash: String,
        val sourcePostingCount: Int,
        val sourceLastBusinessDate: LocalDate?,
        val sourceLedgerHash: String,
        val syntheticOnly: Boolean,
        val createdAt: OffsetDateTime,
        val lastViewedAt: OffsetDateTime
    ) {
        fun toDto(): BalanceCertificateDto =
            BalanceCertificateDto(
                certificateId = certificateId,
                accountId = accountId,
                customerId = customerId,
                currency = currency,
                date = date,
                balanceAsOfMinor = balanceAsOfMinor,
                currentLedgerBalanceMinor = currentLedgerBalanceMinor,
                currentAvailableBalanceMinor = currentAvailableBalanceMinor,
                deterministicInputHash = deterministicInputHash,
                sourcePostingCount = sourcePostingCount,
                sourceLastBusinessDate = sourceLastBusinessDate,
                sourceLedgerHash = sourceLedgerHash,
                snapshotCreatedAt = createdAt,
                lastViewedAt = lastViewedAt,
                syntheticOnly = syntheticOnly
            )
    }

    private data class StatementArtifactSnapshot(
        val createdAt: OffsetDateTime,
        val lastViewedAt: OffsetDateTime
    )

    private companion object {
        val STAFF_READ_ROLES = setOf(
            "BRANCH_STAFF",
            "BRANCH_MANAGER",
            "CALL_CENTER_MANAGER",
            "OPS_MANAGER",
            "AUDITOR",
            "COMPLIANCE_MANAGER"
        )
    }
}
