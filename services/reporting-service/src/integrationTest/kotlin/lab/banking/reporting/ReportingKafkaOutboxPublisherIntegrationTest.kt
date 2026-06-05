package lab.banking.reporting

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit
import lab.banking.reporting.domain.GenerateReportCommand
import lab.banking.reporting.domain.ReportingService
import lab.banking.reporting.eventing.ReportingKafkaPublisherConfig
import lab.banking.reporting.eventing.ReportingOutboxKafkaEnvelope
import lab.banking.reporting.eventing.ReportingOutboxPublisherPort
import lab.banking.reporting.security.ReportingPrincipal
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
import org.testcontainers.redpanda.RedpandaContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@Testcontainers
class ReportingKafkaOutboxPublisherIntegrationTest {
    @Autowired
    lateinit var reportingService: ReportingService

    @Autowired
    lateinit var publisher: ReportingOutboxPublisherPort

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              reporting_outbox_events,
              reporting_access_audit_events,
              report_artifacts
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
    }

    @Test
    fun `Redpanda publisher emits generated and exported reporting domain events`() {
        val topic = uniqueTopic("banking-lab-reporting-events")
        createTopic(topic)
        val principal = ReportingPrincipal("reporting01", setOf("REPORTING_ANALYST"))
        val generated = reportingService.generate(
            GenerateReportCommand(
                reportType = "EVIDENCE_COVERAGE",
                requestedBy = "reporting01",
                requestedByRole = "REPORTING_ANALYST",
                reason = "Synthetic reporting Kafka publisher generation",
                idempotencyKey = "RPT-KAFKA-GEN-001"
            ),
            principal
        ).item
        reportingService.exportArtifact(
            generated.artifactId,
            "Synthetic reporting Kafka publisher export",
            principal
        )

        val result = publisher.publishAvailable(
            ReportingKafkaPublisherConfig(
                bootstrapServers = redpanda.bootstrapServers,
                topic = topic,
                clientId = "reporting-kafka-publisher-${UUID.randomUUID()}"
            ),
            limit = 5
        )

        assertEquals(2, result.attempted)
        assertEquals(2, result.published)
        assertEquals(0, result.failed)
        assertEquals(1, countRows("reporting_outbox_events WHERE event_type = 'ReportArtifactGenerated' AND status = 'PUBLISHED'"))
        assertEquals(1, countRows("reporting_outbox_events WHERE event_type = 'ReportArtifactExported' AND status = 'PUBLISHED'"))

        val envelopes = consume(topic, "reporting-kafka-publisher-test-${UUID.randomUUID()}", expectedCount = 2)
        assertEquals(setOf("ReportArtifactGenerated", "ReportArtifactExported"), envelopes.map { it.eventType }.toSet())
        for (envelope in envelopes) {
            assertTrue(envelope.outboxEventId.startsWith("RPO-"))
            assertEquals("REPORT_ARTIFACT", envelope.aggregateType)
            assertEquals(generated.artifactId, envelope.aggregateId)
            assertEquals(true, envelope.headers["syntheticOnly"])
            assertEquals("reporting-service", envelope.headers["sourceService"])
            assertEquals(generated.artifactId, envelope.payload["artifactId"])
            assertEquals(true, envelope.payload["syntheticOnly"])
            assertEquals(false, envelope.payload["ledgerRowsMutated"])
        }
    }

    private fun createTopic(topic: String) {
        val props = Properties().apply {
            put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, redpanda.bootstrapServers)
        }
        AdminClient.create(props).use { admin ->
            admin.createTopics(listOf(NewTopic(topic, 1, 1.toShort())))
                .all()
                .get(10, TimeUnit.SECONDS)
        }
    }

    private fun consume(topic: String, groupId: String, expectedCount: Int): List<ReportingOutboxKafkaEnvelope> {
        val envelopes = mutableListOf<ReportingOutboxKafkaEnvelope>()
        KafkaConsumer<String, String>(consumerProperties(groupId)).use { consumer ->
            consumer.subscribe(listOf(topic))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (System.nanoTime() < deadline && envelopes.size < expectedCount) {
                val records = consumer.poll(Duration.ofMillis(250))
                for (record in records) {
                    assertTrue(record.headers().any { it.key() == "syntheticOnly" })
                    envelopes += objectMapper.readValue(record.value(), ReportingOutboxKafkaEnvelope::class.java)
                }
            }
        }
        if (envelopes.size != expectedCount) {
            throw AssertionError("reporting Kafka publisher produced ${envelopes.size} records on $topic; expected $expectedCount")
        }
        return envelopes
    }

    private fun consumerProperties(groupId: String): Properties =
        Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, redpanda.bootstrapServers)
            put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
            put(ConsumerConfig.CLIENT_ID_CONFIG, "$groupId-client")
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        }

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $tableExpression", emptyMap<String, Any?>(), Int::class.java) ?: 0

    private fun uniqueTopic(prefix: String): String =
        "$prefix-${UUID.randomUUID().toString().lowercase()}"

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        @Container
        @JvmStatic
        val redpanda = RedpandaContainer(
            DockerImageName.parse("docker.redpanda.com/redpandadata/redpanda:v24.3.7")
        )

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
