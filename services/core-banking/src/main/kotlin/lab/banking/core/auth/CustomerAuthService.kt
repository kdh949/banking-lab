package lab.banking.core.auth

import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.sql.ResultSet
import lab.banking.core.audit.AuditEventAppender
import lab.banking.core.common.BankingLabDomainException
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class CustomerAuthService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val passwordEncoder: PasswordEncoder,
    private val tokenIssuer: SyntheticCustomerAuthTokenIssuer,
    private val auditEvents: AuditEventAppender
) {
    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun signup(command: CustomerSignupCommand): CustomerAuthResponse {
        tokenIssuer.requireIssuerEnabled()
        val normalized = normalizeSignup(command)
        val commandHash = signupCommandHash(normalized)
        identityBySignupIdempotencyKey(normalized.idempotencyKey)?.let { existing ->
            requireSameSignupCommandHash(existing.signupCommandHash, commandHash)
            return authResponse(existing, replayed = true, eventType = "CUSTOMER_SIGNUP_REPLAYED")
        }
        requireUsernameAvailable(normalized.username)

        val customerId = "SYN-CUS-SIGNUP-${nextSignupSequence().toString().padStart(6, '0')}"
        val authSubject = "SYN-AUTH-$customerId"
        val passwordHash = passwordEncoder.encode(command.password)
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
                    "customerName" to normalized.syntheticCustomerName,
                    "customerPhone" to normalized.syntheticPhone,
                    "customerAddress" to normalized.syntheticAddress,
                    "customerGrade" to normalized.customerGrade,
                    "riskGrade" to normalized.riskGrade
                )
            )
            jdbc.update(
                """
                INSERT INTO customer_kyc_profiles (
                  customer_id, kyc_status, source_of_funds_code,
                  transaction_purpose_code, simulated_provider_reference
                )
                VALUES (
                  :customerId, 'PENDING', :sourceOfFundsCode,
                  :transactionPurposeCode, :simulatedProviderReference
                )
                """.trimIndent(),
                mapOf(
                    "customerId" to customerId,
                    "sourceOfFundsCode" to normalized.sourceOfFundsCode,
                    "transactionPurposeCode" to normalized.transactionPurposeCode,
                    "simulatedProviderReference" to "SIM-SELF-SIGNUP-$customerId"
                )
            )
            jdbc.update(
                """
                INSERT INTO customer_auth_identities (
                  auth_subject, customer_id, username, password_hash, status,
                  signup_idempotency_key, signup_command_hash, metadata_json
                )
                VALUES (
                  :authSubject, :customerId, :username, :passwordHash, 'ACTIVE',
                  :idempotencyKey, :commandHash, CAST(:metadataJson AS jsonb)
                )
                """.trimIndent(),
                mapOf(
                    "authSubject" to authSubject,
                    "customerId" to customerId,
                    "username" to normalized.username,
                    "passwordHash" to passwordHash,
                    "idempotencyKey" to normalized.idempotencyKey,
                    "commandHash" to commandHash,
                    "metadataJson" to metadataJson(
                        "operation" to "customer-self-signup-auth-binding",
                        "realIdentityProviderCalled" to false,
                        "keycloakAdminApiCalled" to false,
                        "realKycProviderCalled" to false,
                        "passwordStoredPlaintext" to false
                    )
                )
            )
        } catch (error: DuplicateKeyException) {
            throw signupConflict(error)
        }

        return authResponse(
            identityByCustomerId(customerId),
            replayed = false,
            eventType = "CUSTOMER_SIGNUP_SUCCEEDED"
        )
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, noRollbackFor = [BankingLabDomainException::class])
    fun login(command: CustomerLoginCommand): CustomerAuthResponse {
        tokenIssuer.requireIssuerEnabled()
        val username = normalizeUsername(command.username)
        if (username.isBlank() || command.password.isBlank()) {
            throw invalidCredentials()
        }
        val identity = identityByUsernameForUpdate(username) ?: throw invalidCredentials(username)
        if (identity.status != "ACTIVE") {
            throw BankingLabDomainException(
                code = "CUSTOMER_AUTH_IDENTITY_NOT_ACTIVE",
                status = HttpStatus.FORBIDDEN,
                domain = "auth",
                policy = "ACTIVE_SYNTHETIC_CUSTOMER_AUTH_REQUIRED",
                message = "synthetic customer auth identity is not active",
                causeText = "The requested synthetic customer username is locked or disabled.",
                fix = "Use an active synthetic customer or have staff resolve the modeled auth status."
            )
        }
        if (!passwordEncoder.matches(command.password, identity.passwordHash)) {
            recordFailedLogin(identity, username)
            throw invalidCredentials()
        }
        jdbc.update(
            """
            UPDATE customer_auth_identities
            SET failed_login_count = 0,
                last_login_at = now(),
                updated_at = now(),
                metadata_json = metadata_json || CAST(:metadataJson AS jsonb)
            WHERE auth_subject = :authSubject
            """.trimIndent(),
            mapOf(
                "authSubject" to identity.authSubject,
                "metadataJson" to metadataJson(
                    "lastLoginOperation" to "customer-synthetic-login",
                    "passwordStoredPlaintext" to false
                )
            )
        )
        return authResponse(identity.copy(failedLoginCount = 0), replayed = false, eventType = "CUSTOMER_LOGIN_SUCCEEDED")
    }

    private fun authResponse(
        identity: CustomerAuthIdentityRecord,
        replayed: Boolean,
        eventType: String
    ): CustomerAuthResponse {
        val onboarding = ensureOnboardingChecks(identity)
        ensureSyntheticTrustedDevice(identity)
        val issued = tokenIssuer.issue(
            authSubject = identity.authSubject,
            username = identity.username,
            customerId = identity.customerId
        )
        appendAuthAudit(eventType, identity, issued.session.sessionId, replayed)
        return CustomerAuthResponse(
            customer = CustomerAuthCustomerDto(
                customerId = identity.customerId,
                authSubject = identity.authSubject,
                username = identity.username,
                kycStatus = identity.kycStatus,
                onboardingStatus = onboarding.onboardingStatus,
                duplicateCheckStatus = onboarding.duplicateCheckStatus,
                nextRequiredAction = onboarding.nextRequiredAction
            ),
            session = issued.session,
            bearerToken = issued.bearerToken,
            expiresAt = issued.expiresAt,
            replayed = replayed,
            onboardingStatus = onboarding.onboardingStatus,
            duplicateCheckStatus = onboarding.duplicateCheckStatus,
            nextRequiredAction = onboarding.nextRequiredAction
        )
    }

    private fun ensureOnboardingChecks(identity: CustomerAuthIdentityRecord): OnboardingSummary {
        val profile = customerProfileForOnboardingChecks(identity.customerId)
        val duplicateCount = jdbc.queryForObject(
            """
            SELECT count(*)
            FROM customers
            WHERE customer_id <> :customerId
              AND lower(customer_name) = lower(:customerName)
              AND lower(COALESCE(customer_phone, '')) = lower(:customerPhone)
              AND lower(COALESCE(customer_address, '')) = lower(:customerAddress)
            """.trimIndent(),
            mapOf(
                "customerId" to identity.customerId,
                "customerName" to profile.customerName,
                "customerPhone" to profile.customerPhone.orEmpty(),
                "customerAddress" to profile.customerAddress.orEmpty()
            ),
            Int::class.java
        ) ?: 0
        val profileFingerprint = profileFingerprint(
            profile.customerName,
            profile.customerPhone,
            profile.customerAddress
        )
        val checkInputs = listOf(
            OnboardingCheckInput(
                checkType = "DUPLICATE_IDENTITY",
                status = if (duplicateCount > 0) "REVIEW_REQUIRED" else "PASSED",
                riskLevel = if (duplicateCount > 0) "MEDIUM" else "LOW",
                evidence = mapOf(
                    "profileFingerprint" to profileFingerprint,
                    "duplicateCandidateCount" to duplicateCount,
                    "rawPiiStored" to false,
                    "syntheticOnly" to true
                )
            ),
            OnboardingCheckInput(
                checkType = "KYC_SIMULATION",
                status = when (identity.kycStatus) {
                    "VERIFIED" -> "PASSED"
                    "REVIEW_REQUIRED" -> "REVIEW_REQUIRED"
                    "REJECTED" -> "FAILED"
                    else -> "PENDING"
                },
                riskLevel = if (identity.kycStatus == "REJECTED") "HIGH" else "LOW",
                evidence = mapOf(
                    "kycStatus" to identity.kycStatus,
                    "realKycProviderCalled" to false,
                    "syntheticOnly" to true
                )
            ),
            OnboardingCheckInput(
                checkType = "TERMS_ACCEPTANCE",
                status = "PASSED",
                riskLevel = "LOW",
                evidence = mapOf(
                    "acceptedThrough" to "CUSTOMER_SELF_SIGNUP",
                    "legalDocumentDeliveryPerformed" to false,
                    "syntheticOnly" to true
                )
            ),
            OnboardingCheckInput(
                checkType = "CONTACT_REACHABILITY",
                status = "PASSED",
                riskLevel = "LOW",
                evidence = mapOf(
                    "profileFingerprint" to profileFingerprint,
                    "realSmsOrEmailSent" to false,
                    "syntheticOnly" to true
                )
            )
        )
        checkInputs.forEach { input ->
            jdbc.update(
                """
                INSERT INTO customer_onboarding_checks (
                  check_id, customer_id, check_type, status, risk_level, evidence_json, synthetic_only
                )
                VALUES (
                  :checkId, :customerId, :checkType, :status, :riskLevel, CAST(:evidenceJson AS jsonb), true
                )
                ON CONFLICT (customer_id, check_type) DO NOTHING
                """.trimIndent(),
                mapOf(
                    "checkId" to "CHK-${identity.customerId}-${input.checkType}",
                    "customerId" to identity.customerId,
                    "checkType" to input.checkType,
                    "status" to input.status,
                    "riskLevel" to input.riskLevel,
                    "evidenceJson" to objectMapper.writeValueAsString(input.evidence)
                )
            )
        }
        val statuses = jdbc.query(
            """
            SELECT check_type, status
            FROM customer_onboarding_checks
            WHERE customer_id = :customerId
            """.trimIndent(),
            mapOf("customerId" to identity.customerId)
        ) { rs, _ -> rs.getString("check_type") to rs.getString("status") }.toMap()
        val duplicateStatus = statuses["DUPLICATE_IDENTITY"] ?: "UNKNOWN"
        return OnboardingSummary(
            onboardingStatus = onboardingStatus(identity.kycStatus, statuses),
            duplicateCheckStatus = duplicateStatus,
            nextRequiredAction = nextRequiredAction(identity.kycStatus, statuses)
        )
    }

    private fun onboardingStatus(kycStatus: String, checks: Map<String, String>): String {
        if (checks.values.any { it == "FAILED" }) {
            return "BLOCKED"
        }
        if (checks.values.any { it == "REVIEW_REQUIRED" }) {
            return "REVIEW_REQUIRED"
        }
        if (kycStatus != "VERIFIED" || checks.values.any { it == "PENDING" }) {
            return "PENDING_KYC"
        }
        return "COMPLETE"
    }

    private fun nextRequiredAction(kycStatus: String, checks: Map<String, String>): String {
        if (checks["DUPLICATE_IDENTITY"] == "REVIEW_REQUIRED") {
            return "WAIT_FOR_STAFF_DUPLICATE_REVIEW"
        }
        if (checks.values.any { it == "FAILED" }) {
            return "CONTACT_SYNTHETIC_SUPPORT"
        }
        if (kycStatus != "VERIFIED" || checks["KYC_SIMULATION"] == "PENDING") {
            return "WAIT_FOR_SYNTHETIC_KYC_REVIEW"
        }
        return "NONE"
    }

    private fun customerProfileForOnboardingChecks(customerId: String): CustomerProfileForChecks =
        jdbc.queryForObject(
            """
            SELECT customer_name, customer_phone, customer_address
            FROM customers
            WHERE customer_id = :customerId
            """.trimIndent(),
            mapOf("customerId" to customerId)
        ) { rs, _ ->
            CustomerProfileForChecks(
                customerName = rs.getString("customer_name"),
                customerPhone = rs.getString("customer_phone"),
                customerAddress = rs.getString("customer_address")
            )
        } ?: throw WorkflowErrors.notFound("synthetic customer not found: $customerId")

    private fun ensureSyntheticTrustedDevice(identity: CustomerAuthIdentityRecord) {
        jdbc.update(
            """
            INSERT INTO trusted_devices (
              trusted_device_id, actor_type, actor_id, customer_id,
              device_fingerprint, status, metadata_json
            )
            VALUES (
              :trustedDeviceId, 'CUSTOMER', :customerId, :customerId,
              :deviceFingerprint, 'ACTIVE', CAST(:metadataJson AS jsonb)
            )
            ON CONFLICT (actor_type, actor_id, device_fingerprint)
            DO UPDATE SET
              customer_id = EXCLUDED.customer_id,
              status = 'ACTIVE',
              expires_at = NULL,
              metadata_json = trusted_devices.metadata_json || EXCLUDED.metadata_json
            """.trimIndent(),
            mapOf(
                "trustedDeviceId" to "TD-CUSTOMER-${identity.customerId}-SYNTHETIC",
                "customerId" to identity.customerId,
                "deviceFingerprint" to SyntheticCustomerAuthTokenIssuer.syntheticDeviceFingerprint(identity.customerId),
                "metadataJson" to metadataJson(
                    "operation" to "customer-synthetic-trusted-device-binding",
                    "realDeviceIntelligenceCalled" to false
                )
            )
        )
    }

    private fun normalizeSignup(command: CustomerSignupCommand): NormalizedSignupCommand {
        val idempotencyKey = command.idempotencyKey.trim()
        if (idempotencyKey.isBlank()) {
            throw WorkflowErrors.validation("idempotencyKey is required")
        }
        val username = normalizeUsername(command.username)
        if (username.isBlank()) {
            throw WorkflowErrors.validation("username is required")
        }
        validatePassword(username, command.password)
        return NormalizedSignupCommand(
            idempotencyKey = idempotencyKey,
            username = username,
            syntheticCustomerName = requiredSyntheticTrim(command.syntheticCustomerName, "syntheticCustomerName"),
            syntheticPhone = command.syntheticPhone.trim().ifBlank { "010-0000-0000" },
            syntheticAddress = command.syntheticAddress.trim().ifBlank { "Synthetic self-service address" },
            customerGrade = command.customerGrade.ifBlank { "STANDARD" }.trim().uppercase(),
            riskGrade = command.riskGrade.ifBlank { "LOW" }.trim().uppercase(),
            sourceOfFundsCode = command.sourceOfFundsCode.ifBlank { "SELF_SERVICE_SYNTHETIC" }.trim().uppercase(),
            transactionPurposeCode = command.transactionPurposeCode.ifBlank { "DAILY_BANKING" }.trim().uppercase(),
            passwordFingerprint = sha256(command.password)
        )
    }

    private fun validatePassword(username: String, password: String) {
        if (password.length < 8) {
            throw WorkflowErrors.validation("password must be at least 8 characters")
        }
        if (password.equals(username, ignoreCase = true)) {
            throw WorkflowErrors.validation("password must not match username")
        }
    }

    private fun requireUsernameAvailable(username: String) {
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
        val existingOnboardingRequest = jdbc.queryForObject(
            """
            SELECT count(*)
            FROM customer_onboarding_requests
            WHERE lower(requested_username) = lower(:username)
              AND status IN ('PENDING_APPROVAL', 'APPROVED', 'EXECUTED')
            """.trimIndent(),
            mapOf("username" to username),
            Int::class.java
        ) ?: 0
        if (existingOnboardingRequest > 0) {
            throw WorkflowErrors.validation("username is already reserved by a synthetic customer onboarding request")
        }
    }

    private fun identityBySignupIdempotencyKey(idempotencyKey: String): CustomerAuthIdentityRecord? =
        jdbc.query(
            identitySql("WHERE cai.signup_idempotency_key = :idempotencyKey"),
            mapOf("idempotencyKey" to idempotencyKey),
            this::mapIdentity
        ).firstOrNull()

    private fun identityByUsernameForUpdate(username: String): CustomerAuthIdentityRecord? =
        jdbc.query(
            identitySql("WHERE lower(cai.username) = lower(:username) FOR UPDATE OF cai"),
            mapOf("username" to username),
            this::mapIdentity
        ).firstOrNull()

    private fun identityByCustomerId(customerId: String): CustomerAuthIdentityRecord =
        jdbc.queryForObject(
            identitySql("WHERE cai.customer_id = :customerId"),
            mapOf("customerId" to customerId),
            this::mapIdentity
        ) ?: throw WorkflowErrors.notFound("synthetic customer auth identity not found: $customerId")

    private fun identitySql(suffix: String): String =
        """
        SELECT cai.auth_subject, cai.customer_id, cai.username, cai.password_hash, cai.status,
               cai.failed_login_count, cai.signup_idempotency_key, cai.signup_command_hash,
               COALESCE(ckp.kyc_status, 'PENDING') AS kyc_status
        FROM customer_auth_identities cai
        JOIN customers c ON c.customer_id = cai.customer_id
        LEFT JOIN customer_kyc_profiles ckp ON ckp.customer_id = cai.customer_id
        $suffix
        """.trimIndent()

    private fun mapIdentity(rs: ResultSet, rowNum: Int): CustomerAuthIdentityRecord =
        CustomerAuthIdentityRecord(
            authSubject = rs.getString("auth_subject"),
            customerId = rs.getString("customer_id"),
            username = rs.getString("username"),
            passwordHash = rs.getString("password_hash"),
            status = rs.getString("status"),
            failedLoginCount = rs.getInt("failed_login_count"),
            signupIdempotencyKey = rs.getString("signup_idempotency_key"),
            signupCommandHash = rs.getString("signup_command_hash"),
            kycStatus = rs.getString("kyc_status")
        )

    private fun recordFailedLogin(identity: CustomerAuthIdentityRecord, username: String) {
        jdbc.update(
            """
            UPDATE customer_auth_identities
            SET failed_login_count = failed_login_count + 1,
                updated_at = now(),
                metadata_json = metadata_json || CAST(:metadataJson AS jsonb)
            WHERE auth_subject = :authSubject
            """.trimIndent(),
            mapOf(
                "authSubject" to identity.authSubject,
                "metadataJson" to metadataJson(
                    "lastFailedLoginOperation" to "customer-synthetic-login",
                    "passwordStoredPlaintext" to false
                )
            )
        )
        auditEvents.append(
            eventType = "CUSTOMER_LOGIN_FAILED",
            actorType = "CUSTOMER",
            actorId = username,
            actorRole = "CUSTOMER",
            screenId = "CWB-002",
            businessReferenceId = identity.authSubject,
            customerId = identity.customerId,
            reason = null,
            payload = mapOf(
                "username" to username,
                "passwordProvided" to true,
                "passwordStoredPlaintext" to false,
                "syntheticOnly" to true
            )
        )
    }

    private fun appendAuthAudit(
        eventType: String,
        identity: CustomerAuthIdentityRecord,
        sessionId: String,
        replayed: Boolean
    ) {
        auditEvents.append(
            eventType = eventType,
            actorType = "CUSTOMER",
            actorId = identity.authSubject,
            actorRole = "CUSTOMER",
            screenId = if (eventType.startsWith("CUSTOMER_SIGNUP")) "CWB-001" else "CWB-002",
            businessReferenceId = identity.authSubject,
            customerId = identity.customerId,
            reason = null,
            payload = mapOf(
                "username" to identity.username,
                "sessionId" to sessionId,
                "replayed" to replayed,
                "passwordProvided" to true,
                "passwordStoredPlaintext" to false,
                "realIdentityProviderCalled" to false,
                "keycloakAdminApiCalled" to false,
                "syntheticOnly" to true
            )
        )
    }

    private fun signupCommandHash(command: NormalizedSignupCommand): String =
        sha256(
            objectMapper.writeValueAsString(
                mapOf(
                    "username" to command.username,
                    "syntheticCustomerName" to command.syntheticCustomerName,
                    "syntheticPhone" to command.syntheticPhone,
                    "syntheticAddress" to command.syntheticAddress,
                    "customerGrade" to command.customerGrade,
                    "riskGrade" to command.riskGrade,
                    "sourceOfFundsCode" to command.sourceOfFundsCode,
                    "transactionPurposeCode" to command.transactionPurposeCode,
                    "passwordFingerprint" to command.passwordFingerprint
                ).toSortedMap()
            )
        )

    private fun requireSameSignupCommandHash(existing: String?, requested: String) {
        if (existing != requested) {
            throw BankingLabDomainException(
                code = "IDEMPOTENCY_KEY_CONFLICT",
                status = HttpStatus.CONFLICT,
                domain = "idempotency",
                invariant = "EXTERNALLY_RETRIED_COMMANDS_ARE_IDEMPOTENT",
                message = "idempotency key was reused with a different customer signup command",
                causeText = "The same signup idempotency key already has a different command hash.",
                fix = "Retry with the original signup payload or generate a new idempotency key for a different synthetic signup."
            )
        }
    }

    private fun signupConflict(error: DuplicateKeyException): BankingLabDomainException {
        val message = error.message.orEmpty()
        if (message.contains("signup_idempotency_key", ignoreCase = true)) {
            return BankingLabDomainException(
                code = "IDEMPOTENCY_KEY_CONFLICT",
                status = HttpStatus.CONFLICT,
                domain = "idempotency",
                invariant = "EXTERNALLY_RETRIED_COMMANDS_ARE_IDEMPOTENT",
                message = "idempotency key already exists for another customer signup command",
                causeText = "The unique signup idempotency key has already been persisted.",
                fix = "Retry with the original payload or use a new idempotency key for a distinct synthetic signup."
            )
        }
        return WorkflowErrors.validation("username is already reserved or assigned to a synthetic customer")
    }

    private fun invalidCredentials(username: String? = null): BankingLabDomainException {
        if (!username.isNullOrBlank()) {
            auditEvents.append(
                eventType = "CUSTOMER_LOGIN_FAILED",
                actorType = "CUSTOMER",
                actorId = username,
                actorRole = "CUSTOMER",
                screenId = "CWB-002",
                businessReferenceId = username,
                reason = null,
                payload = mapOf(
                    "username" to username,
                    "passwordProvided" to true,
                    "passwordStoredPlaintext" to false,
                    "syntheticOnly" to true
                )
            )
        }
        return BankingLabDomainException(
            code = "CUSTOMER_AUTHENTICATION_FAILED",
            status = HttpStatus.UNAUTHORIZED,
            domain = "auth",
            policy = "VALID_SYNTHETIC_CUSTOMER_CREDENTIAL_REQUIRED",
            message = "synthetic customer username or password is invalid",
            causeText = "The supplied synthetic customer credential did not match an active lab auth identity.",
            fix = "Retry with the username and password created by synthetic signup or staff customer onboarding."
        )
    }

    private fun metadataJson(vararg entries: Pair<String, Any?>): String =
        objectMapper.writeValueAsString(mapOf("syntheticOnly" to true, *entries))

    private fun profileFingerprint(name: String, phone: String?, address: String?): String =
        sha256(
            listOf(
                name.trim().lowercase(),
                phone.orEmpty().filter { it.isDigit() },
                address.orEmpty().trim().lowercase().replace(Regex("\\s+"), " ")
            ).joinToString("|")
        )

    private fun nextSignupSequence(): Long =
        jdbc.queryForObject(
            "SELECT nextval('synthetic_customer_signup_customer_seq')",
            emptyMap<String, Any?>(),
            Long::class.java
        ) ?: error("synthetic customer signup sequence returned null")

    private fun normalizeUsername(username: String): String =
        username.trim().lowercase()

    private fun requiredSyntheticTrim(value: String, field: String): String =
        value.trim().also {
            if (it.isBlank()) {
                throw WorkflowErrors.validation("$field is required")
            }
        }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private data class NormalizedSignupCommand(
        val idempotencyKey: String,
        val username: String,
        val syntheticCustomerName: String,
        val syntheticPhone: String,
        val syntheticAddress: String,
        val customerGrade: String,
        val riskGrade: String,
        val sourceOfFundsCode: String,
        val transactionPurposeCode: String,
        val passwordFingerprint: String
    )

    private data class CustomerAuthIdentityRecord(
        val authSubject: String,
        val customerId: String,
        val username: String,
        val passwordHash: String,
        val status: String,
        val failedLoginCount: Int,
        val signupIdempotencyKey: String?,
        val signupCommandHash: String?,
        val kycStatus: String
    )

    private data class CustomerProfileForChecks(
        val customerName: String,
        val customerPhone: String?,
        val customerAddress: String?
    )

    private data class OnboardingCheckInput(
        val checkType: String,
        val status: String,
        val riskLevel: String,
        val evidence: Map<String, Any?>
    )

    private data class OnboardingSummary(
        val onboardingStatus: String,
        val duplicateCheckStatus: String,
        val nextRequiredAction: String
    )
}
