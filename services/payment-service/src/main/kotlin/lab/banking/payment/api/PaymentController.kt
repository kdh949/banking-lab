package lab.banking.payment.api

import lab.banking.payment.domain.CancelPaymentInstructionRequest
import lab.banking.payment.domain.CancelAutopayAgreementRequest
import lab.banking.payment.domain.CreatePaymentInstructionRequest
import lab.banking.payment.domain.CreateAutopayAgreementRequest
import lab.banking.payment.domain.DispatchPaymentLedgerPostingRequest
import lab.banking.payment.domain.ExecuteDueAutopayRequest
import lab.banking.payment.domain.ExecuteDueAutopayResponse
import lab.banking.payment.domain.PauseAutopayAgreementRequest
import lab.banking.payment.domain.PaymentAutopayAgreementResponse
import lab.banking.payment.domain.PaymentAutopayService
import lab.banking.payment.domain.PaymentOutboxDispatchResponse
import lab.banking.payment.domain.PaymentInstructionResponse
import lab.banking.payment.domain.PaymentInstructionService
import lab.banking.payment.domain.PaymentCancellationRequestResponse
import lab.banking.payment.domain.PaymentOutboxDispatcherService
import lab.banking.payment.domain.RecordPaymentSettlementRequest
import lab.banking.payment.domain.RequestPaymentCancellationApprovalRequest
import lab.banking.payment.domain.ResumeAutopayAgreementRequest
import lab.banking.payment.domain.ReviewPaymentCancellationRequest
import lab.banking.payment.security.PaymentAuthorizationFilter
import lab.banking.payment.security.PaymentPrincipal
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/payments")
class PaymentController(
    private val paymentInstructionService: PaymentInstructionService,
    private val paymentOutboxDispatcherService: PaymentOutboxDispatcherService,
    private val paymentAutopayService: PaymentAutopayService
) {
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
    fun instruction(
        @PathVariable instructionId: String,
        @RequestParam(required = false) reason: String?,
        request: HttpServletRequest
    ): PaymentInstructionResponse =
        paymentInstructionService.instructionRead(
            instructionId = instructionId,
            reason = reason,
            principal = request.getAttribute(PaymentAuthorizationFilter.PRINCIPAL_ATTRIBUTE) as? PaymentPrincipal
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

    @PostMapping("/instructions/{instructionId}/cancellation-requests")
    fun requestCancellationApproval(
        @PathVariable instructionId: String,
        @RequestBody request: RequestPaymentCancellationApprovalRequest,
        servletRequest: HttpServletRequest
    ): ResponseEntity<PaymentCancellationRequestResponse> {
        val response = paymentInstructionService.requestCancellationApproval(
            instructionId = instructionId,
            request = request,
            principal = servletRequest.getAttribute(PaymentAuthorizationFilter.PRINCIPAL_ATTRIBUTE) as? PaymentPrincipal
        )
        return ResponseEntity
            .status(if (response.replayed) HttpStatus.OK else HttpStatus.CREATED)
            .body(response)
    }

    @PostMapping("/cancellation-requests/{requestId}/approve")
    fun approveCancellationRequest(
        @PathVariable requestId: String,
        @RequestBody request: ReviewPaymentCancellationRequest,
        servletRequest: HttpServletRequest
    ): PaymentCancellationRequestResponse =
        paymentInstructionService.approveCancellationRequest(
            cancellationRequestId = requestId,
            request = request,
            principal = servletRequest.getAttribute(PaymentAuthorizationFilter.PRINCIPAL_ATTRIBUTE) as? PaymentPrincipal
        )

    @PostMapping("/cancellation-requests/{requestId}/reject")
    fun rejectCancellationRequest(
        @PathVariable requestId: String,
        @RequestBody request: ReviewPaymentCancellationRequest,
        servletRequest: HttpServletRequest
    ): PaymentCancellationRequestResponse =
        paymentInstructionService.rejectCancellationRequest(
            cancellationRequestId = requestId,
            request = request,
            principal = servletRequest.getAttribute(PaymentAuthorizationFilter.PRINCIPAL_ATTRIBUTE) as? PaymentPrincipal
        )

    @PostMapping("/outbox/ledger-postings/dispatch-next")
    fun dispatchNextLedgerPosting(
        @RequestBody request: DispatchPaymentLedgerPostingRequest
    ): ResponseEntity<PaymentOutboxDispatchResponse> =
        ResponseEntity.ok(paymentOutboxDispatcherService.dispatchNextLedgerPosting(request))

    @PostMapping("/autopay/agreements")
    fun createAutopayAgreement(
        @RequestBody request: CreateAutopayAgreementRequest
    ): ResponseEntity<PaymentAutopayAgreementResponse> {
        val response = paymentAutopayService.createAgreement(request)
        return ResponseEntity
            .status(if (response.replayed) HttpStatus.OK else HttpStatus.CREATED)
            .body(response)
    }

    @GetMapping("/autopay/agreements/{agreementId}")
    fun autopayAgreement(@PathVariable agreementId: String): PaymentAutopayAgreementResponse =
        PaymentAutopayAgreementResponse(
            item = paymentAutopayService.agreement(agreementId),
            replayed = false
        )

    @PostMapping("/autopay/agreements/{agreementId}/pause")
    fun pauseAutopayAgreement(
        @PathVariable agreementId: String,
        @RequestBody request: PauseAutopayAgreementRequest
    ): PaymentAutopayAgreementResponse =
        paymentAutopayService.pauseAgreement(agreementId, request)

    @PostMapping("/autopay/agreements/{agreementId}/resume")
    fun resumeAutopayAgreement(
        @PathVariable agreementId: String,
        @RequestBody request: ResumeAutopayAgreementRequest
    ): PaymentAutopayAgreementResponse =
        paymentAutopayService.resumeAgreement(agreementId, request)

    @PostMapping("/autopay/agreements/{agreementId}/cancel")
    fun cancelAutopayAgreement(
        @PathVariable agreementId: String,
        @RequestBody request: CancelAutopayAgreementRequest
    ): PaymentAutopayAgreementResponse =
        paymentAutopayService.cancelAgreement(agreementId, request)

    @PostMapping("/autopay/executions/due")
    fun executeDueAutopay(
        @RequestBody request: ExecuteDueAutopayRequest
    ): ResponseEntity<ExecuteDueAutopayResponse> =
        ResponseEntity.ok(paymentAutopayService.executeDue(request))
}
