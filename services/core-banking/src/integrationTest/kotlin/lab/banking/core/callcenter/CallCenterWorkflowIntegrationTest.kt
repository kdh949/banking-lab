package lab.banking.core.callcenter

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Paths
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CallCenterWorkflowIntegrationTest {
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
              call_center_access_audit,
              call_center_escalations,
              call_center_aftercall_tasks,
              call_center_notes,
              call_center_interactions,
              complaint_case_timeline,
              complaint_cases,
              workflow_events,
              workflow_instances,
              outbox_events,
              masking_access_logs,
              screen_access_logs,
              operator_approvals,
              audit_events,
              idempotency_keys,
              ledger_postings,
              ledger_transactions,
              account_balance_projections,
              account_limits,
              accounts,
              customer_kyc_profiles,
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
              ('BANK', 'Bank Suspense', NULL, NULL, 'INTERNAL', 'LOW'),
              ('SYN-CUS-CALL-001', 'Synthetic Caller Alpha', '010-0000-3101', 'Seoul Synthetic Center', 'STANDARD', 'LOW')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO customer_kyc_profiles (
              customer_id, kyc_status, source_of_funds_code, transaction_purpose_code, simulated_provider_reference
            )
            VALUES ('SYN-CUS-CALL-001', 'VERIFIED', 'SALARY', 'DAILY_BANKING', 'SIM-KYC-CALL-001')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO accounts (account_id, customer_id, account_no, currency, status)
            VALUES
              ('BANK-SUSPENSE', 'BANK', 'LAB-000-000000', 'KRW', 'ACTIVE'),
              ('ACC-CALL-001', 'SYN-CUS-CALL-001', 'LAB-310-000001', 'KRW', 'ACTIVE')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO account_balance_projections (
              account_id, currency, ledger_balance_minor, available_balance_minor, hold_amount_minor
            )
            VALUES ('ACC-CALL-001', 'KRW', 1000000, 1000000, 0)
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO account_limits (account_id, daily_transfer_limit_minor, single_transfer_limit_minor)
            VALUES ('ACC-CALL-001', 1000000, 500000)
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

    @Test
    fun `call-center workflow enforces reason redacts notes and records escalation history`() {
        mockMvc.perform(get("/api/staff/call-center/customers/search").queryParam("query", "CALL"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("POLICY_REASON_REQUIRED"))
        assertEquals(0, countRows("audit_events WHERE event_type = 'CALL_CENTER_CUSTOMER_SEARCH'"))

        mockMvc.perform(
            get("/api/staff/call-center/customers/search")
                .queryParam("query", "CALL")
                .queryParam("reason", "Synthetic customer called support")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].customerId").value("SYN-CUS-CALL-001"))
            .andExpect(jsonPath("$.items[0].maskedPhone").value("010-****-3101"))

        val startPayload = mockMvc.perform(
            post("/api/staff/call-center/interactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-CALL-001",
                      "accountId": "ACC-CALL-001",
                      "channel": "PHONE",
                      "contactReasonCode": "BALANCE_INQUIRY",
                      "requestedBy": "call-agent01",
                      "requestedByRole": "CALL_CENTER_AGENT",
                      "assignedTo": "call-agent01",
                      "reason": "Synthetic caller authenticated through lab script"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.status").value("OPEN"))
            .andReturn()
            .response
            .contentAsString
        val interactionId = objectMapper.readTree(startPayload).at("/item/interactionId").asText()

        val deniedEscalation = mockMvc.perform(
            post("/api/staff/call-center/interactions/$interactionId/escalations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requestedBy": "auditor01",
                      "requestedByRole": "AUDITOR",
                      "reason": "Auditor attempted escalation creation",
                      "escalationType": "COMPLAINT"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))
            .andReturn()
            .response
            .contentAsString
        assertTrue(deniedEscalation.contains("RBAC_ABAC_POLICY_REQUIRED"))

        val notePayload = mockMvc.perform(
            post("/api/staff/call-center/interactions/$interactionId/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requestedBy": "call-agent01",
                      "requestedByRole": "CALL_CENTER_AGENT",
                      "reason": "Record synthetic 상담 memo",
                      "noteBody": "Synthetic note: caller mentioned 010-1111-2222 and 900101-1234567 during lab script."
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.note.redactionApplied").value(true))
            .andExpect(jsonPath("$.note.piiPatternCount").value(2))
            .andReturn()
            .response
            .contentAsString
        val noteBody = objectMapper.readTree(notePayload).at("/note/noteBodyRedacted").asText()
        assertFalse(noteBody.contains("010-1111-2222"))
        assertFalse(noteBody.contains("900101-1234567"))
        assertFalse(auditPayloads("CALL_CENTER_NOTE_ADDED").joinToString("\n").contains("010-1111-2222"))

        mockMvc.perform(
            post("/api/staff/call-center/interactions/$interactionId/aftercall-tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requestedBy": "call-agent01",
                      "requestedByRole": "CALL_CENTER_AGENT",
                      "reason": "Follow up synthetic account question",
                      "taskType": "FOLLOW_UP",
                      "assignedTo": "call-agent02"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.task.status").value("OPEN"))
            .andExpect(jsonPath("$.item.status").value("AFTERCALL"))

        val escalationPayload = mockMvc.perform(
            post("/api/staff/call-center/interactions/$interactionId/escalations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requestedBy": "call-agent01",
                      "requestedByRole": "CALL_CENTER_AGENT",
                      "reason": "Request checker approval to convert synthetic unresolved call to complaint",
                      "escalationType": "COMPLAINT",
                      "complaintCategory": "ACCOUNT_ACCESS",
                      "complaintDescription": "Synthetic complaint converted from call-center script with 010-2222-3333 redacted."
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.status").value("AFTERCALL"))
            .andExpect(jsonPath("$.escalation.status").value("PENDING_APPROVAL"))
            .andExpect(jsonPath("$.escalation.approvalId").exists())
            .andExpect(jsonPath("$.escalation.complaintCaseId").doesNotExist())
            .andExpect(jsonPath("$.approval.businessType").value("CALL_CENTER_ESCALATION"))
            .andExpect(jsonPath("$.approval.status").value("PENDING"))
            .andReturn()
            .response
            .contentAsString
        val escalationJson = objectMapper.readTree(escalationPayload)
        val approvalId = escalationJson.at("/approval/approvalId").asText()
        val escalationId = escalationJson.at("/escalation/escalationId").asText()
        assertEquals(
            1,
            countRows(
                "operator_approvals WHERE approval_id = '$approvalId' AND business_type = 'CALL_CENTER_ESCALATION' AND business_reference_id = '$escalationId'"
            )
        )
        assertEquals(0, countRows("complaint_cases WHERE source_reference_json @> '{\"interactionId\": \"$interactionId\"}'::jsonb"))

        mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "approvedBy": "call-agent01",
                      "approvedByRole": "CALL_CENTER_MANAGER",
                      "approvalReason": "Self approval must be rejected for call-center escalation",
                      "screenId": "CALL-106"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("MAKER_CHECKER_SELF_APPROVAL_REJECTED"))

        val approvedPayload = mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "approvedBy": "call-manager01",
                      "approvedByRole": "CALL_CENTER_MANAGER",
                      "approvalReason": "Independent checker approves synthetic call-center complaint conversion",
                      "screenId": "CALL-106"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executed").value(true))
            .andExpect(jsonPath("$.callCenterEscalation.status").value("CREATED"))
            .andExpect(jsonPath("$.callCenterEscalation.complaintCaseId").exists())
            .andReturn()
            .response
            .contentAsString
        val complaintCaseId = objectMapper.readTree(approvedPayload).at("/callCenterEscalation/complaintCaseId").asText()
        assertEquals(1, countRows("complaint_cases WHERE complaint_case_id = '$complaintCaseId' AND source_reference_json @> '{\"syntheticOnly\": true}'::jsonb"))
        assertEquals(1, countRows("call_center_escalations WHERE escalation_id = '$escalationId' AND status = 'CREATED' AND approval_id = '$approvalId'"))

        mockMvc.perform(
            post("/api/staff/call-center/interactions/$interactionId/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requestedBy": "auditor01",
                      "requestedByRole": "AUDITOR",
                      "reason": "Auditor should not close calls"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isForbidden)

        mockMvc.perform(
            post("/api/staff/call-center/interactions/$interactionId/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requestedBy": "call-agent01",
                      "requestedByRole": "CALL_CENTER_AGENT",
                      "reason": "Synthetic call completed"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("CLOSED"))

        mockMvc.perform(
            get("/api/staff/call-center/customers/SYN-CUS-CALL-001/history")
                .queryParam("reason", "Review synthetic call history")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].interactionId").value(interactionId))
            .andExpect(jsonPath("$.items[0].status").value("CLOSED"))

        assertEquals(1, countRows("call_center_notes WHERE interaction_id = '$interactionId'"))
        assertEquals(1, countRows("call_center_aftercall_tasks WHERE interaction_id = '$interactionId'"))
        assertEquals(1, countRows("call_center_escalations WHERE interaction_id = '$interactionId'"))
        assertTrue(countRows("call_center_access_audit WHERE interaction_id = '$interactionId'") >= 5)
    }

    @Test
    fun `held transfer journey links masked interaction redacted note and FDS handoff without ledger posting`() {
        val journeyId = "JRN-CALL-HELD-001"
        jdbc.update(
            """
            INSERT INTO business_journeys (
              journey_id, journey_type, customer_id, status,
              primary_reference_type, primary_reference_id
            ) VALUES (
              :journeyId, 'HELD_TRANSFER', 'SYN-CUS-CALL-001', 'HELD',
              'CUSTOMER_TRANSFER_RESULT', 'TRR-CALL-HELD-001'
            )
            """.trimIndent(),
            mapOf("journeyId" to journeyId)
        )

        val startedPayload = mockMvc.perform(
            post("/api/staff/call-center/interactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-CALL-001",
                      "journeyId": "$journeyId",
                      "channel": "PHONE",
                      "contactReasonCode": "HELD_TRANSFER_STATUS",
                      "requestedBy": "call-agent01",
                      "requestedByRole": "CALL_CENTER_AGENT",
                      "reason": "Customer asked about synthetic held transfer"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.journeyId").value(journeyId))
            .andExpect(jsonPath("$.item.status").value("OPEN"))
            .andReturn()
            .response
            .contentAsString
        val interactionId = objectMapper.readTree(startedPayload).at("/item/interactionId").asText()

        mockMvc.perform(
            post("/api/staff/call-center/interactions/$interactionId/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requestedBy": "call-agent01",
                      "requestedByRole": "CALL_CENTER_AGENT",
                      "reason": "Record synthetic held transfer call",
                      "noteBody": "Synthetic caller shared 010-5555-6666 during the held-transfer inquiry."
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.note.redactionApplied").value(true))

        mockMvc.perform(
            post("/api/staff/call-center/interactions/$interactionId/escalations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requestedBy": "call-agent01",
                      "requestedByRole": "CALL_CENTER_AGENT",
                      "reason": "Handoff the linked held transfer to FDS",
                      "escalationType": "FDS"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.escalation.status").value("PENDING_APPROVAL"))

        mockMvc.perform(get("/api/staff/journeys/$journeyId"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("POLICY_REASON_REQUIRED"))

        mockMvc.perform(
            get("/api/staff/journeys/$journeyId")
                .queryParam("reason", "Review correlated call-center handoff")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("HELD"))

        assertEquals(1, countRows("business_journey_references WHERE journey_id = '$journeyId' AND reference_type = 'CALL_CENTER_INTERACTION'"))
        assertEquals(1, countRows("business_journey_references WHERE journey_id = '$journeyId' AND reference_type = 'CALL_CENTER_ESCALATION'"))
        assertEquals(1, countRows("business_journey_events WHERE journey_id = '$journeyId' AND event_type = 'CALL_CENTER_NOTE_REDACTED'"))
        assertEquals(1, countRows("business_journey_events WHERE journey_id = '$journeyId' AND event_type = 'CALL_CENTER_FDS_HANDOFF_REQUESTED'"))
        assertEquals(0, countRows("business_journey_events WHERE journey_id = '$journeyId' AND payload_json::text LIKE '%010-5555-6666%'"))
        assertEquals(0, countRows("ledger_transactions"))
    }

    private fun countRows(suffix: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $suffix", emptyMap<String, Any?>(), Int::class.java) ?: 0

    private fun auditPayloads(eventType: String): List<String> =
        jdbc.query(
            """
            SELECT payload_json::text AS payload_json
            FROM audit_events
            WHERE event_type = :eventType
            ORDER BY created_at
            """.trimIndent(),
            mapOf("eventType" to eventType)
        ) { rs, _ -> rs.getString("payload_json") }

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
