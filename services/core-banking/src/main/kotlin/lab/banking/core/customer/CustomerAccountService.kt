package lab.banking.core.customer

import java.sql.ResultSet
import lab.banking.core.audit.AuditEventAppender
import lab.banking.core.security.BankingLabAuthContext
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CustomerAccountService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val auditEvents: AuditEventAppender
) {
    @Transactional
    fun accounts(customerId: String): CustomerAccountListResponse {
        if (customerId.isBlank()) {
            throw WorkflowErrors.validation("customerId is required")
        }
        BankingLabAuthContext.requireCustomerOwnership(customerId)
        val items = jdbc.query(
            """
            SELECT a.customer_id, a.account_id, a.account_no, a.status, a.currency,
                   a.opened_at,
                   COALESCE(p.ledger_balance_minor, 0) AS ledger_balance_minor,
                   COALESCE(p.available_balance_minor, 0) AS available_balance_minor,
                   COALESCE(p.hold_amount_minor, 0) AS hold_amount_minor
            FROM accounts a
            LEFT JOIN account_balance_projections p
              ON p.account_id = a.account_id
             AND p.currency = a.currency
            WHERE a.customer_id = :customerId
              AND a.synthetic_system_account = FALSE
            ORDER BY a.opened_at, a.account_id
            """.trimIndent(),
            mapOf("customerId" to customerId),
            this::mapListItem
        )
        appendAccountListAudit(customerId, items)
        return CustomerAccountListResponse(items = items)
    }

    @Transactional
    fun detail(customerId: String, accountId: String): CustomerAccountDetailDto {
        BankingLabAuthContext.requireCustomerOwnership(customerId)
        val detail = jdbc.queryForObject(
            """
            SELECT a.customer_id, a.account_id, a.account_no, a.status, a.currency,
                   a.opened_at,
                   COALESCE(p.ledger_balance_minor, 0) AS ledger_balance_minor,
                   COALESCE(p.available_balance_minor, 0) AS available_balance_minor,
                   COALESCE(p.hold_amount_minor, 0) AS hold_amount_minor
            FROM accounts a
            LEFT JOIN account_balance_projections p
              ON p.account_id = a.account_id
             AND p.currency = a.currency
            WHERE a.customer_id = :customerId
              AND a.account_id = :accountId
            """.trimIndent(),
            mapOf("customerId" to customerId, "accountId" to accountId),
            this::mapDetail
        ) ?: throw WorkflowErrors.notFound("customer account not found: $accountId")
        val enriched = detail.copy(
            limits = accountLimits(accountId),
            holds = accountHolds(accountId),
            recentTransactions = recentLedgerActivity(customerId, accountId, limit = 10),
            statementActions = listOf(
                CustomerStatementActionDto(
                    actionType = "ACCOUNT_STATEMENT",
                    href = "/api/customer/accounts/$accountId/statement"
                ),
                CustomerStatementActionDto(
                    actionType = "BALANCE_CERTIFICATE",
                    href = "/api/accounts/$accountId/balance-certificate"
                )
            )
        )
        appendAccountViewAudit(customerId, accountId, enriched)
        return enriched
    }

    @Transactional
    fun internalRecipientLookup(query: String): InternalRecipientLookupResponse {
        val normalized = query.trim()
        if (normalized.isBlank()) {
            throw WorkflowErrors.validation("recipient lookup query is required")
        }
        val recipient = jdbc.query(
            """
            SELECT account_id, account_no, status, currency
            FROM accounts
            WHERE synthetic_system_account = FALSE
              AND status = 'ACTIVE'
              AND (account_id = :query OR account_no = :query)
            ORDER BY account_id
            LIMIT 1
            """.trimIndent(),
            mapOf("query" to normalized),
            this::mapRecipient
        ).firstOrNull() ?: throw WorkflowErrors.notFound("internal synthetic recipient account not found")
        appendRecipientLookupAudit(normalized, recipient)
        return InternalRecipientLookupResponse(item = recipient)
    }

    private fun appendAccountListAudit(customerId: String, items: List<CustomerAccountListItemDto>) {
        val principal = BankingLabAuthContext.get()
        auditEvents.append(
            eventType = "ACCOUNT_LIST_VIEW",
            actorType = "CUSTOMER",
            actorId = principal?.subject ?: customerId,
            actorRole = principal?.roles?.sorted()?.joinToString(",") ?: "CUSTOMER",
            screenId = "CWB-101",
            businessReferenceId = customerId,
            customerId = customerId,
            reason = null,
            payload = mapOf(
                "resultCount" to items.size,
                "maskedAccountNos" to items.map { it.maskedAccountNo },
                "maskingPolicy" to "CUSTOMER_SELF",
                "syntheticOnly" to true
            )
        )
    }

    private fun appendAccountViewAudit(customerId: String, accountId: String, detail: CustomerAccountDetailDto) {
        val principal = BankingLabAuthContext.get()
        auditEvents.append(
            eventType = "ACCOUNT_VIEW",
            actorType = "CUSTOMER",
            actorId = principal?.subject ?: customerId,
            actorRole = principal?.roles?.sorted()?.joinToString(",") ?: "CUSTOMER",
            screenId = "CWB-102",
            businessReferenceId = accountId,
            customerId = customerId,
            accountId = accountId,
            reason = null,
            payload = mapOf(
                "maskedAccountNo" to detail.maskedAccountNo,
                "status" to detail.status,
                "currency" to detail.currency,
                "ledgerBalanceMinor" to detail.ledgerBalanceMinor,
                "availableBalanceMinor" to detail.availableBalanceMinor,
                "holdAmountMinor" to detail.holdAmountMinor,
                "maskingPolicy" to "CUSTOMER_SELF",
                "syntheticOnly" to true
            )
        )
    }

    private fun appendRecipientLookupAudit(query: String, recipient: InternalRecipientAccountDto) {
        val principal = BankingLabAuthContext.get()
        auditEvents.append(
            eventType = "INTERNAL_RECIPIENT_LOOKUP",
            actorType = "CUSTOMER",
            actorId = principal?.subject ?: "CUSTOMER",
            actorRole = principal?.roles?.sorted()?.joinToString(",") ?: "CUSTOMER",
            screenId = "CWB-201",
            businessReferenceId = recipient.accountId,
            customerId = principal?.customerId,
            accountId = recipient.accountId,
            reason = null,
            payload = mapOf(
                "queryFingerprint" to query.hashCode().toString(),
                "maskedAccountNo" to recipient.maskedAccountNo,
                "status" to recipient.status,
                "currency" to recipient.currency,
                "internalOnly" to true,
                "syntheticOnly" to true
            )
        )
    }

    private fun mapDetail(rs: ResultSet, rowNum: Int): CustomerAccountDetailDto =
        CustomerAccountDetailDto(
            customerId = rs.getString("customer_id"),
            accountId = rs.getString("account_id"),
            maskedAccountNo = maskAccountNo(rs.getString("account_no")),
            status = rs.getString("status"),
            currency = rs.getString("currency"),
            ledgerBalanceMinor = rs.getLong("ledger_balance_minor"),
            availableBalanceMinor = rs.getLong("available_balance_minor"),
            holdAmountMinor = rs.getLong("hold_amount_minor"),
            openedAt = rs.getObject("opened_at", java.time.OffsetDateTime::class.java)
        )

    private fun mapListItem(rs: ResultSet, rowNum: Int): CustomerAccountListItemDto =
        CustomerAccountListItemDto(
            customerId = rs.getString("customer_id"),
            accountId = rs.getString("account_id"),
            maskedAccountNo = maskAccountNo(rs.getString("account_no")),
            status = rs.getString("status"),
            currency = rs.getString("currency"),
            ledgerBalanceMinor = rs.getLong("ledger_balance_minor"),
            availableBalanceMinor = rs.getLong("available_balance_minor"),
            holdAmountMinor = rs.getLong("hold_amount_minor")
        )

    private fun mapRecipient(rs: ResultSet, rowNum: Int): InternalRecipientAccountDto =
        InternalRecipientAccountDto(
            accountId = rs.getString("account_id"),
            maskedAccountNo = maskAccountNo(rs.getString("account_no")),
            status = rs.getString("status"),
            currency = rs.getString("currency"),
            recipientLabel = "Synthetic internal account ${rs.getString("account_id").takeLast(6)}"
        )

    private fun maskAccountNo(accountNo: String?): String {
        val value = accountNo.orEmpty()
        val parts = value.split("-")
        if (parts.size >= 3) {
            return "${parts[0]}-${"*".repeat(parts[1].length)}-${parts.last().takeLast(4)}"
        }
        if (value.length <= 6) {
            return "****"
        }
        return "${value.take(4)}-${"*".repeat(maxOf(3, value.length - 10))}-${value.takeLast(4)}"
    }

    private fun accountLimits(accountId: String): CustomerAccountLimitsDto? =
        jdbc.query(
            """
            SELECT daily_transfer_limit_minor, single_transfer_limit_minor, updated_at
            FROM account_limits
            WHERE account_id = :accountId
            """.trimIndent(),
            mapOf("accountId" to accountId)
        ) { rs, _ ->
            CustomerAccountLimitsDto(
                dailyTransferLimitMinor = rs.getLong("daily_transfer_limit_minor"),
                singleTransferLimitMinor = rs.getLong("single_transfer_limit_minor"),
                updatedAt = rs.getObject("updated_at", java.time.OffsetDateTime::class.java)
            )
        }.firstOrNull()

    private fun accountHolds(accountId: String): List<CustomerAccountHoldDto> =
        jdbc.query(
            """
            SELECT hold_id, hold_amount_minor, reason_code, status, approval_id, created_at
            FROM account_holds
            WHERE account_id = :accountId
              AND status <> 'RELEASED'
            ORDER BY created_at DESC, hold_id DESC
            LIMIT 20
            """.trimIndent(),
            mapOf("accountId" to accountId)
        ) { rs, _ ->
            CustomerAccountHoldDto(
                holdId = rs.getString("hold_id"),
                holdAmountMinor = rs.getLong("hold_amount_minor"),
                reasonCode = rs.getString("reason_code"),
                status = rs.getString("status"),
                approvalId = rs.getString("approval_id"),
                createdAt = rs.getObject("created_at", java.time.OffsetDateTime::class.java)
            )
        }

    private fun recentLedgerActivity(
        customerId: String,
        accountId: String,
        limit: Int
    ): List<CustomerRecentLedgerActivityDto> =
        jdbc.query(
            """
            SELECT lt.ledger_transaction_id, lt.transaction_type, lt.business_date, lt.posted_at,
                   lp.account_id, a.account_no, lp.direction, lp.amount_minor, lp.currency, lp.posting_type,
                   lt.requested_channel
            FROM ledger_transactions lt
            JOIN ledger_postings lp ON lp.ledger_transaction_id = lt.ledger_transaction_id
            JOIN accounts a ON a.account_id = lp.account_id
            WHERE a.customer_id = :customerId
              AND lp.account_id = :accountId
            ORDER BY lt.business_date DESC, lt.posted_at DESC NULLS LAST, lt.ledger_transaction_id DESC, lp.ledger_posting_id DESC
            LIMIT :limit
            """.trimIndent(),
            mapOf("customerId" to customerId, "accountId" to accountId, "limit" to limit)
        ) { rs, _ ->
            val direction = rs.getString("direction")
            val amount = rs.getLong("amount_minor")
            CustomerRecentLedgerActivityDto(
                transactionId = rs.getString("ledger_transaction_id"),
                transactionType = rs.getString("transaction_type"),
                businessDate = rs.getObject("business_date", java.time.LocalDate::class.java),
                postedAt = rs.getObject("posted_at", java.time.OffsetDateTime::class.java),
                accountId = rs.getString("account_id"),
                maskedAccountNo = maskAccountNo(rs.getString("account_no")),
                direction = direction,
                amountMinor = amount,
                signedAmountMinor = if (direction == "DEBIT") -amount else amount,
                currency = rs.getString("currency").trim(),
                postingType = rs.getString("posting_type"),
                requestedChannel = rs.getString("requested_channel")
            )
        }
}
