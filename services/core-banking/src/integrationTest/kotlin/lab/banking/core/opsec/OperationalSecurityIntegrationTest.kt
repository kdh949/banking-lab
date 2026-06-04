package lab.banking.core.opsec

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.Base64
import lab.banking.core.audit.AuditEventAppender
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
        "banking-lab.security.step-up.enforcement-enabled=true"
    ]
)
@AutoConfigureMockMvc
@Testcontainers
class OperationalSecurityIntegrationTest {
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
            DELETE FROM break_glass_grants;
            DELETE FROM break_glass_review_cases;
            DELETE FROM audit_worm_export_segments;
            DELETE FROM audit_events;
            DELETE FROM synthetic_kms_keys WHERE key_version > 1;
            UPDATE synthetic_kms_keys
            SET status = 'ACTIVE', retired_at = NULL
            WHERE key_version = 1;
            """.trimIndent()
        )
    }

    @Test
    fun `WORM audit export detects tampering and remains verifiable after synthetic KMS rotation`() {
        appendSyntheticAudit("OPS-SEC-H5-A")
        appendSyntheticAudit("OPS-SEC-H5-B")

        val exportResponse = mockMvc.perform(
            post("/api/ops/security/audit-exports")
                .header("Authorization", bearer("ops01", listOf("OPS_MANAGER"), stepUp = true))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "exportedBy":"ops01",
                      "exportedRole":"OPS_MANAGER",
                      "reason":"Synthetic WORM export drill"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.eventCount").value(2))
            .andExpect(jsonPath("$.syntheticOnly").value(true))
            .andReturn()
        val segmentId = objectMapper.readTree(exportResponse.response.contentAsString)
            .path("segmentId")
            .asText()

        mockMvc.perform(
            get("/api/ops/security/audit-exports/verification")
                .header("Authorization", bearer("audit01", listOf("AUDITOR")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.checkedSegments").value(1))

        mockMvc.perform(
            post("/api/ops/security/kms/rotate")
                .header("Authorization", bearer("ops01", listOf("OPS_MANAGER"), stepUp = true))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "purpose":"AUDIT_WORM_ANCHOR",
                      "rotatedBy":"ops01",
                      "rotatedByRole":"OPS_MANAGER",
                      "reason":"Synthetic key rotation drill"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.oldKey.status").value("RETIRED"))
            .andExpect(jsonPath("$.newKey.keyVersion").value(2))
            .andExpect(jsonPath("$.auditWormVerification.valid").value(true))

        jdbc.update(
            "UPDATE audit_worm_export_segments SET segment_hash = 'tampered' WHERE segment_id = :segmentId",
            mapOf("segmentId" to segmentId)
        )

        mockMvc.perform(
            get("/api/ops/security/audit-exports/verification")
                .header("Authorization", bearer("audit01", listOf("AUDITOR")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.failures[0]").value("$segmentId: segment hash mismatch"))
    }

    @Test
    fun `break glass requires step up expires and requires independent post review`() {
        val requestBody =
            """
            {
              "operatorId":"ops01",
              "operatorRole":"OPS_MANAGER",
              "elevatedRole":"SECURITY_ADMIN",
              "reason":"Synthetic emergency access drill",
              "durationMinutes":1
            }
            """.trimIndent()

        mockMvc.perform(
            post("/api/ops/security/break-glass")
                .header("Authorization", bearer("ops01", listOf("OPS_MANAGER"), stepUp = false))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("STEP_UP_REQUIRED"))

        val grantResponse = mockMvc.perform(
            post("/api/ops/security/break-glass")
                .header("Authorization", bearer("ops01", listOf("OPS_MANAGER"), stepUp = true))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.reviewCase.status").value("OPEN"))
            .andReturn()
        val grant = objectMapper.readTree(grantResponse.response.contentAsString)
        val grantId = grant.path("grantId").asText()
        val reviewCaseId = grant.path("reviewCase").path("reviewCaseId").asText()

        jdbc.update(
            "UPDATE break_glass_grants SET expires_at = now() - interval '1 minute' WHERE grant_id = :grantId",
            mapOf("grantId" to grantId)
        )
        mockMvc.perform(
            post("/api/ops/security/break-glass/expire-elapsed")
                .header("Authorization", bearer("ops01", listOf("OPS_MANAGER"), stepUp = true))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "expiredBy":"ops01",
                      "expiredByRole":"OPS_MANAGER",
                      "reason":"Synthetic scheduled expiry sweep"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.expiredCount").value(1))

        mockMvc.perform(
            post("/api/ops/security/break-glass/reviews/$reviewCaseId/close")
                .header("Authorization", bearer("ops01", listOf("OPS_MANAGER"), stepUp = true))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "reviewedBy":"ops01",
                      "reviewedByRole":"OPS_MANAGER",
                      "reviewReason":"Synthetic self-review should fail"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("MAKER_CHECKER_SELF_APPROVAL_REJECTED"))

        mockMvc.perform(
            post("/api/ops/security/break-glass/reviews/$reviewCaseId/close")
                .header("Authorization", bearer("compliance01", listOf("COMPLIANCE_MANAGER"), stepUp = true))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "reviewedBy":"compliance01",
                      "reviewedByRole":"COMPLIANCE_MANAGER",
                      "reviewReason":"Synthetic independent post-hoc review"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("REVIEWED"))
            .andExpect(jsonPath("$.reviewCase.status").value("CLOSED"))
            .andExpect(jsonPath("$.reviewCase.reviewerId").value("compliance01"))
    }

    private fun appendSyntheticAudit(referenceId: String) {
        auditEvents.append(
            eventType = "H5_SYNTHETIC_SECURITY_EVENT",
            actorType = "STAFF",
            actorId = "ops01",
            actorRole = "OPS_MANAGER",
            screenId = "OPS-SEC-101",
            businessReferenceId = referenceId,
            reason = "Synthetic H5 audit event seed",
            payload = mapOf("referenceId" to referenceId, "syntheticOnly" to true)
        )
    }

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
            registry.add("spring.flyway.locations") { "filesystem:../../db/migrations" }
            registry.add("banking-lab.synthetic-seed.enabled") { "false" }
            registry.add("banking-lab.temporal.worker.enabled") { "false" }
            registry.add("banking-lab.outbox.worker.enabled") { "false" }
            registry.add("banking-lab.tracing.enabled") { "false" }
            registry.add("banking-lab.otel.tracing.export.enabled") { "false" }
        }
    }
}
