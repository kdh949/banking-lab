package lab.banking.core.eventing

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component

@Component
class OutboxBacklogMetrics(
    private val jdbc: NamedParameterJdbcTemplate,
    registry: MeterRegistry
) {
    init {
        Gauge
            .builder("banking.lab.outbox.pending.count", this) { it.countByStatus("PENDING") }
            .description("Durable outbox rows waiting for publication")
            .register(registry)
        Gauge
            .builder("banking.lab.outbox.dead.letter.count", this) { it.countByStatus("DEAD_LETTER") }
            .description("Durable outbox rows in terminal dead-letter state")
            .register(registry)
    }

    fun countByStatus(status: String): Double =
        try {
            (jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE status = :status",
                mapOf("status" to status),
                Long::class.java
            ) ?: 0L).toDouble()
        } catch (_: RuntimeException) {
            0.0
        }
}
