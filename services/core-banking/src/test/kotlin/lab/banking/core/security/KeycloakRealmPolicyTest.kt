package lab.banking.core.security

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeycloakRealmPolicyTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `realm declares WebAuthn policy and passkey recovery segregation`() {
        val realm = loadRealm()

        assertEquals("Synthetic Banking Lab", realm["webAuthnPolicyRpEntityName"])
        assertEquals("localhost", realm["webAuthnPolicyRpId"])
        assertEquals("none", realm["webAuthnPolicyAttestationConveyancePreference"])
        assertEquals("required", realm["webAuthnPolicyUserVerificationRequirement"])
        assertEquals(true, realm["webAuthnPolicyAvoidSameAuthenticatorRegister"])
        assertEquals(60, realm["webAuthnPolicyCreateTimeout"])
        assertTrue(listValue(realm["webAuthnPolicySignatureAlgorithms"]).contains("ES256"))

        val requiredActions = listMap(realm["requiredActions"])
        val webAuthnAction = requiredActions.single { it["alias"] == "webauthn-register" }
        assertEquals(true, webAuthnAction["enabled"])
        assertEquals(false, webAuthnAction["defaultAction"])

        val roles = listMap(mapValue(realm["roles"])["realm"]).map { it["name"]?.toString() }.toSet()
        assertTrue(roles.contains("PASSKEY_RECOVERY_ADMIN"))
        assertTrue(roles.contains("PAYMENT_SERVICE"))
        assertTrue(roles.contains("NOTIFICATION_SERVICE"))

        val users = listMap(realm["users"])
        val paymentServiceAccount = users.single { it["username"] == "service-account-payment-service-api" }
        assertEquals("payment-service-api", paymentServiceAccount["serviceAccountClientId"])
        assertTrue(listValue(paymentServiceAccount["realmRoles"]).contains("PAYMENT_SERVICE"))
        val notificationServiceAccount = users.single { it["username"] == "service-account-notification-service-api" }
        assertEquals("notification-service-api", notificationServiceAccount["serviceAccountClientId"])
        assertTrue(listValue(notificationServiceAccount["realmRoles"]).contains("NOTIFICATION_SERVICE"))

        val webAuthnUser = users.single { it["username"] == "manager-webauthn01" }
        assertTrue(listValue(webAuthnUser["requiredActions"]).contains("webauthn-register"))

        val recoveryAdmin = users.single { it["username"] == "security-admin01" }
        val recoveryRoles = listValue(recoveryAdmin["realmRoles"]).toSet()
        assertTrue(recoveryRoles.contains("PASSKEY_RECOVERY_ADMIN"))
        assertTrue(recoveryRoles.contains("COMPLIANCE_MANAGER"))
        assertTrue(recoveryRoles.contains("AUDITOR"))
        assertFalse(recoveryRoles.contains("CUSTOMER"))
        assertFalse(recoveryRoles.contains("BRANCH_STAFF"))
        assertFalse(recoveryRoles.contains("BRANCH_MANAGER"))

        val clients = listMap(realm["clients"])
        val adminClient = clients.single { it["clientId"] == "admin-console" }
        assertTrue(listValue(adminClient["redirectUris"]).contains("http://localhost:3007/*"))
        assertTrue(listValue(adminClient["webOrigins"]).contains("http://localhost:3007"))

        val paymentFacingClients = setOf("customer-web", "staff-terminal", "ops-console")
        paymentFacingClients.forEach { clientId ->
            val client = clients.single { it["clientId"] == clientId }
            assertTrue(mapperNames(client).contains("payment-service-api-audience"), "$clientId must carry payment-service-api audience")
        }
        val complaintClient = clients.single { it["clientId"] == "complaint-portal" }
        assertFalse(mapperNames(complaintClient).contains("payment-service-api-audience"))

        val notificationFacingClients = setOf("customer-web", "audit-console", "admin-console")
        notificationFacingClients.forEach { clientId ->
            val client = clients.single { it["clientId"] == clientId }
            assertTrue(
                mapperNames(client).contains("notification-service-api-audience"),
                "$clientId must carry notification-service-api audience"
            )
        }
        val nonNotificationClients = setOf("staff-terminal", "complaint-portal", "ops-console", "fds-aml-console")
        nonNotificationClients.forEach { clientId ->
            val client = clients.single { it["clientId"] == clientId }
            assertFalse(
                mapperNames(client).contains("notification-service-api-audience"),
                "$clientId must not carry notification-service-api audience"
            )
        }

        val paymentServiceClient = clients.single { it["clientId"] == "payment-service-api" }
        assertEquals(false, paymentServiceClient["publicClient"])
        assertEquals(true, paymentServiceClient["serviceAccountsEnabled"])
        assertEquals(false, paymentServiceClient["directAccessGrantsEnabled"])
        val paymentMappers = listMap(paymentServiceClient["protocolMappers"])
            .map { it["name"]?.toString() }
            .toSet()
        assertTrue(paymentMappers.contains("payment-service-api-audience"))
        assertTrue(paymentMappers.contains("core-banking-api-audience"))

        val notificationServiceClient = clients.single { it["clientId"] == "notification-service-api" }
        assertEquals(false, notificationServiceClient["publicClient"])
        assertEquals(true, notificationServiceClient["serviceAccountsEnabled"])
        assertEquals(false, notificationServiceClient["directAccessGrantsEnabled"])
        val notificationMappers = listMap(notificationServiceClient["protocolMappers"])
            .map { it["name"]?.toString() }
            .toSet()
        assertTrue(notificationMappers.contains("notification-service-api-audience"))
        assertFalse(notificationMappers.contains("core-banking-api-audience"))
        assertFalse(notificationMappers.contains("payment-service-api-audience"))
    }

    private fun mapperNames(client: Map<String, Any?>): Set<String> =
        listMap(client["protocolMappers"])
            .mapNotNull { it["name"]?.toString() }
            .toSet()

    @Suppress("UNCHECKED_CAST")
    private fun loadRealm(): Map<String, Any?> {
        val userDir = Path.of(System.getProperty("user.dir"))
        val candidates = listOf(
            userDir.resolve("infra/keycloak/realm-banking-lab.json"),
            userDir.resolve("../../infra/keycloak/realm-banking-lab.json")
        )
        val realmPath = candidates.firstOrNull { Files.exists(it) }
            ?: error("infra/keycloak/realm-banking-lab.json was not found from $userDir")
        return objectMapper.readValue(Files.readString(realmPath), Map::class.java) as Map<String, Any?>
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapValue(value: Any?): Map<String, Any?> =
        value as? Map<String, Any?> ?: error("expected object but found $value")

    @Suppress("UNCHECKED_CAST")
    private fun listMap(value: Any?): List<Map<String, Any?>> =
        value as? List<Map<String, Any?>> ?: error("expected object array but found $value")

    private fun listValue(value: Any?): List<String> =
        (value as? List<*>)?.map { it.toString() } ?: error("expected array but found $value")
}
