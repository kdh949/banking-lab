package lab.banking.core.temporal

import io.micrometer.tracing.Span
import io.micrometer.tracing.Tracer
import io.temporal.failure.ApplicationFailure
import io.temporal.workflow.Workflow
import io.temporal.workflow.WorkflowInfo
import io.temporal.workflow.unsafe.WorkflowUnsafe
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component

@Component
class TemporalWorkflowTraceLogger(
    private val tracerProvider: ObjectProvider<Tracer>
) {
    private val log = LoggerFactory.getLogger(TemporalWorkflowTraceLogger::class.java)

    fun newWorkflow(): BankingCaseTemporalWorkflow =
        TracedBankingCaseTemporalWorkflow(BankingCaseTemporalWorkflowImpl(), this)

    fun recordCompleted(result: TemporalBankingCaseResult) {
        recordWorkflowEvent(
            event = "completed",
            caseType = result.caseType,
            businessReferenceId = result.businessReferenceId,
            finalStatus = result.finalStatus,
            controlEffect = result.controlEffect,
            errorType = null
        )
    }

    fun recordFailed(input: TemporalBankingCaseInput, error: Throwable) {
        recordWorkflowEvent(
            event = "failed",
            caseType = input.caseType,
            businessReferenceId = input.businessReferenceId,
            finalStatus = "FAILED",
            controlEffect = "NO_EFFECT",
            errorType = workflowErrorType(error)
        )
    }

    fun recordSignal(signalName: String, caseTypeHint: String = "unknown", businessReferenceIdHint: String = "unknown") {
        recordWorkflowEvent(
            event = "signal",
            caseType = caseTypeHint,
            businessReferenceId = businessReferenceIdHint,
            finalStatus = signalName,
            controlEffect = "SIGNAL_ACCEPTED",
            errorType = null
        )
    }

    private fun recordWorkflowEvent(
        event: String,
        caseType: String,
        businessReferenceId: String,
        finalStatus: String,
        controlEffect: String,
        errorType: String?
    ) {
        if (WorkflowUnsafe.isWorkflowThread() && WorkflowUnsafe.isReplaying()) {
            return
        }

        val workflowInfo = workflowInfoOrNull()
        val tracer = tracerProvider.ifAvailable
        if (tracer == null) {
            logWorkflowEvent(
                event = event,
                workflowInfo = workflowInfo,
                caseType = caseType,
                businessReferenceId = businessReferenceId,
                finalStatus = finalStatus,
                controlEffect = controlEffect,
                errorType = errorType,
                span = null
            )
            return
        }

        val span = tracer.nextSpan()
            .name("banking-lab.temporal.workflow.$event")
            .tag("synthetic.only", "true")
            .tag("temporal.workflow.event", event)
            .tag("temporal.workflow.id", workflowInfo?.workflowId ?: "unknown")
            .tag("temporal.workflow.run_id", workflowInfo?.runId ?: "unknown")
            .tag("temporal.workflow.type", workflowInfo?.workflowType ?: "unknown")
            .tag("temporal.workflow.task_queue", workflowInfo?.taskQueue ?: "unknown")
            .tag("banking.case_type", caseType)
            .tag("banking.business_reference_id", businessReferenceId)
            .tag("banking.control_effect", controlEffect)
            .tag("banking.error_type", errorType ?: "none")
            .start()
        try {
            tracer.withSpan(span).use {
                logWorkflowEvent(
                    event = event,
                    workflowInfo = workflowInfo,
                    caseType = caseType,
                    businessReferenceId = businessReferenceId,
                    finalStatus = finalStatus,
                    controlEffect = controlEffect,
                    errorType = errorType,
                    span = span
                )
            }
        } catch (ex: Throwable) {
            span.error(ex)
            throw ex
        } finally {
            span.end()
        }
    }

    private fun logWorkflowEvent(
        event: String,
        workflowInfo: WorkflowInfo?,
        caseType: String,
        businessReferenceId: String,
        finalStatus: String,
        controlEffect: String,
        errorType: String?,
        span: Span?
    ) {
        val context = span?.context()
        log.info(
            "observability.workflow event={} workflowId={} runId={} workflowType={} taskQueue={} caseType={} businessReferenceId={} finalStatus={} controlEffect={} errorType={} traceId={} spanId={} syntheticOnly=true",
            event,
            workflowInfo?.workflowId ?: "unknown",
            workflowInfo?.runId ?: "unknown",
            workflowInfo?.workflowType ?: "unknown",
            workflowInfo?.taskQueue ?: "unknown",
            caseType,
            businessReferenceId,
            finalStatus,
            controlEffect,
            errorType ?: "none",
            context?.traceId() ?: "unavailable",
            context?.spanId() ?: "unavailable"
        )
    }

    private fun workflowInfoOrNull(): WorkflowInfo? =
        runCatching { Workflow.getInfo() }.getOrNull()

    private fun workflowErrorType(error: Throwable): String =
        when (error) {
            is ApplicationFailure -> error.type
            else -> error::class.java.simpleName
        }
}

private class TracedBankingCaseTemporalWorkflow(
    private val delegate: BankingCaseTemporalWorkflow,
    private val traceLogger: TemporalWorkflowTraceLogger
) : BankingCaseTemporalWorkflow {
    private var lastCaseType = "unknown"
    private var lastBusinessReferenceId = "unknown"

    override fun run(input: TemporalBankingCaseInput): TemporalBankingCaseResult {
        lastCaseType = input.caseType.ifBlank { "unknown" }
        lastBusinessReferenceId = input.businessReferenceId.ifBlank { "unknown" }
        return try {
            val result = delegate.run(input)
            traceLogger.recordCompleted(result)
            result
        } catch (error: RuntimeException) {
            traceLogger.recordFailed(input, error)
            throw error
        }
    }

    override fun approve(signal: TemporalApprovalSignal) {
        delegate.approve(signal)
        traceLogger.recordSignal("approve", lastCaseType, lastBusinessReferenceId)
    }

    override fun reject(signal: TemporalRejectionSignal) {
        delegate.reject(signal)
        traceLogger.recordSignal("reject", lastCaseType, lastBusinessReferenceId)
    }

    override fun status(): String =
        delegate.status()

    override fun checkpoints(): List<String> =
        delegate.checkpoints()
}
