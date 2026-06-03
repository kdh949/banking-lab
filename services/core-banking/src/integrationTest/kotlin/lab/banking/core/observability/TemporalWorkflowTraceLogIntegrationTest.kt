package lab.banking.core.observability

import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowException
import io.temporal.client.WorkflowOptions
import io.temporal.client.WorkflowStub
import io.temporal.testing.TestEnvironmentOptions
import io.temporal.testing.TestWorkflowEnvironment
import io.temporal.worker.WorkerFactoryOptions
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.TimeUnit
import lab.banking.core.temporal.BankingCaseTemporalWorkflow
import lab.banking.core.temporal.TemporalApprovalSignal
import lab.banking.core.temporal.TemporalBankingCaseInput
import lab.banking.core.temporal.TemporalBankingCaseResult
import lab.banking.core.temporal.TemporalBankingCaseType
import lab.banking.core.temporal.TemporalRejectionSignal
import lab.banking.core.temporal.TemporalWorkflowTraceLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureObservability
@Testcontainers
@ExtendWith(OutputCaptureExtension::class)
class TemporalWorkflowTraceLogIntegrationTest {
    @Autowired
    lateinit var traceLogger: TemporalWorkflowTraceLogger

    @Test
    fun `Temporal workflow signal and completion logs carry trace and span correlation ids for all case types`(
        output: CapturedOutput
    ) {
        val expectedEffects = mapOf(
            TemporalBankingCaseType.COMPLAINT_ANSWER to "CUSTOMER_ANSWER_VISIBLE",
            TemporalBankingCaseType.FDS_RELEASE to "LEDGER_TRANSFER_HANDOFF",
            TemporalBankingCaseType.FDS_BLOCK to "NO_LEDGER_POSTING",
            TemporalBankingCaseType.AML_CLOSURE to "STR_SIMULATION_CLOSURE",
            TemporalBankingCaseType.RECONCILIATION_ADJUSTMENT to "BALANCED_ADJUSTMENT_HANDOFF",
            TemporalBankingCaseType.ACCOUNT_HOLD to "AVAILABLE_BALANCE_HOLD",
            TemporalBankingCaseType.ACCOUNT_RELEASE to "HOLD_RELEASE_HANDOFF"
        )

        withTestEnvironment { client ->
            for ((caseType, expectedEffect) in expectedEffects) {
                val workflow = workflowStub(client, "trace-log-${caseType.name.lowercase()}")
                val businessReferenceId = "TRACE-${caseType.name}-001"

                WorkflowClient.start(
                    workflow::run,
                    TemporalBankingCaseInput(
                        caseType = caseType,
                        businessReferenceId = businessReferenceId,
                        requestedBy = maker(caseType),
                        requestedByRole = makerRole(caseType),
                        reason = "Synthetic Temporal ${caseType.name} trace log drill"
                    )
                )
                waitForStatus(workflow, "WAITING_APPROVAL")
                workflow.approve(
                    TemporalApprovalSignal(
                        approvedBy = "manager01",
                        approvedByRole = "BRANCH_MANAGER",
                        reason = "Synthetic approval for ${caseType.name} trace log drill"
                    )
                )

                val result = WorkflowStub.fromTyped(workflow)
                    .getResult(10, TimeUnit.SECONDS, TemporalBankingCaseResult::class.java)
                assertEquals("COMPLETED", result.finalStatus)
                assertEquals(expectedEffect, result.controlEffect)
            }
        }

        assertThat(output.all)
            .contains("observability.workflow")
            .contains("event=signal")
            .contains("event=completed")
            .contains("syntheticOnly=true")
            .containsPattern("traceId=[a-f0-9]{32}")
            .containsPattern("spanId=[a-f0-9]{16}")

        for ((caseType, expectedEffect) in expectedEffects) {
            assertThat(output.all)
                .contains("workflowId=banking-case-trace-log-${caseType.name.lowercase()}")
                .contains("caseType=${caseType.name}")
                .contains("businessReferenceId=TRACE-${caseType.name}-001")
                .contains("controlEffect=$expectedEffect")
        }
    }

    @Test
    fun `Temporal workflow rejection logs carry trace and span correlation ids`(output: CapturedOutput) {
        withTestEnvironment { client ->
            val workflow = workflowStub(client, "trace-log-rejection")
            WorkflowClient.start(
                workflow::run,
                TemporalBankingCaseInput(
                    caseType = TemporalBankingCaseType.AML_CLOSURE,
                    businessReferenceId = "TRACE-AML-REJECT-001",
                    requestedBy = "aml01",
                    requestedByRole = "AML_REVIEWER",
                    reason = "Synthetic Temporal rejection trace log drill"
                )
            )
            waitForStatus(workflow, "WAITING_APPROVAL")
            workflow.reject(
                TemporalRejectionSignal(
                    rejectedBy = "manager01",
                    rejectedByRole = "BRANCH_MANAGER",
                    reason = "Synthetic rejection for trace log drill"
                )
            )

            val result = WorkflowStub.fromTyped(workflow)
                .getResult(10, TimeUnit.SECONDS, TemporalBankingCaseResult::class.java)
            assertEquals("REJECTED", result.finalStatus)
            assertEquals("NO_EFFECT", result.controlEffect)
        }

        assertThat(output.all)
            .contains("observability.workflow")
            .contains("event=signal")
            .contains("finalStatus=reject")
            .contains("event=completed")
            .contains("caseType=AML_CLOSURE")
            .contains("businessReferenceId=TRACE-AML-REJECT-001")
            .contains("finalStatus=REJECTED")
            .contains("controlEffect=NO_EFFECT")
            .contains("syntheticOnly=true")
            .containsPattern("traceId=[a-f0-9]{32}")
            .containsPattern("spanId=[a-f0-9]{16}")
    }

