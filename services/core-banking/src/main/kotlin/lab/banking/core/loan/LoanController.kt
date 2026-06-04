package lab.banking.core.loan

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/loans")
class LoanController(
    private val service: LoanService
) {
    @GetMapping("/products")
    fun products(): ResponseEntity<LoanProductListResponse> =
        ResponseEntity.ok(service.products())

    @PostMapping("/applications")
    fun apply(@RequestBody command: LoanApplicationCommand): ResponseEntity<LoanApplicationResponse> {
        val response = service.apply(command)
        return ResponseEntity.status(if (response.replayed) HttpStatus.OK else HttpStatus.CREATED).body(response)
    }

    @GetMapping("/{loanId}")
    fun detail(
        @PathVariable loanId: String,
        @RequestParam(required = false) reason: String?
    ): ResponseEntity<LoanDto> =
        ResponseEntity.ok(service.loan(loanId, reason))

    @PostMapping("/{loanId}/repayments")
    fun repay(
        @PathVariable loanId: String,
        @RequestBody command: LoanPaymentCommand
    ): ResponseEntity<LoanPaymentResponse> =
        ResponseEntity.ok(service.repay(loanId, command))

    @PostMapping("/{loanId}/prepayments")
    fun prepay(
        @PathVariable loanId: String,
        @RequestBody command: LoanPaymentCommand
    ): ResponseEntity<LoanPaymentResponse> =
        ResponseEntity.ok(service.prepay(loanId, command))

    @PostMapping("/{loanId}/accruals/run")
    fun runAccrual(
        @PathVariable loanId: String,
        @RequestBody command: LoanAccrualCommand
    ): ResponseEntity<LoanAccrualResponse> =
        ResponseEntity.ok(service.runAccrual(loanId, command))
}
