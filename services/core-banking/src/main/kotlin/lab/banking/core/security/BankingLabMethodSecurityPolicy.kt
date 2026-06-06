package lab.banking.core.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class BankingLabMethodSecurityPolicy(
    @param:Value("\${banking-lab.security.enabled:true}")
    private val securityEnabled: Boolean
) {
    fun securityDisabled(): Boolean = !securityEnabled
}
