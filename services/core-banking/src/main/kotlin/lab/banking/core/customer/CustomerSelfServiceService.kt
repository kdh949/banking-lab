package lab.banking.core.customer

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import lab.banking.core.audit.AuditEventAppender
import lab.banking.core.common.BankingLabDomainException
import lab.banking.core.security.BankingLabAuthContext
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class CustomerSelfServiceService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val auditEvents: AuditEventAppender
) {
    @Transactional
    fun profile(): CustomerProfileDto {
        val customerId = currentCustomerId()
        val profile = loadProfile(customerId)
        appendCustomerAudit(
            eventType = "CUSTOMER_PROFILE_VIEW",
            screenId = "CWB-003",
            businessReferenceId = customerId,
            customerId = customerId,
            accountId = null,
            payload = mapOf(
                "onboardingStatus" to profile.onboardingStatus,
                "duplicateCheckStatus" to profile.duplicateCheckStatus,
                "checkCount" to profile.onboardingChecks.size,
                "maskingPolicy" to "CUSTOMER_SELF",
                "syntheticOnly" to true
            )
        )
        return profile
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun requestAccountOpening(
        command: CustomerSelfServiceAccountOpeningCommand
    ): CustomerSelfServiceAccountOpeningRequestResponse {
        val customerId = currentCustomerId()
        requireActiveIdentity(customerId)
        val normalized = normalizeAccountOpening(command)
        val commandHash = accountOpeningCommandHash(customerId, normalized)
        existingAccountOpeningRequestByIdempotencyKey(normalized.idempotencyKey)?.let { existing ->
            requireSameCommandHash(existing.commandHash, commandHash)
            return CustomerSelfServiceAccountOpeningRequestResponse(
                item = accountOpeningRequest(existing.requestId),
                replayed = true
            )
        }
        requireNoPendingAccountOpening(customerId, normalized.productCode, normalized.currency)

        val requestId = "CSAO-${UUID.randomUUID().toString().uppercase()}"
        try {
            jdbc.update(
                """
                INSERT INTO customer_self_service_account_opening_requests (
                  request_id, idempotency_key, command_hash, customer_id, status,
                  requested_product_code, requested_account_alias, requested_currency,
                  requested_initial_deposit_amount_minor, terms_accepted, metadata_json
                )
                VALUES (
                  :requestId, :idempotencyKey, :commandHash, :customerId, 'CUSTOMER_SUBMITTED',
                  :productCode, :accountAlias, :currency,
                  :initialDepositAmountMinor, true, CAST(:metadataJson AS jsonb)
                )
                """.trimIndent(),
                mapOf(
                    "requestId" to requestId,
                    "idempotencyKey" to normalized.idempotencyKey,
                    "commandHash" to commandHash,
                    "customerId" to customerId,
                    "productCode" to normalized.productCode,
                    "accountAlias" to normalized.accountAlias,
                    "currency" to normalized.currency,
                    "initialDepositAmountMinor" to normalized.syntheticInitialDepositAmountMinor,
                    "metadataJson" to metadataJson(
                        "operation" to "customer-self-service-account-opening-intake",
                        "customerRouteCreatedAccount" to false,
                        "ledgerCommandServiceCalled" to false,
                        "staffMakerCheckerRequired" to true
                    )
                )
            )
        } catch (error: DuplicateKeyException) {
            throw idempotencyConflict("customer account-opening idempotency key already exists")
        }
        val item = accountOpeningRequest(requestId)
        appendCustomerAudit(
            eventType = "CUSTOMER_ACCOUNT_OPENING_REQUESTED",
            screenId = "CWB-004",
            businessReferenceId = requestId,
            customerId = customerId,
            accountId = null,
            payload = mapOf(
                "requestId" to requestId,
                "status" to item.status,
                "requestedProductCode" to item.requestedProductCode,
                "requestedCurrency" to item.requestedCurrency,
                "accountCreated" to false,
                "ledgerTransactionCreated" to false,
                "staffMakerCheckerRequired" to true,
                "syntheticOnly" to true
            )
        )
        return CustomerSelfServiceAccountOpeningRequestResponse(item = item, replayed = false)
    }

    @Transactional
    fun accountOpeningRequests(): CustomerSelfServiceAccountOpeningRequestListResponse {
        val customerId = currentCustomerId()
        val items = jdbc.query(
            """
            SELECT *
            FROM customer_self_service_account_opening_requests
            WHERE customer_id = :customerId
            ORDER BY created_at DESC, request_id DESC
            LIMIT 50
            """.trimIndent(),
            mapOf("customerId" to customerId),
            this::mapAccountOpeningRequest
        )
        appendCustomerAudit(
            eventType = "CUSTOMER_ACCOUNT_OPENING_STATUS_VIEW",
            screenId = "CWB-004",
            businessReferenceId = customerId,
            customerId = customerId,
            accountId = null,
            payload = mapOf(
                "resultCount" to items.size,
                "statuses" to items.groupingBy { it.status }.eachCount(),
                "syntheticOnly" to true
            )
        )
        return CustomerSelfServiceAccountOpeningRequestListResponse(items = items)
    }

    @Transactional
    fun customer360(): Customer360Dto {
        val customerId = currentCustomerId()
        val profile = loadProfile(customerId)
        val accounts = customer360Accounts(customerId)
        val recentActivity = recentLedgerActivity(customerId, accountId = null, limit = 10)
        val accessSummary = accessHistorySummary(customerId)
        val result = Customer360Dto(
            profile = profile,
            kycSummary = Customer360StatusSummaryDto(
                totalCount = profile.onboardingChecks.size,
                statusCounts = profile.onboardingChecks.groupingBy { it.status }.eachCount(),
                source = "customer_onboarding_checks"
            ),
            accountSummary = Customer360AccountSummaryDto(
                totalAccounts = accounts.size,
                activeAccounts = accounts.count { it.status == "ACTIVE" },
                totalLedgerBalanceMinor = accounts.sumOf { it.ledgerBalanceMinor },
                totalAvailableBalanceMinor = accounts.sumOf { it.availableBalanceMinor },
                totalHoldAmountMinor = accounts.sumOf { it.holdAmountMinor },
                currency = accounts.firstOrNull()?.currency ?: "KRW"
            ),
            accounts = accounts,
            loanSummary = statusSummary("loans", "customer_id = :customerId", customerId),
            cardSummary = statusSummary("cards", "customer_id = :customerId", customerId),
            complaintSummary = statusSummary("complaint_cases", "customer_id = :customerId", customerId),
            paymentSummary = Customer360StatusSummaryDto(
                totalCount = 0,
                statusCounts = emptyMap(),
                source = "payment-service-read-model-not-co-located"
            ),
            notificationSummary = Customer360StatusSummaryDto(
                totalCount = 0,
                statusCounts = emptyMap(),
                source = "notification-service-read-model-not-co-located"
            ),
            recentLedgerActivity = recentActivity,
            accessHistorySummary = accessSummary,
            availableActions = availableActions(profile),
            sourceWatermarks = sourceWatermarks(customerId)
        )
        appendCustomerAudit(
            eventType = "CUSTOMER_360_VIEW",
            screenId = "CWB-104",
            businessReferenceId = customerId,
            customerId = customerId,
            accountId = null,
            payload = mapOf(
                "accountCount" to result.accounts.size,
                "recentLedgerActivityCount" to result.recentLedgerActivity.size,
                "accessEventCount" to result.accessHistorySummary.totalEvents,
                "maskedAccountNos" to result.accounts.map { it.maskedAccountNo },
                "maskingPolicy" to result.maskingPolicy,
                "syntheticOnly" to true
            )
        )
        return result
    }

    @Transactional
    fun statementArtifacts(): CustomerStatementArtifactListResponse {
        val customerId = currentCustomerId()
        val items = jdbc.query(
            """
            SELECT statement_id, customer_id, account_id, from_date, to_date,
                   statement_scope, source_ledger_hash, payload_hash,
                   synthetic_only, created_at, last_viewed_at
            FROM statement_artifact_snapshots
            WHERE customer_id = :customerId
            ORDER BY last_viewed_at DESC, statement_id DESC
            LIMIT 50
            """.trimIndent(),
            mapOf("customerId" to customerId)
        ) { rs, _ ->
            CustomerStatementArtifactDto(
                statementId = rs.getString("statement_id"),
                customerId = rs.getString("customer_id"),
                accountId = rs.getString("account_id"),
                from = rs.getObject("from_date", java.time.LocalDate::class.java),
                to = rs.getObject("to_date", java.time.LocalDate::class.java),
                statementScope = rs.getString("statement_scope"),
                sourceLedgerHash = rs.getString("source_ledger_hash"),
                payloadHash = rs.getString("payload_hash"),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
                lastViewedAt = rs.getObject("last_viewed_at", OffsetDateTime::class.java),
                syntheticOnly = rs.getBoolean("synthetic_only")
            )
        }
        appendCustomerAudit(
            eventType = "STATEMENT_ARTIFACT_HISTORY_VIEW",
            screenId = "CWB-107",
            businessReferenceId = customerId,
            customerId = customerId,
            accountId = null,
            payload = mapOf("resultCount" to items.size, "syntheticOnly" to true)
        )
        return CustomerStatementArtifactListResponse(items = items)
    }

    private fun loadProfile(customerId: String): CustomerProfileDto {
        val row = jdbc.query(
            """
            SELECT c.customer_id, c.customer_name, c.customer_phone, c.customer_address,
                   c.customer_grade, c.risk_grade,
                   cai.auth_subject, cai.username, cai.status AS auth_identity_status,
                   cai.last_login_at,
                   COALESCE(ckp.kyc_status, 'PENDING') AS kyc_status
            FROM customers c
            JOIN customer_auth_identities cai ON cai.customer_id = c.customer_id
            LEFT JOIN customer_kyc_profiles ckp ON ckp.customer_id = c.customer_id
            WHERE c.customer_id = :customerId
            """.trimIndent(),
            mapOf("customerId" to customerId),
            this::mapProfileRow
        ).firstOrNull() ?: throw WorkflowErrors.notFound("synthetic customer profile not found: $customerId")
        if (row.authIdentityStatus != "ACTIVE") {
            throw inactiveIdentity()
        }
        ensureOnboardingChecks(row)
        val checks = onboardingChecks(customerId)
        return CustomerProfileDto(
            customerId = row.customerId,
            authSubject = row.authSubject,
            username = row.username,
            maskedCustomerName = maskName(row.customerName),
            maskedPhone = maskPhone(row.customerPhone),
            maskedAddress = maskAddress(row.customerAddress),
            customerGrade = row.customerGrade,
            riskGrade = row.riskGrade,
            kycStatus = row.kycStatus,
            onboardingStatus = onboardingStatus(row.kycStatus, checks),
            duplicateCheckStatus = checks.firstOrNull { it.checkType == "DUPLICATE_IDENTITY" }?.status ?: "UNKNOWN",
            nextRequiredAction = nextRequiredAction(row.kycStatus, checks),
            lastLoginAt = row.lastLoginAt,
            authIdentityStatus = row.authIdentityStatus,
            onboardingChecks = checks
        )
    }

    private fun mapProfileRow(rs: ResultSet, rowNum: Int): ProfileRow =
        ProfileRow(
            customerId = rs.getString("customer_id"),
            customerName = rs.getString("customer_name"),
            customerPhone = rs.getString("customer_phone"),
            customerAddress = rs.getString("customer_address"),
            customerGrade = rs.getString("customer_grade"),
            riskGrade = rs.getString("risk_grade"),
            authSubject = rs.getString("auth_subject"),
            username = rs.getString("username"),
            authIdentityStatus = rs.getString("auth_identity_status"),
            lastLoginAt = rs.getObject("last_login_at", OffsetDateTime::class.java),
            kycStatus = rs.getString("kyc_status")
        )

    private fun ensureOnboardingChecks(profile: ProfileRow) {
        val duplicateCount = count(
            """
            SELECT count(*)
            FROM customers
            WHERE customer_id <> :customerId
              AND lower(customer_name) = lower(:customerName)
              AND lower(COALESCE(customer_phone, '')) = lower(:customerPhone)
              AND lower(COALESCE(customer_address, '')) = lower(:customerAddress)
            """.trimIndent(),
            mapOf(
                "customerId" to profile.customerId,
                "customerName" to profile.customerName,
                "customerPhone" to profile.customerPhone.orEmpty(),
                "customerAddress" to profile.customerAddress.orEmpty()
            )
        )
        val fingerprint = profileFingerprint(profile.customerName, profile.customerPhone, profile.customerAddress)
        val checks = listOf(
            CheckSeed(
                checkType = "DUPLICATE_IDENTITY",
                status = if (duplicateCount > 0) "REVIEW_REQUIRED" else "PASSED",
                riskLevel = if (duplicateCount > 0) "MEDIUM" else "LOW",
                evidence = mapOf(
                    "profileFingerprint" to fingerprint,
                    "duplicateCandidateCount" to duplicateCount,
                    "rawPiiStored" to false,
                    "syntheticOnly" to true
                )
            ),
            CheckSeed(
                checkType = "KYC_SIMULATION",
                status = when (profile.kycStatus) {
                    "VERIFIED" -> "PASSED"
                    "REVIEW_REQUIRED" -> "REVIEW_REQUIRED"
                    "REJECTED" -> "FAILED"
                    else -> "PENDING"
                },
                riskLevel = if (profile.kycStatus == "REJECTED") "HIGH" else "LOW",
                evidence = mapOf(
                    "kycStatus" to profile.kycStatus,
                    "realKycProviderCalled" to false,
                    "syntheticOnly" to true
                )
            ),
            CheckSeed(
                checkType = "TERMS_ACCEPTANCE",
                status = "PASSED",
                riskLevel = "LOW",
                evidence = mapOf(
                    "acceptedThrough" to "CUSTOMER_SELF_SIGNUP",
                    "legalDocumentDeliveryPerformed" to false,
                    "syntheticOnly" to true
                )
            ),
            CheckSeed(
                checkType = "CONTACT_REACHABILITY",
                status = "PASSED",
                riskLevel = "LOW",
                evidence = mapOf(
                    "profileFingerprint" to fingerprint,
                    "realSmsOrEmailSent" to false,
                    "syntheticOnly" to true
                )
            )
        )
        checks.forEach { check ->
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
                    "checkId" to "CHK-${profile.customerId}-${check.checkType}",
                    "customerId" to profile.customerId,
                    "checkType" to check.checkType,
                    "status" to check.status,
                    "riskLevel" to check.riskLevel,
                    "evidenceJson" to objectMapper.writeValueAsString(check.evidence)
                )
            )
        }
    }

    private fun onboardingChecks(customerId: String): List<CustomerOnboardingCheckDto> =
        jdbc.query(
            """
            SELECT check_id, customer_id, check_type, status, risk_level, evidence_json,
                   synthetic_only, created_at
            FROM customer_onboarding_checks
            WHERE customer_id = :customerId
            ORDER BY check_type
            """.trimIndent(),
            mapOf("customerId" to customerId)
        ) { rs, _ ->
            CustomerOnboardingCheckDto(
                checkId = rs.getString("check_id"),
                customerId = rs.getString("customer_id"),
                checkType = rs.getString("check_type"),
                status = rs.getString("status"),
                riskLevel = rs.getString("risk_level"),
                evidence = readJsonMap(rs.getString("evidence_json")),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
                syntheticOnly = rs.getBoolean("synthetic_only")
            )
        }

    private fun onboardingStatus(kycStatus: String, checks: List<CustomerOnboardingCheckDto>): String {
        if (checks.any { it.status == "FAILED" }) {
            return "BLOCKED"
        }
        if (checks.any { it.status == "REVIEW_REQUIRED" }) {
            return "REVIEW_REQUIRED"
        }
        if (kycStatus != "VERIFIED" || checks.any { it.status == "PENDING" }) {
            return "PENDING_KYC"
        }
        return "COMPLETE"
    }

    private fun nextRequiredAction(kycStatus: String, checks: List<CustomerOnboardingCheckDto>): String {
        if (checks.any { it.checkType == "DUPLICATE_IDENTITY" && it.status == "REVIEW_REQUIRED" }) {
            return "WAIT_FOR_STAFF_DUPLICATE_REVIEW"
        }
        if (checks.any { it.status == "FAILED" }) {
            return "CONTACT_SYNTHETIC_SUPPORT"
        }
        if (kycStatus != "VERIFIED" || checks.any { it.checkType == "KYC_SIMULATION" && it.status == "PENDING" }) {
            return "WAIT_FOR_SYNTHETIC_KYC_REVIEW"
        }
        return "NONE"
    }

    private fun requireActiveIdentity(customerId: String) {
        val status = jdbc.query(
            """
            SELECT status
            FROM customer_auth_identities
            WHERE customer_id = :customerId
            """.trimIndent(),
            mapOf("customerId" to customerId)
        ) { rs, _ -> rs.getString("status") }.firstOrNull()
            ?: throw WorkflowErrors.notFound("synthetic customer auth identity not found: $customerId")
        if (status != "ACTIVE") {
            throw inactiveIdentity()
        }
    }

    private fun normalizeAccountOpening(
        command: CustomerSelfServiceAccountOpeningCommand
    ): NormalizedAccountOpeningCommand {
        val idempotencyKey = command.idempotencyKey.trim()
        if (idempotencyKey.isBlank()) {
            throw WorkflowErrors.validation("idempotencyKey is required")
        }
        if (!command.termsAccepted) {
            throw WorkflowErrors.validation("termsAccepted is required")
        }
        if (command.syntheticInitialDepositAmountMinor < 0) {
            throw WorkflowErrors.validation("syntheticInitialDepositAmountMinor must be non-negative")
        }
        val productCode = command.productCode.trim().uppercase().ifBlank { "SYNTHETIC_DEPOSIT" }
        val currency = command.currency.trim().uppercase().ifBlank { "KRW" }
        if (currency.length != 3) {
            throw WorkflowErrors.validation("currency must be ISO-4217 alpha-3")
        }
        return NormalizedAccountOpeningCommand(
            idempotencyKey = idempotencyKey,
            productCode = productCode,
            accountAlias = command.accountAlias?.trim()?.ifBlank { null },
            currency = currency,
            syntheticInitialDepositAmountMinor = command.syntheticInitialDepositAmountMinor,
            termsAccepted = true
        )
    }

    private fun accountOpeningCommandHash(
        customerId: String,
        command: NormalizedAccountOpeningCommand
    ): String =
        sha256(
            objectMapper.writeValueAsString(
                mapOf(
                    "customerId" to customerId,
                    "productCode" to command.productCode,
                    "accountAlias" to command.accountAlias,
                    "currency" to command.currency,
                    "syntheticInitialDepositAmountMinor" to command.syntheticInitialDepositAmountMinor,
                    "termsAccepted" to command.termsAccepted
                ).toSortedMap()
            )
        )

    private fun existingAccountOpeningRequestByIdempotencyKey(idempotencyKey: String): AccountOpeningRequestRecord? =
        jdbc.query(
            """
            SELECT request_id, command_hash
            FROM customer_self_service_account_opening_requests
            WHERE idempotency_key = :idempotencyKey
            """.trimIndent(),
            mapOf("idempotencyKey" to idempotencyKey)
        ) { rs, _ ->
            AccountOpeningRequestRecord(
                requestId = rs.getString("request_id"),
                commandHash = rs.getString("command_hash")
            )
        }.firstOrNull()

    private fun requireNoPendingAccountOpening(customerId: String, productCode: String, currency: String) {
        val count = count(
            """
            SELECT count(*)
            FROM customer_self_service_account_opening_requests
            WHERE customer_id = :customerId
              AND requested_product_code = :productCode
              AND requested_currency = :currency
              AND status IN ('CUSTOMER_SUBMITTED', 'STAFF_REVIEWING', 'STAFF_REQUESTED')
            """.trimIndent(),
            mapOf("customerId" to customerId, "productCode" to productCode, "currency" to currency)
        )
        if (count > 0) {
            throw BankingLabDomainException(
                code = "CUSTOMER_ACCOUNT_OPENING_ALREADY_PENDING",
                status = HttpStatus.CONFLICT,
                domain = "account-opening",
                policy = "SINGLE_PENDING_CUSTOMER_ACCOUNT_OPENING_REQUEST",
                message = "customer already has a pending self-service account-opening request",
                causeText = "The same synthetic customer, product, and currency already has a pending intake awaiting staff review.",
                fix = "Wait for staff review or inspect the existing self-service account-opening request."
            )
        }
    }

    private fun requireSameCommandHash(existing: String, requested: String) {
        if (existing != requested) {
            throw idempotencyConflict("idempotency key was reused with a different customer account-opening command")
        }
    }

    private fun accountOpeningRequest(requestId: String): CustomerSelfServiceAccountOpeningRequestDto =
        jdbc.query(
            """
            SELECT *
            FROM customer_self_service_account_opening_requests
            WHERE request_id = :requestId
            """.trimIndent(),
            mapOf("requestId" to requestId),
            this::mapAccountOpeningRequest
        ).firstOrNull() ?: throw WorkflowErrors.notFound("customer self-service account-opening request not found: $requestId")

    private fun mapAccountOpeningRequest(rs: ResultSet, rowNum: Int): CustomerSelfServiceAccountOpeningRequestDto =
        CustomerSelfServiceAccountOpeningRequestDto(
            requestId = rs.getString("request_id"),
            idempotencyKey = rs.getString("idempotency_key"),
            status = rs.getString("status"),
            customerId = rs.getString("customer_id"),
            approvalId = rs.getString("approval_id"),
            approvalStatus = rs.getString("approval_status"),
            staffAccountOpeningRequestId = rs.getString("staff_account_opening_request_id"),
            requestedProductCode = rs.getString("requested_product_code"),
            requestedAccountAlias = rs.getString("requested_account_alias"),
            requestedCurrency = rs.getString("requested_currency").trim(),
            requestedInitialDepositAmountMinor = rs.getLong("requested_initial_deposit_amount_minor"),
            generatedAccountId = rs.getString("generated_account_id"),
            generatedMaskedAccountNo = rs.getString("generated_account_no")?.let(::maskAccountNo),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
            syntheticOnly = rs.getBoolean("synthetic_only")
        )

    private fun customer360Accounts(customerId: String): List<Customer360AccountDto> =
        jdbc.query(
            """
            SELECT a.account_id, a.account_no, a.status, a.currency, a.opened_at,
                   COALESCE(p.ledger_balance_minor, 0) AS ledger_balance_minor,
                   COALESCE(p.available_balance_minor, 0) AS available_balance_minor,
                   COALESCE(p.hold_amount_minor, 0) AS hold_amount_minor
            FROM accounts a
            LEFT JOIN account_balance_projections p
              ON p.account_id = a.account_id
             AND p.currency = a.currency
            WHERE a.customer_id = :customerId
              AND a.synthetic_system_account = FALSE
            ORDER BY a.opened_at, a.account_id
            """.trimIndent(),
            mapOf("customerId" to customerId)
        ) { rs, _ ->
            val accountId = rs.getString("account_id")
            Customer360AccountDto(
                accountId = accountId,
                maskedAccountNo = maskAccountNo(rs.getString("account_no")),
                status = rs.getString("status"),
                currency = rs.getString("currency").trim(),
                ledgerBalanceMinor = rs.getLong("ledger_balance_minor"),
                availableBalanceMinor = rs.getLong("available_balance_minor"),
                holdAmountMinor = rs.getLong("hold_amount_minor"),
                openedAt = rs.getObject("opened_at", OffsetDateTime::class.java),
                statementActions = listOf(
                    CustomerStatementActionDto("ACCOUNT_STATEMENT", "/api/customer/accounts/$accountId/statement")
                )
            )
        }

    private fun recentLedgerActivity(
        customerId: String,
        accountId: String?,
        limit: Int
    ): List<CustomerRecentLedgerActivityDto> =
        jdbc.query(
            """
            SELECT lt.ledger_transaction_id, lt.transaction_type, lt.business_date, lt.posted_at,
                   lp.account_id, a.account_no, lp.direction, lp.amount_minor, lp.currency, lp.posting_type,
                   lt.requested_channel
            FROM ledger_transactions lt
            JOIN ledger_postings lp ON lp.ledger_transaction_id = lt.ledger_transaction_id
            JOIN accounts a ON a.account_id = lp.account_id
            WHERE a.customer_id = :customerId
              AND (CAST(:accountId AS text) IS NULL OR lp.account_id = :accountId)
            ORDER BY lt.business_date DESC, lt.posted_at DESC NULLS LAST, lt.ledger_transaction_id DESC, lp.ledger_posting_id DESC
            LIMIT :limit
            """.trimIndent(),
            mapOf("customerId" to customerId, "accountId" to accountId, "limit" to limit)
        ) { rs, _ ->
            val direction = rs.getString("direction")
            val amount = rs.getLong("amount_minor")
            CustomerRecentLedgerActivityDto(
                transactionId = rs.getString("ledger_transaction_id"),
                transactionType = rs.getString("transaction_type"),
                businessDate = rs.getObject("business_date", java.time.LocalDate::class.java),
                postedAt = rs.getObject("posted_at", OffsetDateTime::class.java),
                accountId = rs.getString("account_id"),
                maskedAccountNo = maskAccountNo(rs.getString("account_no")),
                direction = direction,
                amountMinor = amount,
                signedAmountMinor = if (direction == "DEBIT") -amount else amount,
                currency = rs.getString("currency").trim(),
                postingType = rs.getString("posting_type"),
                requestedChannel = rs.getString("requested_channel")
            )
        }

    private fun statusSummary(table: String, whereClause: String, customerId: String): Customer360StatusSummaryDto {
        val rows = jdbc.query(
            """
            SELECT status, count(*) AS row_count
            FROM $table
            WHERE $whereClause
            GROUP BY status
            ORDER BY status
            """.trimIndent(),
            mapOf("customerId" to customerId)
        ) { rs, _ -> rs.getString("status") to rs.getInt("row_count") }
        return Customer360StatusSummaryDto(
            totalCount = rows.sumOf { it.second },
            statusCounts = rows.toMap(),
            source = table
        )
    }

    private fun accessHistorySummary(customerId: String): Customer360AccessHistorySummaryDto {
        val row = jdbc.queryForObject(
            """
            SELECT count(*) AS event_count, max(created_at) AS last_access_at
            FROM audit_events
            WHERE customer_id = :customerId
            """.trimIndent(),
            mapOf("customerId" to customerId)
        ) { rs, _ ->
            rs.getInt("event_count") to rs.getObject("last_access_at", OffsetDateTime::class.java)
        } ?: (0 to null)
        val recentTypes = jdbc.query(
            """
            SELECT event_type
            FROM audit_events
            WHERE customer_id = :customerId
            ORDER BY created_at DESC, audit_event_id DESC
            LIMIT 5
            """.trimIndent(),
            mapOf("customerId" to customerId)
        ) { rs, _ -> rs.getString("event_type") }
        return Customer360AccessHistorySummaryDto(
            totalEvents = row.first,
            recentEventTypes = recentTypes,
            lastAccessAt = row.second
        )
    }

    private fun availableActions(profile: CustomerProfileDto): List<Customer360AvailableActionDto> =
        listOf(
            Customer360AvailableActionDto(
                actionType = "REQUEST_ACCOUNT_OPENING",
                enabled = profile.authIdentityStatus == "ACTIVE",
                reason = if (profile.authIdentityStatus == "ACTIVE") null else "auth identity is not active",
                href = "/api/customer/account-opening-requests"
            ),
            Customer360AvailableActionDto(
                actionType = "VIEW_CONSOLIDATED_STATEMENT",
                enabled = true,
                href = "/api/customer/statements/consolidated"
            )
        )

    private fun sourceWatermarks(customerId: String): List<Customer360SourceWatermarkDto> =
        listOf(
            sourceWatermark(
                source = "accounts",
                sql = """
                SELECT count(*) AS row_count, max(opened_at) AS last_updated_at
                FROM accounts
                WHERE customer_id = :customerId
                  AND synthetic_system_account = FALSE
                """.trimIndent(),
                customerId = customerId
            ),
            sourceWatermark(
                source = "ledger_transactions",
                sql = """
                SELECT count(*) AS row_count, max(lt.posted_at) AS last_updated_at
                FROM ledger_transactions lt
                JOIN ledger_postings lp ON lp.ledger_transaction_id = lt.ledger_transaction_id
                JOIN accounts a ON a.account_id = lp.account_id
                WHERE a.customer_id = :customerId
                """.trimIndent(),
                customerId = customerId
            ),
            sourceWatermark(
                source = "audit_events",
                sql = """
                SELECT count(*) AS row_count, max(created_at) AS last_updated_at
                FROM audit_events
                WHERE customer_id = :customerId
                """.trimIndent(),
                customerId = customerId
            )
        )

    private fun sourceWatermark(source: String, sql: String, customerId: String): Customer360SourceWatermarkDto =
        jdbc.queryForObject(sql, mapOf("customerId" to customerId)) { rs, _ ->
            Customer360SourceWatermarkDto(
                source = source,
                rowCount = rs.getInt("row_count"),
                lastUpdatedAt = rs.getObject("last_updated_at", OffsetDateTime::class.java)
            )
        } ?: Customer360SourceWatermarkDto(source = source, rowCount = 0, lastUpdatedAt = null)

    private fun currentCustomerId(): String {
        val principal = BankingLabAuthContext.get()
            ?: throw WorkflowErrors.authorizationViolation("customer token is required")
        if (!principal.roles.contains("CUSTOMER")) {
            throw WorkflowErrors.authorizationViolation("CUSTOMER role is required")
        }
        return principal.customerId?.takeIf { it.isNotBlank() }
            ?: throw WorkflowErrors.authorizationViolation("customer token is missing customerId")
    }

    private fun appendCustomerAudit(
        eventType: String,
        screenId: String,
        businessReferenceId: String?,
        customerId: String?,
        accountId: String?,
        payload: Map<String, Any?>
    ): String {
        val principal = BankingLabAuthContext.get()
        return auditEvents.append(
            eventType = eventType,
            actorType = "CUSTOMER",
            actorId = principal?.subject ?: customerId ?: "CUSTOMER",
            actorRole = principal?.roles?.sorted()?.joinToString(",") ?: "CUSTOMER",
            screenId = screenId,
            businessReferenceId = businessReferenceId,
            customerId = customerId,
            accountId = accountId,
            reason = null,
            payload = payload
        )
    }

    private fun count(sql: String, params: Map<String, Any?>): Int =
        jdbc.queryForObject(sql, params, Int::class.java) ?: 0

    private fun readJsonMap(json: String): Map<String, Any?> =
        objectMapper.readValue(json, object : TypeReference<Map<String, Any?>>() {})

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

    private fun maskName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.length <= 2) {
            return "*".repeat(trimmed.length.coerceAtLeast(1))
        }
        return "${trimmed.first()}${"*".repeat(trimmed.length - 2)}${trimmed.last()}"
    }

    private fun maskPhone(phone: String?): String? {
        val digits = phone?.filter { it.isDigit() }.orEmpty()
        if (digits.isBlank()) {
            return null
        }
        return "***-****-${digits.takeLast(4)}"
    }

    private fun maskAddress(address: String?): String? {
        val value = address?.trim().orEmpty()
        if (value.isBlank()) {
            return null
        }
        val parts = value.split(Regex("\\s+"))
        return if (parts.size == 1) "Synthetic address masked" else "${parts.first()} ${"*".repeat(8)}"
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

    private fun inactiveIdentity(): BankingLabDomainException =
        BankingLabDomainException(
            code = "CUSTOMER_AUTH_IDENTITY_NOT_ACTIVE",
            status = HttpStatus.FORBIDDEN,
            domain = "auth",
            policy = "ACTIVE_SYNTHETIC_CUSTOMER_AUTH_REQUIRED",
            message = "synthetic customer auth identity is not active",
            causeText = "The current synthetic customer identity is locked or disabled.",
            fix = "Use an active synthetic customer or have staff resolve the modeled auth status."
        )

    private fun idempotencyConflict(message: String): BankingLabDomainException =
        BankingLabDomainException(
            code = "IDEMPOTENCY_KEY_CONFLICT",
            status = HttpStatus.CONFLICT,
            domain = "idempotency",
            invariant = "EXTERNALLY_RETRIED_COMMANDS_ARE_IDEMPOTENT",
            message = message,
            causeText = "The same idempotency key already has a different persisted command hash.",
            fix = "Retry with the original payload or generate a new idempotency key for a distinct synthetic request."
        )

    private data class ProfileRow(
        val customerId: String,
        val customerName: String,
        val customerPhone: String?,
        val customerAddress: String?,
        val customerGrade: String,
        val riskGrade: String,
        val authSubject: String,
        val username: String,
        val authIdentityStatus: String,
        val lastLoginAt: OffsetDateTime?,
        val kycStatus: String
    )

    private data class CheckSeed(
        val checkType: String,
        val status: String,
        val riskLevel: String,
        val evidence: Map<String, Any?>
    )

    private data class NormalizedAccountOpeningCommand(
        val idempotencyKey: String,
        val productCode: String,
        val accountAlias: String?,
        val currency: String,
        val syntheticInitialDepositAmountMinor: Long,
        val termsAccepted: Boolean
    )

    private data class AccountOpeningRequestRecord(
        val requestId: String,
        val commandHash: String
    )
}
