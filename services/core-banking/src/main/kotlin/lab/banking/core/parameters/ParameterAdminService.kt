package lab.banking.core.parameters

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import lab.banking.core.approval.ApprovalBusinessTypes
import lab.banking.core.approval.ApprovalStatus
import lab.banking.core.approval.ApproveApprovalCommand
import lab.banking.core.approval.OperatorApproval
import lab.banking.core.approval.PersistentApprovalService
import lab.banking.core.approval.SubmitApprovalCommand
import lab.banking.core.audit.AuditEventAppender
import lab.banking.core.security.BankingLabAuthContext
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class ParameterAdminService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val approvals: PersistentApprovalService,
    private val auditEvents: AuditEventAppender
) {
    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun parameters(namespace: String, reason: String?, asOf: LocalDate = LocalDate.now()): ParameterListResponse {
        val config = config(namespace)
        val actor = actorFor(config)
        val viewReason = requireReason(reason)
        val rows = jdbc.query(
            """
            SELECT parameter_key
            FROM ${config.parameterTable}
            ORDER BY parameter_key
            """.trimIndent(),
            emptyMap<String, Any?>()
        ) { rs, _ -> rs.getString("parameter_key") }
        val items = rows.map { key -> parameterValue(config, key, asOf) }
        val auditEventId = auditEvents.append(
            eventType = "PARAMETER_VIEW",
            actorType = "STAFF",
            actorId = actor.actorId,
            actorRole = actor.actorRole,
            screenId = config.screenId,
            businessReferenceId = config.namespace,
            reason = viewReason,
            payload = mapOf(
                "namespace" to config.namespace,
                "parameterCount" to items.size,
                "asOf" to asOf.toString(),
                "syntheticOnly" to true
            )
        )
        return ParameterListResponse(auditEventId = auditEventId, items = items)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun history(namespace: String, reason: String?): ParameterHistoryResponse {
        val config = config(namespace)
        val actor = actorFor(config)
        val viewReason = requireReason(reason)
        val items = jdbc.query(
            versionSql(config, "ORDER BY parameter_key, effective_from, created_at"),
            emptyMap<String, Any?>()
        ) { rs, _ -> mapVersion(config.namespace, rs) }
        val auditEventId = auditEvents.append(
            eventType = "PARAMETER_HISTORY_VIEW",
            actorType = "STAFF",
            actorId = actor.actorId,
            actorRole = actor.actorRole,
            screenId = config.screenId,
            businessReferenceId = config.namespace,
            reason = viewReason,
            payload = mapOf(
                "namespace" to config.namespace,
                "versionCount" to items.size,
                "syntheticOnly" to true
            )
        )
        return ParameterHistoryResponse(auditEventId = auditEventId, items = items)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun requestChange(namespace: String, command: ParameterChangeRequestCommand): ParameterChangeRequestResponse {
        val config = config(namespace)
        val requestedBy = command.requestedBy?.takeIf { it.isNotBlank() } ?: currentActor().actorId
        val requestedRole = command.requestedByRole?.takeIf { it.isNotBlank() } ?: currentActor().actorRole
        BankingLabAuthContext.requireActor(requestedBy, requestedRole)
        requireRole(requestedRole, config.requestRoles)
        val reason = requireReason(command.reason)
        val idempotencyKey = requireField(command.idempotencyKey, "idempotencyKey")
        existingChangeRequest(config.namespace, requestedBy, idempotencyKey)?.let {
            return ParameterChangeRequestResponse(
                item = it,
                approval = approvals.approval(it.approvalId),
                replayed = true
            )
        }
        val parameterKey = requireField(command.parameterKey, "parameterKey")
        if (parameterKey !in config.parameterKeys) {
            throw WorkflowErrors.validation("parameterKey is not supported for ${config.namespace}: $parameterKey")
        }
        val effectiveFrom = command.effectiveFrom ?: command.effectiveAt?.toLocalDate()
            ?: throw WorkflowErrors.validation("effectiveFrom or effectiveAt is required")
        val rollbackPlan = requireField(command.rollbackPlan, "rollbackPlan")
        val parameter = parameterRow(config, parameterKey)
        val rollbackVersion = command.rollbackOfVersionId?.takeIf { it.isNotBlank() }
            ?.let { versionById(config, it) }
        if (rollbackVersion != null && rollbackVersion.parameterKey != parameterKey) {
            throw WorkflowErrors.validation("rollbackOfVersionId does not belong to parameterKey")
        }
        val requestedValue = rollbackVersion?.parameterValue
            ?: normalizedValue(command.scheduledValue, parameter.valueType)
        validateParameterValue(config, parameterKey, requestedValue)
        val current = currentVersion(config, parameterKey, effectiveFrom)
            ?: currentVersion(config, parameterKey, LocalDate.now())
        val requestId = "${config.requestPrefix}-${UUID.randomUUID().toString().uppercase()}"
        val approval = approvals.submit(
            SubmitApprovalCommand(
                businessType = config.businessType,
                businessReferenceId = requestId,
                requestedBy = requestedBy,
                requestedByRole = requestedRole,
                requestReason = reason,
                beforeSnapshot = mapOf(
                    "namespace" to config.namespace,
                    "parameterKey" to parameterKey,
                    "currentValue" to current?.parameterValue,
                    "currentVersionId" to current?.parameterVersionId,
                    "effectiveFrom" to current?.effectiveFrom?.toString(),
                    "syntheticOnly" to true
                ),
                afterSnapshot = mapOf(
                    "namespace" to config.namespace,
                    "parameterKey" to parameterKey,
                    "requestedValue" to requestedValue,
                    "effectiveFrom" to effectiveFrom.toString(),
                    "rollbackOfVersionId" to rollbackVersion?.parameterVersionId,
                    "syntheticOnly" to true
                ),
                screenId = config.screenId
            )
        )
        jdbc.update(
            """
            INSERT INTO parameter_change_requests (
              request_id, namespace, parameter_key, business_type, approval_id,
              requested_value, value_type, effective_from, rollback_plan, rollback_of_version_id,
              requested_by, requested_role, reason, status, idempotency_key, metadata_json
            )
            VALUES (
              :requestId, :namespace, :parameterKey, :businessType, :approvalId,
              :requestedValue, :valueType, :effectiveFrom, :rollbackPlan, :rollbackOfVersionId,
              :requestedBy, :requestedRole, :reason, 'PENDING_APPROVAL', :idempotencyKey, CAST(:metadata AS jsonb)
            )
            """.trimIndent(),
            mapOf(
                "requestId" to requestId,
                "namespace" to config.namespace,
                "parameterKey" to parameterKey,
                "businessType" to config.businessType,
                "approvalId" to approval.approvalId,
                "requestedValue" to requestedValue,
                "valueType" to parameter.valueType,
                "effectiveFrom" to effectiveFrom,
                "rollbackPlan" to rollbackPlan,
                "rollbackOfVersionId" to rollbackVersion?.parameterVersionId,
                "requestedBy" to requestedBy,
                "requestedRole" to requestedRole,
                "reason" to reason,
                "idempotencyKey" to idempotencyKey,
                "metadata" to objectMapper.writeValueAsString(mapOf("syntheticOnly" to true, "screenId" to config.screenId))
            )
        )
        return ParameterChangeRequestResponse(
            item = changeRequest(requestId),
            approval = approval,
            replayed = false
        )
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun applyApprovedChange(approval: OperatorApproval, command: ApproveApprovalCommand): ParameterChangeRequestDto {
        val config = configsByBusinessType[approval.businessType]
            ?: throw WorkflowErrors.stateViolation("approval is not a parameter change approval")
        if (approval.status != ApprovalStatus.APPROVED) {
            throw WorkflowErrors.stateViolation("approval is not approved: ${approval.status}")
        }
        val request = changeRequestForUpdate(approval.businessReferenceId)
        if (request.approvalId != approval.approvalId || request.businessType != approval.businessType) {
            throw WorkflowErrors.stateViolation("approval does not match parameter change request")
        }
        if (request.status != "PENDING_APPROVAL") {
            throw WorkflowErrors.stateViolation("parameter change request is not pending approval: ${request.status}")
        }
        val versionId = "${config.versionPrefix}-${UUID.randomUUID().toString().uppercase()}"
        jdbc.update(
            """
            INSERT INTO ${config.versionTable} (
              parameter_version_id, parameter_key, parameter_value, value_type, effective_from,
              effective_to, approval_id, created_by, approved_at, rollback_of_version_id, metadata_json
            )
            VALUES (
              :versionId, :parameterKey, :parameterValue, :valueType, :effectiveFrom,
              NULL, :approvalId, :createdBy, now(), :rollbackOfVersionId, CAST(:metadata AS jsonb)
            )
            """.trimIndent(),
            mapOf(
                "versionId" to versionId,
                "parameterKey" to request.parameterKey,
                "parameterValue" to request.requestedValue,
                "valueType" to request.valueType,
                "effectiveFrom" to request.effectiveFrom,
                "approvalId" to approval.approvalId,
                "createdBy" to request.requestedBy,
                "rollbackOfVersionId" to request.rollbackOfVersionId,
                "metadata" to objectMapper.writeValueAsString(mapOf("syntheticOnly" to true, "requestId" to request.requestId))
            )
        )
        if (!request.effectiveFrom.isAfter(LocalDate.now())) {
            jdbc.update(
                """
                UPDATE ${config.parameterTable}
                SET current_version_id = :versionId,
                    updated_at = now()
                WHERE parameter_key = :parameterKey
                """.trimIndent(),
                mapOf("versionId" to versionId, "parameterKey" to request.parameterKey)
            )
        }
        jdbc.update(
            """
            UPDATE parameter_change_requests
            SET status = 'APPLIED',
                applied_version_id = :versionId,
                applied_at = now(),
                updated_at = now()
            WHERE request_id = :requestId
            """.trimIndent(),
            mapOf("requestId" to request.requestId, "versionId" to versionId)
        )
        auditEvents.append(
            eventType = "PARAMETER_CHANGE_APPLIED",
            actorType = "STAFF",
            actorId = command.approvedBy,
            actorRole = command.approvedByRole,
            screenId = config.screenId,
            businessReferenceId = request.requestId,
            reason = request.reason,
            payload = mapOf(
                "approvalId" to approval.approvalId,
                "businessType" to approval.businessType,
                "namespace" to request.namespace,
                "parameterKey" to request.parameterKey,
                "parameterVersionId" to versionId,
                "requestedValue" to request.requestedValue,
                "effectiveFrom" to request.effectiveFrom.toString(),
                "rollbackOfVersionId" to request.rollbackOfVersionId,
                "syntheticOnly" to true,
                "ledgerSourceRowsMutated" to false
            )
        )
        return changeRequest(request.requestId)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun rejectChange(approval: OperatorApproval): ParameterChangeRequestDto {
        val config = configsByBusinessType[approval.businessType]
            ?: throw WorkflowErrors.stateViolation("approval is not a parameter change approval")
        val request = changeRequestForUpdate(approval.businessReferenceId)
        if (request.approvalId != approval.approvalId || request.businessType != approval.businessType) {
            throw WorkflowErrors.stateViolation("approval does not match parameter change request")
        }
        if (request.status != "PENDING_APPROVAL") {
            throw WorkflowErrors.stateViolation("parameter change request is not pending approval: ${request.status}")
        }
        jdbc.update(
            """
            UPDATE parameter_change_requests
            SET status = 'REJECTED',
                updated_at = now()
            WHERE request_id = :requestId
            """.trimIndent(),
            mapOf("requestId" to request.requestId)
        )
        auditEvents.append(
            eventType = "PARAMETER_CHANGE_REJECTED",
            actorType = "STAFF",
            actorId = approval.rejectedBy ?: "UNKNOWN",
            actorRole = "CHECKER",
            screenId = config.screenId,
            businessReferenceId = request.requestId,
            reason = request.reason,
            payload = mapOf(
                "approvalId" to approval.approvalId,
                "businessType" to approval.businessType,
                "namespace" to request.namespace,
                "parameterKey" to request.parameterKey,
                "syntheticOnly" to true
            )
        )
        return changeRequest(request.requestId)
    }

    @Transactional(readOnly = true)
    fun longValue(namespace: String, parameterKey: String, asOf: LocalDate = LocalDate.now()): Long {
        val config = config(namespace)
        val version = currentVersion(config, parameterKey, asOf)
            ?: throw WorkflowErrors.stateViolation("no parameter version is effective for $namespace.$parameterKey on $asOf")
        return version.parameterValue.toLongOrNull()
            ?: throw WorkflowErrors.stateViolation("parameter is not numeric: $namespace.$parameterKey")
    }

    private fun parameterValue(config: ParameterNamespaceConfig, parameterKey: String, asOf: LocalDate): ParameterValueDto {
        val current = currentVersion(config, parameterKey, asOf)
            ?: throw WorkflowErrors.stateViolation("no current parameter version for ${config.namespace}.$parameterKey")
        val scheduled = jdbc.query(
            versionSql(config, "WHERE parameter_key = :parameterKey AND effective_from > :asOf ORDER BY effective_from, created_at"),
            mapOf("parameterKey" to parameterKey, "asOf" to asOf)
        ) { rs, _ -> mapVersion(config.namespace, rs) }
        return ParameterValueDto(
            namespace = config.namespace,
            parameterKey = parameterKey,
            currentValue = current.parameterValue,
            currentVersionId = current.parameterVersionId,
            valueType = current.valueType,
            effectiveFrom = current.effectiveFrom,
            scheduled = scheduled
        )
    }

    private fun currentVersion(config: ParameterNamespaceConfig, parameterKey: String, asOf: LocalDate): ParameterVersionDto? =
        jdbc.query(
            versionSql(
                config,
                """
                WHERE parameter_key = :parameterKey
                  AND effective_from <= :asOf
                  AND (effective_to IS NULL OR effective_to >= :asOf)
                ORDER BY effective_from DESC, created_at DESC
                LIMIT 1
                """.trimIndent()
            ),
            mapOf("parameterKey" to parameterKey, "asOf" to asOf)
        ) { rs, _ -> mapVersion(config.namespace, rs) }.firstOrNull()

    private fun versionById(config: ParameterNamespaceConfig, versionId: String): ParameterVersionDto =
        try {
            jdbc.queryForObject(
                versionSql(config, "WHERE parameter_version_id = :versionId"),
                mapOf("versionId" to versionId)
            ) { rs, _ -> mapVersion(config.namespace, rs) }
                ?: throw WorkflowErrors.notFound("parameter version not found: $versionId")
        } catch (_: EmptyResultDataAccessException) {
            throw WorkflowErrors.notFound("parameter version not found: $versionId")
        }

    private fun parameterRow(config: ParameterNamespaceConfig, parameterKey: String): ParameterRow =
        try {
            jdbc.queryForObject(
                """
                SELECT parameter_key, value_type
                FROM ${config.parameterTable}
                WHERE parameter_key = :parameterKey
                FOR UPDATE
                """.trimIndent(),
                mapOf("parameterKey" to parameterKey)
            ) { rs, _ -> ParameterRow(rs.getString("parameter_key"), rs.getString("value_type")) }
                ?: throw WorkflowErrors.notFound("parameter not found: ${config.namespace}.$parameterKey")
        } catch (_: EmptyResultDataAccessException) {
            throw WorkflowErrors.notFound("parameter not found: ${config.namespace}.$parameterKey")
        }

    private fun existingChangeRequest(namespace: String, requestedBy: String, idempotencyKey: String): ParameterChangeRequestDto? =
        jdbc.query(
            changeRequestSql("WHERE namespace = :namespace AND requested_by = :requestedBy AND idempotency_key = :idempotencyKey"),
            mapOf("namespace" to namespace, "requestedBy" to requestedBy, "idempotencyKey" to idempotencyKey),
            this::mapChangeRequest
        ).firstOrNull()

    fun changeRequest(requestId: String): ParameterChangeRequestDto =
        jdbc.queryForObject(changeRequestSql("WHERE request_id = :requestId"), mapOf("requestId" to requestId), this::mapChangeRequest)
            ?: throw WorkflowErrors.notFound("parameter change request not found: $requestId")

    private fun changeRequestForUpdate(requestId: String): ParameterChangeRequestDto =
        jdbc.queryForObject(changeRequestSql("WHERE request_id = :requestId FOR UPDATE"), mapOf("requestId" to requestId), this::mapChangeRequest)
            ?: throw WorkflowErrors.notFound("parameter change request not found: $requestId")

    private fun versionSql(config: ParameterNamespaceConfig, whereClause: String): String =
        """
        SELECT parameter_version_id, parameter_key, parameter_value, value_type,
               effective_from, effective_to, approval_id, created_by, approved_at,
               rollback_of_version_id, created_at
        FROM ${config.versionTable}
        $whereClause
        """.trimIndent()

    private fun changeRequestSql(whereClause: String): String =
        """
        SELECT request_id, namespace, parameter_key, business_type, approval_id,
               requested_value, value_type, effective_from, rollback_plan, rollback_of_version_id,
               requested_by, requested_role, reason, status, idempotency_key,
               applied_version_id, applied_at, created_at, updated_at
        FROM parameter_change_requests
        $whereClause
        """.trimIndent()

    private fun mapVersion(namespace: String, rs: ResultSet): ParameterVersionDto =
        ParameterVersionDto(
            namespace = namespace,
            parameterVersionId = rs.getString("parameter_version_id"),
            parameterKey = rs.getString("parameter_key"),
            parameterValue = rs.getString("parameter_value"),
            valueType = rs.getString("value_type"),
            effectiveFrom = rs.getObject("effective_from", LocalDate::class.java),
            effectiveTo = rs.getObject("effective_to", LocalDate::class.java),
            approvalId = rs.getString("approval_id"),
            createdBy = rs.getString("created_by"),
            approvedAt = rs.getObject("approved_at", OffsetDateTime::class.java),
            rollbackOfVersionId = rs.getString("rollback_of_version_id"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
        )

    private fun mapChangeRequest(rs: ResultSet, rowNum: Int): ParameterChangeRequestDto =
        ParameterChangeRequestDto(
            requestId = rs.getString("request_id"),
            namespace = rs.getString("namespace"),
            parameterKey = rs.getString("parameter_key"),
            businessType = rs.getString("business_type"),
            approvalId = rs.getString("approval_id"),
            requestedValue = rs.getString("requested_value"),
            valueType = rs.getString("value_type"),
            effectiveFrom = rs.getObject("effective_from", LocalDate::class.java),
            rollbackPlan = rs.getString("rollback_plan"),
            rollbackOfVersionId = rs.getString("rollback_of_version_id"),
            requestedBy = rs.getString("requested_by"),
            requestedRole = rs.getString("requested_role"),
            reason = rs.getString("reason"),
            status = rs.getString("status"),
            idempotencyKey = rs.getString("idempotency_key"),
            appliedVersionId = rs.getString("applied_version_id"),
            appliedAt = rs.getObject("applied_at", OffsetDateTime::class.java),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java)
        )

    private fun normalizedValue(value: Any?, valueType: String): String {
        val normalized = when (value) {
            null -> throw WorkflowErrors.validation("scheduledValue is required")
            is String -> requireField(value, "scheduledValue")
            is Number -> value.toLong().toString()
            is Boolean -> value.toString()
            else -> objectMapper.writeValueAsString(value)
        }
        when (valueType) {
            "NUMBER" -> normalized.toLongOrNull() ?: throw WorkflowErrors.validation("scheduledValue must be numeric")
            "BOOLEAN" -> if (normalized != "true" && normalized != "false") {
                throw WorkflowErrors.validation("scheduledValue must be true or false")
            }
            "JSON" -> objectMapper.readTree(normalized)
            "TEXT" -> Unit
            else -> throw WorkflowErrors.validation("unsupported value type: $valueType")
        }
        return normalized
    }

    private fun validateParameterValue(config: ParameterNamespaceConfig, parameterKey: String, requestedValue: String) {
        if (config.namespace != "authorization") {
            return
        }
        when (parameterKey) {
            "roleMenuMap" -> validateRoleMenuMap(requestedValue)
            "approvalRoleMatrix" -> validateApprovalRoleMatrix(requestedValue)
            "reasonRequiredScreens" -> validateScreenCatalog("reasonRequiredScreens", requestedValue)
        }
    }

    private fun validateRoleMenuMap(value: String) {
        if (looksLikeJson(value)) {
            val node = parseJsonParameter("roleMenuMap", value)
            if (!node.isObject || node.size() == 0) {
                throw WorkflowErrors.validation("roleMenuMap must be a non-empty object mapping roles to screen id arrays")
            }
            val roles = node.fieldNames()
            while (roles.hasNext()) {
                val role = roles.next()
                val screens = node.path(role)
                validateRoleCode("roleMenuMap", role)
                if (!screens.isArray || screens.size() == 0) {
                    throw WorkflowErrors.validation("roleMenuMap values must be non-empty screen id arrays")
                }
                val seenScreens = mutableSetOf<String>()
                screens.forEach { screen ->
                    if (!screen.isTextual) {
                        throw WorkflowErrors.validation("roleMenuMap screen ids must be strings")
                    }
                    val screenId = screen.asText()
                    validateScreenId("roleMenuMap", screenId)
                    if (!seenScreens.add(screenId)) {
                        throw WorkflowErrors.validation("roleMenuMap role $role contains duplicate screen id: $screenId")
                    }
                }
            }
            return
        }
        validateDelimitedRoleMap("roleMenuMap", value, ::validateScreenId)
    }

    private fun validateApprovalRoleMatrix(value: String) {
        if (looksLikeJson(value)) {
            val node = parseJsonParameter("approvalRoleMatrix", value)
            if (!node.isObject || node.size() == 0) {
                throw WorkflowErrors.validation("approvalRoleMatrix must be a non-empty object mapping roles to business type arrays")
            }
            val roles = node.fieldNames()
            while (roles.hasNext()) {
                val role = roles.next()
                val businessTypes = node.path(role)
                validateRoleCode("approvalRoleMatrix", role)
                if (!businessTypes.isArray || businessTypes.size() == 0) {
                    throw WorkflowErrors.validation("approvalRoleMatrix values must be non-empty business type arrays")
                }
                val seenBusinessTypes = mutableSetOf<String>()
                businessTypes.forEach { businessType ->
                    if (!businessType.isTextual) {
                        throw WorkflowErrors.validation("approvalRoleMatrix business types must be strings")
                    }
                    val businessTypeValue = businessType.asText()
                    validateApprovalBusinessType("approvalRoleMatrix", businessTypeValue)
                    if (!seenBusinessTypes.add(businessTypeValue)) {
                        throw WorkflowErrors.validation("approvalRoleMatrix role $role contains duplicate business type: $businessTypeValue")
                    }
                }
            }
            return
        }
        validateDelimitedRoleMap("approvalRoleMatrix", value, ::validateApprovalBusinessType)
    }

    private fun validateScreenCatalog(parameterKey: String, value: String) {
        val screens = if (looksLikeJson(value)) {
            val node = parseJsonParameter(parameterKey, value)
            if (!node.isArray || node.size() == 0) {
                throw WorkflowErrors.validation("$parameterKey must be a non-empty screen id array")
            }
            node.map {
                if (!it.isTextual) {
                    throw WorkflowErrors.validation("$parameterKey screen ids must be strings")
                }
                it.asText()
            }
        } else {
            value.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }
        if (screens.isEmpty()) {
            throw WorkflowErrors.validation("$parameterKey must include at least one screen id")
        }
        val seen = mutableSetOf<String>()
        screens.forEach { screen ->
            validateScreenId(parameterKey, screen)
            if (!seen.add(screen)) {
                throw WorkflowErrors.validation("$parameterKey contains duplicate screen id: $screen")
            }
        }
    }

    private fun validateDelimitedRoleMap(parameterKey: String, value: String, valueValidator: (String, String) -> Unit) {
        val entries = value.split(";").map { it.trim() }.filter { it.isNotBlank() }
        if (entries.isEmpty()) {
            throw WorkflowErrors.validation("$parameterKey must include at least one ROLE:value entry")
        }
        val seenRoles = mutableSetOf<String>()
        entries.forEach { entry ->
            val parts = entry.split(":", limit = 2)
            if (parts.size != 2) {
                throw WorkflowErrors.validation("$parameterKey entries must use ROLE:value format")
            }
            val role = parts[0].trim()
            validateRoleCode(parameterKey, role)
            if (!seenRoles.add(role)) {
                throw WorkflowErrors.validation("$parameterKey contains duplicate role: $role")
            }
            val values = parts[1].split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (values.isEmpty()) {
                throw WorkflowErrors.validation("$parameterKey role $role must include at least one value")
            }
            val seenValues = mutableSetOf<String>()
            values.forEach {
                valueValidator(parameterKey, it)
                if (!seenValues.add(it)) {
                    throw WorkflowErrors.validation("$parameterKey role $role contains duplicate value: $it")
                }
            }
        }
    }

    private fun validateRoleCode(parameterKey: String, role: String) {
        if (!ROLE_CODE_PATTERN.matches(role)) {
            throw WorkflowErrors.validation("$parameterKey contains invalid role code: $role")
        }
    }

    private fun validateScreenId(parameterKey: String, screenId: String) {
        if (!SCREEN_ID_PATTERN.matches(screenId)) {
            throw WorkflowErrors.validation("$parameterKey contains invalid screen id: $screenId")
        }
    }

    private fun validateApprovalBusinessType(parameterKey: String, businessType: String) {
        if (businessType !in ApprovalBusinessTypes.highRisk) {
            throw WorkflowErrors.validation("$parameterKey contains unsupported approval business type: $businessType")
        }
    }

    private fun looksLikeJson(value: String): Boolean {
        val trimmed = value.trimStart()
        return trimmed.startsWith("{") || trimmed.startsWith("[")
    }

    private fun parseJsonParameter(parameterKey: String, value: String) =
        try {
            objectMapper.readTree(value)
        } catch (ex: Exception) {
            throw WorkflowErrors.validation("$parameterKey must contain valid JSON when JSON syntax is used")
        }

    private fun config(namespace: String): ParameterNamespaceConfig =
        configs[namespace] ?: throw WorkflowErrors.validation("unsupported parameter namespace: $namespace")

    private fun currentActor(): ParameterActor {
        val principal = BankingLabAuthContext.get()
        val actorId = principal?.subject ?: "system"
        val actorRole = principal?.roles?.sorted()?.firstOrNull() ?: "SYSTEM"
        return ParameterActor(actorId, actorRole)
    }

    private fun actorFor(config: ParameterNamespaceConfig): ParameterActor {
        val principal = BankingLabAuthContext.get()
        if (principal == null) {
            val actor = currentActor()
            if (actor.actorRole !in config.viewRoles) {
                throw WorkflowErrors.authorizationViolation("actor role cannot view ${config.namespace} parameters")
            }
            return actor
        }
        val allowedRole = principal.roles.sorted().firstOrNull { it in config.viewRoles }
        if (allowedRole == null) {
            throw WorkflowErrors.authorizationViolation("actor role cannot view ${config.namespace} parameters")
        }
        return ParameterActor(principal.subject, allowedRole)
    }

    private fun requireRole(role: String, allowedRoles: Set<String>) {
        if (role !in allowedRoles) {
            throw WorkflowErrors.authorizationViolation("actor role cannot change parameter namespace")
        }
    }

    private fun requireReason(value: String?): String {
        if (value.isNullOrBlank()) {
            throw WorkflowErrors.reasonRequired("reason is required")
        }
        return value
    }

    private fun requireField(value: String?, field: String): String {
        if (value.isNullOrBlank()) {
            throw WorkflowErrors.validation("$field is required")
        }
        return value
    }

    private data class ParameterRow(val parameterKey: String, val valueType: String)

    private data class ParameterActor(val actorId: String, val actorRole: String)

    private data class ParameterNamespaceConfig(
        val namespace: String,
        val parameterTable: String,
        val versionTable: String,
        val businessType: String,
        val screenId: String,
        val requestPrefix: String,
        val versionPrefix: String,
        val parameterKeys: Set<String>,
        val viewRoles: Set<String>,
        val requestRoles: Set<String>
    )

    companion object {
        private val configs: Map<String, ParameterNamespaceConfig> = listOf(
            ParameterNamespaceConfig(
                namespace = "reconciliation",
                parameterTable = "reconciliation_parameters",
                versionTable = "reconciliation_parameter_versions",
                businessType = ApprovalBusinessTypes.RECONCILIATION_PARAMETER_CHANGE,
                screenId = "OPS-301",
                requestPrefix = "RPC",
                versionPrefix = "RPV",
                parameterKeys = setOf("autoMatchToleranceMinor", "unmatchedItemSlaHours"),
                viewRoles = setOf("OPS_OPERATOR", "OPS_MANAGER", "BRANCH_MANAGER", "COMPLIANCE_MANAGER"),
                requestRoles = setOf("OPS_MANAGER", "BRANCH_MANAGER")
            ),
            ParameterNamespaceConfig(
                namespace = "audit",
                parameterTable = "audit_retention_parameters",
                versionTable = "audit_retention_parameter_versions",
                businessType = ApprovalBusinessTypes.AUDIT_PARAMETER_CHANGE,
                screenId = "AUD-201",
                requestPrefix = "APC",
                versionPrefix = "APV",
                parameterKeys = setOf("retentionYears", "hashChainVerificationCadenceHours"),
                viewRoles = setOf("AUDITOR", "COMPLIANCE_MANAGER"),
                requestRoles = setOf("AUDITOR", "COMPLIANCE_MANAGER")
            ),
            ParameterNamespaceConfig(
                namespace = "fds",
                parameterTable = "fds_rule_parameters",
                versionTable = "fds_rule_parameter_versions",
                businessType = ApprovalBusinessTypes.FDS_RULE_PARAMETER_CHANGE,
                screenId = "FDS-301",
                requestPrefix = "FPC",
                versionPrefix = "FPV",
                parameterKeys = setOf("highAmountMinor", "newDeviceHoldHours", "velocityWindowMinutes"),
                viewRoles = setOf("FDS_REVIEWER", "AML_REVIEWER", "COMPLIANCE_MANAGER"),
                requestRoles = setOf("FDS_REVIEWER", "COMPLIANCE_MANAGER")
            ),
            ParameterNamespaceConfig(
                namespace = "security",
                parameterTable = "security_policy_parameters",
                versionTable = "security_policy_parameter_versions",
                businessType = ApprovalBusinessTypes.SECURITY_POLICY_PARAMETER_CHANGE,
                screenId = "ADM-201",
                requestPrefix = "SPC",
                versionPrefix = "SPV",
                parameterKeys = setOf("simulatorTokensEnabled", "passkeyRecoveryDualControlRequired", "staffSessionTtlSeconds"),
                viewRoles = setOf("COMPLIANCE_MANAGER", "PASSKEY_RECOVERY_ADMIN"),
                requestRoles = setOf("COMPLIANCE_MANAGER", "PASSKEY_RECOVERY_ADMIN")
            ),
            ParameterNamespaceConfig(
                namespace = "authorization",
                parameterTable = "menu_role_parameters",
                versionTable = "menu_role_parameter_versions",
                businessType = ApprovalBusinessTypes.AUTHORIZATION_PARAMETER_CHANGE,
                screenId = "ADM-301",
                requestPrefix = "MPC",
                versionPrefix = "MPV",
                parameterKeys = setOf("roleMenuMap", "approvalRoleMatrix", "reasonRequiredScreens"),
                viewRoles = setOf("COMPLIANCE_MANAGER", "PASSKEY_RECOVERY_ADMIN"),
                requestRoles = setOf("COMPLIANCE_MANAGER", "PASSKEY_RECOVERY_ADMIN")
            )
        ).associateBy { it.namespace }

        private val configsByBusinessType: Map<String, ParameterNamespaceConfig> =
            configs.values.associateBy { it.businessType }

        private val ROLE_CODE_PATTERN = Regex("^[A-Z][A-Z0-9_]*$")
        private val SCREEN_ID_PATTERN = Regex("^[A-Z]{2,4}(?:-[A-Z0-9]+)*-\\d{3}$")
    }
}
