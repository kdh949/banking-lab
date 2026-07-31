package lab.banking.payment

import java.sql.DriverManager
import java.sql.SQLException
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class PaymentLedgerPostedSemanticsMigrationIntegrationTest {
    @Test
    fun `V006 separates internal ledger posting from external settlement semantics`() {
        migrate(target = "5")
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    INSERT INTO payment_instructions (
                      payment_instruction_id, customer_id, debit_account_id, biller_id, biller_name,
                      amount_minor, currency, status, ledger_transaction_id, synthetic_only
                    ) VALUES
                      ('PAY-LEGACY-SETTLED-001', 'CUS-LEGACY-001', 'ACC-LEGACY-001', 'SYN-BILLER-UTIL-001', 'Synthetic Utility Biller',
                       25000, 'KRW', 'SETTLED', 'TX-LEGACY-001', true),
                      ('PAY-PUBLISHED-SETTLED-001', 'CUS-LEGACY-002', 'ACC-LEGACY-002', 'SYN-BILLER-UTIL-001', 'Synthetic Utility Biller',
                       31000, 'KRW', 'SETTLED', 'TX-LEGACY-002', true)
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    INSERT INTO payment_attempts (
                      payment_attempt_id, payment_instruction_id, attempt_no, status, requested_by, reason
                    ) VALUES
                      ('PAT-LEGACY-001', 'PAY-LEGACY-SETTLED-001', 1, 'SETTLED', 'payment-service', 'Legacy callback'),
                      ('PAT-LEGACY-002', 'PAY-PUBLISHED-SETTLED-001', 1, 'SETTLED', 'payment-service', 'Published legacy callback')
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    INSERT INTO payment_status_history (
                      payment_status_history_id, payment_instruction_id, status, actor_id, reason
                    ) VALUES
                      ('PHS-LEGACY-001', 'PAY-LEGACY-SETTLED-001', 'SETTLED', 'payment-service', 'Legacy callback'),
                      ('PHS-LEGACY-002', 'PAY-PUBLISHED-SETTLED-001', 'SETTLED', 'payment-service', 'Published legacy callback')
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    INSERT INTO payment_idempotency_keys (
                      idempotency_key, command_type, request_hash, aggregate_id, response_json
                    ) VALUES (
                      'PAY-SETTLEMENT-LEGACY-001', 'RECORD_PAYMENT_SETTLEMENT', 'legacy-request-hash',
                      'PAY-LEGACY-SETTLED-001',
                      '{"item":{"paymentInstructionId":"PAY-LEGACY-SETTLED-001","status":"SETTLED","ledgerTransactionId":"TX-LEGACY-001"},"replayed":false}'::jsonb
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    INSERT INTO payment_outbox_events (
                      outbox_event_id, aggregate_type, aggregate_id, event_type, idempotency_key,
                      payload_json, headers_json, status
                    ) VALUES
                      ('POB-LEGACY-PENDING-001', 'payment_instruction', 'PAY-LEGACY-SETTLED-001', 'PaymentInstructionSettled',
                       'PAY-SETTLEMENT-LEGACY-001',
                       '{"contractVersion":"2026-06-05","paymentInstructionId":"PAY-LEGACY-SETTLED-001","ledgerTransactionId":"TX-LEGACY-001","status":"SETTLED","syntheticOnly":true,"directLedgerWrite":false,"ledgerPostedViaCoreBanking":true,"realPaymentNetworkUsed":false,"realFinancialInstitutionApiUsed":false}'::jsonb,
                       '{}'::jsonb, 'PENDING'),
                      ('POB-LEGACY-PUBLISHED-001', 'payment_instruction', 'PAY-PUBLISHED-SETTLED-001', 'PaymentInstructionSettled',
                       'PAY-SETTLEMENT-PUBLISHED-001',
                       '{"contractVersion":"2026-06-05","paymentInstructionId":"PAY-PUBLISHED-SETTLED-001","ledgerTransactionId":"TX-LEGACY-002","status":"SETTLED","syntheticOnly":true,"directLedgerWrite":false,"ledgerPostedViaCoreBanking":true,"realPaymentNetworkUsed":false,"realFinancialInstitutionApiUsed":false}'::jsonb,
                       '{}'::jsonb, 'PUBLISHED')
                    """.trimIndent()
                )
            }
        }

        migrate()

        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                assertEquals("LEDGER_POSTED", singleString(statement, "SELECT status FROM payment_instructions WHERE payment_instruction_id = 'PAY-LEGACY-SETTLED-001'"))
                assertEquals("LEDGER_POSTED", singleString(statement, "SELECT status FROM payment_attempts WHERE payment_attempt_id = 'PAT-LEGACY-001'"))
                assertEquals("LEDGER_POSTED", singleString(statement, "SELECT status FROM payment_status_history WHERE payment_status_history_id = 'PHS-LEGACY-001'"))
                assertEquals("RECORD_PAYMENT_LEDGER_POSTING", singleString(statement, "SELECT command_type FROM payment_idempotency_keys WHERE idempotency_key = 'PAY-SETTLEMENT-LEGACY-001'"))
                assertEquals("LEDGER_POSTED", singleString(statement, "SELECT response_json #>> '{item,status}' FROM payment_idempotency_keys WHERE idempotency_key = 'PAY-SETTLEMENT-LEGACY-001'"))
                assertEquals("PaymentInstructionLedgerPosted", singleString(statement, "SELECT event_type FROM payment_outbox_events WHERE outbox_event_id = 'POB-LEGACY-PENDING-001'"))
                assertEquals("LEDGER_POSTED", singleString(statement, "SELECT payload_json->>'status' FROM payment_outbox_events WHERE outbox_event_id = 'POB-LEGACY-PENDING-001'"))
                assertEquals(false, singleBoolean(statement, "SELECT (payload_json->>'externalSettlementCompleted')::boolean FROM payment_outbox_events WHERE outbox_event_id = 'POB-LEGACY-PENDING-001'"))
                assertEquals("PaymentInstructionSettled", singleString(statement, "SELECT event_type FROM payment_outbox_events WHERE outbox_event_id = 'POB-LEGACY-PUBLISHED-001'"))
                assertEquals("SETTLED", singleString(statement, "SELECT payload_json->>'status' FROM payment_outbox_events WHERE outbox_event_id = 'POB-LEGACY-PUBLISHED-001'"))

                statement.executeUpdate(
                    """
                    INSERT INTO payment_instructions (
                      payment_instruction_id, customer_id, debit_account_id, biller_id, biller_name,
                      amount_minor, currency, status, ledger_transaction_id, synthetic_only
                    ) VALUES (
                      'PAY-LEDGER-POSTED-NEW-001', 'CUS-NEW-001', 'ACC-NEW-001', 'SYN-BILLER-UTIL-001', 'Synthetic Utility Biller',
                      15000, 'KRW', 'LEDGER_POSTED', 'TX-NEW-001', true
                    )
                    """.trimIndent()
                )
                assertThrows(SQLException::class.java) {
                    statement.executeUpdate(
                        """
                        INSERT INTO payment_instructions (
                          payment_instruction_id, customer_id, debit_account_id, biller_id, biller_name,
                          amount_minor, currency, status, ledger_transaction_id, synthetic_only
                        ) VALUES (
                          'PAY-INVALID-SETTLED-001', 'CUS-INVALID-001', 'ACC-INVALID-001', 'SYN-BILLER-UTIL-001', 'Synthetic Utility Biller',
                          15000, 'KRW', 'SETTLED', 'TX-INVALID-001', true
                        )
                        """.trimIndent()
                    )
                }
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
