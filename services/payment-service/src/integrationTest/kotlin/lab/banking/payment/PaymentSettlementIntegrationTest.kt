package lab.banking.payment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
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
        "banking-lab.security.dev-simulator-token-enabled=true"
    ]
)
@AutoConfigureMockMvc
@Testcontainers
class PaymentSettlementIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @BeforeEach
    fun resetSettlementTables() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              payment_settlement_batch_items,
              payment_settlement_batches,
              payment_settlement_batch_runs,
              payment_external_settlement_lines,
              payment_external_settlement_imports
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
    }

    @Test
    fun `independent CSV import is deduplicated and produces fee VAT net batches`() {
        val csvContent = resourceText("/external-settlement/accepted-clearing.csv")
        val importRequest = importRequest(
            csvContent = csvContent,
            idempotencyKey = "SETTLEMENT-IMPORT-001",
            requestedBy = "settlement-ops01"
        )

        mockMvc.perform(
            post("/api/payments/settlement/imports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(importRequest)
        )
            .andExpect(status().isUnauthorized)

        mockMvc.perform(
            post("/api/payments/settlement/imports")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(importRequest)
        )
            .andExpect(status().isForbidden)

        mockMvc.perform(
            post("/api/payments/settlement/imports")
                .header("Authorization", bearer("settlement-ops01", listOf("OPS_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    importRequest(
                        csvContent = csvContent,
                        idempotencyKey = "SETTLEMENT-IMPORT-ACTOR-MISMATCH",
                        requestedBy = "spoofed-ops"
                    )
                )
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("PAYMENT_ACTOR_BINDING_MISMATCH"))

        val importedResult = mockMvc.perform(
            post("/api/payments/settlement/imports")
                .header("Authorization", bearer("settlement-ops01", listOf("OPS_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(importRequest)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.item.status").value("IMPORTED"))
            .andExpect(jsonPath("$.item.lineCount").value(4))
            .andExpect(jsonPath("$.item.acceptedLineCount").value(3))
            .andExpect(jsonPath("$.item.rejectedLineCount").value(1))
            .andReturn()

        val imported = objectMapper.readTree(importedResult.response.contentAsString)
        val importId = imported.at("/item/externalSettlementImportId").asText()
        val fileSha256 = imported.at("/item/fileSha256").asText()
        assertTrue(importId.startsWith("ESI-"))
        assertTrue(fileSha256.matches(Regex("^[0-9a-f]{64}$")))
        assertEquals(4, imported.at("/item/lines").size())
        assertEquals(
            "EXT-UTIL-001,PAY-EXT-UTIL-001,SYN-BILLER-UTIL-001,100000,KRW,2026-07-31,2026-08-01,ACCEPTED",
            imported.at("/item/lines/0/rawLine").asText()
        )
        assertEquals(0, countRows("payment_instructions"))
        assertEquals(1, countRows("payment_external_settlement_imports"))
        assertEquals(4, countRows("payment_external_settlement_lines"))

        mockMvc.perform(
            post("/api/payments/settlement/imports")
                .header("Authorization", bearer("settlement-ops01", listOf("OPS_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(importRequest)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replayed").value(true))
            .andExpect(jsonPath("$.item.externalSettlementImportId").value(importId))

        mockMvc.perform(
            post("/api/payments/settlement/imports")
                .header("Authorization", bearer("settlement-ops01", listOf("OPS_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    importRequest(
                        csvContent = csvContent,
                        idempotencyKey = "SETTLEMENT-IMPORT-DUPLICATE-001",
                        requestedBy = "settlement-ops01"
                    )
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("PAYMENT_EXTERNAL_SETTLEMENT_FILE_DUPLICATE"))

        mockMvc.perform(
            get("/api/payments/settlement/imports/$importId")
                .header("Authorization", bearer("audit01", listOf("AUDITOR")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.fileSha256").value(fileSha256))

        val batchRequest = batchRequest(
            importId = importId,
            idempotencyKey = "SETTLEMENT-BATCH-001",
            requestedBy = "settlement-ops01"
        )

        mockMvc.perform(
            post("/api/payments/settlement/batch-runs")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(batchRequest)
        )
            .andExpect(status().isForbidden)

        val batchResult = mockMvc.perform(
            post("/api/payments/settlement/batch-runs")
                .header("Authorization", bearer("settlement-ops01", listOf("OPS_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(batchRequest)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andReturn()

        val batchRun = objectMapper.readTree(batchResult.response.contentAsString)
        val batchRunId = batchRun.path("settlementBatchRunId").asText()
        assertTrue(batchRunId.startsWith("SBR-"))
        val batchesByBiller = batchRun.path("items").associateBy { it.path("billerId").asText() }

        assertBatch(
            batchesByBiller.getValue("SYN-BILLER-UTIL-001"),
            grossAmountMinor = 150_000,
            feeAmountMinor = 1_500,
            vatAmountMinor = 150,
            netAmountMinor = 148_350,
            itemCount = 2
        )
        assertBatch(
            batchesByBiller.getValue("SYN-BILLER-TELCO-001"),
            grossAmountMinor = 20_000,
            feeAmountMinor = 200,
            vatAmountMinor = 20,
            netAmountMinor = 19_780,
            itemCount = 1
        )
        assertFalse(batchesByBiller.containsKey("SYN-BILLER-TAX-001"))
        assertEquals(2, countRows("payment_settlement_batches"))
        assertEquals(3, countRows("payment_settlement_batch_items"))
        assertEquals(
            "BATCHED",
            scalarText(
                "SELECT status FROM payment_external_settlement_imports WHERE external_settlement_import_id = :importId",
                mapOf("importId" to importId)
            )
        )

        mockMvc.perform(
            post("/api/payments/settlement/batch-runs")
                .header("Authorization", bearer("settlement-ops01", listOf("OPS_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(batchRequest)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replayed").value(true))
            .andExpect(jsonPath("$.settlementBatchRunId").value(batchRunId))

        mockMvc.perform(
            post("/api/payments/settlement/batch-runs")
                .header("Authorization", bearer("settlement-ops01", listOf("OPS_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    batchRequest(
                        importId = importId,
                        idempotencyKey = "SETTLEMENT-BATCH-DUPLICATE-001",
                        requestedBy = "settlement-ops01"
                    )
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("PAYMENT_SETTLEMENT_IMPORT_ALREADY_BATCHED"))

        mockMvc.perform(
            get("/api/payments/settlement/batch-runs/$batchRunId")
                .header("Authorization", bearer("audit01", listOf("AUDITOR")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
    }

    @Test
    fun `invalid external CSV is rejected before staging`() {
        val invalidCsv = """
            external_reference,payment_instruction_id,biller_id,amount_minor,currency,business_date,value_date,status
            EXT-INVALID-001,PAY-INVALID-001,SYN-BILLER-UTIL-001,1000,KRW,2026-07-30,2026-07-31,ACCEPTED
        """.trimIndent()

        mockMvc.perform(
            post("/api/payments/settlement/imports")
                .header("Authorization", bearer("settlement-ops01", listOf("OPS_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    importRequest(
                        csvContent = invalidCsv,
                        idempotencyKey = "SETTLEMENT-IMPORT-INVALID-001",
                        requestedBy = "settlement-ops01"
                    )
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("PAYMENT_EXTERNAL_SETTLEMENT_CSV_INVALID"))
            .andExpect(jsonPath("$.error.details.lineNumber").value(2))
            .andExpect(jsonPath("$.error.details.field").value("business_date"))

        assertEquals(0, countRows("payment_external_settlement_imports"))
        assertEquals(0, countRows("payment_external_settlement_lines"))
    }

    private fun importRequest(
        csvContent: String,
        idempotencyKey: String,
        requestedBy: String
    ): String =
        objectMapper.writeValueAsString(
            mapOf(
                "originalFileName" to "synthetic-clearing-2026-07-31.csv",
                "institutionCode" to "SYN_CLEARING_HOUSE",
                "businessDate" to "2026-07-31",
                "csvContent" to csvContent,
                "idempotencyKey" to idempotencyKey,
                "requestedBy" to requestedBy,
                "reason" to "Independent synthetic external clearing file import"
            )
        )

    private fun batchRequest(
        importId: String,
        idempotencyKey: String,
        requestedBy: String
    ): String =
        objectMapper.writeValueAsString(
            mapOf(
                "externalSettlementImportId" to importId,
                "feeRateBps" to 100,
                "vatRateBps" to 1_000,
                "cutoffAt" to "2026-07-31T23:00:00+09:00",
                "idempotencyKey" to idempotencyKey,
                "requestedBy" to requestedBy,
                "reason" to "Create synthetic biller settlement positions"
            )
        )

    private fun assertBatch(
        batch: JsonNode,
        grossAmountMinor: Long,
        feeAmountMinor: Long,
        vatAmountMinor: Long,
        netAmountMinor: Long,
        itemCount: Int
    ) {
        assertEquals(grossAmountMinor, batch.path("grossAmountMinor").asLong())
        assertEquals(feeAmountMinor, batch.path("feeAmountMinor").asLong())
        assertEquals(vatAmountMinor, batch.path("vatAmountMinor").asLong())
        assertEquals(0L, batch.path("adjustmentAmountMinor").asLong())
        assertEquals(netAmountMinor, batch.path("netAmountMinor").asLong())
        assertEquals(itemCount, batch.path("itemCount").asInt())
        assertEquals("INCLUDED_IN_BATCH", batch.path("status").asText())
        assertTrue(batch.path("externalPayoutReference").isNull)
    }

    private fun bearer(subject: String, roles: List<String>): String {
        val payload = mapOf<String, Any>(
            "sub" to subject,
            "roles" to roles,
            "active" to true
        )
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(objectMapper.writeValueAsBytes(payload))
        return "Bearer lab.$encoded.sig"
    }

    private fun resourceText(path: String): String =
        requireNotNull(javaClass.getResource(path)) { "missing resource $path" }.readText()

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM $tableExpression",
            emptyMap<String, Any?>(),
            Int::class.java
        ) ?: 0

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
