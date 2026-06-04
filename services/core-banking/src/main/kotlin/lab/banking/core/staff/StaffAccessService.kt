package lab.banking.core.staff

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import lab.banking.core.audit.AuditEventAppender
import lab.banking.core.aml.AmlCaseService
import lab.banking.core.approval.ApprovalBusinessTypes
import lab.banking.core.approval.ApproveApprovalCommand
import lab.banking.core.approval.OperatorApproval
import lab.banking.core.approval.PersistentApprovalService
import lab.banking.core.approval.RejectApprovalCommand
import lab.banking.core.approval.SubmitApprovalCommand
import lab.banking.core.complaint.ComplaintCaseService
import lab.banking.core.fds.FdsCaseService
import lab.banking.core.ledger.application.LedgerCommandService
import lab.banking.core.ledger.application.ReversalCommand
import lab.banking.core.ledger.domain.LedgerCommandResult
import lab.banking.core.reconciliation.ReconciliationOpsService
import lab.banking.core.security.BankingLabAuthContext
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.dao.TransientDataAccessException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@Service
class StaffAccessService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val auditEvents: AuditEventAppender,
    private val approvals: PersistentApprovalService,
    private val complaintCaseService: ComplaintCaseService,
    private val fdsCaseService: FdsCaseService,
    private val amlCaseService: AmlCaseService,
    private val reconciliationOpsService: ReconciliationOpsService,
    private val ledgerCommandService: LedgerCommandService,
    private val transactionManager: PlatformTransactionManager
) {
    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun searchCustomers(query: String, reason: String?): StaffAccessListResponse<MaskedCustomerDto> {
        requireReason(reason, "CUSTOMER_SEARCH requires a business reason")
        val customers = jdbc.query(
            """
            SELECT customer_id, customer_name, customer_phone, customer_address, customer_grade, risk_grade
            FROM customers
            WHERE :query = ''
               OR customer_id ILIKE :queryPattern
               OR customer_name ILIKE :queryPattern
            ORDER BY customer_id
            """.trimIndent(),
            mapOf("query" to query, "queryPattern" to "%$query%"),
            this::mapCustomer
        )
        val auditEventId = appendAudit(
            eventType = "CUSTOMER_SEARCH",
            actorId = "branch01",
            actorRole = "BRANCH_STAFF",
            screenId = "CST-001",
            customerId = null,
            accountId = null,
            reason = reason,
            payload = mapOf("query" to query, "resultCount" to customers.size, "syntheticOnly" to true)
        )
        return StaffAccessListResponse(auditEventId = auditEventId, items = customers.map(::maskedCustomer))
    }

    fun customerDetail(customerId: String, reason: String?): StaffAccessItemResponse<StaffCustomerDetailDto> =
        runSerializableStaffAccess {
            customerDetailInTransaction(customerId, reason)
        }

    private fun customerDetailInTransaction(customerId: String, reason: String?): StaffAccessItemResponse<StaffCustomerDetailDto> {
        requireReason(reason, "CUSTOMER_DETAIL_VIEW requires a business reason")
        val customer = customer(customerId)
        val auditEventId = appendAudit(
            eventType = "CUSTOMER_DETAIL_VIEW",
            actorId = "branch01",
            actorRole = "BRANCH_STAFF",
            screenId = "CST-002",
            customerId = customerId,
            accountId = null,
            reason = reason,
            payload = mapOf("piiExposure" to "MASKED", "syntheticOnly" to true)
        )
        appendMaskingLog(
            auditEventId = auditEventId,
            screenId = "CST-002",
            customerId = customerId,
            accountId = null,
            maskingPolicy = "DEFAULT_STAFF_MASKING",
            accessLevel = "MASKED",
            reason = reason.orEmpty()
        )
        return StaffAccessItemResponse(auditEventId = auditEventId, item = customerDetail(customer, unmasked = false))
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun searchAccounts(customerId: String?, accountId: String?, reason: String?): StaffAccessListResponse<StaffAccountDto> {
        requireReason(reason, "ACCOUNT_VIEW requires a business reason")
        val filters = mutableListOf<String>()
        val params = mutableMapOf<String, Any?>()
        if (!customerId.isNullOrBlank()) {
            filters += "a.customer_id = :customerId"
            params["customerId"] = customerId
        }
        if (!accountId.isNullOrBlank()) {
            filters += "a.account_id = :accountId"
            params["accountId"] = accountId
        }
        val whereClause = if (filters.isEmpty()) "" else "WHERE ${filters.joinToString(" AND ")}"
        val accounts = jdbc.query(
            """
            SELECT a.customer_id, a.account_id, a.account_no, a.status, a.currency,
                   COALESCE(p.ledger_balance_minor, 0) AS ledger_balance_minor,
                   COALESCE(p.available_balance_minor, 0) AS available_balance_minor,
                   COALESCE(p.hold_amount_minor, 0) AS hold_amount_minor
            FROM accounts a
            LEFT JOIN account_balance_projections p
              ON p.account_id = a.account_id
             AND p.currency = a.currency
            $whereClause
            ORDER BY a.account_id
            """.trimIndent(),
            params,
            this::mapAccount
        )
        val auditEventId = appendAudit(
            eventType = "ACCOUNT_VIEW",
            actorId = "branch01",
            actorRole = "BRANCH_STAFF",
            screenId = "ACC-101",
            customerId = customerId,
            accountId = accountId,
            reason = reason,
            payload = mapOf("resultCount" to accounts.size, "syntheticOnly" to true)
        )
        return StaffAccessListResponse(auditEventId = auditEventId, items = accounts)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun searchTransactions(accountId: String?, reason: String?): StaffAccessListResponse<StaffTransactionDto> {
        requireReason(reason, "TRANSACTION_VIEW requires a business reason")
        if (accountId.isNullOrBlank()) {
            throw WorkflowErrors.validation("accountId is required")
        }
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
            this::mapTransaction
        )
        val auditEventId = appendAudit(
            eventType = "TRANSACTION_VIEW",
            actorId = "branch01",
            actorRole = "BRANCH_STAFF",
            screenId = "LED-101",
            customerId = null,
            accountId = accountId,
            reason = reason,
            payload = mapOf("resultCount" to transactions.size, "syntheticOnly" to true)
        )
        return StaffAccessListResponse(auditEventId = auditEventId, items = transactions)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun transferLimits(customerId: String, reason: String?): StaffAccessListResponse<StaffTransferLimitDto> {
        requireReason(reason, "LIMIT_VIEW requires a business reason")
        customer(customerId)
        val limits = jdbc.query(
            transferLimitSql(
                """
                WHERE a.customer_id = :customerId
                ORDER BY a.account_id
                """.trimIndent()
            ),
            mapOf("customerId" to customerId),
            this::mapTransferLimit
        )
        val auditEventId = appendAudit(
            eventType = "LIMIT_VIEW",
            actorId = "branch01",
            actorRole = "BRANCH_STAFF",
            screenId = "LIM-101",
            customerId = customerId,
            accountId = null,
            reason = reason,
            payload = mapOf("resultCount" to limits.size, "syntheticOnly" to true)
        )
        return StaffAccessListResponse(auditEventId = auditEventId, items = limits)
    }

    fun unmaskCustomer(command: PiiUnmaskCommand): StaffUnmaskResponse =
        runSerializableStaffAccess {
            unmaskCustomerInTransaction(command)
        }

    private fun unmaskCustomerInTransaction(command: PiiUnmaskCommand): StaffUnmaskResponse {
        BankingLabAuthContext.requireActor(command.requestedBy, command.actorRole)
        requireReason(command.reason, "PII_UNMASK_REQUESTED requires a business reason")
        val customer = customer(command.customerId)
        val actorRole = command.actorRole ?: "BRANCH_MANAGER"
        if (actorRole !in setOf("BRANCH_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER")) {
            throw WorkflowErrors.authorizationViolation("actor role cannot unmask PII")
        }
        val auditEventId = appendAudit(
            eventType = "PII_UNMASK_REQUESTED",
            actorId = command.requestedBy ?: "manager01",
            actorRole = actorRole,
            screenId = command.screenId ?: "CST-002",
            customerId = command.customerId,
            accountId = null,
            reason = command.reason,
            payload = mapOf("ttlSeconds" to 300, "scope" to "SINGLE_CUSTOMER", "syntheticOnly" to true)
        )
        appendMaskingLog(
            auditEventId = auditEventId,
            screenId = command.screenId ?: "CST-002",
            customerId = command.customerId,
            accountId = null,
            maskingPolicy = "MANAGER_TIMEBOXED_UNMASK",
            accessLevel = "UNMASK_APPROVED",
            reason = command.reason.orEmpty()
        )
        return StaffUnmaskResponse(
            auditEventId = auditEventId,
            expiresInSeconds = 300,
            item = customerDetail(customer, unmasked = true)
        )
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun requestCustomerInfoChange(customerId: String, command: CustomerInfoChangeCommand): CustomerInfoChangeResponse {
        BankingLabAuthContext.requireActor(command.requestedBy, command.requestedByRole)
        requireReason(command.reason, "CUSTOMER_INFO_CHANGE requires a business reason")
        val customer = customer(customerId)
        val afterSnapshot = supportedCustomerInfoChange(command.afterSnapshot)
        if (afterSnapshot.isEmpty()) {
            throw WorkflowErrors.validation("afterSnapshot must include a supported customer field")
        }
        val approval = approvals.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.CUSTOMER_INFO_CHANGE,
                businessReferenceId = customerId,
                requestedBy = command.requestedBy ?: "branch01",
                requestedByRole = command.requestedByRole ?: "BRANCH_STAFF",
                requestReason = command.reason,
                beforeSnapshot = mapOf(
                    "phone" to customer.phone,
                    "address" to customer.address,
                    "customerGrade" to customer.customerGrade
                ),
                afterSnapshot = afterSnapshot,
                screenId = "CST-103"
            )
        )
        return CustomerInfoChangeResponse(item = approval, customer = customerDetail(customer, unmasked = false))
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun requestAccountHold(accountId: String, command: AccountHoldRequestCommand): AccountHoldRequestResponse {
        val requestedBy = command.requestedBy ?: "manager01"
        val requestedRole = command.requestedByRole ?: "BRANCH_MANAGER"
        BankingLabAuthContext.requireActor(requestedBy, requestedRole)
        requireRole(requestedRole, ACCOUNT_HOLD_REQUEST_ROLES, "actor role cannot request account hold")
        requireReason(command.reason, "ACCOUNT_HOLD requires a business reason")
        val idempotencyKey = requireField(command.idempotencyKey, "idempotencyKey")
        existingAccountHoldRequest(ApprovalBusinessTypes.ACCOUNT_HOLD, requestedBy, idempotencyKey)
            ?.let { return accountHoldResponse(it) }

        val account = account(accountId, forUpdate = true)
        if (account.status != "ACTIVE") {
            throw WorkflowErrors.stateViolation("only active accounts can be held")
        }
        val holdAmount = command.holdAmountMinor ?: account.availableBalanceMinor
        if (holdAmount <= 0) {
            throw WorkflowErrors.validation("holdAmountMinor must be positive")
        }
        if (holdAmount > account.availableBalanceMinor) {
            throw WorkflowErrors.validation("holdAmountMinor cannot exceed available balance")
        }
        val requestId = nextAccountHoldRequestId()
        val reasonCode = requireField(command.reasonCode, "reasonCode")
        val metadata = mapOf("description" to command.description.orEmpty(), "syntheticOnly" to true)
        val approval = approvals.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.ACCOUNT_HOLD,
                businessReferenceId = requestId,
                requestedBy = requestedBy,
                requestedByRole = requestedRole,
                requestReason = command.reason,
                beforeSnapshot = account.snapshot(),
                afterSnapshot = mapOf(
                    "accountId" to account.accountId,
                    "targetStatus" to "HOLD",
                    "reasonCode" to reasonCode,
                    "holdAmountMinor" to holdAmount,
                    "description" to command.description.orEmpty()
                ),
                screenId = "ACC-103"
            )
        )
        insertAccountHoldRequest(
            requestId = requestId,
            businessType = ApprovalBusinessTypes.ACCOUNT_HOLD,
            account = account,
            requestedBy = requestedBy,
            requestedRole = requestedRole,
            reason = command.reason.orEmpty(),
            reasonCode = reasonCode,
            holdAmountMinor = holdAmount,
            approvalId = approval.approvalId,
            idempotencyKey = idempotencyKey,
            metadata = metadata
        )
        return AccountHoldRequestResponse(
            item = accountHoldRequest(requestId),
            approval = approval,
            account = account.toDto()
        )
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun requestAccountHoldRelease(accountId: String, command: AccountHoldReleaseRequestCommand): AccountHoldRequestResponse {
        val requestedBy = command.requestedBy ?: "ops01"
        val requestedRole = command.requestedByRole ?: "OPS_MANAGER"
        BankingLabAuthContext.requireActor(requestedBy, requestedRole)
        requireRole(requestedRole, ACCOUNT_HOLD_RELEASE_REQUEST_ROLES, "actor role cannot request account hold release")
        requireReason(command.reason, "ACCOUNT_HOLD_RELEASE requires a business reason")
        val idempotencyKey = requireField(command.idempotencyKey, "idempotencyKey")
        existingAccountHoldRequest(ApprovalBusinessTypes.ACCOUNT_HOLD_RELEASE, requestedBy, idempotencyKey)
            ?.let { return accountHoldResponse(it) }

        val account = account(accountId, forUpdate = true)
        if (account.status != "HOLD" || account.holdAmountMinor <= 0) {
            throw WorkflowErrors.stateViolation("only held accounts can be released")
        }
        val releaseAmount = command.holdAmountMinor ?: account.holdAmountMinor
        if (releaseAmount <= 0) {
            throw WorkflowErrors.validation("holdAmountMinor must be positive")
        }
        if (releaseAmount != account.holdAmountMinor) {
            throw WorkflowErrors.validation("account hold release must release the full held amount")
        }
        val requestId = nextAccountHoldRequestId()
        val reasonCode = command.reasonCode ?: "CUSTOMER_REQUEST"
        val metadata = mapOf("description" to command.description.orEmpty(), "syntheticOnly" to true)
        val approval = approvals.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.ACCOUNT_HOLD_RELEASE,
                businessReferenceId = requestId,
                requestedBy = requestedBy,
                requestedByRole = requestedRole,
                requestReason = command.reason,
                beforeSnapshot = account.snapshot(),
                afterSnapshot = mapOf(
                    "accountId" to account.accountId,
                    "targetStatus" to "ACTIVE",
                    "reasonCode" to reasonCode,
                    "holdAmountMinor" to releaseAmount,
                    "description" to command.description.orEmpty()
                ),
                screenId = "ACC-104"
            )
        )
        insertAccountHoldRequest(
            requestId = requestId,
            businessType = ApprovalBusinessTypes.ACCOUNT_HOLD_RELEASE,
            account = account,
            requestedBy = requestedBy,
            requestedRole = requestedRole,
            reason = command.reason.orEmpty(),
            reasonCode = reasonCode,
            holdAmountMinor = releaseAmount,
            approvalId = approval.approvalId,
            idempotencyKey = idempotencyKey,
            metadata = metadata
        )
        return AccountHoldRequestResponse(
            item = accountHoldRequest(requestId),
            approval = approval,
            account = account.toDto()
        )
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun requestTransferLimitChange(accountId: String, command: TransferLimitChangeRequestCommand): TransferLimitChangeRequestResponse {
        val requestedBy = command.requestedBy ?: "manager01"
        val requestedRole = command.requestedByRole ?: "BRANCH_MANAGER"
        BankingLabAuthContext.requireActor(requestedBy, requestedRole)
        requireRole(requestedRole, TRANSFER_LIMIT_CHANGE_REQUEST_ROLES, "actor role cannot request transfer limit change")
        requireReason(command.reason, "TRANSFER_LIMIT_CHANGE requires a business reason")
        val idempotencyKey = requireField(command.idempotencyKey, "idempotencyKey")
        existingTransferLimitChangeRequest(requestedBy, idempotencyKey)
            ?.let { return transferLimitChangeResponse(it) }

        val current = accountLimit(accountId, forUpdate = true)
        if (current.accountStatus != "ACTIVE") {
            throw WorkflowErrors.stateViolation("only active accounts can change transfer limits")
        }
        val requestedDaily = command.dailyTransferLimitMinor ?: current.dailyTransferLimitMinor
        val requestedSingle = command.singleTransferLimitMinor ?: current.singleTransferLimitMinor
        validateTransferLimitChange(current, requestedDaily, requestedSingle)

        val requestId = nextTransferLimitChangeRequestId()
        val reasonCode = command.reasonCode ?: "CUSTOMER_REQUEST"
        val metadata = mapOf("description" to command.description.orEmpty(), "syntheticOnly" to true)
        val approval = approvals.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.TRANSFER_LIMIT_CHANGE,
                businessReferenceId = requestId,
                requestedBy = requestedBy,
                requestedByRole = requestedRole,
                requestReason = command.reason,
                beforeSnapshot = current.snapshot(),
                afterSnapshot = mapOf(
                    "accountId" to current.accountId,
                    "dailyTransferLimitMinor" to requestedDaily,
                    "singleTransferLimitMinor" to requestedSingle,
                    "reasonCode" to reasonCode,
                    "description" to command.description.orEmpty()
                ),
                screenId = "LIM-102"
            )
        )
        insertTransferLimitChangeRequest(
            requestId = requestId,
            current = current,
            requestedBy = requestedBy,
            requestedRole = requestedRole,
            reason = command.reason.orEmpty(),
            reasonCode = reasonCode,
            requestedDaily = requestedDaily,
            requestedSingle = requestedSingle,
            approvalId = approval.approvalId,
            idempotencyKey = idempotencyKey,
            metadata = metadata
        )
        return TransferLimitChangeRequestResponse(
            item = transferLimitChangeRequest(requestId),
            approval = approval,
            limit = current.toDto()
        )
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun requestCustomerKycReview(customerId: String, command: CustomerKycReviewRequestCommand): CustomerKycReviewRequestResponse {
        val requestedBy = command.requestedBy ?: "manager01"
        val requestedRole = command.requestedByRole ?: "BRANCH_MANAGER"
        BankingLabAuthContext.requireActor(requestedBy, requestedRole)
        requireRole(requestedRole, CUSTOMER_KYC_REVIEW_REQUEST_ROLES, "actor role cannot request customer KYC review")
        requireReason(command.reason, "CUSTOMER_KYC_REVIEW requires a business reason")
        val idempotencyKey = requireField(command.idempotencyKey, "idempotencyKey")
        existingCustomerKycReviewRequest(requestedBy, idempotencyKey)
            ?.let { return customerKycReviewResponse(it) }

        customer(customerId)
        val current = customerKycProfile(customerId, forUpdate = true)
        val requestId = nextCustomerKycReviewRequestId()
        val reasonCode = command.reasonCode ?: "PERIODIC_RECONFIRMATION"
        val reviewTrigger = command.reviewTrigger ?: "PERIODIC_REVIEW"
        val requestedKycStatus = "REVIEW_REQUIRED"
        val metadata = mapOf(
            "description" to command.description.orEmpty(),
            "realKycProviderCalled" to false,
            "syntheticOnly" to true
        )
        val approval = approvals.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.CUSTOMER_KYC_REVIEW,
                businessReferenceId = requestId,
                requestedBy = requestedBy,
                requestedByRole = requestedRole,
                requestReason = command.reason,
                beforeSnapshot = current.snapshot(),
                afterSnapshot = mapOf(
                    "customerId" to customerId,
                    "kycStatus" to requestedKycStatus,
                    "reasonCode" to reasonCode,
                    "reviewTrigger" to reviewTrigger,
                    "description" to command.description.orEmpty(),
                    "realKycProviderCalled" to false
                ),
                screenId = "KYC-101"
            )
        )
        insertCustomerKycReviewRequest(
            requestId = requestId,
            customerId = customerId,
            requestedBy = requestedBy,
            requestedRole = requestedRole,
            reason = command.reason.orEmpty(),
            reasonCode = reasonCode,
            reviewTrigger = reviewTrigger,
            previousKycStatus = current.kycStatus,
            requestedKycStatus = requestedKycStatus,
            approvalId = approval.approvalId,
            idempotencyKey = idempotencyKey,
            metadata = metadata
        )
        return CustomerKycReviewRequestResponse(
            item = customerKycReviewRequest(requestId),
            approval = approval,
            kycProfile = current.toDto()
        )
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun requestFeeWaiver(accountId: String, command: FeeWaiverRequestCommand): FeeWaiverRequestResponse {
        val requestedBy = command.requestedBy ?: "manager01"
        val requestedRole = command.requestedByRole ?: "BRANCH_MANAGER"
        BankingLabAuthContext.requireActor(requestedBy, requestedRole)
        requireRole(requestedRole, FEE_WAIVER_REQUEST_ROLES, "actor role cannot request fee waiver")
        requireReason(command.reason, "FEE_WAIVER requires a business reason")
        val idempotencyKey = requireField(command.idempotencyKey, "idempotencyKey")
        existingFeeWaiverRequest(requestedBy, idempotencyKey)
            ?.let { return feeWaiverResponse(it) }

        val account = account(accountId, forUpdate = true)
        if (account.status != "ACTIVE") {
            throw WorkflowErrors.stateViolation("only active accounts can request fee waiver")
        }
        val feeCode = requireField(command.feeCode, "feeCode").uppercase()
        val waivedAmount = command.waivedAmountMinor ?: throw WorkflowErrors.validation("waivedAmountMinor is required")
        if (waivedAmount <= 0) {
            throw WorkflowErrors.validation("waivedAmountMinor must be positive")
        }
        val currency = (command.currency ?: account.currency).uppercase()
        if (currency != account.currency) {
            throw WorkflowErrors.validation("currency must match account currency")
        }
        val targetTransactionId = command.targetTransactionId?.takeIf { it.isNotBlank() }
        if (targetTransactionId != null && !ledgerPostingBelongsToAccount(targetTransactionId, accountId)) {
            throw WorkflowErrors.validation("targetTransactionId must belong to the target account")
        }
        val requestId = nextFeeWaiverRequestId()
        val reasonCode = command.reasonCode ?: "CUSTOMER_SERVICE_RECOVERY"
        val metadata = mapOf(
            "description" to command.description.orEmpty(),
            "feePostingCreated" to false,
            "syntheticOnly" to true
        )
        val approval = approvals.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.FEE_WAIVER,
                businessReferenceId = requestId,
                requestedBy = requestedBy,
                requestedByRole = requestedRole,
                requestReason = command.reason,
                beforeSnapshot = account.snapshot(),
                afterSnapshot = mapOf(
                    "accountId" to account.accountId,
                    "feeCode" to feeCode,
                    "waivedAmountMinor" to waivedAmount,
                    "currency" to currency,
                    "reasonCode" to reasonCode,
                    "targetTransactionId" to targetTransactionId,
                    "description" to command.description.orEmpty(),
                    "feePostingCreated" to false,
                    "ledgerSourceRowsMutated" to false
                ),
                screenId = "FEE-102"
            )
        )
        insertFeeWaiverRequest(
            requestId = requestId,
            account = account,
            requestedBy = requestedBy,
            requestedRole = requestedRole,
            reason = command.reason.orEmpty(),
            reasonCode = reasonCode,
            feeCode = feeCode,
            waivedAmountMinor = waivedAmount,
            currency = currency,
            targetTransactionId = targetTransactionId,
            approvalId = approval.approvalId,
            idempotencyKey = idempotencyKey,
            metadata = metadata
        )
        return FeeWaiverRequestResponse(
            item = feeWaiverRequest(requestId),
            approval = approval,
            account = account.toDto()
        )
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun requestTransactionCorrection(
        transactionId: String,
        command: TransactionCorrectionRequestCommand
    ): TransactionCorrectionRequestResponse {
        val requestedBy = command.requestedBy ?: "manager01"
        val requestedRole = command.requestedByRole ?: "BRANCH_MANAGER"
        BankingLabAuthContext.requireActor(requestedBy, requestedRole)
        requireRole(requestedRole, TRANSACTION_CORRECTION_REQUEST_ROLES, "actor role cannot request transaction correction")
        requireReason(command.reason, "TRANSACTION_CORRECTION requires a business reason")
        val idempotencyKey = requireField(command.idempotencyKey, "idempotencyKey")
        existingTransactionCorrectionRequest(requestedBy, idempotencyKey)
            ?.let { return transactionCorrectionResponse(it) }

        val correctionType = (command.correctionType ?: "REVERSAL").uppercase()
        if (correctionType != "REVERSAL") {
            throw WorkflowErrors.validation("only REVERSAL transaction correction is supported in this phase")
        }
        if (transactionAlreadyReversed(transactionId)) {
            throw WorkflowErrors.stateViolation("target transaction is already reversed")
        }
        if (pendingTransactionCorrectionExists(transactionId)) {
            throw WorkflowErrors.stateViolation("transaction correction is already pending for target transaction")
        }
        val target = transactionCorrectionTarget(transactionId, command.targetAccountId?.takeIf { it.isNotBlank() })
        val account = account(target.accountId, forUpdate = true)
        if (account.status != "ACTIVE") {
            throw WorkflowErrors.stateViolation("only active accounts can request transaction correction")
        }

        val requestId = nextTransactionCorrectionRequestId()
        val reasonCode = command.reasonCode ?: "CUSTOMER_DISPUTE"
        val correctionBusinessDate = command.businessDate ?: LocalDate.now()
        val metadata = mapOf(
            "description" to command.description.orEmpty(),
            "originalTransactionType" to target.transactionType,
            "originalBusinessDate" to target.businessDate.toString(),
            "targetPostingAmountMinor" to target.accountPostingAmountMinor,
            "reversalPostingRequired" to true,
            "ledgerSourceRowsMutated" to false,
            "syntheticOnly" to true
        )
        val approval = approvals.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.TRANSACTION_CORRECTION,
                businessReferenceId = requestId,
                requestedBy = requestedBy,
                requestedByRole = requestedRole,
                requestReason = command.reason,
                beforeSnapshot = target.snapshot(),
                afterSnapshot = mapOf(
                    "requestId" to requestId,
                    "targetTransactionId" to target.transactionId,
                    "targetAccountId" to target.accountId,
                    "correctionType" to correctionType,
                    "correctionBusinessDate" to correctionBusinessDate.toString(),
                    "reasonCode" to reasonCode,
                    "description" to command.description.orEmpty(),
                    "reversalPostingRequired" to true,
                    "ledgerSourceRowsMutated" to false
                ),
                screenId = "LED-103"
            )
        )
        insertTransactionCorrectionRequest(
            requestId = requestId,
            target = target,
            requestedBy = requestedBy,
            requestedRole = requestedRole,
            reason = command.reason.orEmpty(),
            reasonCode = reasonCode,
            correctionType = correctionType,
            correctionBusinessDate = correctionBusinessDate,
            approvalId = approval.approvalId,
            idempotencyKey = idempotencyKey,
            metadata = metadata
        )
        return TransactionCorrectionRequestResponse(
            item = transactionCorrectionRequest(requestId),
            approval = approval,
            account = account.toDto()
        )
    }

    fun approveStaffRequest(approvalId: String, command: ApproveApprovalCommand): StaffApprovalExecutionResponse {
        return runSerializableApprovalExecution {
            approveStaffRequestInTransaction(approvalId, command)
        }
    }

    private fun approveStaffRequestInTransaction(approvalId: String, command: ApproveApprovalCommand): StaffApprovalExecutionResponse {
        BankingLabAuthContext.requireActor(command.approvedBy, command.approvedByRole)
        val pendingApproval = approvals.approval(approvalId)
        requireCheckerRole(pendingApproval.businessType, command.approvedByRole)
        val approval = approvals.approve(approvalId, command)
        val customerExecuted = if (approval.businessType == ApprovalBusinessTypes.CUSTOMER_INFO_CHANGE) {
            applyCustomerInfoChange(approval.businessReferenceId, approval.afterSnapshot, command)
            true
        } else {
            false
        }
        val customer = if (approval.businessType == ApprovalBusinessTypes.CUSTOMER_INFO_CHANGE) {
            customerDetail(customer(approval.businessReferenceId), unmasked = false)
        } else {
            null
        }
        val complaint = if (approval.businessType == ApprovalBusinessTypes.COMPLAINT_ANSWER_SEND) {
            complaintCaseService.applyApprovedAnswer(approval)
        } else {
            null
        }
        val fdsExecution = if (approval.businessType == ApprovalBusinessTypes.FDS_RELEASE || approval.businessType == ApprovalBusinessTypes.FDS_BLOCK) {
            fdsCaseService.applyApprovedDecision(approval)
        } else {
            null
        }
        val amlCase = if (approval.businessType == ApprovalBusinessTypes.AML_CASE_CLOSE) {
            amlCaseService.applyApprovedClosure(approval)
        } else {
            null
        }
        val reconciliationExecution = if (approval.businessType == ApprovalBusinessTypes.RECONCILIATION_ADJUSTMENT) {
            reconciliationOpsService.applyApprovedAdjustment(approval)
        } else {
            null
        }
        val accountHoldExecution = if (
            approval.businessType == ApprovalBusinessTypes.ACCOUNT_HOLD ||
            approval.businessType == ApprovalBusinessTypes.ACCOUNT_HOLD_RELEASE
        ) {
            applyApprovedAccountHoldRequest(approval, command)
        } else {
            null
        }
        val transferLimitExecution = if (approval.businessType == ApprovalBusinessTypes.TRANSFER_LIMIT_CHANGE) {
            applyApprovedTransferLimitChangeRequest(approval, command)
        } else {
            null
        }
        val kycExecution = if (approval.businessType == ApprovalBusinessTypes.CUSTOMER_KYC_REVIEW) {
            applyApprovedCustomerKycReviewRequest(approval, command)
        } else {
            null
        }
        val feeWaiverExecution = if (approval.businessType == ApprovalBusinessTypes.FEE_WAIVER) {
            applyApprovedFeeWaiverRequest(approval, command)
        } else {
            null
        }
        val transactionCorrectionExecution = if (approval.businessType == ApprovalBusinessTypes.TRANSACTION_CORRECTION) {
            applyApprovedTransactionCorrectionRequest(approval, command)
        } else {
            null
        }
        return StaffApprovalExecutionResponse(
            item = approval,
            executed = customerExecuted ||
                complaint != null ||
                fdsExecution != null ||
                amlCase != null ||
                reconciliationExecution != null ||
                accountHoldExecution != null ||
                transferLimitExecution != null ||
                kycExecution != null ||
                feeWaiverExecution != null ||
                transactionCorrectionExecution != null,
            customer = customer,
            account = accountHoldExecution?.second ?: feeWaiverExecution?.second ?: transactionCorrectionExecution?.second,
            accountHoldRequest = accountHoldExecution?.first,
            transferLimit = transferLimitExecution?.second,
            transferLimitChangeRequest = transferLimitExecution?.first,
            kycProfile = kycExecution?.second,
            kycReviewRequest = kycExecution?.first,
            feeWaiverRequest = feeWaiverExecution?.first,
            transactionCorrectionRequest = transactionCorrectionExecution?.first,
            complaint = complaint,
            fdsCase = fdsExecution?.item,
            amlCase = amlCase,
            reconciliationItem = reconciliationExecution?.item,
            ledgerTransaction = fdsExecution?.ledgerTransaction
                ?: reconciliationExecution?.ledgerTransaction
                ?: transactionCorrectionExecution?.third
        )
    }

    fun rejectStaffRequest(approvalId: String, command: RejectApprovalCommand): StaffApprovalRejectionResponse {
        return runSerializableApprovalExecution {
            rejectStaffRequestInTransaction(approvalId, command)
        }
    }

    private fun rejectStaffRequestInTransaction(approvalId: String, command: RejectApprovalCommand): StaffApprovalRejectionResponse {
        BankingLabAuthContext.requireActor(command.rejectedBy, command.rejectedByRole)
        val pendingApproval = approvals.approval(approvalId)
        requireCheckerRole(pendingApproval.businessType, command.rejectedByRole)
        if (pendingApproval.requestedBy == command.rejectedBy) {
            throw WorkflowErrors.selfApprovalRejected()
        }
        if (
            pendingApproval.businessType != ApprovalBusinessTypes.FEE_WAIVER &&
            pendingApproval.businessType != ApprovalBusinessTypes.TRANSACTION_CORRECTION
        ) {
            throw WorkflowErrors.stateViolation("staff rejection route does not support ${pendingApproval.businessType}")
        }
        val approval = approvals.reject(approvalId, command)
        val feeWaiverRequest = if (pendingApproval.businessType == ApprovalBusinessTypes.FEE_WAIVER) {
            val request = feeWaiverRequestForUpdate(pendingApproval.businessReferenceId)
            if (request.approvalId != pendingApproval.approvalId || request.businessType != pendingApproval.businessType) {
                throw WorkflowErrors.stateViolation("approval does not match fee waiver request")
            }
            if (request.status != "PENDING_APPROVAL") {
                throw WorkflowErrors.stateViolation("only pending fee waiver requests can be rejected")
            }
            markFeeWaiverRequestRejected(request.requestId)
            feeWaiverRequest(request.requestId)
        } else {
            null
        }
        val transactionCorrectionRequest = if (pendingApproval.businessType == ApprovalBusinessTypes.TRANSACTION_CORRECTION) {
            val request = transactionCorrectionRequestForUpdate(pendingApproval.businessReferenceId)
            if (request.approvalId != pendingApproval.approvalId || request.businessType != pendingApproval.businessType) {
                throw WorkflowErrors.stateViolation("approval does not match transaction correction request")
            }
            if (request.status != "PENDING_APPROVAL") {
                throw WorkflowErrors.stateViolation("only pending transaction correction requests can be rejected")
            }
            markTransactionCorrectionRequestRejected(request.requestId)
            transactionCorrectionRequest(request.requestId)
        } else {
            null
        }
        return StaffApprovalRejectionResponse(
            item = approval,
            rejected = true,
            feeWaiverRequest = feeWaiverRequest,
            transactionCorrectionRequest = transactionCorrectionRequest
        )
    }

    private fun <T> runSerializableApprovalExecution(operation: () -> T): T {
        var attempt = 1
        while (true) {
            try {
                val template = TransactionTemplate(transactionManager).apply {
                    isolationLevel = TransactionDefinition.ISOLATION_SERIALIZABLE
                }
                return template.execute { operation() }
                    ?: error("serializable approval execution returned no result")
            } catch (error: RuntimeException) {
                if (attempt >= SERIALIZABLE_APPROVAL_MAX_ATTEMPTS || !isRetryableSerializationFailure(error)) {
                    throw error
                }
                Thread.sleep(75L * attempt * attempt)
                attempt += 1
            }
        }
    }

    private fun <T> runSerializableStaffAccess(operation: () -> T): T {
        var attempt = 1
        while (true) {
            try {
                val template = TransactionTemplate(transactionManager).apply {
                    isolationLevel = TransactionDefinition.ISOLATION_SERIALIZABLE
                }
                return template.execute { operation() }
                    ?: error("serializable staff access returned no result")
            } catch (error: RuntimeException) {
                if (attempt >= SERIALIZABLE_STAFF_ACCESS_MAX_ATTEMPTS || !isRetryableSerializationFailure(error)) {
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

    private fun requireReason(reason: String?, message: String) {
        if (reason.isNullOrBlank()) {
            throw WorkflowErrors.reasonRequired(message)
        }
    }

    private fun requireRole(role: String, allowedRoles: Set<String>, message: String) {
        if (role !in allowedRoles) {
            throw WorkflowErrors.authorizationViolation(message)
        }
    }

    private fun requireCheckerRole(businessType: String, approvedByRole: String) {
        val allowedRoles = when (businessType) {
            ApprovalBusinessTypes.ACCOUNT_HOLD -> ACCOUNT_HOLD_CHECKER_ROLES
            ApprovalBusinessTypes.ACCOUNT_HOLD_RELEASE -> ACCOUNT_HOLD_RELEASE_CHECKER_ROLES
            ApprovalBusinessTypes.TRANSFER_LIMIT_CHANGE -> TRANSFER_LIMIT_CHANGE_CHECKER_ROLES
            ApprovalBusinessTypes.CUSTOMER_KYC_REVIEW -> CUSTOMER_KYC_REVIEW_CHECKER_ROLES
            ApprovalBusinessTypes.FEE_WAIVER -> FEE_WAIVER_CHECKER_ROLES
            ApprovalBusinessTypes.TRANSACTION_CORRECTION -> TRANSACTION_CORRECTION_CHECKER_ROLES
            else -> return
        }
        requireRole(approvedByRole, allowedRoles, "checker role cannot approve $businessType")
    }

    private fun requireField(value: String?, field: String): String {
        if (value.isNullOrBlank()) {
            throw WorkflowErrors.validation("$field is required")
        }
        return value
    }

    private fun nextAccountHoldRequestId(): String =
        "AHR-${UUID.randomUUID().toString().uppercase()}"

    private fun nextTransferLimitChangeRequestId(): String =
        "TLR-${UUID.randomUUID().toString().uppercase()}"

    private fun nextCustomerKycReviewRequestId(): String =
        "KYR-${UUID.randomUUID().toString().uppercase()}"

    private fun nextFeeWaiverRequestId(): String =
        "FWR-${UUID.randomUUID().toString().uppercase()}"

    private fun nextTransactionCorrectionRequestId(): String =
        "TCR-${UUID.randomUUID().toString().uppercase()}"

    private fun customer(customerId: String): StaffCustomerRecord =
        try {
            jdbc.queryForObject(
                """
                SELECT customer_id, customer_name, customer_phone, customer_address, customer_grade, risk_grade
                FROM customers
                WHERE customer_id = :customerId
                """.trimIndent(),
                mapOf("customerId" to customerId),
                this::mapCustomer
            ) ?: throw WorkflowErrors.notFound("customer not found: $customerId")
        } catch (_: EmptyResultDataAccessException) {
            throw WorkflowErrors.notFound("customer not found: $customerId")
        }

    private fun account(accountId: String, forUpdate: Boolean): StaffAccountRecord =
        try {
            jdbc.queryForObject(
                accountSql(
                    if (forUpdate) {
                        "WHERE a.account_id = :accountId FOR UPDATE OF a, p"
                    } else {
                        "WHERE a.account_id = :accountId"
                    }
                ),
                mapOf("accountId" to accountId),
                this::mapAccountRecord
            ) ?: throw WorkflowErrors.notFound("account not found: $accountId")
        } catch (_: EmptyResultDataAccessException) {
            throw WorkflowErrors.notFound("account not found: $accountId")
        }

    private fun accountSql(suffix: String): String =
        """
        SELECT a.customer_id, a.account_id, a.account_no, a.status, a.currency,
               p.ledger_balance_minor, p.available_balance_minor, p.hold_amount_minor
        FROM accounts a
        JOIN account_balance_projections p
          ON p.account_id = a.account_id
         AND p.currency = a.currency
        $suffix
        """.trimIndent()

    private fun accountLimit(accountId: String, forUpdate: Boolean): StaffTransferLimitRecord =
        try {
            jdbc.queryForObject(
                transferLimitSql(
                    if (forUpdate) {
                        "WHERE a.account_id = :accountId FOR UPDATE OF a, l"
                    } else {
                        "WHERE a.account_id = :accountId"
                    }
                ),
                mapOf("accountId" to accountId),
                this::mapTransferLimitRecord
            ) ?: throw WorkflowErrors.notFound("transfer limit not found for account: $accountId")
        } catch (_: EmptyResultDataAccessException) {
            throw WorkflowErrors.notFound("transfer limit not found for account: $accountId")
        }

    private fun transferLimitSql(suffix: String): String =
        """
        SELECT a.customer_id, a.account_id, a.account_no, a.status AS account_status, a.currency,
               l.daily_transfer_limit_minor, l.single_transfer_limit_minor, l.updated_at
        FROM accounts a
        JOIN account_limits l
          ON l.account_id = a.account_id
        $suffix
        """.trimIndent()

    private fun customerKycProfile(customerId: String, forUpdate: Boolean): StaffKycProfileRecord =
        try {
            jdbc.queryForObject(
                """
                SELECT customer_id, kyc_status, source_of_funds_code, transaction_purpose_code,
                       simulated_provider_reference, updated_at
                FROM customer_kyc_profiles
                WHERE customer_id = :customerId
                ${if (forUpdate) "FOR UPDATE" else ""}
                """.trimIndent(),
                mapOf("customerId" to customerId),
                this::mapKycProfileRecord
            ) ?: throw WorkflowErrors.notFound("customer KYC profile not found: $customerId")
        } catch (_: EmptyResultDataAccessException) {
            throw WorkflowErrors.notFound("customer KYC profile not found: $customerId")
        }

    private fun ledgerPostingBelongsToAccount(ledgerTransactionId: String, accountId: String): Boolean =
        (
            jdbc.queryForObject(
                """
                SELECT count(*)
                FROM ledger_postings
                WHERE ledger_transaction_id = :ledgerTransactionId
                  AND account_id = :accountId
                """.trimIndent(),
                mapOf("ledgerTransactionId" to ledgerTransactionId, "accountId" to accountId),
                Int::class.java
            ) ?: 0
            ) > 0

    private fun supportedCustomerInfoChange(afterSnapshot: Map<String, Any?>?): Map<String, Any?> {
        val allowed = linkedMapOf<String, Any?>()
        for (field in listOf("phone", "address", "customerGrade")) {
            val value = afterSnapshot?.get(field)
            if (value != null) {
                allowed[field] = value.toString()
            }
        }
        return allowed
    }

    private fun applyCustomerInfoChange(
        customerId: String,
        afterSnapshot: Map<String, Any?>?,
        command: ApproveApprovalCommand
    ) {
        val allowed = supportedCustomerInfoChange(afterSnapshot)
        if (allowed.isEmpty()) {
            throw WorkflowErrors.validation("approved customer info change has no supported fields")
        }
        jdbc.update(
            """
            UPDATE customers
            SET customer_phone = COALESCE(:phone, customer_phone),
                customer_address = COALESCE(:address, customer_address),
                customer_grade = COALESCE(:customerGrade, customer_grade)
            WHERE customer_id = :customerId
            """.trimIndent(),
            mapOf(
                "customerId" to customerId,
                "phone" to allowed["phone"],
                "address" to allowed["address"],
                "customerGrade" to allowed["customerGrade"]
            )
        )
        appendAudit(
            eventType = "COMMAND_EXECUTED",
            actorId = command.approvedBy,
            actorRole = command.approvedByRole,
            screenId = command.screenId ?: "CST-103",
            customerId = customerId,
            accountId = null,
            reason = null,
            payload = mapOf(
                "businessType" to ApprovalBusinessTypes.CUSTOMER_INFO_CHANGE,
                "fields" to allowed.keys.toList(),
                "syntheticOnly" to true
            )
        )
    }

    private fun applyApprovedAccountHoldRequest(
        approval: lab.banking.core.approval.OperatorApproval,
        command: ApproveApprovalCommand
    ): Pair<AccountHoldRequestDto, StaffAccountDto> {
        val request = accountHoldRequestForUpdate(approval.businessReferenceId)
        if (request.approvalId != approval.approvalId || request.businessType != approval.businessType) {
            throw WorkflowErrors.stateViolation("approval does not match account hold request")
        }
        if (request.status != "PENDING_APPROVAL") {
            throw WorkflowErrors.stateViolation("only pending account hold requests can be executed")
        }
        val account = account(request.targetAccountId, forUpdate = true)
        when (approval.businessType) {
            ApprovalBusinessTypes.ACCOUNT_HOLD -> applyAccountHold(request, account)
            ApprovalBusinessTypes.ACCOUNT_HOLD_RELEASE -> applyAccountHoldRelease(request, account)
            else -> throw WorkflowErrors.stateViolation("approval is not an account hold approval")
        }
        val screenId = command.screenId ?: if (approval.businessType == ApprovalBusinessTypes.ACCOUNT_HOLD) "ACC-103" else "ACC-104"
        appendAudit(
            eventType = "COMMAND_EXECUTED",
            actorId = command.approvedBy,
            actorRole = command.approvedByRole,
            screenId = screenId,
            customerId = request.targetCustomerId,
            accountId = request.targetAccountId,
            reason = request.reason,
            payload = mapOf(
                "businessType" to approval.businessType,
                "requestId" to request.requestId,
                "approvalId" to approval.approvalId,
                "holdAmountMinor" to request.holdAmountMinor,
                "status" to if (approval.businessType == ApprovalBusinessTypes.ACCOUNT_HOLD) "ACTIVE" else "RELEASED",
                "syntheticOnly" to true,
                "ledgerSourceRowsMutated" to false
            )
        )
        val updatedRequest = accountHoldRequest(request.requestId)
        val updatedAccount = account(request.targetAccountId, forUpdate = false)
        return updatedRequest to updatedAccount.toDto()
    }

    private fun applyAccountHold(request: AccountHoldRequestDto, account: StaffAccountRecord) {
        if (account.status != "ACTIVE") {
            throw WorkflowErrors.stateViolation("only active accounts can be held")
        }
        val accountRows = jdbc.update(
            """
            UPDATE accounts
            SET status = 'HOLD'
            WHERE account_id = :accountId
              AND status = 'ACTIVE'
            """.trimIndent(),
            mapOf("accountId" to request.targetAccountId)
        )
        if (accountRows != 1) {
            throw WorkflowErrors.stateViolation("account hold state changed before approval execution")
        }
        val projectionRows = jdbc.update(
            """
            UPDATE account_balance_projections
            SET hold_amount_minor = hold_amount_minor + :holdAmountMinor,
                available_balance_minor = available_balance_minor - :holdAmountMinor,
                version = version + 1,
                updated_at = now()
            WHERE account_id = :accountId
              AND currency = :currency
              AND available_balance_minor >= :holdAmountMinor
            """.trimIndent(),
            mapOf(
                "accountId" to request.targetAccountId,
                "currency" to account.currency,
                "holdAmountMinor" to request.holdAmountMinor
            )
        )
        if (projectionRows != 1) {
            throw WorkflowErrors.stateViolation("account hold amount exceeds available projection")
        }
        markAccountHoldRequestExecuted(request.requestId, "ACTIVE")
    }

    private fun applyAccountHoldRelease(request: AccountHoldRequestDto, account: StaffAccountRecord) {
        if (account.status != "HOLD" || account.holdAmountMinor <= 0) {
            throw WorkflowErrors.stateViolation("only held accounts can be released")
        }
        if (request.holdAmountMinor != account.holdAmountMinor) {
            throw WorkflowErrors.stateViolation("account hold release amount no longer matches current hold")
        }
        val projectionRows = jdbc.update(
            """
            UPDATE account_balance_projections
            SET hold_amount_minor = hold_amount_minor - :holdAmountMinor,
                available_balance_minor = available_balance_minor + :holdAmountMinor,
                version = version + 1,
                updated_at = now()
            WHERE account_id = :accountId
              AND currency = :currency
              AND hold_amount_minor >= :holdAmountMinor
            """.trimIndent(),
            mapOf(
                "accountId" to request.targetAccountId,
                "currency" to account.currency,
                "holdAmountMinor" to request.holdAmountMinor
            )
        )
        if (projectionRows != 1) {
            throw WorkflowErrors.stateViolation("account hold projection changed before release")
        }
        val accountRows = jdbc.update(
            """
            UPDATE accounts
            SET status = 'ACTIVE'
            WHERE account_id = :accountId
              AND status = 'HOLD'
            """.trimIndent(),
            mapOf("accountId" to request.targetAccountId)
        )
        if (accountRows != 1) {
            throw WorkflowErrors.stateViolation("account hold status changed before release")
        }
        markAccountHoldRequestExecuted(request.requestId, "RELEASED")
    }

    private fun insertAccountHoldRequest(
        requestId: String,
        businessType: String,
        account: StaffAccountRecord,
        requestedBy: String,
        requestedRole: String,
        reason: String,
        reasonCode: String,
        holdAmountMinor: Long,
        approvalId: String,
        idempotencyKey: String,
        metadata: Map<String, Any?>
    ) {
        jdbc.update(
            """
            INSERT INTO account_hold_requests (
              request_id, business_type, business_reference_id, target_customer_id, target_account_id,
              requested_by, requested_role, reason, reason_code, hold_amount_minor, status,
              approval_id, idempotency_key, metadata_json
            )
            VALUES (
              :requestId, :businessType, :requestId, :customerId, :accountId,
              :requestedBy, :requestedRole, :reason, :reasonCode, :holdAmountMinor, 'PENDING_APPROVAL',
              :approvalId, :idempotencyKey, CAST(:metadataJson AS jsonb)
            )
            """.trimIndent(),
            mapOf(
                "requestId" to requestId,
                "businessType" to businessType,
                "customerId" to account.customerId,
                "accountId" to account.accountId,
                "requestedBy" to requestedBy,
                "requestedRole" to requestedRole,
                "reason" to reason,
                "reasonCode" to reasonCode,
                "holdAmountMinor" to holdAmountMinor,
                "approvalId" to approvalId,
                "idempotencyKey" to idempotencyKey,
                "metadataJson" to objectMapper.writeValueAsString(metadata)
            )
        )
    }

    private fun markAccountHoldRequestExecuted(requestId: String, status: String) {
        val rows = jdbc.update(
            """
            UPDATE account_hold_requests
            SET status = :status,
                updated_at = now(),
                executed_at = now()
            WHERE request_id = :requestId
              AND status = 'PENDING_APPROVAL'
            """.trimIndent(),
            mapOf("requestId" to requestId, "status" to status)
        )
        if (rows != 1) {
            throw WorkflowErrors.stateViolation("account hold request is no longer pending")
        }
    }

    private fun applyApprovedTransferLimitChangeRequest(
        approval: OperatorApproval,
        command: ApproveApprovalCommand
    ): Pair<TransferLimitChangeRequestDto, StaffTransferLimitDto> {
        val request = transferLimitChangeRequestForUpdate(approval.businessReferenceId)
        if (request.approvalId != approval.approvalId || request.businessType != approval.businessType) {
            throw WorkflowErrors.stateViolation("approval does not match transfer limit change request")
        }
        if (request.status != "PENDING_APPROVAL") {
            throw WorkflowErrors.stateViolation("only pending transfer limit change requests can be executed")
        }
        val current = accountLimit(request.targetAccountId, forUpdate = true)
        if (current.accountStatus != "ACTIVE") {
            throw WorkflowErrors.stateViolation("only active accounts can apply transfer limit changes")
        }
        val rows = jdbc.update(
            """
            UPDATE account_limits
            SET daily_transfer_limit_minor = :dailyTransferLimitMinor,
                single_transfer_limit_minor = :singleTransferLimitMinor,
                updated_at = now()
            WHERE account_id = :accountId
            """.trimIndent(),
            mapOf(
                "accountId" to request.targetAccountId,
                "dailyTransferLimitMinor" to request.requestedDailyTransferLimitMinor,
                "singleTransferLimitMinor" to request.requestedSingleTransferLimitMinor
            )
        )
        if (rows != 1) {
            throw WorkflowErrors.stateViolation("transfer limit row changed before approval execution")
        }
        markTransferLimitChangeRequestApplied(request.requestId)
        appendAudit(
            eventType = "COMMAND_EXECUTED",
            actorId = command.approvedBy,
            actorRole = command.approvedByRole,
            screenId = command.screenId ?: "LIM-102",
            customerId = request.targetCustomerId,
            accountId = request.targetAccountId,
            reason = request.reason,
            payload = mapOf(
                "businessType" to approval.businessType,
                "requestId" to request.requestId,
                "approvalId" to approval.approvalId,
                "dailyTransferLimitMinor" to request.requestedDailyTransferLimitMinor,
                "singleTransferLimitMinor" to request.requestedSingleTransferLimitMinor,
                "syntheticOnly" to true,
                "ledgerSourceRowsMutated" to false
            )
        )
        val updatedRequest = transferLimitChangeRequest(request.requestId)
        val updatedLimit = accountLimit(request.targetAccountId, forUpdate = false)
        return updatedRequest to updatedLimit.toDto()
    }

    private fun validateTransferLimitChange(current: StaffTransferLimitRecord, requestedDaily: Long, requestedSingle: Long) {
        if (requestedDaily < 0 || requestedSingle < 0) {
            throw WorkflowErrors.validation("transfer limits cannot be negative")
        }
        if (requestedSingle > requestedDaily) {
            throw WorkflowErrors.validation("singleTransferLimitMinor cannot exceed dailyTransferLimitMinor")
        }
        if (
            requestedDaily == current.dailyTransferLimitMinor &&
            requestedSingle == current.singleTransferLimitMinor
        ) {
            throw WorkflowErrors.validation("requested transfer limits must change at least one value")
        }
    }

    private fun insertTransferLimitChangeRequest(
        requestId: String,
        current: StaffTransferLimitRecord,
        requestedBy: String,
        requestedRole: String,
        reason: String,
        reasonCode: String,
        requestedDaily: Long,
        requestedSingle: Long,
        approvalId: String,
        idempotencyKey: String,
        metadata: Map<String, Any?>
    ) {
        jdbc.update(
            """
            INSERT INTO account_limit_change_requests (
              request_id, business_type, business_reference_id, target_customer_id, target_account_id,
              requested_by, requested_role, reason, reason_code,
              current_daily_transfer_limit_minor, current_single_transfer_limit_minor,
              requested_daily_transfer_limit_minor, requested_single_transfer_limit_minor,
              status, approval_id, idempotency_key, metadata_json
            )
            VALUES (
              :requestId, :businessType, :requestId, :customerId, :accountId,
              :requestedBy, :requestedRole, :reason, :reasonCode,
              :currentDaily, :currentSingle, :requestedDaily, :requestedSingle,
              'PENDING_APPROVAL', :approvalId, :idempotencyKey, CAST(:metadataJson AS jsonb)
            )
            """.trimIndent(),
            mapOf(
                "requestId" to requestId,
                "businessType" to ApprovalBusinessTypes.TRANSFER_LIMIT_CHANGE,
                "customerId" to current.customerId,
                "accountId" to current.accountId,
                "requestedBy" to requestedBy,
                "requestedRole" to requestedRole,
                "reason" to reason,
                "reasonCode" to reasonCode,
                "currentDaily" to current.dailyTransferLimitMinor,
                "currentSingle" to current.singleTransferLimitMinor,
                "requestedDaily" to requestedDaily,
                "requestedSingle" to requestedSingle,
                "approvalId" to approvalId,
                "idempotencyKey" to idempotencyKey,
                "metadataJson" to objectMapper.writeValueAsString(metadata)
            )
        )
    }

    private fun markTransferLimitChangeRequestApplied(requestId: String) {
        val rows = jdbc.update(
            """
            UPDATE account_limit_change_requests
            SET status = 'APPLIED',
                updated_at = now(),
                executed_at = now()
            WHERE request_id = :requestId
              AND status = 'PENDING_APPROVAL'
            """.trimIndent(),
            mapOf("requestId" to requestId)
        )
        if (rows != 1) {
            throw WorkflowErrors.stateViolation("transfer limit change request is no longer pending")
        }
    }

    private fun transferLimitChangeResponse(request: TransferLimitChangeRequestDto): TransferLimitChangeRequestResponse =
        TransferLimitChangeRequestResponse(
            item = request,
            approval = approvals.approval(request.approvalId ?: throw WorkflowErrors.stateViolation("transfer limit change request has no approval")),
            limit = accountLimit(request.targetAccountId, forUpdate = false).toDto()
        )

    private fun existingTransferLimitChangeRequest(requestedBy: String, idempotencyKey: String): TransferLimitChangeRequestDto? =
        jdbc.query(
            transferLimitChangeRequestSql(
                """
                WHERE business_type = :businessType
                  AND requested_by = :requestedBy
                  AND idempotency_key = :idempotencyKey
                """.trimIndent()
            ),
            mapOf(
                "businessType" to ApprovalBusinessTypes.TRANSFER_LIMIT_CHANGE,
                "requestedBy" to requestedBy,
                "idempotencyKey" to idempotencyKey
            ),
            this::mapTransferLimitChangeRequest
        ).firstOrNull()

    private fun transferLimitChangeRequest(requestId: String): TransferLimitChangeRequestDto =
        jdbc.queryForObject(
            transferLimitChangeRequestSql("WHERE request_id = :requestId"),
            mapOf("requestId" to requestId),
            this::mapTransferLimitChangeRequest
        ) ?: throw WorkflowErrors.notFound("transfer limit change request not found: $requestId")

    private fun transferLimitChangeRequestForUpdate(requestId: String): TransferLimitChangeRequestDto =
        jdbc.queryForObject(
            transferLimitChangeRequestSql("WHERE request_id = :requestId FOR UPDATE"),
            mapOf("requestId" to requestId),
            this::mapTransferLimitChangeRequest
        ) ?: throw WorkflowErrors.notFound("transfer limit change request not found: $requestId")

    private fun transferLimitChangeRequestSql(suffix: String): String =
        """
        SELECT request_id, business_type, business_reference_id, target_customer_id, target_account_id,
               target_transaction_id, requested_by, requested_role, reason, reason_code,
               current_daily_transfer_limit_minor, current_single_transfer_limit_minor,
               requested_daily_transfer_limit_minor, requested_single_transfer_limit_minor,
               status, approval_id, idempotency_key, created_at, updated_at, executed_at,
               metadata_json::text AS metadata_json
        FROM account_limit_change_requests
        $suffix
        """.trimIndent()

    private fun mapTransferLimitChangeRequest(rs: ResultSet, rowNum: Int): TransferLimitChangeRequestDto =
        TransferLimitChangeRequestDto(
            requestId = rs.getString("request_id"),
            businessType = rs.getString("business_type"),
            businessReferenceId = rs.getString("business_reference_id"),
            targetCustomerId = rs.getString("target_customer_id"),
            targetAccountId = rs.getString("target_account_id"),
            requestedBy = rs.getString("requested_by"),
            requestedRole = rs.getString("requested_role"),
            reason = rs.getString("reason"),
            reasonCode = rs.getString("reason_code"),
            currentDailyTransferLimitMinor = rs.getLong("current_daily_transfer_limit_minor"),
            currentSingleTransferLimitMinor = rs.getLong("current_single_transfer_limit_minor"),
            requestedDailyTransferLimitMinor = rs.getLong("requested_daily_transfer_limit_minor"),
            requestedSingleTransferLimitMinor = rs.getLong("requested_single_transfer_limit_minor"),
            status = rs.getString("status"),
            approvalId = rs.getString("approval_id"),
            idempotencyKey = rs.getString("idempotency_key"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
            executedAt = rs.getObject("executed_at", OffsetDateTime::class.java),
            metadata = readMetadata(rs.getString("metadata_json"))
        )

    private fun applyApprovedCustomerKycReviewRequest(
        approval: OperatorApproval,
        command: ApproveApprovalCommand
    ): Pair<CustomerKycReviewRequestDto, StaffKycProfileDto> {
        val request = customerKycReviewRequestForUpdate(approval.businessReferenceId)
        if (request.approvalId != approval.approvalId || request.businessType != approval.businessType) {
            throw WorkflowErrors.stateViolation("approval does not match customer KYC review request")
        }
        if (request.status != "PENDING_APPROVAL") {
            throw WorkflowErrors.stateViolation("only pending customer KYC review requests can be executed")
        }
        customerKycProfile(request.targetCustomerId, forUpdate = true)
        val rows = jdbc.update(
            """
            UPDATE customer_kyc_profiles
            SET kyc_status = :requestedKycStatus,
                updated_at = now()
            WHERE customer_id = :customerId
            """.trimIndent(),
            mapOf(
                "customerId" to request.targetCustomerId,
                "requestedKycStatus" to request.requestedKycStatus
            )
        )
        if (rows != 1) {
            throw WorkflowErrors.stateViolation("customer KYC profile changed before approval execution")
        }
        markCustomerKycReviewRequestRequested(request.requestId)
        appendAudit(
            eventType = "COMMAND_EXECUTED",
            actorId = command.approvedBy,
            actorRole = command.approvedByRole,
            screenId = command.screenId ?: "KYC-101",
            customerId = request.targetCustomerId,
            accountId = null,
            reason = request.reason,
            payload = mapOf(
                "businessType" to approval.businessType,
                "requestId" to request.requestId,
                "approvalId" to approval.approvalId,
                "reviewTrigger" to request.reviewTrigger,
                "previousKycStatus" to request.previousKycStatus,
                "requestedKycStatus" to request.requestedKycStatus,
                "realKycProviderCalled" to false,
                "syntheticOnly" to true,
                "ledgerSourceRowsMutated" to false
            )
        )
        val updatedRequest = customerKycReviewRequest(request.requestId)
        val updatedProfile = customerKycProfile(request.targetCustomerId, forUpdate = false)
        return updatedRequest to updatedProfile.toDto()
    }

    private fun insertCustomerKycReviewRequest(
        requestId: String,
        customerId: String,
        requestedBy: String,
        requestedRole: String,
        reason: String,
        reasonCode: String,
        reviewTrigger: String,
        previousKycStatus: String,
        requestedKycStatus: String,
        approvalId: String,
        idempotencyKey: String,
        metadata: Map<String, Any?>
    ) {
        jdbc.update(
            """
            INSERT INTO customer_kyc_review_requests (
              request_id, business_type, business_reference_id, target_customer_id,
              requested_by, requested_role, reason, reason_code, review_trigger,
              previous_kyc_status, requested_kyc_status, status,
              approval_id, idempotency_key, metadata_json
            )
            VALUES (
              :requestId, :businessType, :requestId, :customerId,
              :requestedBy, :requestedRole, :reason, :reasonCode, :reviewTrigger,
              :previousKycStatus, :requestedKycStatus, 'PENDING_APPROVAL',
              :approvalId, :idempotencyKey, CAST(:metadataJson AS jsonb)
            )
            """.trimIndent(),
            mapOf(
                "requestId" to requestId,
                "businessType" to ApprovalBusinessTypes.CUSTOMER_KYC_REVIEW,
                "customerId" to customerId,
                "requestedBy" to requestedBy,
                "requestedRole" to requestedRole,
                "reason" to reason,
                "reasonCode" to reasonCode,
                "reviewTrigger" to reviewTrigger,
                "previousKycStatus" to previousKycStatus,
                "requestedKycStatus" to requestedKycStatus,
                "approvalId" to approvalId,
                "idempotencyKey" to idempotencyKey,
                "metadataJson" to objectMapper.writeValueAsString(metadata)
            )
        )
    }

    private fun markCustomerKycReviewRequestRequested(requestId: String) {
        val rows = jdbc.update(
            """
            UPDATE customer_kyc_review_requests
            SET status = 'REVIEW_REQUESTED',
                updated_at = now(),
                executed_at = now()
            WHERE request_id = :requestId
              AND status = 'PENDING_APPROVAL'
            """.trimIndent(),
            mapOf("requestId" to requestId)
        )
        if (rows != 1) {
            throw WorkflowErrors.stateViolation("customer KYC review request is no longer pending")
        }
    }

    private fun customerKycReviewResponse(request: CustomerKycReviewRequestDto): CustomerKycReviewRequestResponse =
        CustomerKycReviewRequestResponse(
            item = request,
            approval = approvals.approval(request.approvalId ?: throw WorkflowErrors.stateViolation("customer KYC review request has no approval")),
            kycProfile = customerKycProfile(request.targetCustomerId, forUpdate = false).toDto()
        )

    private fun existingCustomerKycReviewRequest(requestedBy: String, idempotencyKey: String): CustomerKycReviewRequestDto? =
        jdbc.query(
            customerKycReviewRequestSql(
                """
                WHERE business_type = :businessType
                  AND requested_by = :requestedBy
                  AND idempotency_key = :idempotencyKey
                """.trimIndent()
            ),
            mapOf(
                "businessType" to ApprovalBusinessTypes.CUSTOMER_KYC_REVIEW,
                "requestedBy" to requestedBy,
                "idempotencyKey" to idempotencyKey
            ),
            this::mapCustomerKycReviewRequest
        ).firstOrNull()

    private fun customerKycReviewRequest(requestId: String): CustomerKycReviewRequestDto =
        jdbc.queryForObject(
            customerKycReviewRequestSql("WHERE request_id = :requestId"),
            mapOf("requestId" to requestId),
            this::mapCustomerKycReviewRequest
        ) ?: throw WorkflowErrors.notFound("customer KYC review request not found: $requestId")

    private fun customerKycReviewRequestForUpdate(requestId: String): CustomerKycReviewRequestDto =
        jdbc.queryForObject(
            customerKycReviewRequestSql("WHERE request_id = :requestId FOR UPDATE"),
            mapOf("requestId" to requestId),
            this::mapCustomerKycReviewRequest
        ) ?: throw WorkflowErrors.notFound("customer KYC review request not found: $requestId")

    private fun customerKycReviewRequestSql(suffix: String): String =
        """
        SELECT request_id, business_type, business_reference_id, target_customer_id,
               target_account_id, target_transaction_id, requested_by, requested_role,
               reason, reason_code, review_trigger, previous_kyc_status, requested_kyc_status,
               status, approval_id, idempotency_key, created_at, updated_at, executed_at,
               metadata_json::text AS metadata_json
        FROM customer_kyc_review_requests
        $suffix
        """.trimIndent()

    private fun mapCustomerKycReviewRequest(rs: ResultSet, rowNum: Int): CustomerKycReviewRequestDto =
        CustomerKycReviewRequestDto(
            requestId = rs.getString("request_id"),
            businessType = rs.getString("business_type"),
            businessReferenceId = rs.getString("business_reference_id"),
            targetCustomerId = rs.getString("target_customer_id"),
            requestedBy = rs.getString("requested_by"),
            requestedRole = rs.getString("requested_role"),
            reason = rs.getString("reason"),
            reasonCode = rs.getString("reason_code"),
            reviewTrigger = rs.getString("review_trigger"),
            previousKycStatus = rs.getString("previous_kyc_status"),
            requestedKycStatus = rs.getString("requested_kyc_status"),
            status = rs.getString("status"),
            approvalId = rs.getString("approval_id"),
            idempotencyKey = rs.getString("idempotency_key"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
            executedAt = rs.getObject("executed_at", OffsetDateTime::class.java),
            metadata = readMetadata(rs.getString("metadata_json"))
        )

    private fun applyApprovedFeeWaiverRequest(
        approval: OperatorApproval,
        command: ApproveApprovalCommand
    ): Pair<FeeWaiverRequestDto, StaffAccountDto> {
        val request = feeWaiverRequestForUpdate(approval.businessReferenceId)
        if (request.approvalId != approval.approvalId || request.businessType != approval.businessType) {
            throw WorkflowErrors.stateViolation("approval does not match fee waiver request")
        }
        if (request.status != "PENDING_APPROVAL") {
            throw WorkflowErrors.stateViolation("only pending fee waiver requests can be executed")
        }
        val account = account(request.targetAccountId, forUpdate = true)
        if (account.status != "ACTIVE") {
            throw WorkflowErrors.stateViolation("only active accounts can apply fee waiver")
        }
        markFeeWaiverRequestApproved(request.requestId)
        appendAudit(
            eventType = "COMMAND_EXECUTED",
            actorId = command.approvedBy,
            actorRole = command.approvedByRole,
            screenId = command.screenId ?: "FEE-102",
            customerId = request.targetCustomerId,
            accountId = request.targetAccountId,
            reason = request.reason,
            payload = mapOf(
                "businessType" to approval.businessType,
                "requestId" to request.requestId,
                "approvalId" to approval.approvalId,
                "feeCode" to request.feeCode,
                "waivedAmountMinor" to request.waivedAmountMinor,
                "currency" to request.currency,
                "feePostingCreated" to false,
                "syntheticOnly" to true,
                "ledgerSourceRowsMutated" to false
            )
        )
        val updatedRequest = feeWaiverRequest(request.requestId)
        val updatedAccount = account(request.targetAccountId, forUpdate = false)
        return updatedRequest to updatedAccount.toDto()
    }

    private fun insertFeeWaiverRequest(
        requestId: String,
        account: StaffAccountRecord,
        requestedBy: String,
        requestedRole: String,
        reason: String,
        reasonCode: String,
        feeCode: String,
        waivedAmountMinor: Long,
        currency: String,
        targetTransactionId: String?,
        approvalId: String,
        idempotencyKey: String,
        metadata: Map<String, Any?>
    ) {
        jdbc.update(
            """
            INSERT INTO fee_waiver_requests (
              request_id, business_type, business_reference_id, target_customer_id, target_account_id,
              target_transaction_id, requested_by, requested_role, reason, reason_code,
              fee_code, waived_amount_minor, currency, status, approval_id, idempotency_key, metadata_json
            )
            VALUES (
              :requestId, :businessType, :requestId, :customerId, :accountId,
              :targetTransactionId, :requestedBy, :requestedRole, :reason, :reasonCode,
              :feeCode, :waivedAmountMinor, :currency, 'PENDING_APPROVAL',
              :approvalId, :idempotencyKey, CAST(:metadataJson AS jsonb)
            )
            """.trimIndent(),
            mapOf(
                "requestId" to requestId,
                "businessType" to ApprovalBusinessTypes.FEE_WAIVER,
                "customerId" to account.customerId,
                "accountId" to account.accountId,
                "targetTransactionId" to targetTransactionId,
                "requestedBy" to requestedBy,
                "requestedRole" to requestedRole,
                "reason" to reason,
                "reasonCode" to reasonCode,
                "feeCode" to feeCode,
                "waivedAmountMinor" to waivedAmountMinor,
                "currency" to currency,
                "approvalId" to approvalId,
                "idempotencyKey" to idempotencyKey,
                "metadataJson" to objectMapper.writeValueAsString(metadata)
            )
        )
    }

    private fun markFeeWaiverRequestApproved(requestId: String) {
        val rows = jdbc.update(
            """
            UPDATE fee_waiver_requests
            SET status = 'APPROVED',
                updated_at = now(),
                executed_at = now()
            WHERE request_id = :requestId
              AND status = 'PENDING_APPROVAL'
            """.trimIndent(),
            mapOf("requestId" to requestId)
        )
        if (rows != 1) {
            throw WorkflowErrors.stateViolation("fee waiver request is no longer pending")
        }
    }

    private fun markFeeWaiverRequestRejected(requestId: String) {
        val rows = jdbc.update(
            """
            UPDATE fee_waiver_requests
            SET status = 'REJECTED',
                updated_at = now()
            WHERE request_id = :requestId
              AND status = 'PENDING_APPROVAL'
            """.trimIndent(),
            mapOf("requestId" to requestId)
        )
        if (rows != 1) {
            throw WorkflowErrors.stateViolation("fee waiver request is no longer pending")
        }
    }

    private fun feeWaiverResponse(request: FeeWaiverRequestDto): FeeWaiverRequestResponse =
        FeeWaiverRequestResponse(
            item = request,
            approval = approvals.approval(request.approvalId ?: throw WorkflowErrors.stateViolation("fee waiver request has no approval")),
            account = account(request.targetAccountId, forUpdate = false).toDto()
        )

    private fun existingFeeWaiverRequest(requestedBy: String, idempotencyKey: String): FeeWaiverRequestDto? =
        jdbc.query(
            feeWaiverRequestSql(
                """
                WHERE business_type = :businessType
                  AND requested_by = :requestedBy
                  AND idempotency_key = :idempotencyKey
                """.trimIndent()
            ),
            mapOf(
                "businessType" to ApprovalBusinessTypes.FEE_WAIVER,
                "requestedBy" to requestedBy,
                "idempotencyKey" to idempotencyKey
            ),
            this::mapFeeWaiverRequest
        ).firstOrNull()

    private fun feeWaiverRequest(requestId: String): FeeWaiverRequestDto =
        jdbc.queryForObject(
            feeWaiverRequestSql("WHERE request_id = :requestId"),
            mapOf("requestId" to requestId),
            this::mapFeeWaiverRequest
        ) ?: throw WorkflowErrors.notFound("fee waiver request not found: $requestId")

    private fun feeWaiverRequestForUpdate(requestId: String): FeeWaiverRequestDto =
        jdbc.queryForObject(
            feeWaiverRequestSql("WHERE request_id = :requestId FOR UPDATE"),
            mapOf("requestId" to requestId),
            this::mapFeeWaiverRequest
        ) ?: throw WorkflowErrors.notFound("fee waiver request not found: $requestId")

    private fun feeWaiverRequestSql(suffix: String): String =
        """
        SELECT request_id, business_type, business_reference_id, target_customer_id, target_account_id,
               target_transaction_id, requested_by, requested_role, reason, reason_code,
               fee_code, waived_amount_minor, currency, status, approval_id, idempotency_key,
               created_at, updated_at, executed_at, metadata_json::text AS metadata_json
        FROM fee_waiver_requests
        $suffix
        """.trimIndent()

    private fun mapFeeWaiverRequest(rs: ResultSet, rowNum: Int): FeeWaiverRequestDto =
        FeeWaiverRequestDto(
            requestId = rs.getString("request_id"),
            businessType = rs.getString("business_type"),
            businessReferenceId = rs.getString("business_reference_id"),
            targetCustomerId = rs.getString("target_customer_id"),
            targetAccountId = rs.getString("target_account_id"),
            targetTransactionId = rs.getString("target_transaction_id"),
            requestedBy = rs.getString("requested_by"),
            requestedRole = rs.getString("requested_role"),
            reason = rs.getString("reason"),
            reasonCode = rs.getString("reason_code"),
            feeCode = rs.getString("fee_code"),
            waivedAmountMinor = rs.getLong("waived_amount_minor"),
            currency = rs.getString("currency"),
            status = rs.getString("status"),
            approvalId = rs.getString("approval_id"),
            idempotencyKey = rs.getString("idempotency_key"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
            executedAt = rs.getObject("executed_at", OffsetDateTime::class.java),
            metadata = readMetadata(rs.getString("metadata_json"))
        )

    private fun applyApprovedTransactionCorrectionRequest(
        approval: OperatorApproval,
        command: ApproveApprovalCommand
    ): Triple<TransactionCorrectionRequestDto, StaffAccountDto, LedgerCommandResult> {
        val request = transactionCorrectionRequestForUpdate(approval.businessReferenceId)
        if (request.approvalId != approval.approvalId || request.businessType != approval.businessType) {
            throw WorkflowErrors.stateViolation("approval does not match transaction correction request")
        }
        if (request.status != "PENDING_APPROVAL") {
            throw WorkflowErrors.stateViolation("only pending transaction correction requests can be executed")
        }
        val account = account(request.targetAccountId, forUpdate = true)
        if (account.status != "ACTIVE") {
            throw WorkflowErrors.stateViolation("only active accounts can apply transaction correction")
        }
        if (request.correctionType != "REVERSAL") {
            throw WorkflowErrors.validation("only REVERSAL transaction correction is supported in this phase")
        }
        val ledgerResult = ledgerCommandService.reverseTransaction(
            ReversalCommand(
                originalTransactionId = request.targetTransactionId,
                idempotencyKey = "TRANSACTION-CORRECTION-${request.requestId}",
                requestedBy = command.approvedBy,
                requestedChannel = "STAFF_TERMINAL",
                businessDate = request.correctionBusinessDate,
                reason = request.reason,
                businessReferenceId = request.requestId
            )
        )
        markTransactionCorrectionRequestReversed(request.requestId, ledgerResult.value.id)
        appendAudit(
            eventType = "COMMAND_EXECUTED",
            actorId = command.approvedBy,
            actorRole = command.approvedByRole,
            screenId = command.screenId ?: "LED-103",
            customerId = request.targetCustomerId,
            accountId = request.targetAccountId,
            reason = request.reason,
            payload = mapOf(
                "businessType" to approval.businessType,
                "requestId" to request.requestId,
                "approvalId" to approval.approvalId,
                "targetTransactionId" to request.targetTransactionId,
                "reversalTransactionId" to ledgerResult.value.id,
                "correctionType" to request.correctionType,
                "syntheticOnly" to true,
                "ledgerSourceRowsMutated" to false
            )
        )
        val updatedRequest = transactionCorrectionRequest(request.requestId)
        val updatedAccount = account(request.targetAccountId, forUpdate = false)
        return Triple(updatedRequest, updatedAccount.toDto(), ledgerResult)
    }

    private fun insertTransactionCorrectionRequest(
        requestId: String,
        target: TransactionCorrectionTarget,
        requestedBy: String,
        requestedRole: String,
        reason: String,
        reasonCode: String,
        correctionType: String,
        correctionBusinessDate: LocalDate,
        approvalId: String,
        idempotencyKey: String,
        metadata: Map<String, Any?>
    ) {
        jdbc.update(
            """
            INSERT INTO transaction_correction_requests (
              request_id, business_type, business_reference_id, target_customer_id, target_account_id,
              target_transaction_id, requested_by, requested_role, reason, reason_code,
              correction_type, correction_business_date, status, approval_id, idempotency_key, metadata_json
            )
            VALUES (
              :requestId, :businessType, :requestId, :customerId, :accountId,
              :targetTransactionId, :requestedBy, :requestedRole, :reason, :reasonCode,
              :correctionType, :correctionBusinessDate, 'PENDING_APPROVAL',
              :approvalId, :idempotencyKey, CAST(:metadataJson AS jsonb)
            )
            """.trimIndent(),
            mapOf(
                "requestId" to requestId,
                "businessType" to ApprovalBusinessTypes.TRANSACTION_CORRECTION,
                "customerId" to target.customerId,
                "accountId" to target.accountId,
                "targetTransactionId" to target.transactionId,
                "requestedBy" to requestedBy,
                "requestedRole" to requestedRole,
                "reason" to reason,
                "reasonCode" to reasonCode,
                "correctionType" to correctionType,
                "correctionBusinessDate" to correctionBusinessDate,
                "approvalId" to approvalId,
                "idempotencyKey" to idempotencyKey,
                "metadataJson" to objectMapper.writeValueAsString(metadata)
            )
        )
    }

    private fun markTransactionCorrectionRequestReversed(requestId: String, ledgerTransactionId: String) {
        val rows = jdbc.update(
            """
            UPDATE transaction_correction_requests
            SET status = 'REVERSED',
                ledger_transaction_id = :ledgerTransactionId,
                updated_at = now(),
                executed_at = now()
            WHERE request_id = :requestId
              AND status = 'PENDING_APPROVAL'
            """.trimIndent(),
            mapOf("requestId" to requestId, "ledgerTransactionId" to ledgerTransactionId)
        )
        if (rows != 1) {
            throw WorkflowErrors.stateViolation("transaction correction request is no longer pending")
        }
    }

    private fun markTransactionCorrectionRequestRejected(requestId: String) {
        val rows = jdbc.update(
            """
            UPDATE transaction_correction_requests
            SET status = 'REJECTED',
                updated_at = now()
            WHERE request_id = :requestId
              AND status = 'PENDING_APPROVAL'
            """.trimIndent(),
            mapOf("requestId" to requestId)
        )
        if (rows != 1) {
            throw WorkflowErrors.stateViolation("transaction correction request is no longer pending")
        }
    }

    private fun transactionCorrectionResponse(request: TransactionCorrectionRequestDto): TransactionCorrectionRequestResponse =
        TransactionCorrectionRequestResponse(
            item = request,
            approval = approvals.approval(request.approvalId ?: throw WorkflowErrors.stateViolation("transaction correction request has no approval")),
            account = account(request.targetAccountId, forUpdate = false).toDto()
        )

    private fun existingTransactionCorrectionRequest(requestedBy: String, idempotencyKey: String): TransactionCorrectionRequestDto? =
        jdbc.query(
            transactionCorrectionRequestSql(
                """
                WHERE business_type = :businessType
                  AND requested_by = :requestedBy
                  AND idempotency_key = :idempotencyKey
                """.trimIndent()
            ),
            mapOf(
                "businessType" to ApprovalBusinessTypes.TRANSACTION_CORRECTION,
                "requestedBy" to requestedBy,
                "idempotencyKey" to idempotencyKey
            ),
            this::mapTransactionCorrectionRequest
        ).firstOrNull()

    private fun transactionCorrectionRequest(requestId: String): TransactionCorrectionRequestDto =
        jdbc.queryForObject(
            transactionCorrectionRequestSql("WHERE request_id = :requestId"),
            mapOf("requestId" to requestId),
            this::mapTransactionCorrectionRequest
        ) ?: throw WorkflowErrors.notFound("transaction correction request not found: $requestId")

    private fun transactionCorrectionRequestForUpdate(requestId: String): TransactionCorrectionRequestDto =
        jdbc.queryForObject(
            transactionCorrectionRequestSql("WHERE request_id = :requestId FOR UPDATE"),
            mapOf("requestId" to requestId),
            this::mapTransactionCorrectionRequest
        ) ?: throw WorkflowErrors.notFound("transaction correction request not found: $requestId")

    private fun transactionCorrectionRequestSql(suffix: String): String =
        """
        SELECT request_id, business_type, business_reference_id, target_customer_id, target_account_id,
               target_transaction_id, requested_by, requested_role, reason, reason_code,
               correction_type, correction_business_date, status, approval_id, ledger_transaction_id,
               idempotency_key, created_at, updated_at, executed_at, metadata_json::text AS metadata_json
        FROM transaction_correction_requests
        $suffix
        """.trimIndent()

    private fun mapTransactionCorrectionRequest(rs: ResultSet, rowNum: Int): TransactionCorrectionRequestDto =
        TransactionCorrectionRequestDto(
            requestId = rs.getString("request_id"),
            businessType = rs.getString("business_type"),
            businessReferenceId = rs.getString("business_reference_id"),
            targetCustomerId = rs.getString("target_customer_id"),
            targetAccountId = rs.getString("target_account_id"),
            targetTransactionId = rs.getString("target_transaction_id"),
            requestedBy = rs.getString("requested_by"),
            requestedRole = rs.getString("requested_role"),
            reason = rs.getString("reason"),
            reasonCode = rs.getString("reason_code"),
            correctionType = rs.getString("correction_type"),
            correctionBusinessDate = rs.getObject("correction_business_date", LocalDate::class.java),
            status = rs.getString("status"),
            approvalId = rs.getString("approval_id"),
            ledgerTransactionId = rs.getString("ledger_transaction_id"),
            idempotencyKey = rs.getString("idempotency_key"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
            executedAt = rs.getObject("executed_at", OffsetDateTime::class.java),
            metadata = readMetadata(rs.getString("metadata_json"))
        )

    private fun transactionAlreadyReversed(transactionId: String): Boolean =
        (
            jdbc.queryForObject(
                """
                SELECT count(*)
                FROM ledger_transactions
                WHERE original_transaction_id = :transactionId
                  AND transaction_type = 'REVERSAL'
                """.trimIndent(),
                mapOf("transactionId" to transactionId),
                Int::class.java
            ) ?: 0
            ) > 0

    private fun pendingTransactionCorrectionExists(transactionId: String): Boolean =
        (
            jdbc.queryForObject(
                """
                SELECT count(*)
                FROM transaction_correction_requests
                WHERE target_transaction_id = :transactionId
                  AND status = 'PENDING_APPROVAL'
                """.trimIndent(),
                mapOf("transactionId" to transactionId),
                Int::class.java
            ) ?: 0
            ) > 0

    private fun transactionCorrectionTarget(transactionId: String, requestedAccountId: String?): TransactionCorrectionTarget {
        val rows = jdbc.query(
            """
            SELECT lt.ledger_transaction_id, lt.transaction_type, lt.status, lt.business_date,
                   lt.requested_by, lt.requested_channel, lt.original_transaction_id,
                   lp.account_id, lp.direction, lp.amount_minor, lp.currency, a.customer_id
            FROM ledger_transactions lt
            JOIN ledger_postings lp
              ON lp.ledger_transaction_id = lt.ledger_transaction_id
            JOIN accounts a
              ON a.account_id = lp.account_id
            WHERE lt.ledger_transaction_id = :transactionId
            ORDER BY CASE WHEN a.customer_id = 'BANK' THEN 1 ELSE 0 END, lp.ledger_posting_id
            """.trimIndent(),
            mapOf("transactionId" to transactionId)
        ) { rs, _ ->
            TransactionCorrectionPostingRow(
                transactionId = rs.getString("ledger_transaction_id"),
                transactionType = rs.getString("transaction_type"),
                status = rs.getString("status"),
                businessDate = rs.getObject("business_date", LocalDate::class.java),
                requestedBy = rs.getString("requested_by"),
                requestedChannel = rs.getString("requested_channel"),
                originalTransactionId = rs.getString("original_transaction_id"),
                accountId = rs.getString("account_id"),
                direction = rs.getString("direction"),
                amountMinor = rs.getLong("amount_minor"),
                currency = rs.getString("currency"),
                customerId = rs.getString("customer_id")
            )
        }
        if (rows.isEmpty()) {
            throw WorkflowErrors.notFound("ledger transaction not found: $transactionId")
        }
        val first = rows.first()
        if (first.status != "POSTED") {
            throw WorkflowErrors.stateViolation("only posted ledger transactions can be corrected")
        }
        if (first.transactionType == "REVERSAL") {
            throw WorkflowErrors.stateViolation("reversal transactions cannot be corrected directly")
        }
        val customerRows = rows.filter { it.customerId != "BANK" }
        val targetRow = if (requestedAccountId != null) {
            customerRows.firstOrNull { it.accountId == requestedAccountId }
                ?: throw WorkflowErrors.validation("targetAccountId must belong to the target transaction")
        } else {
            customerRows.firstOrNull()
                ?: throw WorkflowErrors.validation("target transaction has no customer account posting")
        }
        return TransactionCorrectionTarget(
            transactionId = first.transactionId,
            transactionType = first.transactionType,
            status = first.status,
            businessDate = first.businessDate,
            requestedBy = first.requestedBy,
            requestedChannel = first.requestedChannel,
            originalTransactionId = first.originalTransactionId,
            customerId = targetRow.customerId,
            accountId = targetRow.accountId,
            currency = targetRow.currency,
            accountPostingAmountMinor = customerRows.filter { it.accountId == targetRow.accountId }.sumOf { it.amountMinor },
            postingsCount = rows.size
        )
    }

    private fun accountHoldResponse(request: AccountHoldRequestDto): AccountHoldRequestResponse =
        AccountHoldRequestResponse(
            item = request,
            approval = approvals.approval(request.approvalId ?: throw WorkflowErrors.stateViolation("account hold request has no approval")),
            account = account(request.targetAccountId, forUpdate = false).toDto()
        )

    private fun existingAccountHoldRequest(businessType: String, requestedBy: String, idempotencyKey: String): AccountHoldRequestDto? =
        jdbc.query(
            accountHoldRequestSql(
                """
                WHERE business_type = :businessType
                  AND requested_by = :requestedBy
                  AND idempotency_key = :idempotencyKey
                """.trimIndent()
            ),
            mapOf("businessType" to businessType, "requestedBy" to requestedBy, "idempotencyKey" to idempotencyKey),
            this::mapAccountHoldRequest
        ).firstOrNull()

    private fun accountHoldRequest(requestId: String): AccountHoldRequestDto =
        jdbc.queryForObject(
            accountHoldRequestSql("WHERE request_id = :requestId"),
            mapOf("requestId" to requestId),
            this::mapAccountHoldRequest
        ) ?: throw WorkflowErrors.notFound("account hold request not found: $requestId")

    private fun accountHoldRequestForUpdate(requestId: String): AccountHoldRequestDto =
        jdbc.queryForObject(
            accountHoldRequestSql("WHERE request_id = :requestId FOR UPDATE"),
            mapOf("requestId" to requestId),
            this::mapAccountHoldRequest
        ) ?: throw WorkflowErrors.notFound("account hold request not found: $requestId")

    private fun accountHoldRequestSql(suffix: String): String =
        """
        SELECT request_id, business_type, business_reference_id, target_customer_id, target_account_id,
               target_transaction_id, requested_by, requested_role, reason, reason_code, hold_amount_minor,
               status, approval_id, idempotency_key, created_at, updated_at, executed_at,
               metadata_json::text AS metadata_json
        FROM account_hold_requests
        $suffix
        """.trimIndent()

    private fun mapAccountHoldRequest(rs: ResultSet, rowNum: Int): AccountHoldRequestDto =
        AccountHoldRequestDto(
            requestId = rs.getString("request_id"),
            businessType = rs.getString("business_type"),
            businessReferenceId = rs.getString("business_reference_id"),
            targetCustomerId = rs.getString("target_customer_id"),
            targetAccountId = rs.getString("target_account_id"),
            requestedBy = rs.getString("requested_by"),
            requestedRole = rs.getString("requested_role"),
            reason = rs.getString("reason"),
            reasonCode = rs.getString("reason_code"),
            holdAmountMinor = rs.getLong("hold_amount_minor"),
            status = rs.getString("status"),
            approvalId = rs.getString("approval_id"),
            idempotencyKey = rs.getString("idempotency_key"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
            executedAt = rs.getObject("executed_at", OffsetDateTime::class.java),
            metadata = readMetadata(rs.getString("metadata_json"))
        )

    private fun readMetadata(metadataJson: String?): Map<String, Any?> {
        if (metadataJson.isNullOrBlank()) {
            return emptyMap()
        }
        return objectMapper.readValue(
            metadataJson,
            objectMapper.typeFactory.constructMapType(LinkedHashMap::class.java, String::class.java, Any::class.java)
        )
    }

    private fun mapCustomer(rs: ResultSet, rowNum: Int): StaffCustomerRecord =
        StaffCustomerRecord(
            customerId = rs.getString("customer_id"),
            name = rs.getString("customer_name"),
            phone = rs.getString("customer_phone"),
            address = rs.getString("customer_address"),
            customerGrade = rs.getString("customer_grade"),
            riskGrade = rs.getString("risk_grade")
        )

    private fun mapAccount(rs: ResultSet, rowNum: Int): StaffAccountDto =
        mapAccountRecord(rs, rowNum).toDto()

    private fun mapAccountRecord(rs: ResultSet, rowNum: Int): StaffAccountRecord =
        StaffAccountRecord(
            customerId = rs.getString("customer_id"),
            accountId = rs.getString("account_id"),
            accountNo = rs.getString("account_no"),
            status = rs.getString("status"),
            currency = rs.getString("currency"),
            ledgerBalanceMinor = rs.getLong("ledger_balance_minor"),
            availableBalanceMinor = rs.getLong("available_balance_minor"),
            holdAmountMinor = rs.getLong("hold_amount_minor")
        )

    private fun mapTransferLimit(rs: ResultSet, rowNum: Int): StaffTransferLimitDto =
        mapTransferLimitRecord(rs, rowNum).toDto()

    private fun mapTransferLimitRecord(rs: ResultSet, rowNum: Int): StaffTransferLimitRecord =
        StaffTransferLimitRecord(
            customerId = rs.getString("customer_id"),
            accountId = rs.getString("account_id"),
            accountNo = rs.getString("account_no"),
            accountStatus = rs.getString("account_status"),
            currency = rs.getString("currency"),
            dailyTransferLimitMinor = rs.getLong("daily_transfer_limit_minor"),
            singleTransferLimitMinor = rs.getLong("single_transfer_limit_minor"),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java)
        )

    private fun mapKycProfileRecord(rs: ResultSet, rowNum: Int): StaffKycProfileRecord =
        StaffKycProfileRecord(
            customerId = rs.getString("customer_id"),
            kycStatus = rs.getString("kyc_status"),
            sourceOfFundsCode = rs.getString("source_of_funds_code"),
            transactionPurposeCode = rs.getString("transaction_purpose_code"),
            simulatedProviderReference = rs.getString("simulated_provider_reference"),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java)
        )

    private fun StaffAccountRecord.toDto(): StaffAccountDto =
        StaffAccountDto(
            customerId = customerId,
            accountId = accountId,
            maskedAccountNo = maskAccountNo(accountNo),
            status = status,
            currency = currency,
            ledgerBalanceMinor = ledgerBalanceMinor,
            availableBalanceMinor = availableBalanceMinor,
            holdAmountMinor = holdAmountMinor
        )

    private fun StaffTransferLimitRecord.toDto(): StaffTransferLimitDto =
        StaffTransferLimitDto(
            customerId = customerId,
            accountId = accountId,
            maskedAccountNo = maskAccountNo(accountNo),
            accountStatus = accountStatus,
            currency = currency,
            dailyTransferLimitMinor = dailyTransferLimitMinor,
            singleTransferLimitMinor = singleTransferLimitMinor,
            updatedAt = updatedAt
        )

    private fun StaffKycProfileRecord.toDto(): StaffKycProfileDto =
        StaffKycProfileDto(
            customerId = customerId,
            kycStatus = kycStatus,
            sourceOfFundsCode = sourceOfFundsCode,
            transactionPurposeCode = transactionPurposeCode,
            simulatedProviderReference = simulatedProviderReference,
            updatedAt = updatedAt
        )

    private fun StaffAccountRecord.snapshot(): Map<String, Any?> =
        mapOf(
            "customerId" to customerId,
            "accountId" to accountId,
            "maskedAccountNo" to maskAccountNo(accountNo),
            "status" to status,
            "currency" to currency,
            "ledgerBalanceMinor" to ledgerBalanceMinor,
            "availableBalanceMinor" to availableBalanceMinor,
            "holdAmountMinor" to holdAmountMinor
        )

    private fun StaffTransferLimitRecord.snapshot(): Map<String, Any?> =
        mapOf(
            "customerId" to customerId,
            "accountId" to accountId,
            "maskedAccountNo" to maskAccountNo(accountNo),
            "accountStatus" to accountStatus,
            "currency" to currency,
            "dailyTransferLimitMinor" to dailyTransferLimitMinor,
            "singleTransferLimitMinor" to singleTransferLimitMinor
        )

    private fun StaffKycProfileRecord.snapshot(): Map<String, Any?> =
        mapOf(
            "customerId" to customerId,
            "kycStatus" to kycStatus,
            "sourceOfFundsCode" to sourceOfFundsCode,
            "transactionPurposeCode" to transactionPurposeCode,
            "simulatedProviderReference" to simulatedProviderReference
        )

    private fun mapTransaction(rs: ResultSet, rowNum: Int): StaffTransactionDto =
        StaffTransactionDto(
            ledgerTransactionId = rs.getString("ledger_transaction_id"),
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

    private fun maskedCustomer(customer: StaffCustomerRecord): MaskedCustomerDto =
        MaskedCustomerDto(
            customerId = customer.customerId,
            maskedName = maskName(customer.name),
            maskedPhone = maskPhone(customer.phone),
            maskedAddress = maskAddress(customer.address),
            customerGrade = customer.customerGrade,
            riskGrade = customer.riskGrade
        )

    private fun customerDetail(customer: StaffCustomerRecord, unmasked: Boolean): StaffCustomerDetailDto =
        if (unmasked) {
            StaffCustomerDetailDto(
                customerId = customer.customerId,
                piiExposure = "UNMASKED_TIMEBOXED",
                name = customer.name,
                phone = customer.phone,
                address = customer.address,
                customerGrade = customer.customerGrade,
                riskGrade = customer.riskGrade
            )
        } else {
            StaffCustomerDetailDto(
                customerId = customer.customerId,
                piiExposure = "MASKED",
                maskedName = maskName(customer.name),
                maskedPhone = maskPhone(customer.phone),
                maskedAddress = maskAddress(customer.address),
                customerGrade = customer.customerGrade,
                riskGrade = customer.riskGrade
            )
        }

    private fun appendAudit(
        eventType: String,
        actorId: String,
        actorRole: String,
        screenId: String,
        customerId: String?,
        accountId: String?,
        reason: String?,
        payload: Map<String, Any?>
    ): String {
        return auditEvents.append(
            eventType = eventType,
            actorType = "STAFF",
            actorId = actorId,
            actorRole = actorRole,
            screenId = screenId,
            customerId = customerId,
            accountId = accountId,
            reason = reason,
            payload = payload
        )
    }

    private fun appendMaskingLog(
        auditEventId: String,
        screenId: String,
        customerId: String?,
        accountId: String?,
        maskingPolicy: String,
        accessLevel: String,
        reason: String
    ) {
        jdbc.update(
            """
            INSERT INTO masking_access_logs (
              masking_access_log_id, screen_id, customer_id, account_id,
              masking_policy, access_level, reason, audit_event_id
            )
            VALUES (
              :maskingAccessLogId, :screenId, :customerId, :accountId,
              :maskingPolicy, :accessLevel, :reason, :auditEventId
            )
            """.trimIndent(),
            mapOf(
                "maskingAccessLogId" to "MSK-${UUID.randomUUID().toString().uppercase()}",
                "screenId" to screenId,
                "customerId" to customerId,
                "accountId" to accountId,
                "maskingPolicy" to maskingPolicy,
                "accessLevel" to accessLevel,
                "reason" to reason,
                "auditEventId" to auditEventId
            )
        )
    }

    private fun maskName(name: String?): String {
        if (name.isNullOrEmpty()) {
            return ""
        }
        if (name.length <= 2) {
            return "${name.first()}*"
        }
        return "${name.first()}${"*".repeat(name.length - 2)}${name.last()}"
    }

    private fun maskPhone(phone: String?): String? =
        phone?.replace(Regex("(\\d{3})-\\d{4}-(\\d{4})"), "$1-****-$2")

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

    private fun maskAddress(address: String?): String? {
        if (address.isNullOrBlank()) {
            return null
        }
        val parts = address.split(" ")
        return listOfNotNull(parts.getOrNull(0), parts.getOrNull(1), "***").joinToString(" ")
    }

    private data class StaffAccountRecord(
        val customerId: String,
        val accountId: String,
        val accountNo: String,
        val status: String,
        val currency: String,
        val ledgerBalanceMinor: Long,
        val availableBalanceMinor: Long,
        val holdAmountMinor: Long
    )

    private data class StaffTransferLimitRecord(
        val customerId: String,
        val accountId: String,
        val accountNo: String,
        val accountStatus: String,
        val currency: String,
        val dailyTransferLimitMinor: Long,
        val singleTransferLimitMinor: Long,
        val updatedAt: OffsetDateTime
    )

    private data class StaffKycProfileRecord(
        val customerId: String,
        val kycStatus: String,
        val sourceOfFundsCode: String,
        val transactionPurposeCode: String,
        val simulatedProviderReference: String,
        val updatedAt: OffsetDateTime
    )

    private data class TransactionCorrectionPostingRow(
        val transactionId: String,
        val transactionType: String,
        val status: String,
        val businessDate: LocalDate,
        val requestedBy: String,
        val requestedChannel: String,
        val originalTransactionId: String?,
        val accountId: String,
        val direction: String,
        val amountMinor: Long,
        val currency: String,
        val customerId: String
    )

    private data class TransactionCorrectionTarget(
        val transactionId: String,
        val transactionType: String,
        val status: String,
        val businessDate: LocalDate,
        val requestedBy: String,
        val requestedChannel: String,
        val originalTransactionId: String?,
        val customerId: String,
        val accountId: String,
        val currency: String,
        val accountPostingAmountMinor: Long,
        val postingsCount: Int
    ) {
        fun snapshot(): Map<String, Any?> =
            mapOf(
                "ledgerTransactionId" to transactionId,
                "transactionType" to transactionType,
                "status" to status,
                "businessDate" to businessDate.toString(),
                "requestedBy" to requestedBy,
                "requestedChannel" to requestedChannel,
                "originalTransactionId" to originalTransactionId,
                "targetCustomerId" to customerId,
                "targetAccountId" to accountId,
                "currency" to currency,
                "targetPostingAmountMinor" to accountPostingAmountMinor,
                "postingsCount" to postingsCount
            )
    }

    private companion object {
        const val SERIALIZABLE_APPROVAL_MAX_ATTEMPTS = 5
        const val SERIALIZABLE_STAFF_ACCESS_MAX_ATTEMPTS = 5
        val ACCOUNT_HOLD_REQUEST_ROLES = setOf("BRANCH_MANAGER", "CALL_CENTER_MANAGER")
        val ACCOUNT_HOLD_RELEASE_REQUEST_ROLES = setOf("BRANCH_MANAGER", "OPS_MANAGER")
        val ACCOUNT_HOLD_CHECKER_ROLES = setOf("BRANCH_MANAGER", "COMPLIANCE_MANAGER")
        val ACCOUNT_HOLD_RELEASE_CHECKER_ROLES = setOf("OPS_MANAGER", "BRANCH_MANAGER", "COMPLIANCE_MANAGER")
        val TRANSFER_LIMIT_CHANGE_REQUEST_ROLES = setOf("BRANCH_STAFF", "BRANCH_MANAGER")
        val TRANSFER_LIMIT_CHANGE_CHECKER_ROLES = setOf("BRANCH_MANAGER", "COMPLIANCE_MANAGER")
        val CUSTOMER_KYC_REVIEW_REQUEST_ROLES = setOf("BRANCH_STAFF", "BRANCH_MANAGER", "COMPLIANCE_MANAGER")
        val CUSTOMER_KYC_REVIEW_CHECKER_ROLES = setOf("BRANCH_MANAGER", "COMPLIANCE_MANAGER")
        val FEE_WAIVER_REQUEST_ROLES = setOf("BRANCH_STAFF", "BRANCH_MANAGER", "CALL_CENTER_MANAGER")
        val FEE_WAIVER_CHECKER_ROLES = setOf("BRANCH_MANAGER", "COMPLIANCE_MANAGER")
        val TRANSACTION_CORRECTION_REQUEST_ROLES = setOf("BRANCH_MANAGER", "OPS_MANAGER")
        val TRANSACTION_CORRECTION_CHECKER_ROLES = setOf("BRANCH_MANAGER", "OPS_MANAGER", "COMPLIANCE_MANAGER")
    }
}
