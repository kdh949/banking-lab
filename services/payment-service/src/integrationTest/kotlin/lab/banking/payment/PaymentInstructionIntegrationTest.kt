package lab.banking.payment

import lab.banking.payment.domain.CancelPaymentInstructionRequest
import lab.banking.payment.domain.CreatePaymentInstructionRequest
import lab.banking.payment.domain.PaymentDomainException
import lab.banking.payment.domain.PaymentInstructionService
import lab.banking.payment.domain.PaymentInstructionStatus
import lab.banking.payment.domain.RecordPaymentSettlementRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
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
class PaymentInstructionIntegrationTest {
    @Autowired
    lateinit var paymentInstructionService: PaymentInstructionService

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              payment_outbox_events,
              payment_status_history,
              payment_attempts,
              payment_idempotency_keys,
              payment_instructions
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
    }

    @Test
    fun `payment instruction persists state and durable ledger command outbox without ledger table writes`() {
        val response = paymentInstructionService.createInstruction(sampleCreate("PAY-IT-CREATE-001"))

        assertEquals(false, response.replayed)
        assertEquals(PaymentInstructionStatus.POSTING_REQUESTED, response.item.status)
        assertEquals(true, response.item.syntheticOnly)
        assertNotNull(response.item.lastOutboxEventId)
        assertEquals(1, countRows("payment_instructions WHERE payment_instruction_id = '${response.item.paymentInstructionId}'"))
        assertEquals(1, countRows("payment_attempts WHERE payment_instruction_id = '${response.item.paymentInstructionId}'"))
        assertEquals(1, countRows("payment_status_history WHERE payment_instruction_id = '${response.item.paymentInstructionId}'"))
        assertEquals(1, countRows("payment_idempotency_keys WHERE idempotency_key = 'PAY-IT-CREATE-001'"))
        assertEquals(1, countRows("payment_outbox_events WHERE event_type = 'PaymentLedgerPostingRequested'"))
        assertEquals(0, countRows("information_schema.tables WHERE table_name = 'ledger_transactions'"))

        val payload = scalarText(
            """
            SELECT payload_json::text
            FROM payment_outbox_events
            WHERE aggregate_id = :instructionId
              AND event_type = 'PaymentLedgerPostingRequested'
            """.trimIndent(),
            mapOf("instructionId" to response.item.paymentInstructionId)
        )
        assertTrue(payload.contains("\"syntheticOnly\": true"))
        assertTrue(payload.contains("\"directLedgerWrite\": false"))
        assertTrue(payload.contains("\"realPaymentNetworkUsed\": false"))
        assertTrue(payload.contains("\"ledgerCommandContract\": \"core-banking.ledger.posting-request.v1\""))
    }

    @Test
    fun `payment instruction idempotency replays same result and rejects conflicting payload`() {
        val first = paymentInstructionService.createInstruction(sampleCreate("PAY-IT-IDEMP-001"))
        val second = paymentInstructionService.createInstruction(sampleCreate("PAY-IT-IDEMP-001"))

        assertEquals(true, second.replayed)
        assertEquals(first.item.paymentInstructionId, second.item.paymentInstructionId)
        assertEquals(1, countRows("payment_instructions"))
        assertEquals(1, countRows("payment_outbox_events WHERE event_type = 'PaymentLedgerPostingRequested'"))

        val conflict = assertThrows(PaymentDomainException::class.java) {
            paymentInstructionService.createInstruction(
                sampleCreate("PAY-IT-IDEMP-001").copy(amountMinor = 99_000)
            )
        }
        assertEquals("PAYMENT_IDEMPOTENCY_CONFLICT", conflict.code)
    }

    @Test
    fun `settlement and cancellation are idempotent audited state transitions with synthetic-only billers`() {
        val forbidden = assertThrows(PaymentDomainException::class.java) {
            paymentInstructionService.createInstruction(sampleCreate("PAY-IT-REAL-001").copy(billerId = "REAL-BILLER-001"))
        }
        assertEquals("PAYMENT_SYNTHETIC_BILLER_REQUIRED", forbidden.code)

        val settledInstruction = paymentInstructionService.createInstruction(sampleCreate("PAY-IT-SETTLE-001"))
        val settled = paymentInstructionService.recordSettlement(
            settledInstruction.item.paymentInstructionId,
            RecordPaymentSettlementRequest(
                ledgerTransactionId = "TX-PAYMENT-SYN-001",
                idempotencyKey = "PAY-IT-SETTLE-CALLBACK-001",
                requestedBy = "core-banking-ledger",
                reason = "Synthetic core-banking ledger settlement callback"
            )
        )
        val replayedSettlement = paymentInstructionService.recordSettlement(
            settledInstruction.item.paymentInstructionId,
            RecordPaymentSettlementRequest(
                ledgerTransactionId = "TX-PAYMENT-SYN-001",
                idempotencyKey = "PAY-IT-SETTLE-CALLBACK-001",
                requestedBy = "core-banking-ledger",
                reason = "Synthetic core-banking ledger settlement callback"
            )
        )

        assertEquals(PaymentInstructionStatus.SETTLED, settled.item.status)
        assertEquals("TX-PAYMENT-SYN-001", settled.item.ledgerTransactionId)
        assertEquals(true, replayedSettlement.replayed)
        assertEquals(1, countRows("payment_outbox_events WHERE event_type = 'PaymentInstructionSettled'"))

        val cancelRejected = assertThrows(PaymentDomainException::class.java) {
            paymentInstructionService.cancelInstruction(
                settledInstruction.item.paymentInstructionId,
                CancelPaymentInstructionRequest(
                    idempotencyKey = "PAY-IT-CANCEL-SETTLED-001",
                    requestedBy = "customer01",
                    reason = "Synthetic cancellation attempt after settlement"
                )
            )
        }
        assertEquals("PAYMENT_STATE_TRANSITION_REJECTED", cancelRejected.code)

        val cancellable = paymentInstructionService.createInstruction(sampleCreate("PAY-IT-CANCEL-001"))
        val canceled = paymentInstructionService.cancelInstruction(
            cancellable.item.paymentInstructionId,
            CancelPaymentInstructionRequest(
                idempotencyKey = "PAY-IT-CANCEL-CALL-001",
                requestedBy = "customer01",
                reason = "Synthetic customer canceled before ledger settlement"
            )
        )
        val canceledReplay = paymentInstructionService.cancelInstruction(
            cancellable.item.paymentInstructionId,
            CancelPaymentInstructionRequest(
                idempotencyKey = "PAY-IT-CANCEL-CALL-001",
                requestedBy = "customer01",
                reason = "Synthetic customer canceled before ledger settlement"
            )
        )

        assertEquals(PaymentInstructionStatus.CANCELED, canceled.item.status)
        assertEquals(true, canceledReplay.replayed)
        assertEquals(1, countRows("payment_attempts WHERE payment_instruction_id = '${cancellable.item.paymentInstructionId}' AND status = 'CANCELED'"))
        assertEquals(1, countRows("payment_outbox_events WHERE event_type = 'PaymentInstructionCanceled'"))
        assertEquals(2, countRows("payment_status_history WHERE status = 'CANCELED' OR status = 'SETTLED'"))
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

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $tableExpression", emptyMap<String, Any?>(), Int::class.java) ?: 0

    private fun scalarText(sql: String, params: Map<String, Any?>): String =
        jdbc.queryForObject(sql, params, String::class.java) ?: ""

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
