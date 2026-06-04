package lab.banking.core.statement

import java.time.LocalDate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class StatementController(
    private val statementService: StatementService
) {
    @GetMapping("/api/customers/{customerId}/statements")
    fun customerStatement(
        @PathVariable customerId: String,
        @RequestParam from: LocalDate,
        @RequestParam to: LocalDate,
        @RequestParam(required = false) reason: String?
    ): CustomerStatementDto =
        statementService.customerStatement(customerId, from, to, reason)

    @GetMapping("/api/customers/{customerId}/access-history")
    fun accessHistory(
        @PathVariable customerId: String,
        @RequestParam(required = false) reason: String?
    ): CustomerAccessHistoryDto =
        statementService.accessHistory(customerId, reason)

    @GetMapping("/api/transactions/{transactionId}/confirmation")
    fun transactionConfirmation(
        @PathVariable transactionId: String,
        @RequestParam(required = false) reason: String?
    ): TransactionConfirmationDto =
        statementService.transactionConfirmation(transactionId, reason)

    @GetMapping("/api/accounts/{accountId}/balance-certificate")
    fun balanceCertificate(
        @PathVariable accountId: String,
        @RequestParam date: LocalDate,
        @RequestParam(required = false) reason: String?
    ): BalanceCertificateDto =
        statementService.balanceCertificate(accountId, date, reason)
}
