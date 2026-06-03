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

        val users = listMap(realm["users"])
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
    }

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
