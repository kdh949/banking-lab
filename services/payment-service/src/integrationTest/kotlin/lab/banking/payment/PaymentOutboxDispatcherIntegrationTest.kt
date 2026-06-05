package lab.banking.payment

import lab.banking.payment.domain.CoreLedgerPaymentPostingCommand
import lab.banking.payment.domain.CoreLedgerPostingClient
import lab.banking.payment.domain.CoreLedgerPostingResult
import lab.banking.payment.domain.CreatePaymentInstructionRequest
import lab.banking.payment.domain.DispatchPaymentLedgerPostingRequest
import lab.banking.payment.domain.PaymentInstructionService
import lab.banking.payment.domain.PaymentInstructionStatus
import lab.banking.payment.domain.PaymentOutboxDispatcherService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(properties = ["banking-lab.payment-service.core-banking.http-enabled=false"])
@Testcontainers
class PaymentOutboxDispatcherIntegrationTest {
    @Autowired
    lateinit var paymentInstructionService: PaymentInstructionService

    @Autowired
    lateinit var dispatcherService: PaymentOutboxDispatcherService

    @Autowired
    lateinit var coreLedgerPostingClient: CapturingCoreLedgerPostingClient

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              payment_autopay_executions,
              payment_autopay_status_history,
              payment_autopay_agreements,
              payment_outbox_events,
              payment_status_history,
              payment_attempts,
              payment_idempotency_keys,
              payment_instructions
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
        coreLedgerPostingClient.reset()
    }

    @Test
    fun `dispatcher posts pending ledger outbox through core port and records settlement`() {
        coreLedgerPostingClient.nextResult = CoreLedgerPostingResult("TX-PAY-DISPATCH-001")
        val created = paymentInstructionService.createInstruction(sampleCreate("PAY-IT-DISPATCH-001"))

        val dispatched = dispatcherService.dispatchNextLedgerPosting(dispatchRequest())
        val replay = dispatcherService.dispatchNextLedgerPosting(dispatchRequest())
        val settled = paymentInstructionService.instruction(created.item.paymentInstructionId)
        val command = coreLedgerPostingClient.commands.single()

        assertEquals("PUBLISHED", dispatched.status)
        assertEquals(created.item.paymentInstructionId, dispatched.paymentInstructionId)
        assertEquals("TX-PAY-DISPATCH-001", dispatched.ledgerTransactionId)
        assertEquals("NO_PENDING_EVENT", replay.status)
        assertEquals(PaymentInstructionStatus.SETTLED, settled.status)
        assertEquals("TX-PAY-DISPATCH-001", settled.ledgerTransactionId)
        assertEquals(created.item.paymentInstructionId, command.paymentInstructionId)
        assertEquals("ACC-PAY-001", command.debitAccountId)
        assertEquals("SYN-BILLER-UTIL-001", command.syntheticBillerId)
        assertEquals(45_000, command.amountMinor)
        assertEquals("PAYMENT_SERVICE", command.requestedChannel)
        assertEquals("payment-service", command.requestedBy)
        assertEquals(1, countRows("payment_outbox_events WHERE event_type = 'PaymentLedgerPostingRequested' AND status = 'PUBLISHED'"))
        assertEquals(1, countRows("payment_outbox_events WHERE event_type = 'PaymentInstructionSettled' AND status = 'PENDING'"))
        assertEquals(1, countRows("payment_attempts WHERE payment_instruction_id = '${created.item.paymentInstructionId}' AND status = 'SETTLED'"))
    }

    @Test
    fun `dispatcher records failed retry and later settles the same outbox event with stable idempotency`() {
        val created = paymentInstructionService.createInstruction(sampleCreate("PAY-IT-DISPATCH-RETRY-001"))
        coreLedgerPostingClient.failOnce("synthetic core ledger unavailable")

        val failed = dispatcherService.dispatchNextLedgerPosting(dispatchRequest(deadLetterThreshold = 3))
        jdbc.update(
            """
            UPDATE payment_outbox_events
            SET next_retry_at = now() - interval '1 second'
            WHERE outbox_event_id = :outboxEventId
            """.trimIndent(),
            mapOf("outboxEventId" to failed.outboxEventId)
        )
        coreLedgerPostingClient.nextResult = CoreLedgerPostingResult("TX-PAY-DISPATCH-RETRY-001")
        val recovered = dispatcherService.dispatchNextLedgerPosting(dispatchRequest(deadLetterThreshold = 3))
        val settled = paymentInstructionService.instruction(created.item.paymentInstructionId)

        assertEquals("FAILED", failed.status)
        assertEquals(1, failed.retryCount)
        assertEquals("PUBLISHED", recovered.status)
        assertEquals(1, recovered.retryCount)
        assertEquals(PaymentInstructionStatus.SETTLED, settled.status)
        assertEquals("TX-PAY-DISPATCH-RETRY-001", settled.ledgerTransactionId)
        assertEquals(2, coreLedgerPostingClient.commands.size)
        assertEquals(coreLedgerPostingClient.commands[0].idempotencyKey, coreLedgerPostingClient.commands[1].idempotencyKey)
        assertEquals(1, countRows("payment_outbox_events WHERE event_type = 'PaymentLedgerPostingRequested' AND status = 'PUBLISHED' AND retry_count = 1"))
        assertEquals(1, countRows("payment_outbox_events WHERE event_type = 'PaymentInstructionFailed' AND status = 'PENDING'"))
        assertEquals(1, countRows("payment_outbox_events WHERE event_type = 'PaymentInstructionRetryScheduled' AND status = 'PENDING'"))
        assertEquals(1, countRows("payment_attempts WHERE payment_instruction_id = '${created.item.paymentInstructionId}' AND status = 'SETTLED'"))

        val failedPayload = payloadFor(created.item.paymentInstructionId, "PaymentInstructionFailed")
        assertContains(failedPayload, "\"status\": \"FAILED\"")
        assertContains(failedPayload, "\"retryable\": true")
        assertContains(failedPayload, "\"directLedgerWrite\": false")
        assertContains(failedPayload, "\"realPaymentNetworkUsed\": false")
        assertContains(failedPayload, "\"realFinancialInstitutionApiUsed\": false")
        val retryPayload = payloadFor(created.item.paymentInstructionId, "PaymentInstructionRetryScheduled")
        assertContains(retryPayload, "\"status\": \"RETRY_SCHEDULED\"")
        assertContains(retryPayload, "\"nextRetryAt\"")
    }

    @Test
    fun `dispatcher dead letters ledger outbox after threshold without settling instruction`() {
        val created = paymentInstructionService.createInstruction(sampleCreate("PAY-IT-DISPATCH-DEAD-001"))
        coreLedgerPostingClient.failOnce("synthetic core ledger hard failure")

        val failed = dispatcherService.dispatchNextLedgerPosting(dispatchRequest(deadLetterThreshold = 1))
        val current = paymentInstructionService.instruction(created.item.paymentInstructionId)

        assertEquals("DEAD_LETTER", failed.status)
        assertEquals(1, failed.retryCount)
        assertEquals(PaymentInstructionStatus.FAILED, current.status)
        assertNull(current.ledgerTransactionId)
        assertEquals(1, countRows("payment_outbox_events WHERE event_type = 'PaymentLedgerPostingRequested' AND status = 'DEAD_LETTER'"))
        assertEquals(0, countRows("payment_outbox_events WHERE event_type = 'PaymentInstructionSettled'"))
        assertEquals(1, countRows("payment_outbox_events WHERE event_type = 'PaymentInstructionDeadLettered' AND status = 'PENDING'"))
        assertEquals(1, countRows("payment_status_history WHERE payment_instruction_id = '${created.item.paymentInstructionId}' AND status = 'FAILED'"))
        assertEquals(1, countRows("payment_attempts WHERE payment_instruction_id = '${created.item.paymentInstructionId}' AND status = 'FAILED'"))

        val deadLetterPayload = payloadFor(created.item.paymentInstructionId, "PaymentInstructionDeadLettered")
        assertContains(deadLetterPayload, "\"status\": \"DEAD_LETTER\"")
        assertContains(deadLetterPayload, "\"retryable\": false")
        assertContains(deadLetterPayload, "\"deadLetterThreshold\": 1")
        assertContains(deadLetterPayload, "\"directLedgerWrite\": false")
        assertContains(deadLetterPayload, "\"realPaymentNetworkUsed\": false")
        assertContains(deadLetterPayload, "\"realFinancialInstitutionApiUsed\": false")
    }

    private fun sampleCreate(idempotencyKey: String): CreatePaymentInstructionRequest =
        CreatePaymentInstructionRequest(
            customerId = "CUS-PAY-001",
            debitAccountId = "ACC-PAY-001",
            billerId = "SYN-BILLER-UTIL-001",
            amountMinor = 45_000,
            currency = "KRW",
            idempotencyKey = idempotencyKey,
            requestedBy = "customer01",
            requestedChannel = "CUSTOMER_WEB",
            reason = "Synthetic utility bill payment"
        )

    private fun dispatchRequest(deadLetterThreshold: Int = 3): DispatchPaymentLedgerPostingRequest =
        DispatchPaymentLedgerPostingRequest(
            requestedBy = "payment-service",
            reason = "Synthetic payment-service outbox dispatch",
            deadLetterThreshold = deadLetterThreshold
        )

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $tableExpression", emptyMap<String, Any?>(), Int::class.java) ?: 0

    private fun payloadFor(instructionId: String, eventType: String): String =
        jdbc.queryForObject(
            """
            SELECT payload_json::text
            FROM payment_outbox_events
            WHERE aggregate_id = :instructionId
              AND event_type = :eventType
            ORDER BY created_at DESC
            LIMIT 1
            """.trimIndent(),
            mapOf("instructionId" to instructionId, "eventType" to eventType),
            String::class.java
        ) ?: error("missing payload for $eventType")

    private fun assertContains(actual: String, expected: String) {
        org.junit.jupiter.api.Assertions.assertTrue(
            actual.contains(expected),
            "Expected payload to contain <$expected> but was <$actual>"
        )
    }

    @TestConfiguration
    class TestCoreLedgerPostingClientConfig {
        @Bean
        fun coreLedgerPostingClient(): CapturingCoreLedgerPostingClient = CapturingCoreLedgerPostingClient()
    }

    class CapturingCoreLedgerPostingClient : CoreLedgerPostingClient {
        val commands: MutableList<CoreLedgerPaymentPostingCommand> = mutableListOf()
        var nextResult: CoreLedgerPostingResult = CoreLedgerPostingResult("TX-PAY-DISPATCH-DEFAULT")
        private var nextError: RuntimeException? = null

        override fun postBillPayment(command: CoreLedgerPaymentPostingCommand): CoreLedgerPostingResult {
            commands += command
            nextError?.let { error ->
                nextError = null
                throw error
            }
            return nextResult
        }

        fun failOnce(message: String) {
            nextError = IllegalStateException(message)
        }

        fun reset() {
            commands.clear()
            nextResult = CoreLedgerPostingResult("TX-PAY-DISPATCH-DEFAULT")
            nextError = null
        }
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
