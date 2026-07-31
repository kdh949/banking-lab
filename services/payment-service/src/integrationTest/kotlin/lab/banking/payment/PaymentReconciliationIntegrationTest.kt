
package lab.banking.payment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Base64
import lab.banking.payment.core.CoreLedgerEvidenceClient
import lab.banking.payment.core.CorePaymentLedgerEvidence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(
    properties = [
        "banking-lab.security.enabled=true",
        "banking-lab.security.simulator-tokens-enabled=true",
        "banking-lab.security.dev-simulator-token-enabled=true",
        "banking-lab.payment-service.core-banking.http-enabled=false"
    ]
)
@AutoConfigureMockMvc
@Import(PaymentReconciliationIntegrationTest.EvidenceConfig::class)
@Testcontainers
class PaymentReconciliationIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @Autowired
    lateinit var evidenceClient: CapturingCoreLedgerEvidenceClient

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              payment_reconciliation_access_audit_events,
              payment_reconciliation_results,
              payment_reconciliation_runs,
              payment_settlement_batch_items,
              payment_settlement_batches,
              payment_settlement_batch_runs,
              payment_external_settlement_lines,
              payment_external_settlement_imports,
              payment_cancellation_requests,
              payment_access_audit_events,
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
        seedPaymentInstructions()
        evidenceClient.reset(evidence())
    }

    @Test
    fun `three-way reconciliation classifies durable exceptions with owner SLA aging and audited reads`() {
        val importId = importExternalFile()
        val request = reconciliationRequest(importId, requestedBy = "ops-recon01")

        mockMvc.perform(
            post("/api/payments/reconciliation/runs")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isForbidden)

        mockMvc.perform(
            post("/api/payments/reconciliation/runs")
                .header("Authorization", bearer("ops-recon01", listOf("OPS_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reconciliationRequest(importId, requestedBy = "spoofed-ops")))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("PAYMENT_ACTOR_BINDING_MISMATCH"))

        val created = mockMvc.perform(
            post("/api/payments/reconciliation/runs")
                .header("Authorization", bearer("ops-recon01", listOf("OPS_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.status").value("EXCEPTIONS_OPEN"))
            .andExpect(jsonPath("$.item.externalLineCount").value(9))
            .andExpect(jsonPath("$.item.ledgerEvidenceCount").value(7))
            .andExpect(jsonPath("$.item.matchedCount").value(1))
            .andExpect(jsonPath("$.item.exceptionCount").value(9))
            .andExpect(jsonPath("$.results.length()").value(10))
            .andReturn()

        val createdJson = objectMapper.readTree(created.response.contentAsString)
        val runId = createdJson.at("/item/paymentReconciliationRunId").asText()
        assertTrue(runId.startsWith("PRR-"))
        val mismatchCounts = createdJson.path("results")
            .groupingBy { it.path("mismatchType").asText() }
            .eachCount()
        assertEquals(1, mismatchCounts["MATCHED"])
        assertEquals(1, mismatchCounts["MISSING_PAYMENT"])
        assertEquals(1, mismatchCounts["MISSING_LEDGER"])
        assertEquals(1, mismatchCounts["MISSING_EXTERNAL"])
        assertEquals(1, mismatchCounts["AMOUNT_MISMATCH"])
        assertEquals(1, mismatchCounts["STATUS_MISMATCH"])
        assertEquals(2, mismatchCounts["DUPLICATE_EXTERNAL"])
        assertEquals(1, mismatchCounts["VALUE_DATE_MISMATCH"])
        assertEquals(1, mismatchCounts["LATE_SETTLEMENT"])

        mockMvc.perform(
            post("/api/payments/reconciliation/runs")
                .header("Authorization", bearer("ops-recon01", listOf("OPS_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replayed").value(true))
            .andExpect(jsonPath("$.item.paymentReconciliationRunId").value(runId))

        mockMvc.perform(
            get("/api/payments/reconciliation/runs/$runId")
                .header("Authorization", bearer("audit01", listOf("AUDITOR")))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("PAYMENT_RECONCILIATION_READ_REASON_REQUIRED"))

        mockMvc.perform(
            get("/api/payments/reconciliation/runs/$runId")
                .header("Authorization", bearer("audit01", listOf("AUDITOR")))
                .queryParam("reason", "Quarter-end reconciliation evidence review")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.auditEventId").value(org.hamcrest.Matchers.startsWith("PRA-")))
            .andExpect(jsonPath("$.results[0].dueAt").exists())

        mockMvc.perform(
            get("/api/payments/reconciliation/exceptions")
                .header("Authorization", bearer("audit01", listOf("AUDITOR")))
                .queryParam("ownerId", "ops-owner01")
                .queryParam("status", "OPEN")
                .queryParam("reason", "Open settlement exception queue review")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(9))
            .andExpect(jsonPath("$.auditEventId").value(org.hamcrest.Matchers.startsWith("PRA-")))
            .andExpect(jsonPath("$.items[0].agingDays").isNumber)
            .andExpect(jsonPath("$.items[0].overdue").isBoolean)

        assertEquals(
            2,
            countRows("payment_reconciliation_access_audit_events")
        )
        assertEquals(10, countRows("payment_reconciliation_results"))
        assertEquals(1, evidenceClient.calls)
        assertEquals("Three-way synthetic settlement reconciliation", evidenceClient.lastReason)
    }

    private fun importExternalFile(): String {
        val csv = listOf(
            "external_reference,payment_instruction_id,biller_id,amount_minor,currency,business_date,value_date,status",
            "EXT-MATCH,PAY-REC-MATCH,SYN-BILLER-UTIL-001,100000,KRW,2026-07-30,2026-07-31,ACCEPTED",
            "EXT-NO-LEDGER,PAY-REC-NO-LEDGER,SYN-BILLER-UTIL-001,200000,KRW,2026-07-30,2026-07-31,ACCEPTED",
            "EXT-AMOUNT,PAY-REC-AMOUNT,SYN-BILLER-UTIL-001,350000,KRW,2026-07-30,2026-07-31,ACCEPTED",
            "EXT-STATUS,PAY-REC-STATUS,SYN-BILLER-UTIL-001,400000,KRW,2026-07-30,2026-07-31,REJECTED",
            "EXT-DUP-1,PAY-REC-DUP,SYN-BILLER-UTIL-001,500000,KRW,2026-07-30,2026-07-31,ACCEPTED",
            "EXT-DUP-2,PAY-REC-DUP,SYN-BILLER-UTIL-001,500000,KRW,2026-07-30,2026-07-31,ACCEPTED",
            "EXT-LATE,PAY-REC-LATE,SYN-BILLER-UTIL-001,600000,KRW,2026-07-30,2026-08-03,ACCEPTED",
            "EXT-VALUE,PAY-REC-VALUE,SYN-BILLER-UTIL-001,650000,KRW,2026-07-30,2026-07-30,ACCEPTED",
            "EXT-NO-PAYMENT,PAY-REC-NO-PAYMENT,SYN-BILLER-UTIL-001,800000,KRW,2026-07-30,2026-07-31,ACCEPTED"
        ).joinToString("\n")
        val body = mapOf(
            "originalFileName" to "three-way-reconciliation.csv",
            "institutionCode" to "SYN-CLEAR-001",
            "businessDate" to "2026-07-30",
            "csvContent" to csv,
            "idempotencyKey" to "REC-IMPORT-001",
            "requestedBy" to "ops-recon01",
            "reason" to "Independent synthetic external clearing import"
        )
        val response = mockMvc.perform(
            post("/api/payments/settlement/imports")
                .header("Authorization", bearer("ops-recon01", listOf("OPS_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isCreated)
            .andReturn()
        return objectMapper.readTree(response.response.contentAsString)
            .at("/item/externalSettlementImportId")
            .asText()
    }

    private fun reconciliationRequest(importId: String, requestedBy: String): Map<String, Any> =
        mapOf(
            "externalSettlementImportId" to importId,
            "expectedValueDate" to "2026-07-31",
            "allowedValueDateLagDays" to 1,
            "slaDays" to 2,
            "ownerId" to "ops-owner01",
            "idempotencyKey" to "REC-RUN-001",
            "requestedBy" to requestedBy,
            "reason" to "Three-way synthetic settlement reconciliation"
        )

    private fun seedPaymentInstructions() {
        val rows = listOf(
            PaymentSeed("PAY-REC-MATCH", 100000, "LEDGER_POSTED", "TX-REC-MATCH"),
            PaymentSeed("PAY-REC-NO-LEDGER", 200000, "POSTING_REQUESTED", null),
            PaymentSeed("PAY-REC-AMOUNT", 300000, "LEDGER_POSTED", "TX-REC-AMOUNT"),
            PaymentSeed("PAY-REC-STATUS", 400000, "LEDGER_POSTED", "TX-REC-STATUS"),
            PaymentSeed("PAY-REC-DUP", 500000, "LEDGER_POSTED", "TX-REC-DUP"),
            PaymentSeed("PAY-REC-LATE", 600000, "LEDGER_POSTED", "TX-REC-LATE"),
            PaymentSeed("PAY-REC-VALUE", 650000, "LEDGER_POSTED", "TX-REC-VALUE"),
            PaymentSeed("PAY-REC-NO-EXTERNAL", 700000, "LEDGER_POSTED", "TX-REC-NO-EXTERNAL")
        )
        rows.forEach { row ->
            jdbc.update(
                """
                INSERT INTO payment_instructions (
                  payment_instruction_id, customer_id, debit_account_id, biller_id,
                  biller_name, amount_minor, currency, status,
                  ledger_transaction_id, synthetic_only
                ) VALUES (
                  :paymentInstructionId, 'CUS-REC-001', 'ACC-REC-001', 'SYN-BILLER-UTIL-001',
                  'Synthetic Utility Biller', :amountMinor, 'KRW', :status,
                  :ledgerTransactionId, true
                )
                """.trimIndent(),
                mapOf(
                    "paymentInstructionId" to row.paymentInstructionId,
                    "amountMinor" to row.amountMinor,
                    "status" to row.status,
                    "ledgerTransactionId" to row.ledgerTransactionId
                )
            )
        }
    }

    private fun evidence(): List<CorePaymentLedgerEvidence> =
        listOf(
            evidence("PAY-REC-MATCH", "TX-REC-MATCH", 100000),
            evidence("PAY-REC-AMOUNT", "TX-REC-AMOUNT", 300000),
            evidence("PAY-REC-STATUS", "TX-REC-STATUS", 400000),
            evidence("PAY-REC-DUP", "TX-REC-DUP", 500000),
            evidence("PAY-REC-LATE", "TX-REC-LATE", 600000),
            evidence("PAY-REC-VALUE", "TX-REC-VALUE", 650000),
            evidence("PAY-REC-NO-EXTERNAL", "TX-REC-NO-EXTERNAL", 700000)
        )

    private fun evidence(paymentId: String, transactionId: String, amount: Long): CorePaymentLedgerEvidence =
        CorePaymentLedgerEvidence(
            ledgerTransactionId = transactionId,
            paymentInstructionId = paymentId,
            businessDate = LocalDate.of(2026, 7, 30),
            transactionType = "BILL_PAYMENT",
            status = "POSTED",
            currency = "KRW",
            amountMinor = amount,
            totalDebitMinor = amount,
            totalCreditMinor = amount,
            postingCount = 2,
            balanced = true,
            postedAt = OffsetDateTime.parse("2026-07-30T12:00:00+09:00"),
            syntheticOnly = true
        )

    private fun bearer(subject: String, roles: List<String>): String {
        val payload = mapOf<String, Any>(
            "sub" to subject,
            "roles" to roles,
            "active" to true
        )
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(objectMapper.writeValueAsBytes(payload))
        return "Bearer lab.$encoded.sig"
    }

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM $tableExpression",
            emptyMap<String, Any?>(),
            Int::class.java
        ) ?: 0

    private data class PaymentSeed(
        val paymentInstructionId: String,
        val amountMinor: Long,
        val status: String,
        val ledgerTransactionId: String?
    )

    @TestConfiguration
    class EvidenceConfig {
        @Bean
        @Primary
        fun coreLedgerEvidenceClient(): CapturingCoreLedgerEvidenceClient =
            CapturingCoreLedgerEvidenceClient()
    }

    class CapturingCoreLedgerEvidenceClient : CoreLedgerEvidenceClient {
        private var items: List<CorePaymentLedgerEvidence> = emptyList()
        var calls: Int = 0
            private set
        var lastReason: String? = null
            private set

        override fun paymentPostings(
            businessDate: LocalDate,
            reason: String
        ): List<CorePaymentLedgerEvidence> {
            calls += 1
            lastReason = reason
            return items.filter { it.businessDate == businessDate }
        }

        fun reset(items: List<CorePaymentLedgerEvidence>) {
            this.items = items
            calls = 0
            lastReason = null
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
