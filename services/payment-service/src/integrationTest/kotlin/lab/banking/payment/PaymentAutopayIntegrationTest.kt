package lab.banking.payment

import java.time.LocalDate
import lab.banking.payment.domain.CancelAutopayAgreementRequest
import lab.banking.payment.domain.CreateAutopayAgreementRequest
import lab.banking.payment.domain.ExecuteDueAutopayRequest
import lab.banking.payment.domain.PauseAutopayAgreementRequest
import lab.banking.payment.domain.PaymentAutopayFrequency
import lab.banking.payment.domain.PaymentAutopayService
import lab.banking.payment.domain.PaymentAutopayStatus
import lab.banking.payment.domain.PaymentDomainException
import lab.banking.payment.domain.ResumeAutopayAgreementRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
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
class PaymentAutopayIntegrationTest {
    @Autowired
    lateinit var autopayService: PaymentAutopayService

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
    }

    @Test
    fun `autopay create pause resume and cancel are durable audited and idempotent`() {
        val created = autopayService.createAgreement(sampleCreate("PAY-APAY-CREATE-001"))
        val replayedCreate = autopayService.createAgreement(sampleCreate("PAY-APAY-CREATE-001"))

        assertEquals(false, created.replayed)
        assertEquals(true, replayedCreate.replayed)
        assertEquals(created.item.autopayAgreementId, replayedCreate.item.autopayAgreementId)
        assertEquals(PaymentAutopayStatus.ACTIVE, created.item.status)
        assertEquals(LocalDate.of(2026, 1, 31), created.item.nextRunOn)
        assertEquals(true, created.item.syntheticOnly)
        assertEquals(1, countRows("payment_autopay_agreements"))
        assertEquals(1, countRows("payment_autopay_status_history WHERE status = 'ACTIVE'"))

        val paused = autopayService.pauseAgreement(
            created.item.autopayAgreementId,
            PauseAutopayAgreementRequest(
                idempotencyKey = "PAY-APAY-PAUSE-001",
                requestedBy = "customer01",
                reason = "Synthetic customer pauses autopay"
            )
        )
        val pausedReplay = autopayService.pauseAgreement(
            created.item.autopayAgreementId,
            PauseAutopayAgreementRequest(
                idempotencyKey = "PAY-APAY-PAUSE-001",
                requestedBy = "customer01",
                reason = "Synthetic customer pauses autopay"
            )
        )

        assertEquals(PaymentAutopayStatus.PAUSED, paused.item.status)
        assertEquals(true, pausedReplay.replayed)
        assertEquals(1, countRows("payment_autopay_status_history WHERE status = 'PAUSED'"))

        val resumed = autopayService.resumeAgreement(
            created.item.autopayAgreementId,
            ResumeAutopayAgreementRequest(
                idempotencyKey = "PAY-APAY-RESUME-001",
                requestedBy = "customer01",
                reason = "Synthetic customer resumes autopay",
                nextRunOn = LocalDate.of(2026, 2, 28)
            )
        )
        assertEquals(PaymentAutopayStatus.ACTIVE, resumed.item.status)
        assertEquals(LocalDate.of(2026, 2, 28), resumed.item.nextRunOn)

        val canceled = autopayService.cancelAgreement(
            created.item.autopayAgreementId,
            CancelAutopayAgreementRequest(
                idempotencyKey = "PAY-APAY-CANCEL-001",
                requestedBy = "customer01",
                reason = "Synthetic customer cancels autopay"
            )
        )
        assertEquals(PaymentAutopayStatus.CANCELED, canceled.item.status)
        assertEquals(4, countRows("payment_autopay_status_history"))

        val resumeCanceled = assertThrows(PaymentDomainException::class.java) {
            autopayService.resumeAgreement(
                created.item.autopayAgreementId,
                ResumeAutopayAgreementRequest(
                    idempotencyKey = "PAY-APAY-RESUME-CANCELED",
                    requestedBy = "customer01",
                    reason = "Synthetic invalid resume after cancel"
                )
            )
        }
        assertEquals("PAYMENT_AUTOPAY_STATE_REJECTED", resumeCanceled.code)
    }

    @Test
    fun `due autopay execution creates payment instruction outbox and advances schedule idempotently`() {
        val created = autopayService.createAgreement(sampleCreate("PAY-APAY-EXEC-CREATE-001"))

        val executed = autopayService.executeDue(executeDue("PAY-APAY-EXEC-DUE-001", LocalDate.of(2026, 1, 31)))
        val replayed = autopayService.executeDue(executeDue("PAY-APAY-EXEC-DUE-001", LocalDate.of(2026, 1, 31)))
        val agreement = autopayService.agreement(created.item.autopayAgreementId)
        val execution = executed.items.single()

        assertEquals(false, executed.replayed)
        assertEquals(true, replayed.replayed)
        assertEquals(1, executed.executedCount)
        assertEquals(execution.autopayExecutionId, replayed.items.single().autopayExecutionId)
        assertEquals(created.item.autopayAgreementId, execution.autopayAgreementId)
        assertEquals(LocalDate.of(2026, 1, 31), execution.scheduledRunOn)
        assertNotNull(execution.paymentInstructionId)
        assertEquals(PaymentAutopayStatus.ACTIVE, agreement.status)
        assertEquals(LocalDate.of(2026, 1, 31), agreement.lastRunOn)
        assertEquals(LocalDate.of(2026, 2, 28), agreement.nextRunOn)
        assertEquals(execution.paymentInstructionId, agreement.lastPaymentInstructionId)
        assertEquals(1, countRows("payment_instructions"))
        assertEquals(1, countRows("payment_outbox_events WHERE event_type = 'PaymentLedgerPostingRequested'"))
        assertEquals(1, countRows("payment_outbox_events WHERE event_type = 'PaymentAutopayExecutionCreated' AND aggregate_type = 'payment_autopay_execution'"))
        assertEquals(1, countRows("payment_autopay_executions WHERE status = 'INSTRUCTION_CREATED'"))
    }

    @Test
    fun `paused canceled and real-network autopay are not executed or configured`() {
        val paused = autopayService.createAgreement(sampleCreate("PAY-APAY-PAUSED-CREATE"))
        autopayService.pauseAgreement(
            paused.item.autopayAgreementId,
            PauseAutopayAgreementRequest(
                idempotencyKey = "PAY-APAY-PAUSED-CALL",
                requestedBy = "customer01",
                reason = "Synthetic customer pauses before due execution"
            )
        )
        val canceled = autopayService.createAgreement(sampleCreate("PAY-APAY-CANCELED-CREATE").copy(nextRunOn = LocalDate.of(2026, 1, 30)))
        autopayService.cancelAgreement(
            canceled.item.autopayAgreementId,
            CancelAutopayAgreementRequest(
                idempotencyKey = "PAY-APAY-CANCELED-CALL",
                requestedBy = "customer01",
                reason = "Synthetic customer cancels before due execution"
            )
        )

        val executed = autopayService.executeDue(executeDue("PAY-APAY-NONE-DUE", LocalDate.of(2026, 1, 31)))
        val forbidden = assertThrows(PaymentDomainException::class.java) {
            autopayService.createAgreement(sampleCreate("PAY-APAY-REAL-NETWORK").copy(billerId = "REAL-BILLER-001"))
        }

        assertEquals(0, executed.executedCount)
        assertEquals(0, countRows("payment_autopay_executions"))
        assertEquals(0, countRows("payment_instructions"))
        assertEquals("PAYMENT_SYNTHETIC_BILLER_REQUIRED", forbidden.code)
    }

    private fun sampleCreate(idempotencyKey: String): CreateAutopayAgreementRequest =
        CreateAutopayAgreementRequest(
            customerId = "CUS-PAY-001",
            debitAccountId = "ACC-PAY-001",
            billerId = "SYN-BILLER-UTIL-001",
            amountMinor = 45_000,
            currency = "KRW",
            frequency = PaymentAutopayFrequency.MONTHLY,
            nextRunOn = LocalDate.of(2026, 1, 31),
            idempotencyKey = idempotencyKey,
            requestedBy = "customer01",
            requestedChannel = "CUSTOMER_WEB",
            reason = "Synthetic utility bill autopay"
        )

    private fun executeDue(idempotencyKey: String, businessDate: LocalDate): ExecuteDueAutopayRequest =
        ExecuteDueAutopayRequest(
            businessDate = businessDate,
            idempotencyKey = idempotencyKey,
            requestedBy = "payment-service",
            reason = "Synthetic autopay due execution",
            limit = 10
        )

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $tableExpression", emptyMap<String, Any?>(), Int::class.java) ?: 0

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
