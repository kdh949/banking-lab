package lab.banking.payment.worker

import lab.banking.payment.domain.DispatchPaymentLedgerPostingRequest
import lab.banking.payment.domain.PaymentOutboxDispatcherService
import lab.banking.payment.domain.PaymentOutboxDispatchResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

data class PaymentOutboxWorkerRunResult(
    val attemptedCount: Int,
    val publishedCount: Int,
    val failedCount: Int,
    val deadLetterCount: Int,
    val noPendingEvent: Boolean,
    val items: List<PaymentOutboxDispatchResponse>
)

@Component
@ConditionalOnProperty(
    prefix = "banking-lab.payment-service.outbox-worker",
    name = ["enabled"],
    havingValue = "true"
)
class PaymentOutboxWorker(
    private val dispatcherService: PaymentOutboxDispatcherService,
    @param:Value("\${banking-lab.payment-service.outbox-worker.max-batch-size:10}")
    private val maxBatchSize: Int,
    @param:Value("\${banking-lab.payment-service.outbox-worker.dead-letter-threshold:3}")
    private val deadLetterThreshold: Int,
    @param:Value("\${banking-lab.payment-service.outbox-worker.requested-by:payment-service-outbox-worker}")
    private val requestedBy: String,
    @param:Value("\${banking-lab.payment-service.outbox-worker.reason:Synthetic payment outbox worker dispatch}")
    private val reason: String
) {
    @Scheduled(
        initialDelayString = "\${banking-lab.payment-service.outbox-worker.initial-delay-ms:5000}",
        fixedDelayString = "\${banking-lab.payment-service.outbox-worker.fixed-delay-ms:5000}"
    )
    fun scheduledDispatch() {
        dispatchBatch()
    }

    fun dispatchBatch(): PaymentOutboxWorkerRunResult {
        require(maxBatchSize > 0) { "payment outbox worker max-batch-size must be positive" }
        require(deadLetterThreshold > 0) { "payment outbox worker dead-letter-threshold must be positive" }
        require(requestedBy.isNotBlank()) { "payment outbox worker requested-by must be configured" }
        require(reason.isNotBlank()) { "payment outbox worker reason must be configured" }

        val items = mutableListOf<PaymentOutboxDispatchResponse>()
        var noPendingEvent = false
        while (items.size < maxBatchSize && !noPendingEvent) {
            val response = dispatcherService.dispatchNextLedgerPosting(
                DispatchPaymentLedgerPostingRequest(
                    requestedBy = requestedBy,
                    reason = reason,
                    deadLetterThreshold = deadLetterThreshold
                )
            )
            if (response.status == "NO_PENDING_EVENT") {
                noPendingEvent = true
            } else {
                items += response
            }
        }
        return PaymentOutboxWorkerRunResult(
            attemptedCount = items.size,
            publishedCount = items.count { it.status == "PUBLISHED" },
            failedCount = items.count { it.status == "FAILED" },
            deadLetterCount = items.count { it.status == "DEAD_LETTER" },
            noPendingEvent = noPendingEvent,
            items = items
        )
    }
}
