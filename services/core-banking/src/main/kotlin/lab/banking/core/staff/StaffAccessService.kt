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
import lab.banking.core.approval.PersistentApprovalService
import lab.banking.core.approval.SubmitApprovalCommand
import lab.banking.core.complaint.ComplaintCaseService
import lab.banking.core.fds.FdsCaseService
import lab.banking.core.reconciliation.ReconciliationOpsService
import lab.banking.core.security.BankingLabAuthContext
import lab.banking.core.workflow.WorkflowErrors
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
    fun unmaskCustomer(command: PiiUnmaskCommand): StaffUnmaskResponse {
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

    fun approveStaffRequest(approvalId: String, command: ApproveApprovalCommand): StaffApprovalExecutionResponse {
        return runSerializableApprovalExecution {
            approveStaffRequestInTransaction(approvalId, command)
        }
    }

    private fun approveStaffRequestInTransaction(approvalId: String, command: ApproveApprovalCommand): StaffApprovalExecutionResponse {
        BankingLabAuthContext.requireActor(command.approvedBy, command.approvedByRole)
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
        return StaffApprovalExecutionResponse(
            item = approval,
            executed = customerExecuted || complaint != null || fdsExecution != null || amlCase != null || reconciliationExecution != null,
            customer = customer,
            complaint = complaint,
            fdsCase = fdsExecution?.item,
            amlCase = amlCase,
            reconciliationItem = reconciliationExecution?.item,
            ledgerTransaction = fdsExecution?.ledgerTransaction ?: reconciliationExecution?.ledgerTransaction
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

    private fun customer(customerId: String): StaffCustomerRecord =
        jdbc.queryForObject(
            """
            SELECT customer_id, customer_name, customer_phone, customer_address, customer_grade, risk_grade
            FROM customers
            WHERE customer_id = :customerId
            """.trimIndent(),
            mapOf("customerId" to customerId),
            this::mapCustomer
        ) ?: throw WorkflowErrors.notFound("customer not found: $customerId")

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
        StaffAccountDto(
            customerId = rs.getString("customer_id"),
            accountId = rs.getString("account_id"),
            maskedAccountNo = maskAccountNo(rs.getString("account_no")),
            status = rs.getString("status"),
            currency = rs.getString("currency"),
            ledgerBalanceMinor = rs.getLong("ledger_balance_minor"),
            availableBalanceMinor = rs.getLong("available_balance_minor"),
            holdAmountMinor = rs.getLong("hold_amount_minor")
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

    private companion object {
        const val SERIALIZABLE_APPROVAL_MAX_ATTEMPTS = 5
        const val SERIALIZABLE_STAFF_ACCESS_MAX_ATTEMPTS = 5
    }
}
