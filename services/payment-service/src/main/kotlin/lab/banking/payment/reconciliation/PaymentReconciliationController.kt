
package lab.banking.payment.reconciliation

import jakarta.servlet.http.HttpServletRequest
import lab.banking.payment.security.PaymentAuthorizationFilter
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
@RequestMapping("/api/payments/reconciliation")
class PaymentReconciliationController(
    private val service: PaymentReconciliationService
) {
    @PostMapping("/runs")
    fun createRun(
        @RequestBody request: CreatePaymentReconciliationRunRequest,
        servletRequest: HttpServletRequest
    ): ResponseEntity<PaymentReconciliationRunResponse> {
        val response = service.createRun(request, principal(servletRequest))
        return ResponseEntity
            .status(if (response.replayed) HttpStatus.OK else HttpStatus.CREATED)
            .body(response)
    }

    @GetMapping("/runs/{runId}")
    fun run(
        @PathVariable runId: String,
        @RequestParam(required = false) reason: String?,
        servletRequest: HttpServletRequest
    ): PaymentReconciliationRunResponse =
        service.run(runId, reason, principal(servletRequest))

    @GetMapping("/exceptions")
    fun exceptions(
        @RequestParam(required = false) ownerId: String?,
        @RequestParam(required = false) status: PaymentReconciliationResultStatus?,
        @RequestParam(defaultValue = "false") overdueOnly: Boolean,
        @RequestParam(required = false) reason: String?,
        servletRequest: HttpServletRequest
    ): PaymentReconciliationExceptionListResponse =
        service.exceptions(ownerId, status, overdueOnly, reason, principal(servletRequest))

    private fun principal(request: HttpServletRequest): PaymentPrincipal? =
        request.getAttribute(PaymentAuthorizationFilter.PRINCIPAL_ATTRIBUTE) as? PaymentPrincipal
}
