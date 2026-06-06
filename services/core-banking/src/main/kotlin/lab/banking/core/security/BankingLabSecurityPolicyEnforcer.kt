package lab.banking.core.security

import jakarta.servlet.http.HttpServletRequest
import java.time.Instant
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component

@Component
class BankingLabSecurityPolicyEnforcer(
    private val jdbc: NamedParameterJdbcTemplate,
    @param:Value("\${banking-lab.security.step-up.enforcement-enabled:true}")
    private val stepUpEnforcementEnabled: Boolean,
    @param:Value("\${banking-lab.security.step-up.max-age-seconds:300}")
    private val stepUpMaxAgeSeconds: Long,
    @param:Value("\${banking-lab.security.trusted-device-enforcement-enabled:true}")
    private val trustedDeviceEnforcementEnabled: Boolean,
    @param:Value("\${banking-lab.security.session.enforcement-enabled:true}")
    private val sessionEnforcementEnabled: Boolean,
    @param:Value("\${banking-lab.security.session.max-age-seconds:3600}")
    private val sessionMaxAgeSeconds: Long
) {
    fun denialFor(request: HttpServletRequest, principal: BankingLabPrincipal): SecurityPolicyDenial? {
        sessionDenial(request, principal)?.let { return it }
        trustedDeviceDenial(request, principal)?.let { return it }
        stepUpDenial(request, principal)?.let { return it }
        return null
    }

    private fun sessionDenial(request: HttpServletRequest, principal: BankingLabPrincipal): SecurityPolicyDenial? {
        if (!sessionEnforcementEnabled) {
            return null
        }
        val sessionRequired = request.requestURI == "/api/auth/session"
        val sessionId = principal.sessionId?.takeIf { it.isNotBlank() }
        if (sessionRequired && sessionId == null) {
            return SecurityPolicyDenial(
                code = "SESSION_REQUIRED",
                status = HttpStatus.UNAUTHORIZED,
                policy = "SESSION_BINDING_REQUIRED",
                message = "authenticated session claim is required",
                cause = "The synthetic signed token did not include a session identifier for session validation.",
                fix = "Retry after completing Keycloak login so the token includes a valid sid claim."
            )
        }
        if (sessionId == null) {
            return null
        }
        if (sessionRevoked(sessionId)) {
            return SecurityPolicyDenial(
                code = "SESSION_REVOKED",
                status = HttpStatus.UNAUTHORIZED,
                policy = "SESSION_REVOCATION",
                message = "authenticated session has been revoked",
                cause = "The session id is present in the synthetic revoked session registry.",
                fix = "Force logout locally, complete Keycloak login again, and retry with a fresh session."
            )
        }
        val startedAt = principal.authTime ?: principal.issuedAt
        if (startedAt == null && sessionRequired) {
            return SecurityPolicyDenial(
                code = "SESSION_REQUIRED",
                status = HttpStatus.UNAUTHORIZED,
                policy = "SESSION_BINDING_REQUIRED",
                message = "authenticated session freshness claim is required",
                cause = "The synthetic signed token did not include auth_time or iat for session freshness checks.",
                fix = "Retry after completing Keycloak login so the token includes a fresh authentication timestamp."
            )
        }
        if (startedAt != null && startedAt.plusSeconds(sessionMaxAgeSeconds).isBefore(Instant.now())) {
            return SecurityPolicyDenial(
                code = "SESSION_EXPIRED",
                status = HttpStatus.UNAUTHORIZED,
                policy = "SESSION_TIMEOUT",
                message = "authenticated session has expired",
                cause = "The token authentication timestamp is older than the modeled session TTL.",
                fix = "Re-authenticate with Keycloak before retrying the synthetic banking operation."
            )
        }
        return null
    }

    private fun trustedDeviceDenial(request: HttpServletRequest, principal: BankingLabPrincipal): SecurityPolicyDenial? {
        if (!trustedDeviceEnforcementEnabled || !trustedDeviceRequired(request)) {
            return null
        }
        val actorType = if (principal.roles.contains("CUSTOMER")) "CUSTOMER" else "STAFF"
        val actorId = if (actorType == "CUSTOMER") principal.customerId?.takeIf { it.isNotBlank() } else principal.subject
        val deviceFingerprint = principal.deviceFingerprint?.takeIf { it.isNotBlank() }
        if (actorId == null || deviceFingerprint == null) {
            return SecurityPolicyDenial(
                code = "TRUSTED_DEVICE_REQUIRED",
                status = HttpStatus.FORBIDDEN,
                policy = "TRUSTED_DEVICE_BINDING_REQUIRED",
                message = "trusted device binding is required for this route",
                cause = "The token did not include a device fingerprint tied to an active synthetic trusted device.",
                fix = "Register or bind the synthetic trusted device before retrying."
            )
        }
        if (!trustedDeviceActive(actorType, actorId, deviceFingerprint)) {
            return SecurityPolicyDenial(
                code = "TRUSTED_DEVICE_REQUIRED",
                status = HttpStatus.FORBIDDEN,
                policy = "TRUSTED_DEVICE_BINDING_REQUIRED",
                message = "device is not trusted for this actor",
                cause = "The device fingerprint is missing, pending, revoked, or expired in the synthetic trusted device registry.",
                fix = "Approve the synthetic trusted device binding or retry from an active registered device."
            )
        }
        jdbc.update(
            """
            UPDATE trusted_devices
            SET last_seen_at = now()
            WHERE actor_type = :actorType
              AND actor_id = :actorId
              AND device_fingerprint = :deviceFingerprint
            """.trimIndent(),
            mapOf(
                "actorType" to actorType,
                "actorId" to actorId,
                "deviceFingerprint" to deviceFingerprint
            )
        )
        return null
    }

    private fun stepUpDenial(request: HttpServletRequest, principal: BankingLabPrincipal): SecurityPolicyDenial? {
        if (!stepUpEnforcementEnabled || !highRiskStepUpRequired(request)) {
            return null
        }
        val authTime = principal.authTime
        val fresh = authTime != null && authTime.plusSeconds(stepUpMaxAgeSeconds).isAfter(Instant.now())
        if (!fresh || !principal.hasStepUpAuthentication()) {
            return SecurityPolicyDenial(
                code = "STEP_UP_REQUIRED",
                status = HttpStatus.FORBIDDEN,
                policy = "STEP_UP_REAUTHENTICATION_REQUIRED",
                message = "fresh step-up authentication is required for this high-risk command",
                cause = "The route models a privileged banking operation and the token lacks a fresh MFA/WebAuthn step-up claim.",
                fix = "Complete synthetic Keycloak MFA/WebAuthn re-authentication and retry with a fresh token."
            )
        }
        return null
    }

    private fun trustedDeviceRequired(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        val method = request.method
        return (method.equals("POST", ignoreCase = true) && path == "/api/customer/transfers") ||
            path == "/api/auth/session"
    }

    private fun highRiskStepUpRequired(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        if (!request.method.equals("POST", ignoreCase = true)) {
            return false
        }
        return path == "/api/staff/pii/unmask" ||
            path.startsWith("/api/audit/exports") ||
            path.startsWith("/api/ops/security/") ||
            path.startsWith("/api/ops/ledger/projection-rebuild-requests") ||
            path.startsWith("/api/aml/governance/") ||
            path == "/api/ops/daily-closings" ||
            path.matches(Regex("^/api/(staff/)?approvals/[^/]+/(approve|reject)$")) ||
            path.endsWith("/change-requests") && (
                path.startsWith("/api/ops/parameters/") ||
                    path.startsWith("/api/staff/audit-parameters") ||
                    path.startsWith("/api/staff/fds-parameters") ||
                    path.startsWith("/api/admin/platform/security-parameters") ||
                    path.startsWith("/api/admin/platform/authorization-parameters")
                )
    }

    private fun trustedDeviceActive(actorType: String, actorId: String, deviceFingerprint: String): Boolean =
        count(
            """
            SELECT count(*)
            FROM trusted_devices
            WHERE actor_type = :actorType
              AND actor_id = :actorId
              AND device_fingerprint = :deviceFingerprint
              AND status = 'ACTIVE'
              AND (expires_at IS NULL OR expires_at > now())
            """.trimIndent(),
            mapOf("actorType" to actorType, "actorId" to actorId, "deviceFingerprint" to deviceFingerprint)
        ) > 0

    private fun sessionRevoked(sessionId: String): Boolean =
        count(
            """
            SELECT count(*)
            FROM revoked_sessions
            WHERE session_id = :sessionId
              AND (expires_at IS NULL OR expires_at > now())
            """.trimIndent(),
            mapOf("sessionId" to sessionId)
        ) > 0

    private fun count(sql: String, params: Map<String, Any?>): Int =
        jdbc.queryForObject(sql, params, Int::class.java) ?: 0
}

data class SecurityPolicyDenial(
    val code: String,
    val status: HttpStatus,
    val policy: String,
    val message: String,
    val cause: String,
    val fix: String
)
