package lab.banking.core.security

import com.fasterxml.jackson.databind.ObjectMapper
import java.math.BigInteger
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64
import java.util.concurrent.Executors
import org.junit.jupiter.api.AfterAll
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

@SpringBootTest(
    properties = [
        "banking-lab.security.enabled=true",
        "banking-lab.security.simulator-tokens-enabled=false",
        "banking-lab.security.jwt.issuer=http://keycloak.local/realms/banking-lab",
        "banking-lab.security.jwt.audience=banking-lab-api"
    ]
)
@AutoConfigureMockMvc
@Testcontainers
class JwksAuthorizationIntegrationTest {
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
    fun `signed JWKS token authorizes staff routes and simulator fallback is disabled`() {
        mockMvc.perform(
            get("/api/staff/customers/SYN-CUS-001/detail")
                .header("Authorization", signedBearer("branch01", listOf("BRANCH_STAFF")))
                .queryParam("reason", "Branch service request")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.customerId").value("SYN-CUS-001"))
            .andExpect(jsonPath("$.item.maskedPhone").value("010-****-1001"))

        mockMvc.perform(
            get("/api/staff/customers/SYN-CUS-001/detail")
                .header("Authorization", simulatorBearer("branch01", listOf("BRANCH_STAFF")))
                .header("x-request-id", "REQ-JWKS-SIM-DISABLED")
                .queryParam("reason", "Branch service request")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-JWKS-SIM-DISABLED"))

        mockMvc.perform(
            get("/api/staff/customers/SYN-CUS-001/detail")
                .header("Authorization", tamperedSignedBearer("branch01", listOf("BRANCH_STAFF")))
                .header("x-request-id", "REQ-JWKS-BAD-SIG")
                .queryParam("reason", "Branch service request")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-JWKS-BAD-SIG"))

        assertEquals(2, countRows("audit_events WHERE event_type = 'AUTHORIZATION_DENIED'"))
    }

    @Test
    fun `signed JWKS customer token enforces ownership and expiry`() {
        mockMvc.perform(
            get("/api/customer/accounts/ACC-SYN-001-001/detail")
                .header("Authorization", signedBearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .queryParam("customerId", "SYN-CUS-001")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.customerId").value("SYN-CUS-001"))
            .andExpect(jsonPath("$.maskedAccountNo").value("LAB-***-0001"))

        mockMvc.perform(
            get("/api/customer/accounts/ACC-SYN-002-001/detail")
                .header("Authorization", signedBearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .queryParam("customerId", "SYN-CUS-002")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))

        mockMvc.perform(
            get("/api/customer/accounts/ACC-SYN-001-001/detail")
                .header(
                    "Authorization",
                    signedBearer(
                        subject = "customer01",
                        roles = listOf("CUSTOMER"),
                        customerId = "SYN-CUS-001",
                        expiresAt = Instant.now().minusSeconds(120)
                    )
                )
                .header("x-request-id", "REQ-JWKS-EXPIRED")
                .queryParam("customerId", "SYN-CUS-001")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-JWKS-EXPIRED"))

        assertEquals(2, countRows("audit_events WHERE event_type = 'AUTHORIZATION_DENIED'"))
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

    private fun signedBearer(
        subject: String,
        roles: List<String>,
        customerId: String? = null,
        expiresAt: Instant = Instant.now().plusSeconds(300)
    ): String {
        val header = mapOf("alg" to "RS256", "typ" to "JWT", "kid" to keyId)
        val now = Instant.now()
        val payload = linkedMapOf<String, Any?>(
            "iss" to issuer,
            "sub" to subject,
            "aud" to listOf(audience),
            "iat" to now.epochSecond,
            "nbf" to now.minusSeconds(5).epochSecond,
            "exp" to expiresAt.epochSecond,
            "realm_access" to mapOf("roles" to roles),
            "customerId" to customerId,
            "active" to true
        )
        val encodedHeader = base64Url(objectMapper.writeValueAsBytes(header))
        val encodedPayload = base64Url(objectMapper.writeValueAsBytes(payload))
        val signingInput = "$encodedHeader.$encodedPayload"
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(keyPair.private)
        signature.update(signingInput.toByteArray(StandardCharsets.US_ASCII))
        return "Bearer $signingInput.${base64Url(signature.sign())}"
    }

    private fun tamperedSignedBearer(subject: String, roles: List<String>): String =
        signedBearer(subject, roles).replaceAfterLast(".", "tampered")

    private fun simulatorBearer(subject: String, roles: List<String>): String {
        val payload = linkedMapOf<String, Any?>(
            "iss" to issuer,
            "sub" to subject,
            "roles" to roles,
            "active" to true
        )
        val encoded = base64Url(objectMapper.writeValueAsBytes(payload))
        return "Bearer lab.$encoded.sig"
    }

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM $tableExpression",
            emptyMap<String, Any?>(),
            Int::class.java
        ) ?: 0

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    companion object {
        private const val issuer = "http://keycloak.local/realms/banking-lab"
        private const val audience = "banking-lab-api"
        private const val keyId = "banking-lab-jwks-test-key"
        private val objectMapper = ObjectMapper()
        private val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()
        private var jwksServer: TinyJwksServer? = null

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
                ensureJwksServer().uri
            }
        }

        @AfterAll
        @JvmStatic
        fun stopJwksServer() {
            jwksServer?.stop()
        }

        private fun ensureJwksServer(): TinyJwksServer =
            synchronized(this) {
                jwksServer ?: TinyJwksServer(jwksJson()).also {
                    it.start()
                    jwksServer = it
                }
            }

        private fun jwksJson(): String {
            val publicKey = keyPair.public as RSAPublicKey
            val jwk = mapOf(
                "kty" to "RSA",
                "use" to "sig",
                "alg" to "RS256",
                "kid" to keyId,
                "n" to base64Url(unsignedBytes(publicKey.modulus)),
                "e" to base64Url(unsignedBytes(publicKey.publicExponent))
            )
            return objectMapper.writeValueAsString(mapOf("keys" to listOf(jwk)))
        }

        private fun base64Url(bytes: ByteArray): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        private fun unsignedBytes(value: BigInteger): ByteArray {
            val bytes = value.toByteArray()
            return if (bytes.size > 1 && bytes[0] == 0.toByte()) {
                bytes.copyOfRange(1, bytes.size)
            } else {
                bytes
            }
        }
    }

    private class TinyJwksServer(private val jwksJson: String) {
        private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()
        val uri: String = "http://127.0.0.1:${socket.localPort}/jwks"

        fun start() {
            executor.submit {
                while (!socket.isClosed) {
                    runCatching {
                        socket.accept().use(::handle)
                    }
                }
            }
        }

        fun stop() {
            socket.close()
            executor.shutdownNow()
        }

        private fun handle(client: Socket) {
            val input = client.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) {
                    break
                }
            }
            val body = jwksJson.toByteArray(StandardCharsets.UTF_8)
            val headers = (
                "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: ${body.size}\r\n" +
                    "Connection: close\r\n\r\n"
                ).toByteArray(StandardCharsets.US_ASCII)
            client.getOutputStream().use { output ->
                output.write(headers)
                output.write(body)
            }
        }
    }
}
