package lab.banking.core.ledger.application

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.Callable
import org.springframework.stereotype.Component

@Component
class LedgerCommandMetrics(
    private val registry: MeterRegistry
) {
    fun <T> record(commandType: String, operation: Callable<T>): T {
        val sample = Timer.start(registry)
        try {
            val result = operation.call()
            recordLatency(sample, commandType, "success")
            return result
        } catch (error: RuntimeException) {
            errorCounter(commandType, error).increment()
            recordLatency(sample, commandType, "error")
            throw error
        }
    }

    fun recordIdempotencyReplay(commandType: String) {
        Counter
            .builder("banking.lab.idempotency.replay.count")
            .description("Idempotency replay count for retried ledger commands")
            .tag("command_type", commandType)
            .register(registry)
            .increment()
    }

    private fun recordLatency(sample: Timer.Sample, commandType: String, outcome: String) {
        sample.stop(
            Timer
                .builder("banking.lab.ledger.command.latency")
                .description("Ledger command latency by command type and outcome")
                .tag("command_type", commandType)
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(registry)
        )
    }

    private fun errorCounter(commandType: String, error: RuntimeException): Counter =
        Counter
            .builder("banking.lab.ledger.command.errors")
            .description("Ledger command error count by command type and exception family")
            .tag("command_type", commandType)
            .tag("exception", error::class.simpleName ?: "RuntimeException")
            .register(registry)
}
