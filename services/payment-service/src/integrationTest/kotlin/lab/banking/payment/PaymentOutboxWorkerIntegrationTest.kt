package lab.banking.payment

import java.util.ArrayDeque
import lab.banking.payment.domain.CoreLedgerPaymentPostingCommand
import lab.banking.payment.domain.CoreLedgerPostingClient
import lab.banking.payment.domain.CoreLedgerPostingResult
import lab.banking.payment.domain.CreatePaymentInstructionRequest
import lab.banking.payment.domain.PaymentInstructionService
import lab.banking.payment.worker.PaymentOutboxWorker
import org.junit.jupiter.api.Assertions.assertEquals
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

@SpringBootTest(
    properties = [
        "banking-lab.payment-service.core-banking.http-enabled=false",
        "banking-lab.payment-service.outbox-worker.enabled=true",
        "banking-lab.payment-service.outbox-worker.initial-delay-ms=3600000",
        "banking-lab.payment-service.outbox-worker.max-batch-size=2",
        "banking-lab.payment-service.outbox-worker.dead-letter-threshold=3",
        "banking-lab.payment-service.outbox-worker.requested-by=payment-service-worker",
        "banking-lab.payment-service.outbox-worker.reason=Synthetic payment outbox worker integration dispatch"
    ]
)
@Testcontainers
class PaymentOutboxWorkerIntegrationTest {
    @Autowired
    lateinit var paymentInstructionService: PaymentInstructionService

    @Autowired
    lateinit var worker: PaymentOutboxWorker

    @Autowired
    lateinit var coreLedgerPostingClient: WorkerCoreLedgerPostingClient

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
    fun `worker dispatches due payment ledger posting outbox in bounded batches`() {
        val first = paymentInstructionService.createInstruction(sampleCreate("PAY-WORKER-CREATE-001"))
        val second = paymentInstructionService.createInstruction(sampleCreate("PAY-WORKER-CREATE-002"))
        val third = paymentInstructionService.createInstruction(sampleCreate("PAY-WORKER-CREATE-003"))
        coreLedgerPostingClient.results.addAll(
            listOf(
                "TX-PAY-WORKER-001",
                "TX-PAY-WORKER-002",
                "TX-PAY-WORKER-003"
            )
        )

        val firstRun = worker.dispatchBatch()
        val secondRun = worker.dispatchBatch()
        val emptyRun = worker.dispatchBatch()

        assertEquals(2, firstRun.attemptedCount)
        assertEquals(2, firstRun.publishedCount)
        assertEquals(false, firstRun.noPendingEvent)
        assertEquals(1, secondRun.attemptedCount)
        assertEquals(1, secondRun.publishedCount)
        assertEquals(true, secondRun.noPendingEvent)
        assertEquals(0, emptyRun.attemptedCount)
        assertEquals(true, emptyRun.noPendingEvent)
        assertEquals(3, coreLedgerPostingClient.commands.size)
        assertEquals("payment-service-worker", coreLedgerPostingClient.commands.first().requestedBy)
        assertEquals("TX-PAY-WORKER-001", paymentInstructionService.instruction(first.item.paymentInstructionId).ledgerTransactionId)
        assertEquals("TX-PAY-WORKER-002", paymentInstructionService.instruction(second.item.paymentInstructionId).ledgerTransactionId)
        assertEquals("TX-PAY-WORKER-003", paymentInstructionService.instruction(third.item.paymentInstructionId).ledgerTransactionId)
        assertEquals(3, countRows("payment_outbox_events WHERE event_type = 'PaymentLedgerPostingRequested' AND status = 'PUBLISHED'"))
        assertEquals(3, countRows("payment_instructions WHERE status = 'LEDGER_POSTED'"))
    }

    private fun sampleCreate(idempotencyKey: String): CreatePaymentInstructionRequest =
        CreatePaymentInstructionRequest(
            customerId = "CUS-PAY-WORKER-001",
            debitAccountId = "ACC-PAY-WORKER-001",
            billerId = "SYN-BILLER-UTIL-001",
            amountMinor = 45_000,
            currency = "KRW",
            idempotencyKey = idempotencyKey,
            requestedBy = "customer01",
            requestedChannel = "CUSTOMER_WEB",
            reason = "Synthetic utility bill payment for worker"
        )

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $tableExpression", emptyMap<String, Any?>(), Int::class.java) ?: 0

    @TestConfiguration
    class TestCoreLedgerPostingClientConfig {
        @Bean
        fun coreLedgerPostingClient(): WorkerCoreLedgerPostingClient = WorkerCoreLedgerPostingClient()
    }

    class WorkerCoreLedgerPostingClient : CoreLedgerPostingClient {
        val commands: MutableList<CoreLedgerPaymentPostingCommand> = mutableListOf()
        val results: ArrayDeque<String> = ArrayDeque()

        override fun postBillPayment(command: CoreLedgerPaymentPostingCommand): CoreLedgerPostingResult {
            commands += command
            val ledgerTransactionId = if (results.isEmpty()) "TX-PAY-WORKER-DEFAULT" else results.removeFirst()
            return CoreLedgerPostingResult(ledgerTransactionId)
        }

        fun reset() {
            commands.clear()
            results.clear()
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
