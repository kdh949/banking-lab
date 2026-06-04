package lab.banking.core.analytics

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64
import org.hamcrest.Matchers.greaterThan
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(properties = ["banking-lab.security.enabled=true"])
@AutoConfigureMockMvc
@Testcontainers
class FdsAnalyticsEvidenceIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @Test
    fun `FDS analytics evidence Spring API is reason required role gated and synthetic only`() {
        mockMvc.perform(get("/api/fds/analytics"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))

        mockMvc.perform(
            get("/api/fds/analytics")
                .header("Authorization", bearer("branch01", listOf("BRANCH_STAFF")))
                .param("reason", "Synthetic analytics evidence view")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))

        mockMvc.perform(
            get("/api/fds/analytics")
                .header("Authorization", bearer("fds01", listOf("FDS_REVIEWER")))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("POLICY_REASON_REQUIRED"))

        mockMvc.perform(
            get("/api/fds/analytics")
                .header("Authorization", bearer("fds01", listOf("FDS_REVIEWER")))
                .param("reason", "Synthetic analytics evidence view")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.engine").value("banking_lab_analytics.duckdb_batch"))
            .andExpect(jsonPath("$.syntheticOnly").value(true))
            .andExpect(jsonPath("$.controls.realMoneyUsed").value(false))
            .andExpect(jsonPath("$.controls.realPiiUsed").value(false))
            .andExpect(jsonPath("$.controls.realBankNetworkUsed").value(false))
            .andExpect(jsonPath("$.scoredTransactions").value(greaterThan(0)))
            .andExpect(jsonPath("$.highRiskResults").value(greaterThan(0)))
            .andExpect(jsonPath("$.highestRisk.syntheticOnly").value(true))
            .andExpect(jsonPath("$.auditEventId").exists())

        assertTrue(countRows("audit_events WHERE event_type = 'FDS_ANALYTICS_EVIDENCE_VIEW'") >= 1)
    }

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $tableExpression", emptyMap<String, Any?>(), Int::class.java) ?: 0

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
            registry.add("spring.flyway.locations") {
                "filesystem:${migrationsPath()}"
            }
        }

        private fun migrationsPath(): Path =
            generateSequence(Paths.get("").toAbsolutePath()) { it.parent }
                .map { it.resolve("db/migrations") }
                .first(Files::isDirectory)
    }
}
