package lab.banking.core.parameters

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.util.Base64
import org.hamcrest.Matchers.greaterThan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(properties = ["banking-lab.security.enabled=true"])
@AutoConfigureMockMvc
@Testcontainers
class ParameterAdminIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun resetDatabase() {
        deleteNonSeedParameterVersions()
        jdbc.jdbcTemplate.execute(
            """
            DELETE FROM parameter_change_requests;
            DELETE FROM customer_transfer_results;
            DELETE FROM fds_case_timeline;
            DELETE FROM fds_cases;
            DELETE FROM outbox_events;
            DELETE FROM inbox_events;
            DELETE FROM idempotency_keys;
            DELETE FROM ledger_postings;
            DELETE FROM ledger_transactions;
            DELETE FROM account_balance_projections;
            DELETE FROM account_limits;
            DELETE FROM account_holds;
            DELETE FROM accounts;
            DELETE FROM customer_kyc_profiles;
            DELETE FROM customers;
            DELETE FROM operator_approvals;
            DELETE FROM audit_events;
            """.trimIndent()
        )
        restoreSeedCurrentVersionPointers()
        seedSyntheticTransferFixtures()
    }

    @Test
    fun `parameter APIs require approval honor effective dates and drive FDS detection`() {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)

        val endpoints = listOf(
            Endpoint("/api/ops/parameters/reconciliation", "/api/ops/parameters/reconciliation/history", "/api/ops/parameters/reconciliation/change-requests", "OPS_MANAGER", "ops01", "autoMatchToleranceMinor", 2_000),
            Endpoint("/api/staff/audit-parameters", "/api/staff/audit-parameters/history", "/api/staff/audit-parameters/change-requests", "AUDITOR", "audit01", "retentionYears", 8),
            Endpoint("/api/staff/fds-parameters", "/api/staff/fds-parameters/history", "/api/staff/fds-parameters/change-requests", "FDS_REVIEWER", "fds01", "highAmountMinor", 3_000_000),
            Endpoint("/api/admin/platform/security-parameters", "/api/admin/platform/security-parameters/history", "/api/admin/platform/security-parameters/change-requests", "COMPLIANCE_MANAGER", "security01", "staffSessionTtlSeconds", 4_000),
            Endpoint("/api/admin/platform/authorization-parameters", "/api/admin/platform/authorization-parameters/history", "/api/admin/platform/authorization-parameters/change-requests", "COMPLIANCE_MANAGER", "security01", "reasonRequiredScreens", "OPS-301,AUD-201,FDS-301,ADM-201,ADM-301")
        )

        endpoints.forEachIndexed { index, endpoint ->
            mockMvc.perform(
                get(endpoint.listPath)
                    .header("Authorization", bearer(endpoint.actor, listOf(endpoint.role)))
                    .param("reason", "Synthetic parameter view")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(greaterThan(0)))

            mockMvc.perform(
                get(endpoint.historyPath)
                    .header("Authorization", bearer(endpoint.actor, listOf(endpoint.role)))
                    .param("reason", "Synthetic parameter history view")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(greaterThan(0)))

            val request = mockMvc.perform(
                post(endpoint.requestPath)
                    .header("Authorization", bearer(endpoint.actor, listOf(endpoint.role)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(parameterChangeJson(endpoint, endpoint.value, tomorrow, "PARAM-SMOKE-$index"))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.item.status").value("PENDING_APPROVAL"))
                .andReturn()
            val approvalId = objectMapper.readTree(request.response.contentAsString)
                .path("approval")
                .path("approvalId")
                .asText()
            assertEquals(0, countRows("parameter_change_requests WHERE approval_id = '$approvalId' AND status = 'APPLIED'"))
        }

        mockMvc.perform(
            post("/api/staff/fds-parameters/change-requests")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-PARAM-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(parameterChangeJson(endpoints[2], 3_000_000, today, "PARAM-UNAUTH"))
        )
            .andExpect(status().isForbidden)

        val defaultFdsVersion = historyVersionId("/api/staff/fds-parameters/history", "FPV-SEED-HIGH-AMOUNT")
        val futureApprovalId = requestFdsThreshold("PARAM-FDS-FUTURE", 3_000_000, tomorrow)
        assertEquals("5000000", fdsHighAmount(today))
        assertEquals("5000000", fdsHighAmount(tomorrow))

        mockMvc.perform(
            post("/api/staff/approvals/$futureApprovalId/approve")
                .header("Authorization", bearer("fds01", listOf("COMPLIANCE_MANAGER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"approvedBy":"fds01","approvedByRole":"COMPLIANCE_MANAGER","screenId":"FDS-301"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("MAKER_CHECKER_SELF_APPROVAL_REJECTED"))

        approve(futureApprovalId)
        assertEquals("5000000", fdsHighAmount(today))
        assertEquals("3000000", fdsHighAmount(tomorrow))

        mockMvc.perform(
            post("/api/customer/transfers")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-PARAM-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(customerTransferJson("PARAM-FDS-POST-BEFORE", 4_000_000))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.status").value("POSTED"))

        val immediateApprovalId = requestFdsThreshold("PARAM-FDS-IMMEDIATE", 3_000_000, today)
        approve(immediateApprovalId)
        assertEquals("3000000", fdsHighAmount(today))

        mockMvc.perform(
            post("/api/customer/transfers")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-PARAM-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(customerTransferJson("PARAM-FDS-HELD-AFTER", 4_000_000))
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.item.status").value("HELD"))
            .andExpect(jsonPath("$.item.caseId").exists())

        val rollbackApprovalId = requestFdsRollback("PARAM-FDS-ROLLBACK", defaultFdsVersion, today)
        approve(rollbackApprovalId)
        assertEquals("5000000", fdsHighAmount(today))

        mockMvc.perform(
            post("/api/customer/transfers")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-PARAM-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(customerTransferJson("PARAM-FDS-POST-ROLLBACK", 4_000_000))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.status").value("POSTED"))

        assertTrue(countRows("audit_events WHERE event_type LIKE 'PARAMETER_%'") >= 4)
        assertEquals(0, unbalancedTransactionCount())
    }

    private fun requestFdsThreshold(idempotencyKey: String, threshold: Long, effectiveFrom: LocalDate): String {
        val response = mockMvc.perform(
            post("/api/staff/fds-parameters/change-requests")
                .header("Authorization", bearer("fds01", listOf("FDS_REVIEWER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(parameterChangeJson(Endpoint.fds, threshold, effectiveFrom, idempotencyKey))
        )
            .andExpect(status().isCreated)
            .andReturn()
        return objectMapper.readTree(response.response.contentAsString)
            .path("approval")
            .path("approvalId")
            .asText()
    }

    private fun requestFdsRollback(idempotencyKey: String, rollbackVersionId: String, effectiveFrom: LocalDate): String {
        val response = mockMvc.perform(
            post("/api/staff/fds-parameters/change-requests")
                .header("Authorization", bearer("fds01", listOf("FDS_REVIEWER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "parameterKey": "highAmountMinor",
                      "rollbackOfVersionId": "$rollbackVersionId",
                      "effectiveFrom": "$effectiveFrom",
                      "rollbackPlan": "Restore seed synthetic threshold as a new version",
                      "requestedBy": "fds01",
                      "requestedByRole": "FDS_REVIEWER",
                      "reason": "Synthetic FDS threshold rollback",
                      "idempotencyKey": "$idempotencyKey"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andReturn()
        return objectMapper.readTree(response.response.contentAsString)
            .path("approval")
            .path("approvalId")
            .asText()
    }

    private fun approve(approvalId: String) {
        mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .header("Authorization", bearer("compliance02", listOf("COMPLIANCE_MANAGER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"approvedBy":"compliance02","approvedByRole":"COMPLIANCE_MANAGER","screenId":"FDS-301"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executed").value(true))
            .andExpect(jsonPath("$.parameterChangeRequest.status").value("APPLIED"))
    }

    private fun fdsHighAmount(asOf: LocalDate): String {
        val response = mockMvc.perform(
            get("/api/staff/fds-parameters")
                .header("Authorization", bearer("fds01", listOf("FDS_REVIEWER")))
                .param("reason", "Synthetic FDS parameter read")
                .param("asOf", asOf.toString())
        )
            .andExpect(status().isOk)
            .andReturn()
        val items = objectMapper.readTree(response.response.contentAsString).path("items")
        return items.first { it.path("parameterKey").asText() == "highAmountMinor" }
            .path("currentValue")
            .asText()
    }

    private fun historyVersionId(path: String, versionId: String): String {
        val response = mockMvc.perform(
            get(path)
                .header("Authorization", bearer("fds01", listOf("FDS_REVIEWER")))
                .param("reason", "Synthetic FDS parameter history")
        )
            .andExpect(status().isOk)
            .andReturn()
        val items = objectMapper.readTree(response.response.contentAsString).path("items")
        return items.first { it.path("parameterVersionId").asText() == versionId }
            .path("parameterVersionId")
            .asText()
    }

    private fun parameterChangeJson(endpoint: Endpoint, value: Any, effectiveFrom: LocalDate, idempotencyKey: String): String {
        val valueJson = when (value) {
            is Number -> value.toString()
            else -> objectMapper.writeValueAsString(value)
        }
        return """
        {
          "parameterKey": "${endpoint.parameterKey}",
          "scheduledValue": $valueJson,
          "effectiveFrom": "$effectiveFrom",
          "rollbackPlan": "Apply a synthetic parameter rollback as a new approved version",
          "requestedBy": "${endpoint.actor}",
          "requestedByRole": "${endpoint.role}",
          "reason": "Synthetic parameter change",
          "idempotencyKey": "$idempotencyKey"
        }
        """.trimIndent()
    }

    private fun customerTransferJson(idempotencyKey: String, amountMinor: Long): String =
        """
        {
          "customerId": "SYN-CUS-PARAM-001",
          "fromAccountId": "ACC-PARAM-FROM",
          "toAccountId": "ACC-PARAM-TO",
          "amountMinor": $amountMinor,
          "idempotencyKey": "$idempotencyKey",
          "requestedBy": "SYN-CUS-PARAM-001",
          "reason": "Synthetic FDS parameter behavior proof"
        }
        """.trimIndent()

    private fun seedSyntheticTransferFixtures() {
        TransactionTemplate(transactionManager).executeWithoutResult {
        jdbc.update(
            """
            INSERT INTO customers (customer_id, customer_name, customer_grade, risk_grade)
            VALUES
              ('BANK', 'Synthetic Bank', 'SYSTEM', 'LOW'),
              ('SYN-CUS-PARAM-001', 'Synthetic Parameter Customer', 'STANDARD', 'LOW'),
              ('SYN-CUS-PARAM-002', 'Synthetic Parameter Beneficiary', 'STANDARD', 'LOW')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO accounts (account_id, customer_id, account_no, currency, status)
            VALUES
              ('BANK-SUSPENSE', 'BANK', 'LAB-000-000000', 'KRW', 'ACTIVE'),
              ('ACC-PARAM-FROM', 'SYN-CUS-PARAM-001', 'LAB-090-000001', 'KRW', 'ACTIVE'),
              ('ACC-PARAM-TO', 'SYN-CUS-PARAM-002', 'LAB-090-000002', 'KRW', 'ACTIVE')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO account_limits (
              account_id, daily_transfer_limit_minor, single_transfer_limit_minor,
              monthly_transfer_limit_minor, customer_web_daily_transfer_limit_minor,
              customer_web_monthly_transfer_limit_minor, customer_web_single_transfer_limit_minor
            )
            VALUES ('ACC-PARAM-FROM', 50000000, 50000000, 100000000, 50000000, 100000000, 50000000)
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO ledger_transactions (
              ledger_transaction_id, transaction_type, business_reference_id, idempotency_key,
              business_date, status, requested_by, requested_channel, posted_at, reason
            )
            VALUES (
              'TX-PARAM-OPENING', 'SYNTHETIC_OPENING_BALANCE', 'TX-PARAM-OPENING',
              'SEED-TX-PARAM-OPENING', CURRENT_DATE, 'POSTED',
              'SEED', 'SYNTHETIC_DATA_GENERATOR', now(), 'Synthetic parameter funding'
            )
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO ledger_postings (
              ledger_posting_id, ledger_transaction_id, account_id, currency, direction, amount_minor, posting_type
            )
            VALUES
              ('LP-PARAM-OPENING-D', 'TX-PARAM-OPENING', 'BANK-SUSPENSE', 'KRW', 'DEBIT', 20000000, 'OPENING'),
              ('LP-PARAM-OPENING-C', 'TX-PARAM-OPENING', 'ACC-PARAM-FROM', 'KRW', 'CREDIT', 20000000, 'OPENING')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO account_balance_projections (
              account_id, currency, ledger_balance_minor, available_balance_minor, hold_amount_minor
            )
            VALUES
              ('BANK-SUSPENSE', 'KRW', -20000000, -20000000, 0),
              ('ACC-PARAM-FROM', 'KRW', 20000000, 20000000, 0),
              ('ACC-PARAM-TO', 'KRW', 0, 0, 0)
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        }
    }

    private fun deleteNonSeedParameterVersions() {
        listOf(
            "reconciliation_parameter_versions",
            "audit_retention_parameter_versions",
            "fds_rule_parameter_versions",
            "security_policy_parameter_versions",
            "menu_role_parameter_versions"
        ).forEach { table ->
            jdbc.update("DELETE FROM $table WHERE created_by <> 'SEED'", emptyMap<String, Any?>())
        }
    }

    private fun restoreSeedCurrentVersionPointers() {
        jdbc.update("UPDATE reconciliation_parameters SET current_version_id = 'RPV-SEED-AUTO-MATCH-TOLERANCE' WHERE parameter_key = 'autoMatchToleranceMinor'", emptyMap<String, Any?>())
        jdbc.update("UPDATE reconciliation_parameters SET current_version_id = 'RPV-SEED-UNMATCHED-SLA' WHERE parameter_key = 'unmatchedItemSlaHours'", emptyMap<String, Any?>())
        jdbc.update("UPDATE audit_retention_parameters SET current_version_id = 'APV-SEED-RETENTION-YEARS' WHERE parameter_key = 'retentionYears'", emptyMap<String, Any?>())
        jdbc.update("UPDATE audit_retention_parameters SET current_version_id = 'APV-SEED-HASH-CADENCE' WHERE parameter_key = 'hashChainVerificationCadenceHours'", emptyMap<String, Any?>())
        jdbc.update("UPDATE fds_rule_parameters SET current_version_id = 'FPV-SEED-HIGH-AMOUNT' WHERE parameter_key = 'highAmountMinor'", emptyMap<String, Any?>())
        jdbc.update("UPDATE fds_rule_parameters SET current_version_id = 'FPV-SEED-NEW-DEVICE-HOURS' WHERE parameter_key = 'newDeviceHoldHours'", emptyMap<String, Any?>())
        jdbc.update("UPDATE fds_rule_parameters SET current_version_id = 'FPV-SEED-VELOCITY-WINDOW' WHERE parameter_key = 'velocityWindowMinutes'", emptyMap<String, Any?>())
        jdbc.update("UPDATE security_policy_parameters SET current_version_id = 'SPV-SEED-SIMULATOR-TOKENS' WHERE parameter_key = 'simulatorTokensEnabled'", emptyMap<String, Any?>())
        jdbc.update("UPDATE security_policy_parameters SET current_version_id = 'SPV-SEED-PASSKEY-DUAL-CONTROL' WHERE parameter_key = 'passkeyRecoveryDualControlRequired'", emptyMap<String, Any?>())
        jdbc.update("UPDATE security_policy_parameters SET current_version_id = 'SPV-SEED-STAFF-TTL' WHERE parameter_key = 'staffSessionTtlSeconds'", emptyMap<String, Any?>())
        jdbc.update("UPDATE menu_role_parameters SET current_version_id = 'MPV-SEED-ROLE-MENU' WHERE parameter_key = 'roleMenuMap'", emptyMap<String, Any?>())
        jdbc.update("UPDATE menu_role_parameters SET current_version_id = 'MPV-SEED-APPROVAL-MATRIX' WHERE parameter_key = 'approvalRoleMatrix'", emptyMap<String, Any?>())
        jdbc.update("UPDATE menu_role_parameters SET current_version_id = 'MPV-SEED-REASON-SCREENS' WHERE parameter_key = 'reasonRequiredScreens'", emptyMap<String, Any?>())
    }

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $tableExpression", emptyMap<String, Any?>(), Int::class.java) ?: 0

    private fun unbalancedTransactionCount(): Int =
        jdbc.queryForObject(
            """
            SELECT count(*)
            FROM (
              SELECT ledger_transaction_id,
                     currency,
                     SUM(CASE WHEN direction = 'DEBIT' THEN -amount_minor ELSE amount_minor END) AS signed_total
              FROM ledger_postings
              GROUP BY ledger_transaction_id, currency
            ) totals
            WHERE signed_total <> 0
            """.trimIndent(),
            emptyMap<String, Any?>(),
            Int::class.java
        ) ?: 0

    private fun bearer(subject: String, roles: List<String>, customerId: String? = null): String {
        val payload = linkedMapOf<String, Any?>(
            "iss" to "http://keycloak.local/realms/banking-lab",
            "sub" to subject,
            "roles" to roles,
            "customerId" to customerId,
            "active" to true
        )
        val json = objectMapper.writeValueAsBytes(payload)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json)
        return "Bearer lab.$encoded.sig"
    }

    data class Endpoint(
        val listPath: String,
        val historyPath: String,
        val requestPath: String,
        val role: String,
        val actor: String,
        val parameterKey: String,
        val value: Any
    ) {
        companion object {
            val fds = Endpoint(
                "/api/staff/fds-parameters",
                "/api/staff/fds-parameters/history",
                "/api/staff/fds-parameters/change-requests",
                "FDS_REVIEWER",
                "fds01",
                "highAmountMinor",
                3_000_000
            )
        }
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.locations") {
                "filesystem:${migrationsPath()}"
            }
        }

        private fun migrationsPath(): Path =
            generateSequence(Paths.get("").toAbsolutePath()) { it.parent }
                .map { it.resolve("db/migrations") }
                .first(Files::isDirectory)
    }
}
