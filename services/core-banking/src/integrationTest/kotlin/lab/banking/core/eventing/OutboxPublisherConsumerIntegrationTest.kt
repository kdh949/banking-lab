package lab.banking.core.eventing

import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers
class OutboxPublisherConsumerIntegrationTest {
    @Autowired
    lateinit var outboxService: DurableOutboxService

    @Autowired
    lateinit var outboxRepository: OutboxEventRepository

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              inbox_events,
              outbox_events
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
    }

    @Test
    fun `pending outbox event can be marked published durably`() {
        val pending = outboxService.enqueue(outboxCommand("TX-EVT-PUBLISHED", "IDEMP-EVT-PUBLISHED"))

        val published = outboxService.markPublished(pending.outboxEventId)
        val reloaded = outboxService.event(pending.outboxEventId)

        assertEquals(OutboxEventStatus.PENDING, pending.status)
        assertEquals(OutboxEventStatus.PUBLISHED, published.status)
        assertEquals(OutboxEventStatus.PUBLISHED, reloaded.status)
        assertNotNull(reloaded.publishedAt)
        assertNull(reloaded.errorMessage)
    }

    @Test
    fun `duplicate inbox event is idempotent per consumer and source event`() {
        val sourceEventId = "OBX-DUPLICATE-001"
        val first = outboxService.recordInboxProcessed(
            consumerName = "ledger-projection",
            sourceEventId = sourceEventId,
            eventType = "LedgerTransactionPosted",
            payload = mapOf("ledgerTransactionId" to "TX-DUP-001")
        )
        val duplicate = outboxService.recordInboxProcessed(
            consumerName = "ledger-projection",
            sourceEventId = sourceEventId,
            eventType = "LedgerTransactionPosted",
            payload = mapOf("ledgerTransactionId" to "TX-DUP-001")
        )

        assertEquals(true, first.processedNow)
        assertEquals(false, duplicate.processedNow)
        assertEquals(1, outboxRepository.countInboxRows("ledger-projection", sourceEventId))
    }

    @Test
    fun `outbox retry increments then moves to dead letter at threshold`() {
        val pending = outboxService.enqueue(outboxCommand("TX-EVT-FAILED", "IDEMP-EVT-FAILED"))

        val firstFailure = outboxService.recordPublishFailure(
            outboxEventId = pending.outboxEventId,
            errorMessage = "synthetic broker unavailable",
            deadLetterThreshold = 3,
            retryDelaySeconds = 1
        )
        val secondFailure = outboxService.recordPublishFailure(
            outboxEventId = pending.outboxEventId,
            errorMessage = "synthetic broker still unavailable",
            deadLetterThreshold = 3,
            retryDelaySeconds = 1
        )
        val deadLetter = outboxService.recordPublishFailure(
            outboxEventId = pending.outboxEventId,
            errorMessage = "synthetic broker threshold exceeded",
            deadLetterThreshold = 3,
            retryDelaySeconds = 1
        )

        assertEquals(OutboxEventStatus.FAILED, firstFailure.status)
        assertEquals(1, firstFailure.retryCount)
        assertNotNull(firstFailure.nextRetryAt)
        assertEquals(OutboxEventStatus.FAILED, secondFailure.status)
        assertEquals(2, secondFailure.retryCount)
        assertEquals(OutboxEventStatus.DEAD_LETTER, deadLetter.status)
        assertEquals(3, deadLetter.retryCount)
        assertNull(deadLetter.nextRetryAt)
    }

    private fun outboxCommand(aggregateId: String, idempotencyKey: String): CreateOutboxEventCommand =
        CreateOutboxEventCommand(
            aggregateType = "LedgerTransaction",
            aggregateId = aggregateId,
            eventType = "LedgerTransactionPosted",
            idempotencyKey = idempotencyKey,
            payload = mapOf("ledgerTransactionId" to aggregateId, "syntheticOnly" to true)
        )

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.locations") {
                val userDir = Paths.get(System.getProperty("user.dir"))
                listOf(
                    "filesystem:${userDir.resolve("db/migrations").normalize()}",
                    "filesystem:${userDir.resolve("../../db/migrations").normalize()}"
                ).joinToString(",")
            }
        }
    }
}
