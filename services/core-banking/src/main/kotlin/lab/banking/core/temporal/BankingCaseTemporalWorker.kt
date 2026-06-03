package lab.banking.core.temporal

import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowClientOptions
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.serviceclient.WorkflowServiceStubsOptions
import io.temporal.worker.WorkerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "banking-lab.temporal.worker", name = ["enabled"], havingValue = "true")
class BankingCaseTemporalWorker(
    @param:Value("\${banking-lab.temporal.target:127.0.0.1:7233}")
    private val target: String,
    @param:Value("\${banking-lab.temporal.namespace:default}")
    private val namespace: String,
    @param:Value("\${banking-lab.temporal.task-queue:banking-case-workflows}")
    private val taskQueue: String,
    private val metrics: TemporalWorkerMetrics,
    private val traceLogger: TemporalWorkflowTraceLogger
) : SmartLifecycle {
    private var running = false
    private var serviceStubs: WorkflowServiceStubs? = null
    private var workerFactory: WorkerFactory? = null

    override fun start() {
        if (running) {
            return
        }
        var stubs: WorkflowServiceStubs? = null
        var factory: WorkerFactory? = null
        try {
            stubs = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                    .setTarget(target)
                    .build()
            )
            val client = WorkflowClient.newInstance(
                stubs,
                WorkflowClientOptions.newBuilder()
                    .setNamespace(namespace)
                    .build()
            )
            factory = WorkerFactory.newInstance(client)
            factory.newWorker(taskQueue)
                .registerWorkflowImplementationFactory(
                    BankingCaseTemporalWorkflow::class.java
                ) { traceLogger.newWorkflow() }
            factory.start()
            serviceStubs = stubs
            workerFactory = factory
            running = true
            metrics.recordStarted()
        } catch (error: RuntimeException) {
            factory?.shutdownNow()
            stubs?.shutdownNow()
            metrics.recordStartFailed()
            throw error
        }
    }

    override fun stop() {
        val wasRunning = running || workerFactory != null || serviceStubs != null
        workerFactory?.shutdownNow()
        serviceStubs?.shutdownNow()
        workerFactory = null
        serviceStubs = null
        running = false
        if (wasRunning) {
            metrics.recordStopped()
        }
    }

    override fun isRunning(): Boolean =
        running
}
