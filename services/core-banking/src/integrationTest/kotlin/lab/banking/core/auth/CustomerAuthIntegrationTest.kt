package lab.banking.core.auth

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Paths
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
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
        "banking-lab.security.customer-auth.synthetic-token-issuer-enabled=true"
    ]
)
@AutoConfigureMockMvc
@Testcontainers
class CustomerAuthIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @Autowired
    lateinit var passwordEncoder: PasswordEncoder

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              customer_auth_identities,
              customer_onboarding_requests,
              operator_approvals,
              audit_events,
              customer_kyc_profiles,
              customers
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
    }

    @Test
    fun `customer signup is unauthenticated idempotent hashed and returns session customer id`() {
        val payload = signup("SIGNUP-P3-001", username = "selfsignup01")
        val created = postSignup(payload)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.syntheticOnly").value(true))
            .andExpect(jsonPath("$.customer.username").value("selfsignup01"))
            .andExpect(jsonPath("$.customer.kycStatus").value("PENDING"))
            .andExpect(jsonPath("$.session.roles[0]").value("CUSTOMER"))
            .andExpect(jsonPath("$.session.customerId").value(startsWith("SYN-CUS-SIGNUP-")))
            .andExpect(jsonPath("$.session.subject").value("selfsignup01"))
            .andExpect(jsonPath("$.bearerToken").value(startsWith("lab.")))
            .andReturn()
        val customerId = created.read("$.customer.customerId")
        val bearerToken = created.read("$.bearerToken")

        postSignup(payload)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replayed").value(true))
            .andExpect(jsonPath("$.customer.customerId").value(customerId))
        assertEquals(1, countRows("customers"))
        assertEquals(1, countRows("customer_auth_identities"))

        val changedPayload = payload.toMutableMap()
        changedPayload["syntheticCustomerName"] = "Changed Synthetic Name"
        postSignup(changedPayload)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_CONFLICT"))

        mockMvc.perform(get("/api/auth/session").header("Authorization", "Bearer $bearerToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.subject").value("selfsignup01"))
            .andExpect(jsonPath("$.customerId").value(customerId))
            .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))

        val passwordHash = singleString("SELECT password_hash FROM customer_auth_identities WHERE username = 'selfsignup01'")
        assertTrue(passwordHash.startsWith("{bcrypt}"))
        assertFalse(passwordHash.contains("CustomerPass123!"))
        assertTrue(passwordEncoder.matches("CustomerPass123!", passwordHash))
        assertEquals(0, countRows("audit_events WHERE payload_json::text LIKE '%CustomerPass123!%'"))
        assertEquals(0, countRows("customer_auth_identities WHERE metadata_json::text LIKE '%CustomerPass123!%'"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'CUSTOMER_SIGNUP_SUCCEEDED' AND customer_id = '$customerId'"))
    }

    @Test
    fun `customer signup rejects duplicate username and weak password`() {
        postSignup(signup("SIGNUP-P3-DUP-1", username = "dupesignup01"))
            .andExpect(status().isCreated)

        postSignup(signup("SIGNUP-P3-DUP-2", username = "DUPESIGNUP01"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("REQUEST_VALIDATION_FAILED"))

        val weakPassword = signup("SIGNUP-P3-WEAK", username = "weaksignup01").toMutableMap()
        weakPassword["password"] = "short"
        postSignup(weakPassword)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("REQUEST_VALIDATION_FAILED"))

        val sameAsUsername = signup("SIGNUP-P3-SAME-PW", username = "samepassword01").toMutableMap()
        sameAsUsername["password"] = "samepassword01"
        postSignup(sameAsUsername)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("REQUEST_VALIDATION_FAILED"))
    }

    @Test
    fun `customer login returns simulator token resets failures and rejects invalid password`() {
        postSignup(signup("SIGNUP-P3-LOGIN", username = "loginuser01"))
            .andExpect(status().isCreated)
        val customerId = singleString("SELECT customer_id FROM customer_auth_identities WHERE username = 'loginuser01'")

        postLogin(mapOf("username" to "loginuser01", "password" to "wrong-password"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("CUSTOMER_AUTHENTICATION_FAILED"))
        assertEquals(1, singleInt("SELECT failed_login_count FROM customer_auth_identities WHERE username = 'loginuser01'"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'CUSTOMER_LOGIN_FAILED' AND customer_id = '$customerId'"))

        val login = postLogin(mapOf("username" to "LOGINUSER01", "password" to "CustomerPass123!"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.customer.customerId").value(customerId))
            .andExpect(jsonPath("$.session.customerId").value(customerId))
            .andExpect(jsonPath("$.bearerToken").value(startsWith("lab.")))
            .andReturn()
        assertEquals(0, singleInt("SELECT failed_login_count FROM customer_auth_identities WHERE username = 'loginuser01'"))
        assertNotNull(singleString("SELECT last_login_at::text FROM customer_auth_identities WHERE username = 'loginuser01'"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'CUSTOMER_LOGIN_SUCCEEDED' AND customer_id = '$customerId'"))

        mockMvc.perform(get("/api/auth/session").header("Authorization", "Bearer ${login.read("$.bearerToken")}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.customerId").value(customerId))
    }

    @Test
    fun `customer signup and login reject when synthetic issuer flag is absent`() {
        val issuer = SyntheticCustomerAuthTokenIssuer(
            objectMapper = objectMapper,
            issuerEnabled = false,
            simulatorTokensEnabled = true,
            devSimulatorTokenEnabled = true,
            tokenTtlSeconds = 3600
        )
        val error = org.junit.jupiter.api.Assertions.assertThrows(lab.banking.core.common.BankingLabDomainException::class.java) {
            issuer.requireIssuerEnabled()
        }
        assertEquals("SYNTHETIC_CUSTOMER_AUTH_DISABLED", error.code)
    }

    private fun postSignup(payload: Map<String, Any?>) =
        mockMvc.perform(
            post("/api/auth/customer/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )

    private fun postLogin(payload: Map<String, Any?>) =
        mockMvc.perform(
            post("/api/auth/customer/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )

    private fun signup(idempotencyKey: String, username: String): Map<String, Any?> =
        mapOf(
            "idempotencyKey" to idempotencyKey,
            "username" to username,
            "password" to "CustomerPass123!",
            "syntheticCustomerName" to "Synthetic Self Service $username",
            "syntheticPhone" to "010-7777-0001",
            "syntheticAddress" to "Seoul Synthetic Signup District",
            "customerGrade" to "STANDARD",
            "riskGrade" to "LOW",
            "sourceOfFundsCode" to "SELF_SERVICE_SYNTHETIC",
            "transactionPurposeCode" to "DAILY_BANKING"
        )

    private fun MvcResult.read(jsonPointer: String): String =
        objectMapper.readTree(response.contentAsString).at(jsonPointer.replace("$.", "/").replace(".", "/")).asText()

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $tableExpression", emptyMap<String, Any?>(), Int::class.java) ?: 0

    private fun singleString(sql: String): String =
        jdbc.queryForObject(sql, emptyMap<String, Any?>(), String::class.java) ?: error("query returned null")

    private fun singleInt(sql: String): Int =
        jdbc.queryForObject(sql, emptyMap<String, Any?>(), Int::class.java) ?: error("query returned null")

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
