package lab.banking.core.security

import com.fasterxml.jackson.databind.ObjectMapper
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.time.Instant
import java.util.Base64
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class SignedJwtJwksTokenDecoder(
    private val objectMapper: ObjectMapper,
    @param:Value("\${banking-lab.security.jwt.jwks-uri:}")
    private val jwksUri: String,
    @param:Value("\${banking-lab.security.jwt.issuer:}")
    private val expectedIssuer: String,
    @param:Value("\${banking-lab.security.jwt.audience:}")
    private val expectedAudience: String
) {
    private val base64Url = Base64.getUrlDecoder()
    private val clockSkewSeconds = 30L
    private val allowedIssuers = expectedIssuer
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()

    fun decodeToken(token: String): BankingLabPrincipal? {
        if (jwksUri.isBlank()) {
            return null
        }
        return runCatching {
            val parts = token.split(".")
            if (parts.size != 3) {
                return null
            }
            val header = readMap(decodeJson(parts[0]))
            if (header["alg"]?.toString() != "RS256") {
                return null
            }
            val key = publicKey(header["kid"]?.toString()) ?: return null
            if (!verifySignature(parts, key)) {
                return null
            }
            val claims = readMap(decodeJson(parts[1]))
            if (!claimsAreValid(claims)) {
                return null
            }
            val subject = subjectFromClaims(claims) ?: return null
            val roles = rolesFromClaims(claims)
            if (roles.isEmpty()) {
                return null
            }
            BankingLabPrincipal(
                subject = subject,
                roles = roles,
                customerId = customerIdFromClaims(claims),
                issuer = claims["iss"]?.toString(),
                sessionId = claims["sid"]?.toString()?.takeIf { it.isNotBlank() },
                authTime = epochInstant(claims["auth_time"]),
                issuedAt = epochInstant(claims["iat"]),
                authenticationMethods = stringSet(claims["amr"]),
                assuranceLevel = claims["acr"]?.toString(),
                deviceFingerprint = claims["deviceFingerprint"]?.toString()
                    ?: claims["device_fingerprint"]?.toString()
            )
        }.getOrNull()
    }

    private fun verifySignature(parts: List<String>, publicKey: RSAPublicKey): Boolean {
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initVerify(publicKey)
        signature.update("${parts[0]}.${parts[1]}".toByteArray(StandardCharsets.US_ASCII))
        return signature.verify(base64Url.decode(parts[2]))
    }

    private fun publicKey(kid: String?): RSAPublicKey? {
        val jwks = readMap(fetchJwks())
        val keys = jwks["keys"] as? Collection<*> ?: return null
        val jwk = keys
            .filterIsInstance<Map<*, *>>()
            .firstOrNull { key ->
                key["kty"]?.toString() == "RSA" && (kid == null || key["kid"]?.toString() == kid)
            } ?: return null
        val modulus = jwk["n"]?.toString()?.let { BigInteger(1, base64Url.decode(it)) } ?: return null
        val exponent = jwk["e"]?.toString()?.let { BigInteger(1, base64Url.decode(it)) } ?: return null
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(RSAPublicKeySpec(modulus, exponent)) as RSAPublicKey
    }

    private fun claimsAreValid(claims: Map<String, Any?>): Boolean {
        if (claims["active"] == false) {
            return false
        }
        if (allowedIssuers.isNotEmpty() && claims["iss"]?.toString() !in allowedIssuers) {
            return false
        }
        if (expectedAudience.isNotBlank() && !audiences(claims).contains(expectedAudience)) {
            return false
        }
        val now = Instant.now().epochSecond
        val expiresAt = epochSeconds(claims["exp"]) ?: return false
        if (expiresAt + clockSkewSeconds < now) {
            return false
        }
        val notBefore = epochSeconds(claims["nbf"])
        if (notBefore != null && notBefore - clockSkewSeconds > now) {
            return false
        }
        return true
    }

    private fun rolesFromClaims(claims: Map<String, Any?>): Set<String> {
        val roles = linkedSetOf<String>()
        roles += stringSet(claims["roles"])
        val realmAccess = claims["realm_access"] as? Map<*, *>
        roles += stringSet(realmAccess?.get("roles"))
        val resourceAccess = claims["resource_access"] as? Map<*, *>
        resourceAccess
            ?.values
            ?.filterIsInstance<Map<*, *>>()
            ?.forEach { roles += stringSet(it["roles"]) }
        roles += claims["scope"]
            ?.toString()
            ?.split(" ")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        return roles
    }

    private fun customerIdFromClaims(claims: Map<String, Any?>): String? =
        claims["customerId"]?.toString()
            ?: claims["customer_id"]?.toString()
            ?: claims["banking_lab_customer_id"]?.toString()

    private fun subjectFromClaims(claims: Map<String, Any?>): String? =
        claims["preferred_username"]?.toString()?.takeIf { it.isNotBlank() }
            ?: claims["sub"]?.toString()?.takeIf { it.isNotBlank() }

    private fun audiences(claims: Map<String, Any?>): Set<String> =
        stringSet(claims["aud"])

    private fun stringSet(value: Any?): Set<String> =
        when (value) {
            is Collection<*> -> value.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }.toSet()
            is String -> value.split(" ", ",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
            else -> emptySet()
        }

    private fun epochSeconds(value: Any?): Long? =
        when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }

    private fun epochInstant(value: Any?): Instant? =
        epochSeconds(value)?.let(Instant::ofEpochSecond)

    private fun decodeJson(segment: String): ByteArray =
        base64Url.decode(segment)

    @Suppress("UNCHECKED_CAST")
    private fun readMap(json: ByteArray): Map<String, Any?> =
        objectMapper.readValue(json, Map::class.java) as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun readMap(value: String): Map<String, Any?> =
        objectMapper.readValue(value, Map::class.java) as Map<String, Any?>

    private fun fetchJwks(): ByteArray {
        val connection = URI.create(jwksUri).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 2_000
        connection.readTimeout = 2_000
        connection.requestMethod = "GET"
        return connection.inputStream.use { it.readBytes() }
    }
}
