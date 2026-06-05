package lab.banking.notification.domain

import java.util.UUID
import lab.banking.notification.persistence.NotificationRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationTemplateAdminService(
    private val repository: NotificationRepository
) {
    private val providerByChannel = mapOf(
        "SMS" to "SYNTHETIC_SMS_SINK",
        "EMAIL" to "SYNTHETIC_EMAIL_SINK",
        "PUSH" to "SYNTHETIC_PUSH_SINK",
        "CHAT" to "SYNTHETIC_CHAT_SINK"
    )
    private val checkerRoles = setOf("OPS_MANAGER", "COMPLIANCE_MANAGER", "NOTIFICATION_MANAGER")

    fun templates(eventType: String?, channel: String?): List<NotificationTemplateDto> =
        repository
            .listTemplates(eventType?.takeIf { it.isNotBlank() }, channel?.takeIf { it.isNotBlank() }?.uppercase())
            .map { it.toDto() }

    fun changeRequest(changeRequestId: String): NotificationTemplateChangeRequestDto =
        (repository.findTemplateChangeRequest(changeRequestId) ?: throw changeRequestNotFound(changeRequestId)).toDto()

    @Transactional
    fun createChangeRequest(
        request: CreateNotificationTemplateChangeRequest
    ): NotificationTemplateChangeRequestDto {
        validateCreate(request)
        val normalizedEventType = request.eventType.trim()
        val normalizedChannel = request.channel.uppercase()
        val normalizedProvider = request.providerKind.uppercase()
        val latestVersion = repository.latestTemplateVersion(normalizedEventType, normalizedChannel)
        if (request.version <= latestVersion) {
            throw notificationError(
                code = "NOTIFICATION_TEMPLATE_VERSION_NOT_INCREMENTED",
                status = HttpStatus.CONFLICT,
                invariant = "notification template versions must increase monotonically",
                message = "template version must be greater than the latest approved version",
                cause = "Template activation is append-only and cannot overwrite an active or retired version.",
                fix = "Submit a version greater than $latestVersion for $normalizedEventType/$normalizedChannel.",
                details = mapOf("latestVersion" to latestVersion)
            )
        }

        val changeRequestId = changeRequestId()
        repository.insertTemplateChangeRequest(
            changeRequestId = changeRequestId,
            eventType = normalizedEventType,
            channel = normalizedChannel,
            version = request.version,
            bodyTemplate = request.bodyTemplate.trim(),
            providerKind = normalizedProvider,
            requestedBy = request.requestedBy.trim(),
            reason = request.reason.trim()
        )
        return changeRequest(changeRequestId)
    }

    @Transactional
    fun approveChangeRequest(
        changeRequestId: String,
        request: ApproveNotificationTemplateChangeRequest
    ): NotificationTemplateChangeRequestDto {
        validateDecision(request.approvedBy, request.approvedByRole, request.reason)
        val current = repository.findTemplateChangeRequestForUpdate(changeRequestId)
            ?: throw changeRequestNotFound(changeRequestId)
        requirePending(current)
        requireDifferentChecker(current.requestedBy, request.approvedBy)
        requireCheckerRole(request.approvedByRole)

        val latestVersion = repository.latestTemplateVersion(current.eventType, current.channel)
        if (current.requestedVersion <= latestVersion) {
            throw notificationError(
                code = "NOTIFICATION_TEMPLATE_VERSION_NOT_INCREMENTED",
                status = HttpStatus.CONFLICT,
                invariant = "notification template versions must increase monotonically",
                message = "approved template version is no longer greater than the latest approved version",
                cause = "Another checker already approved a newer template for this event/channel.",
                fix = "Reject this request or submit a new request with a higher version.",
                details = mapOf("latestVersion" to latestVersion)
            )
        }

        val templateId = templateId(current.channel)
        repository.retireActiveTemplates(current.eventType, current.channel)
        repository.insertTemplate(
            templateId = templateId,
            eventType = current.eventType,
            channel = current.channel,
            version = current.requestedVersion,
            bodyTemplate = current.bodyTemplate,
            providerKind = current.providerKind
        )
        repository.approveTemplateChangeRequest(
            changeRequestId = current.changeRequestId,
            reviewedBy = request.approvedBy.trim(),
            reviewedByRole = request.approvedByRole.trim().uppercase(),
            reason = request.reason.trim(),
            approvedTemplateId = templateId
        )
        return changeRequest(changeRequestId)
    }

    @Transactional
    fun rejectChangeRequest(
        changeRequestId: String,
        request: RejectNotificationTemplateChangeRequest
    ): NotificationTemplateChangeRequestDto {
        validateDecision(request.rejectedBy, request.rejectedByRole, request.reason)
        val current = repository.findTemplateChangeRequestForUpdate(changeRequestId)
            ?: throw changeRequestNotFound(changeRequestId)
        requirePending(current)
        requireDifferentChecker(current.requestedBy, request.rejectedBy)
        requireCheckerRole(request.rejectedByRole)
        repository.rejectTemplateChangeRequest(
            changeRequestId = current.changeRequestId,
            reviewedBy = request.rejectedBy.trim(),
            reviewedByRole = request.rejectedByRole.trim().uppercase(),
            reason = request.reason.trim()
        )
        return changeRequest(changeRequestId)
    }

    private fun validateCreate(request: CreateNotificationTemplateChangeRequest) {
        requireNonBlank(request.eventType, "eventType")
        requireNonBlank(request.channel, "channel")
        requireNonBlank(request.bodyTemplate, "bodyTemplate")
        requireNonBlank(request.providerKind, "providerKind")
        requireNonBlank(request.requestedBy, "requestedBy")
        requireReason(request.reason)
        if (!request.syntheticOnly) {
            throw notificationError(
                code = "NOTIFICATION_REAL_PROVIDER_FORBIDDEN",
                status = HttpStatus.FORBIDDEN,
                policy = "SYNTHETIC_ONLY_NOTIFICATION_PROVIDER",
                message = "notification templates must stay synthetic-only",
                cause = "Notification Service must not configure real provider behavior.",
                fix = "Set syntheticOnly=true and use a SYNTHETIC_*_SINK provider kind."
            )
        }
        if (request.version <= 0) {
            throw notificationError(
                code = "NOTIFICATION_TEMPLATE_VERSION_INVALID",
                status = HttpStatus.BAD_REQUEST,
                invariant = "notification template versions must be positive",
                message = "template version must be positive",
                cause = "Template activation needs an ordered synthetic version.",
                fix = "Submit version greater than zero."
            )
        }
        val channel = request.channel.uppercase()
        val providerKind = request.providerKind.uppercase()
        val expectedProvider = providerByChannel[channel] ?: throw notificationError(
            code = "NOTIFICATION_TEMPLATE_CHANNEL_INVALID",
            status = HttpStatus.BAD_REQUEST,
            message = "notification channel is not supported",
            cause = "Notification Service supports only synthetic SMS, EMAIL, PUSH, and CHAT channels.",
            fix = "Use one of ${providerByChannel.keys.sorted().joinToString(", ")}.",
            details = mapOf("channel" to request.channel)
        )
        if (providerKind != expectedProvider) {
            throw notificationError(
                code = "NOTIFICATION_REAL_PROVIDER_FORBIDDEN",
                status = HttpStatus.FORBIDDEN,
                policy = "SYNTHETIC_ONLY_NOTIFICATION_PROVIDER",
                message = "provider kind must match the synthetic channel sink",
                cause = "Notification templates can activate only provider sinks owned by the synthetic lab.",
                fix = "Use $expectedProvider for channel $channel.",
                details = mapOf("channel" to channel, "providerKind" to providerKind)
            )
        }
        if (containsRawSensitiveLiteral(request.bodyTemplate)) {
            throw notificationError(
                code = "NOTIFICATION_TEMPLATE_RAW_PII_FORBIDDEN",
                status = HttpStatus.FORBIDDEN,
                policy = "PII_MASKING_BY_DEFAULT",
                message = "template body must not contain raw account, phone, or email literals",
                cause = "Templates should render masked payload placeholders instead of embedding raw account or contact data.",
                fix = "Use placeholders such as {accountNo}, {phone}, or {email}; rendering will mask sensitive values."
            )
        }
    }

    private fun validateDecision(actor: String, role: String, reason: String) {
        requireNonBlank(actor, "reviewedBy")
        requireNonBlank(role, "reviewedByRole")
        requireReason(reason)
    }

    private fun requirePending(current: NotificationTemplateChangeRequestRecord) {
        if (current.status != NotificationTemplateChangeStatus.PENDING) {
            throw notificationError(
                code = "NOTIFICATION_TEMPLATE_CHANGE_NOT_PENDING",
                status = HttpStatus.CONFLICT,
                invariant = "template approval decisions are terminal",
                message = "template change request is not pending",
                cause = "Only pending template changes can be approved or rejected.",
                fix = "Submit a new template change request for another decision.",
                details = mapOf("status" to current.status.name)
            )
        }
    }

    private fun requireDifferentChecker(requestedBy: String, reviewedBy: String) {
        if (requestedBy == reviewedBy.trim()) {
            throw notificationError(
                code = "MAKER_CHECKER_SELF_APPROVAL_REJECTED",
                status = HttpStatus.CONFLICT,
                policy = "MAKER_CHECKER_SEPARATION_OF_DUTIES",
                message = "maker and checker must be different users",
                cause = "A notification template change cannot be approved or rejected by the same actor who requested it.",
                fix = "Have a different authorized checker review the template change.",
                details = mapOf("requestedBy" to requestedBy, "reviewedBy" to reviewedBy.trim())
            )
        }
    }

    private fun requireCheckerRole(role: String) {
        val normalizedRole = role.trim().uppercase()
        if (normalizedRole !in checkerRoles) {
            throw notificationError(
                code = "NOTIFICATION_TEMPLATE_CHECKER_ROLE_REQUIRED",
                status = HttpStatus.FORBIDDEN,
                policy = "NOTIFICATION_TEMPLATE_MAKER_CHECKER_ROLE_POLICY",
                message = "checker role is not allowed to decide notification template changes",
                cause = "Template changes that can expose sensitive data require an operations, compliance, or notification manager checker.",
                fix = "Retry with one of ${checkerRoles.sorted().joinToString(", ")}.",
                details = mapOf("role" to normalizedRole)
            )
        }
    }

    private fun requireReason(reason: String) {
        if (reason.isBlank()) {
            throw notificationError(
                code = "POLICY_REASON_REQUIRED",
                status = HttpStatus.BAD_REQUEST,
                policy = "REASON_REQUIRED_FOR_TEMPLATE_APPROVAL",
                message = "reason is required",
                cause = "Notification template administration is a sensitive operation and must carry a business reason.",
                fix = "Provide a concise synthetic business reason before retrying."
            )
        }
    }

    private fun requireNonBlank(value: String, field: String) {
        if (value.isBlank()) {
            throw notificationError(
                code = "NOTIFICATION_REQUIRED_FIELD_MISSING",
                status = HttpStatus.BAD_REQUEST,
                message = "$field is required",
                cause = "Notification template administration requires stable target, actor, role, and reason fields.",
                fix = "Populate $field before retrying the notification template command.",
                details = mapOf("field" to field)
            )
        }
    }

    private fun containsRawSensitiveLiteral(bodyTemplate: String): Boolean =
        Regex("""\b\d{2,3}-\d{3,4}-\d{4}\b""").containsMatchIn(bodyTemplate) ||
            Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""").containsMatchIn(bodyTemplate) ||
            Regex("""\b[A-Z]{2,8}-\d{2,8}-\d{2,8}\b""").containsMatchIn(bodyTemplate)

    private fun changeRequestNotFound(changeRequestId: String): NotificationDomainException =
        notificationError(
            code = "NOTIFICATION_TEMPLATE_CHANGE_NOT_FOUND",
            status = HttpStatus.NOT_FOUND,
            message = "notification template change request was not found",
            cause = "No durable template change request exists for the supplied id.",
            fix = "Check the template change request id before retrying.",
            details = mapOf("changeRequestId" to changeRequestId)
        )

    private fun NotificationTemplateRecord.toDto(): NotificationTemplateDto =
        NotificationTemplateDto(
            templateId = templateId,
            eventType = eventType,
            channel = channel,
            version = version,
            status = status,
            bodyTemplate = bodyTemplate,
            providerKind = providerKind,
            syntheticOnly = syntheticOnly,
            createdAt = createdAt
        )

    private fun NotificationTemplateChangeRequestRecord.toDto(): NotificationTemplateChangeRequestDto =
        NotificationTemplateChangeRequestDto(
            changeRequestId = changeRequestId,
            eventType = eventType,
            channel = channel,
            requestedVersion = requestedVersion,
            bodyTemplate = bodyTemplate,
            providerKind = providerKind,
            status = status,
            requestedBy = requestedBy,
            requestReason = requestReason,
            requestedAt = requestedAt,
            reviewedBy = reviewedBy,
            reviewedByRole = reviewedByRole,
            reviewedAt = reviewedAt,
            reviewReason = reviewReason,
            approvedTemplateId = approvedTemplateId,
            syntheticOnly = syntheticOnly
        )

    private fun notificationError(
        code: String,
        status: HttpStatus,
        invariant: String? = null,
        policy: String? = null,
        message: String,
        cause: String,
        fix: String,
        details: Map<String, Any?>? = null
    ): NotificationDomainException =
        NotificationDomainException(
            code = code,
            status = status,
            invariant = invariant,
            policy = policy,
            message = message,
            causeText = cause,
            fix = fix,
            details = details
        )

    private fun changeRequestId(): String = "NTCR-${UUID.randomUUID().toString().uppercase()}"

    private fun templateId(channel: String): String = "NTPL-${channel}-${UUID.randomUUID().toString().uppercase()}"
}
