package lab.banking.core.statement

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
    private val auditEvents: AuditEventAppender
) {
    @Transactional
    fun customerStatement(customerId: String, from: LocalDate, to: LocalDate, reason: String?): CustomerStatementDto {
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
              AND lt.business_date >= :from
              AND lt.business_date <= :to
            ORDER BY lt.business_date, lt.posted_at NULLS LAST, lt.ledger_transaction_id, lp.ledger_posting_id
            """.trimIndent(),
            mapOf("customerId" to customerId, "from" to from, "to" to to),
            this::mapStatementLine
        )
        val currency = lines.firstOrNull()?.currency ?: primaryCurrency(customerId)
        val statement = CustomerStatementDto(
            customerId = customerId,
            from = from,
            to = to,
            currency = currency,
            openingBalanceMinor = customerSignedPostingSum(customerId, toExclusive = from),
            closingBalanceMinor = customerSignedPostingSum(customerId, toInclusive = to),
            debitTotalMinor = lines.filter { it.direction == PostingDirection.DEBIT }.sumOf { it.amountMinor },
            creditTotalMinor = lines.filter { it.direction == PostingDirection.CREDIT }.sumOf { it.amountMinor },
            netAmountMinor = lines.sumOf { it.signedAmountMinor },
            lineCount = lines.size,
            lines = lines
        )
        appendReadAudit(
            actor = actor,
            eventType = "STATEMENT_VIEW",
            screenId = "CWB-103",
            businessReferenceId = "$customerId:$from:$to",
            customerId = customerId,
            accountId = null,
            reason = reason,
            payload = mapOf(
                "from" to from.toString(),
                "to" to to.toString(),
                "lineCount" to statement.lineCount,
                "syntheticOnly" to true
            )
        )
        return statement
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
        val hash = sha256("${account.accountId}:${account.customerId}:$date:$balanceAsOf:${account.currency}")
        val certificate = BalanceCertificateDto(
            certificateId = "BALCERT-${hash.take(16).uppercase()}",
            accountId = account.accountId,
            customerId = account.customerId,
            currency = account.currency,
            date = date,
            balanceAsOfMinor = balanceAsOf,
            currentLedgerBalanceMinor = account.currentLedgerBalanceMinor,
            currentAvailableBalanceMinor = account.currentAvailableBalanceMinor,
            deterministicInputHash = hash
        )
        appendReadAudit(
            actor = actor,
            eventType = "BALANCE_CERTIFICATE_VIEW",
            screenId = "ACC-102",
            businessReferenceId = certificate.certificateId,
            customerId = account.customerId,
            accountId = account.accountId,
            reason = reason,
            payload = mapOf(
                "date" to date.toString(),
                "certificateId" to certificate.certificateId,
                "syntheticOnly" to true
            )
        )
        return certificate
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

    private fun appendReadAudit(
        actor: ReadActor,
        eventType: String,
        screenId: String,
        businessReferenceId: String?,
        customerId: String?,
        accountId: String?,
        reason: String?,
        payload: Map<String, Any?>
    ) {
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
    }

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
