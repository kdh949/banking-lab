package lab.banking.reporting.security

import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
class ReportingSimulatorTokenProfileGuard(
    private val environment: Environment,
    @param:Value("\${banking-lab.security.simulator-tokens-enabled:false}")
    private val simulatorTokensEnabled: Boolean,
    @param:Value("\${banking-lab.security.dev-simulator-token-enabled:false}")
    private val devSimulatorTokenEnabled: Boolean
) : InitializingBean {
    override fun afterPropertiesSet() {
        val prodLike = environment.activeProfiles.map { it.lowercase() }.any { it in PROD_LIKE_PROFILES }
        if (prodLike && (simulatorTokensEnabled || devSimulatorTokenEnabled)) {
            throw IllegalStateException("simulator tokens are dev/test only and must be disabled for prod-like profiles")
        }
    }

    companion object {
        private val PROD_LIKE_PROFILES = setOf("prod", "production", "prod-like", "prodlike")
    }
}
