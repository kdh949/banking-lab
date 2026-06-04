package lab.banking.core.card

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import lab.banking.core.audit.AuditEventAppender
import lab.banking.core.common.BankingLabDomainException
import lab.banking.core.ledger.application.CardCaptureCommand as LedgerCardCaptureCommand
import lab.banking.core.ledger.application.LedgerCommandService
import lab.banking.core.ledger.application.ReversalCommand
import lab.banking.core.ledger.domain.LedgerCommandResult
import lab.banking.core.security.BankingLabAuthContext
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class CardService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val ledgerCommandService: LedgerCommandService,
    private val auditEvents: AuditEventAppender
) {
    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun issue(command: IssueCardCommand): CardIssueResponse {
        val actor = actor(command.requestedBy, command.requestedByRole, defaultRole = "CUSTOMER")
        val reason = requireReason(command.reason, "CARD_ISSUE requires a business reason")
        val idempotencyKey = requireNonBlank(command.idempotencyKey, "idempotencyKey")
        existingCardByIdempotency(idempotencyKey)?.let { return CardIssueResponse(it, replayed = true) }
        BankingLabAuthContext.requireCustomerOwnership(command.customerId)
        requireTokenizedPan(command.panToken)
        if (!command.panLast4.matches(Regex("^[0-9]{4}$"))) {
            throw WorkflowErrors.validation("panLast4 must contain exactly four digits")
        }
        if (command.dailyLimitMinor < 0 || command.monthlyLimitMinor < command.dailyLimitMinor || command.singleLimitMinor < 0) {
            throw WorkflowErrors.validation("card limits must be non-negative and monthly must cover daily")
        }
        val account = account(command.accountId)
        if (account.customerId != command.customerId) {
            throw WorkflowErrors.authorizationViolation("card account must belong to customer")
        }
        if (account.status != "ACTIVE") {
            throw WorkflowErrors.stateViolation("only active accounts can issue cards")
        }
        val cardId = "CARD-${UUID.randomUUID().toString().uppercase()}"
        jdbc.update(
            """
            INSERT INTO cards (
              card_id, customer_id, account_id, pan_token, pan_last4,
              status, issued_by, reason, idempotency_key, metadata_json
            )
            VALUES (
              :cardId, :customerId, :accountId, :panToken, :panLast4,
              'ACTIVE', :issuedBy, :reason, :idempotencyKey, CAST(:metadata AS jsonb)
            )
            """.trimIndent(),
            mapOf(
                "cardId" to cardId,
                "customerId" to command.customerId,
                "accountId" to command.accountId,
                "panToken" to command.panToken,
                "panLast4" to command.panLast4,
                "issuedBy" to actor.actorId,
                "reason" to reason,
                "idempotencyKey" to idempotencyKey,
                "metadata" to objectMapper.writeValueAsString(mapOf("syntheticOnly" to true, "rawPanStored" to false))
            )
        )
        jdbc.update(
            """
            INSERT INTO card_limits (card_id, daily_limit_minor, monthly_limit_minor, single_limit_minor)
            VALUES (:cardId, :dailyLimitMinor, :monthlyLimitMinor, :singleLimitMinor)
            """.trimIndent(),
            mapOf(
                "cardId" to cardId,
                "dailyLimitMinor" to command.dailyLimitMinor,
                "monthlyLimitMinor" to command.monthlyLimitMinor,
                "singleLimitMinor" to command.singleLimitMinor
            )
        )
        appendAudit("CARD_ISSUED", actor, "CWB-601", cardId, command.customerId, command.accountId, reason, mapOf("rawPanStored" to false, "syntheticOnly" to true))
        return CardIssueResponse(card(cardId), replayed = false)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun simulateThreeDs(command: ThreeDsSimulationCommand): ThreeDsSimulationDto {
        requirePositive(command.amountMinor, "amountMinor")
        val idempotencyKey = requireNonBlank(command.idempotencyKey, "idempotencyKey")
        existingThreeDs(idempotencyKey)?.let { return it }
        val card = cardForUpdate(command.cardId)
        authorizeCustomer(card.customerId)
        val authenticationId = "3DS-${UUID.randomUUID().toString().uppercase()}"
        jdbc.update(
            """
            INSERT INTO card_3ds_simulations (authentication_id, card_id, amount_minor, status, idempotency_key)
            VALUES (:authenticationId, :cardId, :amountMinor, 'AUTHENTICATED', :idempotencyKey)
            """.trimIndent(),
            mapOf("authenticationId" to authenticationId, "cardId" to command.cardId, "amountMinor" to command.amountMinor, "idempotencyKey" to idempotencyKey)
        )
        appendAudit("CARD_3DS_AUTHENTICATED", currentActor(), "CWB-605", authenticationId, card.customerId, card.accountId, "Synthetic 3DS simulation", mapOf("syntheticOnly" to true))
        return threeDs(authenticationId)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun authorize(command: CardAuthorizationCommand): CardAuthorizationResponse {
        val actor = actor(command.requestedBy, null, defaultRole = "CUSTOMER")
        val reason = requireReason(command.reason, "CARD_AUTHORIZATION requires a business reason")
        val idempotencyKey = requireNonBlank(command.idempotencyKey, "idempotencyKey")
        existingAuthorization(idempotencyKey)?.let { return CardAuthorizationResponse(it, replayed = true) }
        requirePositive(command.amountMinor, "amountMinor")
        val card = cardForUpdate(command.cardId)
        authorizeCustomer(card.customerId)
        if (card.status != "ACTIVE") {
            throw WorkflowErrors.stateViolation("only active cards can authorize purchases")
        }
        if (command.amountMinor >= THREE_DS_REQUIRED_MINOR) {
            requireAuthenticated3ds(card.cardId, command.amountMinor, command.threeDsAuthenticationId)
        }
        val businessDate = command.businessDate ?: LocalDate.now()
        enforceCardLimits(card.cardId, businessDate, command.amountMinor)
        ensureProjection(card.accountId)
        val available = availableBalanceForUpdate(card.accountId)
        if (available < command.amountMinor) {
            throw WorkflowErrors.stateViolation("card authorization amount exceeds available balance")
        }
        val holdId = "HOLD-CARD-${UUID.randomUUID().toString().uppercase()}"
        jdbc.update(
            """
            INSERT INTO account_holds (hold_id, account_id, hold_amount_minor, reason_code, status, approval_id)
            VALUES (:holdId, :accountId, :amountMinor, 'CARD_AUTHORIZATION', 'ACTIVE', NULL)
            """.trimIndent(),
            mapOf("holdId" to holdId, "accountId" to card.accountId, "amountMinor" to command.amountMinor)
        )
        updateHoldProjection(card.accountId, command.currency, command.amountMinor)
        val authorizationId = "CAUTH-${UUID.randomUUID().toString().uppercase()}"
        jdbc.update(
            """
            INSERT INTO card_authorizations (
              authorization_id, card_id, account_id, amount_minor, currency, merchant_name,
              business_date, status, hold_id, three_ds_authentication_id,
              requested_by, requested_channel, reason, idempotency_key
            )
            VALUES (
              :authorizationId, :cardId, :accountId, :amountMinor, :currency, :merchantName,
              :businessDate, 'HELD', :holdId, :threeDsAuthenticationId,
              :requestedBy, :requestedChannel, :reason, :idempotencyKey
            )
            """.trimIndent(),
            mapOf(
                "authorizationId" to authorizationId,
                "cardId" to card.cardId,
                "accountId" to card.accountId,
                "amountMinor" to command.amountMinor,
                "currency" to command.currency,
                "merchantName" to command.merchantName,
                "businessDate" to businessDate,
                "holdId" to holdId,
                "threeDsAuthenticationId" to command.threeDsAuthenticationId,
                "requestedBy" to actor.actorId,
                "requestedChannel" to (command.requestedChannel ?: "CARD_AUTH"),
                "reason" to reason,
                "idempotencyKey" to idempotencyKey
            )
        )
        appendAudit("CARD_AUTHORIZATION_HELD", actor, "CWB-602", authorizationId, card.customerId, card.accountId, reason, mapOf("holdId" to holdId, "syntheticOnly" to true))
        return CardAuthorizationResponse(authorization(authorizationId), replayed = false)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun capture(command: CardCaptureCommand): CardCaptureResponse {
        val reason = requireReason(command.reason, "CARD_CAPTURE requires a business reason")
        val idempotencyKey = requireNonBlank(command.idempotencyKey, "idempotencyKey")
        existingCapture(idempotencyKey)?.let { capture ->
            val transaction = ledgerCommandService.transaction(capture.ledgerTransactionId)
                ?: throw WorkflowErrors.stateViolation("card capture ledger transaction not found")
            return CardCaptureResponse(capture, LedgerCommandResult(transaction, replayed = true), replayed = true)
        }
        val auth = authorizationForUpdate(command.authorizationId)
        val card = cardForUpdate(auth.cardId)
        authorizeCustomer(card.customerId)
        if (auth.status != "HELD") {
            throw WorkflowErrors.stateViolation("only held card authorizations can be captured")
        }
        releaseHold(auth)
        val actor = actor(command.requestedBy, null, defaultRole = "CUSTOMER")
        val ledgerResult = ledgerCommandService.captureCardPurchase(
            LedgerCardCaptureCommand(
                authorizationId = auth.authorizationId,
                cardId = auth.cardId,
                accountId = auth.accountId,
                amountMinor = auth.amountMinor,
                idempotencyKey = "CARD-CAP-$idempotencyKey",
                requestedBy = actor.actorId,
                requestedChannel = command.requestedChannel ?: "CARD_CAPTURE",
                businessDate = command.businessDate ?: auth.businessDate,
                reason = reason,
                currency = auth.currency
            )
        )
        val captureId = "CCAP-${UUID.randomUUID().toString().uppercase()}"
        jdbc.update(
            """
            INSERT INTO card_captures (
              capture_id, authorization_id, card_id, amount_minor, currency,
              ledger_transaction_id, status, idempotency_key
            )
            VALUES (
              :captureId, :authorizationId, :cardId, :amountMinor, :currency,
              :ledgerTransactionId, 'POSTED', :idempotencyKey
            )
            """.trimIndent(),
            mapOf(
                "captureId" to captureId,
                "authorizationId" to auth.authorizationId,
                "cardId" to auth.cardId,
                "amountMinor" to auth.amountMinor,
                "currency" to auth.currency,
                "ledgerTransactionId" to ledgerResult.value.id,
                "idempotencyKey" to idempotencyKey
            )
        )
        updateAuthorizationStatus(auth.authorizationId, "CAPTURED")
        appendAudit("CARD_CAPTURE_POSTED", actor, "CWB-603", captureId, card.customerId, card.accountId, reason, mapOf("ledgerTransactionId" to ledgerResult.value.id, "syntheticOnly" to true))
        return CardCaptureResponse(capture(captureId), ledgerResult, replayed = false)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun cancelAuthorization(authorizationId: String, command: CardCancelCommand): CardAuthorizationResponse {
        val reason = requireReason(command.reason, "CARD_AUTHORIZATION_CANCEL requires a business reason")
        val auth = authorizationForUpdate(authorizationId)
        val card = cardForUpdate(auth.cardId)
        authorizeCustomer(card.customerId)
        if (auth.status == "CANCELLED") {
            return CardAuthorizationResponse(auth, replayed = true)
        }
        if (auth.status != "HELD") {
            throw WorkflowErrors.stateViolation("only held authorizations can be cancelled")
        }
        releaseHold(auth)
        updateAuthorizationStatus(authorizationId, "CANCELLED")
        appendAudit("CARD_AUTHORIZATION_CANCELLED", actor(command.requestedBy, null, "CUSTOMER"), "CWB-604", authorizationId, card.customerId, card.accountId, reason, mapOf("syntheticOnly" to true))
        return CardAuthorizationResponse(authorization(authorizationId), replayed = false)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun reverseCapture(captureId: String, command: CardCancelCommand): CardCaptureResponse {
        val reason = requireReason(command.reason, "CARD_CAPTURE_REVERSAL requires a business reason")
        val capture = captureForUpdate(captureId)
        val card = cardForUpdate(capture.cardId)
        authorizeCustomer(card.customerId)
        if (capture.status != "POSTED") {
            val transaction = ledgerCommandService.transaction(capture.ledgerTransactionId)
                ?: throw WorkflowErrors.stateViolation("card capture ledger transaction not found")
            return CardCaptureResponse(capture, LedgerCommandResult(transaction, replayed = true), replayed = true)
        }
        val reversal = ledgerCommandService.reverseTransaction(
            ReversalCommand(
                originalTransactionId = capture.ledgerTransactionId,
                idempotencyKey = requireNonBlank(command.idempotencyKey, "idempotencyKey"),
                requestedBy = command.requestedBy ?: currentActor().actorId,
                requestedChannel = command.requestedChannel ?: "CARD_CAPTURE_REVERSAL",
                reason = reason,
                businessReferenceId = capture.captureId
            )
        )
        jdbc.update(
            """
            UPDATE card_captures
            SET status = 'REVERSED',
                reversed_at = now()
            WHERE capture_id = :captureId
            """.trimIndent(),
            mapOf("captureId" to captureId)
        )
        appendAudit("CARD_CAPTURE_REVERSED", actor(command.requestedBy, null, "CUSTOMER"), "CWB-604", captureId, card.customerId, card.accountId, reason, mapOf("reversalTransactionId" to reversal.value.id, "syntheticOnly" to true))
        return CardCaptureResponse(capture(captureId), reversal, replayed = false)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun reportLost(cardId: String, command: CardLossReportCommand): CardDto {
        val reason = requireReason(command.reason, "CARD_LOSS_REPORT requires a business reason")
        val card = cardForUpdate(cardId)
        authorizeCustomer(card.customerId)
        if (card.status != "ACTIVE") {
            return card(cardId)
        }
        jdbc.update(
            "UPDATE cards SET status = 'LOST', updated_at = now() WHERE card_id = :cardId",
            mapOf("cardId" to cardId)
        )
        appendAudit("CARD_LOST_REPORTED", actor(command.requestedBy, command.requestedByRole, "CUSTOMER"), "CWB-606", cardId, card.customerId, card.accountId, reason, mapOf("syntheticOnly" to true))
        return card(cardId)
    }

    @Transactional
    fun card(cardId: String): CardDto {
        val card = cardRow(cardId)
        authorizeCustomer(card.customerId)
        return card.toDto()
    }

    private fun enforceCardLimits(cardId: String, businessDate: LocalDate, amountMinor: Long) {
        val limits = jdbc.queryForObject(
            """
            SELECT daily_limit_minor, monthly_limit_minor, single_limit_minor
            FROM card_limits
            WHERE card_id = :cardId
            FOR UPDATE
            """.trimIndent(),
            mapOf("cardId" to cardId)
        ) { rs, _ -> CardLimit(rs.getLong("daily_limit_minor"), rs.getLong("monthly_limit_minor"), rs.getLong("single_limit_minor")) }
            ?: throw WorkflowErrors.notFound("card limits not found: $cardId")
        if (amountMinor > limits.singleLimitMinor) {
            throw limitExceeded("PER_TRANSACTION", limits.singleLimitMinor, amountMinor, limits.singleLimitMinor)
        }
        incrementUsage(cardId, "DAILY", businessDate, amountMinor, limits.dailyLimitMinor)
        incrementUsage(cardId, "MONTHLY", businessDate.withDayOfMonth(1), amountMinor, limits.monthlyLimitMinor)
    }

    private fun incrementUsage(cardId: String, periodKind: String, periodStartDate: LocalDate, amountMinor: Long, configuredLimitMinor: Long) {
        jdbc.update(
            """
            INSERT INTO card_limit_usage_counters (card_id, period_kind, business_date, used_amount_minor)
            VALUES (:cardId, :periodKind, :periodStartDate, 0)
            ON CONFLICT (card_id, period_kind, business_date) DO NOTHING
            """.trimIndent(),
            mapOf("cardId" to cardId, "periodKind" to periodKind, "periodStartDate" to periodStartDate)
        )
        val used = jdbc.queryForObject(
            """
            SELECT used_amount_minor
            FROM card_limit_usage_counters
            WHERE card_id = :cardId
              AND period_kind = :periodKind
              AND business_date = :periodStartDate
            FOR UPDATE
            """.trimIndent(),
            mapOf("cardId" to cardId, "periodKind" to periodKind, "periodStartDate" to periodStartDate),
            Long::class.java
        ) ?: 0L
        val remaining = (configuredLimitMinor - used).coerceAtLeast(0)
        if (amountMinor > remaining) {
            throw limitExceeded(periodKind, configuredLimitMinor, amountMinor, remaining)
        }
        jdbc.update(
            """
            UPDATE card_limit_usage_counters
            SET used_amount_minor = used_amount_minor + :amountMinor,
                version = version + 1,
                updated_at = now()
            WHERE card_id = :cardId
              AND period_kind = :periodKind
              AND business_date = :periodStartDate
            """.trimIndent(),
            mapOf("cardId" to cardId, "periodKind" to periodKind, "periodStartDate" to periodStartDate, "amountMinor" to amountMinor)
        )
    }

    private fun releaseHold(auth: CardAuthorizationDto) {
        val holdId = auth.holdId ?: throw WorkflowErrors.stateViolation("card authorization has no active hold")
        val holdRows = jdbc.update(
            """
            UPDATE account_holds
            SET status = 'RELEASED'
            WHERE hold_id = :holdId
              AND status = 'ACTIVE'
            """.trimIndent(),
            mapOf("holdId" to holdId)
        )
        if (holdRows != 1) {
            throw WorkflowErrors.stateViolation("card authorization hold was already released")
        }
        val projectionRows = jdbc.update(
            """
            UPDATE account_balance_projections
            SET hold_amount_minor = hold_amount_minor - :amountMinor,
                available_balance_minor = available_balance_minor + :amountMinor,
                version = version + 1,
                updated_at = now()
            WHERE account_id = :accountId
              AND currency = :currency
              AND hold_amount_minor >= :amountMinor
            """.trimIndent(),
            mapOf("accountId" to auth.accountId, "currency" to auth.currency, "amountMinor" to auth.amountMinor)
        )
        if (projectionRows != 1) {
            throw WorkflowErrors.stateViolation("card authorization hold projection cannot be released")
        }
    }

    private fun updateHoldProjection(accountId: String, currency: String, amountMinor: Long) {
        val projectionRows = jdbc.update(
            """
            UPDATE account_balance_projections
            SET hold_amount_minor = hold_amount_minor + :amountMinor,
                available_balance_minor = available_balance_minor - :amountMinor,
                version = version + 1,
                updated_at = now()
            WHERE account_id = :accountId
              AND currency = :currency
              AND available_balance_minor >= :amountMinor
            """.trimIndent(),
            mapOf("accountId" to accountId, "currency" to currency, "amountMinor" to amountMinor)
        )
        if (projectionRows != 1) {
            throw WorkflowErrors.stateViolation("card authorization amount exceeds available balance")
        }
    }

    private fun requireAuthenticated3ds(cardId: String, amountMinor: Long, authenticationId: String?) {
        if (authenticationId.isNullOrBlank()) {
            throw WorkflowErrors.stateViolation("3DS simulation is required for this card authorization")
        }
        val count = jdbc.queryForObject(
            """
            SELECT count(*)
            FROM card_3ds_simulations
            WHERE authentication_id = :authenticationId
              AND card_id = :cardId
              AND amount_minor = :amountMinor
              AND status = 'AUTHENTICATED'
            """.trimIndent(),
            mapOf("authenticationId" to authenticationId, "cardId" to cardId, "amountMinor" to amountMinor),
            Int::class.java
        ) ?: 0
        if (count != 1) {
            throw WorkflowErrors.stateViolation("3DS simulation does not match authorization")
        }
    }

    private fun account(accountId: String): AccountRow =
        try {
            jdbc.queryForObject(
                "SELECT account_id, customer_id, currency, status FROM accounts WHERE account_id = :accountId FOR UPDATE",
                mapOf("accountId" to accountId)
            ) { rs, _ -> AccountRow(rs.getString("account_id"), rs.getString("customer_id"), rs.getString("currency").trim(), rs.getString("status")) }
                ?: throw WorkflowErrors.notFound("account not found: $accountId")
        } catch (_: EmptyResultDataAccessException) {
            throw WorkflowErrors.notFound("account not found: $accountId")
        }

    private fun ensureProjection(accountId: String) {
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

    private fun availableBalanceForUpdate(accountId: String): Long =
        jdbc.queryForObject(
            "SELECT available_balance_minor FROM account_balance_projections WHERE account_id = :accountId FOR UPDATE",
            mapOf("accountId" to accountId),
            Long::class.java
        ) ?: 0L

    private fun cardForUpdate(cardId: String): CardRow =
        cardRow(cardId, "FOR UPDATE")

    private fun cardRow(cardId: String, lock: String = ""): CardRow =
        try {
            jdbc.queryForObject(
                """
                SELECT c.card_id, c.customer_id, c.account_id, c.pan_token, c.pan_last4, c.status,
                       l.daily_limit_minor, l.monthly_limit_minor, l.single_limit_minor, c.created_at
                FROM cards c
                JOIN card_limits l ON l.card_id = c.card_id
                WHERE c.card_id = :cardId
                $lock
                """.trimIndent(),
                mapOf("cardId" to cardId),
                this::mapCard
            ) ?: throw WorkflowErrors.notFound("card not found: $cardId")
        } catch (_: EmptyResultDataAccessException) {
            throw WorkflowErrors.notFound("card not found: $cardId")
        }

    private fun existingCardByIdempotency(idempotencyKey: String): CardDto? =
        jdbc.query(
            """
            SELECT c.card_id, c.customer_id, c.account_id, c.pan_token, c.pan_last4, c.status,
                   l.daily_limit_minor, l.monthly_limit_minor, l.single_limit_minor, c.created_at
            FROM cards c
            JOIN card_limits l ON l.card_id = c.card_id
            WHERE c.idempotency_key = :idempotencyKey
            """.trimIndent(),
            mapOf("idempotencyKey" to idempotencyKey)
        ) { rs, _ -> mapCard(rs, 0).toDto() }.firstOrNull()

    private fun mapCard(rs: ResultSet, rowNum: Int): CardRow =
        CardRow(
            cardId = rs.getString("card_id"),
            customerId = rs.getString("customer_id"),
            accountId = rs.getString("account_id"),
            panToken = rs.getString("pan_token"),
            panLast4 = rs.getString("pan_last4"),
            status = rs.getString("status"),
            dailyLimitMinor = rs.getLong("daily_limit_minor"),
            monthlyLimitMinor = rs.getLong("monthly_limit_minor"),
            singleLimitMinor = rs.getLong("single_limit_minor"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
        )

    private fun authorization(authorizationId: String): CardAuthorizationDto =
        jdbc.queryForObject(authorizationSql("WHERE authorization_id = :authorizationId"), mapOf("authorizationId" to authorizationId), this::mapAuthorization)
            ?: throw WorkflowErrors.notFound("card authorization not found: $authorizationId")

    private fun authorizationForUpdate(authorizationId: String): CardAuthorizationDto =
        jdbc.queryForObject(authorizationSql("WHERE authorization_id = :authorizationId FOR UPDATE"), mapOf("authorizationId" to authorizationId), this::mapAuthorization)
            ?: throw WorkflowErrors.notFound("card authorization not found: $authorizationId")

    private fun existingAuthorization(idempotencyKey: String): CardAuthorizationDto? =
        jdbc.query(authorizationSql("WHERE idempotency_key = :idempotencyKey"), mapOf("idempotencyKey" to idempotencyKey), this::mapAuthorization).firstOrNull()

    private fun authorizationSql(whereClause: String): String =
        """
        SELECT authorization_id, card_id, account_id, amount_minor, currency, merchant_name,
               business_date, status, hold_id, three_ds_authentication_id, created_at
        FROM card_authorizations
        $whereClause
        """.trimIndent()

    private fun mapAuthorization(rs: ResultSet, rowNum: Int): CardAuthorizationDto =
        CardAuthorizationDto(
            authorizationId = rs.getString("authorization_id"),
            cardId = rs.getString("card_id"),
            accountId = rs.getString("account_id"),
            amountMinor = rs.getLong("amount_minor"),
            currency = rs.getString("currency").trim(),
            merchantName = rs.getString("merchant_name"),
            businessDate = rs.getObject("business_date", LocalDate::class.java),
            status = rs.getString("status"),
            holdId = rs.getString("hold_id"),
            threeDsAuthenticationId = rs.getString("three_ds_authentication_id"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
        )

    private fun updateAuthorizationStatus(authorizationId: String, status: String) {
        jdbc.update(
            "UPDATE card_authorizations SET status = :status, updated_at = now() WHERE authorization_id = :authorizationId",
            mapOf("authorizationId" to authorizationId, "status" to status)
        )
    }

    private fun capture(captureId: String): CardCaptureDto =
        jdbc.queryForObject(captureSql("WHERE capture_id = :captureId"), mapOf("captureId" to captureId), this::mapCapture)
            ?: throw WorkflowErrors.notFound("card capture not found: $captureId")

    private fun captureForUpdate(captureId: String): CardCaptureDto =
        jdbc.queryForObject(captureSql("WHERE capture_id = :captureId FOR UPDATE"), mapOf("captureId" to captureId), this::mapCapture)
            ?: throw WorkflowErrors.notFound("card capture not found: $captureId")

    private fun existingCapture(idempotencyKey: String): CardCaptureDto? =
        jdbc.query(captureSql("WHERE idempotency_key = :idempotencyKey"), mapOf("idempotencyKey" to idempotencyKey), this::mapCapture).firstOrNull()

    private fun captureSql(whereClause: String): String =
        """
        SELECT capture_id, authorization_id, card_id, amount_minor, currency,
               ledger_transaction_id, status, created_at, reversed_at
        FROM card_captures
        $whereClause
        """.trimIndent()

    private fun mapCapture(rs: ResultSet, rowNum: Int): CardCaptureDto =
        CardCaptureDto(
            captureId = rs.getString("capture_id"),
            authorizationId = rs.getString("authorization_id"),
            cardId = rs.getString("card_id"),
            amountMinor = rs.getLong("amount_minor"),
            currency = rs.getString("currency").trim(),
            ledgerTransactionId = rs.getString("ledger_transaction_id"),
            status = rs.getString("status"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            reversedAt = rs.getObject("reversed_at", OffsetDateTime::class.java)
        )

    private fun existingThreeDs(idempotencyKey: String): ThreeDsSimulationDto? =
        jdbc.query(
            "SELECT authentication_id, card_id, amount_minor, status, created_at FROM card_3ds_simulations WHERE idempotency_key = :idempotencyKey",
            mapOf("idempotencyKey" to idempotencyKey),
            this::mapThreeDs
        ).firstOrNull()

    private fun threeDs(authenticationId: String): ThreeDsSimulationDto =
        jdbc.queryForObject(
            "SELECT authentication_id, card_id, amount_minor, status, created_at FROM card_3ds_simulations WHERE authentication_id = :authenticationId",
            mapOf("authenticationId" to authenticationId),
            this::mapThreeDs
        ) ?: throw WorkflowErrors.notFound("3DS simulation not found: $authenticationId")

    private fun mapThreeDs(rs: ResultSet, rowNum: Int): ThreeDsSimulationDto =
        ThreeDsSimulationDto(
            authenticationId = rs.getString("authentication_id"),
            cardId = rs.getString("card_id"),
            amountMinor = rs.getLong("amount_minor"),
            status = rs.getString("status"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
        )

    private fun authorizeCustomer(customerId: String) {
        BankingLabAuthContext.requireCustomerOwnership(customerId)
    }

    private fun actor(requestedBy: String?, requestedByRole: String?, defaultRole: String): Actor {
        val principal = BankingLabAuthContext.get()
        val actorId = requestedBy ?: principal?.subject ?: "customer01"
        val actorRole = requestedByRole ?: principal?.roles?.firstOrNull() ?: defaultRole
        BankingLabAuthContext.requireActor(actorId, actorRole)
        return Actor(actorId, actorRole)
    }

    private fun currentActor(): Actor {
        val principal = BankingLabAuthContext.get()
        return Actor(principal?.subject ?: "SYSTEM", principal?.roles?.firstOrNull() ?: "SYSTEM")
    }

    private fun requireTokenizedPan(panToken: String) {
        if (panToken.isBlank() || panToken.matches(Regex("^[0-9]{12,19}$"))) {
            throw WorkflowErrors.validation("panToken must be tokenized and must not be raw PAN")
        }
    }

    private fun requirePositive(value: Long, field: String) {
        if (value <= 0) {
            throw WorkflowErrors.validation("$field must be positive")
        }
    }

    private fun requireReason(value: String?, message: String): String {
        if (value.isNullOrBlank()) {
            throw WorkflowErrors.reasonRequired(message)
        }
        return value
    }

    private fun requireNonBlank(value: String?, field: String): String {
        if (value.isNullOrBlank()) {
            throw WorkflowErrors.validation("$field is required")
        }
        return value
    }

    private fun limitExceeded(limitKind: String, configuredMinor: Long, attemptedMinor: Long, remainingMinor: Long): BankingLabDomainException =
        BankingLabDomainException(
            code = "LIMIT_EXCEEDED",
            status = HttpStatus.CONFLICT,
            domain = "limits",
            invariant = "card authorization must fit configured card limits",
            message = "card authorization exceeds $limitKind limit",
            causeText = "The card authorization amount is above the remaining synthetic card limit.",
            fix = "Retry with an amount within the configured synthetic card limits.",
            details = mapOf(
                "limitKind" to limitKind,
                "configuredMinor" to configuredMinor,
                "attemptedMinor" to attemptedMinor,
                "remainingMinor" to remainingMinor
            )
        )

    private fun appendAudit(
        eventType: String,
        actor: Actor,
        screenId: String,
        businessReferenceId: String,
        customerId: String?,
        accountId: String?,
        reason: String,
        payload: Map<String, Any?>
    ) {
        auditEvents.append(
            eventType = eventType,
            actorType = if (actor.actorRole == "CUSTOMER") "CUSTOMER" else "STAFF",
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

    private data class AccountRow(val accountId: String, val customerId: String, val currency: String, val status: String)
    private data class CardLimit(val dailyLimitMinor: Long, val monthlyLimitMinor: Long, val singleLimitMinor: Long)
    private data class Actor(val actorId: String, val actorRole: String)

    private data class CardRow(
        val cardId: String,
        val customerId: String,
        val accountId: String,
        val panToken: String,
        val panLast4: String,
        val status: String,
        val dailyLimitMinor: Long,
        val monthlyLimitMinor: Long,
        val singleLimitMinor: Long,
        val createdAt: OffsetDateTime
    ) {
        fun toDto(): CardDto =
            CardDto(
                cardId = cardId,
                customerId = customerId,
                accountId = accountId,
                panToken = panToken,
                panLast4 = panLast4,
                status = status,
                dailyLimitMinor = dailyLimitMinor,
                monthlyLimitMinor = monthlyLimitMinor,
                singleLimitMinor = singleLimitMinor,
                createdAt = createdAt
            )
    }

    private companion object {
        const val THREE_DS_REQUIRED_MINOR = 100_000L
    }
}
