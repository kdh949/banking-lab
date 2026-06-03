package lab.banking.core.ledger.domain

import java.time.LocalDate
import java.time.OffsetDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LedgerInvariantsTest {
    @Test
    fun `internal transfer creates balanced double-entry postings`() {
        val transaction = internalTransfer(
            transactionId = "TX-TEST-001",
            fromAccountId = "ACC-A",
            toAccountId = "ACC-B",
            amountMinor = 1_000,
            idempotencyKey = "IDEMP-001"
        )

        assertTransactionBalanced(transaction)

        assertEquals(2, transaction.postings.size)
        assertEquals(-1_000, signedAmount(transaction.postings[0]))
        assertEquals(1_000, signedAmount(transaction.postings[1]))
    }

    @Test
    fun `balance projection is derived from postings`() {
        val transaction = internalTransfer(
            transactionId = "TX-TEST-002",
            fromAccountId = "ACC-A",
            toAccountId = "ACC-B",
            amountMinor = 2_500,
            idempotencyKey = "IDEMP-002"
        )

        val balances = projectBalances(
            transactions = listOf(transaction),
            accounts = listOf("ACC-A", "ACC-B")
        )

        assertEquals(-2_500, balances.getValue("ACC-A"))
        assertEquals(2_500, balances.getValue("ACC-B"))
    }

    @Test
    fun `reversal references original transaction and restores projected balances`() {
        val original = internalTransfer(
            transactionId = "TX-TEST-004",
            fromAccountId = "ACC-A",
            toAccountId = "ACC-B",
            amountMinor = 750,
            idempotencyKey = "IDEMP-004"
        )
        val reversal = reversal(
            transactionId = "TX-TEST-004-R",
            original = original,
            idempotencyKey = "IDEMP-004-R"
        )

        val balances = projectBalances(
            transactions = listOf(original, reversal),
            accounts = listOf("ACC-A", "ACC-B")
        )

        assertEquals(original.id, reversal.originalTransactionId)
        assertEquals(0, balances.getValue("ACC-A"))
        assertEquals(0, balances.getValue("ACC-B"))
    }

    @Test
    fun `unbalanced ledger transaction is rejected`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            LedgerInvariants.requireBalanced(
                "TX-BAD-001",
                listOf(
                    LedgerPostingInput("ACC-A", PostingDirection.DEBIT, 100),
                    LedgerPostingInput("ACC-B", PostingDirection.CREDIT, 90)
                )
            )
        }

        assertEquals(true, error.message?.contains("not balanced"))
    }

    @Test
    fun `ledger transaction requires at least two postings and positive amounts`() {
        val singlePosting = assertThrows(IllegalArgumentException::class.java) {
            LedgerInvariants.requireBalanced(
                "TX-BAD-002",
                listOf(LedgerPostingInput("ACC-A", PostingDirection.DEBIT, 100))
            )
        }
        val nonPositiveAmount = assertThrows(IllegalArgumentException::class.java) {
            LedgerInvariants.requireBalanced(
                "TX-BAD-003",
                listOf(
                    LedgerPostingInput("ACC-A", PostingDirection.DEBIT, 0),
                    LedgerPostingInput("ACC-B", PostingDirection.CREDIT, 0)
                )
            )
        }

        assertTrue(singlePosting.message?.contains("at least two postings") == true)
        assertTrue(nonPositiveAmount.message?.contains("positive integer minor-unit value") == true)
    }

    private fun internalTransfer(
        transactionId: String,
        fromAccountId: String,
        toAccountId: String,
        amountMinor: Long,
        idempotencyKey: String
    ): LedgerTransactionDto =
        ledgerTransaction(
            transactionId = transactionId,
            transactionType = "INTERNAL_TRANSFER",
            businessReferenceId = transactionId,
            idempotencyKey = idempotencyKey,
            requestedBy = "customer01",
            requestedChannel = "CUSTOMER_WEB",
            postings = listOf(
                posting(transactionId, 1, fromAccountId, PostingDirection.DEBIT, amountMinor),
                posting(transactionId, 2, toAccountId, PostingDirection.CREDIT, amountMinor)
            )
        )

    private fun reversal(
        transactionId: String,
        original: LedgerTransactionDto,
        idempotencyKey: String
    ): LedgerTransactionDto =
        ledgerTransaction(
            transactionId = transactionId,
            transactionType = "REVERSAL",
            businessReferenceId = "${original.businessReferenceId}-REV",
            idempotencyKey = idempotencyKey,
            requestedBy = "branch01",
            requestedChannel = "STAFF_TERMINAL",
            originalTransactionId = original.id,
            postings = original.postings.mapIndexed { index, posting ->
                posting.copy(
                    id = "$transactionId-P${(index + 1).toString().padStart(3, '0')}",
                    ledgerTransactionId = transactionId,
                    direction = if (posting.direction == PostingDirection.DEBIT) {
                        PostingDirection.CREDIT
                    } else {
                        PostingDirection.DEBIT
                    },
                    postingType = "REVERSAL"
                )
            }
        )

    private fun ledgerTransaction(
        transactionId: String,
        transactionType: String,
        businessReferenceId: String,
        idempotencyKey: String,
        requestedBy: String,
        requestedChannel: String,
        postings: List<LedgerPostingDto>,
        originalTransactionId: String? = null
    ): LedgerTransactionDto {
        val transaction = LedgerTransactionDto(
            id = transactionId,
            transactionType = transactionType,
            businessReferenceId = businessReferenceId,
            idempotencyKey = idempotencyKey,
            businessDate = LocalDate.of(2026, 6, 3),
            status = "POSTED",
            requestedBy = requestedBy,
            requestedChannel = requestedChannel,
            postedAt = OffsetDateTime.parse("2026-06-03T00:00:00Z"),
            originalTransactionId = originalTransactionId,
            postings = postings
        )
        assertTransactionBalanced(transaction)
        return transaction
    }

    private fun posting(
        transactionId: String,
        postingNumber: Int,
        accountId: String,
        direction: PostingDirection,
        amountMinor: Long
    ): LedgerPostingDto =
        LedgerPostingDto(
            id = "$transactionId-P${postingNumber.toString().padStart(3, '0')}",
            ledgerTransactionId = transactionId,
            accountId = accountId,
            currency = "KRW",
            direction = direction,
            amountMinor = amountMinor,
            postingType = "PRINCIPAL"
        )

    private fun assertTransactionBalanced(transaction: LedgerTransactionDto) {
        LedgerInvariants.requireBalanced(
            transaction.id,
            transaction.postings.map {
                LedgerPostingInput(
                    accountId = it.accountId,
                    direction = it.direction,
                    amountMinor = it.amountMinor,
                    currency = it.currency,
                    postingType = it.postingType
                )
            }
        )
    }

    private fun projectBalances(
        transactions: List<LedgerTransactionDto>,
        accounts: List<String>
    ): Map<String, Long> {
        val balances = accounts.associateWith { 0L }.toMutableMap()
        transactions
            .filter { it.status in setOf("POSTED", "REVERSED") }
            .forEach { transaction ->
                assertTransactionBalanced(transaction)
                transaction.postings.forEach { posting ->
                    balances[posting.accountId] = balances.getOrDefault(posting.accountId, 0L) + signedAmount(posting)
                }
            }
        return balances
    }

    private fun signedAmount(posting: LedgerPostingDto): Long =
        when (posting.direction) {
            PostingDirection.DEBIT -> -posting.amountMinor
            PostingDirection.CREDIT -> posting.amountMinor
        }
}
