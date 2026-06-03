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
              complaint_case_timeline,
              complaint_cases,
              audit_events,
              customers
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
        jdbc.update(
            """
            INSERT INTO customers (
              customer_id, customer_name, customer_phone, customer_address, customer_grade, risk_grade
            )
            VALUES (
              'SYN-CUS-001', 'Lab Customer Alpha', '010-0000-1001',
              'Seoul Synthetic District', 'STANDARD', 'LOW'
            )
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
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
                      "category": "TRANSFER_DISPUTE",
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
