package lab.banking.core.audit

import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.tracing.Tracer
import java.security.MessageDigest
import java.util.UUID
import lab.banking.core.observability.RequestCorrelation
import org.springframework.beans.factory.ObjectProvider
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

@Service
class AuditEventAppender(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val tracerProvider: ObjectProvider<Tracer>,
    registry: MeterRegistry
) {
    private val appendFailures = Counter
        .builder("banking.lab.audit.append.failure.count")
        .description("Audit event append failures before hash-chain persistence")
        .register(registry)

    fun append(
        eventType: String,
        actorType: String,
        actorId: String,
        actorRole: String,
        screenId: String?,
        businessReferenceId: String? = null,
        customerId: String? = null,
        accountId: String? = null,
        reason: String?,
        payload: Map<String, Any?>
    ): String {
        val auditEventId = "AUD-${UUID.randomUUID().toString().uppercase()}"
        val correlatedPayload = payload.toMutableMap()
        RequestCorrelation.currentRequestId()?.let { correlatedPayload.putIfAbsent("requestId", it) }
        val traceId = RequestCorrelation.currentTraceId()
            ?: tracerProvider.ifAvailable?.currentSpan()?.context()?.traceId()
                ?.takeIf { it.length == 32 && it.any { character -> character != '0' } }
        traceId?.let { correlatedPayload.putIfAbsent("traceId", it) }
        val payloadJson = objectMapper.writeValueAsString(correlatedPayload)
        val rows = try {
            jdbc.update(
                """
                WITH locked_chain AS (
                  SELECT lock_key
                  FROM audit_hash_chain_lock
                  WHERE lock_key = 'GLOBAL'
                  FOR UPDATE
                ),
                previous_event AS (
                  SELECT e.payload_hash
                  FROM audit_events e, locked_chain
                  ORDER BY e.created_at DESC, e.audit_event_id DESC
                  LIMIT 1
                )
                INSERT INTO audit_events (
                  audit_event_id, event_type, actor_type, actor_id, actor_role,
                  screen_id, business_reference_id, customer_id, account_id,
                  reason, payload_hash, previous_event_hash, payload_json
                )
                SELECT
                  :auditEventId, :eventType, :actorType, :actorId, :actorRole,
                  :screenId, :businessReferenceId, :customerId, :accountId,
                  :reason, :payloadHash,
                  (SELECT payload_hash FROM previous_event),
                  CAST(:payloadJson AS jsonb)
                FROM locked_chain
                """.trimIndent(),
                mapOf(
                    "auditEventId" to auditEventId,
                    "eventType" to eventType,
                    "actorType" to actorType,
                    "actorId" to actorId,
                    "actorRole" to actorRole,
                    "screenId" to screenId,
                    "businessReferenceId" to businessReferenceId,
                    "customerId" to customerId,
                    "accountId" to accountId,
                    "reason" to reason,
                    "payloadHash" to sha256(payloadJson),
                    "payloadJson" to payloadJson
                )
            )
        } catch (error: RuntimeException) {
            appendFailures.increment()
            throw error
        }
        if (rows != 1) {
            appendFailures.increment()
            check(rows == 1) { "audit hash-chain lock row is missing" }
        }
        return auditEventId
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
