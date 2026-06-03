package lab.banking.core.admin

import java.nio.file.Paths
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
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
class AdminPlatformApiParityIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `admin platform summary is synthetic only and compliance-gated`() {
        mockMvc.perform(get("/api/admin/platform/summary"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))

        mockMvc.perform(
            get("/api/admin/platform/summary")
                .header("Authorization", bearer("branch01", listOf("BRANCH_STAFF")))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))

        mockMvc.perform(
            get("/api/admin/platform/summary")
                .header("Authorization", bearer("security-admin01", listOf("COMPLIANCE_MANAGER", "AUDITOR", "PASSKEY_RECOVERY_ADMIN")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.syntheticOnly").value(true))
            .andExpect(jsonPath("$.migrationTarget").value("kotlin-spring-boot"))
            .andExpect(jsonPath("$.nodeReferenceRuntimeRetained").value(true))
            .andExpect(jsonPath("$.controls[0].controlId").value("SYNTHETIC_ONLY"))
            .andExpect(jsonPath("$.controls[0].status").value("PASS"))
            .andExpect(jsonPath("$.controls[2].controlId").value("NODE_REFERENCE_BOUNDARY"))
            .andExpect(jsonPath("$.controls[2].status").value("BLOCKED"))
    }

    private fun bearer(subject: String, roles: List<String>): String {
        val payload = linkedMapOf<String, Any?>(
            "iss" to "http://keycloak.local/realms/banking-lab",
            "sub" to subject,
            "roles" to roles,
            "active" to true
        )
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ObjectMapperHolder.mapper.writeValueAsBytes(payload))
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
                val userDir = Paths.get(System.getProperty("user.dir"))
                listOf(
                    "filesystem:${userDir.resolve("db/migrations").normalize()}",
                    "filesystem:${userDir.resolve("../../db/migrations").normalize()}"
                ).joinToString(",")
            }
        }
    }
}

private object ObjectMapperHolder {
    val mapper = com.fasterxml.jackson.databind.ObjectMapper()
}
