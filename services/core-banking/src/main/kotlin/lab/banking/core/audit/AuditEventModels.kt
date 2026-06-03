package lab.banking.core.audit

import java.time.OffsetDateTime

data class AuditEventDto(
    val auditEventId: String,
    val eventType: String,
    val actorType: String,
    val actorId: String,
    val actorRole: String,
    val screenId: String?,
    val businessReferenceId: String?,
    val customerId: String?,
    val accountId: String?,
    val reason: String?,
    val payloadHash: String,
    val previousEventHash: String?,
    val createdAt: OffsetDateTime
)

data class AuditEventListResponse(
    val hashChainValid: Boolean,
    val items: List<AuditEventDto>
)
