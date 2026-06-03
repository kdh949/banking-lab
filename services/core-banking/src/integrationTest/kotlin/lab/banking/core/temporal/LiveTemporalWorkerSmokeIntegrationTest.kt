package lab.banking.core.temporal

import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowClientOptions
import io.temporal.client.WorkflowException
import io.temporal.client.WorkflowOptions
import io.temporal.client.WorkflowStub
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.serviceclient.WorkflowServiceStubsOptions
import io.temporal.worker.WorkerFactory
import io.grpc.health.v1.HealthCheckResponse
import java.nio.file.Paths
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class LiveTemporalWorkerSmokeIntegrationTest {
    @Test
    fun `live Temporal worker executes approval workflow from server task queue`() {
        val target = System.getenv("BANKING_LAB_LIVE_TEMPORAL_TARGET")
        assumeTrue(!target.isNullOrBlank(), "set BANKING_LAB_LIVE_TEMPORAL_TARGET to run live Temporal smoke")

        val namespace = System.getenv("BANKING_LAB_LIVE_TEMPORAL_NAMESPACE") ?: "default"
        val taskQueue = System.getenv("BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE") ?: "banking-case-workflows"
        val serviceStubs = WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                .setTarget(target)
                .build()
        )
        try {
            val client = WorkflowClient.newInstance(
                serviceStubs,
                WorkflowClientOptions.newBuilder()
                    .setNamespace(namespace)
                    .build()
            )
            val workflow = client.newWorkflowStub(
                BankingCaseTemporalWorkflow::class.java,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(taskQueue)
                    .setWorkflowId("banking-case-live-smoke-${UUID.randomUUID()}")
                    .setWorkflowExecutionTimeout(Duration.ofSeconds(30))
                    .build()
            )

            WorkflowClient.start(
                workflow::run,
                TemporalBankingCaseInput(
                    caseType = TemporalBankingCaseType.COMPLAINT_ANSWER,
                    businessReferenceId = "COMPLAINT-LIVE-TEMPORAL-SMOKE",
                    requestedBy = "complaint01",
                    requestedByRole = "COMPLAINT_HANDLER",
                    reason = "Synthetic live Temporal smoke"
                )
            )
            waitForStatus(workflow, "WAITING_APPROVAL")
            workflow.approve(
                TemporalApprovalSignal(
                    approvedBy = "manager01",
                    approvedByRole = "BRANCH_MANAGER",
                    reason = "Synthetic live smoke approval"
                )
            )

            val result = WorkflowStub.fromTyped(workflow)
                .getResult(20, TimeUnit.SECONDS, TemporalBankingCaseResult::class.java)
            assertEquals("COMPLETED", result.finalStatus)
            assertEquals("COMPLAINT_ANSWER", result.caseType)
            assertEquals("CUSTOMER_ANSWER_VISIBLE", result.controlEffect)
            assertEquals(true, result.syntheticOnly)
        } finally {
            serviceStubs.shutdownNow()
        }
    }

    @Test
    fun `live Temporal worker executes all approval workflow case types from server task queue`() {
        val target = System.getenv("BANKING_LAB_LIVE_TEMPORAL_TARGET")
        assumeTrue(!target.isNullOrBlank(), "set BANKING_LAB_LIVE_TEMPORAL_TARGET to run live Temporal all-case smoke")

        val namespace = System.getenv("BANKING_LAB_LIVE_TEMPORAL_NAMESPACE") ?: "default"
        val taskQueue = System.getenv("BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE") ?: "banking-case-workflows"
        val expectedEffects = mapOf(
            TemporalBankingCaseType.COMPLAINT_ANSWER to "CUSTOMER_ANSWER_VISIBLE",
            TemporalBankingCaseType.FDS_RELEASE to "LEDGER_TRANSFER_HANDOFF",
            TemporalBankingCaseType.FDS_BLOCK to "NO_LEDGER_POSTING",
            TemporalBankingCaseType.AML_CLOSURE to "STR_SIMULATION_CLOSURE",
            TemporalBankingCaseType.RECONCILIATION_ADJUSTMENT to "BALANCED_ADJUSTMENT_HANDOFF",
            TemporalBankingCaseType.ACCOUNT_HOLD to "AVAILABLE_BALANCE_HOLD",
            TemporalBankingCaseType.ACCOUNT_RELEASE to "HOLD_RELEASE_HANDOFF"
        )
        val serviceStubs = WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                .setTarget(target)
                .build()
        )
        try {
            val client = WorkflowClient.newInstance(
                serviceStubs,
                WorkflowClientOptions.newBuilder()
                    .setNamespace(namespace)
                    .build()
            )
            for ((caseType, expectedEffect) in expectedEffects) {
                val workflow = client.newWorkflowStub(
                    BankingCaseTemporalWorkflow::class.java,
                    WorkflowOptions.newBuilder()
                        .setTaskQueue(taskQueue)
                        .setWorkflowId("banking-case-live-all-${caseType.name.lowercase()}-${UUID.randomUUID()}")
                        .setWorkflowExecutionTimeout(Duration.ofSeconds(30))
                        .build()
                )

                WorkflowClient.start(
                    workflow::run,
                    TemporalBankingCaseInput(
                        caseType = caseType,
                        businessReferenceId = "LIVE-TRACE-${caseType.name}-001",
                        requestedBy = maker(caseType),
                        requestedByRole = makerRole(caseType),
                        reason = "Synthetic live Temporal ${caseType.name} all-case smoke"
                    )
                )
                waitForStatus(workflow, "WAITING_APPROVAL")
                workflow.approve(
                    TemporalApprovalSignal(
                        approvedBy = "manager01",
                        approvedByRole = "BRANCH_MANAGER",
                        reason = "Synthetic live ${caseType.name} all-case approval"
                    )
                )

                val result = WorkflowStub.fromTyped(workflow)
                    .getResult(20, TimeUnit.SECONDS, TemporalBankingCaseResult::class.java)
                assertEquals("COMPLETED", result.finalStatus)
                assertEquals(caseType.name, result.caseType)
                assertEquals(expectedEffect, result.controlEffect)
                assertEquals(true, result.syntheticOnly)
            }
        } finally {
            serviceStubs.shutdownNow()
        }
    }

    @Test
    fun `live Temporal worker executes rejection and failure transitions from server task queue`() {
        val target = System.getenv("BANKING_LAB_LIVE_TEMPORAL_TARGET")
        assumeTrue(!target.isNullOrBlank(), "set BANKING_LAB_LIVE_TEMPORAL_TARGET to run live Temporal transition smoke")

        val namespace = System.getenv("BANKING_LAB_LIVE_TEMPORAL_NAMESPACE") ?: "default"
        val taskQueue = System.getenv("BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE") ?: "banking-case-workflows"
        val serviceStubs = WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                .setTarget(target)
                .build()
        )
        try {
            val client = WorkflowClient.newInstance(
                serviceStubs,
                WorkflowClientOptions.newBuilder()
                    .setNamespace(namespace)
                    .build()
            )

            val rejectionWorkflow = client.newWorkflowStub(
                BankingCaseTemporalWorkflow::class.java,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(taskQueue)
                    .setWorkflowId("banking-case-live-reject-aml-${UUID.randomUUID()}")
                    .setWorkflowExecutionTimeout(Duration.ofSeconds(30))
                    .build()
            )
            WorkflowClient.start(
                rejectionWorkflow::run,
                TemporalBankingCaseInput(
                    caseType = TemporalBankingCaseType.AML_CLOSURE,
                    businessReferenceId = "LIVE-TRACE-AML_REJECT-001",
                    requestedBy = "aml01",
                    requestedByRole = "AML_REVIEWER",
                    reason = "Synthetic live Temporal AML rejection transition smoke"
                )
            )
            waitForStatus(rejectionWorkflow, "WAITING_APPROVAL")
            rejectionWorkflow.reject(
                TemporalRejectionSignal(
                    rejectedBy = "manager01",
                    rejectedByRole = "BRANCH_MANAGER",
                    reason = "Synthetic live AML rejection transition"
                )
            )
            val rejectedResult = WorkflowStub.fromTyped(rejectionWorkflow)
                .getResult(20, TimeUnit.SECONDS, TemporalBankingCaseResult::class.java)
            assertEquals("REJECTED", rejectedResult.finalStatus)
            assertEquals("AML_CLOSURE", rejectedResult.caseType)
            assertEquals("NO_EFFECT", rejectedResult.controlEffect)
            assertEquals(true, rejectedResult.syntheticOnly)

            val failureWorkflow = client.newWorkflowStub(
                BankingCaseTemporalWorkflow::class.java,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(taskQueue)
                    .setWorkflowId("banking-case-live-fail-fds-self-approval-${UUID.randomUUID()}")
                    .setWorkflowExecutionTimeout(Duration.ofSeconds(30))
                    .build()
            )
            WorkflowClient.start(
                failureWorkflow::run,
                TemporalBankingCaseInput(
                    caseType = TemporalBankingCaseType.FDS_RELEASE,
                    businessReferenceId = "LIVE-TRACE-FDS_SELF_APPROVAL-001",
                    requestedBy = "fds01",
                    requestedByRole = "FDS_REVIEWER",
                    reason = "Synthetic live Temporal self-approval failure transition smoke"
                )
            )
            waitForStatus(failureWorkflow, "WAITING_APPROVAL")
            failureWorkflow.approve(
                TemporalApprovalSignal(
                    approvedBy = "fds01",
                    approvedByRole = "BRANCH_MANAGER",
                    reason = "Synthetic live invalid self approval"
                )
            )
            val error = runCatching {
                WorkflowStub.fromTyped(failureWorkflow)
                    .getResult(20, TimeUnit.SECONDS, TemporalBankingCaseResult::class.java)
            }.exceptionOrNull()
            assertTrue(error is WorkflowException)
            assertTrue(causeChain(error).any { it.contains("MAKER_CHECKER_SELF_APPROVAL_REJECTED") })
        } finally {
            serviceStubs.shutdownNow()
        }
    }

    @Test
    fun `live Temporal workflow survives worker restart before approval completion`() {
        val target = System.getenv("BANKING_LAB_LIVE_TEMPORAL_TARGET")
        assumeTrue(!target.isNullOrBlank(), "set BANKING_LAB_LIVE_TEMPORAL_TARGET to run live Temporal restart drill")

        val namespace = System.getenv("BANKING_LAB_LIVE_TEMPORAL_NAMESPACE") ?: "default"
        val baseTaskQueue = System.getenv("BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE") ?: "banking-case-workflows"
        val restartTaskQueue = "$baseTaskQueue-restart-${UUID.randomUUID()}"
        val serviceStubs = WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                .setTarget(target)
                .build()
        )
        var firstFactory: WorkerFactory? = null
        var secondFactory: WorkerFactory? = null
        try {
            val client = WorkflowClient.newInstance(
                serviceStubs,
                WorkflowClientOptions.newBuilder()
                    .setNamespace(namespace)
                    .build()
            )
            firstFactory = startWorker(client, restartTaskQueue)
            val workflow = client.newWorkflowStub(
                BankingCaseTemporalWorkflow::class.java,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(restartTaskQueue)
                    .setWorkflowId("banking-case-live-restart-${UUID.randomUUID()}")
                    .setWorkflowExecutionTimeout(Duration.ofSeconds(45))
                    .build()
            )

            WorkflowClient.start(
                workflow::run,
                TemporalBankingCaseInput(
                    caseType = TemporalBankingCaseType.COMPLAINT_ANSWER,
                    businessReferenceId = "COMPLAINT-LIVE-TEMPORAL-RESTART",
                    requestedBy = "complaint01",
                    requestedByRole = "COMPLAINT_HANDLER",
                    reason = "Synthetic live Temporal restart drill"
                )
            )
            waitForStatus(workflow, "WAITING_APPROVAL")

            firstFactory.shutdownNow()
            firstFactory.awaitTermination(5, TimeUnit.SECONDS)
            firstFactory = null

            workflow.approve(
                TemporalApprovalSignal(
                    approvedBy = "manager01",
                    approvedByRole = "BRANCH_MANAGER",
                    reason = "Synthetic approval after worker restart"
                )
            )

            secondFactory = startWorker(client, restartTaskQueue)
            val result = WorkflowStub.fromTyped(workflow)
                .getResult(20, TimeUnit.SECONDS, TemporalBankingCaseResult::class.java)
            assertEquals("COMPLETED", result.finalStatus)
            assertEquals("COMPLAINT_ANSWER", result.caseType)
            assertEquals("CUSTOMER_ANSWER_VISIBLE", result.controlEffect)
            assertEquals(true, result.syntheticOnly)
            assertEquals(
                listOf(
                    "COMPLAINT_ANSWER:STARTED",
                    "COMPLAINT_ANSWER:WAITING_APPROVAL",
                    "COMPLAINT_ANSWER:COMPLETED"
                ),
                result.checkpoints
            )
        } finally {
            firstFactory?.shutdownNow()
            secondFactory?.shutdownNow()
            serviceStubs.shutdownNow()
        }
    }

    @Test
    fun `live Temporal workflow survives Compose worker container restart before approval completion`() {
        runComposeWorkerContainerRestartDrill(
            caseType = TemporalBankingCaseType.COMPLAINT_ANSWER,
            businessReferenceId = "COMPLAINT-LIVE-TEMPORAL-CONTAINER-RESTART",
            expectedControlEffect = "CUSTOMER_ANSWER_VISIBLE",
            approvalReason = "Synthetic approval while worker container is down"
        )
    }

    @Test
    fun `live FDS release workflow survives Compose worker container restart before approval completion`() {
        runComposeWorkerContainerRestartDrill(
            caseType = TemporalBankingCaseType.FDS_RELEASE,
            businessReferenceId = "FDS-LIVE-TEMPORAL-CONTAINER-RESTART",
            expectedControlEffect = "LEDGER_TRANSFER_HANDOFF",
            approvalReason = "Synthetic FDS release approval while worker container is down"
        )
    }

    @Test
    fun `live AML closure workflow survives Compose worker container restart before approval completion`() {
        runComposeWorkerContainerRestartDrill(
            caseType = TemporalBankingCaseType.AML_CLOSURE,
            businessReferenceId = "AML-LIVE-TEMPORAL-CONTAINER-RESTART",
            expectedControlEffect = "STR_SIMULATION_CLOSURE",
            approvalReason = "Synthetic AML closure approval while worker container is down"
        )
    }

    @Test
    fun `live reconciliation adjustment workflow survives Compose worker container restart before approval completion`() {
        runComposeWorkerContainerRestartDrill(
            caseType = TemporalBankingCaseType.RECONCILIATION_ADJUSTMENT,
            businessReferenceId = "REC-LIVE-TEMPORAL-CONTAINER-RESTART",
            expectedControlEffect = "BALANCED_ADJUSTMENT_HANDOFF",
            approvalReason = "Synthetic reconciliation adjustment approval while worker container is down"
        )
    }

    @Test
    fun `live FDS block workflow survives Compose worker container restart before approval completion`() {
        runComposeWorkerContainerRestartDrill(
            caseType = TemporalBankingCaseType.FDS_BLOCK,
            businessReferenceId = "FDS-BLOCK-LIVE-TEMPORAL-CONTAINER-RESTART",
            expectedControlEffect = "NO_LEDGER_POSTING",
            approvalReason = "Synthetic FDS block approval while worker container is down"
        )
    }

    @Test
    fun `live account hold workflow survives Compose worker container restart before approval completion`() {
        runComposeWorkerContainerRestartDrill(
            caseType = TemporalBankingCaseType.ACCOUNT_HOLD,
            businessReferenceId = "HOLD-LIVE-TEMPORAL-CONTAINER-RESTART",
            expectedControlEffect = "AVAILABLE_BALANCE_HOLD",
            approvalReason = "Synthetic account hold approval while worker container is down"
        )
    }

    @Test
    fun `live account release workflow survives Compose worker container restart before approval completion`() {
        runComposeWorkerContainerRestartDrill(
            caseType = TemporalBankingCaseType.ACCOUNT_RELEASE,
            businessReferenceId = "RELEASE-LIVE-TEMPORAL-CONTAINER-RESTART",
            expectedControlEffect = "HOLD_RELEASE_HANDOFF",
            approvalReason = "Synthetic account release approval while worker container is down"
        )
    }

    @Test
    fun `live Temporal workflow survives Compose Temporal server restart before approval completion`() {
        runComposeTemporalServerRestartDrill(
            caseType = TemporalBankingCaseType.COMPLAINT_ANSWER,
            businessReferenceId = "COMPLAINT-LIVE-TEMPORAL-SERVER-RESTART",
            expectedControlEffect = "CUSTOMER_ANSWER_VISIBLE",
            approvalReason = "Synthetic approval after Temporal server container restart"
        )
    }

    @Test
    fun `live Temporal workflow survives Compose PostgreSQL restart before approval completion`() {
        runComposePostgresRestartDrill(
            caseType = TemporalBankingCaseType.COMPLAINT_ANSWER,
            businessReferenceId = "COMPLAINT-LIVE-TEMPORAL-POSTGRES-RESTART",
            expectedControlEffect = "CUSTOMER_ANSWER_VISIBLE",
            approvalReason = "Synthetic approval after PostgreSQL container restart"
        )
    }

    private fun runComposeWorkerContainerRestartDrill(
        caseType: TemporalBankingCaseType,
        businessReferenceId: String,
        expectedControlEffect: String,
        approvalReason: String
    ) {
        val target = System.getenv("BANKING_LAB_LIVE_TEMPORAL_TARGET")
        assumeTrue(!target.isNullOrBlank(), "set BANKING_LAB_LIVE_TEMPORAL_TARGET to run live Temporal container drill")
        val composeProject = System.getenv("BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT")
        assumeTrue(
            !composeProject.isNullOrBlank(),
            "set BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT to run live Temporal container drill"
        )

        val namespace = System.getenv("BANKING_LAB_LIVE_TEMPORAL_NAMESPACE") ?: "default"
        val taskQueue = System.getenv("BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE") ?: "banking-case-workflows"
        val composeEnv = restartComposeEnvironment(taskQueue)
        val workerService = System.getenv("BANKING_LAB_LIVE_TEMPORAL_WORKER_SERVICE")
            ?: "core-banking-temporal-worker"
        val serviceStubs = WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                .setTarget(target)
                .build()
        )
        var workerKilled = false
        try {
            val client = WorkflowClient.newInstance(
                serviceStubs,
                WorkflowClientOptions.newBuilder()
                    .setNamespace(namespace)
                    .build()
            )
            val workflow = client.newWorkflowStub(
                BankingCaseTemporalWorkflow::class.java,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(taskQueue)
                    .setWorkflowId("banking-case-container-restart-${caseType.name.lowercase()}-${UUID.randomUUID()}")
                    .setWorkflowExecutionTimeout(Duration.ofSeconds(90))
                    .build()
            )

            WorkflowClient.start(
                workflow::run,
                TemporalBankingCaseInput(
                    caseType = caseType,
                    businessReferenceId = businessReferenceId,
                    requestedBy = maker(caseType),
                    requestedByRole = makerRole(caseType),
                    reason = "Synthetic live Temporal ${caseType.name} container worker restart drill"
                )
            )
            waitForStatus(workflow, "WAITING_APPROVAL", Duration.ofSeconds(45))

            runDockerCompose(composeProject, composeEnv, "kill", workerService)
            workerKilled = true

            workflow.approve(
                TemporalApprovalSignal(
                    approvedBy = "manager01",
                    approvedByRole = "BRANCH_MANAGER",
                    reason = approvalReason
                )
            )

            runDockerCompose(composeProject, composeEnv, "up", "-d", "--no-deps", workerService)
            workerKilled = false

            val result = WorkflowStub.fromTyped(workflow)
                .getResult(60, TimeUnit.SECONDS, TemporalBankingCaseResult::class.java)
            assertEquals("COMPLETED", result.finalStatus)
            assertEquals(caseType.name, result.caseType)
            assertEquals(expectedControlEffect, result.controlEffect)
            assertEquals(true, result.syntheticOnly)
            assertEquals(
                listOf(
                    "${caseType.name}:STARTED",
                    "${caseType.name}:WAITING_APPROVAL",
                    "${caseType.name}:COMPLETED"
                ),
                result.checkpoints
            )
        } finally {
            if (workerKilled) {
                runCatching { runDockerCompose(composeProject!!, composeEnv, "up", "-d", "--no-deps", workerService) }
            }
            serviceStubs.shutdownNow()
        }
    }

    private fun runComposeTemporalServerRestartDrill(
        caseType: TemporalBankingCaseType,
        businessReferenceId: String,
        expectedControlEffect: String,
        approvalReason: String
    ) {
        val target = System.getenv("BANKING_LAB_LIVE_TEMPORAL_TARGET")
        assumeTrue(!target.isNullOrBlank(), "set BANKING_LAB_LIVE_TEMPORAL_TARGET to run live Temporal server drill")
        val composeProject = System.getenv("BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT")
        assumeTrue(
            !composeProject.isNullOrBlank(),
            "set BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT to run live Temporal server drill"
        )

        val namespace = System.getenv("BANKING_LAB_LIVE_TEMPORAL_NAMESPACE") ?: "default"
        val taskQueue = System.getenv("BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE") ?: "banking-case-workflows"
        val composeEnv = restartComposeEnvironment(taskQueue)
        val temporalService = System.getenv("BANKING_LAB_LIVE_TEMPORAL_SERVICE") ?: "temporal"
        val serviceStubs = WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                .setTarget(target)
                .build()
        )
        var temporalKilled = false
        try {
            val client = WorkflowClient.newInstance(
                serviceStubs,
                WorkflowClientOptions.newBuilder()
                    .setNamespace(namespace)
                    .build()
            )
            val workflow = client.newWorkflowStub(
                BankingCaseTemporalWorkflow::class.java,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(taskQueue)
                    .setWorkflowId("banking-case-server-restart-${caseType.name.lowercase()}-${UUID.randomUUID()}")
                    .setWorkflowExecutionTimeout(Duration.ofSeconds(120))
                    .build()
            )

            WorkflowClient.start(
                workflow::run,
                TemporalBankingCaseInput(
                    caseType = caseType,
                    businessReferenceId = businessReferenceId,
                    requestedBy = maker(caseType),
                    requestedByRole = makerRole(caseType),
                    reason = "Synthetic live Temporal ${caseType.name} server restart drill"
                )
            )
            waitForStatus(workflow, "WAITING_APPROVAL", Duration.ofSeconds(45))

            runDockerCompose(composeProject, composeEnv, "kill", temporalService)
            temporalKilled = true
            runDockerCompose(composeProject, composeEnv, "up", "-d", "--no-deps", temporalService)
            temporalKilled = false
            waitForTemporalService(serviceStubs, Duration.ofSeconds(90))
            waitForStatus(workflow, "WAITING_APPROVAL", Duration.ofSeconds(45))

            workflow.approve(
                TemporalApprovalSignal(
                    approvedBy = "manager01",
                    approvedByRole = "BRANCH_MANAGER",
                    reason = approvalReason
                )
            )

            val result = WorkflowStub.fromTyped(workflow)
                .getResult(60, TimeUnit.SECONDS, TemporalBankingCaseResult::class.java)
            assertEquals("COMPLETED", result.finalStatus)
            assertEquals(caseType.name, result.caseType)
            assertEquals(expectedControlEffect, result.controlEffect)
            assertEquals(true, result.syntheticOnly)
            assertEquals(
                listOf(
                    "${caseType.name}:STARTED",
                    "${caseType.name}:WAITING_APPROVAL",
                    "${caseType.name}:COMPLETED"
                ),
                result.checkpoints
            )
        } finally {
            if (temporalKilled) {
                runCatching { runDockerCompose(composeProject!!, composeEnv, "up", "-d", "--no-deps", temporalService) }
            }
            serviceStubs.shutdownNow()
        }
    }

    private fun runComposePostgresRestartDrill(
        caseType: TemporalBankingCaseType,
        businessReferenceId: String,
        expectedControlEffect: String,
        approvalReason: String
    ) {
        val target = System.getenv("BANKING_LAB_LIVE_TEMPORAL_TARGET")
        assumeTrue(!target.isNullOrBlank(), "set BANKING_LAB_LIVE_TEMPORAL_TARGET to run live PostgreSQL restart drill")
        val composeProject = System.getenv("BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT")
        assumeTrue(
            !composeProject.isNullOrBlank(),
            "set BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT to run live PostgreSQL restart drill"
        )

        val namespace = System.getenv("BANKING_LAB_LIVE_TEMPORAL_NAMESPACE") ?: "default"
        val taskQueue = System.getenv("BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE") ?: "banking-case-workflows"
        val composeEnv = restartComposeEnvironment(taskQueue)
        val postgresService = System.getenv("BANKING_LAB_LIVE_POSTGRES_SERVICE") ?: "postgres"
        val serviceStubs = WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                .setTarget(target)
                .build()
        )
        var postgresKilled = false
        try {
            val client = WorkflowClient.newInstance(
                serviceStubs,
                WorkflowClientOptions.newBuilder()
                    .setNamespace(namespace)
                    .build()
            )
            val workflow = client.newWorkflowStub(
                BankingCaseTemporalWorkflow::class.java,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(taskQueue)
                    .setWorkflowId("banking-case-postgres-restart-${caseType.name.lowercase()}-${UUID.randomUUID()}")
                    .setWorkflowExecutionTimeout(Duration.ofSeconds(150))
                    .build()
            )

            WorkflowClient.start(
                workflow::run,
                TemporalBankingCaseInput(
                    caseType = caseType,
                    businessReferenceId = businessReferenceId,
                    requestedBy = maker(caseType),
                    requestedByRole = makerRole(caseType),
                    reason = "Synthetic live Temporal ${caseType.name} PostgreSQL restart drill"
                )
            )
            waitForStatus(workflow, "WAITING_APPROVAL", Duration.ofSeconds(45))

            runDockerCompose(composeProject, composeEnv, "kill", postgresService)
            postgresKilled = true
            runDockerCompose(composeProject, composeEnv, "up", "-d", "--no-deps", postgresService)
            postgresKilled = false
            waitForPostgresService(composeProject, composeEnv, postgresService, Duration.ofSeconds(90))
            waitForTemporalService(serviceStubs, Duration.ofSeconds(120))
            waitForStatus(workflow, "WAITING_APPROVAL", Duration.ofSeconds(60))

            workflow.approve(
                TemporalApprovalSignal(
                    approvedBy = "manager01",
                    approvedByRole = "BRANCH_MANAGER",
                    reason = approvalReason
                )
            )

            val result = WorkflowStub.fromTyped(workflow)
                .getResult(60, TimeUnit.SECONDS, TemporalBankingCaseResult::class.java)
            assertEquals("COMPLETED", result.finalStatus)
            assertEquals(caseType.name, result.caseType)
            assertEquals(expectedControlEffect, result.controlEffect)
            assertEquals(true, result.syntheticOnly)
            assertEquals(
                listOf(
                    "${caseType.name}:STARTED",
                    "${caseType.name}:WAITING_APPROVAL",
                    "${caseType.name}:COMPLETED"
                ),
                result.checkpoints
            )
        } finally {
            if (postgresKilled) {
                runCatching { runDockerCompose(composeProject!!, composeEnv, "up", "-d", "--no-deps", postgresService) }
            }
            serviceStubs.shutdownNow()
        }
    }

    private fun waitForStatus(workflow: BankingCaseTemporalWorkflow, status: String) {
        waitForStatus(workflow, status, Duration.ofSeconds(15))
    }

    private fun waitForStatus(workflow: BankingCaseTemporalWorkflow, status: String, timeout: Duration) {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            if (workflow.status() == status) {
                return
            }
            Thread.sleep(50)
        }
        assertEquals(status, workflow.status())
    }

    private fun startWorker(client: WorkflowClient, taskQueue: String): WorkerFactory {
        val factory = WorkerFactory.newInstance(client)
        factory
            .newWorker(taskQueue)
            .registerWorkflowImplementationTypes(BankingCaseTemporalWorkflowImpl::class.java)
        factory.start()
        return factory
    }

    private fun runDockerCompose(composeProject: String, extraEnvironment: Map<String, String>, vararg args: String) {
        val result = runDockerComposeResult(composeProject, extraEnvironment, args.toList())
        if (result.exitCode != 0) {
            fail<Unit>(
                "docker compose ${args.joinToString(" ")} failed with exit ${result.exitCode}\n${result.output}"
            )
        }
    }

    private fun runDockerComposeResult(
        composeProject: String,
        extraEnvironment: Map<String, String>,
        args: List<String>,
        timeout: Duration = Duration.ofSeconds(60)
    ): CommandResult {
        val process = ProcessBuilder(listOf("docker", "compose") + args.toList())
            .directory(Paths.get(System.getProperty("user.dir")).toFile())
            .redirectErrorStream(true)
            .also {
                it.environment()["COMPOSE_PROJECT_NAME"] = composeProject
                it.environment().putAll(extraEnvironment)
            }
            .start()
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            return CommandResult(
                exitCode = -1,
                output = "docker compose ${args.joinToString(" ")} timed out after ${timeout.seconds}s"
            )
        }
        val output = process.inputStream.bufferedReader().readText()
        return CommandResult(process.exitValue(), output)
    }

    private fun waitForTemporalService(serviceStubs: WorkflowServiceStubs, timeout: Duration) {
        val deadline = System.nanoTime() + timeout.toNanos()
        var lastError: Throwable? = null
        while (System.nanoTime() < deadline) {
            try {
                val response = serviceStubs.healthCheck()
                if (response.status == HealthCheckResponse.ServingStatus.SERVING) {
                    return
                }
            } catch (error: RuntimeException) {
                lastError = error
            }
            Thread.sleep(250)
        }
        fail<Unit>("Temporal service did not become healthy after restart: ${lastError?.message}")
    }

    private fun waitForPostgresService(
        composeProject: String,
        extraEnvironment: Map<String, String>,
        postgresService: String,
        timeout: Duration
    ) {
        val deadline = System.nanoTime() + timeout.toNanos()
        var lastOutput = ""
        while (System.nanoTime() < deadline) {
            val result = runDockerComposeResult(
                composeProject = composeProject,
                extraEnvironment = extraEnvironment,
                args = listOf("exec", "-T", postgresService, "pg_isready", "-U", "banking_lab", "-d", "banking_lab"),
                timeout = Duration.ofSeconds(5)
            )
            lastOutput = result.output
            if (result.exitCode == 0 && result.output.contains("accepting connections")) {
                return
            }
            Thread.sleep(500)
        }
        fail<Unit>("PostgreSQL service did not become healthy after restart: $lastOutput")
    }

    private fun restartComposeEnvironment(taskQueue: String): Map<String, String> =
        buildMap {
            put("BANKING_LAB_TEMPORAL_TASK_QUEUE", taskQueue)
            copyEnvironmentIfPresent("BANKING_LAB_TRACING_ENABLED")
            copyEnvironmentIfPresent("BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED")
            copyEnvironmentIfPresent("BANKING_LAB_OTLP_TRACES_ENDPOINT")
            copyEnvironmentIfPresent("BANKING_LAB_POSTGRES_PORT")
            copyEnvironmentIfPresent("BANKING_LAB_TEMPORAL_PORT")
        }

    private fun MutableMap<String, String>.copyEnvironmentIfPresent(name: String) {
        val value = System.getenv(name)
        if (!value.isNullOrBlank()) {
            put(name, value)
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

    private fun causeChain(error: Throwable?): List<String> =
        generateSequence(error) { it.cause }
            .flatMap { sequenceOf(it::class.java.name, it.message ?: "") }
            .toList()

    private data class CommandResult(
        val exitCode: Int,
        val output: String
    )
}
