package lab.banking.core.ledger.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class LedgerInvariantsTest {
    @Test
    fun `balanced double entry postings are accepted`() {
        val postings = listOf(
            LedgerPostingInput("ACC-A", PostingDirection.DEBIT, 1_000),
            LedgerPostingInput("ACC-B", PostingDirection.CREDIT, 1_000)
        )

        LedgerInvariants.requireBalanced("TX-UNIT-001", postings)

        assertEquals(-1_000, LedgerInvariants.signedAmount(postings[0]))
        assertEquals(1_000, LedgerInvariants.signedAmount(postings[1]))
    }

    @Test
    fun `unbalanced postings are rejected`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            LedgerInvariants.requireBalanced(
                "TX-UNIT-002",
                listOf(
                    LedgerPostingInput("ACC-A", PostingDirection.DEBIT, 1_000),
                    LedgerPostingInput("ACC-B", PostingDirection.CREDIT, 900)
                )
            )
        }

        assertEquals(true, error.message?.contains("not balanced"))
    }
}
