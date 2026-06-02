package lab.banking.core.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "banking-lab")
data class BankingLabProperties(
    val syntheticOnly: Boolean = true,
    val nodeReferenceRuntimeRetained: Boolean = true,
    val migrationTarget: String = "kotlin-spring-boot"
)
