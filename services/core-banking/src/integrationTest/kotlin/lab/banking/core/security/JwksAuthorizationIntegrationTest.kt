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
import lab.banking.core.testsupport.ParameterSeedSupport
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
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
        "banking-lab.security.simulator-tokens-enabled=false",
        "banking-lab.security.jwt.issuer=http://wrong-issuer.local/realms/banking-lab,http://keycloak.local/realms/banking-lab",
        "banking-lab.security.jwt.audience=banking-lab-api",
        "banking-lab.security.trusted-device-enforcement-enabled=true",
        "banking-lab.security.step-up.enforcement-enabled=true",
        "banking-lab.security.session.enforcement-enabled=true"
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
              revoked_sessions,
              trusted_devices,
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
        ParameterSeedSupport.reseedFdsRuleParameters(jdbc)
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
    fun `resource server validates issuer audience resource roles and scope roles`() {
        mockMvc.perform(
            get("/api/staff/customers/SYN-CUS-001/detail")
                .header(
                    "Authorization",
                    signedBearer(
                        subject = "resource-role-staff01",
                        roles = emptyList(),
                        resourceAccessRoles = mapOf("banking-lab-api" to listOf("BRANCH_STAFF"))
                    )
                )
                .queryParam("reason", "Resource access role smoke")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.customerId").value("SYN-CUS-001"))

        mockMvc.perform(
            get("/api/audit/events")
                .header(
                    "Authorization",
                    signedBearer(
                        subject = "scope-auditor01",
                        roles = emptyList(),
                        scope = "AUDITOR"
                    )
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items").isArray)

        mockMvc.perform(
            get("/api/staff/customers/SYN-CUS-001/detail")
                .header(
                    "Authorization",
                    signedBearer(
                        subject = "wrong-issuer01",
                        roles = listOf("BRANCH_STAFF"),
                        issuerOverride = "http://untrusted-issuer.local/realms/banking-lab"
                    )
                )
                .header("x-request-id", "REQ-JWKS-WRONG-ISSUER")
                .queryParam("reason", "Wrong issuer denial smoke")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-JWKS-WRONG-ISSUER"))

        mockMvc.perform(
            get("/api/staff/customers/SYN-CUS-001/detail")
                .header(
                    "Authorization",
                    signedBearer(
                        subject = "wrong-audience01",
                        roles = listOf("BRANCH_STAFF"),
                        audienceOverride = "other-synthetic-api"
                    )
                )
                .header("x-request-id", "REQ-JWKS-WRONG-AUDIENCE")
                .queryParam("reason", "Wrong audience denial smoke")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-JWKS-WRONG-AUDIENCE"))

        assertEquals(2, countRows("audit_events WHERE event_type = 'AUTHORIZATION_DENIED'"))
    }

    @Test
    fun `signed JWKS token enforces step up trusted device and session revocation`() {
        mockMvc.perform(
            post("/api/staff/pii/unmask")
                .header(
                    "Authorization",
                    signedBearer(
                        subject = "manager01",
                        roles = listOf("BRANCH_MANAGER"),
                        sessionId = "SID-STAFF-H2",
                        deviceFingerprint = "staff-device-h2",
                        authenticationMethods = listOf("pwd"),
                        authTime = Instant.now()
                    )
                )
                .header("x-request-id", "REQ-H2-NO-STEP-UP")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "requestedBy": "manager01",
                      "actorRole": "BRANCH_MANAGER",
                      "reason": "H2 step-up denial smoke"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("STEP_UP_REQUIRED"))
            .andExpect(jsonPath("$.error.policy").value("STEP_UP_REAUTHENTICATION_REQUIRED"))

        val staffStepUpToken = signedBearer(
            subject = "manager01",
            roles = listOf("BRANCH_MANAGER"),
            sessionId = "SID-STAFF-H2",
            deviceFingerprint = "staff-device-h2",
            authenticationMethods = listOf("pwd", "otp"),
            assuranceLevel = "aal2",
            authTime = Instant.now()
        )

        mockMvc.perform(
            get("/api/auth/session")
                .header("Authorization", staffStepUpToken)
                .header("x-request-id", "REQ-H2-UNKNOWN-DEVICE")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("TRUSTED_DEVICE_REQUIRED"))

        insertTrustedDevice("STAFF", "manager01", null, "staff-device-h2")

        mockMvc.perform(get("/api/auth/session").header("Authorization", staffStepUpToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.subject").value("manager01"))
            .andExpect(jsonPath("$.stepUpSatisfied").value(true))

        mockMvc.perform(
            post("/api/staff/pii/unmask")
                .header("Authorization", staffStepUpToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "requestedBy": "manager01",
                      "actorRole": "BRANCH_MANAGER",
                      "reason": "H2 step-up success smoke"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.piiExposure").value("UNMASKED_TIMEBOXED"))

        val unknownCustomerDeviceToken = signedBearer(
            subject = "customer01",
            roles = listOf("CUSTOMER"),
            customerId = "SYN-CUS-001",
            sessionId = "SID-CUSTOMER-H2",
            deviceFingerprint = "customer-device-h2"
        )
        val transferBody = """
            {
              "customerId": "SYN-CUS-001",
              "fromAccountId": "ACC-SYN-001-001",
              "toAccountId": "ACC-SYN-002-001",
              "amountMinor": 1000,
              "idempotencyKey": "H2-TRUSTED-DEVICE-TRANSFER",
              "requestedBy": "SYN-CUS-001",
              "reason": "H2 trusted device customer transfer smoke"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/customer/transfers")
                .header("Authorization", unknownCustomerDeviceToken)
                .header("x-request-id", "REQ-H2-CUSTOMER-UNKNOWN-DEVICE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferBody)
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("TRUSTED_DEVICE_REQUIRED"))

        insertTrustedDevice("CUSTOMER", "SYN-CUS-001", "SYN-CUS-001", "customer-device-h2")

        mockMvc.perform(
            post("/api/customer/transfers")
                .header("Authorization", unknownCustomerDeviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferBody)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.status").value("POSTED"))

        revokeSession("SID-STAFF-H2", "manager01", "STAFF")

        mockMvc.perform(
            get("/api/auth/session")
                .header("Authorization", staffStepUpToken)
                .header("x-request-id", "REQ-H2-REVOKED-SESSION")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("SESSION_REVOKED"))
            .andExpect(jsonPath("$.error.policy").value("SESSION_REVOCATION"))
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
        sessionId: String? = null,
        deviceFingerprint: String? = null,
        authenticationMethods: List<String> = emptyList(),
        assuranceLevel: String? = null,
        authTime: Instant? = null,
        expiresAt: Instant = Instant.now().plusSeconds(300),
        issuerOverride: String? = null,
        audienceOverride: String? = null,
        resourceAccessRoles: Map<String, List<String>> = emptyMap(),
        scope: String? = null
    ): String {
        val header = mapOf("alg" to "RS256", "typ" to "JWT", "kid" to keyId)
        val now = Instant.now()
        val payload = linkedMapOf<String, Any?>(
            "iss" to (issuerOverride ?: issuer),
            "sub" to subject,
            "aud" to listOf(audienceOverride ?: audience),
            "iat" to now.epochSecond,
            "nbf" to now.minusSeconds(5).epochSecond,
            "exp" to expiresAt.epochSecond,
            "realm_access" to mapOf("roles" to roles),
            "customerId" to customerId,
            "active" to true
        )
        if (sessionId != null) {
            payload["sid"] = sessionId
        }
        if (deviceFingerprint != null) {
            payload["deviceFingerprint"] = deviceFingerprint
        }
        if (authenticationMethods.isNotEmpty()) {
            payload["amr"] = authenticationMethods
        }
        if (assuranceLevel != null) {
            payload["acr"] = assuranceLevel
        }
        if (authTime != null) {
            payload["auth_time"] = authTime.epochSecond
        }
        if (resourceAccessRoles.isNotEmpty()) {
            payload["resource_access"] = resourceAccessRoles.mapValues { (_, mappedRoles) ->
                mapOf("roles" to mappedRoles)
            }
        }
        if (!scope.isNullOrBlank()) {
            payload["scope"] = scope
        }
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

    private fun insertTrustedDevice(
        actorType: String,
        actorId: String,
        customerId: String?,
        deviceFingerprint: String
    ) {
        jdbc.update(
            """
            INSERT INTO trusted_devices (
              trusted_device_id, actor_type, actor_id, customer_id, device_fingerprint, status, metadata_json
            )
            VALUES (
              :trustedDeviceId, :actorType, :actorId, :customerId, :deviceFingerprint, 'ACTIVE',
              '{"syntheticOnly": true}'::jsonb
            )
            """.trimIndent(),
            mapOf(
                "trustedDeviceId" to "TD-$actorType-$actorId-$deviceFingerprint",
                "actorType" to actorType,
                "actorId" to actorId,
                "customerId" to customerId,
                "deviceFingerprint" to deviceFingerprint
            )
        )
    }

    private fun revokeSession(sessionId: String, actorId: String, actorType: String) {
        jdbc.update(
            """
            INSERT INTO revoked_sessions (
              session_id, actor_id, actor_type, revoked_by, reason, metadata_json
            )
            VALUES (
              :sessionId, :actorId, :actorType, 'security-admin01',
              'H2 synthetic forced logout smoke',
              '{"syntheticOnly": true}'::jsonb
            )
            """.trimIndent(),
            mapOf("sessionId" to sessionId, "actorId" to actorId, "actorType" to actorType)
        )
    }

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
