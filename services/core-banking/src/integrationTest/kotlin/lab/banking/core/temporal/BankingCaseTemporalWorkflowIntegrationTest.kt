package lab.banking.core.temporal

import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowException
import io.temporal.client.WorkflowOptions
import io.temporal.client.WorkflowStub
import io.temporal.common.RetryOptions
import io.temporal.testing.TestWorkflowEnvironment
import java.time.Duration
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BankingCaseTemporalWorkflowIntegrationTest {
    private lateinit var testEnvironment: TestWorkflowEnvironment
    private lateinit var client: WorkflowClient

    @BeforeEach
    fun setUp() {
        testEnvironment = TestWorkflowEnvironment.newInstance()
        val worker = testEnvironment.newWorker(taskQueue)
        worker.registerWorkflowImplementationTypes(BankingCaseTemporalWorkflowImpl::class.java)
        testEnvironment.start()
        client = testEnvironment.workflowClient
    }

    @AfterEach
    fun tearDown() {
        testEnvironment.close()
    }

    @Test
    fun `Temporal workflows wait for approval signals across required banking case types`() {
        val expectedEffects = mapOf(
            TemporalBankingCaseType.COMPLAINT_ANSWER to "CUSTOMER_ANSWER_VISIBLE",
            TemporalBankingCaseType.FDS_RELEASE to "LEDGER_TRANSFER_HANDOFF",
            TemporalBankingCaseType.FDS_BLOCK to "NO_LEDGER_POSTING",
            TemporalBankingCaseType.AML_CLOSURE to "STR_SIMULATION_CLOSURE",
            TemporalBankingCaseType.RECONCILIATION_ADJUSTMENT to "BALANCED_ADJUSTMENT_HANDOFF",
            TemporalBankingCaseType.ACCOUNT_HOLD to "AVAILABLE_BALANCE_HOLD",
            TemporalBankingCaseType.ACCOUNT_RELEASE to "HOLD_RELEASE_HANDOFF"
        )

        for ((caseType, expectedEffect) in expectedEffects) {
            val workflow = workflowStub("${caseType.name.lowercase()}-approval")
            val input = TemporalBankingCaseInput(
                caseType = caseType,
                businessReferenceId = "SYN-${caseType.name}-001",
                requestedBy = "maker01",
                requestedByRole = makerRole(caseType),
                reason = "Synthetic Temporal ${caseType.name} approval",
                payload = mapOf("caseType" to caseType.name)
            )
            WorkflowClient.start(workflow::run, input)
            val untyped = WorkflowStub.fromTyped(workflow)

            waitForStatus(workflow, "WAITING_APPROVAL")
            assertTrue(workflow.checkpoints().contains("${caseType.name}:WAITING_APPROVAL"))

            workflow.approve(
                TemporalApprovalSignal(
                    approvedBy = "manager01",
                    approvedByRole = "BRANCH_MANAGER",
                    reason = "Synthetic checker approval"
                )
            )

            val result = untyped.getResult(10, TimeUnit.SECONDS, TemporalBankingCaseResult::class.java)
            assertEquals("COMPLETED", result.finalStatus)
            assertEquals(caseType.name, result.caseType)
            assertEquals(expectedEffect, result.controlEffect)
            assertEquals("manager01", result.checkerId)
            assertEquals(true, result.syntheticOnly)
            assertEquals(
                listOf(
                    "${caseType.name}:STARTED",
                    "${caseType.name}:WAITING_APPROVAL",
                    "${caseType.name}:COMPLETED"
                ),
                result.checkpoints
            )
        }
    }

    @Test
    fun `Temporal workflow rejection finishes without business side effect`() {
        val workflow = workflowStub("aml-rejection")
        WorkflowClient.start(
            workflow::run,
            TemporalBankingCaseInput(
                caseType = TemporalBankingCaseType.AML_CLOSURE,
                businessReferenceId = "AML-TEMPORAL-REJECT-001",
                requestedBy = "aml01",
                requestedByRole = "AML_REVIEWER",
                reason = "Synthetic AML closure"
            )
        )
        val untyped = WorkflowStub.fromTyped(workflow)

        waitForStatus(workflow, "WAITING_APPROVAL")
        workflow.reject(
            TemporalRejectionSignal(
                rejectedBy = "manager01",
                rejectedByRole = "BRANCH_MANAGER",
                reason = "Synthetic checker rejection"
            )
        )

        val result = untyped.getResult(10, TimeUnit.SECONDS, TemporalBankingCaseResult::class.java)
        assertEquals("REJECTED", result.finalStatus)
        assertEquals("NO_EFFECT", result.controlEffect)
        assertEquals(
            listOf("AML_CLOSURE:STARTED", "AML_CLOSURE:WAITING_APPROVAL", "AML_CLOSURE:REJECTED"),
            result.checkpoints
        )
    }

    @Test
    fun `Temporal workflow rejects maker self approval`() {
        val workflow = workflowStub("fds-self-approval")
        WorkflowClient.start(
            workflow::run,
            TemporalBankingCaseInput(
                caseType = TemporalBankingCaseType.FDS_RELEASE,
                businessReferenceId = "FDS-TEMPORAL-SELF-001",
                requestedBy = "fds01",
                requestedByRole = "FDS_REVIEWER",
                reason = "Synthetic FDS release"
            )
        )
        val untyped = WorkflowStub.fromTyped(workflow)

        waitForStatus(workflow, "WAITING_APPROVAL")
        workflow.approve(
            TemporalApprovalSignal(
                approvedBy = "fds01",
                approvedByRole = "BRANCH_MANAGER",
                reason = "Synthetic invalid self approval"
            )
        )

        val error = runCatching {
            untyped.getResult(10, TimeUnit.SECONDS, TemporalBankingCaseResult::class.java)
        }.exceptionOrNull()
        assertTrue(error is WorkflowException)
        assertTrue(causeChain(error).any { it.contains("MAKER_CHECKER_SELF_APPROVAL_REJECTED") })
    }

    @Test
    fun `Temporal workflow retry drill recovers after synthetic transient worker failure`() {
        val workflow = client.newWorkflowStub(
            BankingCaseTemporalWorkflow::class.java,
            WorkflowOptions.newBuilder()
                .setTaskQueue(taskQueue)
                .setWorkflowId("banking-case-temporal-retry-drill")
                .setWorkflowExecutionTimeout(Duration.ofSeconds(30))
                .setRetryOptions(
                    RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofMillis(100))
                        .setMaximumInterval(Duration.ofMillis(500))
                        .setMaximumAttempts(2)
                        .build()
                )
                .build()
        )
        WorkflowClient.start(
            workflow::run,
            TemporalBankingCaseInput(
                caseType = TemporalBankingCaseType.COMPLAINT_ANSWER,
                businessReferenceId = "CMP-TEMPORAL-RETRY-001",
                requestedBy = "complaint01",
                requestedByRole = "COMPLAINT_HANDLER",
                reason = "Synthetic Temporal retry drill",
                payload = mapOf("syntheticTransientFailuresBeforeApproval" to 1)
            )
        )
        val untyped = WorkflowStub.fromTyped(workflow)

        waitForStatus(workflow, "WAITING_APPROVAL")
        assertTrue(workflow.checkpoints().contains("COMPLAINT_ANSWER:RETRY_RECOVERED_ATTEMPT_2"))
        workflow.approve(
            TemporalApprovalSignal(
                approvedBy = "manager01",
                approvedByRole = "BRANCH_MANAGER",
                reason = "Synthetic checker approval after retry"
            )
        )

        val result = untyped.getResult(10, TimeUnit.SECONDS, TemporalBankingCaseResult::class.java)
        assertEquals("COMPLETED", result.finalStatus)
        assertEquals("CUSTOMER_ANSWER_VISIBLE", result.controlEffect)
        assertEquals("manager01", result.checkerId)
        assertTrue(result.checkpoints.contains("COMPLAINT_ANSWER:RETRY_RECOVERED_ATTEMPT_2"))
        assertTrue(result.syntheticOnly)
    }

    private fun workflowStub(workflowIdSuffix: String): BankingCaseTemporalWorkflow =
        client.newWorkflowStub(
            BankingCaseTemporalWorkflow::class.java,
            WorkflowOptions.newBuilder()
                .setTaskQueue(taskQueue)
                .setWorkflowId("banking-case-$workflowIdSuffix")
                .setWorkflowExecutionTimeout(Duration.ofSeconds(30))
                .build()
        )

    private fun waitForStatus(workflow: BankingCaseTemporalWorkflow, status: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        var lastObservedStatus: String? = null
        var lastQueryFailure: WorkflowException? = null
        while (System.nanoTime() < deadline) {
            try {
                lastObservedStatus = workflow.status()
                if (lastObservedStatus == status) {
                    return
                }
                lastQueryFailure = null
            } catch (error: WorkflowException) {
                lastQueryFailure = error
            }
            Thread.sleep(50)
        }
        lastQueryFailure?.let { throw it }
        assertEquals(status, lastObservedStatus)
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

    private fun causeChain(error: Throwable?): List<String> =
        generateSequence(error) { it.cause }
            .flatMap { sequenceOf(it::class.java.name, it.message ?: "") }
            .toList()

    companion object {
        private const val taskQueue = "banking-case-temporal-test"
    }
}
