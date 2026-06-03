package lab.banking.core.staff

import java.nio.file.Paths
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class StaffAccessApiParityIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              masking_access_logs,
              screen_access_logs,
              operator_approvals,
              audit_events,
              idempotency_keys,
              ledger_postings,
              ledger_transactions,
              account_balance_projections,
              account_holds,
              account_limits,
              accounts,
              customer_kyc_profiles,
              customers
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
        jdbc.update(
            """
            INSERT INTO customers (
              customer_id, customer_name, customer_phone, customer_address, customer_grade, risk_grade
            )
            VALUES
              ('BANK', 'Bank Suspense', NULL, NULL, 'INTERNAL', 'LOW'),
              ('SYN-CUS-001', 'Lab Customer Alpha', '010-0000-1001', 'Seoul Synthetic District', 'STANDARD', 'LOW')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO accounts (account_id, customer_id, account_no, currency, status)
            VALUES
              ('BANK-SUSPENSE', 'BANK', 'LAB-000-000000', 'KRW', 'ACTIVE'),
              ('ACC-SYN-001-001', 'SYN-CUS-001', 'LAB-001-000001', 'KRW', 'ACTIVE')
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
        jdbc.update(
            """
            INSERT INTO ledger_transactions (
              ledger_transaction_id, transaction_type, business_reference_id, idempotency_key,
              business_date, status, requested_by, requested_channel, posted_at
            )
            VALUES (
              'TX-OPEN-001', 'SYNTHETIC_OPENING_BALANCE', 'TX-OPEN-001', 'SEED-TX-OPEN-001',
              CURRENT_DATE, 'POSTED', 'SEED', 'SYNTHETIC_DATA_GENERATOR', now()
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
              ('LP-OPEN-001-D', 'TX-OPEN-001', 'BANK-SUSPENSE', 'KRW', 'DEBIT', 100000000, 'OPENING'),
              ('LP-OPEN-001-C', 'TX-OPEN-001', 'ACC-SYN-001-001', 'KRW', 'CREDIT', 100000000, 'OPENING')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

    @Test
    fun `staff customer detail requires reason and returns masked PII with logs`() {
        mockMvc.perform(
            get("/api/staff/customers/SYN-CUS-001/detail")
                .header("x-request-id", "REQ-STAFF-DETAIL-REASON")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("POLICY_REASON_REQUIRED"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-STAFF-DETAIL-REASON"))
        assertEquals(0, countRows("audit_events WHERE event_type = 'CUSTOMER_DETAIL_VIEW'"))
        assertEquals(0, countRows("masking_access_logs WHERE access_level = 'MASKED'"))

        mockMvc.perform(
            get("/api/staff/customers/SYN-CUS-001/detail")
                .queryParam("reason", "Branch service request")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.auditEventId").exists())
            .andExpect(jsonPath("$.item.customerId").value("SYN-CUS-001"))
            .andExpect(jsonPath("$.item.piiExposure").value("MASKED"))
            .andExpect(jsonPath("$.item.name").doesNotExist())
            .andExpect(jsonPath("$.item.maskedPhone").value("010-****-1001"))
            .andExpect(jsonPath("$.item.maskedAddress").value("Seoul Synthetic ***"))

        assertEquals(1, countRows("audit_events WHERE event_type = 'CUSTOMER_DETAIL_VIEW'"))
        assertEquals(1, countRows("masking_access_logs WHERE access_level = 'MASKED'"))
    }

    @Test
    fun `staff unmask requires privileged role and writes unmask access log`() {
        mockMvc.perform(
            post("/api/staff/pii/unmask")
                .header("x-request-id", "REQ-PII-DENIED")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "requestedBy": "branch01",
                      "actorRole": "BRANCH_STAFF",
                      "reason": "Branch service request"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))
            .andExpect(jsonPath("$.error.policy").value("RBAC_ABAC_POLICY_REQUIRED"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-PII-DENIED"))
        assertEquals(0, countRows("audit_events WHERE event_type = 'PII_UNMASK_REQUESTED'"))
        assertEquals(0, countRows("masking_access_logs WHERE access_level = 'UNMASK_APPROVED'"))

        mockMvc.perform(
            post("/api/staff/pii/unmask")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "requestedBy": "manager01",
                      "actorRole": "BRANCH_MANAGER",
                      "reason": "Manager verified customer request"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.auditEventId").exists())
            .andExpect(jsonPath("$.expiresInSeconds").value(300))
            .andExpect(jsonPath("$.item.piiExposure").value("UNMASKED_TIMEBOXED"))
            .andExpect(jsonPath("$.item.phone").value("010-0000-1001"))

        assertEquals(1, countRows("audit_events WHERE event_type = 'PII_UNMASK_REQUESTED'"))
        assertEquals(1, countRows("masking_access_logs WHERE access_level = 'UNMASK_APPROVED'"))
    }

    @Test
    fun `staff account and transaction inquiry require reasons and write audit events`() {
        mockMvc.perform(get("/api/staff/accounts/search").queryParam("customerId", "SYN-CUS-001"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("POLICY_REASON_REQUIRED"))
        assertEquals(0, countRows("audit_events WHERE event_type = 'ACCOUNT_VIEW'"))

        mockMvc.perform(
            get("/api/staff/accounts/search")
                .queryParam("customerId", "SYN-CUS-001")
                .queryParam("reason", "Customer asked balance")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].accountId").value("ACC-SYN-001-001"))
            .andExpect(jsonPath("$.items[0].maskedAccountNo").value("LAB-***-0001"))
            .andExpect(jsonPath("$.items[0].availableBalanceMinor").value(100000000))

        mockMvc.perform(get("/api/staff/transactions/search").queryParam("accountId", "ACC-SYN-001-001"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("POLICY_REASON_REQUIRED"))
        assertEquals(0, countRows("audit_events WHERE event_type = 'TRANSACTION_VIEW'"))

        mockMvc.perform(
            get("/api/staff/transactions/search")
                .queryParam("accountId", "ACC-SYN-001-001")
                .queryParam("reason", "Customer asked history")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].ledgerTransactionId").value("TX-OPEN-001"))
            .andExpect(jsonPath("$.items[0].direction").value("CREDIT"))
            .andExpect(jsonPath("$.items[0].amountMinor").value(100000000))

        assertEquals(1, countRows("audit_events WHERE event_type = 'ACCOUNT_VIEW'"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'TRANSACTION_VIEW'"))
    }

    @Test
    fun `customer information change applies only after maker-checker approval`() {
        val beforePhone = customerPhone()

        mockMvc.perform(
            post("/api/staff/customers/SYN-CUS-001/change-requests")
                .header("x-request-id", "REQ-CUSTOMER-CHANGE-REASON")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requestedBy": "branch01",
                      "afterSnapshot": {
                        "phone": "010-0000-1999",
                        "address": "Seoul Synthetic Updated"
                      }
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("POLICY_REASON_REQUIRED"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-CUSTOMER-CHANGE-REASON"))
        assertEquals(0, countRows("operator_approvals WHERE business_type = 'CUSTOMER_INFO_CHANGE'"))
        assertEquals(0, countRows("audit_events WHERE event_type = 'COMMAND_REQUESTED'"))

        val requestResponse = mockMvc.perform(
            post("/api/staff/customers/SYN-CUS-001/change-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requestedBy": "branch01",
                      "reason": "Customer requested phone update",
                      "afterSnapshot": {
                        "phone": "010-0000-1999",
                        "address": "Seoul Synthetic Updated"
                      }
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.status").value("PENDING"))
            .andExpect(jsonPath("$.customer.piiExposure").value("MASKED"))
            .andReturn()

        val approvalId = objectMapper.readTree(requestResponse.response.contentAsString)
            .path("item")
            .path("approvalId")
            .asText()
        assertEquals(beforePhone, customerPhone())

        mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "approvedBy": "branch01",
                      "approvedByRole": "BRANCH_STAFF",
                      "screenId": "CST-103"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("MAKER_CHECKER_SELF_APPROVAL_REJECTED"))
            .andExpect(jsonPath("$.error.policy").value("MAKER_CHECKER_SEPARATION_OF_DUTIES"))
        assertEquals(beforePhone, customerPhone())
        assertEquals("PENDING", approvalStatus(approvalId))
        assertEquals(0, countRows("audit_events WHERE event_type = 'COMMAND_APPROVED'"))

        mockMvc.perform(
            post("/api/staff/approvals/$approvalId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "approvedBy": "manager01",
                      "approvedByRole": "BRANCH_MANAGER",
                      "screenId": "CST-103"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("APPROVED"))
            .andExpect(jsonPath("$.executed").value(true))
            .andExpect(jsonPath("$.customer.maskedPhone").value("010-****-1999"))

        assertEquals("010-0000-1999", customerPhone())
        assertEquals("Seoul Synthetic Updated", customerAddress())
        assertEquals("APPROVED", approvalStatus(approvalId))
        assertEquals(1, countRows("audit_events WHERE event_type = 'COMMAND_REQUESTED'"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'COMMAND_APPROVED'"))
        assertEquals(1, countRows("audit_events WHERE event_type = 'COMMAND_EXECUTED' AND screen_id = 'CST-103'"))
    }

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM $tableExpression",
            emptyMap<String, Any?>(),
            Int::class.java
        ) ?: 0

    private fun customerPhone(): String? =
        jdbc.queryForObject(
            "SELECT customer_phone FROM customers WHERE customer_id = 'SYN-CUS-001'",
            emptyMap<String, Any?>(),
            String::class.java
        )

    private fun customerAddress(): String? =
        jdbc.queryForObject(
            "SELECT customer_address FROM customers WHERE customer_id = 'SYN-CUS-001'",
            emptyMap<String, Any?>(),
            String::class.java
        )

    private fun approvalStatus(approvalId: String): String? =
        jdbc.queryForObject(
            "SELECT status FROM operator_approvals WHERE approval_id = :approvalId",
            mapOf("approvalId" to approvalId),
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
