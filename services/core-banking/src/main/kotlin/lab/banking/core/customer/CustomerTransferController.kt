package lab.banking.core.customer

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/customer/transfers")
class CustomerTransferController(
    private val customerTransferService: CustomerTransferService,
    private val faultProperties: CustomerTransferFaultProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping
    fun transfer(@RequestBody command: CustomerTransferCommand): ResponseEntity<CustomerTransferResponse> {
        val result = customerTransferService.transfer(command)
        crashAfterDurableCommitIfConfigured(result)
        val status = when {
            result.replayed -> HttpStatus.OK
            result.item.status == "HELD" -> HttpStatus.ACCEPTED
            result.item.status == "FAILED" -> HttpStatus.OK
            else -> HttpStatus.CREATED
        }
        return ResponseEntity.status(status).body(result)
    }

    private fun crashAfterDurableCommitIfConfigured(result: CustomerTransferResponse) {
        if (!faultProperties.shouldCrashAfterCommit(result.item.idempotencyKey, result.replayed)) {
            return
        }
        logger.error(
            "observability.customer.transfer event=fault-crash-after-durable-commit idempotencyKey={} resultId={} transactionId={} status={} syntheticOnly=true",
            result.item.idempotencyKey,
            result.item.resultId,
            result.item.transactionId,
            result.item.status
        )
        Runtime.getRuntime().halt(faultProperties.crashAfterCommitExitCode)
    }

    @GetMapping
    fun transferStatuses(@RequestParam customerId: String): CustomerTransferStatusResponse =
        customerTransferService.transferStatuses(customerId)
}

@RestController
@RequestMapping("/api/customer/transactions")
class CustomerTransactionController(
    private val customerTransferService: CustomerTransferService
) {
    @GetMapping
    fun transactionHistory(
        @RequestParam customerId: String,
        @RequestParam accountId: String
    ): CustomerTransactionHistoryResponse =
        customerTransferService.transactionHistory(customerId, accountId)
}