    @Test
    fun `Temporal workflow failure logs carry trace and span correlation ids`(output: CapturedOutput) {
        withTestEnvironment { client ->
            val workflow = workflowStub(client, "trace-log-failure")
            WorkflowClient.start(
                workflow::run,
                TemporalBankingCaseInput(
                    caseType = TemporalBankingCaseType.FDS_RELEASE,
                    businessReferenceId = "TRACE-FDS-SELF-APPROVAL-001",
                    requestedBy = "fds01",
                    requestedByRole = "FDS_REVIEWER",
                    reason = "Synthetic Temporal failed transition trace log drill"
                )
            )
            waitForStatus(workflow, "WAITING_APPROVAL")
            workflow.approve(
                TemporalApprovalSignal(
                    approvedBy = "fds01",
                    approvedByRole = "BRANCH_MANAGER",
                    reason = "Synthetic self approval failure for trace log drill"
                )
            )

            val error = runCatching {
                WorkflowStub.fromTyped(workflow)
                    .getResult(10, TimeUnit.SECONDS, TemporalBankingCaseResult::class.java)
            }.exceptionOrNull()
            assertTrue(error is WorkflowException)
            assertTrue(causeChain(error).any { it.contains("MAKER_CHECKER_SELF_APPROVAL_REJECTED") })
        }

        assertThat(output.all)
            .contains("observability.workflow")
            .contains("event=signal")
            .contains("finalStatus=approve")
            .contains("event=failed")
            .contains("caseType=FDS_RELEASE")
            .contains("businessReferenceId=TRACE-FDS-SELF-APPROVAL-001")
            .contains("finalStatus=FAILED")
            .contains("controlEffect=NO_EFFECT")
            .contains("errorType=MAKER_CHECKER_SELF_APPROVAL_REJECTED")
            .contains("syntheticOnly=true")
            .containsPattern("traceId=[a-f0-9]{32}")
            .containsPattern("spanId=[a-f0-9]{16}")
    }

    private fun withTestEnvironment(block: (WorkflowClient) -> Unit) {
        val workerFactoryOptions = WorkerFactoryOptions.newBuilder()
            .setEnableLoggingInReplay(false)
            .build()
        val testEnvironment = TestWorkflowEnvironment.newInstance(
            TestEnvironmentOptions.newBuilder()
                .setWorkerFactoryOptions(workerFactoryOptions)
                .build()
        )
        try {
            val worker = testEnvironment.newWorker(taskQueue)
            worker.registerWorkflowImplementationFactory(
                BankingCaseTemporalWorkflow::class.java
            ) { traceLogger.newWorkflow() }
            testEnvironment.start()
            block(testEnvironment.workflowClient)
        } finally {
            testEnvironment.close()
        }
    }

    private fun workflowStub(client: WorkflowClient, workflowIdSuffix: String): BankingCaseTemporalWorkflow =
        client.newWorkflowStub(
            BankingCaseTemporalWorkflow::class.java,
            WorkflowOptions.newBuilder()
                .setTaskQueue(taskQueue)
                .setWorkflowId("banking-case-$workflowIdSuffix")
                .setWorkflowExecutionTimeout(Duration.ofSeconds(30))
                .build()
        )

    private fun waitForStatus(workflow: BankingCaseTemporalWorkflow, status: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            if (workflow.status() == status) {
                return
            }
            Thread.sleep(25)
        }
        assertEquals(status, workflow.status())
    }

    private fun causeChain(error: Throwable?): List<String> =
        generateSequence(error) { it.cause }
            .flatMap { sequenceOf(it::class.java.name, it.message ?: "") }
            .toList()

    companion object {
        private const val taskQueue = "banking-case-temporal-trace-log-test"

        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.locations") {
                val userDir = Paths.get(System.getProperty("user.dir"))
                listOf(
                    "filesystem:${userDir.resolve("db/migrations").normalize()}",
                    "filesystem:${userDir.resolve("../../db/migrations").normalize()}"
                ).joinToString(",")
            }
            registry.add("management.tracing.enabled") { "true" }
            registry.add("management.otlp.tracing.export.enabled") { "false" }
        }
    }

    private fun maker(caseType: TemporalBankingCaseType): String =
        when (caseType) {
            TemporalBankingCaseType.COMPLAINT_ANSWER -> "complaint01"
            TemporalBankingCaseType.FDS_RELEASE,
            TemporalBankingCaseType.FDS_BLOCK -> "fds01"
            TemporalBankingCaseType.AML_CLOSURE -> "aml01"
            TemporalBankingCaseType.RECONCILIATION_ADJUSTMENT,
            TemporalBankingCaseType.ACCOUNT_HOLD,
            TemporalBankingCaseType.ACCOUNT_RELEASE -> "ops01"
        }

    private fun makerRole(caseType: TemporalBankingCaseType): String =
        when (caseType) {
            TemporalBankingCaseType.COMPLAINT_ANSWER -> "COMPLAINT_HANDLER"
            TemporalBankingCaseType.FDS_RELEASE,
            TemporalBankingCaseType.FDS_BLOCK -> "FDS_REVIEWER"
            TemporalBankingCaseType.AML_CLOSURE -> "AML_REVIEWER"
            TemporalBankingCaseType.RECONCILIATION_ADJUSTMENT,
            TemporalBankingCaseType.ACCOUNT_HOLD,
            TemporalBankingCaseType.ACCOUNT_RELEASE -> "OPS_OPERATOR"
        }
}
