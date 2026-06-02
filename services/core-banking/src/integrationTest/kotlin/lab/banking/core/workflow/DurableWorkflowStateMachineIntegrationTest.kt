package lab.banking.core.workflow

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Paths
import lab.banking.core.common.BankingLabDomainException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers
class DurableWorkflowStateMachineIntegrationTest {
    @Autowired
    lateinit var workflowService: DurableWorkflowService

    @Autowired
    lateinit var workflowRepository: DurableWorkflowRepository

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              workflow_events,
              workflow_instances
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
    }

    @Test
    fun `workflow instance and events survive repository and service recreation`() {
        val started = workflowService.start(
            StartWorkflowCommand(
                workflowType = "COMPLAINT_ANSWER",
                businessReferenceId = "CMP-DURABLE-001",
                startedBy = "complaint01",
                payload = mapOf("syntheticOnly" to true)
            )
        )
        val running = workflowService.transition(
            TransitionWorkflowCommand(
                workflowInstanceId = started.workflowInstanceId,
                nextStatus = WorkflowInstanceStatus.RUNNING,
                eventType = "COMPLAINT_CLASSIFIED",
                actorId = "complaint01",
                payload = mapOf("classification" to "ACCOUNT_ACCESS")
            )
        )
        val waiting = workflowService.transition(
            TransitionWorkflowCommand(
                workflowInstanceId = running.workflowInstanceId,
                nextStatus = WorkflowInstanceStatus.WAITING_APPROVAL,
                eventType = "ANSWER_APPROVAL_REQUESTED",
                actorId = "complaint01",
                payload = mapOf("approvalId" to "APR-DURABLE-001")
            )
        )

        val recreatedRepository = DurableWorkflowRepository(jdbc, objectMapper)
        val recreatedService = DurableWorkflowService(recreatedRepository)
        val reloaded = recreatedService.instance(started.workflowInstanceId)
        val reloadedEvents = recreatedService.events(started.workflowInstanceId)

        assertEquals(WorkflowInstanceStatus.WAITING_APPROVAL, waiting.status)
        assertEquals(WorkflowInstanceStatus.WAITING_APPROVAL, reloaded.status)
        assertEquals("COMPLAINT_ANSWER", reloaded.workflowType)
        assertEquals("CMP-DURABLE-001", reloaded.businessReferenceId)
        assertEquals(3, reloadedEvents.size)
        assertEquals(listOf("WORKFLOW_STARTED", "COMPLAINT_CLASSIFIED", "ANSWER_APPROVAL_REQUESTED"), reloadedEvents.map { it.eventType })
        assertEquals("WAITING_APPROVAL", reloadedEvents.last().payload["toStatus"])
    }

    @Test
    fun `invalid durable workflow transition returns stable workflow state violation`() {
        val started = workflowService.start(
            StartWorkflowCommand(
                workflowType = "FDS_RELEASE",
                businessReferenceId = "FDS-DURABLE-001",
                startedBy = "fds01"
            )
        )
        val running = workflowService.transition(
            TransitionWorkflowCommand(
                workflowInstanceId = started.workflowInstanceId,
                nextStatus = WorkflowInstanceStatus.RUNNING,
                eventType = "FDS_INVESTIGATION_STARTED",
                actorId = "fds01"
            )
        )
        val completed = workflowService.transition(
            TransitionWorkflowCommand(
                workflowInstanceId = running.workflowInstanceId,
                nextStatus = WorkflowInstanceStatus.COMPLETED,
                eventType = "FDS_RELEASE_COMPLETED",
                actorId = "manager01"
            )
        )

        val error = assertThrows(BankingLabDomainException::class.java) {
            workflowService.transition(
                TransitionWorkflowCommand(
                    workflowInstanceId = completed.workflowInstanceId,
                    nextStatus = WorkflowInstanceStatus.RUNNING,
                    eventType = "FDS_REOPEN_ATTEMPTED",
                    actorId = "fds01"
                )
            )
        }

        assertEquals("WORKFLOW_STATE_VIOLATION", error.code)
        assertEquals("workflow", error.domain)
        assertEquals("VALID_WORKFLOW_TRANSITION_REQUIRED", error.policy)
        assertEquals(3, workflowRepository.events(started.workflowInstanceId).size)
        assertEquals(WorkflowInstanceStatus.COMPLETED, workflowService.instance(started.workflowInstanceId).status)
    }

    companion object {
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
        }
    }
}
