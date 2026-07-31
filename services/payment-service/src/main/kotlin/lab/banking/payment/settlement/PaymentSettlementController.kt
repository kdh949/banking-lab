package lab.banking.payment.settlement

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
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/payments/settlement")
class PaymentSettlementController(
    private val paymentSettlementService: PaymentSettlementService
) {
    @PostMapping("/imports")
    fun importExternalSettlementCsv(
        @RequestBody request: ImportExternalSettlementCsvRequest,
        servletRequest: HttpServletRequest
    ): ResponseEntity<ExternalSettlementImportResponse> {
        val response = paymentSettlementService.importExternalCsv(
            request = request,
            principal = principal(servletRequest)
        )
        return ResponseEntity
            .status(if (response.replayed) HttpStatus.OK else HttpStatus.CREATED)
            .body(response)
    }

    @GetMapping("/imports/{importId}")
    fun getExternalSettlementImport(
        @PathVariable importId: String
    ): ExternalSettlementImportResponse =
        paymentSettlementService.externalImport(importId)

    @PostMapping("/batch-runs")
    fun createSettlementBatchRun(
        @RequestBody request: CreateSettlementBatchRunRequest,
        servletRequest: HttpServletRequest
    ): ResponseEntity<SettlementBatchRunResponse> {
        val response = paymentSettlementService.createBatchRun(
            request = request,
            principal = principal(servletRequest)
        )
        return ResponseEntity
            .status(if (response.replayed) HttpStatus.OK else HttpStatus.CREATED)
            .body(response)
    }

    @GetMapping("/batch-runs/{batchRunId}")
    fun getSettlementBatchRun(
        @PathVariable batchRunId: String
    ): SettlementBatchRunResponse =
        paymentSettlementService.batchRun(batchRunId)

    private fun principal(request: HttpServletRequest): PaymentPrincipal? =
        request.getAttribute(PaymentAuthorizationFilter.PRINCIPAL_ATTRIBUTE) as? PaymentPrincipal
}
