package lab.banking.core.product

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import lab.banking.core.approval.ApprovalBusinessTypes
import lab.banking.core.approval.ApprovalServicePort
import lab.banking.core.approval.ApproveApprovalCommand
import lab.banking.core.approval.OperatorApproval
import lab.banking.core.approval.SubmitApprovalCommand
import lab.banking.core.audit.AuditEventAppender
import lab.banking.core.ledger.application.InterestPostingCommand
import lab.banking.core.ledger.application.InterestPostingCredit
import lab.banking.core.ledger.application.LedgerCommandService
import lab.banking.core.security.BankingLabAuthContext
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class DepositProductService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val approvals: ApprovalServicePort,
    private val ledgerCommandService: LedgerCommandService,
    private val auditEvents: AuditEventAppender
) {
    @Transactional(readOnly = true)
    fun listDepositProducts(asOf: LocalDate = LocalDate.now()): DepositProductListResponse =
        DepositProductListResponse(
            jdbc.query(
                """
                SELECT product_id, product_code, product_name, currency, status,
                       minimum_opening_balance_minor, synthetic_only
                FROM deposit_products
                ORDER BY product_code
                """.trimIndent(),
                emptyMap<String, Any?>()
            ) { rs, _ -> mapProduct(rs, currentRate(rs.getString("product_id"), asOf)) }
        )

    @Transactional(readOnly = true)
    fun depositProduct(productId: String, asOf: LocalDate = LocalDate.now()): DepositProductDto =
        productRow(productId, asOf)

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun requestRateChange(productId: String, command: DepositRateChangeRequestCommand): DepositRateChangeRequestResponse {
        BankingLabAuthContext.requireActor(command.requestedBy, command.actorRole)
        requireRole(command.actorRole, RATE_CHANGE_REQUEST_ROLES)
        requireNonBlank(command.reason, "reason")
        requireNonBlank(command.idempotencyKey, "idempotencyKey")
        requireAnnualRate(command.requestedAnnualRateBps)
        val existing = rateChangeRequestByIdempotency(command.requestedBy, command.idempotencyKey)
        if (existing != null) {
            return DepositRateChangeRequestResponse(
                item = existing,
                approval = existing.approvalId?.let(approvals::approval),
                replayed = true
            )
        }
        val product = productRow(productId, command.effectiveFrom)
        val currentRate = currentRate(productId, command.effectiveFrom)
            ?: currentRate(productId, LocalDate.now())
        val requestId = "DPR-${UUID.randomUUID().toString().uppercase()}"
        val approval = approvals.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.PRODUCT_PARAMETER_CHANGE,
                businessReferenceId = requestId,
                requestedBy = command.requestedBy,
                requestedByRole = command.actorRole,
                requestReason = command.reason,
                beforeSnapshot = mapOf(
                    "productId" to product.productId,
                    "annualRateBps" to currentRate?.annualRateBps,
                    "rateVersionId" to currentRate?.rateVersionId,
                    "effectiveFrom" to currentRate?.effectiveFrom?.toString(),
                    "syntheticOnly" to true
                ),
                afterSnapshot = mapOf(
                    "productId" to product.productId,
                    "requestedAnnualRateBps" to command.requestedAnnualRateBps,
                    "effectiveFrom" to command.effectiveFrom.toString(),
                    "syntheticOnly" to true
                ),
                screenId = "PRD-102"
            )
        )
        jdbc.update(
            """
            INSERT INTO deposit_rate_change_requests (
              request_id, product_id, approval_id, requested_annual_rate_bps, effective_from,
              requested_by, requested_role, reason, status, idempotency_key, metadata_json
            )
            VALUES (
              :requestId, :productId, :approvalId, :requestedAnnualRateBps, :effectiveFrom,
              :requestedBy, :requestedRole, :reason, 'PENDING_APPROVAL', :idempotencyKey, CAST(:metadata AS jsonb)
            )
            """.trimIndent(),
            mapOf(
                "requestId" to requestId,
                "productId" to productId,
                "approvalId" to approval.approvalId,
                "requestedAnnualRateBps" to command.requestedAnnualRateBps,
                "effectiveFrom" to command.effectiveFrom,
                "requestedBy" to command.requestedBy,
                "requestedRole" to command.actorRole,
                "reason" to command.reason,
                "idempotencyKey" to command.idempotencyKey,
                "metadata" to objectMapper.writeValueAsString(mapOf("syntheticOnly" to true, "approvalId" to approval.approvalId))
            )
        )
        return DepositRateChangeRequestResponse(
            item = rateChangeRequest(requestId),
            approval = approval,
            replayed = false
        )
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun applyApprovedRateChange(approval: OperatorApproval, command: ApproveApprovalCommand): DepositRateChangeRequestDto {
        if (approval.businessType != ApprovalBusinessTypes.PRODUCT_PARAMETER_CHANGE) {
            throw WorkflowErrors.stateViolation("approval is not a product parameter change approval")
        }
        val request = rateChangeRequestForUpdate(approval.approvalId)
        if (request.status != "PENDING_APPROVAL") {
            throw WorkflowErrors.stateViolation("deposit rate change request is not pending approval: ${request.status}")
        }
        val rateVersionId = "RATE-${UUID.randomUUID().toString().uppercase()}"
        jdbc.update(
            """
            UPDATE product_interest_rate_versions
            SET status = 'SUPERSEDED',
                effective_to = CASE WHEN effective_from < :effectiveFrom THEN :effectiveTo ELSE effective_to END
            WHERE product_id = :productId
              AND status = 'ACTIVE'
            """.trimIndent(),
            mapOf(
                "productId" to request.productId,
                "effectiveFrom" to request.effectiveFrom,
                "effectiveTo" to request.effectiveFrom.minusDays(1)
            )
        )
        jdbc.update(
            """
            INSERT INTO product_interest_rate_versions (
              rate_version_id, product_id, annual_rate_bps, effective_from, effective_to,
              status, approval_id, created_by, approved_at
            )
            VALUES (
              :rateVersionId, :productId, :annualRateBps, :effectiveFrom, NULL,
              'ACTIVE', :approvalId, :createdBy, now()
            )
            """.trimIndent(),
            mapOf(
                "rateVersionId" to rateVersionId,
                "productId" to request.productId,
                "annualRateBps" to request.requestedAnnualRateBps,
                "effectiveFrom" to request.effectiveFrom,
                "approvalId" to approval.approvalId,
                "createdBy" to request.requestedBy
            )
        )
        jdbc.update(
            """
            UPDATE deposit_rate_change_requests
            SET status = 'APPLIED',
                applied_rate_version_id = :rateVersionId,
                applied_at = now(),
                updated_at = now()
            WHERE request_id = :requestId
            """.trimIndent(),
            mapOf("requestId" to request.requestId, "rateVersionId" to rateVersionId)
        )
        auditEvents.append(
            eventType = "COMMAND_EXECUTED",
            actorType = "STAFF",
            actorId = approval.approvedBy ?: "UNKNOWN",
            actorRole = command.approvedByRole,
            screenId = "PRD-102",
            businessReferenceId = request.requestId,
            reason = request.reason,
            payload = mapOf(
                "approvalId" to approval.approvalId,
                "businessType" to ApprovalBusinessTypes.PRODUCT_PARAMETER_CHANGE,
                "productId" to request.productId,
                "rateVersionId" to rateVersionId,
                "requestedAnnualRateBps" to request.requestedAnnualRateBps,
                "effectiveFrom" to request.effectiveFrom.toString(),
                "syntheticOnly" to true,
                "ledgerSourceRowsMutated" to false
            )
        )
        return rateChangeRequest(request.requestId)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun rejectRateChange(approval: OperatorApproval): DepositRateChangeRequestDto {
        if (approval.businessType != ApprovalBusinessTypes.PRODUCT_PARAMETER_CHANGE) {
            throw WorkflowErrors.stateViolation("approval is not a product parameter change approval")
        }
        val request = rateChangeRequestForUpdate(approval.approvalId)
        if (request.status != "PENDING_APPROVAL") {
            throw WorkflowErrors.stateViolation("deposit rate change request is not pending approval: ${request.status}")
        }
        jdbc.update(
            """
            UPDATE deposit_rate_change_requests
            SET status = 'REJECTED',
                updated_at = now()
            WHERE request_id = :requestId
            """.trimIndent(),
            mapOf("requestId" to request.requestId)
        )
        return rateChangeRequest(request.requestId)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun runInterestAccrual(command: InterestAccrualRunCommand): InterestAccrualRunResponse {
        BankingLabAuthContext.requireActor(command.requestedBy, command.actorRole)
        requireRole(command.actorRole, INTEREST_OPERATOR_ROLES)
        requireNonBlank(command.reason, "reason")
        val candidates = accrualCandidates(command.accrualDate)
        candidates.forEach { candidate ->
            val accruedInterestMinor = calculateDailyInterest(candidate.balanceMinor, candidate.annualRateBps)
            if (accruedInterestMinor > 0) {
                jdbc.update(
                    """
                    INSERT INTO interest_accruals (
                      accrual_id, account_id, product_id, rate_version_id, accrual_date,
                      balance_minor, annual_rate_bps, accrued_interest_minor, status
                    )
                    VALUES (
                      :accrualId, :accountId, :productId, :rateVersionId, :accrualDate,
                      :balanceMinor, :annualRateBps, :accruedInterestMinor, 'CALCULATED'
                    )
                    ON CONFLICT (account_id, accrual_date) DO NOTHING
                    """.trimIndent(),
                    mapOf(
                        "accrualId" to "IAC-${UUID.randomUUID().toString().uppercase()}",
                        "accountId" to candidate.accountId,
                        "productId" to candidate.productId,
                        "rateVersionId" to candidate.rateVersionId,
                        "accrualDate" to command.accrualDate,
                        "balanceMinor" to candidate.balanceMinor,
                        "annualRateBps" to candidate.annualRateBps,
                        "accruedInterestMinor" to accruedInterestMinor
                    )
                )
            }
        }
        val items = accrualsForDate(command.accrualDate)
        auditEvents.append(
            eventType = "INTEREST_ACCRUAL_RUN",
            actorType = "STAFF",
            actorId = command.requestedBy,
            actorRole = command.actorRole,
            screenId = "OPS-401",
            businessReferenceId = command.accrualDate.toString(),
            reason = command.reason,
            payload = mapOf(
                "accrualDate" to command.accrualDate.toString(),
                "accountCount" to items.size,
                "totalInterestMinor" to items.sumOf { it.accruedInterestMinor },
                "syntheticOnly" to true
            )
        )
        return InterestAccrualRunResponse(
            accrualDate = command.accrualDate,
            items = items,
            totalInterestMinor = items.sumOf { it.accruedInterestMinor }
        )
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun postInterestBatch(command: InterestPostingBatchCommand): InterestPostingBatchResponse {
        BankingLabAuthContext.requireActor(command.requestedBy, command.actorRole)
        requireRole(command.actorRole, INTEREST_OPERATOR_ROLES)
        requireNonBlank(command.reason, "reason")
        requireNonBlank(command.idempotencyKey, "idempotencyKey")
        val existing = interestBatchByIdempotency(command.idempotencyKey)
        if (existing != null) {
            return InterestPostingBatchResponse(
                item = existing,
                ledgerTransaction = ledgerCommandService.transaction(existing.ledgerTransactionId),
                replayed = true
            )
        }
        val accruals = payableAccrualsForUpdate(command.businessDate)
        if (accruals.isEmpty()) {
            throw WorkflowErrors.stateViolation("no calculated interest accruals are available for posting")
        }
        val batchId = "INTB-${UUID.randomUUID().toString().uppercase()}"
        val credits = accruals.map {
            InterestPostingCredit(accountId = it.accountId, amountMinor = it.accruedInterestMinor)
        }
        val ledgerResult = ledgerCommandService.interestPosting(
            InterestPostingCommand(
                credits = credits,
                idempotencyKey = command.idempotencyKey,
                requestedBy = command.requestedBy,
                requestedChannel = "CORE_BANKING",
                businessDate = command.businessDate,
                reason = command.reason,
                businessReferenceId = batchId
            )
        )
        jdbc.update(
            """
            INSERT INTO interest_posting_batches (
              batch_id, business_date, idempotency_key, status, ledger_transaction_id,
              total_interest_minor, account_count, requested_by, reason
            )
            VALUES (
              :batchId, :businessDate, :idempotencyKey, 'POSTED', :ledgerTransactionId,
              :totalInterestMinor, :accountCount, :requestedBy, :reason
            )
            """.trimIndent(),
            mapOf(
                "batchId" to batchId,
                "businessDate" to command.businessDate,
                "idempotencyKey" to command.idempotencyKey,
                "ledgerTransactionId" to ledgerResult.value.id,
                "totalInterestMinor" to accruals.sumOf { it.accruedInterestMinor },
                "accountCount" to accruals.map { it.accountId }.distinct().size,
                "requestedBy" to command.requestedBy,
                "reason" to command.reason
            )
        )
        jdbc.update(
            """
            UPDATE interest_accruals
            SET status = 'POSTED',
                batch_id = :batchId,
                ledger_transaction_id = :ledgerTransactionId,
                posted_at = now()
            WHERE accrual_id IN (:accrualIds)
            """.trimIndent(),
            mapOf(
                "batchId" to batchId,
                "ledgerTransactionId" to ledgerResult.value.id,
                "accrualIds" to accruals.map { it.accrualId }
            )
        )
        auditEvents.append(
            eventType = "COMMAND_EXECUTED",
            actorType = "STAFF",
            actorId = command.requestedBy,
            actorRole = command.actorRole,
            screenId = "OPS-402",
            businessReferenceId = batchId,
            reason = command.reason,
            payload = mapOf(
                "ledgerTransactionId" to ledgerResult.value.id,
                "totalInterestMinor" to accruals.sumOf { it.accruedInterestMinor },
                "accountCount" to accruals.map { it.accountId }.distinct().size,
                "syntheticOnly" to true,
                "ledgerSourceRowsMutated" to false
            )
        )
        return InterestPostingBatchResponse(
            item = interestBatch(batchId),
            ledgerTransaction = ledgerResult.value,
            replayed = false
        )
    }

    private fun productRow(productId: String, asOf: LocalDate): DepositProductDto =
        jdbc.query(
            """
            SELECT product_id, product_code, product_name, currency, status,
                   minimum_opening_balance_minor, synthetic_only
            FROM deposit_products
            WHERE product_id = :productId
            """.trimIndent(),
            mapOf("productId" to productId)
        ) { rs, _ -> mapProduct(rs, currentRate(productId, asOf)) }
            .firstOrNull()
            ?: throw WorkflowErrors.notFound("deposit product not found: $productId")

    private fun currentRate(productId: String, asOf: LocalDate): RateVersion? =
        jdbc.query(
            """
            SELECT rate_version_id, annual_rate_bps, effective_from, effective_to
            FROM product_interest_rate_versions
            WHERE product_id = :productId
              AND status = 'ACTIVE'
              AND effective_from <= :asOf
              AND (effective_to IS NULL OR effective_to >= :asOf)
            ORDER BY effective_from DESC, created_at DESC
            LIMIT 1
            """.trimIndent(),
            mapOf("productId" to productId, "asOf" to asOf),
            this::mapRate
        ).firstOrNull()

    private fun rateChangeRequestByIdempotency(requestedBy: String, idempotencyKey: String): DepositRateChangeRequestDto? =
        jdbc.query(
            rateChangeSql("WHERE requested_by = :requestedBy AND idempotency_key = :idempotencyKey"),
            mapOf("requestedBy" to requestedBy, "idempotencyKey" to idempotencyKey),
            this::mapRateChangeRequest
        ).firstOrNull()

    private fun rateChangeRequest(requestId: String): DepositRateChangeRequestDto =
        jdbc.query(
            rateChangeSql("WHERE request_id = :requestId"),
            mapOf("requestId" to requestId),
            this::mapRateChangeRequest
        ).firstOrNull() ?: throw WorkflowErrors.notFound("deposit rate change request not found: $requestId")

    private fun rateChangeRequestForUpdate(approvalId: String): DepositRateChangeRequestDto =
        jdbc.query(
            rateChangeSql("WHERE approval_id = :approvalId FOR UPDATE"),
            mapOf("approvalId" to approvalId),
            this::mapRateChangeRequest
        ).firstOrNull() ?: throw WorkflowErrors.stateViolation("deposit rate change request is missing")

    private fun rateChangeSql(suffix: String): String =
        """
        SELECT request_id, product_id, approval_id, requested_annual_rate_bps, effective_from,
               requested_by, requested_role, reason, status, idempotency_key,
               applied_rate_version_id, created_at, updated_at, applied_at
        FROM deposit_rate_change_requests
        $suffix
        """.trimIndent()

    private fun accrualCandidates(accrualDate: LocalDate): List<AccrualCandidate> =
        jdbc.query(
            """
            SELECT e.account_id, e.product_id, rv.rate_version_id, bp.ledger_balance_minor, rv.annual_rate_bps
            FROM account_product_enrollments e
            JOIN accounts a ON a.account_id = e.account_id AND a.status = 'ACTIVE'
            JOIN account_balance_projections bp ON bp.account_id = e.account_id AND bp.currency = a.currency
            JOIN LATERAL (
              SELECT rate_version_id, annual_rate_bps
              FROM product_interest_rate_versions
              WHERE product_id = e.product_id
                AND status = 'ACTIVE'
                AND effective_from <= :accrualDate
                AND (effective_to IS NULL OR effective_to >= :accrualDate)
              ORDER BY effective_from DESC, created_at DESC
              LIMIT 1
            ) rv ON TRUE
            WHERE e.status = 'ACTIVE'
              AND bp.ledger_balance_minor > 0
            ORDER BY e.account_id
            """.trimIndent(),
            mapOf("accrualDate" to accrualDate)
        ) { rs, _ ->
            AccrualCandidate(
                accountId = rs.getString("account_id"),
                productId = rs.getString("product_id"),
                rateVersionId = rs.getString("rate_version_id"),
                balanceMinor = rs.getLong("ledger_balance_minor"),
                annualRateBps = rs.getInt("annual_rate_bps")
            )
        }

    private fun accrualsForDate(accrualDate: LocalDate): List<InterestAccrualDto> =
        jdbc.query(
            """
            SELECT accrual_id, account_id, product_id, rate_version_id, accrual_date,
                   balance_minor, annual_rate_bps, accrued_interest_minor, status, batch_id, ledger_transaction_id
            FROM interest_accruals
            WHERE accrual_date = :accrualDate
            ORDER BY account_id
            """.trimIndent(),
            mapOf("accrualDate" to accrualDate),
            this::mapAccrual
        )

    private fun payableAccrualsForUpdate(businessDate: LocalDate): List<InterestAccrualDto> =
        jdbc.query(
            """
            SELECT accrual_id, account_id, product_id, rate_version_id, accrual_date,
                   balance_minor, annual_rate_bps, accrued_interest_minor, status, batch_id, ledger_transaction_id
            FROM interest_accruals
            WHERE status = 'CALCULATED'
              AND accrued_interest_minor > 0
              AND accrual_date <= :businessDate
            ORDER BY account_id, accrual_date
            FOR UPDATE
            """.trimIndent(),
            mapOf("businessDate" to businessDate),
            this::mapAccrual
        )

    private fun interestBatchByIdempotency(idempotencyKey: String): InterestPostingBatchDto? =
        jdbc.query(
            interestBatchSql("WHERE idempotency_key = :idempotencyKey"),
            mapOf("idempotencyKey" to idempotencyKey),
            this::mapInterestBatch
        ).firstOrNull()

    private fun interestBatch(batchId: String): InterestPostingBatchDto =
        jdbc.query(
            interestBatchSql("WHERE batch_id = :batchId"),
            mapOf("batchId" to batchId),
            this::mapInterestBatch
        ).firstOrNull() ?: throw WorkflowErrors.notFound("interest posting batch not found: $batchId")

    private fun interestBatchSql(suffix: String): String =
        """
        SELECT batch_id, business_date, idempotency_key, status, ledger_transaction_id,
               total_interest_minor, account_count, requested_by, reason, posted_at
        FROM interest_posting_batches
        $suffix
        """.trimIndent()

    private fun calculateDailyInterest(balanceMinor: Long, annualRateBps: Int): Long =
        (balanceMinor * annualRateBps) / (10_000L * 365L)

    private fun requireAnnualRate(annualRateBps: Int) {
        if (annualRateBps !in 0..20_000) {
            throw WorkflowErrors.validation("requestedAnnualRateBps must be between 0 and 20000")
        }
    }

    private fun requireRole(actorRole: String, allowedRoles: Set<String>) {
        if (actorRole !in allowedRoles) {
            throw WorkflowErrors.authorizationViolation("actor role is not allowed for this product operation: $actorRole")
        }
    }

    private fun requireNonBlank(value: String?, field: String) {
        if (value.isNullOrBlank()) {
            if (field == "reason") {
                throw WorkflowErrors.reasonRequired("product and interest operations require a business reason")
            }
            throw WorkflowErrors.validation("$field is required")
        }
    }

    private fun mapProduct(rs: ResultSet, rate: RateVersion?): DepositProductDto =
        DepositProductDto(
            productId = rs.getString("product_id"),
            productCode = rs.getString("product_code"),
            productName = rs.getString("product_name"),
            currency = rs.getString("currency").trim(),
            status = rs.getString("status"),
            minimumOpeningBalanceMinor = rs.getLong("minimum_opening_balance_minor"),
            currentRateVersionId = rate?.rateVersionId,
            annualRateBps = rate?.annualRateBps,
            rateEffectiveFrom = rate?.effectiveFrom,
            rateEffectiveTo = rate?.effectiveTo,
            syntheticOnly = rs.getBoolean("synthetic_only")
        )

    private fun mapRate(rs: ResultSet, rowNum: Int): RateVersion =
        RateVersion(
            rateVersionId = rs.getString("rate_version_id"),
            annualRateBps = rs.getInt("annual_rate_bps"),
            effectiveFrom = rs.getObject("effective_from", LocalDate::class.java),
            effectiveTo = rs.getObject("effective_to", LocalDate::class.java)
        )

    private fun mapRateChangeRequest(rs: ResultSet, rowNum: Int): DepositRateChangeRequestDto =
        DepositRateChangeRequestDto(
            requestId = rs.getString("request_id"),
            productId = rs.getString("product_id"),
            approvalId = rs.getString("approval_id"),
            requestedAnnualRateBps = rs.getInt("requested_annual_rate_bps"),
            effectiveFrom = rs.getObject("effective_from", LocalDate::class.java),
            requestedBy = rs.getString("requested_by"),
            requestedRole = rs.getString("requested_role"),
            reason = rs.getString("reason"),
            status = rs.getString("status"),
            idempotencyKey = rs.getString("idempotency_key"),
            appliedRateVersionId = rs.getString("applied_rate_version_id"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
            appliedAt = rs.getObject("applied_at", OffsetDateTime::class.java)
        )

    private fun mapAccrual(rs: ResultSet, rowNum: Int): InterestAccrualDto =
        InterestAccrualDto(
            accrualId = rs.getString("accrual_id"),
            accountId = rs.getString("account_id"),
            productId = rs.getString("product_id"),
            rateVersionId = rs.getString("rate_version_id"),
            accrualDate = rs.getObject("accrual_date", LocalDate::class.java),
            balanceMinor = rs.getLong("balance_minor"),
            annualRateBps = rs.getInt("annual_rate_bps"),
            accruedInterestMinor = rs.getLong("accrued_interest_minor"),
            status = rs.getString("status"),
            batchId = rs.getString("batch_id"),
            ledgerTransactionId = rs.getString("ledger_transaction_id")
        )

    private fun mapInterestBatch(rs: ResultSet, rowNum: Int): InterestPostingBatchDto =
        InterestPostingBatchDto(
            batchId = rs.getString("batch_id"),
            businessDate = rs.getObject("business_date", LocalDate::class.java),
            idempotencyKey = rs.getString("idempotency_key"),
            status = rs.getString("status"),
            ledgerTransactionId = rs.getString("ledger_transaction_id"),
            totalInterestMinor = rs.getLong("total_interest_minor"),
            accountCount = rs.getInt("account_count"),
            requestedBy = rs.getString("requested_by"),
            reason = rs.getString("reason"),
            postedAt = rs.getObject("posted_at", OffsetDateTime::class.java)
        )

    private data class RateVersion(
        val rateVersionId: String,
        val annualRateBps: Int,
        val effectiveFrom: LocalDate,
        val effectiveTo: LocalDate?
    )

    private data class AccrualCandidate(
        val accountId: String,
        val productId: String,
        val rateVersionId: String,
        val balanceMinor: Long,
        val annualRateBps: Int
    )

    companion object {
        private val RATE_CHANGE_REQUEST_ROLES = setOf("OPS_MANAGER", "COMPLIANCE_MANAGER")
        private val INTEREST_OPERATOR_ROLES = setOf("OPS_OPERATOR", "BRANCH_MANAGER")
    }
}
