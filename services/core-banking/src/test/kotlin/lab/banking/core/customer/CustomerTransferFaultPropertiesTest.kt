package lab.banking.core.customer

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomerTransferFaultPropertiesTest {
    @Test
    fun `post-commit crash fault only matches first execution for configured idempotency key`() {
        val properties = CustomerTransferFaultProperties(
            crashAfterCommitIdempotencyKey = "IDEMP-API-CRASH-001",
            crashAfterCommitExitCode = 89
        )

        assertTrue(properties.shouldCrashAfterCommit("IDEMP-API-CRASH-001", replayed = false))
        assertFalse(properties.shouldCrashAfterCommit("IDEMP-API-CRASH-001", replayed = true))
        assertFalse(properties.shouldCrashAfterCommit("IDEMP-OTHER", replayed = false))
    }
}
