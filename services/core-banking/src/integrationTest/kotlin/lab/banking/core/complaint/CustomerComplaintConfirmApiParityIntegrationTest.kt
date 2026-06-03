package lab.banking.core.complaint

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Paths
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(properties = ["banking-lab.security.enabled=true"])
@AutoConfigureMockMvc
@Testcontainers
class CustomerComplaintConfirmApiParityIntegrationTest {
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
              sla_due_at, classification, owner_id, answer_json
            )
            VALUES (
              'CMP-ANSWERED-001', 'SYN-CUS-001', 'FEE_INQUIRY',
              'Fee explanation requested', 'ANSWERED',
              now() + interval '7 days', 'FEE_INQUIRY', 'complaint01',
              '{"body":"Synthetic fee answer","answeredBy":"manager01","answeredAt":"2026-06-03T00:00:00Z"}'::jsonb
            )
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO complaint_cases (
              complaint_case_id, customer_id, category, description, status,
              sla_due_at, classification, owner_id
            )
            VALUES (
              'CMP-RECEIVED-001', 'SYN-CUS-001', 'ACCOUNT_ACCESS',
              'Cannot see synthetic account history', 'RECEIVED',
              now() + interval '7 days', NULL, NULL
            )
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO complaint_case_timeline (
              complaint_timeline_id, complaint_case_id, event_type, from_status,
              to_status, actor_id, note, payload_json
            )
            VALUES (
              'CMT-ANSWERED-001', 'CMP-ANSWERED-001', 'ANSWERED', 'WAITING_APPROVAL',
              'ANSWERED', 'manager01', 'Synthetic answer sent', '{}'::jsonb
            )
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

    @Test
    fun `customer complaint confirmation closes answered case with timeline and audit`() {
        val response = mockMvc.perform(
            post("/api/customer/complaints/CMP-ANSWERED-001/confirm")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "note": "Customer accepted answer"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.caseId").value("CMP-ANSWERED-001"))
            .andExpect(jsonPath("$.item.status").value("CLOSED"))
            .andExpect(jsonPath("$.item.customerConfirmedAt").exists())
            .andReturn()

        val item = objectMapper.readTree(response.response.contentAsString).path("item")
        assertTrue(item.path("timeline").any { it.path("type").asText() == "CLOSED" })
        assertEquals("CLOSED", complaintStatus("CMP-ANSWERED-001"))
        assertEquals(1, countRows("complaint_cases WHERE complaint_case_id = 'CMP-ANSWERED-001' AND customer_confirmed_at IS NOT NULL"))
        assertEquals(1, countRows("complaint_case_timeline WHERE complaint_case_id = 'CMP-ANSWERED-001' AND event_type = 'CLOSED'"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'COMMAND_EXECUTED' AND business_reference_id = 'CMP-ANSWERED-001'"))
    }

    @Test
    fun `customer complaint confirmation rejects invalid state and ownership mismatch`() {
        mockMvc.perform(
            post("/api/customer/complaints/CMP-RECEIVED-001/confirm")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .header("x-request-id", "REQ-CWB-COMPLAINT-CONFIRM-STATE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "note": "Attempt early close"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("WORKFLOW_STATE_VIOLATION"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-CWB-COMPLAINT-CONFIRM-STATE"))
            .andExpect(jsonPath("$.error.route").value("/api/customer/complaints/CMP-RECEIVED-001/confirm"))

        mockMvc.perform(
            post("/api/customer/complaints/CMP-ANSWERED-001/confirm")
                .header("Authorization", bearer("customer02", listOf("CUSTOMER"), customerId = "SYN-CUS-002"))
                .header("x-request-id", "REQ-CWB-COMPLAINT-CONFIRM-OWNER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-002",
                      "note": "Attempt another customer complaint"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-CWB-COMPLAINT-CONFIRM-OWNER"))

        assertEquals("RECEIVED", complaintStatus("CMP-RECEIVED-001"))
        assertEquals("ANSWERED", complaintStatus("CMP-ANSWERED-001"))
        assertEquals(0, countRows("complaint_cases WHERE customer_confirmed_at IS NOT NULL"))
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
