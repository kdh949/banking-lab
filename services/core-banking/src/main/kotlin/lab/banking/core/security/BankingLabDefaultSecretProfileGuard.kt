package lab.banking.core.security

import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
class BankingLabDefaultSecretProfileGuard(
    private val environment: Environment,
    @param:Value("\${spring.datasource.password:}")
    private val datasourcePassword: String,
    @param:Value("\${spring.datasource.username:}")
    private val datasourceUsername: String
) : InitializingBean {
    override fun afterPropertiesSet() {
        val prodLike = environment.activeProfiles.map { it.lowercase() }.any { it in PROD_LIKE_PROFILES }
        if (!prodLike) {
            return
        }
        val normalizedPassword = datasourcePassword.trim()
        if (normalizedPassword in DISALLOWED_DEFAULT_PASSWORDS) {
            throw IllegalStateException(
                "default synthetic database password is not allowed for prod-like profiles"
            )
        }
        if (datasourceUsername.trim() == "banking_lab" && normalizedPassword.startsWith("replace-with-")) {
            throw IllegalStateException(
                "placeholder database password is not allowed for prod-like profiles"
            )
        }
    }

    companion object {
        private val PROD_LIKE_PROFILES = setOf("prod", "production", "prod-like", "prodlike")
        private val DISALLOWED_DEFAULT_PASSWORDS = setOf(
            "",
            "banking_lab",
            "admin",
            "password",
            "replace-with-local-synthetic-password"
        )
    }
}
