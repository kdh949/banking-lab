package lab.banking.core.complaint

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Paths
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
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

@SpringBootTest(properties = ["banking-lab.security.enabled=true"])
@AutoConfigureMockMvc
@Testcontainers
class CustomerComplaintEntryApiParityIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              card_authorizations,
              card_limits,
              cards,
              customer_transfer_results,
              complaint_case_timeline,
              complaint_cases,
              audit_events,
              accounts,
              customers
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
        jdbc.update(
            """
            INSERT INTO customers (
              customer_id, customer_name, customer_phone, customer_address, customer_grade, risk_grade
            )
            VALUES
              (
                'SYN-CUS-001', 'Lab Customer Alpha', '010-0000-1001',
                'Seoul Synthetic District', 'STANDARD', 'LOW'
              ),
              (
                'SYN-CUS-002', 'Lab Customer Beta', '010-0000-1002',
                'Busan Synthetic District', 'STANDARD', 'LOW'
              )
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        seedDisputeSources()
    }

    @Test
    fun `customer and staff share the same complaint case with SLA and timeline`() {
        val response = mockMvc.perform(
            post("/api/customer/complaints")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "category": "ACCOUNT_ACCESS",
                      "description": "Synthetic complaint workflow test",
                      "reason": "Customer submitted complaint workflow parity"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.status").value("RECEIVED"))
            .andExpect(jsonPath("$.item.slaDueAt").exists())
            .andExpect(jsonPath("$.item.timeline[0].type").value("RECEIVED"))
            .andReturn()

        val created = objectMapper.readTree(response.response.contentAsString).path("item")
        val caseId = created.path("caseId").asText()

        val customerList = mockMvc.perform(
            get("/api/customer/complaints")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .queryParam("customerId", "SYN-CUS-001")
        )
            .andExpect(status().isOk)
            .andReturn()

        val staffList = mockMvc.perform(
            get("/api/staff/complaints")
                .header("Authorization", bearer("complaint01", listOf("COMPLAINT_HANDLER")))
        )
            .andExpect(status().isOk)
            .andReturn()

        val customerItems = objectMapper.readTree(customerList.response.contentAsString).path("items")
        val staffItems = objectMapper.readTree(staffList.response.contentAsString)
        assertEquals(true, customerItems.any { it.path("caseId").asText() == caseId })
        assertEquals(true, staffItems.any { it.path("caseId").asText() == caseId })
        assertEquals(true, customerItems.first { it.path("caseId").asText() == caseId }.path("slaDueAt").asText().isNotBlank())
        assertEquals("RECEIVED", customerItems.first { it.path("caseId").asText() == caseId }.path("timeline").first().path("type").asText())
        assertEquals(1, countRows("audit_events WHERE event_type = 'COMPLAINT_VIEW' AND actor_type = 'CUSTOMER' AND screen_id = 'CMP-102'"))
        assertEquals(0, countRows("audit_events WHERE event_type = 'COMPLAINT_VIEW' AND payload_json::text LIKE '%Synthetic complaint workflow test%'"))
    }

    @Test
    fun `customer complaint entry creates received case with timeline and audit`() {
        val response = mockMvc.perform(
            post("/api/customer/complaints")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "category": "ACCOUNT_ACCESS",
                      "description": "Cannot see synthetic account history.",
                      "reason": "Customer submitted complaint entry smoke"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.customerId").value("SYN-CUS-001"))
            .andExpect(jsonPath("$.item.category").value("ACCOUNT_ACCESS"))
            .andExpect(jsonPath("$.item.status").value("RECEIVED"))
            .andReturn()

        val caseId = objectMapper.readTree(response.response.contentAsString)
            .path("item")
            .path("caseId")
            .asText()

        assertEquals(1, countRows("complaint_cases WHERE complaint_case_id = '$caseId' AND status = 'RECEIVED'"))
        assertEquals(1, countRows("complaint_case_timeline WHERE complaint_case_id = '$caseId' AND event_type = 'RECEIVED'"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'COMMAND_REQUESTED' AND business_reference_id = '$caseId'"))
    }

    @Test
    fun `customer complaint entry links owned transfer and card dispute sources`() {
        val transferResponse = mockMvc.perform(
            post("/api/customer/complaints")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "category": "TRANSFER_DISPUTE",
                      "description": "Synthetic wrong-transfer dispute",
                      "sourceReference": {
                        "sourceType": "CUSTOMER_TRANSFER",
                        "sourceId": "TRR-DISP-001",
                        "accountId": "ACC-DISP-001",
                        "amountMinor": 72000,
                        "currency": "KRW",
                        "businessDate": "2026-06-05",
                        "syntheticOnly": true
                      },
                      "reason": "Customer disputes synthetic transfer source"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.category").value("TRANSFER_DISPUTE"))
            .andExpect(jsonPath("$.item.sourceReference.sourceType").value("CUSTOMER_TRANSFER"))
            .andExpect(jsonPath("$.item.sourceReference.sourceId").value("TRR-DISP-001"))
            .andExpect(jsonPath("$.item.sourceReference.accountId").value("ACC-DISP-001"))
            .andExpect(jsonPath("$.item.sourceReference.amountMinor").value(72000))
            .andExpect(jsonPath("$.item.sourceReference.syntheticOnly").value(true))
            .andReturn()

        val transferCaseId = objectMapper.readTree(transferResponse.response.contentAsString)
            .path("item")
            .path("caseId")
            .asText()

        mockMvc.perform(
            post("/api/customer/complaints")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "category": "CARD_DISPUTE",
                      "description": "Synthetic card authorization dispute",
                      "sourceReference": {
                        "sourceType": "CARD_AUTHORIZATION",
                        "sourceId": "CAUTH-DISP-001",
                        "accountId": "ACC-DISP-001",
                        "cardId": "CARD-DISP-001",
                        "amountMinor": 31000,
                        "currency": "KRW",
                        "businessDate": "2026-06-05",
                        "syntheticOnly": true
                      },
                      "reason": "Customer disputes synthetic card authorization"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.category").value("CARD_DISPUTE"))
            .andExpect(jsonPath("$.item.sourceReference.sourceType").value("CARD_AUTHORIZATION"))
            .andExpect(jsonPath("$.item.sourceReference.sourceId").value("CAUTH-DISP-001"))
            .andExpect(jsonPath("$.item.sourceReference.cardId").value("CARD-DISP-001"))

        mockMvc.perform(
            get("/api/customer/complaints")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .queryParam("customerId", "SYN-CUS-001")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[?(@.caseId == '$transferCaseId')].sourceReference.sourceId").value("TRR-DISP-001"))

        assertEquals(1, countRows("complaint_cases WHERE complaint_case_id = '$transferCaseId' AND source_reference_json->>'sourceId' = 'TRR-DISP-001'"))
        assertEquals(1, countRows("audit_events WHERE business_reference_id = '$transferCaseId' AND payload_json->'sourceReference'->>'sourceId' = 'TRR-DISP-001'"))
        assertEquals(0, countRows("audit_events WHERE payload_json::text LIKE '%Synthetic wrong-transfer dispute%'"))
    }

    @Test
    fun `customer complaint entry rejects invalid body and customer ownership mismatch`() {
        mockMvc.perform(
            post("/api/customer/complaints")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .header("x-request-id", "REQ-CWB-COMPLAINT-VALIDATION")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "category": "ACCOUNT_ACCESS",
                      "description": ""
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("REQUEST_VALIDATION_FAILED"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-CWB-COMPLAINT-VALIDATION"))

        mockMvc.perform(
            post("/api/customer/complaints")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .header("x-request-id", "REQ-CWB-COMPLAINT-OWNERSHIP")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-002",
                      "category": "ACCOUNT_ACCESS",
                      "description": "Attempt another customer complaint."
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-CWB-COMPLAINT-OWNERSHIP"))

        assertEquals(0, countRows("complaint_cases"))
    }

    @Test
    fun `customer complaint entry rejects missing or unauthorized dispute source references`() {
        mockMvc.perform(
            post("/api/customer/complaints")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .header("x-request-id", "REQ-CWB-DISPUTE-SOURCE-MISSING")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "category": "TRANSFER_DISPUTE",
                      "description": "Missing source reference"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("REQUEST_VALIDATION_FAILED"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-CWB-DISPUTE-SOURCE-MISSING"))

        mockMvc.perform(
            post("/api/customer/complaints")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .header("x-request-id", "REQ-CWB-DISPUTE-SOURCE-OWNER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "category": "TRANSFER_DISPUTE",
                      "description": "Attempt to dispute another customer source",
                      "sourceReference": {
                        "sourceType": "CUSTOMER_TRANSFER",
                        "sourceId": "TRR-DISP-002",
                        "syntheticOnly": true
                      }
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-CWB-DISPUTE-SOURCE-OWNER"))

        mockMvc.perform(
            post("/api/customer/complaints")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .header("x-request-id", "REQ-CWB-DISPUTE-SOURCE-CATEGORY")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "category": "ACCOUNT_ACCESS",
                      "description": "Attempt to attach a dispute source to access complaint",
                      "sourceReference": {
                        "sourceType": "CUSTOMER_TRANSFER",
                        "sourceId": "TRR-DISP-001",
                        "syntheticOnly": true
                      }
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("REQUEST_VALIDATION_FAILED"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-CWB-DISPUTE-SOURCE-CATEGORY"))

        assertEquals(0, countRows("complaint_cases"))
    }

    private fun seedDisputeSources() {
        jdbc.update(
            """
            INSERT INTO accounts (account_id, customer_id, account_no, currency, status)
            VALUES
              ('ACC-DISP-001', 'SYN-CUS-001', 'LAB-DISP-001', 'KRW', 'ACTIVE'),
              ('ACC-DISP-002', 'SYN-CUS-002', 'LAB-DISP-002', 'KRW', 'ACTIVE')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO customer_transfer_results (
              result_id, idempotency_key, command_hash, customer_id,
              from_account_id, to_account_id, amount_minor, currency, status,
              ledger_transaction_id, fds_case_id, failure_code, message,
              requested_by, requested_channel, business_reference_id, business_date
            )
            VALUES
              (
                'TRR-DISP-001', 'IT-DISP-001', 'hash-disp-001', 'SYN-CUS-001',
                'ACC-DISP-001', 'ACC-DISP-002', 72000, 'KRW', 'FAILED',
                NULL, NULL, 'SYNTHETIC_DISPUTE_SOURCE', 'Synthetic dispute source for customer 001',
                'customer01', 'CUSTOMER_WEB', 'BR-DISP-001', DATE '2026-06-05'
              ),
              (
                'TRR-DISP-002', 'IT-DISP-002', 'hash-disp-002', 'SYN-CUS-002',
                'ACC-DISP-002', 'ACC-DISP-001', 83000, 'KRW', 'FAILED',
                NULL, NULL, 'SYNTHETIC_DISPUTE_SOURCE', 'Synthetic dispute source for customer 002',
                'customer02', 'CUSTOMER_WEB', 'BR-DISP-002', DATE '2026-06-05'
              )
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO cards (
              card_id, customer_id, account_id, pan_token, pan_last4,
              status, issued_by, reason, idempotency_key, metadata_json
            )
            VALUES (
              'CARD-DISP-001', 'SYN-CUS-001', 'ACC-DISP-001',
              'tok_synthetic_dispute_001', '4242', 'ACTIVE',
              'customer01', 'Synthetic card dispute test fixture',
              'CARD-DISP-ISSUE-001', '{"syntheticOnly":true,"rawPanStored":false}'::jsonb
            )
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO card_limits (card_id, daily_limit_minor, monthly_limit_minor, single_limit_minor)
            VALUES ('CARD-DISP-001', 1000000, 5000000, 500000)
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO card_authorizations (
              authorization_id, card_id, account_id, amount_minor, currency, merchant_name,
              business_date, status, hold_id, three_ds_authentication_id,
              requested_by, requested_channel, reason, idempotency_key
            )
            VALUES (
              'CAUTH-DISP-001', 'CARD-DISP-001', 'ACC-DISP-001',
              31000, 'KRW', 'Synthetic Merchant', DATE '2026-06-05',
              'HELD', NULL, NULL, 'customer01', 'CARD_AUTH',
              'Synthetic authorization dispute source', 'CAUTH-DISP-001'
            )
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

    private fun bearer(subject: String, roles: List<String>, customerId: String? = null): String {
        val payload = linkedMapOf<String, Any?>(
            "iss" to "http://keycloak.local/realms/banking-lab",
            "sub" to subject,
            "roles" to roles,
            "customerId" to customerId,
            "active" to true
        )
        val json = objectMapper.writeValueAsBytes(payload)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json)
        return "Bearer lab.$encoded.sig"
    }

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $tableExpression", emptyMap<String, Any?>(), Int::class.java) ?: 0

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
