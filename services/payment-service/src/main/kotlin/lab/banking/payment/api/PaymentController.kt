package lab.banking.payment.api

import jakarta.servlet.http.HttpServletRequest
import lab.banking.payment.domain.CancelAutopayAgreementRequest
import lab.banking.payment.domain.CancelPaymentInstructionRequest
import lab.banking.payment.domain.CreateAutopayAgreementRequest
import lab.banking.payment.domain.CreatePaymentInstructionRequest
import lab.banking.payment.domain.DispatchPaymentLedgerPostingRequest
import lab.banking.payment.domain.ExecuteDueAutopayRequest
import lab.banking.payment.domain.ExecuteDueAutopayResponse
import lab.banking.payment.domain.PauseAutopayAgreementRequest
import lab.banking.payment.domain.PaymentAutopayAgreementResponse
import lab.banking.payment.domain.PaymentAutopayService
import lab.banking.payment.domain.PaymentCancellationRequestResponse
import lab.banking.payment.domain.PaymentInstructionResponse
import lab.banking.payment.domain.PaymentInstructionService
import lab.banking.payment.domain.PaymentOutboxDispatchResponse
import lab.banking.payment.domain.PaymentOutboxDispatcherService
import lab.banking.payment.domain.RecordPaymentLedgerPostingRequest
import lab.banking.payment.domain.RequestPaymentCancellationApprovalRequest
import lab.banking.payment.domain.ResumeAutopayAgreementRequest
import lab.banking.payment.domain.ReviewPaymentCancellationRequest
import lab.banking.payment.security.PaymentAuthorizationFilter
import lab.banking.payment.security.PaymentCustomerRequestBinder
import lab.banking.payment.security.PaymentPrincipal
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
@RequestMapping("/api/payments")
class PaymentController(
    private val paymentInstructionService: PaymentInstructionService,
    private val paymentOutboxDispatcherService: PaymentOutboxDispatcherService,
    private val paymentAutopayService: PaymentAutopayService,
    private val paymentCustomerRequestBinder: PaymentCustomerRequestBinder
) {
    @PostMapping("/instructions")
    fun createInstruction(
        @RequestBody request: CreatePaymentInstructionRequest,
        servletRequest: HttpServletRequest
    ): ResponseEntity<PaymentInstructionResponse> {
        val response = paymentInstructionService.createInstruction(
            paymentCustomerRequestBinder.bindCreateInstruction(request, servletRequest)
        )
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

    @PostMapping("/instructions/{instructionId}/ledger-postings")
    fun recordLedgerPosting(
        @PathVariable instructionId: String,
        @RequestBody request: RecordPaymentLedgerPostingRequest
    ): PaymentInstructionResponse =
        paymentInstructionService.recordLedgerPosting(instructionId, request)

    @Deprecated("Use /api/payments/instructions/{instructionId}/ledger-postings")
    @PostMapping("/instructions/{instructionId}/settlements")
    fun recordSettlementCompatibility(
        @PathVariable instructionId: String,
        @RequestBody request: RecordPaymentLedgerPostingRequest
    ): PaymentInstructionResponse =
        paymentInstructionService.recordLedgerPosting(instructionId, request)

    @PostMapping("/instructions/{instructionId}/cancel")
    fun cancel(
        @PathVariable instructionId: String,
        @RequestBody request: CancelPaymentInstructionRequest,
        servletRequest: HttpServletRequest
    ): PaymentInstructionResponse =
        paymentInstructionService.cancelInstruction(
            instructionId,
            paymentCustomerRequestBinder.bindCancelInstruction(request, servletRequest)
        )

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
        @RequestBody request: CreateAutopayAgreementRequest,
        servletRequest: HttpServletRequest
    ): ResponseEntity<PaymentAutopayAgreementResponse> {
        val response = paymentAutopayService.createAgreement(
            paymentCustomerRequestBinder.bindCreateAutopayAgreement(request, servletRequest)
        )
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
        @RequestBody request: PauseAutopayAgreementRequest,
        servletRequest: HttpServletRequest
    ): PaymentAutopayAgreementResponse =
        paymentAutopayService.pauseAgreement(
            agreementId,
            paymentCustomerRequestBinder.bindPauseAutopayAgreement(request, servletRequest)
        )

    @PostMapping("/autopay/agreements/{agreementId}/resume")
    fun resumeAutopayAgreement(
        @PathVariable agreementId: String,
        @RequestBody request: ResumeAutopayAgreementRequest,
        servletRequest: HttpServletRequest
    ): PaymentAutopayAgreementResponse =
        paymentAutopayService.resumeAgreement(
            agreementId,
            paymentCustomerRequestBinder.bindResumeAutopayAgreement(request, servletRequest)
        )

    @PostMapping("/autopay/agreements/{agreementId}/cancel")
    fun cancelAutopayAgreement(
        @PathVariable agreementId: String,
        @RequestBody request: CancelAutopayAgreementRequest,
        servletRequest: HttpServletRequest
    ): PaymentAutopayAgreementResponse =
        paymentAutopayService.cancelAgreement(
            agreementId,
            paymentCustomerRequestBinder.bindCancelAutopayAgreement(request, servletRequest)
        )

    @PostMapping("/autopay/executions/due")
    fun executeDueAutopay(
        @RequestBody request: ExecuteDueAutopayRequest
    ): ResponseEntity<ExecuteDueAutopayResponse> =
        ResponseEntity.ok(paymentAutopayService.executeDue(request))
}
