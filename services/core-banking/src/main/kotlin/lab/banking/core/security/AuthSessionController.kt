package lab.banking.core.security

import java.time.OffsetDateTime
import java.time.ZoneOffset
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthSessionController {
    @GetMapping("/session")
    fun session(): AuthSessionResponse {
        val principal = BankingLabAuthContext.get()
            ?: throw WorkflowErrors.authorizationViolation("authenticated session context is required")
        return AuthSessionResponse(
            subject = principal.subject,
            roles = principal.roles.sorted(),
            customerId = principal.customerId,
            issuer = principal.issuer,
            sessionId = principal.sessionId,
            deviceFingerprint = principal.deviceFingerprint,
            authTime = principal.authTime?.atOffset(ZoneOffset.UTC),
            issuedAt = principal.issuedAt?.atOffset(ZoneOffset.UTC),
            stepUpSatisfied = principal.hasStepUpAuthentication(),
            syntheticOnly = true
        )
    }
}

data class AuthSessionResponse(
    val subject: String,
    val roles: List<String>,
    val customerId: String?,
    val issuer: String?,
    val sessionId: String?,
    val deviceFingerprint: String?,
    val authTime: OffsetDateTime?,
    val issuedAt: OffsetDateTime?,
    val stepUpSatisfied: Boolean,
    val syntheticOnly: Boolean
)
