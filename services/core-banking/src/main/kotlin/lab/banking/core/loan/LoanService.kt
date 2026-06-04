package lab.banking.core.loan

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import lab.banking.core.approval.ApprovalBusinessTypes
import lab.banking.core.approval.ApprovalServicePort
import lab.banking.core.approval.ApproveApprovalCommand
import lab.banking.core.approval.OperatorApproval
import lab.banking.core.approval.SubmitApprovalCommand
import lab.banking.core.audit.AuditEventAppender
import lab.banking.core.ledger.application.DisburseLoanCommand
import lab.banking.core.ledger.application.LedgerCommandService
import lab.banking.core.ledger.application.LoanRepaymentCommand
import lab.banking.core.ledger.domain.LedgerCommandResult
import lab.banking.core.security.BankingLabAuthContext
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class LoanService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val approvals: ApprovalServicePort,
    private val ledgerCommandService: LedgerCommandService,
    private val auditEvents: AuditEventAppender
) {
    @Transactional(readOnly = true)
    fun products(): LoanProductListResponse =
        LoanProductListResponse(
            jdbc.query(
                """
                SELECT product_id, product_code, product_name, currency, annual_rate_bps,
                       term_months, minimum_amount_minor, maximum_amount_minor,
                       approval_threshold_minor, status, synthetic_only
                FROM loan_products
                ORDER BY product_code
                """.trimIndent(),
                emptyMap<String, Any?>(),
                this::mapProduct
            )
        )

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun apply(command: LoanApplicationCommand): LoanApplicationResponse {
        val actor = requestActor(command)
        requireReason(command.reason, "LOAN_EXECUTION requires a business reason")
        val idempotencyKey = requireNonBlank(command.idempotencyKey, "idempotencyKey")
        BankingLabAuthContext.requireCustomerOwnership(command.customerId)
        val existing = applicationByIdempotency(actor.actorId, idempotencyKey)
        if (existing != null) {
            return LoanApplicationResponse(existing, existing.approvalId?.let(approvals::approval), replayed = true)
        }
        val product = product(command.productId)
        if (product.status != "ACTIVE") {
            throw WorkflowErrors.stateViolation("loan product is not active: ${product.status}")
        }
        if (command.requestedAmountMinor < product.minimumAmountMinor || command.requestedAmountMinor > product.maximumAmountMinor) {
            throw WorkflowErrors.validation("requestedAmountMinor must fit the synthetic loan product amount range")
        }
        val termMonths = command.requestedTermMonths ?: product.termMonths
        if (termMonths <= 0 || termMonths > product.termMonths) {
            throw WorkflowErrors.validation("requestedTermMonths must be between 1 and product termMonths")
        }
        val account = account(command.depositAccountId)
        if (account.customerId != command.customerId) {
            throw WorkflowErrors.authorizationViolation("loan disbursement account must belong to the application customer")
        }
        if (account.status != "ACTIVE") {
            throw WorkflowErrors.stateViolation("loan disbursement account must be active")
        }
        val underwriting = underwritingDecision(
            amountMinor = command.requestedAmountMinor,
            monthlyIncomeMinor = command.syntheticMonthlyIncomeMinor,
            monthlyDebtMinor = command.syntheticMonthlyDebtMinor,
            creditGrade = command.syntheticCreditGrade,
            riskGrade = command.syntheticRiskGrade
        )
        val applicationId = "LAPP-${UUID.randomUUID().toString().uppercase()}"
        val status = if (underwriting.decision == "DECLINE") "DECLINED" else "PENDING_APPROVAL"
        val approval = if (status == "PENDING_APPROVAL") {
            approvals.submit(
                SubmitApprovalCommand(
                    businessType = ApprovalBusinessTypes.LOAN_EXECUTION,
                    businessReferenceId = applicationId,
                    requestedBy = actor.actorId,
                    requestedByRole = actor.actorRole,
                    requestReason = command.reason,
                    beforeSnapshot = mapOf(
                        "customerId" to command.customerId,
                        "depositAccountId" to command.depositAccountId,
                        "syntheticOnly" to true
                    ),
                    afterSnapshot = mapOf(
                        "productId" to product.productId,
                        "requestedAmountMinor" to command.requestedAmountMinor,
                        "requestedTermMonths" to termMonths,
                        "underwritingScore" to underwriting.score,
                        "underwritingDecision" to underwriting.decision,
                        "syntheticOnly" to true
                    ),
                    screenId = "LON-101"
                )
            )
        } else {
            null
        }
        jdbc.update(
            """
            INSERT INTO loan_applications (
              application_id, customer_id, deposit_account_id, product_id,
              requested_amount_minor, requested_term_months,
              synthetic_monthly_income_minor, synthetic_monthly_debt_minor,
              synthetic_credit_grade, synthetic_risk_grade,
              underwriting_score, underwriting_decision, status, approval_id,
              requested_by, requested_role, reason, idempotency_key, metadata_json
            )
            VALUES (
              :applicationId, :customerId, :depositAccountId, :productId,
              :requestedAmountMinor, :requestedTermMonths,
              :syntheticMonthlyIncomeMinor, :syntheticMonthlyDebtMinor,
              :syntheticCreditGrade, :syntheticRiskGrade,
              :underwritingScore, :underwritingDecision, :status, :approvalId,
              :requestedBy, :requestedRole, :reason, :idempotencyKey, CAST(:metadata AS jsonb)
            )
            """.trimIndent(),
            mapOf(
                "applicationId" to applicationId,
                "customerId" to command.customerId,
                "depositAccountId" to command.depositAccountId,
                "productId" to product.productId,
                "requestedAmountMinor" to command.requestedAmountMinor,
                "requestedTermMonths" to termMonths,
                "syntheticMonthlyIncomeMinor" to command.syntheticMonthlyIncomeMinor,
                "syntheticMonthlyDebtMinor" to command.syntheticMonthlyDebtMinor,
                "syntheticCreditGrade" to command.syntheticCreditGrade,
                "syntheticRiskGrade" to command.syntheticRiskGrade,
                "underwritingScore" to underwriting.score,
                "underwritingDecision" to underwriting.decision,
                "status" to status,
                "approvalId" to approval?.approvalId,
                "requestedBy" to actor.actorId,
                "requestedRole" to actor.actorRole,
                "reason" to command.reason,
                "idempotencyKey" to idempotencyKey,
                "metadata" to objectMapper.writeValueAsString(mapOf("syntheticOnly" to true, "externalCreditBureauUsed" to false))
            )
        )
        appendAudit(
            eventType = "LOAN_APPLICATION_SUBMITTED",
            actor = actor,
            screenId = "CWB-501",
            businessReferenceId = applicationId,
            customerId = command.customerId,
            accountId = command.depositAccountId,
            reason = command.reason,
            payload = mapOf(
                "approvalId" to approval?.approvalId,
                "underwritingDecision" to underwriting.decision,
                "syntheticOnly" to true,
                "externalCreditBureauUsed" to false
            )
        )
        return LoanApplicationResponse(application(applicationId), approval, replayed = false)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun applyApprovedLoanExecution(approval: OperatorApproval, command: ApproveApprovalCommand): LoanExecutionResponse {
        if (approval.businessType != ApprovalBusinessTypes.LOAN_EXECUTION) {
            throw WorkflowErrors.stateViolation("approval is not a loan execution approval")
        }
        val application = applicationForUpdate(approval.businessReferenceId)
        if (application.approvalId != approval.approvalId) {
            throw WorkflowErrors.stateViolation("approval does not match loan application")
        }
        if (application.status == "EXECUTED") {
            val existingLoan = loanByApplication(application.applicationId)
            val transaction = existingLoan.disbursementTransactionId?.let(ledgerCommandService::transaction)
                ?: throw WorkflowErrors.stateViolation("executed loan has no disbursement transaction")
            return LoanExecutionResponse(application, existingLoan, LedgerCommandResult(transaction, replayed = true))
        }
        if (application.status != "PENDING_APPROVAL") {
            throw WorkflowErrors.stateViolation("loan application is not pending approval: ${application.status}")
        }
        val product = product(application.productId)
        val loanId = "LOAN-${UUID.randomUUID().toString().uppercase()}"
        jdbc.update(
            """
            INSERT INTO loans (
              loan_id, application_id, customer_id, deposit_account_id, product_id,
              principal_minor, outstanding_principal_minor, annual_rate_bps, term_months,
              status, next_due_date
            )
            VALUES (
              :loanId, :applicationId, :customerId, :depositAccountId, :productId,
              :principalMinor, :outstandingPrincipalMinor, :annualRateBps, :termMonths,
              'ACTIVE', :nextDueDate
            )
            """.trimIndent(),
            mapOf(
                "loanId" to loanId,
                "applicationId" to application.applicationId,
                "customerId" to application.customerId,
                "depositAccountId" to application.depositAccountId,
                "productId" to product.productId,
                "principalMinor" to application.requestedAmountMinor,
                "outstandingPrincipalMinor" to application.requestedAmountMinor,
                "annualRateBps" to product.annualRateBps,
                "termMonths" to application.requestedTermMonths,
                "nextDueDate" to LocalDate.now().plusMonths(1)
            )
        )
        insertSchedule(loanId, application.requestedAmountMinor, product.annualRateBps, application.requestedTermMonths, LocalDate.now().plusMonths(1), 1)
        val ledgerResult = ledgerCommandService.disburseLoan(
            DisburseLoanCommand(
                loanId = loanId,
                applicationId = application.applicationId,
                depositAccountId = application.depositAccountId,
                amountMinor = application.requestedAmountMinor,
                idempotencyKey = "LOAN-DISB-${application.applicationId}",
                approvalId = approval.approvalId,
                requestedBy = application.requestedBy,
                requestedChannel = "LOAN_SERVICE",
                businessDate = LocalDate.now(),
                reason = application.reason
            )
        )
        jdbc.update(
            """
            UPDATE loans
            SET disbursement_transaction_id = :transactionId,
                disbursed_at = now(),
                updated_at = now()
            WHERE loan_id = :loanId
            """.trimIndent(),
            mapOf("loanId" to loanId, "transactionId" to ledgerResult.value.id)
        )
        jdbc.update(
            """
            UPDATE loan_applications
            SET status = 'EXECUTED',
                executed_at = now(),
                updated_at = now()
            WHERE application_id = :applicationId
            """.trimIndent(),
            mapOf("applicationId" to application.applicationId)
        )
        appendAudit(
            eventType = "LOAN_EXECUTED",
            actor = ReadWriteActor(command.approvedBy, command.approvedByRole),
            screenId = command.screenId ?: "LON-102",
            businessReferenceId = loanId,
            customerId = application.customerId,
            accountId = application.depositAccountId,
            reason = application.reason,
            payload = mapOf(
                "approvalId" to approval.approvalId,
                "applicationId" to application.applicationId,
                "ledgerTransactionId" to ledgerResult.value.id,
                "syntheticOnly" to true,
                "ledgerSourceRowsMutated" to false
            )
        )
        return LoanExecutionResponse(application(approval.businessReferenceId), loanDto(loanRow(loanId)), ledgerResult)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun rejectLoanExecution(approval: OperatorApproval): LoanApplicationDto {
        if (approval.businessType != ApprovalBusinessTypes.LOAN_EXECUTION) {
            throw WorkflowErrors.stateViolation("approval is not a loan execution approval")
        }
        val application = applicationForUpdate(approval.businessReferenceId)
        if (application.status != "PENDING_APPROVAL") {
            throw WorkflowErrors.stateViolation("loan application is not pending approval: ${application.status}")
        }
        jdbc.update(
            """
            UPDATE loan_applications
            SET status = 'REJECTED',
                updated_at = now()
            WHERE application_id = :applicationId
            """.trimIndent(),
            mapOf("applicationId" to application.applicationId)
        )
        return application(application.applicationId)
    }

    @Transactional
    fun loan(loanId: String, reason: String?): LoanDto {
        val loan = loanRow(loanId)
        authorizeLoanRead(loan.customerId, reason)
        appendAudit(
            eventType = "LOAN_DETAIL_VIEW",
            actor = currentActorForAudit(),
            screenId = "CWB-502",
            businessReferenceId = loanId,
            customerId = loan.customerId,
            accountId = loan.depositAccountId,
            reason = reason,
            payload = mapOf("syntheticOnly" to true)
        )
        return loanDto(loan)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun repay(loanId: String, command: LoanPaymentCommand): LoanPaymentResponse {
        val loan = loanForUpdate(loanId)
        authorizeLoanWrite(loan.customerId, command.reason)
        val actor = paymentActor(command)
        val idempotencyKey = requireNonBlank(command.idempotencyKey, "idempotencyKey")
        existingPayment(loanId, idempotencyKey)?.let { payment ->
            val transaction = ledgerCommandService.transaction(payment.ledgerTransactionId)
                ?: throw WorkflowErrors.stateViolation("loan payment ledger transaction not found")
            return LoanPaymentResponse(payment, loanDto(loanRow(loanId)), LedgerCommandResult(transaction, replayed = true), replayed = true)
        }
        if (loan.status == "CLOSED") {
            throw WorkflowErrors.stateViolation("closed loan cannot accept repayment")
        }
        val due = nextPayableSchedule(loanId) ?: throw WorkflowErrors.stateViolation("loan has no payable schedule")
        val principalMinor = command.principalMinor ?: due.principalMinor
        val interestMinor = command.interestMinor ?: due.interestMinor
        if (principalMinor != due.principalMinor || interestMinor != due.interestMinor) {
            throw WorkflowErrors.validation("scheduled repayment must match the next unpaid schedule row")
        }
        val businessDate = command.businessDate ?: LocalDate.now()
        val ledgerResult = ledgerCommandService.repayLoan(
            LoanRepaymentCommand(
                loanId = loanId,
                depositAccountId = loan.depositAccountId,
                principalMinor = principalMinor,
                interestMinor = interestMinor,
                idempotencyKey = "LOAN-REPAY-$idempotencyKey",
                requestedBy = actor.actorId,
                requestedChannel = command.requestedChannel ?: "CUSTOMER_WEB",
                businessDate = businessDate,
                reason = requireReason(command.reason, "LOAN_REPAYMENT requires a business reason")
            )
        )
        val payment = insertPayment(
            loanId = loanId,
            paymentType = "SCHEDULED",
            principalMinor = principalMinor,
            interestMinor = interestMinor,
            businessDate = businessDate,
            idempotencyKey = idempotencyKey,
            ledgerTransactionId = ledgerResult.value.id,
            actor = actor,
            requestedChannel = command.requestedChannel ?: "CUSTOMER_WEB",
            reason = command.reason
        )
        markSchedulePaid(due.scheduleId, ledgerResult.value.id)
        updateLoanAfterPrincipalPayment(loanId, principalMinor)
        appendPaymentAudit("LOAN_REPAYMENT_POSTED", loan, payment, actor, command.reason)
        return LoanPaymentResponse(payment, loanDto(loanRow(loanId)), ledgerResult, replayed = false)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun prepay(loanId: String, command: LoanPaymentCommand): LoanPaymentResponse {
        val loan = loanForUpdate(loanId)
        authorizeLoanWrite(loan.customerId, command.reason)
        val actor = paymentActor(command)
        val idempotencyKey = requireNonBlank(command.idempotencyKey, "idempotencyKey")
        existingPayment(loanId, idempotencyKey)?.let { payment ->
            val transaction = ledgerCommandService.transaction(payment.ledgerTransactionId)
                ?: throw WorkflowErrors.stateViolation("loan prepayment ledger transaction not found")
            return LoanPaymentResponse(payment, loanDto(loanRow(loanId)), LedgerCommandResult(transaction, replayed = true), replayed = true)
        }
        if (loan.status == "CLOSED") {
            throw WorkflowErrors.stateViolation("closed loan cannot accept prepayment")
        }
        val principalMinor = command.principalMinor ?: loan.outstandingPrincipalMinor
        val interestMinor = command.interestMinor ?: 0L
        if (principalMinor <= 0 || principalMinor > loan.outstandingPrincipalMinor) {
            throw WorkflowErrors.validation("prepayment principalMinor must be positive and not exceed outstanding principal")
        }
        val businessDate = command.businessDate ?: LocalDate.now()
        val ledgerResult = ledgerCommandService.repayLoan(
            LoanRepaymentCommand(
                loanId = loanId,
                depositAccountId = loan.depositAccountId,
                principalMinor = principalMinor,
                interestMinor = interestMinor,
                idempotencyKey = "LOAN-PREPAY-$idempotencyKey",
                requestedBy = actor.actorId,
                requestedChannel = command.requestedChannel ?: "CUSTOMER_WEB",
                businessDate = businessDate,
                reason = requireReason(command.reason, "LOAN_PREPAYMENT requires a business reason"),
                prepayment = true
            )
        )
        val payment = insertPayment(
            loanId = loanId,
            paymentType = "PREPAYMENT",
            principalMinor = principalMinor,
            interestMinor = interestMinor,
            businessDate = businessDate,
            idempotencyKey = idempotencyKey,
            ledgerTransactionId = ledgerResult.value.id,
            actor = actor,
            requestedChannel = command.requestedChannel ?: "CUSTOMER_WEB",
            reason = command.reason
        )
        updateLoanAfterPrincipalPayment(loanId, principalMinor)
        recomputeScheduleAfterPrepayment(loanId, businessDate)
        appendPaymentAudit("LOAN_PREPAYMENT_POSTED", loan, payment, actor, command.reason)
        return LoanPaymentResponse(payment, loanDto(loanRow(loanId)), ledgerResult, replayed = false)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun runAccrual(loanId: String, command: LoanAccrualCommand): LoanAccrualResponse {
        BankingLabAuthContext.requireActor(command.requestedBy, command.actorRole)
        requireRole(command.actorRole, ACCRUAL_ROLES, "actor role cannot run loan accrual")
        requireReason(command.reason, "LOAN_INTEREST_ACCRUAL requires a business reason")
        existingAccrual(loanId, command.accrualDate)?.let {
            return LoanAccrualResponse(it, loanDto(loanRow(loanId)), replayed = true)
        }
        val loan = loanForUpdate(loanId)
        if (loan.status == "CLOSED") {
            throw WorkflowErrors.stateViolation("closed loan cannot accrue interest")
        }
        val earliestOverdue = earliestPendingDueDateBefore(loanId, command.accrualDate)
        val overdueDays = earliestOverdue?.let { ChronoUnit.DAYS.between(it, command.accrualDate).toInt().coerceAtLeast(0) } ?: 0
        if (overdueDays > 0) {
            jdbc.update(
                """
                UPDATE loan_repayment_schedule
                SET status = 'OVERDUE'
                WHERE loan_id = :loanId
                  AND status = 'PENDING'
                  AND due_date < :accrualDate
                """.trimIndent(),
                mapOf("loanId" to loanId, "accrualDate" to command.accrualDate)
            )
        }
        val interestMinor = calculateDailyInterest(loan.outstandingPrincipalMinor, loan.annualRateBps)
        val accrualId = "LACR-${UUID.randomUUID().toString().uppercase()}"
        jdbc.update(
            """
            INSERT INTO loan_interest_accruals (
              accrual_id, loan_id, accrual_date, outstanding_principal_minor,
              annual_rate_bps, interest_minor, overdue_days, status
            )
            VALUES (
              :accrualId, :loanId, :accrualDate, :outstandingPrincipalMinor,
              :annualRateBps, :interestMinor, :overdueDays, 'CALCULATED'
            )
            ON CONFLICT (loan_id, accrual_date) DO NOTHING
            """.trimIndent(),
            mapOf(
                "accrualId" to accrualId,
                "loanId" to loanId,
                "accrualDate" to command.accrualDate,
                "outstandingPrincipalMinor" to loan.outstandingPrincipalMinor,
                "annualRateBps" to loan.annualRateBps,
                "interestMinor" to interestMinor,
                "overdueDays" to overdueDays
            )
        )
        jdbc.update(
            """
            UPDATE loans
            SET status = CASE WHEN :overdueDays > 0 THEN 'OVERDUE' ELSE status END,
                overdue_days = :overdueDays,
                updated_at = now()
            WHERE loan_id = :loanId
            """.trimIndent(),
            mapOf("loanId" to loanId, "overdueDays" to overdueDays)
        )
        appendAudit(
            eventType = "LOAN_INTEREST_ACCRUAL_RUN",
            actor = ReadWriteActor(command.requestedBy, command.actorRole),
            screenId = "LON-103",
            businessReferenceId = loanId,
            customerId = loan.customerId,
            accountId = loan.depositAccountId,
            reason = command.reason,
            payload = mapOf(
                "accrualDate" to command.accrualDate.toString(),
                "interestMinor" to interestMinor,
                "overdueDays" to overdueDays,
                "syntheticOnly" to true
            )
        )
        val item = existingAccrual(loanId, command.accrualDate)
            ?: throw WorkflowErrors.stateViolation("loan accrual was not persisted")
        return LoanAccrualResponse(item, loanDto(loanRow(loanId)), replayed = false)
    }

    private fun requestActor(command: LoanApplicationCommand): ReadWriteActor {
        val principal = BankingLabAuthContext.get()
        val actorId = command.requestedBy ?: principal?.subject ?: "branch01"
        val actorRole = command.requestedByRole ?: principal?.roles?.firstOrNull() ?: "BRANCH_STAFF"
        BankingLabAuthContext.requireActor(actorId, actorRole)
        if (principal?.roles?.contains("CUSTOMER") == true) {
            BankingLabAuthContext.requireCustomerOwnership(command.customerId)
            return ReadWriteActor(actorId, "CUSTOMER")
        }
        requireRole(actorRole, APPLICATION_ROLES, "actor role cannot submit loan applications")
        return ReadWriteActor(actorId, actorRole)
    }

    private fun paymentActor(command: LoanPaymentCommand): ReadWriteActor {
        val principal = BankingLabAuthContext.get()
        val actorId = command.requestedBy ?: principal?.subject ?: "customer01"
        val actorRole = principal?.roles?.firstOrNull() ?: "CUSTOMER"
        BankingLabAuthContext.requireActor(actorId, actorRole)
        requireReason(command.reason, "LOAN_PAYMENT requires a business reason")
        return ReadWriteActor(actorId, actorRole)
    }

    private fun authorizeLoanRead(customerId: String, reason: String?) {
        val principal = BankingLabAuthContext.get() ?: return
        if (principal.roles.contains("CUSTOMER")) {
            BankingLabAuthContext.requireCustomerOwnership(customerId)
            return
        }
        if (!principal.hasAnyRole(LOAN_READ_ROLES)) {
            throw WorkflowErrors.authorizationViolation("actor role cannot read loan data")
        }
        if (reason.isNullOrBlank()) {
            throw WorkflowErrors.reasonRequired("staff loan access requires a business reason")
        }
    }

    private fun authorizeLoanWrite(customerId: String, reason: String?) {
        val principal = BankingLabAuthContext.get() ?: return
        if (principal.roles.contains("CUSTOMER")) {
            BankingLabAuthContext.requireCustomerOwnership(customerId)
            return
        }
        if (!principal.hasAnyRole(LOAN_WRITE_ROLES)) {
            throw WorkflowErrors.authorizationViolation("actor role cannot operate loan repayment")
        }
        if (reason.isNullOrBlank()) {
            throw WorkflowErrors.reasonRequired("staff loan operation requires a business reason")
        }
    }

    private fun currentActorForAudit(): ReadWriteActor {
        val principal = BankingLabAuthContext.get() ?: return ReadWriteActor("SYSTEM", "SYSTEM")
        return ReadWriteActor(principal.subject, principal.roles.sorted().joinToString(","))
    }

    private fun requireRole(role: String, allowedRoles: Set<String>, message: String) {
        if (role !in allowedRoles) {
            throw WorkflowErrors.authorizationViolation(message)
        }
    }

    private fun requireNonBlank(value: String?, field: String): String {
        if (value.isNullOrBlank()) {
            throw WorkflowErrors.validation("$field is required")
        }
        return value
    }

    private fun requireReason(value: String?, message: String): String {
        if (value.isNullOrBlank()) {
            throw WorkflowErrors.reasonRequired(message)
        }
        return value
    }

    private fun product(productId: String): LoanProductDto =
        try {
            jdbc.queryForObject(
                """
                SELECT product_id, product_code, product_name, currency, annual_rate_bps,
                       term_months, minimum_amount_minor, maximum_amount_minor,
                       approval_threshold_minor, status, synthetic_only
                FROM loan_products
                WHERE product_id = :productId
                """.trimIndent(),
                mapOf("productId" to productId),
                this::mapProduct
            ) ?: throw WorkflowErrors.notFound("loan product not found: $productId")
        } catch (_: EmptyResultDataAccessException) {
            throw WorkflowErrors.notFound("loan product not found: $productId")
        }

    private fun mapProduct(rs: ResultSet, rowNum: Int): LoanProductDto =
        LoanProductDto(
            productId = rs.getString("product_id"),
            productCode = rs.getString("product_code"),
            productName = rs.getString("product_name"),
            currency = rs.getString("currency").trim(),
            annualRateBps = rs.getInt("annual_rate_bps"),
            termMonths = rs.getInt("term_months"),
            minimumAmountMinor = rs.getLong("minimum_amount_minor"),
            maximumAmountMinor = rs.getLong("maximum_amount_minor"),
            approvalThresholdMinor = rs.getLong("approval_threshold_minor"),
            status = rs.getString("status"),
            syntheticOnly = rs.getBoolean("synthetic_only")
        )

    private fun account(accountId: String): AccountRow =
        try {
            jdbc.queryForObject(
                """
                SELECT account_id, customer_id, currency, status
                FROM accounts
                WHERE account_id = :accountId
                FOR UPDATE
                """.trimIndent(),
                mapOf("accountId" to accountId),
                this::mapAccount
            ) ?: throw WorkflowErrors.notFound("account not found: $accountId")
        } catch (_: EmptyResultDataAccessException) {
            throw WorkflowErrors.notFound("account not found: $accountId")
        }

    private fun mapAccount(rs: ResultSet, rowNum: Int): AccountRow =
        AccountRow(
            accountId = rs.getString("account_id"),
            customerId = rs.getString("customer_id"),
            currency = rs.getString("currency").trim(),
            status = rs.getString("status")
        )

    private fun application(applicationId: String): LoanApplicationDto =
        try {
            jdbc.queryForObject(applicationSql("WHERE application_id = :applicationId"), mapOf("applicationId" to applicationId), this::mapApplication)
                ?: throw WorkflowErrors.notFound("loan application not found: $applicationId")
        } catch (_: EmptyResultDataAccessException) {
            throw WorkflowErrors.notFound("loan application not found: $applicationId")
        }

    private fun applicationForUpdate(applicationId: String): LoanApplicationDto =
        try {
            jdbc.queryForObject(
                applicationSql("WHERE application_id = :applicationId FOR UPDATE"),
                mapOf("applicationId" to applicationId),
                this::mapApplication
            ) ?: throw WorkflowErrors.notFound("loan application not found: $applicationId")
        } catch (_: EmptyResultDataAccessException) {
            throw WorkflowErrors.notFound("loan application not found: $applicationId")
        }

    private fun applicationByIdempotency(requestedBy: String, idempotencyKey: String): LoanApplicationDto? =
        jdbc.query(
            applicationSql("WHERE requested_by = :requestedBy AND idempotency_key = :idempotencyKey"),
            mapOf("requestedBy" to requestedBy, "idempotencyKey" to idempotencyKey),
            this::mapApplication
        ).firstOrNull()

    private fun applicationSql(whereClause: String): String =
        """
        SELECT application_id, customer_id, deposit_account_id, product_id,
               requested_amount_minor, requested_term_months,
               synthetic_credit_grade, synthetic_risk_grade,
               underwriting_score, underwriting_decision, status, approval_id,
               requested_by, requested_role, reason, created_at, updated_at, executed_at
        FROM loan_applications
        $whereClause
        """.trimIndent()

    private fun mapApplication(rs: ResultSet, rowNum: Int): LoanApplicationDto =
        LoanApplicationDto(
            applicationId = rs.getString("application_id"),
            customerId = rs.getString("customer_id"),
            depositAccountId = rs.getString("deposit_account_id"),
            productId = rs.getString("product_id"),
            requestedAmountMinor = rs.getLong("requested_amount_minor"),
            requestedTermMonths = rs.getInt("requested_term_months"),
            syntheticCreditGrade = rs.getString("synthetic_credit_grade"),
            syntheticRiskGrade = rs.getString("synthetic_risk_grade"),
            underwritingScore = rs.getInt("underwriting_score"),
            underwritingDecision = rs.getString("underwriting_decision"),
            status = rs.getString("status"),
            approvalId = rs.getString("approval_id"),
            requestedBy = rs.getString("requested_by"),
            requestedRole = rs.getString("requested_role"),
            reason = rs.getString("reason"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
            executedAt = rs.getObject("executed_at", OffsetDateTime::class.java)
        )

    private fun loanRow(loanId: String): LoanRow =
        try {
            jdbc.queryForObject(loanSql("WHERE loan_id = :loanId"), mapOf("loanId" to loanId), this::mapLoanRow)
                ?: throw WorkflowErrors.notFound("loan not found: $loanId")
        } catch (_: EmptyResultDataAccessException) {
            throw WorkflowErrors.notFound("loan not found: $loanId")
        }

    private fun loanForUpdate(loanId: String): LoanRow =
        try {
            jdbc.queryForObject(loanSql("WHERE loan_id = :loanId FOR UPDATE"), mapOf("loanId" to loanId), this::mapLoanRow)
                ?: throw WorkflowErrors.notFound("loan not found: $loanId")
        } catch (_: EmptyResultDataAccessException) {
            throw WorkflowErrors.notFound("loan not found: $loanId")
        }

    private fun loanByApplication(applicationId: String): LoanDto =
        try {
            val row = jdbc.queryForObject(loanSql("WHERE application_id = :applicationId"), mapOf("applicationId" to applicationId), this::mapLoanRow)
                ?: throw WorkflowErrors.notFound("loan not found for application: $applicationId")
            loanDto(row)
        } catch (_: EmptyResultDataAccessException) {
            throw WorkflowErrors.notFound("loan not found for application: $applicationId")
        }

    private fun loanSql(whereClause: String): String =
        """
        SELECT loan_id, application_id, customer_id, deposit_account_id, product_id,
               principal_minor, outstanding_principal_minor, annual_rate_bps, term_months,
               status, disbursement_transaction_id, next_due_date, overdue_days, disbursed_at
        FROM loans
        $whereClause
        """.trimIndent()

    private fun mapLoanRow(rs: ResultSet, rowNum: Int): LoanRow =
        LoanRow(
            loanId = rs.getString("loan_id"),
            applicationId = rs.getString("application_id"),
            customerId = rs.getString("customer_id"),
            depositAccountId = rs.getString("deposit_account_id"),
            productId = rs.getString("product_id"),
            principalMinor = rs.getLong("principal_minor"),
            outstandingPrincipalMinor = rs.getLong("outstanding_principal_minor"),
            annualRateBps = rs.getInt("annual_rate_bps"),
            termMonths = rs.getInt("term_months"),
            status = rs.getString("status"),
            disbursementTransactionId = rs.getString("disbursement_transaction_id"),
            nextDueDate = rs.getObject("next_due_date", LocalDate::class.java),
            overdueDays = rs.getInt("overdue_days"),
            disbursedAt = rs.getObject("disbursed_at", OffsetDateTime::class.java)
        )

    private fun loanDto(row: LoanRow): LoanDto =
        LoanDto(
            loanId = row.loanId,
            applicationId = row.applicationId,
            customerId = row.customerId,
            depositAccountId = row.depositAccountId,
            productId = row.productId,
            principalMinor = row.principalMinor,
            outstandingPrincipalMinor = row.outstandingPrincipalMinor,
            annualRateBps = row.annualRateBps,
            termMonths = row.termMonths,
            status = row.status,
            disbursementTransactionId = row.disbursementTransactionId,
            nextDueDate = row.nextDueDate,
            overdueDays = row.overdueDays,
            disbursedAt = row.disbursedAt,
            schedule = schedule(row.loanId)
        )

    private fun schedule(loanId: String): List<LoanScheduleItemDto> =
        jdbc.query(
            """
            SELECT schedule_id, installment_no, due_date, principal_minor, interest_minor,
                   total_minor, status, ledger_transaction_id, paid_at
            FROM loan_repayment_schedule
            WHERE loan_id = :loanId
            ORDER BY due_date, installment_no, schedule_id
            """.trimIndent(),
            mapOf("loanId" to loanId),
            this::mapSchedule
        )

    private fun mapSchedule(rs: ResultSet, rowNum: Int): LoanScheduleItemDto =
        LoanScheduleItemDto(
            scheduleId = rs.getString("schedule_id"),
            installmentNo = rs.getInt("installment_no"),
            dueDate = rs.getObject("due_date", LocalDate::class.java),
            principalMinor = rs.getLong("principal_minor"),
            interestMinor = rs.getLong("interest_minor"),
            totalMinor = rs.getLong("total_minor"),
            status = rs.getString("status"),
            ledgerTransactionId = rs.getString("ledger_transaction_id"),
            paidAt = rs.getObject("paid_at", OffsetDateTime::class.java)
        )

    private fun insertSchedule(
        loanId: String,
        principalMinor: Long,
        annualRateBps: Int,
        termMonths: Int,
        firstDueDate: LocalDate,
        installmentOffset: Int
    ) {
        var outstanding = principalMinor
        val basePrincipal = principalMinor / termMonths
        val remainder = principalMinor % termMonths
        (1..termMonths).forEach { installment ->
            val principal = basePrincipal + if (installment.toLong() == termMonths.toLong()) remainder else 0L
            val interest = calculateMonthlyInterest(outstanding, annualRateBps)
            outstanding = (outstanding - principal).coerceAtLeast(0)
            jdbc.update(
                """
                INSERT INTO loan_repayment_schedule (
                  schedule_id, loan_id, installment_no, due_date,
                  principal_minor, interest_minor, total_minor, status
                )
                VALUES (
                  :scheduleId, :loanId, :installmentNo, :dueDate,
                  :principalMinor, :interestMinor, :totalMinor, 'PENDING'
                )
                """.trimIndent(),
                mapOf(
                    "scheduleId" to "LSCH-${UUID.randomUUID().toString().uppercase()}",
                    "loanId" to loanId,
                    "installmentNo" to installmentOffset + installment - 1,
                    "dueDate" to firstDueDate.plusMonths((installment - 1).toLong()),
                    "principalMinor" to principal,
                    "interestMinor" to interest,
                    "totalMinor" to principal + interest
                )
            )
        }
    }

    private fun nextPayableSchedule(loanId: String): LoanScheduleItemDto? =
        jdbc.query(
            """
            SELECT schedule_id, installment_no, due_date, principal_minor, interest_minor,
                   total_minor, status, ledger_transaction_id, paid_at
            FROM loan_repayment_schedule
            WHERE loan_id = :loanId
              AND status IN ('PENDING', 'OVERDUE')
            ORDER BY due_date, installment_no
            LIMIT 1
            FOR UPDATE
            """.trimIndent(),
            mapOf("loanId" to loanId),
            this::mapSchedule
        ).firstOrNull()

    private fun markSchedulePaid(scheduleId: String, ledgerTransactionId: String) {
        jdbc.update(
            """
            UPDATE loan_repayment_schedule
            SET status = 'PAID',
                ledger_transaction_id = :ledgerTransactionId,
                paid_at = now()
            WHERE schedule_id = :scheduleId
            """.trimIndent(),
            mapOf("scheduleId" to scheduleId, "ledgerTransactionId" to ledgerTransactionId)
        )
    }

    private fun updateLoanAfterPrincipalPayment(loanId: String, principalMinor: Long) {
        val remaining = jdbc.queryForObject(
            """
            UPDATE loans
            SET outstanding_principal_minor = GREATEST(0, outstanding_principal_minor - :principalMinor),
                updated_at = now()
            WHERE loan_id = :loanId
            RETURNING outstanding_principal_minor
            """.trimIndent(),
            mapOf("loanId" to loanId, "principalMinor" to principalMinor),
            Long::class.java
        ) ?: 0L
        val nextDue = nextPendingDueDate(loanId)
        jdbc.update(
            """
            UPDATE loans
            SET status = CASE WHEN :remaining = 0 THEN 'CLOSED' WHEN status = 'CLOSED' THEN 'ACTIVE' ELSE status END,
                next_due_date = CASE WHEN :remaining = 0 THEN NULL ELSE CAST(:nextDueDate AS date) END,
                overdue_days = CASE WHEN :remaining = 0 THEN 0 ELSE overdue_days END,
                updated_at = now()
            WHERE loan_id = :loanId
            """.trimIndent(),
            mapOf("loanId" to loanId, "remaining" to remaining, "nextDueDate" to nextDue)
        )
    }

    private fun recomputeScheduleAfterPrepayment(loanId: String, businessDate: LocalDate) {
        val row = loanForUpdate(loanId)
        if (row.outstandingPrincipalMinor == 0L) {
            jdbc.update(
                """
                UPDATE loan_repayment_schedule
                SET status = 'PREPAID'
                WHERE loan_id = :loanId
                  AND status IN ('PENDING', 'OVERDUE')
                """.trimIndent(),
                mapOf("loanId" to loanId)
            )
            jdbc.update(
                """
                UPDATE loans
                SET status = 'CLOSED',
                    next_due_date = NULL,
                    overdue_days = 0,
                    updated_at = now()
                WHERE loan_id = :loanId
                """.trimIndent(),
                mapOf("loanId" to loanId)
            )
            return
        }
        val remainingInstallments = countActiveScheduleRows(loanId).coerceAtLeast(1)
        jdbc.update(
            """
            UPDATE loan_repayment_schedule
            SET status = 'SUPERSEDED'
            WHERE loan_id = :loanId
              AND status IN ('PENDING', 'OVERDUE')
            """.trimIndent(),
            mapOf("loanId" to loanId)
        )
        val nextInstallmentNo = nextInstallmentNumber(loanId)
        val firstDueDate = businessDate.plusMonths(1)
        insertSchedule(loanId, row.outstandingPrincipalMinor, row.annualRateBps, remainingInstallments, firstDueDate, nextInstallmentNo)
        jdbc.update(
            """
            UPDATE loans
            SET status = 'ACTIVE',
                next_due_date = :nextDueDate,
                overdue_days = 0,
                updated_at = now()
            WHERE loan_id = :loanId
            """.trimIndent(),
            mapOf("loanId" to loanId, "nextDueDate" to firstDueDate)
        )
    }

    private fun insertPayment(
        loanId: String,
        paymentType: String,
        principalMinor: Long,
        interestMinor: Long,
        businessDate: LocalDate,
        idempotencyKey: String,
        ledgerTransactionId: String,
        actor: ReadWriteActor,
        requestedChannel: String,
        reason: String?
    ): LoanPaymentDto {
        val paymentId = "LPAY-${UUID.randomUUID().toString().uppercase()}"
        jdbc.update(
            """
            INSERT INTO loan_payments (
              payment_id, loan_id, payment_type, principal_minor, interest_minor,
              total_minor, business_date, idempotency_key, ledger_transaction_id,
              requested_by, requested_channel, reason
            )
            VALUES (
              :paymentId, :loanId, :paymentType, :principalMinor, :interestMinor,
              :totalMinor, :businessDate, :idempotencyKey, :ledgerTransactionId,
              :requestedBy, :requestedChannel, :reason
            )
            """.trimIndent(),
            mapOf(
                "paymentId" to paymentId,
                "loanId" to loanId,
                "paymentType" to paymentType,
                "principalMinor" to principalMinor,
                "interestMinor" to interestMinor,
                "totalMinor" to principalMinor + interestMinor,
                "businessDate" to businessDate,
                "idempotencyKey" to idempotencyKey,
                "ledgerTransactionId" to ledgerTransactionId,
                "requestedBy" to actor.actorId,
                "requestedChannel" to requestedChannel,
                "reason" to reason
            )
        )
        return payment(paymentId)
    }

    private fun existingPayment(loanId: String, idempotencyKey: String): LoanPaymentDto? =
        jdbc.query(
            paymentSql("WHERE loan_id = :loanId AND idempotency_key = :idempotencyKey"),
            mapOf("loanId" to loanId, "idempotencyKey" to idempotencyKey),
            this::mapPayment
        ).firstOrNull()

    private fun payment(paymentId: String): LoanPaymentDto =
        jdbc.queryForObject(paymentSql("WHERE payment_id = :paymentId"), mapOf("paymentId" to paymentId), this::mapPayment)
            ?: throw WorkflowErrors.notFound("loan payment not found: $paymentId")

    private fun paymentSql(whereClause: String): String =
        """
        SELECT payment_id, loan_id, payment_type, principal_minor, interest_minor, total_minor,
               business_date, idempotency_key, ledger_transaction_id, requested_by,
               requested_channel, reason, created_at
        FROM loan_payments
        $whereClause
        """.trimIndent()

    private fun mapPayment(rs: ResultSet, rowNum: Int): LoanPaymentDto =
        LoanPaymentDto(
            paymentId = rs.getString("payment_id"),
            loanId = rs.getString("loan_id"),
            paymentType = rs.getString("payment_type"),
            principalMinor = rs.getLong("principal_minor"),
            interestMinor = rs.getLong("interest_minor"),
            totalMinor = rs.getLong("total_minor"),
            businessDate = rs.getObject("business_date", LocalDate::class.java),
            idempotencyKey = rs.getString("idempotency_key"),
            ledgerTransactionId = rs.getString("ledger_transaction_id"),
            requestedBy = rs.getString("requested_by"),
            requestedChannel = rs.getString("requested_channel"),
            reason = rs.getString("reason"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
        )

    private fun existingAccrual(loanId: String, accrualDate: LocalDate): LoanAccrualDto? =
        jdbc.query(
            """
            SELECT accrual_id, loan_id, accrual_date, outstanding_principal_minor,
                   annual_rate_bps, interest_minor, overdue_days, status, created_at
            FROM loan_interest_accruals
            WHERE loan_id = :loanId
              AND accrual_date = :accrualDate
            """.trimIndent(),
            mapOf("loanId" to loanId, "accrualDate" to accrualDate),
            this::mapAccrual
        ).firstOrNull()

    private fun mapAccrual(rs: ResultSet, rowNum: Int): LoanAccrualDto =
        LoanAccrualDto(
            accrualId = rs.getString("accrual_id"),
            loanId = rs.getString("loan_id"),
            accrualDate = rs.getObject("accrual_date", LocalDate::class.java),
            outstandingPrincipalMinor = rs.getLong("outstanding_principal_minor"),
            annualRateBps = rs.getInt("annual_rate_bps"),
            interestMinor = rs.getLong("interest_minor"),
            overdueDays = rs.getInt("overdue_days"),
            status = rs.getString("status"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
        )

    private fun nextPendingDueDate(loanId: String): LocalDate? =
        jdbc.queryForObject(
            """
            SELECT min(due_date)
            FROM loan_repayment_schedule
            WHERE loan_id = :loanId
              AND status IN ('PENDING', 'OVERDUE')
            """.trimIndent(),
            mapOf("loanId" to loanId),
            LocalDate::class.java
        )

    private fun earliestPendingDueDateBefore(loanId: String, date: LocalDate): LocalDate? =
        jdbc.queryForObject(
            """
            SELECT min(due_date)
            FROM loan_repayment_schedule
            WHERE loan_id = :loanId
              AND status = 'PENDING'
              AND due_date < :date
            """.trimIndent(),
            mapOf("loanId" to loanId, "date" to date),
            LocalDate::class.java
        )

    private fun countActiveScheduleRows(loanId: String): Int =
        jdbc.queryForObject(
            """
            SELECT count(*)
            FROM loan_repayment_schedule
            WHERE loan_id = :loanId
              AND status IN ('PENDING', 'OVERDUE')
            """.trimIndent(),
            mapOf("loanId" to loanId),
            Int::class.java
        ) ?: 0

    private fun nextInstallmentNumber(loanId: String): Int =
        (jdbc.queryForObject(
            "SELECT COALESCE(max(installment_no), 0) + 1 FROM loan_repayment_schedule WHERE loan_id = :loanId",
            mapOf("loanId" to loanId),
            Int::class.java
        ) ?: 1)

    private fun underwritingDecision(
        amountMinor: Long,
        monthlyIncomeMinor: Long,
        monthlyDebtMinor: Long,
        creditGrade: String,
        riskGrade: String
    ): UnderwritingResult {
        if (monthlyIncomeMinor <= 0) {
            return UnderwritingResult(score = 0, decision = "DECLINE")
        }
        val debtRatioBps = (monthlyDebtMinor * 10_000 / monthlyIncomeMinor).coerceAtMost(20_000)
        val creditBonus = when (creditGrade.uppercase()) {
            "A" -> 20
            "B" -> 12
            "C" -> 4
            else -> -12
        }
        val riskPenalty = when (riskGrade.uppercase()) {
            "LOW" -> 0
            "MEDIUM" -> 12
            "HIGH" -> 28
            else -> 18
        }
        val amountPressure = (amountMinor / 10_000_000).toInt().coerceAtMost(20)
        val debtPenalty = (debtRatioBps / 500).toInt()
        val score = (70 + creditBonus - riskPenalty - amountPressure - debtPenalty).coerceIn(0, 100)
        val decision = when {
            score >= 70 -> "APPROVE"
            score >= 55 -> "REFER"
            else -> "DECLINE"
        }
        return UnderwritingResult(score = score, decision = decision)
    }

    private fun calculateMonthlyInterest(outstandingMinor: Long, annualRateBps: Int): Long =
        ((outstandingMinor * annualRateBps) / 120_000L).coerceAtLeast(0)

    private fun calculateDailyInterest(outstandingMinor: Long, annualRateBps: Int): Long =
        ((outstandingMinor * annualRateBps) / 3_650_000L).coerceAtLeast(0)

    private fun appendPaymentAudit(
        eventType: String,
        loan: LoanRow,
        payment: LoanPaymentDto,
        actor: ReadWriteActor,
        reason: String?
    ) {
        appendAudit(
            eventType = eventType,
            actor = actor,
            screenId = if (payment.paymentType == "PREPAYMENT") "CWB-504" else "CWB-503",
            businessReferenceId = payment.paymentId,
            customerId = loan.customerId,
            accountId = loan.depositAccountId,
            reason = reason,
            payload = mapOf(
                "loanId" to loan.loanId,
                "paymentType" to payment.paymentType,
                "ledgerTransactionId" to payment.ledgerTransactionId,
                "syntheticOnly" to true,
                "ledgerSourceRowsMutated" to false
            )
        )
    }

    private fun appendAudit(
        eventType: String,
        actor: ReadWriteActor,
        screenId: String,
        businessReferenceId: String,
        customerId: String?,
        accountId: String?,
        reason: String?,
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

    private data class ReadWriteActor(
        val actorId: String,
        val actorRole: String
    )

    private data class AccountRow(
        val accountId: String,
        val customerId: String,
        val currency: String,
        val status: String
    )

    private data class LoanRow(
        val loanId: String,
        val applicationId: String,
        val customerId: String,
        val depositAccountId: String,
        val productId: String,
        val principalMinor: Long,
        val outstandingPrincipalMinor: Long,
        val annualRateBps: Int,
        val termMonths: Int,
        val status: String,
        val disbursementTransactionId: String?,
        val nextDueDate: LocalDate?,
        val overdueDays: Int,
        val disbursedAt: OffsetDateTime?
    )

    private data class UnderwritingResult(
        val score: Int,
        val decision: String
    )

    private companion object {
        val APPLICATION_ROLES = setOf("CUSTOMER", "BRANCH_STAFF", "BRANCH_MANAGER")
        val LOAN_READ_ROLES = setOf("BRANCH_STAFF", "BRANCH_MANAGER", "CALL_CENTER_MANAGER", "OPS_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER")
        val LOAN_WRITE_ROLES = setOf("CUSTOMER", "BRANCH_STAFF", "BRANCH_MANAGER", "OPS_MANAGER")
        val ACCRUAL_ROLES = setOf("OPS_OPERATOR", "OPS_MANAGER", "BRANCH_MANAGER")
    }
}
