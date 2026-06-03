package lab.banking.core.security

import com.fasterxml.jackson.databind.ObjectMapper
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
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

@SpringBootTest(
    properties = [
        "banking-lab.security.enabled=true",
        "banking-lab.security.simulator-tokens-enabled=false"
    ]
)
@AutoConfigureMockMvc
@Testcontainers
class LiveKeycloakRealmIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              reconciliation_adjustment_requests,
              reconciliation_items,
              operator_approvals,
              audit_events,
              masking_access_logs,
              screen_access_logs,
              account_balance_projections,
              ledger_postings,
              ledger_transactions,
              idempotency_keys,
              accounts,
              customers
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
        seedCustomersAndAccounts()
    }

    @Test
    fun `live Keycloak realm tokens authorize staff and customer routes through Spring JWKS`() {
        val baseUrl = requireLiveKeycloakBaseUrl()
        waitForRealm(baseUrl)

        val staffToken = passwordGrant(
            baseUrl = baseUrl,
            clientId = "staff-terminal",
            username = "branch01",
            password = "branch01-pass"
        )
        mockMvc.perform(
            get("/api/staff/customers/SYN-CUS-001/detail")
                .header("Authorization", "Bearer $staffToken")
                .queryParam("reason", "Live Keycloak staff inquiry")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.customerId").value("SYN-CUS-001"))
            .andExpect(jsonPath("$.item.maskedPhone").value("010-****-1001"))

        val customerToken = passwordGrant(
            baseUrl = baseUrl,
            clientId = "customer-web",
            username = "customer01",
            password = "customer01-pass"
        )
        mockMvc.perform(
            get("/api/customer/accounts/ACC-SYN-001-001/detail")
                .header("Authorization", "Bearer $customerToken")
                .queryParam("customerId", "SYN-CUS-001")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.customerId").value("SYN-CUS-001"))
            .andExpect(jsonPath("$.maskedAccountNo").value("LAB-***-0001"))

        mockMvc.perform(
            get("/api/customer/accounts/ACC-SYN-002-001/detail")
                .header("Authorization", "Bearer $customerToken")
                .header("x-request-id", "REQ-LIVE-KEYCLOAK-OWNERSHIP")
                .queryParam("customerId", "SYN-CUS-002")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-LIVE-KEYCLOAK-OWNERSHIP"))

        assertEquals(1, countRows("audit_events WHERE event_type = 'AUTHORIZATION_DENIED'"))
    }

    @Test
    fun `live Keycloak realm enforces MFA required action for synthetic manager`() {
        val baseUrl = requireLiveKeycloakBaseUrl()
        waitForRealm(baseUrl)

        val response = httpResponse(
            method = "POST",
            url = "$baseUrl/realms/banking-lab/protocol/openid-connect/token",
            form = mapOf(
                "grant_type" to "password",
                "client_id" to "staff-terminal",
                "username" to "manager-mfa01",
                "password" to "manager-mfa01-pass"
            )
        )
        assertEquals(400, response.status)
        assertTrue(response.body.contains("\"invalid_grant\""))
        assertTrue(response.body.contains("Account is not fully set up"))
    }

    @Test
    fun `live Keycloak realm enforces WebAuthn required action for synthetic manager`() {
        val baseUrl = requireLiveKeycloakBaseUrl()
        waitForRealm(baseUrl)

        val response = httpResponse(
            method = "POST",
            url = "$baseUrl/realms/banking-lab/protocol/openid-connect/token",
            form = mapOf(
                "grant_type" to "password",
                "client_id" to "staff-terminal",
                "username" to "manager-webauthn-block01",
                "password" to "manager-webauthn-block01-pass"
            )
        )
        assertEquals(400, response.status)
        assertTrue(response.body.contains("\"invalid_grant\""))
        assertTrue(response.body.contains("Account is not fully set up"))
    }

    @Test
    fun `live Keycloak realm issues segregated passkey recovery admin token`() {
        val baseUrl = requireLiveKeycloakBaseUrl()
        waitForRealm(baseUrl)

        val recoveryToken = passwordGrant(
            baseUrl = baseUrl,
            clientId = "staff-terminal",
            username = "security-admin01",
            password = "security-admin01-pass"
        )
        val roles = realmRoles(recoveryToken)
        assertTrue(roles.contains("PASSKEY_RECOVERY_ADMIN"))
        assertTrue(roles.contains("COMPLIANCE_MANAGER"))
        assertTrue(roles.contains("AUDITOR"))
        assertTrue(!roles.contains("CUSTOMER"))
        assertTrue(!roles.contains("BRANCH_STAFF"))
        assertTrue(!roles.contains("BRANCH_MANAGER"))

        mockMvc.perform(
            get("/api/staff/customers/SYN-CUS-001/detail")
                .header("Authorization", "Bearer $recoveryToken")
                .queryParam("reason", "Live Keycloak passkey recovery segregation smoke")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.customerId").value("SYN-CUS-001"))
            .andExpect(jsonPath("$.item.maskedPhone").value("010-****-1001"))
    }

    private fun seedCustomersAndAccounts() {
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
            INSERT INTO accounts (account_id, customer_id, account_no, currency, status)
            VALUES
              ('ACC-SYN-001-001', 'SYN-CUS-001', 'LAB-001-000001', 'KRW', 'ACTIVE'),
              ('ACC-SYN-002-001', 'SYN-CUS-002', 'LAB-002-000001', 'KRW', 'ACTIVE')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO account_balance_projections (
              account_id, currency, ledger_balance_minor, available_balance_minor, hold_amount_minor
            )
            VALUES
              ('ACC-SYN-001-001', 'KRW', 100000000, 100000000, 0),
              ('ACC-SYN-002-001', 'KRW', 200000000, 200000000, 0)
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

    private fun requireLiveKeycloakBaseUrl(): String {
        val baseUrl = liveKeycloakBaseUrlOrNull()
        assumeTrue(!baseUrl.isNullOrBlank(), "set BANKING_LAB_LIVE_KEYCLOAK_BASE_URL to run live Keycloak smoke")
        return baseUrl!!
    }

    private fun waitForRealm(baseUrl: String) {
        val deadline = System.nanoTime() + 60_000_000_000L
        var lastError: Throwable? = null
        while (System.nanoTime() < deadline) {
            try {
                val config = httpJson(
                    method = "GET",
                    url = "$baseUrl/realms/banking-lab/.well-known/openid-configuration"
                )
                if (config["issuer"] == "$baseUrl/realms/banking-lab") {
                    return
                }
            } catch (error: Throwable) {
                lastError = error
            }
            Thread.sleep(1_000)
        }
        throw IllegalStateException("live Keycloak realm did not become ready", lastError)
    }

    private fun passwordGrant(baseUrl: String, clientId: String, username: String, password: String): String {
        val tokenResponse = httpJson(
            method = "POST",
            url = "$baseUrl/realms/banking-lab/protocol/openid-connect/token",
            form = mapOf(
                "grant_type" to "password",
                "client_id" to clientId,
                "username" to username,
                "password" to password
            )
        )
        return tokenResponse["access_token"]?.toString()
            ?: error("Keycloak token response did not contain access_token")
    }

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM $tableExpression",
            emptyMap<String, Any?>(),
            Int::class.java
        ) ?: 0

    @Suppress("UNCHECKED_CAST")
    private fun realmRoles(token: String): Set<String> {
        val parts = token.split(".")
        require(parts.size >= 2) { "expected a JWT access token" }
        val payloadBytes = Base64.getUrlDecoder().decode(parts[1])
        val payload = ObjectMapper().readValue(payloadBytes, Map::class.java) as Map<String, Any?>
        val realmAccess = payload["realm_access"] as? Map<*, *> ?: return emptySet()
        return (realmAccess["roles"] as? List<*>)?.map { it.toString() }?.toSet() ?: emptySet()
    }

    companion object {
        private val objectMapper = ObjectMapper()

        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun dynamicProperties(registry: DynamicPropertyRegistry) {
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
            registry.add("banking-lab.security.jwt.jwks-uri") {
                "${liveKeycloakBaseUrl()}/realms/banking-lab/protocol/openid-connect/certs"
            }
            registry.add("banking-lab.security.jwt.issuer") {
                "${liveKeycloakBaseUrl()}/realms/banking-lab"
            }
            registry.add("banking-lab.security.jwt.audience") {
                "core-banking-api"
            }
        }

        private fun liveKeycloakBaseUrlOrNull(): String? =
            System.getenv("BANKING_LAB_LIVE_KEYCLOAK_BASE_URL")
                ?.trim()
                ?.trimEnd('/')
                ?.takeIf { it.isNotBlank() }

        private fun liveKeycloakBaseUrl(): String =
            liveKeycloakBaseUrlOrNull() ?: "http://127.0.0.1:1"

        @Suppress("UNCHECKED_CAST")
        private fun httpJson(
            method: String,
            url: String,
            form: Map<String, String> = emptyMap(),
            bearerToken: String? = null
        ): Map<String, Any?> =
            httpBody(method, url, form, bearerToken).let {
                objectMapper.readValue(it, Map::class.java) as Map<String, Any?>
            }

        private fun httpBody(
            method: String,
            url: String,
            form: Map<String, String>,
            bearerToken: String?
        ): ByteArray {
            val response = httpResponse(method, url, form, bearerToken)
            if (response.status !in 200..299) {
                error("HTTP ${response.status} from $url: ${response.body}")
            }
            return response.body.toByteArray(StandardCharsets.UTF_8)
        }

        private fun httpResponse(
            method: String,
            url: String,
            form: Map<String, String> = emptyMap(),
            bearerToken: String? = null
        ): HttpResponse {
            val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 3_000
            connection.readTimeout = 5_000
            connection.requestMethod = method
            if (bearerToken != null) {
                connection.setRequestProperty("Authorization", "Bearer $bearerToken")
            }
            if (form.isNotEmpty()) {
                val body = form.entries
                    .joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
                    .toByteArray(StandardCharsets.UTF_8)
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val responseBytes = if (status in 200..299) {
                connection.inputStream.use { it.readBytes() }
            } else {
                connection.errorStream?.use { it.readBytes() } ?: ByteArray(0)
            }
            return HttpResponse(status, responseBytes.toString(StandardCharsets.UTF_8))
        }

        private fun encode(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8)

        private data class HttpResponse(
            val status: Int,
            val body: String
        )
    }
}
