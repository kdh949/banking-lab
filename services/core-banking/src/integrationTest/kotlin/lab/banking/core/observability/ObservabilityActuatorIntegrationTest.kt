package lab.banking.core.observability

import java.nio.file.Paths
import lab.banking.core.eventing.KafkaOutboxPublishBatchResult
import lab.banking.core.eventing.OutboxWorkerMetrics
import lab.banking.core.ledger.application.LedgerCommandMetrics
import lab.banking.core.security.AuthorizationMetrics
import lab.banking.core.temporal.TemporalWorkerMetrics
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@Testcontainers
@ExtendWith(OutputCaptureExtension::class)
class ObservabilityActuatorIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var temporalWorkerMetrics: TemporalWorkerMetrics

    @Autowired
    lateinit var outboxWorkerMetrics: OutboxWorkerMetrics

    @Autowired
    lateinit var ledgerCommandMetrics: LedgerCommandMetrics

    @Autowired
    lateinit var authorizationMetrics: AuthorizationMetrics

    @Test
    fun `Prometheus actuator exposes Temporal worker control metrics`() {
        temporalWorkerMetrics.recordStarted()

        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("banking_lab_temporal_worker_running")))
            .andExpect(content().string(containsString("banking_lab_temporal_worker_starts_total")))
            .andExpect(content().string(containsString("namespace=\"default\"")))
            .andExpect(content().string(containsString("task_queue=\"banking-case-workflows\"")))
    }

    @Test
    fun `Prometheus actuator exposes Outbox worker control and delivery metrics`() {
        outboxWorkerMetrics.recordStarted()
        outboxWorkerMetrics.recordBatch(
            KafkaOutboxPublishBatchResult(
                attempted = 2,
                published = 1,
                failed = 1,
                deadLettered = 0,
                results = emptyList()
            )
        )

        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("banking_lab_outbox_worker_running")))
            .andExpect(content().string(containsString("banking_lab_outbox_worker_starts_total")))
            .andExpect(content().string(containsString("banking_lab_outbox_worker_events_attempted_total")))
            .andExpect(content().string(containsString("banking_lab_outbox_worker_events_published_total")))
            .andExpect(content().string(containsString("topic=\"banking.lab.domain-events\"")))
            .andExpect(content().string(containsString("client_id=\"core-banking-outbox-worker\"")))
    }

    @Test
    fun `Prometheus actuator exposes core control metrics required by runbooks`() {
        ledgerCommandMetrics.record("DEPOSIT") { "ok" }
        runCatching {
            ledgerCommandMetrics.record("WITHDRAWAL") {
                throw IllegalStateException("synthetic metrics error")
            }
        }
        ledgerCommandMetrics.recordIdempotencyReplay("DEPOSIT")
        authorizationMetrics.recordDenied(
            code = "STEP_UP_REQUIRED",
            policy = "STEP_UP_REAUTHENTICATION_REQUIRED",
            routeFamily = "AUD-501"
        )

        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("banking_lab_ledger_command_latency_seconds")))
            .andExpect(content().string(containsString("banking_lab_ledger_command_errors_total")))
            .andExpect(content().string(containsString("banking_lab_idempotency_replay_count_total")))
            .andExpect(content().string(containsString("banking_lab_outbox_pending_count")))
            .andExpect(content().string(containsString("banking_lab_outbox_dead_letter_count")))
            .andExpect(content().string(containsString("banking_lab_authorization_denied_count_total")))
            .andExpect(content().string(containsString("banking_lab_audit_append_failure_count_total")))
    }


    @Test
    fun `HTTP access log carries request trace and span correlation ids`(output: CapturedOutput) {
        val traceId = "4bf92f3577b34da6a3ce929d0e0e4736"
        mockMvc.perform(
            get("/health")
                .header("x-request-id", "REQ-OTEL-CORRELATION")
                .header("traceparent", "00-$traceId-00f067aa0ba902b7-01")
        )
            .andExpect(status().isOk)

        assertThat(output.out)
            .contains("observability.access")
            .contains("path=/health")
            .contains("requestId=REQ-OTEL-CORRELATION")
            .contains("traceId=$traceId")
            .containsPattern("spanId=[a-f0-9]{16}")
    }

    @Test
    fun `HTTP access log rejects unsafe request correlation values without logging secrets`(output: CapturedOutput) {
        mockMvc.perform(get("/health").header("x-request-id", "Bearer synthetic-secret-value"))
            .andExpect(status().isOk)

        assertThat(output.out)
            .contains("observability.access")
            .containsPattern("requestId=REQ-[A-F0-9-]{36}")
            .doesNotContain("synthetic-secret-value")
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
            registry.add("management.tracing.enabled") { "true" }
            registry.add("management.otlp.tracing.export.enabled") { "false" }
        }
    }
}
