package lab.banking.core.onboarding

import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.sql.ResultSet
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
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class CustomerOnboardingService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val approvals: PersistentApprovalService,
    private val auditEvents: AuditEventAppender,
    private val passwordEncoder: PasswordEncoder
) {
    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun requestOnboarding(command: CustomerOnboardingRequestCommand): CustomerOnboardingRequestResponse {
        BankingLabAuthContext.requireActor(command.requestedBy, command.requestedByRole)
        val normalized = normalizeAndValidate(command)
        requireMakerRole(normalized.requestedByRole)
        val commandHash = requestCommandHash(normalized)
        existingRequestByIdempotencyKey(normalized.idempotencyKey)?.let { existing ->
            requireSameCommandHash(existing.commandHash, commandHash)
            return requestResponse(existing.requestId, replayed = true)
        }
        requireUsernameAvailable(normalized.username, excludingRequestId = null)
        val requestedPasswordHash = passwordEncoder.encode(command.temporaryPassword)

        val requestId = nextId("COR")
        val approval = approvals.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.CUSTOMER_ONBOARDING,
                businessReferenceId = requestId,
                requestedBy = normalized.requestedBy,
                requestReason = normalized.reason,
                requestedByRole = normalized.requestedByRole,
                beforeSnapshot = mapOf("customerExists" to false, "syntheticOnly" to true),
                afterSnapshot = mapOf(
                    "requestId" to requestId,
                    "status" to "PENDING_APPROVAL",
                    "customerName" to normalized.customerName,
                    "customerGrade" to normalized.customerGrade,
                    "riskGrade" to normalized.riskGrade,
                    "sourceOfFundsCode" to normalized.sourceOfFundsCode,
                    "transactionPurposeCode" to normalized.transactionPurposeCode,
                    "username" to normalized.username,
                    "passwordProvided" to true,
                    "passwordPolicy" to PASSWORD_POLICY,
                    "realKycProviderCalled" to false,
                    "keycloakAdminApiCalled" to false,
                    "syntheticOnly" to true
                ),
                screenId = "CST-201"
            )
        )

        try {
            jdbc.update(
                """
                INSERT INTO customer_onboarding_requests (
                  request_id, idempotency_key, command_hash, status,
                  requested_by, requested_by_role, reason, approval_id,
                  requested_customer_name, requested_customer_phone, requested_customer_address,
                  requested_customer_grade, requested_risk_grade,
                  requested_source_of_funds_code, requested_transaction_purpose_code,
                  requested_username, password_policy, requested_password_hash, metadata_json
                )
                VALUES (
                  :requestId, :idempotencyKey, :commandHash, 'PENDING_APPROVAL',
                  :requestedBy, :requestedByRole, :reason, :approvalId,
                  :customerName, :customerPhone, :customerAddress,
                  :customerGrade, :riskGrade,
                  :sourceOfFundsCode, :transactionPurposeCode,
                  :username, :passwordPolicy, :requestedPasswordHash, CAST(:metadataJson AS jsonb)
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
                    "customerName" to normalized.customerName,
                    "customerPhone" to normalized.customerPhone,
                    "customerAddress" to normalized.customerAddress,
                    "customerGrade" to normalized.customerGrade,
                    "riskGrade" to normalized.riskGrade,
                    "sourceOfFundsCode" to normalized.sourceOfFundsCode,
                    "transactionPurposeCode" to normalized.transactionPurposeCode,
                    "username" to normalized.username,
                    "passwordPolicy" to PASSWORD_POLICY,
                    "requestedPasswordHash" to requestedPasswordHash,
                    "metadataJson" to metadataJson(
                        "operation" to "customer-onboarding-request",
                        "realKycProviderCalled" to false,
                        "keycloakAdminApiCalled" to false
                    )
                )
            )
        } catch (error: DuplicateKeyException) {
            throw usernameOrIdempotencyConflict(error)
        }

        return requestResponse(requestId, replayed = false)
    }

    @Transactional(readOnly = true)
    fun onboardingRequest(requestId: String): CustomerOnboardingRequestResponse =
        requestResponse(requestId, replayed = false)

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun approveOnboarding(
        requestId: String,
        command: CustomerOnboardingApproveCommand
    ): CustomerOnboardingReviewResponse {
        BankingLabAuthContext.requireActor(command.approvedBy, command.approvedByRole)
        requireNonBlank(command.approvedBy, "approvedBy")
        requireCheckerRole(command.approvedByRole)
        val request = findRequestForUpdate(requestId)
        if (request.status == "APPROVED" || request.status == "EXECUTED") {
            return CustomerOnboardingReviewResponse(request.toDto(), approvals.approval(request.approvalId), replayed = true)
        }
        if (request.status == "REJECTED") {
            throw WorkflowErrors.stateViolation("rejected customer onboarding request cannot be approved")
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
            UPDATE customer_onboarding_requests
            SET status = 'APPROVED',
                approved_by = :approvedBy,
                approved_at = now(),
                updated_at = now()
            WHERE request_id = :requestId
            """.trimIndent(),
            mapOf("requestId" to requestId, "approvedBy" to command.approvedBy)
        )
        return CustomerOnboardingReviewResponse(findRequest(requestId).toDto(), approval, replayed = false)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun rejectOnboarding(
        requestId: String,
        command: CustomerOnboardingRejectCommand
    ): CustomerOnboardingReviewResponse {
        BankingLabAuthContext.requireActor(command.rejectedBy, command.rejectedByRole)
        requireNonBlank(command.rejectedBy, "rejectedBy")
        requireNonBlank(command.rejectReason, "rejectReason")
        requireCheckerRole(command.rejectedByRole)
        val request = findRequestForUpdate(requestId)
        if (request.status == "REJECTED") {
            return CustomerOnboardingReviewResponse(request.toDto(), approvals.approval(request.approvalId), replayed = true)
        }
        if (request.status == "APPROVED" || request.status == "EXECUTED") {
            throw WorkflowErrors.stateViolation("approved customer onboarding request cannot be rejected")
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
            UPDATE customer_onboarding_requests
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
        return CustomerOnboardingReviewResponse(findRequest(requestId).toDto(), approval, replayed = false)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun executeOnboarding(
        requestId: String,
        command: CustomerOnboardingExecuteCommand
    ): CustomerOnboardingExecuteResponse {
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
            throw WorkflowErrors.stateViolation("customer onboarding execution requires approved maker-checker request")
        }
        val approval = approvals.approval(request.approvalId)
        if (approval.status != ApprovalStatus.APPROVED) {
            throw WorkflowErrors.stateViolation("customer onboarding approval is not approved")
        }
        requireUsernameAvailable(request.requestedUsername, excludingRequestId = requestId)

        val customerId = "SYN-CUS-NEW-${nextCustomerSequence().toString().padStart(6, '0')}"
        val authSubject = "SYN-AUTH-$customerId"
        val passwordHash = request.requestedPasswordHash

        try {
            jdbc.update(
                """
                INSERT INTO customers (
                  customer_id, customer_name, customer_phone, customer_address,
                  customer_grade, risk_grade
                )
                VALUES (
                  :customerId, :customerName, :customerPhone, :customerAddress,
                  :customerGrade, :riskGrade
                )
                """.trimIndent(),
                mapOf(
                    "customerId" to customerId,
                    "customerName" to request.requestedCustomerName,
                    "customerPhone" to request.requestedCustomerPhone,
                    "customerAddress" to request.requestedCustomerAddress,
                    "customerGrade" to request.requestedCustomerGrade,
                    "riskGrade" to request.requestedRiskGrade
                )
            )
            jdbc.update(
                """
                INSERT INTO customer_kyc_profiles (
                  customer_id, kyc_status, source_of_funds_code,
                  transaction_purpose_code, simulated_provider_reference
                )
                VALUES (
                  :customerId, 'VERIFIED', :sourceOfFundsCode,
                  :transactionPurposeCode, :simulatedProviderReference
                )
                """.trimIndent(),
                mapOf(
                    "customerId" to customerId,
                    "sourceOfFundsCode" to request.requestedSourceOfFundsCode,
                    "transactionPurposeCode" to request.requestedTransactionPurposeCode,
                    "simulatedProviderReference" to "SIM-KYC-$customerId"
                )
            )
            jdbc.update(
                """
                INSERT INTO customer_auth_identities (
                  auth_subject, customer_id, username, password_hash, status, metadata_json
                )
                VALUES (
                  :authSubject, :customerId, :username, :passwordHash, 'ACTIVE',
                  CAST(:metadataJson AS jsonb)
                )
                """.trimIndent(),
                mapOf(
                    "authSubject" to authSubject,
                    "customerId" to customerId,
                    "username" to request.requestedUsername,
                    "passwordHash" to passwordHash,
                    "metadataJson" to metadataJson(
                        "operation" to "customer-onboarding-auth-binding",
                        "realIdentityProviderCalled" to false,
                        "keycloakAdminApiCalled" to false
                    )
                )
            )
        } catch (error: DuplicateKeyException) {
            throw usernameOrIdempotencyConflict(error)
        }

        val auditEventId = auditEvents.append(
            eventType = "CUSTOMER_ONBOARDING_EXECUTED",
            actorType = "OPERATOR",
            actorId = command.executedBy,
            actorRole = command.executedByRole,
            screenId = "CST-201",
            businessReferenceId = requestId,
            customerId = customerId,
            reason = command.reason,
            payload = mapOf(
                "requestId" to requestId,
                "approvalId" to request.approvalId,
                "generatedCustomerId" to customerId,
                "generatedAuthSubject" to authSubject,
                "username" to request.requestedUsername,
                "kycStatus" to "VERIFIED",
                "realKycProviderCalled" to false,
                "keycloakAdminApiCalled" to false,
                "passwordStoredPlaintext" to false,
                "syntheticOnly" to true
            )
        )
        jdbc.update(
            """
            UPDATE customer_onboarding_requests
            SET status = 'EXECUTED',
                generated_customer_id = :customerId,
                generated_auth_subject = :authSubject,
                executed_by = :executedBy,
                executed_by_role = :executedByRole,
                execute_reason = :reason,
                execute_idempotency_key = :idempotencyKey,
                execute_command_hash = :executeCommandHash,
                executed_at = now(),
                updated_at = now(),
                metadata_json = metadata_json || CAST(:metadataJson AS jsonb)
            WHERE request_id = :requestId
            """.trimIndent(),
            mapOf(
                "requestId" to requestId,
                "customerId" to customerId,
                "authSubject" to authSubject,
                "executedBy" to command.executedBy,
                "executedByRole" to command.executedByRole,
                "reason" to command.reason,
                "idempotencyKey" to command.idempotencyKey,
                "executeCommandHash" to executeHash,
                "metadataJson" to objectMapper.writeValueAsString(
                    mapOf(
                        "executionAuditEventId" to auditEventId,
                        "passwordStoredPlaintext" to false,
                        "syntheticOnly" to true
                    )
                )
            )
        )
        return executeResponse(requestId, replayed = false)
    }

    private fun requestResponse(requestId: String, replayed: Boolean): CustomerOnboardingRequestResponse {
        val item = findRequest(requestId).toDto()
        return CustomerOnboardingRequestResponse(item, approvals.approval(item.approvalId), replayed)
    }

    private fun executeResponse(requestId: String, replayed: Boolean): CustomerOnboardingExecuteResponse {
        val item = findRequest(requestId).toDto()
        val customer = if (item.generatedCustomerId != null && item.generatedAuthSubject != null) {
            CreatedSyntheticCustomerDto(
                customerId = item.generatedCustomerId,
                authSubject = item.generatedAuthSubject,
                username = item.requestedUsername,
                kycStatus = "VERIFIED"
            )
        } else {
            null
        }
        return CustomerOnboardingExecuteResponse(item, approvals.approval(item.approvalId), customer, replayed)
    }

    private fun normalizeAndValidate(command: CustomerOnboardingRequestCommand): NormalizedCustomerOnboardingCommand {
        requireNonBlank(command.requestedBy, "requestedBy")
        val role = command.requestedByRole.ifBlank { "BRANCH_STAFF" }.trim()
        val reason = command.reason.trim()
        if (reason.isBlank()) {
            throw WorkflowErrors.reasonRequired("customer onboarding requires a business reason")
        }
        requireNonBlank(command.idempotencyKey, "idempotencyKey")
        val username = normalizeUsername(command.username)
        if (username.isBlank()) {
            throw WorkflowErrors.validation("username is required")
        }
        validatePassword(username, command.temporaryPassword)
        return NormalizedCustomerOnboardingCommand(
            requestedBy = command.requestedBy.trim(),
            requestedByRole = role,
            reason = reason,
            idempotencyKey = command.idempotencyKey.trim(),
            customerName = requiredTrim(command.customerName, "customerName"),
            customerPhone = requiredTrim(command.customerPhone, "customerPhone"),
            customerAddress = requiredTrim(command.customerAddress, "customerAddress"),
            customerGrade = command.customerGrade.ifBlank { "STANDARD" }.trim().uppercase(),
            riskGrade = command.riskGrade.ifBlank { "LOW" }.trim().uppercase(),
            sourceOfFundsCode = command.sourceOfFundsCode.ifBlank { "SALARY" }.trim().uppercase(),
            transactionPurposeCode = command.transactionPurposeCode.ifBlank { "DAILY_BANKING" }.trim().uppercase(),
            username = username,
            passwordFingerprint = sha256(command.temporaryPassword)
        )
    }

    private fun validateExecuteCommand(command: CustomerOnboardingExecuteCommand) {
        requireNonBlank(command.executedBy, "executedBy")
        requireNonBlank(command.executedByRole, "executedByRole")
        if (command.reason.isBlank()) {
            throw WorkflowErrors.reasonRequired("customer onboarding execution requires a business reason")
        }
        requireNonBlank(command.idempotencyKey, "idempotencyKey")
    }

    private fun validatePassword(username: String, temporaryPassword: String) {
        if (temporaryPassword.length < 8) {
            throw WorkflowErrors.validation("temporaryPassword must be at least 8 characters")
        }
        if (temporaryPassword.equals(username, ignoreCase = true)) {
            throw WorkflowErrors.validation("temporaryPassword must not match username")
        }
    }

    private fun requireMakerRole(role: String) {
        if (role !in REQUEST_ROLES) {
            throw WorkflowErrors.authorizationViolation("actor role is not allowed to request customer onboarding")
        }
    }

    private fun requireCheckerRole(role: String) {
        if (role !in CHECKER_ROLES) {
            throw WorkflowErrors.authorizationViolation("actor role is not allowed to approve or reject customer onboarding")
        }
    }

    private fun requireExecutorRole(role: String) {
        if (role !in EXECUTOR_ROLES) {
            throw WorkflowErrors.authorizationViolation("actor role is not allowed to execute customer onboarding")
        }
    }

    private fun requireUsernameAvailable(username: String, excludingRequestId: String?) {
        val existingIdentity = jdbc.queryForObject(
            """
            SELECT count(*)
            FROM customer_auth_identities
            WHERE lower(username) = lower(:username)
            """.trimIndent(),
            mapOf("username" to username),
            Int::class.java
        ) ?: 0
        if (existingIdentity > 0) {
            throw WorkflowErrors.validation("username is already assigned to a synthetic customer")
        }
        val params = mutableMapOf<String, Any?>("username" to username)
        val excludeClause = if (excludingRequestId != null) {
            params["requestId"] = excludingRequestId
            "AND request_id <> :requestId"
        } else {
            ""
        }
        val existingRequest = jdbc.queryForObject(
            """
            SELECT count(*)
            FROM customer_onboarding_requests
            WHERE lower(requested_username) = lower(:username)
              AND status IN ('PENDING_APPROVAL', 'APPROVED', 'EXECUTED')
              $excludeClause
            """.trimIndent(),
            params,
            Int::class.java
        ) ?: 0
        if (existingRequest > 0) {
            throw WorkflowErrors.validation("username is already reserved by a synthetic customer onboarding request")
        }
    }

    private fun existingRequestByIdempotencyKey(idempotencyKey: String): CustomerOnboardingRequestRecord? =
        jdbc.query(
            requestSql("WHERE idempotency_key = :idempotencyKey"),
            mapOf("idempotencyKey" to idempotencyKey),
            this::mapRequest
        ).firstOrNull()

    private fun existingExecutedRequestByExecuteIdempotencyKey(idempotencyKey: String): CustomerOnboardingRequestRecord? =
        jdbc.query(
            requestSql("WHERE execute_idempotency_key = :idempotencyKey"),
            mapOf("idempotencyKey" to idempotencyKey),
            this::mapRequest
        ).firstOrNull()

    private fun findRequest(requestId: String): CustomerOnboardingRequestRecord =
        jdbc.queryForObject(
            requestSql("WHERE request_id = :requestId"),
            mapOf("requestId" to requestId),
            this::mapRequest
        ) ?: throw WorkflowErrors.notFound("customer onboarding request not found: $requestId")

    private fun findRequestForUpdate(requestId: String): CustomerOnboardingRequestRecord =
        jdbc.queryForObject(
            requestSql("WHERE request_id = :requestId FOR UPDATE"),
            mapOf("requestId" to requestId),
            this::mapRequest
        ) ?: throw WorkflowErrors.notFound("customer onboarding request not found: $requestId")

    private fun requestSql(suffix: String): String =
        """
        SELECT request_id, idempotency_key, command_hash, status,
               requested_by, requested_by_role, reason, approval_id,
               requested_customer_name, requested_customer_phone, requested_customer_address,
               requested_customer_grade, requested_risk_grade,
               requested_source_of_funds_code, requested_transaction_purpose_code,
               requested_username, requested_password_hash, generated_customer_id, generated_auth_subject,
               approved_by, approved_at, rejected_by, rejected_at, reject_reason,
               executed_by, executed_by_role, execute_idempotency_key, execute_command_hash,
               executed_at, synthetic_only, created_at, updated_at
        FROM customer_onboarding_requests
        $suffix
        """.trimIndent()

    private fun mapRequest(rs: ResultSet, rowNum: Int): CustomerOnboardingRequestRecord =
        CustomerOnboardingRequestRecord(
            requestId = rs.getString("request_id"),
            idempotencyKey = rs.getString("idempotency_key"),
            commandHash = rs.getString("command_hash"),
            status = rs.getString("status"),
            requestedBy = rs.getString("requested_by"),
            requestedByRole = rs.getString("requested_by_role"),
            reason = rs.getString("reason"),
            approvalId = rs.getString("approval_id"),
            requestedCustomerName = rs.getString("requested_customer_name"),
            requestedCustomerPhone = rs.getString("requested_customer_phone"),
            requestedCustomerAddress = rs.getString("requested_customer_address"),
            requestedCustomerGrade = rs.getString("requested_customer_grade"),
            requestedRiskGrade = rs.getString("requested_risk_grade"),
            requestedSourceOfFundsCode = rs.getString("requested_source_of_funds_code"),
            requestedTransactionPurposeCode = rs.getString("requested_transaction_purpose_code"),
            requestedUsername = rs.getString("requested_username"),
            requestedPasswordHash = rs.getString("requested_password_hash"),
            generatedCustomerId = rs.getString("generated_customer_id"),
            generatedAuthSubject = rs.getString("generated_auth_subject"),
            approvedBy = rs.getString("approved_by"),
            approvedAt = rs.getObject("approved_at", OffsetDateTime::class.java),
            rejectedBy = rs.getString("rejected_by"),
            rejectedAt = rs.getObject("rejected_at", OffsetDateTime::class.java),
            rejectReason = rs.getString("reject_reason"),
            executedBy = rs.getString("executed_by"),
            executedByRole = rs.getString("executed_by_role"),
            executeIdempotencyKey = rs.getString("execute_idempotency_key"),
            executeCommandHash = rs.getString("execute_command_hash"),
            executedAt = rs.getObject("executed_at", OffsetDateTime::class.java),
            syntheticOnly = rs.getBoolean("synthetic_only"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java)
        )

    private fun requestCommandHash(command: NormalizedCustomerOnboardingCommand): String =
        commandHash(
            mapOf(
                "requestedBy" to command.requestedBy,
                "requestedByRole" to command.requestedByRole,
                "reason" to command.reason,
                "customerName" to command.customerName,
                "customerPhone" to command.customerPhone,
                "customerAddress" to command.customerAddress,
                "customerGrade" to command.customerGrade,
                "riskGrade" to command.riskGrade,
                "sourceOfFundsCode" to command.sourceOfFundsCode,
                "transactionPurposeCode" to command.transactionPurposeCode,
                "username" to command.username,
                "passwordFingerprint" to command.passwordFingerprint
            )
        )

    private fun executeCommandHash(requestId: String, command: CustomerOnboardingExecuteCommand): String =
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
                message = "idempotency key was reused with a different customer onboarding command",
                causeText = "The same idempotency key already has a different command hash.",
                fix = "Retry with the original request body or generate a new idempotency key for a different command."
            )
        }
    }

    private fun usernameOrIdempotencyConflict(error: DuplicateKeyException): BankingLabDomainException {
        val message = error.message.orEmpty()
        if (message.contains("idempotency", ignoreCase = true)) {
            return BankingLabDomainException(
                code = "IDEMPOTENCY_KEY_CONFLICT",
                status = HttpStatus.CONFLICT,
                domain = "idempotency",
                invariant = "EXTERNALLY_RETRIED_COMMANDS_ARE_IDEMPOTENT",
                message = "idempotency key already exists for another customer onboarding command",
                causeText = "The unique idempotency key has already been persisted.",
                fix = "Retry with the original payload or use a new idempotency key for a distinct command."
            )
        }
        return WorkflowErrors.validation("username is already reserved or assigned to a synthetic customer")
    }

    private fun nextCustomerSequence(): Long =
        jdbc.queryForObject(
            "SELECT nextval('synthetic_customer_onboarding_customer_seq')",
            emptyMap<String, Any?>(),
            Long::class.java
        ) ?: error("synthetic customer sequence returned null")

    private fun nextId(prefix: String): String =
        "$prefix-${UUID.randomUUID().toString().uppercase()}"

    private fun normalizeUsername(username: String): String =
        username.trim().lowercase()

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

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private data class NormalizedCustomerOnboardingCommand(
        val requestedBy: String,
        val requestedByRole: String,
        val reason: String,
        val idempotencyKey: String,
        val customerName: String,
        val customerPhone: String,
        val customerAddress: String,
        val customerGrade: String,
        val riskGrade: String,
        val sourceOfFundsCode: String,
        val transactionPurposeCode: String,
        val username: String,
        val passwordFingerprint: String
    )

    private data class CustomerOnboardingRequestRecord(
        val requestId: String,
        val idempotencyKey: String,
        val commandHash: String,
        val status: String,
        val requestedBy: String,
        val requestedByRole: String,
        val reason: String,
        val approvalId: String,
        val requestedCustomerName: String,
        val requestedCustomerPhone: String,
        val requestedCustomerAddress: String,
        val requestedCustomerGrade: String,
        val requestedRiskGrade: String,
        val requestedSourceOfFundsCode: String,
        val requestedTransactionPurposeCode: String,
        val requestedUsername: String,
        val requestedPasswordHash: String,
        val generatedCustomerId: String?,
        val generatedAuthSubject: String?,
        val approvedBy: String?,
        val approvedAt: OffsetDateTime?,
        val rejectedBy: String?,
        val rejectedAt: OffsetDateTime?,
        val rejectReason: String?,
        val executedBy: String?,
        val executedByRole: String?,
        val executeIdempotencyKey: String?,
        val executeCommandHash: String?,
        val executedAt: OffsetDateTime?,
        val syntheticOnly: Boolean,
        val createdAt: OffsetDateTime,
        val updatedAt: OffsetDateTime
    ) {
        fun toDto(): CustomerOnboardingRequestDto =
            CustomerOnboardingRequestDto(
                requestId = requestId,
                idempotencyKey = idempotencyKey,
                status = status,
                requestedBy = requestedBy,
                requestedByRole = requestedByRole,
                reason = reason,
                approvalId = approvalId,
                requestedCustomerName = requestedCustomerName,
                requestedCustomerPhone = requestedCustomerPhone,
                requestedCustomerAddress = requestedCustomerAddress,
                requestedCustomerGrade = requestedCustomerGrade,
                requestedRiskGrade = requestedRiskGrade,
                requestedSourceOfFundsCode = requestedSourceOfFundsCode,
                requestedTransactionPurposeCode = requestedTransactionPurposeCode,
                requestedUsername = requestedUsername,
                generatedCustomerId = generatedCustomerId,
                generatedAuthSubject = generatedAuthSubject,
                approvedBy = approvedBy,
                approvedAt = approvedAt,
                rejectedBy = rejectedBy,
                rejectedAt = rejectedAt,
                rejectReason = rejectReason,
                executedBy = executedBy,
                executedByRole = executedByRole,
                executedAt = executedAt,
                syntheticOnly = syntheticOnly,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
    }

    companion object {
        private const val PASSWORD_POLICY = "SYNTHETIC_MIN_8_NOT_USERNAME"
        private val REQUEST_ROLES = setOf("BRANCH_STAFF", "BRANCH_MANAGER", "COMPLIANCE_MANAGER")
        private val CHECKER_ROLES = setOf("BRANCH_MANAGER", "COMPLIANCE_MANAGER")
        private val EXECUTOR_ROLES = setOf("BRANCH_STAFF", "BRANCH_MANAGER", "OPS_MANAGER")
    }
}
