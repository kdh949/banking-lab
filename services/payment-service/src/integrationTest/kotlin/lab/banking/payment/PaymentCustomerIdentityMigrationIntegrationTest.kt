package lab.banking.payment

import java.sql.DriverManager
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class PaymentCustomerIdentityMigrationIntegrationTest {
    @Test
    fun `V005 pauses legacy autopay and quarantines unbound ledger work`() {
        migrate(target = "4")
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    INSERT INTO payment_autopay_agreements (
                      autopay_agreement_id, customer_id, debit_account_id, biller_id, biller_name,
                      amount_minor, currency, frequency, status, next_run_on, synthetic_only,
                      created_by, created_reason
                    ) VALUES (
                      'AUTOPAY-LEGACY-001', 'CUS-LEGACY', 'ACC-LEGACY', 'SYN-BILLER-UTIL-001', 'Synthetic Utility Biller',
                      10000, 'KRW', 'MONTHLY', 'ACTIVE', CURRENT_DATE + 1, true,
                      'legacy-customer', 'Legacy synthetic agreement'
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    INSERT INTO payment_idempotency_keys (
                      idempotency_key, command_type, request_hash, aggregate_id, response_json
                    ) VALUES (
                      'AUTOPAY-LEGACY-IDEMPOTENCY', 'CREATE_AUTOPAY_AGREEMENT', 'legacy-autopay-hash', 'AUTOPAY-LEGACY-001',
                      '{"item":{"autopayAgreementId":"AUTOPAY-LEGACY-001","status":"ACTIVE","updatedAt":"2026-01-01T00:00:00Z"},"replayed":false}'::jsonb
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    INSERT INTO payment_instructions (
                      payment_instruction_id, customer_id, debit_account_id, biller_id, biller_name,
                      amount_minor, currency, status, synthetic_only
                    ) VALUES (
                      'PAY-LEGACY-001', 'CUS-FORGED', 'ACC-OTHER-CUSTOMER', 'SYN-BILLER-UTIL-001', 'Synthetic Utility Biller',
                      20000, 'KRW', 'POSTING_REQUESTED', true
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    INSERT INTO payment_attempts (
                      payment_attempt_id, payment_instruction_id, attempt_no, status, requested_by, reason
                    ) VALUES (
                      'ATT-LEGACY-001', 'PAY-LEGACY-001', 1, 'POSTING_REQUESTED', 'forged-actor', 'Legacy unbound request'
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    INSERT INTO payment_status_history (
                      payment_status_history_id, payment_instruction_id, status, actor_id, reason
                    ) VALUES (
                      'PSH-LEGACY-001', 'PAY-LEGACY-001', 'POSTING_REQUESTED', 'forged-actor', 'Legacy unbound request'
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    INSERT INTO payment_idempotency_keys (
                      idempotency_key, command_type, request_hash, aggregate_id, response_json
                    ) VALUES (
                      'PAY-LEGACY-IDEMPOTENCY', 'CREATE_PAYMENT_INSTRUCTION', 'legacy-payment-hash', 'PAY-LEGACY-001',
                      '{"item":{"paymentInstructionId":"PAY-LEGACY-001","status":"POSTING_REQUESTED","updatedAt":"2026-01-01T00:00:00Z"},"replayed":false}'::jsonb
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    INSERT INTO payment_outbox_events (
                      outbox_event_id, aggregate_type, aggregate_id, event_type, idempotency_key,
                      payload_json, headers_json, status
                    ) VALUES (
                      'POE-LEGACY-001', 'PAYMENT_INSTRUCTION', 'PAY-LEGACY-001', 'PaymentLedgerPostingRequested',
                      'PAY-LEGACY-IDEMPOTENCY', '{"customerId":"CUS-FORGED","debitAccountId":"ACC-OTHER-CUSTOMER"}'::jsonb,
                      '{}'::jsonb, 'PENDING'
                    )
                    """.trimIndent()
                )
            }
        }

        migrate()

        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                assertEquals("PAUSED", singleString(statement, "SELECT status FROM payment_autopay_agreements WHERE autopay_agreement_id = 'AUTOPAY-LEGACY-001'"))
                assertEquals(false, singleBoolean(statement, "SELECT customer_identity_bound FROM payment_autopay_agreements WHERE autopay_agreement_id = 'AUTOPAY-LEGACY-001'"))
                assertEquals("PAUSED", singleString(statement, "SELECT response_json->'item'->>'status' FROM payment_idempotency_keys WHERE idempotency_key = 'AUTOPAY-LEGACY-IDEMPOTENCY'"))
                assertEquals("FAILED", singleString(statement, "SELECT status FROM payment_instructions WHERE payment_instruction_id = 'PAY-LEGACY-001'"))
                assertEquals("FAILED", singleString(statement, "SELECT status FROM payment_attempts WHERE payment_attempt_id = 'ATT-LEGACY-001'"))
                assertEquals("DEAD_LETTER", singleString(statement, "SELECT status FROM payment_outbox_events WHERE outbox_event_id = 'POE-LEGACY-001'"))
                assertEquals("FAILED", singleString(statement, "SELECT response_json->'item'->>'status' FROM payment_idempotency_keys WHERE idempotency_key = 'PAY-LEGACY-IDEMPOTENCY'"))

                statement.executeUpdate(
                    """
                    INSERT INTO payment_autopay_agreements (
                      autopay_agreement_id, customer_id, debit_account_id, biller_id, biller_name,
                      amount_minor, currency, frequency, status, next_run_on, synthetic_only,
                      created_by, created_reason
                    ) VALUES (
                      'AUTOPAY-BOUND-001', 'CUS-BOUND', 'ACC-BOUND', 'SYN-BILLER-UTIL-001', 'Synthetic Utility Biller',
                      10000, 'KRW', 'MONTHLY', 'ACTIVE', CURRENT_DATE + 1, true,
                      'customer-bound', 'Post-migration synthetic agreement'
                    )
                    """.trimIndent()
                )
                assertEquals(true, singleBoolean(statement, "SELECT customer_identity_bound FROM payment_autopay_agreements WHERE autopay_agreement_id = 'AUTOPAY-BOUND-001'"))
            }
        }
    }

    private fun migrate(target: String? = null) {
        val configuration = Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")
        if (target != null) {
            configuration.target(MigrationVersion.fromVersion(target))
        }
        configuration.load().migrate()
    }

    private fun singleString(statement: java.sql.Statement, sql: String): String =
        statement.executeQuery(sql).use { result ->
            result.next()
            result.getString(1)
        }

    private fun singleBoolean(statement: java.sql.Statement, sql: String): Boolean =
        statement.executeQuery(sql).use { result ->
            result.next()
            result.getBoolean(1)
        }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }
}
