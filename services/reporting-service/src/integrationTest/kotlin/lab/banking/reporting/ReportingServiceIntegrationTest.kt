package lab.banking.reporting

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.http.MediaType
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
class ReportingServiceIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @Test
    fun `reporting catalog and artifacts are reason required role gated audited and idempotent`() {
        val token = bearer("auditor01", listOf("AUDITOR"))

        mockMvc.perform(get("/api/reports/catalog"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("REPORTING_AUTHORIZATION_POLICY_VIOLATION"))

        mockMvc.perform(
            get("/api/reports/catalog")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER")))
                .param("reason", "Synthetic report catalog review")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("REPORTING_AUTHORIZATION_POLICY_VIOLATION"))

        mockMvc.perform(
            get("/api/reports/catalog")
                .header("Authorization", token)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("REPORTING_POLICY_REASON_REQUIRED"))

        mockMvc.perform(
            get("/api/reports/catalog")
                .header("Authorization", token)
                .param("reason", "Synthetic report catalog review")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.syntheticOnly").value(true))
            .andExpect(jsonPath("$.auditEventId").exists())
            .andExpect(jsonPath("$.items[0].reportType").value("AUDIT_SUMMARY"))
            .andExpect(jsonPath("$.items[0].syntheticOnly").value(true))

        val command = """
            {
              "reportType": "AUDIT_SUMMARY",
              "requestedBy": "auditor01",
              "requestedByRole": "AUDITOR",
              "reason": "Synthetic audit report generation",
              "idempotencyKey": "RPT-IT-001"
            }
        """.trimIndent()

        val generateResult = mockMvc.perform(
            post("/api/reports/artifacts")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(command)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.item.reportType").value("AUDIT_SUMMARY"))
            .andExpect(jsonPath("$.item.maskedByDefault").value(true))
            .andExpect(jsonPath("$.item.syntheticOnly").value(true))
            .andExpect(jsonPath("$.item.artifactContent.syntheticOnly").value(true))
            .andExpect(jsonPath("$.item.artifactContent.maskedByDefault").value(true))
            .andExpect(jsonPath("$.item.artifactContent.controls.realPiiUsed").value(false))
            .andExpect(jsonPath("$.item.artifactContent.controls.ledgerRowsMutated").value(false))
            .andExpect(jsonPath("$.item.contentSha256").value(org.hamcrest.Matchers.matchesPattern("^[a-f0-9]{64}$")))
            .andExpect(jsonPath("$.item.retentionPolicy").value("SYNTHETIC_7Y"))
            .andExpect(jsonPath("$.item.retentionUntil").exists())
            .andExpect(jsonPath("$.item.exportFormat").value("JSON"))
            .andExpect(jsonPath("$.item.sourceReferences[0]").value("audit_events"))
            .andReturn()

        val artifactId = ObjectMapper().readTree(generateResult.response.contentAsString)
            .at("/item/artifactId")
            .asText()

        mockMvc.perform(
            post("/api/reports/artifacts")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(command)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.replayed").value(true))

        mockMvc.perform(
            get("/api/reports/artifacts")
                .header("Authorization", token)
                .param("reason", "Synthetic artifact inventory review")
                .param("reportType", "AUDIT_SUMMARY")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].reportType").value("AUDIT_SUMMARY"))
            .andExpect(jsonPath("$.items[0].contentSha256").value(org.hamcrest.Matchers.matchesPattern("^[a-f0-9]{64}$")))
            .andExpect(jsonPath("$.items[0].retentionPolicy").value("SYNTHETIC_7Y"))
            .andExpect(jsonPath("$.items[0].artifactPath").value(org.hamcrest.Matchers.containsString("reports/synthetic")))

        mockMvc.perform(
            get("/api/reports/artifacts/$artifactId/export")
                .header("Authorization", token)
                .param("reason", "Synthetic artifact export package review")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.auditEventId").exists())
            .andExpect(jsonPath("$.packageName").value(org.hamcrest.Matchers.endsWith(".json")))
            .andExpect(jsonPath("$.contentSha256").value(org.hamcrest.Matchers.matchesPattern("^[a-f0-9]{64}$")))
            .andExpect(jsonPath("$.exportFormat").value("JSON"))
            .andExpect(jsonPath("$.syntheticOnly").value(true))
            .andExpect(jsonPath("$.packageContent.syntheticOnly").value(true))
            .andExpect(jsonPath("$.packageContent.controls.downloadSimulationOnly").value(true))
            .andExpect(jsonPath("$.packageContent.controls.ledgerRowsMutated").value(false))
            .andExpect(jsonPath("$.packageContent.artifactContent.syntheticOnly").value(true))

        jdbc.update(
            "UPDATE report_artifacts SET retention_until = CURRENT_DATE - INTERVAL '1 day' WHERE artifact_id = :artifactId",
            mapOf("artifactId" to artifactId)
        )
        mockMvc.perform(
            post("/api/reports/retention/sweeps")
                .header("Authorization", bearer("reporting-admin01", listOf("REPORTING_ANALYST")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requestedBy": "reporting-admin01",
                      "requestedByRole": "REPORTING_ANALYST",
                      "reason": "Synthetic retention lifecycle test",
                      "sweepDate": "2099-01-01"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.auditEventId").exists())
            .andExpect(jsonPath("$.expiredCount").value(1))
            .andExpect(jsonPath("$.expiredArtifactIds[0]").value(artifactId))
            .andExpect(jsonPath("$.ledgerRowsMutated").value(false))
            .andExpect(jsonPath("$.syntheticOnly").value(true))

        mockMvc.perform(
            get("/api/reports/artifacts")
                .header("Authorization", token)
                .param("reason", "Synthetic expired artifact inventory review")
                .param("reportType", "AUDIT_SUMMARY")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].artifactId").value(artifactId))
            .andExpect(jsonPath("$.items[0].status").value("EXPIRED"))

        mockMvc.perform(
            get("/api/reports/artifacts/$artifactId/export")
                .header("Authorization", token)
                .param("reason", "Synthetic expired artifact export denial review")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("REPORTING_REQUEST_VALIDATION_FAILED"))

        assertEquals(1, countRows("report_artifacts"))
        assertEquals(1, countRowsWhere("report_artifacts", "artifact_content ->> 'syntheticOnly' = 'true'"))
        assertEquals(1, countRowsWhere("report_artifacts", "content_sha256 <> repeat('0', 64)"))
        assertEquals(1, countRowsWhere("report_artifacts", "status = 'EXPIRED' AND retention_action = 'SYNTHETIC_RETENTION_EXPIRED'"))
        assertEquals(3, countRows("reporting_outbox_events"))
        assertEquals(1, countRowsWhere("reporting_outbox_events", "event_type = 'ReportArtifactGenerated' AND status = 'PENDING' AND payload_json ->> 'ledgerRowsMutated' = 'false'"))
        assertEquals(1, countRowsWhere("reporting_outbox_events", "event_type = 'ReportArtifactExported' AND status = 'PENDING'"))
        assertEquals(1, countRowsWhere("reporting_outbox_events", "event_type = 'ReportRetentionSweepCompleted' AND aggregate_type = 'REPORT_RETENTION_SWEEP'"))
        assertTrue(countRows("reporting_access_audit_events") >= 6)
    }

    private fun countRows(tableName: String): Long =
        jdbc.queryForObject("SELECT COUNT(*) FROM $tableName", emptyMap<String, Any?>(), Long::class.java) ?: 0L

    private fun countRowsWhere(tableName: String, whereClause: String): Long =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM $tableName WHERE $whereClause",
            emptyMap<String, Any?>(),
            Long::class.java
        ) ?: 0L

    private fun bearer(subject: String, roles: List<String>): String {
        val payload = linkedMapOf<String, Any?>(
            "iss" to "http://keycloak.local/realms/banking-lab",
            "sub" to subject,
            "roles" to roles,
            "active" to true
        )
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ObjectMapper().writeValueAsBytes(payload))
        return "Bearer lab.$encoded.sig"
    }

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
        }
    }
}
