package lab.banking.core.security

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import org.springframework.security.oauth2.jwt.Jwt

class SpringSecurityResourceServerTest {
    @Test
    fun `JWT converter preserves roles from standard Keycloak and scope claims`() {
        val jwt = Jwt.withTokenValue("signed-jwt")
            .header("alg", "RS256")
            .subject("staff-resource01")
            .issuer("http://keycloak.local/realms/banking-lab")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .claim("realm_access", mapOf("roles" to listOf("BRANCH_STAFF")))
            .claim("resource_access", mapOf("banking-lab-api" to mapOf("roles" to listOf("OPS_MANAGER"))))
            .claim("scope", "AUDITOR")
            .claim("customerId", "SYN-CUS-001")
            .claim("sid", "SID-RESOURCE-001")
            .claim("amr", listOf("pwd", "otp"))
            .claim("acr", "aal2")
            .claim("deviceFingerprint", "device-resource-001")
            .build()

        val authentication = BankingLabJwtAuthenticationConverter().convert(jwt)
        val principal = BankingLabJwtClaims.toPrincipal(jwt.claims)!!

        assertEquals("staff-resource01", authentication.name)
        assertTrue(authentication.authorities.any { it.authority == "ROLE_BRANCH_STAFF" })
        assertTrue(authentication.authorities.any { it.authority == "ROLE_OPS_MANAGER" })
        assertTrue(authentication.authorities.any { it.authority == "ROLE_AUDITOR" })
        assertEquals(setOf("BRANCH_STAFF", "OPS_MANAGER", "AUDITOR"), principal.roles)
        assertEquals("SYN-CUS-001", principal.customerId)
        assertEquals("SID-RESOURCE-001", principal.sessionId)
        assertTrue(principal.hasStepUpAuthentication())
    }

    @Test
    fun `simulator tokens fail fast when enabled for prod like profiles`() {
        val prodEnvironment = MockEnvironment().withProperty("spring.profiles.active", "prod")
        prodEnvironment.setActiveProfiles("prod")

        assertThrows(IllegalStateException::class.java) {
            BankingLabSimulatorTokenProfileGuard(
                environment = prodEnvironment,
                simulatorTokensEnabled = true,
                devSimulatorTokenEnabled = true
            ).afterPropertiesSet()
        }

        val testEnvironment = MockEnvironment()
        BankingLabSimulatorTokenProfileGuard(
            environment = testEnvironment,
            simulatorTokensEnabled = true,
            devSimulatorTokenEnabled = true
        ).afterPropertiesSet()
    }

    @Test
    fun `default database passwords fail fast for prod like profiles`() {
        val prodEnvironment = MockEnvironment().withProperty("spring.profiles.active", "prod-like")
        prodEnvironment.setActiveProfiles("prod-like")

        assertThrows(IllegalStateException::class.java) {
            BankingLabDefaultSecretProfileGuard(
                environment = prodEnvironment,
                datasourcePassword = "banking_lab",
                datasourceUsername = "banking_lab"
            ).afterPropertiesSet()
        }

        assertThrows(IllegalStateException::class.java) {
            BankingLabDefaultSecretProfileGuard(
                environment = prodEnvironment,
                datasourcePassword = "replace-with-local-synthetic-password",
                datasourceUsername = "banking_lab"
            ).afterPropertiesSet()
        }

        BankingLabDefaultSecretProfileGuard(
            environment = prodEnvironment,
            datasourcePassword = "synthetic-prod-like-secret-from-private-env",
            datasourceUsername = "banking_lab"
        ).afterPropertiesSet()

        val devEnvironment = MockEnvironment()
        BankingLabDefaultSecretProfileGuard(
            environment = devEnvironment,
            datasourcePassword = "banking_lab",
            datasourceUsername = "banking_lab"
        ).afterPropertiesSet()
    }
}
