package lab.banking.core.customer

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Paths
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
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

@SpringBootTest(properties = ["banking-lab.security.enabled=true"])
@AutoConfigureMockMvc
@Testcontainers
class CustomerAccountApiParityIntegrationTest {
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
              audit_events,
              account_balance_projections,
              accounts,
              customers
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
        seedCustomersAndAccounts()
    }

    @Test
    fun `customer account detail is masked and records customer self service audit`() {
        mockMvc.perform(
            get("/api/customer/accounts/ACC-SYN-001-001/detail")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .queryParam("customerId", "SYN-CUS-001")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.customerId").value("SYN-CUS-001"))
            .andExpect(jsonPath("$.accountId").value("ACC-SYN-001-001"))
            .andExpect(jsonPath("$.maskedAccountNo").value("LAB-***-0001"))
            .andExpect(jsonPath("$.ledgerBalanceMinor").value(100_000_000))
            .andExpect(jsonPath("$.availableBalanceMinor").value(100_000_000))

        assertEquals(
            1,
            countRows(
                """
                audit_events
                WHERE event_type = 'ACCOUNT_VIEW'
                  AND actor_type = 'CUSTOMER'
                  AND actor_id = 'customer01'
                  AND actor_role = 'CUSTOMER'
                  AND screen_id = 'CWB-102'
                  AND customer_id = 'SYN-CUS-001'
                  AND account_id = 'ACC-SYN-001-001'
                  AND reason IS NULL
                """.trimIndent()
            )
        )
        assertEquals(
            1,
            countRows(
                """
                audit_events
                WHERE event_type = 'ACCOUNT_VIEW'
                  AND payload_json ->> 'maskedAccountNo' = 'LAB-***-0001'
                  AND payload_json ->> 'maskingPolicy' = 'CUSTOMER_SELF'
                  AND payload_json ->> 'syntheticOnly' = 'true'
                """.trimIndent()
            )
        )
        assertEquals(0, countRows("audit_events WHERE payload_json::text LIKE '%LAB-001-000001%'"))
    }

    @Test
    fun `customer account detail rejects ownership mismatch before account view audit`() {
        mockMvc.perform(
            get("/api/customer/accounts/ACC-SYN-002-001/detail")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .header("x-request-id", "REQ-CWB-ACCOUNT-OWNERSHIP")
                .queryParam("customerId", "SYN-CUS-002")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-CWB-ACCOUNT-OWNERSHIP"))
            .andExpect(jsonPath("$.error.route").value("/api/customer/accounts/ACC-SYN-002-001/detail"))

        assertEquals(0, countRows("audit_events WHERE event_type = 'ACCOUNT_VIEW'"))
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
              ('ACC-SYN-002-001', 'KRW', 50000000, 50000000, 0)
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
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
