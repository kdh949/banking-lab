package lab.banking.core.observability

import java.nio.file.Paths
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
    fun `HTTP access log carries request trace and span correlation ids`(output: CapturedOutput) {
        mockMvc.perform(get("/health").header("x-request-id", "REQ-OTEL-CORRELATION"))
            .andExpect(status().isOk)

        assertThat(output.out)
            .contains("observability.access")
            .contains("path=/health")
            .contains("requestId=REQ-OTEL-CORRELATION")
            .containsPattern("traceId=[a-f0-9]{32}")
            .containsPattern("spanId=[a-f0-9]{16}")
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
