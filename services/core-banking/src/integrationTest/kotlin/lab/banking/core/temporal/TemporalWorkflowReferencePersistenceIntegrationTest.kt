package lab.banking.core.temporal

import java.nio.file.Paths
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
class TemporalWorkflowReferencePersistenceIntegrationTest {
    @Autowired
    lateinit var references: TemporalWorkflowReferenceService

    @Autowired
    lateinit var complaintCases: lab.banking.core.complaint.ComplaintCaseService

    @Autowired
    lateinit var fdsCases: lab.banking.core.fds.FdsCaseService

    @Autowired
    lateinit var amlCases: lab.banking.core.aml.AmlCaseService

    @Autowired
    lateinit var reconciliation: lab.banking.core.reconciliation.ReconciliationOpsService

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              reconciliation_adjustment_requests,
              reconciliation_items,
              aml_case_comments,
              aml_cases,
              fds_case_timeline,
              fds_cases,
              complaint_case_timeline,
              complaint_cases,
              account_holds,
              operator_approvals,
              audit_events,
              account_balance_projections,
              ledger_postings,
              ledger_transactions,
              idempotency_keys,
              accounts,
              customers
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
        seedDomainRows()
    }

    @Test
    fun `Temporal workflow references persist on case and hold domain rows`() {
        references.attach(ref(TemporalBankingCaseType.COMPLAINT_ANSWER, "CMP-TEMPORAL-001", "wf-complaint-answer", "run-001"))
        references.attach(ref(TemporalBankingCaseType.FDS_RELEASE, "FDS-TEMPORAL-001", "wf-fds-release", "run-002"))
        references.attach(ref(TemporalBankingCaseType.AML_CLOSURE, "AML-TEMPORAL-001", "wf-aml-closure", "run-003"))
        references.attach(ref(TemporalBankingCaseType.RECONCILIATION_ADJUSTMENT, "RAR-TEMPORAL-001", "wf-recon-adjustment", "run-004"))
        references.attach(ref(TemporalBankingCaseType.ACCOUNT_HOLD, "HOLD-TEMPORAL-001", "wf-account-hold", "run-005"))

        assertEquals("wf-complaint-answer", complaintCases.find("CMP-TEMPORAL-001").temporalWorkflow?.workflowId)
        assertEquals("run-001", complaintCases.find("CMP-TEMPORAL-001").temporalWorkflow?.runId)
        assertEquals("wf-fds-release", fdsCases.find("FDS-TEMPORAL-001").temporalWorkflow?.workflowId)
        assertEquals("wf-aml-closure", amlCases.find("AML-TEMPORAL-001").temporalWorkflow?.workflowId)
        assertEquals(
            "wf-recon-adjustment",
            reconciliation.find("REC-TEMPORAL-001").adjustmentRequest?.temporalWorkflow?.workflowId
        )
        assertEquals("wf-account-hold", accountHoldWorkflowId("HOLD-TEMPORAL-001"))
    }

    @Test
    fun `missing Temporal reference target fails with structured workflow not found`() {
        val error = assertThrows(lab.banking.core.common.BankingLabDomainException::class.java) {
            references.attach(ref(TemporalBankingCaseType.FDS_BLOCK, "FDS-MISSING", "wf-missing", "run-missing"))
        }

        assertEquals("RESOURCE_NOT_FOUND", error.code)
        assertEquals("resource", error.domain)
    }

    private fun ref(
        caseType: TemporalBankingCaseType,
        referenceId: String,
        workflowId: String,
        runId: String
    ): AttachTemporalWorkflowReferenceCommand =
        AttachTemporalWorkflowReferenceCommand(
            businessType = caseType,
            businessReferenceId = referenceId,
            workflowId = workflowId,
            runId = runId
        )

    private fun seedDomainRows() {
        jdbc.update(
            """
            INSERT INTO customers (customer_id, customer_name, customer_grade, risk_grade)
            VALUES ('SYN-CUS-001', 'Lab Customer Alpha', 'STANDARD', 'LOW')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO accounts (account_id, customer_id, account_no, currency, status)
            VALUES ('ACC-SYN-001-001', 'SYN-CUS-001', 'LAB-001-000001', 'KRW', 'ACTIVE')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO account_balance_projections (
              account_id, currency, ledger_balance_minor, available_balance_minor, hold_amount_minor
            )
            VALUES ('ACC-SYN-001-001', 'KRW', 100000000, 100000000, 0)
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        seedApproval("APR-TEMPORAL-001", "RECONCILIATION_ADJUSTMENT", "REC-TEMPORAL-001")
        jdbc.update(
            """
            INSERT INTO complaint_cases (
              complaint_case_id, customer_id, category, description, status, sla_due_at
            )
            VALUES (
              'CMP-TEMPORAL-001', 'SYN-CUS-001', 'ACCOUNT_ACCESS',
              'Synthetic Temporal complaint', 'IN_REVIEW', now() + interval '7 days'
            )
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO fds_cases (
              fds_case_id, transfer_reference_id, customer_id, status, risk_score, alerts_json
            )
            VALUES (
              'FDS-TEMPORAL-001', 'TRF-TEMPORAL-001', 'SYN-CUS-001',
              'INVESTIGATING', 900, '[]'::jsonb
            )
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO aml_cases (
              aml_case_id, customer_id, status, risk_score, alerts_json, str_simulation_json
            )
            VALUES (
              'AML-TEMPORAL-001', 'SYN-CUS-001', 'INVESTIGATING',
              850, '[]'::jsonb, '{}'::jsonb
            )
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO reconciliation_items (
              reconciliation_item_id, business_date, source_system, amount_minor, currency, status, owner_id
            )
            VALUES ('REC-TEMPORAL-001', :businessDate, 'EOD_EXTERNAL_SIM', 1000, 'KRW', 'ADJUSTMENT_REQUESTED', 'ops01')
            """.trimIndent(),
            mapOf("businessDate" to LocalDate.parse("2026-06-02"))
        )
        jdbc.update(
            """
            INSERT INTO reconciliation_adjustment_requests (
              reconciliation_adjustment_request_id, reconciliation_item_id, approval_id,
              account_id, direction, amount_minor, business_date, idempotency_key,
              reason, requested_by, status
            )
            VALUES (
              'RAR-TEMPORAL-001', 'REC-TEMPORAL-001', 'APR-TEMPORAL-001',
              'ACC-SYN-001-001', 'CREDIT', 1000, :businessDate, 'REC-TEMPORAL-IDEMP',
              'Synthetic Temporal reconciliation adjustment', 'ops01', 'REQUESTED'
            )
            """.trimIndent(),
            mapOf("businessDate" to LocalDate.parse("2026-06-03"))
        )
        jdbc.update(
            """
            INSERT INTO account_holds (
              hold_id, account_id, hold_amount_minor, reason_code, status, approval_id
            )
            VALUES (
              'HOLD-TEMPORAL-001', 'ACC-SYN-001-001', 1000,
              'SYNTHETIC_REVIEW', 'ACTIVE', NULL
            )
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

    private fun seedApproval(approvalId: String, businessType: String, referenceId: String) {
        jdbc.update(
            """
            INSERT INTO operator_approvals (
              approval_id, business_type, business_reference_id, requested_by,
              request_reason, before_snapshot_json, after_snapshot_json, status
            )
            VALUES (
              :approvalId, :businessType, :referenceId, 'ops01',
              'Synthetic Temporal approval', '{}'::jsonb, '{}'::jsonb, 'PENDING'
            )
            """.trimIndent(),
            mapOf(
                "approvalId" to approvalId,
                "businessType" to businessType,
                "referenceId" to referenceId
            )
        )
    }

    private fun accountHoldWorkflowId(holdId: String): String? =
        jdbc.queryForObject(
            "SELECT temporal_workflow_id FROM account_holds WHERE hold_id = :holdId",
            mapOf("holdId" to holdId),
            String::class.java
        )

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
