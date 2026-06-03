package lab.banking.core.customer

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "banking-lab.customer-transfer.fault")
data class CustomerTransferFaultProperties(
    val crashAfterCommitIdempotencyKey: String? = null,
    val crashAfterCommitExitCode: Int = 89
) {
    fun shouldCrashAfterCommit(idempotencyKey: String, replayed: Boolean): Boolean =
        !replayed && crashAfterCommitIdempotencyKey?.takeIf { it.isNotBlank() } == idempotencyKey
}
