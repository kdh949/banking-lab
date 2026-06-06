package lab.banking.core.ledger.application

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
import lab.banking.core.security.BankingLabAuthContext
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class LedgerProjectionIntegrityService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val auditEvents: AuditEventAppender,
    private val approvals: PersistentApprovalService
) {
    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun startDriftRun(command: LedgerProjectionDriftRunCommand): LedgerProjectionDriftRunResponse {
        BankingLabAuthContext.requireActor(command.requestedBy, command.requestedByRole)
        validateDriftCommand(command)
        val currency = normalizeCurrency(command.currency)
        val commandHash = commandHash(
            mapOf(
                "requestedBy" to command.requestedBy,
                "requestedByRole" to command.requestedByRole,
                "reason" to command.reason,
                "accountId" to command.accountId,
                "currency" to currency,
                "asOfBusinessDate" to command.asOfBusinessDate?.toString()
            )
        )
        existingDriftRunByIdempotencyKey(command.idempotencyKey)?.let { existing ->
            requireSameCommandHash(existing.commandHash, commandHash)
            return driftRunResponse(existing.runId, replayed = true)
        }

        val runId = nextId("LPD")
        val targets = projectionTargets(command.accountId, currency)
        val sourceStats = sourceStats(command.accountId, currency, command.asOfBusinessDate)
        val aggregate = aggregateSourceStats(targets, sourceStats)
        val auditEventId = appendAudit(
            eventType = "LEDGER_PROJECTION_DRIFT_RUN",
            actorId = command.requestedBy,
            actorRole = command.requestedByRole,
            screenId = "OPS-LEDGER-101",
            businessReferenceId = runId,
            accountId = command.accountId,
            reason = command.reason,
            payload = mapOf(
                "runId" to runId,
                "accountId" to command.accountId,
                "currency" to currency,
                "asOfBusinessDate" to command.asOfBusinessDate?.toString(),
                "syntheticOnly" to true
            )
        )
        jdbc.update(
            """
            INSERT INTO ledger_projection_drift_runs (
              run_id, idempotency_key, command_hash, status, requested_by, requested_by_role,
              reason, account_id, currency, as_of_business_date, source_posting_count,
              source_last_posting_id, source_hash, audit_event_id, metadata_json
            )
            VALUES (
              :runId, :idempotencyKey, :commandHash, 'RUNNING', :requestedBy, :requestedByRole,
              :reason, :accountId, :currency, :asOfBusinessDate, :sourcePostingCount,
              :sourceLastPostingId, :sourceHash, :auditEventId, CAST(:metadataJson AS jsonb)
            )
            """.trimIndent(),
            mapOf(
                "runId" to runId,
                "idempotencyKey" to command.idempotencyKey,
                "commandHash" to commandHash,
                "requestedBy" to command.requestedBy,
                "requestedByRole" to command.requestedByRole,
                "reason" to command.reason,
                "accountId" to command.accountId,
                "currency" to currency,
                "asOfBusinessDate" to command.asOfBusinessDate,
                "sourcePostingCount" to aggregate.sourcePostingCount,
                "sourceLastPostingId" to aggregate.sourceLastPostingId,
                "sourceHash" to aggregate.sourceHash,
                "auditEventId" to auditEventId,
                "metadataJson" to metadataJson("operation" to "projection-drift")
            )
        )
        val items = insertDriftItems(runId, targets, sourceStats)
        jdbc.update(
            """
            UPDATE ledger_projection_drift_runs
            SET status = 'COMPLETED',
                drift_item_count = :driftItemCount,
                completed_at = now()
            WHERE run_id = :runId
            """.trimIndent(),
            mapOf("runId" to runId, "driftItemCount" to items.size)
        )
        return driftRunResponse(runId, replayed = false)
    }

    @Transactional(readOnly = true)
    fun driftRun(runId: String): LedgerProjectionDriftRunResponse =
        driftRunResponse(runId, replayed = false)

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun requestRebuild(command: LedgerProjectionRebuildRequestCommand): LedgerProjectionRebuildRequestResponse {
        BankingLabAuthContext.requireActor(command.requestedBy, command.requestedByRole)
        validateRebuildRequestCommand(command)
        val currency = normalizeCurrency(command.currency)
        val commandHash = commandHash(
            mapOf(
                "requestedBy" to command.requestedBy,
                "requestedByRole" to command.requestedByRole,
                "reason" to command.reason,
                "accountId" to command.accountId,
                "currency" to currency,
                "driftRunId" to command.driftRunId
            )
        )
        existingRebuildRequestByIdempotencyKey(command.idempotencyKey)?.let { existing ->
            requireSameCommandHash(existing.commandHash, commandHash)
            return rebuildRequestResponse(existing.requestId, replayed = true)
        }
        if (command.driftRunId != null) {
            findDriftRun(command.driftRunId)
        }

        val requestId = nextId("LPR")
        val targets = projectionTargets(command.accountId, currency)
        val aggregate = aggregateSourceStats(targets, sourceStats(command.accountId, currency, null))
        val beforeProjectionHash = projectionHash(targets)
        val approval = approvals.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.LEDGER_PROJECTION_REBUILD,
                businessReferenceId = requestId,
                requestedBy = command.requestedBy,
                requestReason = command.reason,
                requestedByRole = command.requestedByRole,
                beforeSnapshot = mapOf(
                    "accountId" to command.accountId,
                    "currency" to currency,
                    "sourceHash" to aggregate.sourceHash,
                    "projectionHash" to beforeProjectionHash
                ),
                afterSnapshot = mapOf(
                    "status" to "PENDING_APPROVAL",
                    "requestId" to requestId,
                    "driftRunId" to command.driftRunId,
                    "syntheticOnly" to true
                ),
                screenId = "OPS-LEDGER-102"
            )
        )
        jdbc.update(
            """
            INSERT INTO ledger_projection_rebuild_requests (
              request_id, idempotency_key, command_hash, drift_run_id, account_id, currency,
              status, requested_by, requested_by_role, reason, approval_id,
              before_source_posting_count, before_source_last_posting_id, before_source_hash,
              before_projection_hash, metadata_json
            )
            VALUES (
              :requestId, :idempotencyKey, :commandHash, :driftRunId, :accountId, :currency,
              'PENDING_APPROVAL', :requestedBy, :requestedByRole, :reason, :approvalId,
              :beforeSourcePostingCount, :beforeSourceLastPostingId, :beforeSourceHash,
              :beforeProjectionHash, CAST(:metadataJson AS jsonb)
            )
            """.trimIndent(),
            mapOf(
                "requestId" to requestId,
                "idempotencyKey" to command.idempotencyKey,
                "commandHash" to commandHash,
                "driftRunId" to command.driftRunId,
                "accountId" to command.accountId,
                "currency" to currency,
                "requestedBy" to command.requestedBy,
                "requestedByRole" to command.requestedByRole,
                "reason" to command.reason,
                "approvalId" to approval.approvalId,
                "beforeSourcePostingCount" to aggregate.sourcePostingCount,
                "beforeSourceLastPostingId" to aggregate.sourceLastPostingId,
                "beforeSourceHash" to aggregate.sourceHash,
                "beforeProjectionHash" to beforeProjectionHash,
                "metadataJson" to metadataJson("operation" to "projection-rebuild-request")
            )
        )
        return rebuildRequestResponse(requestId, replayed = false)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun approveRebuildRequest(
        requestId: String,
        command: LedgerProjectionRebuildApproveCommand
    ): LedgerProjectionRebuildReviewResponse {
        BankingLabAuthContext.requireActor(command.approvedBy, command.approvedByRole)
        requireNonBlank(command.approvedBy, "approvedBy")
        requireProjectionCheckerRole(command.approvedByRole)
        val request = findRebuildRequestForUpdate(requestId)
        if (request.status == "APPROVED" || request.status == "EXECUTED") {
            return LedgerProjectionRebuildReviewResponse(request, approvals.approval(request.approvalId), replayed = true)
        }
        if (request.status == "REJECTED") {
            throw WorkflowErrors.stateViolation("rejected projection rebuild request cannot be approved")
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
            UPDATE ledger_projection_rebuild_requests
            SET status = 'APPROVED',
                approved_by = :approvedBy,
                approved_at = now(),
                updated_at = now()
            WHERE request_id = :requestId
            """.trimIndent(),
            mapOf("requestId" to requestId, "approvedBy" to command.approvedBy)
        )
        return LedgerProjectionRebuildReviewResponse(findRebuildRequest(requestId), approval, replayed = false)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun rejectRebuildRequest(
        requestId: String,
        command: LedgerProjectionRebuildRejectCommand
    ): LedgerProjectionRebuildReviewResponse {
        BankingLabAuthContext.requireActor(command.rejectedBy, command.rejectedByRole)
        requireNonBlank(command.rejectedBy, "rejectedBy")
        requireNonBlank(command.rejectReason, "rejectReason")
        requireProjectionCheckerRole(command.rejectedByRole)
        val request = findRebuildRequestForUpdate(requestId)
        if (request.status == "REJECTED") {
            return LedgerProjectionRebuildReviewResponse(request, approvals.approval(request.approvalId), replayed = true)
        }
        if (request.status == "APPROVED" || request.status == "EXECUTED") {
            throw WorkflowErrors.stateViolation("approved projection rebuild request cannot be rejected")
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
            UPDATE ledger_projection_rebuild_requests
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
        return LedgerProjectionRebuildReviewResponse(findRebuildRequest(requestId), approval, replayed = false)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun executeRebuild(requestId: String, command: LedgerProjectionRebuildExecuteCommand): LedgerProjectionRebuildRunResponse {
        BankingLabAuthContext.requireActor(command.executedBy, command.executedByRole)
        validateExecuteCommand(command)
        existingRebuildRunByIdempotencyKey(command.idempotencyKey)?.let { existing ->
            requireSameCommandHash(existing.commandHash, executeCommandHash(requestId, command))
            return rebuildRunResponse(existing.runId, replayed = true)
        }
        existingRebuildRunByRequestId(requestId)?.let { existing ->
            return rebuildRunResponse(existing.runId, replayed = true)
        }

        val request = findRebuildRequestForUpdate(requestId)
        if (request.status != "APPROVED") {
            throw WorkflowErrors.stateViolation("projection rebuild requires approved maker-checker request")
        }
        val approval = approvals.approval(request.approvalId)
        if (approval.status != ApprovalStatus.APPROVED) {
            throw WorkflowErrors.stateViolation("projection rebuild approval is not approved")
        }

        lockProjectionTargets(request.accountId, request.currency)
        val targetsBefore = projectionTargets(request.accountId, request.currency)
        val sourceStats = sourceStats(request.accountId, request.currency, null)
        val beforeAggregate = aggregateSourceStats(targetsBefore, sourceStats)
        val beforeProjectionHash = projectionHash(targetsBefore)
        val runId = nextId("LPRUN")
        val commandHash = executeCommandHash(requestId, command)
        jdbc.update(
            """
            INSERT INTO ledger_projection_rebuild_runs (
              run_id, request_id, idempotency_key, command_hash, approval_id, status,
              executed_by, executed_by_role, reason, account_id, currency,
              before_source_posting_count, before_source_last_posting_id, before_source_hash,
              before_projection_hash, after_source_hash, after_projection_hash, metadata_json
            )
            VALUES (
              :runId, :requestId, :idempotencyKey, :commandHash, :approvalId, 'RUNNING',
              :executedBy, :executedByRole, :reason, :accountId, :currency,
              :beforeSourcePostingCount, :beforeSourceLastPostingId, :beforeSourceHash,
              :beforeProjectionHash, :afterSourceHash, :afterProjectionHash, CAST(:metadataJson AS jsonb)
            )
            """.trimIndent(),
            mapOf(
                "runId" to runId,
                "requestId" to requestId,
                "idempotencyKey" to command.idempotencyKey,
                "commandHash" to commandHash,
                "approvalId" to request.approvalId,
                "executedBy" to command.executedBy,
                "executedByRole" to command.executedByRole,
                "reason" to command.reason,
                "accountId" to request.accountId,
                "currency" to request.currency,
                "beforeSourcePostingCount" to beforeAggregate.sourcePostingCount,
                "beforeSourceLastPostingId" to beforeAggregate.sourceLastPostingId,
                "beforeSourceHash" to beforeAggregate.sourceHash,
                "beforeProjectionHash" to beforeProjectionHash,
                "afterSourceHash" to beforeAggregate.sourceHash,
                "afterProjectionHash" to beforeProjectionHash,
                "metadataJson" to metadataJson("operation" to "projection-rebuild-execute")
            )
        )
        val items = rebuildProjectionItems(runId, targetsBefore, sourceStats)
        val targetsAfter = projectionTargets(request.accountId, request.currency)
        val afterAggregate = aggregateSourceStats(targetsAfter, sourceStats(request.accountId, request.currency, null))
        val afterProjectionHash = projectionHash(targetsAfter)
        val auditEventId = appendAudit(
            eventType = "LEDGER_PROJECTION_REBUILD_EXECUTED",
            actorId = command.executedBy,
            actorRole = command.executedByRole,
            screenId = "OPS-LEDGER-103",
            businessReferenceId = requestId,
            accountId = request.accountId,
            reason = command.reason,
            payload = mapOf(
                "requestId" to requestId,
                "runId" to runId,
                "approvalId" to request.approvalId,
                "beforeSourceHash" to beforeAggregate.sourceHash,
                "afterSourceHash" to afterAggregate.sourceHash,
                "beforeProjectionHash" to beforeProjectionHash,
                "afterProjectionHash" to afterProjectionHash,
                "rebuiltItemCount" to items.size,
                "syntheticOnly" to true
            )
        )
        jdbc.update(
            """
            UPDATE ledger_projection_rebuild_runs
            SET status = 'COMPLETED',
                after_source_posting_count = :afterSourcePostingCount,
                after_source_last_posting_id = :afterSourceLastPostingId,
                after_source_hash = :afterSourceHash,
                after_projection_hash = :afterProjectionHash,
                rebuilt_item_count = :rebuiltItemCount,
                audit_event_id = :auditEventId,
                completed_at = now()
            WHERE run_id = :runId
            """.trimIndent(),
            mapOf(
                "runId" to runId,
                "afterSourcePostingCount" to afterAggregate.sourcePostingCount,
                "afterSourceLastPostingId" to afterAggregate.sourceLastPostingId,
                "afterSourceHash" to afterAggregate.sourceHash,
                "afterProjectionHash" to afterProjectionHash,
                "rebuiltItemCount" to items.size,
                "auditEventId" to auditEventId
            )
        )
        jdbc.update(
            """
            UPDATE ledger_projection_rebuild_requests
            SET status = 'EXECUTED',
                updated_at = now()
            WHERE request_id = :requestId
            """.trimIndent(),
            mapOf("requestId" to requestId)
        )
        return rebuildRunResponse(runId, replayed = false)
    }

    @Transactional(readOnly = true)
    fun rebuildRun(runId: String): LedgerProjectionRebuildRunResponse =
        rebuildRunResponse(runId, replayed = false)

    private fun insertDriftItems(
        runId: String,
        targets: List<ProjectionTarget>,
        sourceStats: Map<AccountCurrency, SourceStat>
    ): List<LedgerProjectionDriftItemDto> {
        targets.forEach { target ->
            val source = sourceStats[target.key] ?: SourceStat.empty(target.accountId, target.currency)
            val expectedLedger = source.expectedLedgerBalanceMinor
            val expectedAvailable = expectedLedger - target.holdAmountMinor
            val driftAmount = expectedLedger - (target.ledgerBalanceMinor ?: 0)
            val drifted = target.ledgerBalanceMinor != expectedLedger ||
                target.availableBalanceMinor != expectedAvailable
            if (drifted) {
                jdbc.update(
                    """
                    INSERT INTO ledger_projection_drift_items (
                      item_id, run_id, account_id, currency, expected_ledger_balance_minor,
                      actual_ledger_balance_minor, expected_available_balance_minor,
                      actual_available_balance_minor, hold_amount_minor, drift_amount_minor,
                      source_posting_count, source_last_posting_id, source_hash, status
                    )
                    VALUES (
                      :itemId, :runId, :accountId, :currency, :expectedLedgerBalanceMinor,
                      :actualLedgerBalanceMinor, :expectedAvailableBalanceMinor,
                      :actualAvailableBalanceMinor, :holdAmountMinor, :driftAmountMinor,
                      :sourcePostingCount, :sourceLastPostingId, :sourceHash, :status
                    )
                    """.trimIndent(),
                    mapOf(
                        "itemId" to nextId("LPDI"),
                        "runId" to runId,
                        "accountId" to target.accountId,
                        "currency" to target.currency,
                        "expectedLedgerBalanceMinor" to expectedLedger,
                        "actualLedgerBalanceMinor" to target.ledgerBalanceMinor,
                        "expectedAvailableBalanceMinor" to expectedAvailable,
                        "actualAvailableBalanceMinor" to target.availableBalanceMinor,
                        "holdAmountMinor" to target.holdAmountMinor,
                        "driftAmountMinor" to driftAmount,
                        "sourcePostingCount" to source.sourcePostingCount,
                        "sourceLastPostingId" to source.sourceLastPostingId,
                        "sourceHash" to source.sourceHash,
                        "status" to if (target.ledgerBalanceMinor == null) "PROJECTION_MISSING" else "DRIFT_DETECTED"
                    )
                )
            }
        }
        return driftItems(runId)
    }

    private fun rebuildProjectionItems(
        runId: String,
        targets: List<ProjectionTarget>,
        sourceStats: Map<AccountCurrency, SourceStat>
    ): List<LedgerProjectionRebuildItemDto> {
        targets.forEach { target ->
            val source = sourceStats[target.key] ?: SourceStat.empty(target.accountId, target.currency)
            val rebuiltLedger = source.expectedLedgerBalanceMinor
            val rebuiltAvailable = rebuiltLedger - target.holdAmountMinor
            val driftAmount = rebuiltLedger - (target.ledgerBalanceMinor ?: 0)
            val itemStatus = when {
                target.ledgerBalanceMinor == null -> "PROJECTION_CREATED"
                target.ledgerBalanceMinor == rebuiltLedger &&
                    target.availableBalanceMinor == rebuiltAvailable &&
                    target.lastPostingId == source.sourceLastPostingId -> "UNCHANGED"
                else -> "REBUILT"
            }
            jdbc.update(
                """
                INSERT INTO account_balance_projections (
                  account_id, currency, ledger_balance_minor, available_balance_minor,
                  hold_amount_minor, last_posting_id, version
                )
                VALUES (
                  :accountId, :currency, :rebuiltLedgerBalanceMinor, :rebuiltAvailableBalanceMinor,
                  :holdAmountMinor, :sourceLastPostingId, 1
                )
                ON CONFLICT (account_id, currency) DO UPDATE SET
                  ledger_balance_minor = EXCLUDED.ledger_balance_minor,
                  available_balance_minor = EXCLUDED.available_balance_minor,
                  last_posting_id = EXCLUDED.last_posting_id,
                  version = account_balance_projections.version + 1,
                  updated_at = now()
                """.trimIndent(),
                mapOf(
                    "accountId" to target.accountId,
                    "currency" to target.currency,
                    "rebuiltLedgerBalanceMinor" to rebuiltLedger,
                    "rebuiltAvailableBalanceMinor" to rebuiltAvailable,
                    "holdAmountMinor" to target.holdAmountMinor,
                    "sourceLastPostingId" to source.sourceLastPostingId
                )
            )
            jdbc.update(
                """
                INSERT INTO ledger_projection_rebuild_items (
                  item_id, run_id, account_id, currency, previous_ledger_balance_minor,
                  rebuilt_ledger_balance_minor, previous_available_balance_minor,
                  rebuilt_available_balance_minor, hold_amount_minor, drift_amount_minor,
                  source_posting_count, source_last_posting_id, source_hash, status
                )
                VALUES (
                  :itemId, :runId, :accountId, :currency, :previousLedgerBalanceMinor,
                  :rebuiltLedgerBalanceMinor, :previousAvailableBalanceMinor,
                  :rebuiltAvailableBalanceMinor, :holdAmountMinor, :driftAmountMinor,
                  :sourcePostingCount, :sourceLastPostingId, :sourceHash, :status
                )
                """.trimIndent(),
                mapOf(
                    "itemId" to nextId("LPRI"),
                    "runId" to runId,
                    "accountId" to target.accountId,
                    "currency" to target.currency,
                    "previousLedgerBalanceMinor" to target.ledgerBalanceMinor,
                    "rebuiltLedgerBalanceMinor" to rebuiltLedger,
                    "previousAvailableBalanceMinor" to target.availableBalanceMinor,
                    "rebuiltAvailableBalanceMinor" to rebuiltAvailable,
                    "holdAmountMinor" to target.holdAmountMinor,
                    "driftAmountMinor" to driftAmount,
                    "sourcePostingCount" to source.sourcePostingCount,
                    "sourceLastPostingId" to source.sourceLastPostingId,
                    "sourceHash" to source.sourceHash,
                    "status" to itemStatus
                )
            )
        }
        return rebuildItems(runId)
    }

    private fun projectionTargets(accountId: String?, currency: String?): List<ProjectionTarget> {
        val params = mutableMapOf<String, Any?>()
        val accountClause = if (!accountId.isNullOrBlank()) {
            params["accountId"] = accountId
            "AND a.account_id = :accountId"
        } else {
            ""
        }
        val projectionCurrencyJoin = if (!currency.isNullOrBlank()) {
            params["currency"] = currency
            "AND p.currency = :currency"
        } else {
            ""
        }
        val currencyClause = if (!currency.isNullOrBlank()) {
            "AND (a.currency = :currency OR p.currency = :currency)"
        } else {
            ""
        }
        val targets = jdbc.query(
            """
            SELECT a.account_id,
                   COALESCE(p.currency, a.currency) AS currency,
                   p.ledger_balance_minor,
                   p.available_balance_minor,
                   COALESCE(p.hold_amount_minor, 0) AS hold_amount_minor,
                   p.last_posting_id
            FROM accounts a
            LEFT JOIN account_balance_projections p
              ON p.account_id = a.account_id
             $projectionCurrencyJoin
            WHERE 1 = 1
              $accountClause
              $currencyClause
            ORDER BY a.account_id, COALESCE(p.currency, a.currency)
            """.trimIndent(),
            params,
            ::mapProjectionTarget
        )
        if (!accountId.isNullOrBlank() && targets.isEmpty()) {
            throw WorkflowErrors.notFound("account not found for projection integrity check: $accountId")
        }
        return targets
    }

    private fun lockProjectionTargets(accountId: String?, currency: String?) {
        val accountParams = mutableMapOf<String, Any?>()
        val accountClause = if (!accountId.isNullOrBlank()) {
            accountParams["accountId"] = accountId
            "WHERE account_id = :accountId"
        } else {
            ""
        }
        jdbc.query(
            """
            SELECT account_id
            FROM accounts
            $accountClause
            ORDER BY account_id
            FOR UPDATE
            """.trimIndent(),
            accountParams
        ) { _, _ -> Unit }

        val projectionParams = mutableMapOf<String, Any?>()
        val projectionClauses = mutableListOf<String>()
        if (!accountId.isNullOrBlank()) {
            projectionParams["accountId"] = accountId
            projectionClauses += "account_id = :accountId"
        }
        if (!currency.isNullOrBlank()) {
            projectionParams["currency"] = currency
            projectionClauses += "currency = :currency"
        }
        val where = if (projectionClauses.isEmpty()) "" else "WHERE ${projectionClauses.joinToString(" AND ")}"
        jdbc.query(
            """
            SELECT account_id, currency
            FROM account_balance_projections
            $where
            ORDER BY account_id, currency
            FOR UPDATE
            """.trimIndent(),
            projectionParams
        ) { _, _ -> Unit }
    }

    private fun sourceStats(
        accountId: String?,
        currency: String?,
        asOfBusinessDate: LocalDate?
    ): Map<AccountCurrency, SourceStat> {
        val params = mutableMapOf<String, Any?>()
        val clauses = mutableListOf<String>()
        if (!accountId.isNullOrBlank()) {
            params["accountId"] = accountId
            clauses += "lp.account_id = :accountId"
        }
        if (!currency.isNullOrBlank()) {
            params["currency"] = currency
            clauses += "lp.currency = :currency"
        }
        if (asOfBusinessDate != null) {
            params["asOfBusinessDate"] = asOfBusinessDate
            clauses += "lt.business_date <= :asOfBusinessDate"
        }
        val where = if (clauses.isEmpty()) "" else "WHERE ${clauses.joinToString(" AND ")}"
        val mutable = linkedMapOf<AccountCurrency, MutableSourceStat>()
        jdbc.query(
            """
            SELECT lp.account_id, lp.currency, lp.ledger_posting_id, lp.ledger_transaction_id,
                   lp.direction, lp.amount_minor, lp.posting_type, lt.business_date
            FROM ledger_postings lp
            JOIN ledger_transactions lt
              ON lt.ledger_transaction_id = lp.ledger_transaction_id
            $where
            ORDER BY lp.account_id, lp.currency, lt.business_date, lp.ledger_transaction_id, lp.ledger_posting_id
            """.trimIndent(),
            params
        ) { rs, _ ->
            val key = AccountCurrency(rs.getString("account_id"), rs.getString("currency").trim())
            val stat = mutable.getOrPut(key) { MutableSourceStat(key.accountId, key.currency) }
            val direction = rs.getString("direction")
            val amount = rs.getLong("amount_minor")
            val signedAmount = if (direction == "DEBIT") -amount else amount
            val postingId = rs.getString("ledger_posting_id")
            stat.expectedLedgerBalanceMinor += signedAmount
            stat.sourcePostingCount += 1
            stat.sourceLastPostingId = postingId
            stat.hashLines += listOf(
                postingId,
                rs.getString("ledger_transaction_id"),
                direction,
                amount.toString(),
                rs.getString("posting_type"),
                rs.getObject("business_date", LocalDate::class.java).toString()
            ).joinToString("|")
        }
        return mutable.mapValues { (_, stat) -> stat.toSourceStat() }
    }

    private fun aggregateSourceStats(
        targets: List<ProjectionTarget>,
        sourceStats: Map<AccountCurrency, SourceStat>
    ): AggregateSourceStats {
        val lines = targets
            .map { target ->
                val source = sourceStats[target.key] ?: SourceStat.empty(target.accountId, target.currency)
                listOf(
                    target.accountId,
                    target.currency,
                    source.expectedLedgerBalanceMinor.toString(),
                    source.sourcePostingCount.toString(),
                    source.sourceLastPostingId ?: "",
                    source.sourceHash
                ).joinToString("|")
            }
        return AggregateSourceStats(
            sourcePostingCount = targets.sumOf { sourceStats[it.key]?.sourcePostingCount ?: 0 },
            sourceLastPostingId = targets.mapNotNull { sourceStats[it.key]?.sourceLastPostingId }.lastOrNull(),
            sourceHash = sha256(lines.joinToString("\n"))
        )
    }

    private fun projectionHash(targets: List<ProjectionTarget>): String =
        sha256(
            targets.joinToString("\n") { target ->
                listOf(
                    target.accountId,
                    target.currency,
                    target.ledgerBalanceMinor?.toString() ?: "",
                    target.availableBalanceMinor?.toString() ?: "",
                    target.holdAmountMinor.toString(),
                    target.lastPostingId ?: ""
                ).joinToString("|")
            }
        )

    private fun driftRunResponse(runId: String, replayed: Boolean): LedgerProjectionDriftRunResponse =
        LedgerProjectionDriftRunResponse(findDriftRun(runId), driftItems(runId), replayed)

    private fun rebuildRequestResponse(requestId: String, replayed: Boolean): LedgerProjectionRebuildRequestResponse {
        val request = findRebuildRequest(requestId)
        return LedgerProjectionRebuildRequestResponse(request, approvals.approval(request.approvalId), replayed)
    }

    private fun rebuildRunResponse(runId: String, replayed: Boolean): LedgerProjectionRebuildRunResponse =
        LedgerProjectionRebuildRunResponse(findRebuildRun(runId), rebuildItems(runId), replayed)

    private fun findDriftRun(runId: String): LedgerProjectionDriftRunDto =
        jdbc.query(
            driftRunSql("WHERE run_id = :runId"),
            mapOf("runId" to runId),
            ::mapDriftRun
        ).singleOrNull() ?: throw WorkflowErrors.notFound("projection drift run not found: $runId")

    private fun existingDriftRunByIdempotencyKey(idempotencyKey: String): DriftRunIdentity? =
        jdbc.query(
            """
            SELECT run_id, command_hash
            FROM ledger_projection_drift_runs
            WHERE idempotency_key = :idempotencyKey
            """.trimIndent(),
            mapOf("idempotencyKey" to idempotencyKey)
        ) { rs, _ -> DriftRunIdentity(rs.getString("run_id"), rs.getString("command_hash")) }
            .singleOrNull()

    private fun driftItems(runId: String): List<LedgerProjectionDriftItemDto> =
        jdbc.query(
            """
            SELECT item_id, run_id, account_id, currency, expected_ledger_balance_minor,
                   actual_ledger_balance_minor, expected_available_balance_minor,
                   actual_available_balance_minor, hold_amount_minor, drift_amount_minor,
                   source_posting_count, source_last_posting_id, source_hash, status, created_at
            FROM ledger_projection_drift_items
            WHERE run_id = :runId
            ORDER BY account_id, currency
            """.trimIndent(),
            mapOf("runId" to runId),
            ::mapDriftItem
        )

    private fun findRebuildRequest(requestId: String): LedgerProjectionRebuildRequestDto =
        jdbc.query(
            rebuildRequestSql("WHERE request_id = :requestId"),
            mapOf("requestId" to requestId),
            ::mapRebuildRequest
        ).singleOrNull() ?: throw WorkflowErrors.notFound("projection rebuild request not found: $requestId")

    private fun findRebuildRequestForUpdate(requestId: String): LedgerProjectionRebuildRequestDto =
        jdbc.query(
            rebuildRequestSql("WHERE request_id = :requestId FOR UPDATE"),
            mapOf("requestId" to requestId),
            ::mapRebuildRequest
        ).singleOrNull() ?: throw WorkflowErrors.notFound("projection rebuild request not found: $requestId")

    private fun existingRebuildRequestByIdempotencyKey(idempotencyKey: String): RebuildRequestIdentity? =
        jdbc.query(
            """
            SELECT request_id, command_hash
            FROM ledger_projection_rebuild_requests
            WHERE idempotency_key = :idempotencyKey
            """.trimIndent(),
            mapOf("idempotencyKey" to idempotencyKey)
        ) { rs, _ -> RebuildRequestIdentity(rs.getString("request_id"), rs.getString("command_hash")) }
            .singleOrNull()

    private fun findRebuildRun(runId: String): LedgerProjectionRebuildRunDto =
        jdbc.query(
            rebuildRunSql("WHERE run_id = :runId"),
            mapOf("runId" to runId),
            ::mapRebuildRun
        ).singleOrNull() ?: throw WorkflowErrors.notFound("projection rebuild run not found: $runId")

    private fun existingRebuildRunByIdempotencyKey(idempotencyKey: String): RebuildRunIdentity? =
        jdbc.query(
            """
            SELECT run_id, command_hash
            FROM ledger_projection_rebuild_runs
            WHERE idempotency_key = :idempotencyKey
            """.trimIndent(),
            mapOf("idempotencyKey" to idempotencyKey)
        ) { rs, _ -> RebuildRunIdentity(rs.getString("run_id"), rs.getString("command_hash")) }
            .singleOrNull()

    private fun existingRebuildRunByRequestId(requestId: String): RebuildRunIdentity? =
        jdbc.query(
            """
            SELECT run_id, command_hash
            FROM ledger_projection_rebuild_runs
            WHERE request_id = :requestId
            """.trimIndent(),
            mapOf("requestId" to requestId)
        ) { rs, _ -> RebuildRunIdentity(rs.getString("run_id"), rs.getString("command_hash")) }
            .singleOrNull()

    private fun rebuildItems(runId: String): List<LedgerProjectionRebuildItemDto> =
        jdbc.query(
            """
            SELECT item_id, run_id, account_id, currency, previous_ledger_balance_minor,
                   rebuilt_ledger_balance_minor, previous_available_balance_minor,
                   rebuilt_available_balance_minor, hold_amount_minor, drift_amount_minor,
                   source_posting_count, source_last_posting_id, source_hash, status, created_at
            FROM ledger_projection_rebuild_items
            WHERE run_id = :runId
            ORDER BY account_id, currency
            """.trimIndent(),
            mapOf("runId" to runId),
            ::mapRebuildItem
        )

    private fun driftRunSql(suffix: String): String =
        """
        SELECT run_id, status, requested_by, requested_by_role, reason, account_id, currency,
               as_of_business_date, source_posting_count, source_last_posting_id, source_hash,
               drift_item_count, audit_event_id, synthetic_only, created_at, completed_at
        FROM ledger_projection_drift_runs
        $suffix
        """.trimIndent()

    private fun rebuildRequestSql(suffix: String): String =
        """
        SELECT request_id, drift_run_id, account_id, currency, status, requested_by,
               requested_by_role, reason, approval_id, approved_by, approved_at,
               rejected_by, rejected_at, reject_reason, before_source_posting_count,
               before_source_last_posting_id, before_source_hash, before_projection_hash,
               synthetic_only, created_at, updated_at
        FROM ledger_projection_rebuild_requests
        $suffix
        """.trimIndent()

    private fun rebuildRunSql(suffix: String): String =
        """
        SELECT run_id, request_id, approval_id, status, executed_by, executed_by_role,
               reason, account_id, currency, before_source_posting_count,
               before_source_last_posting_id, before_source_hash, before_projection_hash,
               after_source_posting_count, after_source_last_posting_id, after_source_hash,
               after_projection_hash, rebuilt_item_count, audit_event_id, synthetic_only,
               created_at, completed_at
        FROM ledger_projection_rebuild_runs
        $suffix
        """.trimIndent()

    private fun mapProjectionTarget(rs: ResultSet, rowNum: Int): ProjectionTarget =
        ProjectionTarget(
            accountId = rs.getString("account_id"),
            currency = rs.getString("currency").trim(),
            ledgerBalanceMinor = nullableLong(rs, "ledger_balance_minor"),
            availableBalanceMinor = nullableLong(rs, "available_balance_minor"),
            holdAmountMinor = rs.getLong("hold_amount_minor"),
            lastPostingId = rs.getString("last_posting_id")
        )

    private fun mapDriftRun(rs: ResultSet, rowNum: Int): LedgerProjectionDriftRunDto =
        LedgerProjectionDriftRunDto(
            runId = rs.getString("run_id"),
            status = rs.getString("status"),
            requestedBy = rs.getString("requested_by"),
            requestedByRole = rs.getString("requested_by_role"),
            reason = rs.getString("reason"),
            accountId = rs.getString("account_id"),
            currency = rs.getString("currency")?.trim(),
            asOfBusinessDate = rs.getObject("as_of_business_date", LocalDate::class.java),
            sourcePostingCount = rs.getLong("source_posting_count"),
            sourceLastPostingId = rs.getString("source_last_posting_id"),
            sourceHash = rs.getString("source_hash"),
            driftItemCount = rs.getInt("drift_item_count"),
            auditEventId = rs.getString("audit_event_id"),
            syntheticOnly = rs.getBoolean("synthetic_only"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            completedAt = rs.getObject("completed_at", OffsetDateTime::class.java)
        )

    private fun mapDriftItem(rs: ResultSet, rowNum: Int): LedgerProjectionDriftItemDto =
        LedgerProjectionDriftItemDto(
            itemId = rs.getString("item_id"),
            runId = rs.getString("run_id"),
            accountId = rs.getString("account_id"),
            currency = rs.getString("currency").trim(),
            expectedLedgerBalanceMinor = rs.getLong("expected_ledger_balance_minor"),
            actualLedgerBalanceMinor = nullableLong(rs, "actual_ledger_balance_minor"),
            expectedAvailableBalanceMinor = rs.getLong("expected_available_balance_minor"),
            actualAvailableBalanceMinor = nullableLong(rs, "actual_available_balance_minor"),
            holdAmountMinor = rs.getLong("hold_amount_minor"),
            driftAmountMinor = rs.getLong("drift_amount_minor"),
            sourcePostingCount = rs.getLong("source_posting_count"),
            sourceLastPostingId = rs.getString("source_last_posting_id"),
            sourceHash = rs.getString("source_hash"),
            status = rs.getString("status"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
        )

    private fun mapRebuildRequest(rs: ResultSet, rowNum: Int): LedgerProjectionRebuildRequestDto =
        LedgerProjectionRebuildRequestDto(
            requestId = rs.getString("request_id"),
            driftRunId = rs.getString("drift_run_id"),
            accountId = rs.getString("account_id"),
            currency = rs.getString("currency")?.trim(),
            status = rs.getString("status"),
            requestedBy = rs.getString("requested_by"),
            requestedByRole = rs.getString("requested_by_role"),
            reason = rs.getString("reason"),
            approvalId = rs.getString("approval_id"),
            approvedBy = rs.getString("approved_by"),
            approvedAt = rs.getObject("approved_at", OffsetDateTime::class.java),
            rejectedBy = rs.getString("rejected_by"),
            rejectedAt = rs.getObject("rejected_at", OffsetDateTime::class.java),
            rejectReason = rs.getString("reject_reason"),
            beforeSourcePostingCount = rs.getLong("before_source_posting_count"),
            beforeSourceLastPostingId = rs.getString("before_source_last_posting_id"),
            beforeSourceHash = rs.getString("before_source_hash"),
            beforeProjectionHash = rs.getString("before_projection_hash"),
            syntheticOnly = rs.getBoolean("synthetic_only"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java)
        )

    private fun mapRebuildRun(rs: ResultSet, rowNum: Int): LedgerProjectionRebuildRunDto =
        LedgerProjectionRebuildRunDto(
            runId = rs.getString("run_id"),
            requestId = rs.getString("request_id"),
            approvalId = rs.getString("approval_id"),
            status = rs.getString("status"),
            executedBy = rs.getString("executed_by"),
            executedByRole = rs.getString("executed_by_role"),
            reason = rs.getString("reason"),
            accountId = rs.getString("account_id"),
            currency = rs.getString("currency")?.trim(),
            beforeSourcePostingCount = rs.getLong("before_source_posting_count"),
            beforeSourceLastPostingId = rs.getString("before_source_last_posting_id"),
            beforeSourceHash = rs.getString("before_source_hash"),
            beforeProjectionHash = rs.getString("before_projection_hash"),
            afterSourcePostingCount = rs.getLong("after_source_posting_count"),
            afterSourceLastPostingId = rs.getString("after_source_last_posting_id"),
            afterSourceHash = rs.getString("after_source_hash"),
            afterProjectionHash = rs.getString("after_projection_hash"),
            rebuiltItemCount = rs.getInt("rebuilt_item_count"),
            auditEventId = rs.getString("audit_event_id"),
            syntheticOnly = rs.getBoolean("synthetic_only"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            completedAt = rs.getObject("completed_at", OffsetDateTime::class.java)
        )

    private fun mapRebuildItem(rs: ResultSet, rowNum: Int): LedgerProjectionRebuildItemDto =
        LedgerProjectionRebuildItemDto(
            itemId = rs.getString("item_id"),
            runId = rs.getString("run_id"),
            accountId = rs.getString("account_id"),
            currency = rs.getString("currency").trim(),
            previousLedgerBalanceMinor = nullableLong(rs, "previous_ledger_balance_minor"),
            rebuiltLedgerBalanceMinor = rs.getLong("rebuilt_ledger_balance_minor"),
            previousAvailableBalanceMinor = nullableLong(rs, "previous_available_balance_minor"),
            rebuiltAvailableBalanceMinor = rs.getLong("rebuilt_available_balance_minor"),
            holdAmountMinor = rs.getLong("hold_amount_minor"),
            driftAmountMinor = rs.getLong("drift_amount_minor"),
            sourcePostingCount = rs.getLong("source_posting_count"),
            sourceLastPostingId = rs.getString("source_last_posting_id"),
            sourceHash = rs.getString("source_hash"),
            status = rs.getString("status"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
        )

    private fun validateDriftCommand(command: LedgerProjectionDriftRunCommand) {
        requireNonBlank(command.requestedBy, "requestedBy")
        requireNonBlank(command.requestedByRole, "requestedByRole")
        requireNonBlank(command.idempotencyKey, "idempotencyKey")
        requireReason(command.reason)
    }

    private fun validateRebuildRequestCommand(command: LedgerProjectionRebuildRequestCommand) {
        requireNonBlank(command.requestedBy, "requestedBy")
        requireNonBlank(command.requestedByRole, "requestedByRole")
        requireNonBlank(command.idempotencyKey, "idempotencyKey")
        requireReason(command.reason)
    }

    private fun validateExecuteCommand(command: LedgerProjectionRebuildExecuteCommand) {
        requireNonBlank(command.executedBy, "executedBy")
        requireNonBlank(command.executedByRole, "executedByRole")
        requireNonBlank(command.idempotencyKey, "idempotencyKey")
        requireReason(command.reason)
    }

    private fun requireReason(value: String?) {
        if (value.isNullOrBlank()) {
            throw WorkflowErrors.reasonRequired("ledger projection operation requires reason")
        }
    }

    private fun requireNonBlank(value: String?, field: String) {
        if (value.isNullOrBlank()) {
            throw WorkflowErrors.validation("$field is required")
        }
    }

    private fun requireProjectionCheckerRole(role: String) {
        if (role !in setOf("OPS_MANAGER", "COMPLIANCE_MANAGER", "BRANCH_MANAGER")) {
            throw WorkflowErrors.authorizationViolation("role cannot approve ledger projection rebuild")
        }
    }

    private fun normalizeCurrency(value: String?): String? =
        value?.trim()?.uppercase()?.takeIf { it.isNotBlank() }

    private fun nullableLong(rs: ResultSet, column: String): Long? {
        val value = rs.getLong(column)
        return if (rs.wasNull()) null else value
    }

    private fun appendAudit(
        eventType: String,
        actorId: String,
        actorRole: String,
        screenId: String,
        businessReferenceId: String,
        accountId: String?,
        reason: String,
        payload: Map<String, Any?>
    ): String =
        auditEvents.append(
            eventType = eventType,
            actorType = "STAFF",
            actorId = actorId,
            actorRole = actorRole,
            screenId = screenId,
            businessReferenceId = businessReferenceId,
            accountId = accountId,
            reason = reason,
            payload = payload
        )

    private fun executeCommandHash(requestId: String, command: LedgerProjectionRebuildExecuteCommand): String =
        commandHash(
            mapOf(
                "requestId" to requestId,
                "executedBy" to command.executedBy,
                "executedByRole" to command.executedByRole,
                "reason" to command.reason
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
                message = "idempotency key was reused with a different ledger projection command",
                causeText = "The same idempotency key already has a different command hash.",
                fix = "Retry with the original request body or generate a new idempotency key for a different command."
            )
        }
    }

    private fun nextId(prefix: String): String =
        "$prefix-${UUID.randomUUID().toString().uppercase()}"

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private data class ProjectionTarget(
    val accountId: String,
    val currency: String,
    val ledgerBalanceMinor: Long?,
    val availableBalanceMinor: Long?,
    val holdAmountMinor: Long,
    val lastPostingId: String?
) {
    val key: AccountCurrency = AccountCurrency(accountId, currency)
}

private data class AccountCurrency(
    val accountId: String,
    val currency: String
)

private data class SourceStat(
    val accountId: String,
    val currency: String,
    val expectedLedgerBalanceMinor: Long,
    val sourcePostingCount: Long,
    val sourceLastPostingId: String?,
    val sourceHash: String
) {
    companion object {
        fun empty(accountId: String, currency: String): SourceStat =
            SourceStat(
                accountId = accountId,
                currency = currency,
                expectedLedgerBalanceMinor = 0,
                sourcePostingCount = 0,
                sourceLastPostingId = null,
                sourceHash = emptyHash(accountId, currency)
            )

        private fun emptyHash(accountId: String, currency: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest("NO_POSTINGS|$accountId|$currency".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

private data class MutableSourceStat(
    val accountId: String,
    val currency: String,
    var expectedLedgerBalanceMinor: Long = 0,
    var sourcePostingCount: Long = 0,
    var sourceLastPostingId: String? = null,
    val hashLines: MutableList<String> = mutableListOf()
) {
    fun toSourceStat(): SourceStat =
        SourceStat(
            accountId = accountId,
            currency = currency,
            expectedLedgerBalanceMinor = expectedLedgerBalanceMinor,
            sourcePostingCount = sourcePostingCount,
            sourceLastPostingId = sourceLastPostingId,
            sourceHash = MessageDigest.getInstance("SHA-256")
                .digest(hashLines.joinToString("\n").toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        )
}

private data class AggregateSourceStats(
    val sourcePostingCount: Long,
    val sourceLastPostingId: String?,
    val sourceHash: String
)

private data class DriftRunIdentity(
    val runId: String,
    val commandHash: String
)

private data class RebuildRequestIdentity(
    val requestId: String,
    val commandHash: String
)

private data class RebuildRunIdentity(
    val runId: String,
    val commandHash: String
)
