package lab.banking.core.security

import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
class BankingLabSimulatorTokenProfileGuard(
    private val environment: Environment,
    @param:Value("\${banking-lab.security.simulator-tokens-enabled:false}")
    private val simulatorTokensEnabled: Boolean,
    @param:Value("\${banking-lab.security.dev-simulator-token-enabled:false}")
    private val devSimulatorTokenEnabled: Boolean,
    @param:Value("\${banking-lab.security.customer-auth.synthetic-token-issuer-enabled:false}")
    private val syntheticCustomerAuthTokenIssuerEnabled: Boolean
) : InitializingBean {
    override fun afterPropertiesSet() {
        val activeProfiles = environment.activeProfiles.map { it.lowercase() }.toSet()
        val prodLike = activeProfiles.any { it in PROD_LIKE_PROFILES }
        if (prodLike && (simulatorTokensEnabled || devSimulatorTokenEnabled)) {
            throw IllegalStateException(
                "simulator tokens are dev/test only and must be disabled for prod-like profiles"
            )
        }
        if (prodLike && syntheticCustomerAuthTokenIssuerEnabled) {
            throw IllegalStateException(
                "synthetic customer auth token issuance is dev/test only and must be disabled for prod-like profiles"
            )
        }
    }

    companion object {
        private val PROD_LIKE_PROFILES = setOf("prod", "production", "prod-like", "prodlike")
    }
}
