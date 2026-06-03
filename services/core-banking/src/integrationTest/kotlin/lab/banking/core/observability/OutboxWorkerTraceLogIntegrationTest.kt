package lab.banking.core.observability

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.nio.file.Paths
import lab.banking.core.eventing.KafkaOutboxPublishBatchResult
import lab.banking.core.eventing.KafkaOutboxPublishResult
import lab.banking.core.eventing.KafkaOutboxPublisherConfig
import lab.banking.core.eventing.OutboxEventStatus
import lab.banking.core.eventing.OutboxPublisherPort
import lab.banking.core.eventing.OutboxWorkerMetrics
import lab.banking.core.eventing.OutboxWorkerProperties
import lab.banking.core.eventing.OutboxWorkerRunner
import lab.banking.core.eventing.OutboxWorkerTraceLogger
import org.assertj.core.api.Assertions.assertThat
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
class OutboxWorkerTraceLogIntegrationTest {
    @Autowired
    lateinit var traceLogger: OutboxWorkerTraceLogger

    @Test
    fun `outbox worker batch logs carry trace and span correlation ids`(output: CapturedOutput) {
        val topic = "banking.lab.outbox.trace"
        val clientId = "trace-test-outbox-worker"
        val result = KafkaOutboxPublishBatchResult(
            attempted = 2,
            published = 1,
            failed = 1,
            deadLettered = 0,
            results = listOf(
                KafkaOutboxPublishResult(
                    outboxEventId = "OBX-TRACE-PUBLISHED-001",
                    status = OutboxEventStatus.PUBLISHED,
                    topic = topic,
                    partition = 0,
                    offset = 41,
                    errorMessage = null
                ),
                KafkaOutboxPublishResult(
                    outboxEventId = "OBX-TRACE-FAILED-001",
                    status = OutboxEventStatus.FAILED,
                    topic = topic,
                    partition = null,
                    offset = null,
                    errorMessage = "synthetic publish failure"
                )
            )
        )
        val runner = OutboxWorkerRunner(
            publisher = RecordingOutboxPublisher(result),
            properties = OutboxWorkerProperties(
                bootstrapServers = "redpanda:9092",
                topic = topic,
                worker = OutboxWorkerProperties.Worker(
                    enabled = true,
                    clientId = clientId,
                    batchSize = 2
                )
            ),
            metrics = OutboxWorkerMetrics(SimpleMeterRegistry(), topic, clientId),
            traceLogger = traceLogger
        )

        runner.runOneBatch()

        assertThat(output.all)
            .contains("observability.outbox.worker")
            .contains("event=batch")
            .contains("topic=$topic")
            .contains("clientId=$clientId")
            .contains("attempted=2")
            .contains("published=1")
            .contains("failed=1")
            .contains("deadLettered=0")
            .contains("outboxEventIds=OBX-TRACE-PUBLISHED-001,OBX-TRACE-FAILED-001")
            .contains("errorType=none")
            .contains("syntheticOnly=true")
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

    private class RecordingOutboxPublisher(
        private val result: KafkaOutboxPublishBatchResult
    ) : OutboxPublisherPort {
        override fun publishAvailable(config: KafkaOutboxPublisherConfig, limit: Int): KafkaOutboxPublishBatchResult =
            result
    }
}
