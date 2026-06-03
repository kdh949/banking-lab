package lab.banking.core.ledger.api

import lab.banking.core.ledger.application.AdjustmentCommand
import lab.banking.core.ledger.application.DepositCommand
import lab.banking.core.ledger.application.InternalTransferCommand
import lab.banking.core.ledger.application.LedgerCommandService
import lab.banking.core.ledger.application.ReversalCommand
import lab.banking.core.ledger.application.WithdrawalCommand
import lab.banking.core.ledger.domain.LedgerCommandResult
import lab.banking.core.reconciliation.ReconciliationClosingResponse
import lab.banking.core.reconciliation.ReconciliationDailyClosingCommand
import lab.banking.core.reconciliation.ReconciliationOpsService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class LedgerController(
    private val ledgerCommandService: LedgerCommandService,
    private val reconciliationOpsService: ReconciliationOpsService
) {
    @PostMapping("/ledger/deposits")
    fun deposit(@RequestBody command: DepositCommand): ResponseEntity<LedgerCommandResult> =
        ledgerResponse(ledgerCommandService.deposit(command))

    @PostMapping("/ledger/withdrawals")
    fun withdraw(@RequestBody command: WithdrawalCommand): ResponseEntity<LedgerCommandResult> =
        ledgerResponse(ledgerCommandService.withdraw(command))

    @PostMapping("/ledger/transfers")
    fun transfer(@RequestBody command: InternalTransferCommand): ResponseEntity<LedgerCommandResult> =
        ledgerResponse(ledgerCommandService.internalTransfer(command))

    @PostMapping("/ledger/reversals")
    fun reversal(@RequestBody command: ReversalCommand): ResponseEntity<LedgerCommandResult> =
        ledgerResponse(ledgerCommandService.reverseTransaction(command))

    @PostMapping("/ledger/adjustments")
    fun adjustment(@RequestBody command: AdjustmentCommand): ResponseEntity<LedgerCommandResult> =
        ledgerResponse(ledgerCommandService.adjustment(command))

    @PostMapping("/ops/daily-closings")
    fun closeBusinessDay(@RequestBody command: ReconciliationDailyClosingCommand): ResponseEntity<ReconciliationClosingResponse> {
        val result = reconciliationOpsService.closeBusinessDay(command)
        return ResponseEntity.status(if (result.replayed) HttpStatus.OK else HttpStatus.CREATED).body(result)
    }

    private fun ledgerResponse(result: LedgerCommandResult): ResponseEntity<LedgerCommandResult> =
        ResponseEntity.status(if (result.replayed) HttpStatus.OK else HttpStatus.CREATED).body(result)
}
