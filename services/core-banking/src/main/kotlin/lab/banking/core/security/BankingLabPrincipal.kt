package lab.banking.core.security

import lab.banking.core.workflow.WorkflowErrors

data class BankingLabPrincipal(
    val subject: String,
    val roles: Set<String>,
    val customerId: String? = null,
    val issuer: String? = null
) {
    fun hasAnyRole(allowed: Set<String>): Boolean =
        roles.any(allowed::contains)
}

object BankingLabAuthContext {
    private val current = ThreadLocal<BankingLabPrincipal?>()

    fun set(principal: BankingLabPrincipal?) {
        current.set(principal)
    }

    fun get(): BankingLabPrincipal? =
        current.get()

    fun clear() {
        current.remove()
    }

    fun requireActor(actorId: String?, role: String?) {
        val principal = current.get() ?: return
        if (!actorId.isNullOrBlank() && actorId != principal.subject) {
            throw WorkflowErrors.authorizationViolation("authenticated actor does not match request actor")
        }
        if (!role.isNullOrBlank() && !principal.roles.contains(role)) {
            throw WorkflowErrors.authorizationViolation("authenticated role does not match request role")
        }
    }

    fun requireCustomerOwnership(customerId: String) {
        val principal = current.get() ?: return
        if (principal.roles.contains("CUSTOMER") && principal.customerId != customerId) {
            throw WorkflowErrors.authorizationViolation("customer token cannot access another customer")
        }
    }
}
