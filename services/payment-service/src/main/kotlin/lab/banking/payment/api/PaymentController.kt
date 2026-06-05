package lab.banking.payment.api

import lab.banking.payment.domain.CancelPaymentInstructionRequest
import lab.banking.payment.domain.CreatePaymentInstructionRequest
import lab.banking.payment.domain.PaymentInstructionResponse
import lab.banking.payment.domain.PaymentInstructionService
import lab.banking.payment.domain.RecordPaymentSettlementRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/payments")
class PaymentController(private val paymentInstructionService: PaymentInstructionService) {
    @PostMapping("/instructions")
    fun createInstruction(
        @RequestBody request: CreatePaymentInstructionRequest
    ): ResponseEntity<PaymentInstructionResponse> {
        val response = paymentInstructionService.createInstruction(request)
        return ResponseEntity
            .status(if (response.replayed) HttpStatus.OK else HttpStatus.CREATED)
            .body(response)
    }

    @GetMapping("/instructions/{instructionId}")
    fun instruction(@PathVariable instructionId: String): PaymentInstructionResponse =
        PaymentInstructionResponse(
            item = paymentInstructionService.instruction(instructionId),
            replayed = false
        )

    @PostMapping("/instructions/{instructionId}/settlements")
    fun recordSettlement(
        @PathVariable instructionId: String,
        @RequestBody request: RecordPaymentSettlementRequest
    ): PaymentInstructionResponse =
        paymentInstructionService.recordSettlement(instructionId, request)

    @PostMapping("/instructions/{instructionId}/cancel")
    fun cancel(
        @PathVariable instructionId: String,
        @RequestBody request: CancelPaymentInstructionRequest
    ): PaymentInstructionResponse =
        paymentInstructionService.cancelInstruction(instructionId, request)
}
