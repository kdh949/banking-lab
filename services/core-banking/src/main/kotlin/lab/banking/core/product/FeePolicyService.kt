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
import lab.banking.core.ledger.application.FeePostingCharge
import lab.banking.core.ledger.application.FeePostingCommand
import lab.banking.core.ledger.application.LedgerCommandService
import lab.banking.core.security.BankingLabAuthContext
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class FeePolicyService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val approvals: ApprovalServicePort,
    private val ledgerCommandService: LedgerCommandService,
    private val auditEvents: AuditEventAppender
) {
    @Transactional(readOnly = true)
    fun listFeePolicies(asOf: LocalDate = LocalDate.now()): FeePolicyListResponse =
        FeePolicyListResponse(policyRows(asOf))

    @Transactional(readOnly = true)
    fun feePolicy(policyId: String, asOf: LocalDate = LocalDate.now()): FeePolicyDto =
        policyRow(policyId, asOf)

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun staffFeeInquiry(accountId: String?, reason: String?, asOf: LocalDate = LocalDate.now()): StaffFeePolicyListResponse {
        requireNonBlank(reason, "reason")
        if (!accountId.isNullOrBlank()) {
            jdbc.query(
                "SELECT account_id FROM accounts WHERE account_id = :accountId",
                mapOf("accountId" to accountId)
            ) { rs, _ -> rs.getString("account_id") }
                .firstOrNull()
                ?: throw WorkflowErrors.notFound("account not found: $accountId")
        }
        val items = policyRows(asOf)
        val auditEventId = auditEvents.append(
            eventType = "FEE_VIEW",
            actorType = "STAFF",
            actorId = "branch01",
            actorRole = "BRANCH_STAFF",
            screenId = "FEE-101",
            businessReferenceId = accountId ?: "FEE-POLICY-CATALOG",
            reason = reason,
            payload = mapOf(
                "accountId" to accountId,
                "policyCount" to items.size,
                "piiExposure" to "MASKED",
                "syntheticOnly" to true
            )
        )
        return StaffFeePolicyListResponse(auditEventId = auditEventId, items = items)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun requestPolicyChange(policyId: String, command: FeePolicyChangeRequestCommand): FeePolicyChangeRequestResponse {
        BankingLabAuthContext.requireActor(command.requestedBy, command.actorRole)
        requireRole(command.actorRole, FEE_POLICY_CHANGE_REQUEST_ROLES)
        requireNonBlank(command.reason, "reason")
        requireNonBlank(command.idempotencyKey, "idempotencyKey")
        requireFeeAmount(command.requestedAmountMinor)
        val existing = policyChangeRequestByIdempotency(command.requestedBy, command.idempotencyKey)
        if (existing != null) {
            return FeePolicyChangeRequestResponse(
                item = existing,
                approval = existing.approvalId?.let(approvals::approval),
                replayed = true
            )
        }
        val policy = policyRow(policyId, command.effectiveFrom)
        val currentVersion = currentVersion(policyId, command.effectiveFrom)
            ?: currentVersion(policyId, LocalDate.now())
        val requestId = "FPC-${UUID.randomUUID().toString().uppercase()}"
        val approval = approvals.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.FEE_POLICY_PARAMETER_CHANGE,
                businessReferenceId = requestId,
                requestedBy = command.requestedBy,
                requestedByRole = command.actorRole,
                requestReason = command.reason,
                beforeSnapshot = mapOf(
                    "policyId" to policy.policyId,
                    "feeCode" to policy.feeCode,
                    "amountMinor" to currentVersion?.amountMinor,
                    "feePolicyVersionId" to currentVersion?.feePolicyVersionId,
                    "effectiveFrom" to currentVersion?.effectiveFrom?.toString(),
                    "syntheticOnly" to true
                ),
                afterSnapshot = mapOf(
                    "policyId" to policy.policyId,
                    "feeCode" to policy.feeCode,
                    "requestedAmountMinor" to command.requestedAmountMinor,
                    "effectiveFrom" to command.effectiveFrom.toString(),
                    "syntheticOnly" to true
                ),
                screenId = "FEE-103"
            )
        )
        jdbc.update(
            """
            INSERT INTO fee_policy_change_requests (
              request_id, policy_id, approval_id, requested_amount_minor, effective_from,
              requested_by, requested_role, reason, status, idempotency_key, metadata_json
            )
            VALUES (
              :requestId, :policyId, :approvalId, :requestedAmountMinor, :effectiveFrom,
              :requestedBy, :requestedRole, :reason, 'PENDING_APPROVAL', :idempotencyKey, CAST(:metadata AS jsonb)
            )
            """.trimIndent(),
            mapOf(
                "requestId" to requestId,
                "policyId" to policyId,
                "approvalId" to approval.approvalId,
                "requestedAmountMinor" to command.requestedAmountMinor,
                "effectiveFrom" to command.effectiveFrom,
                "requestedBy" to command.requestedBy,
                "requestedRole" to command.actorRole,
                "reason" to command.reason,
                "idempotencyKey" to command.idempotencyKey,
                "metadata" to objectMapper.writeValueAsString(mapOf("syntheticOnly" to true, "approvalId" to approval.approvalId))
            )
        )
        return FeePolicyChangeRequestResponse(
            item = policyChangeRequest(requestId),
            approval = approval,
            replayed = false
        )
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun applyApprovedPolicyChange(approval: OperatorApproval, command: ApproveApprovalCommand): FeePolicyChangeRequestDto {
        if (approval.businessType != ApprovalBusinessTypes.FEE_POLICY_PARAMETER_CHANGE) {
            throw WorkflowErrors.stateViolation("approval is not a fee policy parameter change approval")
        }
        val request = policyChangeRequestForUpdate(approval.approvalId)
        if (request.status != "PENDING_APPROVAL") {
            throw WorkflowErrors.stateViolation("fee policy change request is not pending approval: ${request.status}")
        }
        val versionId = "FVER-${UUID.randomUUID().toString().uppercase()}"
        jdbc.update(
            """
            UPDATE fee_policy_versions
            SET status = 'SUPERSEDED',
                effective_to = CASE WHEN effective_from < :effectiveFrom THEN :effectiveTo ELSE effective_to END
            WHERE policy_id = :policyId
              AND status = 'ACTIVE'
            """.trimIndent(),
            mapOf(
                "policyId" to request.policyId,
                "effectiveFrom" to request.effectiveFrom,
                "effectiveTo" to request.effectiveFrom.minusDays(1)
            )
        )
        jdbc.update(
            """
            INSERT INTO fee_policy_versions (
              fee_policy_version_id, policy_id, amount_minor, effective_from, effective_to,
              status, approval_id, created_by, approved_at
            )
            VALUES (
              :versionId, :policyId, :amountMinor, :effectiveFrom, NULL,
              'ACTIVE', :approvalId, :createdBy, now()
            )
            """.trimIndent(),
            mapOf(
                "versionId" to versionId,
                "policyId" to request.policyId,
                "amountMinor" to request.requestedAmountMinor,
                "effectiveFrom" to request.effectiveFrom,
                "approvalId" to approval.approvalId,
                "createdBy" to request.requestedBy
            )
        )
        jdbc.update(
            """
            UPDATE fee_policy_change_requests
            SET status = 'APPLIED',
                applied_fee_policy_version_id = :versionId,
                applied_at = now(),
                updated_at = now()
            WHERE request_id = :requestId
            """.trimIndent(),
            mapOf("requestId" to request.requestId, "versionId" to versionId)
        )
        auditEvents.append(
            eventType = "COMMAND_EXECUTED",
            actorType = "STAFF",
            actorId = approval.approvedBy ?: "UNKNOWN",
            actorRole = command.approvedByRole,
            screenId = "FEE-103",
            businessReferenceId = request.requestId,
            reason = request.reason,
            payload = mapOf(
                "approvalId" to approval.approvalId,
                "businessType" to ApprovalBusinessTypes.FEE_POLICY_PARAMETER_CHANGE,
                "policyId" to request.policyId,
                "feePolicyVersionId" to versionId,
                "requestedAmountMinor" to request.requestedAmountMinor,
                "effectiveFrom" to request.effectiveFrom.toString(),
                "syntheticOnly" to true,
                "ledgerSourceRowsMutated" to false
            )
        )
        return policyChangeRequest(request.requestId)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun rejectPolicyChange(approval: OperatorApproval): FeePolicyChangeRequestDto {
        if (approval.businessType != ApprovalBusinessTypes.FEE_POLICY_PARAMETER_CHANGE) {
            throw WorkflowErrors.stateViolation("approval is not a fee policy parameter change approval")
        }
        val request = policyChangeRequestForUpdate(approval.approvalId)
        if (request.status != "PENDING_APPROVAL") {
            throw WorkflowErrors.stateViolation("fee policy change request is not pending approval: ${request.status}")
        }
        jdbc.update(
            """
            UPDATE fee_policy_change_requests
            SET status = 'REJECTED',
                updated_at = now()
            WHERE request_id = :requestId
            """.trimIndent(),
            mapOf("requestId" to request.requestId)
        )
        return policyChangeRequest(request.requestId)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun postFeeBatch(command: FeePostingBatchCommand): FeePostingBatchResponse {
        BankingLabAuthContext.requireActor(command.requestedBy, command.actorRole)
        requireRole(command.actorRole, FEE_OPERATOR_ROLES)
        requireNonBlank(command.policyId, "policyId")
        requireNonBlank(command.reason, "reason")
        requireNonBlank(command.idempotencyKey, "idempotencyKey")
        val existing = feeBatchByIdempotency(command.idempotencyKey)
        if (existing != null) {
            return FeePostingBatchResponse(
                item = existing,
                ledgerTransaction = ledgerCommandService.transaction(existing.ledgerTransactionId),
                replayed = true
            )
        }
        val policy = policyRow(command.policyId, command.businessDate)
        val version = currentVersion(command.policyId, command.businessDate)
            ?: throw WorkflowErrors.stateViolation("no active fee policy version is effective on ${command.businessDate}")
        if (version.amountMinor <= 0) {
            throw WorkflowErrors.stateViolation("fee policy amount is zero and cannot be posted")
        }
        val candidates = feePostingCandidates(policy, version)
        if (candidates.isEmpty()) {
            throw WorkflowErrors.stateViolation("no eligible accounts are available for fee posting")
        }
        val batchId = "FEEB-${UUID.randomUUID().toString().uppercase()}"
        val ledgerResult = ledgerCommandService.feePosting(
            FeePostingCommand(
                charges = candidates.map { FeePostingCharge(accountId = it.accountId, amountMinor = it.amountMinor) },
                idempotencyKey = command.idempotencyKey,
                requestedBy = command.requestedBy,
                requestedChannel = "CORE_BANKING",
                businessDate = command.businessDate,
                reason = command.reason,
                currency = policy.currency,
                businessReferenceId = batchId
            )
        )
        jdbc.update(
            """
            INSERT INTO fee_posting_batches (
              batch_id, policy_id, fee_policy_version_id, business_date, idempotency_key,
              status, ledger_transaction_id, total_fee_minor, account_count, requested_by, reason
            )
            VALUES (
              :batchId, :policyId, :feePolicyVersionId, :businessDate, :idempotencyKey,
              'POSTED', :ledgerTransactionId, :totalFeeMinor, :accountCount, :requestedBy, :reason
            )
            """.trimIndent(),
            mapOf(
                "batchId" to batchId,
                "policyId" to policy.policyId,
                "feePolicyVersionId" to version.feePolicyVersionId,
                "businessDate" to command.businessDate,
                "idempotencyKey" to command.idempotencyKey,
                "ledgerTransactionId" to ledgerResult.value.id,
                "totalFeeMinor" to candidates.sumOf { it.amountMinor },
                "accountCount" to candidates.size,
                "requestedBy" to command.requestedBy,
                "reason" to command.reason
            )
        )
        auditEvents.append(
            eventType = "COMMAND_EXECUTED",
            actorType = "STAFF",
            actorId = command.requestedBy,
            actorRole = command.actorRole,
            screenId = "OPS-403",
            businessReferenceId = batchId,
            reason = command.reason,
            payload = mapOf(
                "policyId" to policy.policyId,
                "feeCode" to policy.feeCode,
                "ledgerTransactionId" to ledgerResult.value.id,
                "totalFeeMinor" to candidates.sumOf { it.amountMinor },
                "accountCount" to candidates.size,
                "syntheticOnly" to true,
                "ledgerSourceRowsMutated" to false
            )
        )
        return FeePostingBatchResponse(
            item = feeBatch(batchId),
            ledgerTransaction = ledgerResult.value,
            replayed = false
        )
    }

    private fun policyRows(asOf: LocalDate): List<FeePolicyDto> =
        jdbc.query(
            """
            SELECT policy_id, fee_code, fee_name, product_id, currency, status, waiver_eligible, synthetic_only
            FROM fee_policies
            ORDER BY fee_code
            """.trimIndent(),
            emptyMap<String, Any?>()
        ) { rs, _ -> mapPolicy(rs, currentVersion(rs.getString("policy_id"), asOf)) }

    private fun policyRow(policyId: String, asOf: LocalDate): FeePolicyDto =
        jdbc.query(
            """
            SELECT policy_id, fee_code, fee_name, product_id, currency, status, waiver_eligible, synthetic_only
            FROM fee_policies
            WHERE policy_id = :policyId
            """.trimIndent(),
            mapOf("policyId" to policyId)
        ) { rs, _ -> mapPolicy(rs, currentVersion(policyId, asOf)) }
            .firstOrNull()
            ?: throw WorkflowErrors.notFound("fee policy not found: $policyId")

    private fun currentVersion(policyId: String, asOf: LocalDate): FeePolicyVersion? =
        jdbc.query(
            """
            SELECT fee_policy_version_id, amount_minor, effective_from, effective_to
            FROM fee_policy_versions
            WHERE policy_id = :policyId
              AND status = 'ACTIVE'
              AND effective_from <= :asOf
              AND (effective_to IS NULL OR effective_to >= :asOf)
            ORDER BY effective_from DESC, created_at DESC
            LIMIT 1
            """.trimIndent(),
            mapOf("policyId" to policyId, "asOf" to asOf),
            this::mapVersion
        ).firstOrNull()

    private fun policyChangeRequestByIdempotency(requestedBy: String, idempotencyKey: String): FeePolicyChangeRequestDto? =
        jdbc.query(
            policyChangeSql("WHERE requested_by = :requestedBy AND idempotency_key = :idempotencyKey"),
            mapOf("requestedBy" to requestedBy, "idempotencyKey" to idempotencyKey),
            this::mapPolicyChangeRequest
        ).firstOrNull()

    private fun policyChangeRequest(requestId: String): FeePolicyChangeRequestDto =
        jdbc.query(
            policyChangeSql("WHERE request_id = :requestId"),
            mapOf("requestId" to requestId),
            this::mapPolicyChangeRequest
        ).firstOrNull() ?: throw WorkflowErrors.notFound("fee policy change request not found: $requestId")

    private fun policyChangeRequestForUpdate(approvalId: String): FeePolicyChangeRequestDto =
        jdbc.query(
            policyChangeSql("WHERE approval_id = :approvalId FOR UPDATE"),
            mapOf("approvalId" to approvalId),
            this::mapPolicyChangeRequest
        ).firstOrNull() ?: throw WorkflowErrors.stateViolation("fee policy change request is missing")

    private fun policyChangeSql(suffix: String): String =
        """
        SELECT request_id, policy_id, approval_id, requested_amount_minor, effective_from,
               requested_by, requested_role, reason, status, idempotency_key,
               applied_fee_policy_version_id, created_at, updated_at, applied_at
        FROM fee_policy_change_requests
        $suffix
        """.trimIndent()

    private fun feePostingCandidates(policy: FeePolicyDto, version: FeePolicyVersion): List<FeePostingCandidate> =
        jdbc.query(
            """
            SELECT e.account_id, :amountMinor AS amount_minor
            FROM account_product_enrollments e
            JOIN accounts a ON a.account_id = e.account_id AND a.status = 'ACTIVE'
            WHERE e.status = 'ACTIVE'
              AND (:productId IS NULL OR e.product_id = :productId)
              AND NOT EXISTS (
                SELECT 1
                FROM fee_waiver_requests w
                WHERE w.target_account_id = e.account_id
                  AND w.fee_code = :feeCode
                  AND w.status = 'APPROVED'
                  AND w.target_transaction_id IS NULL
              )
            ORDER BY e.account_id
            """.trimIndent(),
            mapOf(
                "amountMinor" to version.amountMinor,
                "productId" to policy.productId,
                "feeCode" to policy.feeCode
            )
        ) { rs, _ ->
            FeePostingCandidate(
                accountId = rs.getString("account_id"),
                amountMinor = rs.getLong("amount_minor")
            )
        }

    private fun feeBatchByIdempotency(idempotencyKey: String): FeePostingBatchDto? =
        jdbc.query(
            feeBatchSql("WHERE idempotency_key = :idempotencyKey"),
            mapOf("idempotencyKey" to idempotencyKey),
            this::mapFeeBatch
        ).firstOrNull()

    private fun feeBatch(batchId: String): FeePostingBatchDto =
        jdbc.query(
            feeBatchSql("WHERE batch_id = :batchId"),
            mapOf("batchId" to batchId),
            this::mapFeeBatch
        ).firstOrNull() ?: throw WorkflowErrors.notFound("fee posting batch not found: $batchId")

    private fun feeBatchSql(suffix: String): String =
        """
        SELECT batch_id, policy_id, fee_policy_version_id, business_date, idempotency_key,
               status, ledger_transaction_id, total_fee_minor, account_count, requested_by, reason, posted_at
        FROM fee_posting_batches
        $suffix
        """.trimIndent()

    private fun requireFeeAmount(amountMinor: Long) {
        if (amountMinor < 0) {
            throw WorkflowErrors.validation("requestedAmountMinor must be zero or a positive minor-unit amount")
        }
    }

    private fun requireRole(actorRole: String, allowedRoles: Set<String>) {
        if (actorRole !in allowedRoles) {
            throw WorkflowErrors.authorizationViolation("actor role is not allowed for this fee operation: $actorRole")
        }
    }

    private fun requireNonBlank(value: String?, field: String) {
        if (value.isNullOrBlank()) {
            if (field == "reason") {
                throw WorkflowErrors.reasonRequired("fee policy and fee posting operations require a business reason")
            }
            throw WorkflowErrors.validation("$field is required")
        }
    }

    private fun mapPolicy(rs: ResultSet, version: FeePolicyVersion?): FeePolicyDto =
        FeePolicyDto(
            policyId = rs.getString("policy_id"),
            feeCode = rs.getString("fee_code"),
            feeName = rs.getString("fee_name"),
            productId = rs.getString("product_id"),
            currency = rs.getString("currency").trim(),
            status = rs.getString("status"),
            waiverEligible = rs.getBoolean("waiver_eligible"),
            currentVersionId = version?.feePolicyVersionId,
            amountMinor = version?.amountMinor,
            effectiveFrom = version?.effectiveFrom,
            effectiveTo = version?.effectiveTo,
            syntheticOnly = rs.getBoolean("synthetic_only")
        )

    private fun mapVersion(rs: ResultSet, rowNum: Int): FeePolicyVersion =
        FeePolicyVersion(
            feePolicyVersionId = rs.getString("fee_policy_version_id"),
            amountMinor = rs.getLong("amount_minor"),
            effectiveFrom = rs.getObject("effective_from", LocalDate::class.java),
            effectiveTo = rs.getObject("effective_to", LocalDate::class.java)
        )

    private fun mapPolicyChangeRequest(rs: ResultSet, rowNum: Int): FeePolicyChangeRequestDto =
        FeePolicyChangeRequestDto(
            requestId = rs.getString("request_id"),
            policyId = rs.getString("policy_id"),
            approvalId = rs.getString("approval_id"),
            requestedAmountMinor = rs.getLong("requested_amount_minor"),
            effectiveFrom = rs.getObject("effective_from", LocalDate::class.java),
            requestedBy = rs.getString("requested_by"),
            requestedRole = rs.getString("requested_role"),
            reason = rs.getString("reason"),
            status = rs.getString("status"),
            idempotencyKey = rs.getString("idempotency_key"),
            appliedFeePolicyVersionId = rs.getString("applied_fee_policy_version_id"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
            appliedAt = rs.getObject("applied_at", OffsetDateTime::class.java)
        )

    private fun mapFeeBatch(rs: ResultSet, rowNum: Int): FeePostingBatchDto =
        FeePostingBatchDto(
            batchId = rs.getString("batch_id"),
            policyId = rs.getString("policy_id"),
            feePolicyVersionId = rs.getString("fee_policy_version_id"),
            businessDate = rs.getObject("business_date", LocalDate::class.java),
            idempotencyKey = rs.getString("idempotency_key"),
            status = rs.getString("status"),
            ledgerTransactionId = rs.getString("ledger_transaction_id"),
            totalFeeMinor = rs.getLong("total_fee_minor"),
            accountCount = rs.getInt("account_count"),
            requestedBy = rs.getString("requested_by"),
            reason = rs.getString("reason"),
            postedAt = rs.getObject("posted_at", OffsetDateTime::class.java)
        )

    private data class FeePolicyVersion(
        val feePolicyVersionId: String,
        val amountMinor: Long,
        val effectiveFrom: LocalDate,
        val effectiveTo: LocalDate?
    )

    private data class FeePostingCandidate(
        val accountId: String,
        val amountMinor: Long
    )

    companion object {
        val FEE_POLICY_CHANGE_REQUEST_ROLES = setOf("OPS_MANAGER", "COMPLIANCE_MANAGER")
        val FEE_OPERATOR_ROLES = setOf("OPS_OPERATOR", "OPS_MANAGER")
    }
}
