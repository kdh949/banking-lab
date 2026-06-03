package lab.banking.core.audit

import java.sql.ResultSet
import java.time.OffsetDateTime
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuditEventService(
    private val jdbc: NamedParameterJdbcTemplate
) {
    @Transactional(readOnly = true)
    fun list(): AuditEventListResponse {
        val items = jdbc.query(
            """
            SELECT audit_event_id, event_type, actor_type, actor_id, actor_role,
                   screen_id, business_reference_id, customer_id, account_id,
                   reason, payload_hash, previous_event_hash, created_at
            FROM audit_events
            ORDER BY created_at, audit_event_id
            """.trimIndent(),
            emptyMap<String, Any?>(),
            this::mapEvent
        )
        return AuditEventListResponse(hashChainValid = hashChainValid(items), items = items)
    }

    private fun mapEvent(rs: ResultSet, rowNum: Int): AuditEventDto =
        AuditEventDto(
            auditEventId = rs.getString("audit_event_id"),
            eventType = rs.getString("event_type"),
            actorType = rs.getString("actor_type"),
            actorId = rs.getString("actor_id"),
            actorRole = rs.getString("actor_role"),
            screenId = rs.getString("screen_id"),
            businessReferenceId = rs.getString("business_reference_id"),
            customerId = rs.getString("customer_id"),
            accountId = rs.getString("account_id"),
            reason = rs.getString("reason"),
            payloadHash = rs.getString("payload_hash"),
            previousEventHash = rs.getString("previous_event_hash"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
        )

    private fun hashChainValid(items: List<AuditEventDto>): Boolean {
        var previousHash: String? = null
        for (item in items) {
            if (item.previousEventHash != previousHash) {
                return false
            }
            previousHash = item.payloadHash
        }
        return true
    }
}
