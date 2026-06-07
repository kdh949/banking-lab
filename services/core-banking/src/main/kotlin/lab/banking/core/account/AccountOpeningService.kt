package lab.banking.core.account

import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import lab.banking.core.approval.ApprovalBusinessTypes
import lab.banking.core.approval.ApprovalStatus
import lab.banking.core.approval.ApproveApprovalCommand
import lab.banking.core.approval.OperatorApproval
import lab.banking.core.approval.PersistentApprovalService
import lab.banking.core.approval.RejectApprovalCommand
import lab.banking.core.approval.SubmitApprovalCommand
import lab.banking.core.audit.AuditEventAppender
import lab.banking.core.common.BankingLabDomainException
import lab.banking.core.ledger.application.DepositCommand
import lab.banking.core.ledger.application.LedgerCommandService
import lab.banking.core.security.BankingLabAuthContext
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class AccountOpeningService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val approvals: PersistentApprovalService,
    private val auditEvents: AuditEventAppender,
    private val ledgerCommandService: LedgerCommandService
) {
    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun requestOpening(command: AccountOpeningRequestCommand): AccountOpeningRequestResponse {
        BankingLabAuthContext.requireActor(command.requestedBy, command.requestedByRole)
        val normalized = normalizeAndValidate(command)
        requireMakerRole(normalized.requestedByRole)
        val commandHash = requestCommandHash(normalized)
        existingRequestByIdempotencyKey(normalized.idempotencyKey)?.let { existing ->
            requireSameCommandHash(existing.commandHash, commandHash)
            return requestResponse(existing.requestId, replayed = true)
        }
        requireSyntheticCustomer(normalized.customerId)

        val requestId = nextId("AOR")
        val approval = approvals.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.ACCOUNT_OPENING,
                businessReferenceId = requestId,
                requestedBy = normalized.requestedBy,
                requestReason = normalized.reason,
                requestedByRole = normalized.requestedByRole,
                beforeSnapshot = mapOf("accountExists" to false, "syntheticOnly" to true),
                afterSnapshot = mapOf(
                    "requestId" to requestId,
                    "status" to "PENDING_APPROVAL",
                    "customerId" to normalized.customerId,
                    "productCode" to normalized.productCode,
                    "currency" to normalized.currency,
                    "dailyTransferLimitMinor" to normalized.dailyTransferLimitMinor,
                    "singleTransferLimitMinor" to normalized.singleTransferLimitMinor,
                    "initialDepositAmountMinor" to normalized.initialDepositAmountMinor,
                    "initialDepositUsesLedgerCommandService" to (normalized.initialDepositAmountMinor > 0),
                    "realPaymentNetworkCalled" to false,
                    "syntheticOnly" to true
                ),
                screenId = "ACC-201"
            )
        )

        try {
            jdbc.update(
                """
                INSERT INTO account_opening_requests (
                  request_id, idempotency_key, command_hash, status,
                  requested_by, requested_by_role, reason, approval_id,
                  target_customer_id, requested_product_code, requested_account_alias,
                  requested_currency, requested_daily_transfer_limit_minor,
                  requested_single_transfer_limit_minor, requested_initial_deposit_amount_minor,
                  requested_initial_deposit_idempotency_key, requested_business_date, metadata_json
                )
                VALUES (
                  :requestId, :idempotencyKey, :commandHash, 'PENDING_APPROVAL',
                  :requestedBy, :requestedByRole, :reason, :approvalId,
                  :customerId, :productCode, :accountAlias,
                  :currency, :dailyTransferLimitMinor,
                  :singleTransferLimitMinor, :initialDepositAmountMinor,
                  :initialDepositIdempotencyKey, :businessDate, CAST(:metadataJson AS jsonb)
                )
                """.trimIndent(),
                mapOf(
                    "requestId" to requestId,
                    "idempotencyKey" to normalized.idempotencyKey,
                    "commandHash" to commandHash,
                    "requestedBy" to normalized.requestedBy,
                    "requestedByRole" to normalized.requestedByRole,
                    "reason" to normalized.reason,
                    "approvalId" to approval.approvalId,
                    "customerId" to normalized.customerId,
                    "productCode" to normalized.productCode,
                    "accountAlias" to normalized.accountAlias,
                    "currency" to normalized.currency,
                    "dailyTransferLimitMinor" to normalized.dailyTransferLimitMinor,
                    "singleTransferLimitMinor" to normalized.singleTransferLimitMinor,
                    "initialDepositAmountMinor" to normalized.initialDepositAmountMinor,
                    "initialDepositIdempotencyKey" to normalized.initialDepositIdempotencyKey,
                    "businessDate" to normalized.businessDate,
                    "metadataJson" to metadataJson(
                        "operation" to "account-opening-request",
                        "realPaymentNetworkCalled" to false,
                        "initialDepositRequiresLedgerCommandService" to (normalized.initialDepositAmountMinor > 0)
                    )
                )
            )
        } catch (error: DuplicateKeyException) {
            throw idempotencyConflict(error)
        }

        return requestResponse(requestId, replayed = false)
    }

    @Transactional(readOnly = true)
    fun openingRequest(requestId: String): AccountOpeningRequestResponse =
        requestResponse(requestId, replayed = false)

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun approveOpening(
        requestId: String,
        command: AccountOpeningApproveCommand
    ): AccountOpeningReviewResponse {
        BankingLabAuthContext.requireActor(command.approvedBy, command.approvedByRole)
        requireNonBlank(command.approvedBy, "approvedBy")
        requireCheckerRole(command.approvedByRole)
        val request = findRequestForUpdate(requestId)
        if (request.status == "APPROVED" || request.status == "EXECUTED") {
            return AccountOpeningReviewResponse(request.toDto(), approvals.approval(request.approvalId), replayed = true)
        }
        if (request.status == "REJECTED") {
            throw WorkflowErrors.stateViolation("rejected account opening request cannot be approved")
        }

        val approval = approvals.approve(
            request.approvalId,
            ApproveApprovalCommand(
                approvedBy = command.approvedBy,
                approvedByRole = command.approvedByRole,
                screenId = command.screenId
            )
        )
        jdbc.update(
            """
            UPDATE account_opening_requests
            SET status = 'APPROVED',
                approved_by = :approvedBy,
                approved_at = now(),
                updated_at = now()
            WHERE request_id = :requestId
            """.trimIndent(),
            mapOf("requestId" to requestId, "approvedBy" to command.approvedBy)
        )
        return AccountOpeningReviewResponse(findRequest(requestId).toDto(), approval, replayed = false)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun rejectOpening(
        requestId: String,
        command: AccountOpeningRejectCommand
    ): AccountOpeningReviewResponse {
        BankingLabAuthContext.requireActor(command.rejectedBy, command.rejectedByRole)
        requireNonBlank(command.rejectedBy, "rejectedBy")
        requireNonBlank(command.rejectReason, "rejectReason")
        requireCheckerRole(command.rejectedByRole)
        val request = findRequestForUpdate(requestId)
        if (request.status == "REJECTED") {
            return AccountOpeningReviewResponse(request.toDto(), approvals.approval(request.approvalId), replayed = true)
        }
        if (request.status == "APPROVED" || request.status == "EXECUTED") {
            throw WorkflowErrors.stateViolation("approved account opening request cannot be rejected")
        }

        val approval = approvals.reject(
            request.approvalId,
            RejectApprovalCommand(
                rejectedBy = command.rejectedBy,
                rejectedByRole = command.rejectedByRole,
                rejectReason = command.rejectReason,
                screenId = command.screenId
            )
        )
        jdbc.update(
            """
            UPDATE account_opening_requests
            SET status = 'REJECTED',
                rejected_by = :rejectedBy,
                rejected_at = now(),
                reject_reason = :rejectReason,
                updated_at = now()
            WHERE request_id = :requestId
            """.trimIndent(),
            mapOf(
                "requestId" to requestId,
                "rejectedBy" to command.rejectedBy,
                "rejectReason" to command.rejectReason
            )
        )
        return AccountOpeningReviewResponse(findRequest(requestId).toDto(), approval, replayed = false)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun executeOpening(
        requestId: String,
        command: AccountOpeningExecuteCommand
    ): AccountOpeningExecuteResponse {
        BankingLabAuthContext.requireActor(command.executedBy, command.executedByRole)
        validateExecuteCommand(command)
        requireExecutorRole(command.executedByRole)
        val executeHash = executeCommandHash(requestId, command)
        existingExecutedRequestByExecuteIdempotencyKey(command.idempotencyKey)?.let { existing ->
            requireSameCommandHash(existing.executeCommandHash ?: "", executeHash)
            return executeResponse(existing.requestId, replayed = true)
        }

        val request = findRequestForUpdate(requestId)
        if (request.status == "EXECUTED") {
            return executeResponse(requestId, replayed = true)
        }
        if (request.status != "APPROVED") {
            throw WorkflowErrors.stateViolation("account opening execution requires approved maker-checker request")
        }
        val approval = approvals.approval(request.approvalId)
        if (approval.status != ApprovalStatus.APPROVED) {
            throw WorkflowErrors.stateViolation("account opening approval is not approved")
        }
        requireSyntheticCustomer(request.customerId)

        val sequence = nextAccountSequence()
        val accountId = "ACC-SYN-NEW-${sequence.toString().padStart(6, '0')}"
        val accountNo = "LAB-901-${sequence.toString().padStart(6, '0')}"
        var initialDepositLedgerTransactionId: String? = null

        try {
            jdbc.update(
                """
                INSERT INTO accounts (
                  account_id, customer_id, account_no, currency, status,
                  account_class, synthetic_system_account
                )
                VALUES (
                  :accountId, :customerId, :accountNo, :currency, 'ACTIVE',
                  'LIABILITY', FALSE
                )
                """.trimIndent(),
                mapOf(
                    "accountId" to accountId,
                    "customerId" to request.customerId,
                    "accountNo" to accountNo,
                    "currency" to request.requestedCurrency
                )
            )
            jdbc.update(
                """
                INSERT INTO account_limits (
                  account_id, daily_transfer_limit_minor, single_transfer_limit_minor
                )
                VALUES (
                  :accountId, :dailyTransferLimitMinor, :singleTransferLimitMinor
                )
                """.trimIndent(),
                mapOf(
                    "accountId" to accountId,
                    "dailyTransferLimitMinor" to request.requestedDailyTransferLimitMinor,
                    "singleTransferLimitMinor" to request.requestedSingleTransferLimitMinor
                )
            )
            jdbc.update(
                """
                INSERT INTO account_balance_projections (
                  account_id, currency, ledger_balance_minor, available_balance_minor, hold_amount_minor
                )
                VALUES (
                  :accountId, :currency, 0, 0, 0
                )
                """.trimIndent(),
                mapOf("accountId" to accountId, "currency" to request.requestedCurrency)
            )
        } catch (error: DuplicateKeyException) {
            throw accountGenerationConflict(error)
        }

        if (request.requestedInitialDepositAmountMinor > 0) {
            val result = ledgerCommandService.deposit(
                DepositCommand(
                    accountId = accountId,
                    amountMinor = request.requestedInitialDepositAmountMinor,
                    idempotencyKey = request.requestedInitialDepositIdempotencyKey
                        ?: throw WorkflowErrors.validation("initialDepositIdempotencyKey is required"),
                    requestedBy = command.executedBy,
                    requestedChannel = "STAFF_TERMINAL",
                    businessDate = request.requestedBusinessDate,
                    reason = command.reason,
                    currency = request.requestedCurrency,
                    businessReferenceId = requestId
                )
            )
            initialDepositLedgerTransactionId = result.value.id
        }

        val auditEventId = auditEvents.append(
            eventType = "ACCOUNT_OPENING_EXECUTED",
            actorType = "OPERATOR",
            actorId = command.executedBy,
            actorRole = command.executedByRole,
            screenId = "ACC-201",
            businessReferenceId = requestId,
            customerId = request.customerId,
            accountId = accountId,
            reason = command.reason,
            payload = mapOf(
                "requestId" to requestId,
                "approvalId" to request.approvalId,
                "generatedAccountId" to accountId,
                "maskedAccountNo" to maskAccountNo(accountNo),
                "currency" to request.requestedCurrency,
                "initialDepositAmountMinor" to request.requestedInitialDepositAmountMinor,
                "initialDepositLedgerTransactionId" to initialDepositLedgerTransactionId,
                "initialDepositPostedThroughLedgerCommandService" to (request.requestedInitialDepositAmountMinor > 0),
                "realPaymentNetworkCalled" to false,
                "syntheticOnly" to true
            )
        )

        jdbc.update(
            """
            UPDATE account_opening_requests
            SET status = 'EXECUTED',
                generated_account_id = :accountId,
                generated_account_no = :accountNo,
                executed_by = :executedBy,
                executed_by_role = :executedByRole,
                execute_reason = :reason,
                execute_idempotency_key = :idempotencyKey,
                execute_command_hash = :executeCommandHash,
                initial_deposit_ledger_transaction_id = :ledgerTransactionId,
                executed_at = now(),
                updated_at = now(),
                metadata_json = metadata_json || CAST(:metadataJson AS jsonb)
            WHERE request_id = :requestId
            """.trimIndent(),
            mapOf(
                "requestId" to requestId,
                "accountId" to accountId,
                "accountNo" to accountNo,
                "executedBy" to command.executedBy,
                "executedByRole" to command.executedByRole,
                "reason" to command.reason,
                "idempotencyKey" to command.idempotencyKey,
                "executeCommandHash" to executeHash,
                "ledgerTransactionId" to initialDepositLedgerTransactionId,
                "metadataJson" to objectMapper.writeValueAsString(
                    mapOf(
                        "executionAuditEventId" to auditEventId,
                        "balanceSource" to "ledger_postings_projection",
                        "realPaymentNetworkCalled" to false,
                        "syntheticOnly" to true
                    )
                )
            )
        )
        return executeResponse(requestId, replayed = false)
    }

    private fun requestResponse(requestId: String, replayed: Boolean): AccountOpeningRequestResponse {
        val item = findRequest(requestId).toDto()
        return AccountOpeningRequestResponse(item, approvals.approval(item.approvalId), replayed)
    }

    private fun executeResponse(requestId: String, replayed: Boolean): AccountOpeningExecuteResponse {
        val item = findRequest(requestId).toDto()
        val account = if (item.generatedAccountId != null && item.generatedMaskedAccountNo != null) {
            val balance = accountBalance(item.generatedAccountId, item.requestedCurrency)
            CreatedSyntheticAccountDto(
                customerId = item.customerId,
                accountId = item.generatedAccountId,
                maskedAccountNo = item.generatedMaskedAccountNo,
                currency = item.requestedCurrency,
                ledgerBalanceMinor = balance.ledgerBalanceMinor,
                availableBalanceMinor = balance.availableBalanceMinor,
                initialDepositLedgerTransactionId = item.initialDepositLedgerTransactionId
            )
        } else {
            null
        }
        return AccountOpeningExecuteResponse(item, approvals.approval(item.approvalId), account, replayed)
    }

    private fun normalizeAndValidate(command: AccountOpeningRequestCommand): NormalizedAccountOpeningCommand {
        requireNonBlank(command.requestedBy, "requestedBy")
        val role = command.requestedByRole.ifBlank { "BRANCH_STAFF" }.trim()
        val reason = command.reason.trim()
        if (reason.isBlank()) {
            throw WorkflowErrors.reasonRequired("account opening requires a business reason")
        }
        requireNonBlank(command.idempotencyKey, "idempotencyKey")
        val currency = command.currency.trim().uppercase()
        if (!CURRENCY_PATTERN.matches(currency)) {
            throw WorkflowErrors.validation("currency must be a 3-letter ISO-like synthetic currency code")
        }
        val dailyLimit = command.dailyTransferLimitMinor
        val singleLimit = command.singleTransferLimitMinor
        if (dailyLimit < 0 || singleLimit < 0) {
            throw WorkflowErrors.validation("transfer limits must be non-negative")
        }
        if (singleLimit > dailyLimit) {
            throw WorkflowErrors.validation("singleTransferLimitMinor must be less than or equal to dailyTransferLimitMinor")
        }
        if (command.initialDepositAmountMinor < 0) {
            throw WorkflowErrors.validation("initialDepositAmountMinor must be non-negative")
        }
        val initialDepositKey = command.initialDepositIdempotencyKey?.trim()?.takeIf { it.isNotBlank() }
        if (command.initialDepositAmountMinor > 0 && initialDepositKey == null) {
            throw WorkflowErrors.validation("initialDepositIdempotencyKey is required when initialDepositAmountMinor is positive")
        }
        return NormalizedAccountOpeningCommand(
            requestedBy = command.requestedBy.trim(),
            requestedByRole = role,
            reason = reason,
            idempotencyKey = command.idempotencyKey.trim(),
            customerId = requiredTrim(command.customerId, "customerId"),
            productCode = command.productCode.ifBlank { "SYNTHETIC_DEPOSIT" }.trim().uppercase(),
            accountAlias = command.accountAlias?.trim()?.takeIf { it.isNotBlank() },
            currency = currency,
            dailyTransferLimitMinor = dailyLimit,
            singleTransferLimitMinor = singleLimit,
            initialDepositAmountMinor = command.initialDepositAmountMinor,
            initialDepositIdempotencyKey = initialDepositKey,
            businessDate = command.businessDate
        )
    }

    private fun validateExecuteCommand(command: AccountOpeningExecuteCommand) {
        requireNonBlank(command.executedBy, "executedBy")
        requireNonBlank(command.executedByRole, "executedByRole")
        if (command.reason.isBlank()) {
            throw WorkflowErrors.reasonRequired("account opening execution requires a business reason")
        }
        requireNonBlank(command.idempotencyKey, "idempotencyKey")
    }

    private fun requireMakerRole(role: String) {
        if (role !in REQUEST_ROLES) {
            throw WorkflowErrors.authorizationViolation("actor role is not allowed to request account opening")
        }
    }

    private fun requireCheckerRole(role: String) {
        if (role !in CHECKER_ROLES) {
            throw WorkflowErrors.authorizationViolation("actor role is not allowed to approve or reject account opening")
        }
    }

    private fun requireExecutorRole(role: String) {
        if (role !in EXECUTOR_ROLES) {
            throw WorkflowErrors.authorizationViolation("actor role is not allowed to execute account opening")
        }
    }

    private fun requireSyntheticCustomer(customerId: String) {
        val count = jdbc.queryForObject(
            """
            SELECT count(*)
            FROM customers
            WHERE customer_id = :customerId
              AND customer_id <> 'BANK'
            """.trimIndent(),
            mapOf("customerId" to customerId),
            Int::class.java
        ) ?: 0
        if (count == 0) {
            throw WorkflowErrors.notFound("synthetic customer not found for account opening: $customerId")
        }
    }

    private fun existingRequestByIdempotencyKey(idempotencyKey: String): AccountOpeningRequestRecord? =
        jdbc.query(
            requestSql("WHERE idempotency_key = :idempotencyKey"),
            mapOf("idempotencyKey" to idempotencyKey),
            this::mapRequest
        ).firstOrNull()

    private fun existingExecutedRequestByExecuteIdempotencyKey(idempotencyKey: String): AccountOpeningRequestRecord? =
        jdbc.query(
            requestSql("WHERE execute_idempotency_key = :idempotencyKey"),
            mapOf("idempotencyKey" to idempotencyKey),
            this::mapRequest
        ).firstOrNull()

    private fun findRequest(requestId: String): AccountOpeningRequestRecord =
        jdbc.queryForObject(
            requestSql("WHERE request_id = :requestId"),
            mapOf("requestId" to requestId),
            this::mapRequest
        ) ?: throw WorkflowErrors.notFound("account opening request not found: $requestId")

    private fun findRequestForUpdate(requestId: String): AccountOpeningRequestRecord =
        jdbc.queryForObject(
            requestSql("WHERE request_id = :requestId FOR UPDATE"),
            mapOf("requestId" to requestId),
            this::mapRequest
        ) ?: throw WorkflowErrors.notFound("account opening request not found: $requestId")

    private fun requestSql(suffix: String): String =
        """
        SELECT request_id, idempotency_key, command_hash, status,
               requested_by, requested_by_role, reason, approval_id,
               target_customer_id, requested_product_code, requested_account_alias,
               requested_currency, requested_daily_transfer_limit_minor,
               requested_single_transfer_limit_minor, requested_initial_deposit_amount_minor,
               requested_initial_deposit_idempotency_key, requested_business_date,
               generated_account_id, generated_account_no,
               approved_by, approved_at, rejected_by, rejected_at, reject_reason,
               executed_by, executed_by_role, execute_idempotency_key, execute_command_hash,
               initial_deposit_ledger_transaction_id, executed_at, synthetic_only, created_at, updated_at
        FROM account_opening_requests
        $suffix
        """.trimIndent()

    private fun mapRequest(rs: ResultSet, rowNum: Int): AccountOpeningRequestRecord =
        AccountOpeningRequestRecord(
            requestId = rs.getString("request_id"),
            idempotencyKey = rs.getString("idempotency_key"),
            commandHash = rs.getString("command_hash"),
            status = rs.getString("status"),
            requestedBy = rs.getString("requested_by"),
            requestedByRole = rs.getString("requested_by_role"),
            reason = rs.getString("reason"),
            approvalId = rs.getString("approval_id"),
            customerId = rs.getString("target_customer_id"),
            requestedProductCode = rs.getString("requested_product_code"),
            requestedAccountAlias = rs.getString("requested_account_alias"),
            requestedCurrency = rs.getString("requested_currency"),
            requestedDailyTransferLimitMinor = rs.getLong("requested_daily_transfer_limit_minor"),
            requestedSingleTransferLimitMinor = rs.getLong("requested_single_transfer_limit_minor"),
            requestedInitialDepositAmountMinor = rs.getLong("requested_initial_deposit_amount_minor"),
            requestedInitialDepositIdempotencyKey = rs.getString("requested_initial_deposit_idempotency_key"),
            requestedBusinessDate = rs.getObject("requested_business_date", LocalDate::class.java),
            generatedAccountId = rs.getString("generated_account_id"),
            generatedAccountNo = rs.getString("generated_account_no"),
            approvedBy = rs.getString("approved_by"),
            approvedAt = rs.getObject("approved_at", OffsetDateTime::class.java),
            rejectedBy = rs.getString("rejected_by"),
            rejectedAt = rs.getObject("rejected_at", OffsetDateTime::class.java),
            rejectReason = rs.getString("reject_reason"),
            executedBy = rs.getString("executed_by"),
            executedByRole = rs.getString("executed_by_role"),
            executeIdempotencyKey = rs.getString("execute_idempotency_key"),
            executeCommandHash = rs.getString("execute_command_hash"),
            initialDepositLedgerTransactionId = rs.getString("initial_deposit_ledger_transaction_id"),
            executedAt = rs.getObject("executed_at", OffsetDateTime::class.java),
            syntheticOnly = rs.getBoolean("synthetic_only"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java)
        )

    private fun accountBalance(accountId: String, currency: String): AccountBalance =
        jdbc.queryForObject(
            """
            SELECT ledger_balance_minor, available_balance_minor
            FROM account_balance_projections
            WHERE account_id = :accountId
              AND currency = :currency
            """.trimIndent(),
            mapOf("accountId" to accountId, "currency" to currency)
        ) { rs, _ ->
            AccountBalance(
                ledgerBalanceMinor = rs.getLong("ledger_balance_minor"),
                availableBalanceMinor = rs.getLong("available_balance_minor")
            )
        } ?: AccountBalance(0, 0)

    private fun requestCommandHash(command: NormalizedAccountOpeningCommand): String =
        commandHash(
            mapOf(
                "requestedBy" to command.requestedBy,
                "requestedByRole" to command.requestedByRole,
                "reason" to command.reason,
                "customerId" to command.customerId,
                "productCode" to command.productCode,
                "accountAlias" to command.accountAlias,
                "currency" to command.currency,
                "dailyTransferLimitMinor" to command.dailyTransferLimitMinor,
                "singleTransferLimitMinor" to command.singleTransferLimitMinor,
                "initialDepositAmountMinor" to command.initialDepositAmountMinor,
                "initialDepositIdempotencyKey" to command.initialDepositIdempotencyKey,
                "businessDate" to command.businessDate?.toString()
            )
        )

    private fun executeCommandHash(requestId: String, command: AccountOpeningExecuteCommand): String =
        commandHash(
            mapOf(
                "requestId" to requestId,
                "executedBy" to command.executedBy.trim(),
                "executedByRole" to command.executedByRole.trim(),
                "reason" to command.reason.trim()
            )
        )

    private fun commandHash(values: Map<String, Any?>): String =
        sha256(objectMapper.writeValueAsString(values.toSortedMap()))

    private fun metadataJson(vararg entries: Pair<String, Any?>): String =
        objectMapper.writeValueAsString(mapOf("syntheticOnly" to true, *entries))

    private fun requireSameCommandHash(existing: String, requested: String) {
        if (existing != requested) {
            throw BankingLabDomainException(
                code = "IDEMPOTENCY_KEY_CONFLICT",
                status = HttpStatus.CONFLICT,
                domain = "idempotency",
                invariant = "EXTERNALLY_RETRIED_COMMANDS_ARE_IDEMPOTENT",
                message = "idempotency key was reused with a different account opening command",
                causeText = "The same idempotency key already has a different command hash.",
                fix = "Retry with the original request body or generate a new idempotency key for a different command."
            )
        }
    }

    private fun idempotencyConflict(error: DuplicateKeyException): BankingLabDomainException {
        val message = error.message.orEmpty()
        if (message.contains("idempotency", ignoreCase = true)) {
            return BankingLabDomainException(
                code = "IDEMPOTENCY_KEY_CONFLICT",
                status = HttpStatus.CONFLICT,
                domain = "idempotency",
                invariant = "EXTERNALLY_RETRIED_COMMANDS_ARE_IDEMPOTENT",
                message = "idempotency key already exists for another account opening command",
                causeText = "The unique idempotency key has already been persisted.",
                fix = "Retry with the original payload or use a new idempotency key for a distinct command."
            )
        }
        return WorkflowErrors.validation("account opening request conflicts with existing synthetic state")
    }

    private fun accountGenerationConflict(error: DuplicateKeyException): BankingLabDomainException =
        BankingLabDomainException(
            code = "ACCOUNT_OPENING_GENERATION_CONFLICT",
            status = HttpStatus.CONFLICT,
            domain = "account",
            policy = "SYNTHETIC_ACCOUNT_NUMBER_UNIQUENESS",
            message = "generated synthetic account identifier conflicts with existing account state",
            causeText = error.message.orEmpty().ifBlank { "The generated account_id or account_no already exists." },
            fix = "Retry the approved execution with the same idempotency key to confirm replay state, or create a new request if no account was created."
        )

    private fun nextAccountSequence(): Long =
        jdbc.queryForObject(
            "SELECT nextval('synthetic_account_opening_seq')",
            emptyMap<String, Any?>(),
            Long::class.java
        ) ?: error("synthetic account sequence returned null")

    private fun nextId(prefix: String): String =
        "$prefix-${UUID.randomUUID().toString().uppercase()}"

    private fun requiredTrim(value: String, field: String): String =
        value.trim().also {
            if (it.isBlank()) {
                throw WorkflowErrors.validation("$field is required")
            }
        }

    private fun requireNonBlank(value: String?, field: String) {
        if (value.isNullOrBlank()) {
            throw WorkflowErrors.validation("$field is required")
        }
    }

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

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private data class NormalizedAccountOpeningCommand(
        val requestedBy: String,
        val requestedByRole: String,
        val reason: String,
        val idempotencyKey: String,
        val customerId: String,
        val productCode: String,
        val accountAlias: String?,
        val currency: String,
        val dailyTransferLimitMinor: Long,
        val singleTransferLimitMinor: Long,
        val initialDepositAmountMinor: Long,
        val initialDepositIdempotencyKey: String?,
        val businessDate: LocalDate?
    )

    private data class AccountOpeningRequestRecord(
        val requestId: String,
        val idempotencyKey: String,
        val commandHash: String,
        val status: String,
        val requestedBy: String,
        val requestedByRole: String,
        val reason: String,
        val approvalId: String,
        val customerId: String,
        val requestedProductCode: String,
        val requestedAccountAlias: String?,
        val requestedCurrency: String,
        val requestedDailyTransferLimitMinor: Long,
        val requestedSingleTransferLimitMinor: Long,
        val requestedInitialDepositAmountMinor: Long,
        val requestedInitialDepositIdempotencyKey: String?,
        val requestedBusinessDate: LocalDate?,
        val generatedAccountId: String?,
        val generatedAccountNo: String?,
        val approvedBy: String?,
        val approvedAt: OffsetDateTime?,
        val rejectedBy: String?,
        val rejectedAt: OffsetDateTime?,
        val rejectReason: String?,
        val executedBy: String?,
        val executedByRole: String?,
        val executeIdempotencyKey: String?,
        val executeCommandHash: String?,
        val initialDepositLedgerTransactionId: String?,
        val executedAt: OffsetDateTime?,
        val syntheticOnly: Boolean,
        val createdAt: OffsetDateTime,
        val updatedAt: OffsetDateTime
    ) {
        fun toDto(): AccountOpeningRequestDto =
            AccountOpeningRequestDto(
                requestId = requestId,
                idempotencyKey = idempotencyKey,
                status = status,
                requestedBy = requestedBy,
                requestedByRole = requestedByRole,
                reason = reason,
                approvalId = approvalId,
                customerId = customerId,
                requestedProductCode = requestedProductCode,
                requestedAccountAlias = requestedAccountAlias,
                requestedCurrency = requestedCurrency,
                requestedDailyTransferLimitMinor = requestedDailyTransferLimitMinor,
                requestedSingleTransferLimitMinor = requestedSingleTransferLimitMinor,
                requestedInitialDepositAmountMinor = requestedInitialDepositAmountMinor,
                requestedInitialDepositIdempotencyKey = requestedInitialDepositIdempotencyKey,
                requestedBusinessDate = requestedBusinessDate,
                generatedAccountId = generatedAccountId,
                generatedMaskedAccountNo = maskAccountNo(generatedAccountNo),
                approvedBy = approvedBy,
                approvedAt = approvedAt,
                rejectedBy = rejectedBy,
                rejectedAt = rejectedAt,
                rejectReason = rejectReason,
                executedBy = executedBy,
                executedByRole = executedByRole,
                executedAt = executedAt,
                initialDepositLedgerTransactionId = initialDepositLedgerTransactionId,
                syntheticOnly = syntheticOnly,
                createdAt = createdAt,
                updatedAt = updatedAt
            )

        private fun maskAccountNo(accountNo: String?): String? {
            val value = accountNo ?: return null
            val parts = value.split("-")
            if (parts.size >= 3) {
                return "${parts[0]}-${"*".repeat(parts[1].length)}-${parts.last().takeLast(4)}"
            }
            if (value.length <= 6) {
                return "****"
            }
            return "${value.take(4)}-${"*".repeat(maxOf(3, value.length - 10))}-${value.takeLast(4)}"
        }
    }

    private data class AccountBalance(
        val ledgerBalanceMinor: Long,
        val availableBalanceMinor: Long
    )

    companion object {
        private val CURRENCY_PATTERN = Regex("^[A-Z]{3}$")
        private val REQUEST_ROLES = setOf("BRANCH_STAFF", "BRANCH_MANAGER", "COMPLIANCE_MANAGER")
        private val CHECKER_ROLES = setOf("BRANCH_MANAGER", "COMPLIANCE_MANAGER")
        private val EXECUTOR_ROLES = setOf("BRANCH_STAFF", "BRANCH_MANAGER", "OPS_MANAGER")
    }
}
