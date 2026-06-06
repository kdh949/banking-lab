package lab.banking.core.audit

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Paths
import java.time.Instant
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
        "banking-lab.security.dev-simulator-token-enabled=true",
        "banking-lab.security.step-up.enforcement-enabled=true"
    ]
)
@AutoConfigureMockMvc
@Testcontainers
class AuditExportIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @Autowired
    lateinit var auditEvents: AuditEventAppender

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            DELETE FROM audit_export_files;
            DELETE FROM audit_export_jobs;
            DELETE FROM operator_approvals;
            DELETE FROM audit_events;
            DELETE FROM ledger_transactions;
            """.trimIndent()
        )
    }

    @Test
    fun `audit export request requires fresh step up before pending approval is created`() {
        mockMvc.perform(
            post("/api/audit/exports")
                .header("Authorization", bearer("auditor01", listOf("AUDITOR"), stepUp = false))
                .header("x-request-id", "REQ-AUDIT-EXPORT-NO-STEP")
                .contentType(MediaType.APPLICATION_JSON)
                .content(exportRequest())
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("STEP_UP_REQUIRED"))
            .andExpect(jsonPath("$.error.policy").value("STEP_UP_REAUTHENTICATION_REQUIRED"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-AUDIT-EXPORT-NO-STEP"))

        assertEquals(0, countRows("audit_export_jobs"))
        assertEquals(0, countRows("operator_approvals"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'AUTHORIZATION_DENIED' AND screen_id = 'AUD-501'"))
    }

    @Test
    fun `audit export is idempotent maker-checker protected and never mutates ledger rows`() {
        appendSyntheticAudit("AUD-EXPORT-SEED-A")
        appendSyntheticAudit("AUD-EXPORT-SEED-B")

        val requestResponse = mockMvc.perform(
            post("/api/audit/exports")
                .header("Authorization", bearer("auditor01", listOf("AUDITOR"), stepUp = true))
                .contentType(MediaType.APPLICATION_JSON)
                .content(exportRequest())
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.item.status").value("PENDING_APPROVAL"))
            .andExpect(jsonPath("$.item.approvalId").exists())
            .andExpect(jsonPath("$.item.ledgerRowsMutated").value(false))
            .andReturn()

        val exportId = objectMapper.readTree(requestResponse.response.contentAsString)
            .path("item")
            .path("exportId")
            .asText()

        mockMvc.perform(
            post("/api/audit/exports")
                .header("Authorization", bearer("auditor01", listOf("AUDITOR"), stepUp = true))
                .contentType(MediaType.APPLICATION_JSON)
                .content(exportRequest())
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.replayed").value(true))
            .andExpect(jsonPath("$.item.exportId").value(exportId))

        mockMvc.perform(
            post("/api/audit/exports")
                .header("Authorization", bearer("auditor01", listOf("AUDITOR"), stepUp = true))
                .header("x-request-id", "REQ-AUDIT-EXPORT-IDEMPOTENCY")
                .contentType(MediaType.APPLICATION_JSON)
                .content(exportRequest(reason = "Different synthetic export purpose"))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_CONFLICT"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-AUDIT-EXPORT-IDEMPOTENCY"))

        mockMvc.perform(
            post("/api/audit/exports/$exportId/approve")
                .header("Authorization", bearer("auditor01", listOf("AUDITOR"), stepUp = true))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "approvedBy": "auditor01",
                      "approvedByRole": "AUDITOR",
                      "reason": "Synthetic self-approval should fail"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("MAKER_CHECKER_SELF_APPROVAL_REJECTED"))

        mockMvc.perform(
            post("/api/audit/exports/$exportId/approve")
                .header("Authorization", bearer("compliance01", listOf("COMPLIANCE_MANAGER"), stepUp = true))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "approvedBy": "compliance01",
                      "approvedByRole": "COMPLIANCE_MANAGER",
                      "reason": "Synthetic compliance approval for audit export"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("EXPORTED"))
            .andExpect(jsonPath("$.item.approvedBy").value("compliance01"))
            .andExpect(jsonPath("$.item.rowCount").value(4))
            .andExpect(jsonPath("$.item.file.format").value("NDJSON"))
            .andExpect(jsonPath("$.item.file.sha256").exists())
            .andExpect(jsonPath("$.item.ledgerRowsMutated").value(false))
            .andExpect(jsonPath("$.item.syntheticOnly").value(true))

        val fileContent = jdbc.queryForObject(
            "SELECT content_jsonl FROM audit_export_files WHERE export_id = :exportId",
            mapOf("exportId" to exportId),
            String::class.java
        ) ?: ""
        assertTrue(fileContent.lines().any { it.contains("AUD-EXPORT-SEED-A") })
        assertTrue(fileContent.lines().all { it.isBlank() || it.contains("\"syntheticOnly\":true") })

        mockMvc.perform(
            get("/api/audit/exports/$exportId")
                .header("Authorization", bearer("compliance01", listOf("COMPLIANCE_MANAGER")))
                .queryParam("actorId", "compliance01")
                .queryParam("actorRole", "COMPLIANCE_MANAGER")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("POLICY_REASON_REQUIRED"))

        mockMvc.perform(
            get("/api/audit/exports/$exportId")
                .header("Authorization", bearer("auditor02", listOf("AUDITOR")))
                .queryParam("actorId", "auditor02")
                .queryParam("actorRole", "AUDITOR")
                .queryParam("reason", "Synthetic audit export status review")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("EXPORTED"))
            .andExpect(jsonPath("$.item.file.storageUri").value("local://synthetic/audit-exports/$exportId.jsonl"))

        assertEquals(0, countRows("ledger_transactions"))
        assertFalse(
            jdbc.queryForObject(
                "SELECT ledger_rows_mutated FROM audit_export_jobs WHERE export_id = :exportId",
                mapOf("exportId" to exportId),
                Boolean::class.java
            ) ?: true
        )
        assertEquals(1, countRows("audit_export_files"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'AUDIT_EXPORT_COMPLETED'"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'AUDIT_EXPORT_VIEWED'"))
    }

    @Test
    fun `audit export can be rejected by independent checker without file materialization`() {
        val requestResponse = mockMvc.perform(
            post("/api/audit/exports")
                .header("Authorization", bearer("auditor03", listOf("AUDITOR"), stepUp = true))
                .contentType(MediaType.APPLICATION_JSON)
                .content(exportRequest(requestedBy = "auditor03", idempotencyKey = "AUDIT-EXPORT-REJECT-001"))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.status").value("PENDING_APPROVAL"))
            .andReturn()

        val exportId = objectMapper.readTree(requestResponse.response.contentAsString)
            .path("item")
            .path("exportId")
            .asText()

        mockMvc.perform(
            post("/api/audit/exports/$exportId/reject")
                .header("Authorization", bearer("compliance02", listOf("COMPLIANCE_MANAGER"), stepUp = true))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "rejectedBy": "compliance02",
                      "rejectedByRole": "COMPLIANCE_MANAGER",
                      "reason": "Synthetic export request rejected during checker review"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("REJECTED"))
            .andExpect(jsonPath("$.item.rejectedBy").value("compliance02"))
            .andExpect(jsonPath("$.item.file").doesNotExist())

        assertEquals(0, countRows("audit_export_files"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'AUDIT_EXPORT_REJECTED'"))
    }

    private fun appendSyntheticAudit(referenceId: String) {
        auditEvents.append(
            eventType = "AUDIT_EXPORT_SYNTHETIC_SEED",
            actorType = "STAFF",
            actorId = "audit-seed",
            actorRole = "AUDITOR",
            screenId = "AUD-501",
            businessReferenceId = referenceId,
            reason = "Synthetic audit export seed",
            payload = mapOf("referenceId" to referenceId, "syntheticOnly" to true)
        )
    }

    private fun exportRequest(
        requestedBy: String = "auditor01",
        reason: String = "Synthetic audit export for compliance evidence",
        idempotencyKey: String = "AUDIT-EXPORT-IDEMP-001"
    ): String =
        """
        {
          "requestedBy": "$requestedBy",
          "requestedRole": "AUDITOR",
          "reason": "$reason",
          "idempotencyKey": "$idempotencyKey",
          "exportFormat": "NDJSON"
        }
        """.trimIndent()

    private fun bearer(subject: String, roles: List<String>, stepUp: Boolean = false): String {
        val payload = linkedMapOf<String, Any?>(
            "iss" to "http://keycloak.local/realms/banking-lab",
            "sub" to subject,
            "roles" to roles,
            "active" to true,
            "auth_time" to Instant.now().epochSecond,
            "iat" to Instant.now().epochSecond
        )
        if (stepUp) {
            payload["amr"] = listOf("otp")
            payload["acr"] = "banking-lab-step-up"
        }
        val json = objectMapper.writeValueAsBytes(payload)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json)
        return "Bearer lab.$encoded.sig"
    }

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM $tableExpression",
            emptyMap<String, Any?>(),
            Int::class.java
        ) ?: 0

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun configure(registry: DynamicPropertyRegistry) {
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
            registry.add("banking-lab.synthetic-seed.enabled") { "false" }
            registry.add("banking-lab.temporal.worker.enabled") { "false" }
            registry.add("banking-lab.outbox.worker.enabled") { "false" }
            registry.add("banking-lab.tracing.enabled") { "false" }
            registry.add("banking-lab.otel.tracing.export.enabled") { "false" }
        }
    }
}
