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
class CustomerComplaintSelfServiceApiIntegrationTest {
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
              complaint_case_materials,
              complaint_reopen_requests,
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
            VALUES
              ('SYN-CUS-001', 'Lab Customer Alpha', '010-0000-1001', 'Seoul Synthetic District', 'STANDARD', 'LOW'),
              ('SYN-CUS-002', 'Lab Customer Beta', '010-0000-1002', 'Busan Synthetic District', 'STANDARD', 'LOW')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO complaint_cases (
              complaint_case_id, customer_id, category, description, status,
              sla_due_at, classification, owner_id
            )
            VALUES
              (
                'CMP-WAITING-MATERIAL-001', 'SYN-CUS-001', 'TRANSFER_DISPUTE',
                'Synthetic material request', 'WAITING_CUSTOMER',
                now() + interval '7 days', 'TRANSFER_DISPUTE', 'complaint01'
              ),
              (
                'CMP-RECEIVED-001', 'SYN-CUS-001', 'ACCOUNT_ACCESS',
                'Synthetic received complaint', 'RECEIVED',
                now() + interval '7 days', NULL, NULL
              )
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO complaint_cases (
              complaint_case_id, customer_id, category, description, status,
              sla_due_at, classification, owner_id, answer_json, customer_confirmed_at
            )
            VALUES (
              'CMP-CLOSED-001', 'SYN-CUS-001', 'FEE_INQUIRY',
              'Synthetic closed complaint', 'CLOSED',
              now() + interval '7 days', 'FEE_INQUIRY', 'complaint01',
              '{"body":"Synthetic answer","answeredBy":"manager01","answeredAt":"2026-06-03T00:00:00Z"}'::jsonb,
              now()
            )
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

    @Test
    fun `customer complaint type guide is API backed and synthetic`() {
        mockMvc.perform(
            get("/api/customer/complaint-types")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].category").value("TRANSFER_DISPUTE"))
            .andExpect(jsonPath("$.items[0].slaHours").value(72))
            .andExpect(jsonPath("$.items[0].requiredMaterials[0]").value("transactionId"))
            .andExpect(jsonPath("$.items[?(@.category == 'CARD_DISPUTE')]").exists())
    }

    @Test
    fun `customer can submit synthetic material metadata with timeline and audit`() {
        val response = mockMvc.perform(
            post("/api/customer/complaints/CMP-WAITING-MATERIAL-001/materials")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "materialType": "CUSTOMER_STATEMENT",
                      "fileName": "synthetic-statement.pdf",
                      "description": "Synthetic statement metadata only",
                      "reason": "Customer supplies requested material"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("IN_REVIEW"))
            .andExpect(jsonPath("$.material.materialType").value("CUSTOMER_STATEMENT"))
            .andExpect(jsonPath("$.material.syntheticStorageRef").value(org.hamcrest.Matchers.startsWith("synthetic://")))
            .andReturn()

        val materialId = objectMapper.readTree(response.response.contentAsString)
            .path("material")
            .path("materialId")
            .asText()

        assertEquals("IN_REVIEW", complaintStatus("CMP-WAITING-MATERIAL-001"))
        assertEquals(1, countRows("complaint_case_materials WHERE complaint_material_id = '$materialId'"))
        assertEquals(1, countRows("complaint_case_timeline WHERE complaint_case_id = 'CMP-WAITING-MATERIAL-001' AND event_type = 'MATERIAL_SUBMITTED'"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'COMMAND_EXECUTED' AND screen_id = 'CMP-103' AND business_reference_id = 'CMP-WAITING-MATERIAL-001'"))
        assertEquals(0, countRows("audit_events WHERE payload_json::text LIKE '%Synthetic statement metadata only%'"))
    }

    @Test
    fun `material submission rejects non synthetic storage and customer mismatch`() {
        mockMvc.perform(
            post("/api/customer/complaints/CMP-WAITING-MATERIAL-001/materials")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .header("x-request-id", "REQ-CMP-MATERIAL-SYNTHETIC")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "materialType": "CUSTOMER_STATEMENT",
                      "fileName": "real-statement.pdf",
                      "syntheticStorageRef": "s3://real-bucket/object"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("REQUEST_VALIDATION_FAILED"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-CMP-MATERIAL-SYNTHETIC"))
            .andExpect(jsonPath("$.error.route").value("/api/customer/complaints/CMP-WAITING-MATERIAL-001/materials"))

        mockMvc.perform(
            post("/api/customer/complaints/CMP-WAITING-MATERIAL-001/materials")
                .header("Authorization", bearer("customer02", listOf("CUSTOMER"), customerId = "SYN-CUS-002"))
                .header("x-request-id", "REQ-CMP-MATERIAL-OWNER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-002",
                      "materialType": "CUSTOMER_STATEMENT",
                      "fileName": "synthetic-statement.pdf"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-CMP-MATERIAL-OWNER"))

        assertEquals(0, countRows("complaint_case_materials"))
    }

    @Test
    fun `customer can reopen closed complaint with durable request and timeline`() {
        val response = mockMvc.perform(
            post("/api/customer/complaints/CMP-CLOSED-001/reopen-requests")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "reopenReason": "Synthetic customer disputes closure",
                      "reason": "Customer requested reopen"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("REOPENED"))
            .andExpect(jsonPath("$.reopenRequest.status").value("REOPENED"))
            .andReturn()

        val reopenRequestId = objectMapper.readTree(response.response.contentAsString)
            .path("reopenRequest")
            .path("reopenRequestId")
            .asText()

        assertEquals("REOPENED", complaintStatus("CMP-CLOSED-001"))
        assertEquals(1, countRows("complaint_reopen_requests WHERE complaint_reopen_request_id = '$reopenRequestId'"))
        assertEquals(1, countRows("complaint_case_timeline WHERE complaint_case_id = 'CMP-CLOSED-001' AND event_type = 'REOPEN_REQUESTED'"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'COMMAND_EXECUTED' AND screen_id = 'CMP-106' AND business_reference_id = 'CMP-CLOSED-001'"))
    }

    @Test
    fun `reopen rejects non closed state and ownership mismatch`() {
        mockMvc.perform(
            post("/api/customer/complaints/CMP-RECEIVED-001/reopen-requests")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .header("x-request-id", "REQ-CMP-REOPEN-STATE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "reopenReason": "Attempt reopen too early"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("WORKFLOW_STATE_VIOLATION"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-CMP-REOPEN-STATE"))
            .andExpect(jsonPath("$.error.route").value("/api/customer/complaints/CMP-RECEIVED-001/reopen-requests"))

        mockMvc.perform(
            post("/api/customer/complaints/CMP-CLOSED-001/reopen-requests")
                .header("Authorization", bearer("customer02", listOf("CUSTOMER"), customerId = "SYN-CUS-002"))
                .header("x-request-id", "REQ-CMP-REOPEN-OWNER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-002",
                      "reopenReason": "Attempt another customer complaint"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-CMP-REOPEN-OWNER"))

        assertEquals("RECEIVED", complaintStatus("CMP-RECEIVED-001"))
        assertEquals("CLOSED", complaintStatus("CMP-CLOSED-001"))
        assertEquals(0, countRows("complaint_reopen_requests"))
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

    private fun complaintStatus(caseId: String): String? =
        jdbc.queryForObject(
            "SELECT status FROM complaint_cases WHERE complaint_case_id = :caseId",
            mapOf("caseId" to caseId),
            String::class.java
        )

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
