#!/usr/bin/env python3
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path.cwd()


def read(path: str | Path) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str | Path, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def replace_exact(path: str | Path, old: str, new: str, expected: int = 1) -> None:
    content = read(path)
    count = content.count(old)
    if count != expected:
        raise RuntimeError(f"{path}: expected {expected} occurrence(s) of {old!r}, found {count}")
    write(path, content.replace(old, new))


def replace_regex(path: str | Path, pattern: str, replacement: str, expected: int = 1) -> None:
    content = read(path)
    updated, count = re.subn(pattern, replacement, content, flags=re.MULTILINE | re.DOTALL)
    if count != expected:
        raise RuntimeError(f"{path}: expected {expected} regex replacement(s), found {count}: {pattern}")
    write(path, updated)


def replace_after(path: str | Path, marker: str, old: str, new: str) -> None:
    content = read(path)
    marker_index = content.find(marker)
    if marker_index < 0:
        raise RuntimeError(f"{path}: marker not found: {marker}")
    tail = content[marker_index:]
    if old not in tail:
        raise RuntimeError(f"{path}: replacement target not found after marker: {old}")
    tail = tail.replace(old, new, 1)
    write(path, content[:marker_index] + tail)


# 1. Kotlin payment domain terminology and state.
kotlin_roots = [
    ROOT / "services/payment-service/src/main/kotlin",
    ROOT / "services/payment-service/src/test/kotlin",
    ROOT / "services/payment-service/src/integrationTest/kotlin",
]
kotlin_files = sorted(path for root in kotlin_roots if root.exists() for path in root.rglob("*.kt"))
for path in kotlin_files:
    content = path.read_text(encoding="utf-8")
    for old, new in [
        ("RecordPaymentSettlementRequest", "RecordPaymentLedgerPostingRequest"),
        ("recordSettlement", "recordLedgerPosting"),
        ("updateSettlement", "updateLedgerPosting"),
        ("markLatestAttemptSettled", "markLatestAttemptLedgerPosted"),
        ("PaymentInstructionStatus.SETTLED", "PaymentInstructionStatus.LEDGER_POSTED"),
        ("PaymentInstructionSettled", "PaymentInstructionLedgerPosted"),
        ("RECORD_PAYMENT_SETTLEMENT", "RECORD_PAYMENT_LEDGER_POSTING"),
        ("settlementIdempotencyKey", "ledgerPostingResultIdempotencyKey"),
        ("PAY-SETTLEMENT-", "PAY-LEDGER-POSTED-"),
        ("waitForPaymentSettlement", "waitForPaymentLedgerPosting"),
    ]:
        content = content.replace(old, new)
    content = content.replace('"SETTLED"', '"LEDGER_POSTED"')
    content = content.replace("'SETTLED'", "'LEDGER_POSTED'")
    if "/src/integrationTest/" in path.as_posix():
        content = content.replace("/settlements", "/ledger-postings")
    path.write_text(content, encoding="utf-8")

replace_exact(
    "services/payment-service/src/main/kotlin/lab/banking/payment/domain/PaymentModels.kt",
    "    POSTING_REQUESTED,\n    SETTLED,\n",
    "    POSTING_REQUESTED,\n    LEDGER_POSTED,\n",
)

service_path = "services/payment-service/src/main/kotlin/lab/banking/payment/domain/PaymentInstructionService.kt"
service = read(service_path)
for old, new in [
    ("successful payment settlement must reference a core-banking ledger transaction", "successful payment ledger posting must reference a core-banking ledger transaction"),
    ("Payment Service records settlement only after core-banking posts a ledger transaction.", "Payment Service records the ledger-posted state only after core-banking posts a ledger transaction."),
    ("canceled payment instruction cannot be settled", "canceled payment instruction cannot be ledger-posted"),
    ("settled payment instruction cannot change ledger transaction reference", "ledger-posted payment instruction cannot change ledger transaction reference"),
    ("settled payment instruction cannot be staff-canceled", "ledger-posted payment instruction cannot be staff-canceled"),
    ("settled payment instruction cannot be canceled", "ledger-posted payment instruction cannot be canceled"),
    ("only pre-settlement payment instructions can be submitted for staff cancellation", "only pre-ledger-posting payment instructions can be submitted for staff cancellation"),
]:
    service = service.replace(old, new)
legacy_hash_line = '        val requestHash = requestHash("SETTLE", instructionId, request.ledgerTransactionId, request.requestedBy)'
if legacy_hash_line not in service:
    raise RuntimeError("PaymentInstructionService.kt: legacy request-hash line not found")
service = service.replace(
    legacy_hash_line,
    "        // Keep the legacy discriminator so V005 idempotency hashes remain replayable after V006.\n" + legacy_hash_line,
    1,
)
write(service_path, service)
replace_after(
    service_path,
    'eventType = "PaymentInstructionLedgerPosted"',
    '"contractVersion" to "2026-06-05"',
    '"contractVersion" to "2026-07-31"',
)
replace_after(
    service_path,
    'eventType = "PaymentInstructionLedgerPosted"',
    '"ledgerPostedViaCoreBanking" to true,\n                    "realPaymentNetworkUsed"',
    '"ledgerPostedViaCoreBanking" to true,\n                    "externalSettlementCompleted" to false,\n                    "realPaymentNetworkUsed"',
)

# 2. Canonical API plus deprecated compatibility route.
controller_path = "services/payment-service/src/main/kotlin/lab/banking/payment/api/PaymentController.kt"
old_controller_block = '''    @PostMapping("/instructions/{instructionId}/settlements")
    fun recordLedgerPosting(
        @PathVariable instructionId: String,
        @RequestBody request: RecordPaymentLedgerPostingRequest
    ): PaymentInstructionResponse =
        paymentInstructionService.recordLedgerPosting(instructionId, request)
'''
new_controller_block = '''    @PostMapping("/instructions/{instructionId}/ledger-postings")
    fun recordLedgerPosting(
        @PathVariable instructionId: String,
        @RequestBody request: RecordPaymentLedgerPostingRequest
    ): PaymentInstructionResponse =
        paymentInstructionService.recordLedgerPosting(instructionId, request)

    @Deprecated("Use /api/payments/instructions/{instructionId}/ledger-postings")
    @PostMapping("/instructions/{instructionId}/settlements")
    fun recordSettlementCompatibility(
        @PathVariable instructionId: String,
        @RequestBody request: RecordPaymentLedgerPostingRequest
    ): PaymentInstructionResponse =
        paymentInstructionService.recordLedgerPosting(instructionId, request)
'''
replace_exact(controller_path, old_controller_block, new_controller_block)

filter_path = "services/payment-service/src/main/kotlin/lab/banking/payment/security/PaymentAuthorizationFilter.kt"
replace_exact(
    filter_path,
    '    private val instructionSettlement = Regex("^/api/payments/instructions/[^/]+/settlements$")',
    '    private val instructionLedgerPosting = Regex("^/api/payments/instructions/[^/]+/(ledger-postings|settlements)$")',
)
replace_exact(
    filter_path,
    '            instructionSettlement.matches(path) && method == "POST" -> setOf("PAYMENT_SERVICE", "OPS_OPERATOR")',
    '            instructionLedgerPosting.matches(path) && method == "POST" -> setOf("PAYMENT_SERVICE", "OPS_OPERATOR")',
)

# 3. Active publisher configuration emits the canonical event only.
for config_path in [
    "services/payment-service/src/main/resources/application.yml",
    "docker-compose.yml",
    "infra/k8s/payment-domain-event-publisher-deployment.yaml",
    "infra/helm/banking-lab/values.yaml",
]:
    content = read(config_path)
    if "PaymentInstructionSettled" not in content:
        raise RuntimeError(f"{config_path}: active legacy event marker not found")
    write(config_path, content.replace("PaymentInstructionSettled", "PaymentInstructionLedgerPosted"))

# 4. OpenAPI exposes a canonical route and a deprecated compatibility alias.
openapi_path = "contracts/openapi/payment-service.yaml"
openapi = read(openapi_path)
openapi = openapi.replace(
    "description: Synthetic-only bill payment instruction API. It persists payment state and emits durable ledger command requests instead of writing core ledger tables directly.",
    "description: Synthetic-only bill payment instruction API. It persists payment state and emits durable ledger command requests instead of writing core ledger tables directly. LEDGER_POSTED means the internal core ledger accepted the posting; it does not mean external clearing or settlement completed.",
)
settlement_block_pattern = r"  /api/payments/instructions/\{instructionId\}/settlements:\n.*?(?=  /api/payments/instructions/\{instructionId\}/cancel:)"
ledger_posting_blocks = '''  /api/payments/instructions/{instructionId}/ledger-postings:
    post:
      summary: Record the successful core-banking ledger posting reference
      operationId: recordPaymentLedgerPosting
      parameters:
        - $ref: '#/components/parameters/InstructionId'
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/RecordPaymentLedgerPostingRequest'
      responses:
        '200':
          description: Ledger posting recorded as LEDGER_POSTED; external clearing and settlement are not implied
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/PaymentInstructionResponse'
  /api/payments/instructions/{instructionId}/settlements:
    post:
      deprecated: true
      summary: Compatibility alias for recording the core-banking ledger posting reference
      operationId: recordPaymentSettlement
      parameters:
        - $ref: '#/components/parameters/InstructionId'
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/RecordPaymentLedgerPostingRequest'
      responses:
        '200':
          description: Compatibility response using LEDGER_POSTED semantics
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/PaymentInstructionResponse'
'''
openapi, count = re.subn(settlement_block_pattern, ledger_posting_blocks, openapi, flags=re.MULTILINE | re.DOTALL)
if count != 1:
    raise RuntimeError(f"payment-service OpenAPI settlement block replacement count={count}")
openapi = openapi.replace("RecordPaymentSettlementRequest", "RecordPaymentLedgerPostingRequest")
openapi = openapi.replace("enum: [POSTING_REQUESTED, SETTLED, CANCELED, FAILED]", "enum: [POSTING_REQUESTED, LEDGER_POSTED, CANCELED, FAILED]")
openapi = openapi.replace("summary: Cancel before ledger settlement", "summary: Cancel before ledger posting")
write(openapi_path, openapi)

# 5. Shared TypeScript client keeps the old method while adding the canonical one.
client_path = "packages/api-client/src/index.ts"
client = read(client_path)
interface_pattern = r"export interface RecordPaymentSettlementRequest \{([\s\S]*?)\n\}"
match = re.search(interface_pattern, client)
if not match:
    raise RuntimeError("api-client: RecordPaymentSettlementRequest interface not found")
old_interface = match.group(0)
new_interface = old_interface.replace("RecordPaymentSettlementRequest", "RecordPaymentLedgerPostingRequest", 1)
new_interface += "\n\n/** @deprecated Use RecordPaymentLedgerPostingRequest. */\nexport type RecordPaymentSettlementRequest = RecordPaymentLedgerPostingRequest;"
client = client.replace(old_interface, new_interface, 1)
old_method_marker = "    recordPaymentSettlement(instructionId: string, command: RecordPaymentSettlementRequest) {"
method_start = client.find(old_method_marker)
if method_start < 0:
    raise RuntimeError("api-client: recordPaymentSettlement method not found")
method_end = client.find("\n    },", method_start)
if method_end < 0:
    raise RuntimeError("api-client: recordPaymentSettlement method end not found")
method_end += len("\n    },")
old_method = client[method_start:method_end]
canonical_method = old_method.replace("recordPaymentSettlement", "recordPaymentLedgerPosting", 1)
canonical_method = canonical_method.replace("RecordPaymentSettlementRequest", "RecordPaymentLedgerPostingRequest", 1)
canonical_method = canonical_method.replace("/settlements", "/ledger-postings", 1)
client = client[:method_start] + canonical_method + "\n\n    /** @deprecated Use recordPaymentLedgerPosting. */\n" + client[method_start:]
write(client_path, client)

# 6. AsyncAPI adds the canonical event while retaining the historical schema.
asyncapi_path = "contracts/asyncapi/banking-lab-events.yaml"
asyncapi = read(asyncapi_path)
channel_marker = "  payment.instruction.settled:\n"
if channel_marker not in asyncapi:
    raise RuntimeError("AsyncAPI legacy payment instruction channel not found")
canonical_channel = '''  payment.instruction.ledger-posted:
    address: payment.instruction.ledger-posted
    messages:
      PaymentInstructionLedgerPosted:
        payload:
          $ref: ../events/payment-instruction-ledger-posted.schema.json
'''
asyncapi = asyncapi.replace(channel_marker, canonical_channel + channel_marker, 1)
operation_marker = "  publishPaymentInstructionSettled:\n"
if operation_marker not in asyncapi:
    raise RuntimeError("AsyncAPI legacy payment instruction operation not found")
canonical_operation = '''  publishPaymentInstructionLedgerPosted:
    action: send
    channel:
      $ref: '#/channels/payment.instruction.ledger-posted'
'''
asyncapi = asyncapi.replace(operation_marker, canonical_operation + operation_marker, 1)
write(asyncapi_path, asyncapi)

ledger_posted_schema = {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "$id": "https://banking-lab.synthetic/contracts/events/payment-instruction-ledger-posted.schema.json",
    "title": "PaymentInstructionLedgerPosted",
    "description": "The internal core-banking ledger posting completed. This event does not represent external clearing or settlement completion.",
    "type": "object",
    "additionalProperties": False,
    "required": [
        "contractVersion",
        "paymentInstructionId",
        "ledgerTransactionId",
        "status",
        "syntheticOnly",
        "directLedgerWrite",
        "ledgerPostedViaCoreBanking",
        "externalSettlementCompleted",
        "realPaymentNetworkUsed",
        "realFinancialInstitutionApiUsed",
    ],
    "properties": {
        "contractVersion": {"const": "2026-07-31"},
        "paymentInstructionId": {"type": "string", "pattern": "^PAY-"},
        "ledgerTransactionId": {"type": "string", "pattern": "^TX-"},
        "status": {"const": "LEDGER_POSTED"},
        "syntheticOnly": {"const": True},
        "directLedgerWrite": {"const": False},
        "ledgerPostedViaCoreBanking": {"const": True},
        "externalSettlementCompleted": {"const": False},
        "realPaymentNetworkUsed": {"const": False},
        "realFinancialInstitutionApiUsed": {"const": False},
    },
}
write(
    "contracts/events/payment-instruction-ledger-posted.schema.json",
    json.dumps(ledger_posted_schema, indent=2) + "\n",
)

# 7. V006 converts current state and unpublished events while preserving historical published events.
migration = '''ALTER TABLE payment_instructions
  DROP CONSTRAINT IF EXISTS payment_instructions_status_check;
ALTER TABLE payment_attempts
  DROP CONSTRAINT IF EXISTS payment_attempts_status_check;
ALTER TABLE payment_status_history
  DROP CONSTRAINT IF EXISTS payment_status_history_status_check;

UPDATE payment_instructions
SET status = 'LEDGER_POSTED',
    updated_at = now()
WHERE status = 'SETTLED';

UPDATE payment_attempts
SET status = 'LEDGER_POSTED'
WHERE status = 'SETTLED';

UPDATE payment_status_history
SET status = 'LEDGER_POSTED'
WHERE status = 'SETTLED';

UPDATE payment_idempotency_keys
SET response_json = jsonb_set(
  response_json,
  '{item,status}',
  to_jsonb('LEDGER_POSTED'::text),
  false
)
WHERE response_json #>> '{item,status}' = 'SETTLED';

UPDATE payment_idempotency_keys
SET response_json = jsonb_set(
  response_json,
  '{instruction,status}',
  to_jsonb('LEDGER_POSTED'::text),
  false
)
WHERE response_json #>> '{instruction,status}' = 'SETTLED';

UPDATE payment_idempotency_keys
SET command_type = 'RECORD_PAYMENT_LEDGER_POSTING'
WHERE command_type = 'RECORD_PAYMENT_SETTLEMENT';

UPDATE payment_outbox_events
SET event_type = 'PaymentInstructionLedgerPosted',
    payload_json = jsonb_set(
      jsonb_set(
        jsonb_set(
          payload_json,
          '{contractVersion}',
          to_jsonb('2026-07-31'::text),
          true
        ),
        '{status}',
        to_jsonb('LEDGER_POSTED'::text),
        true
      ),
      '{externalSettlementCompleted}',
      'false'::jsonb,
      true
    )
WHERE event_type = 'PaymentInstructionSettled'
  AND status IN ('PENDING', 'FAILED');

ALTER TABLE payment_instructions
  ADD CONSTRAINT payment_instructions_status_check
  CHECK (status IN ('POSTING_REQUESTED', 'LEDGER_POSTED', 'CANCELED', 'FAILED'));
ALTER TABLE payment_attempts
  ADD CONSTRAINT payment_attempts_status_check
  CHECK (status IN ('POSTING_REQUESTED', 'LEDGER_POSTED', 'CANCELED', 'FAILED'));
ALTER TABLE payment_status_history
  ADD CONSTRAINT payment_status_history_status_check
  CHECK (status IN ('POSTING_REQUESTED', 'LEDGER_POSTED', 'CANCELED', 'FAILED'));

COMMENT ON COLUMN payment_instructions.status IS
  'LEDGER_POSTED means core-banking accepted the internal ledger posting; external clearing or settlement is not represented by this aggregate.';
COMMENT ON COLUMN payment_instructions.ledger_transaction_id IS
  'Synthetic core-banking ledger transaction reference; not an external settlement or payout reference.';
'''
write(
    "services/payment-service/src/main/resources/db/migration/V006__payment_ledger_posted_semantics.sql",
    migration,
)

# 8. Contract and structural tests use the canonical lifecycle event.
spring_test_path = "tests/springScaffold.test.mjs"
spring_test = read(spring_test_path)
old_tuple = '''    [
      "payment.instruction.settled",
      "PaymentInstructionSettled",
      "payment-instruction-settled.schema.json",
      "SETTLED",
      undefined
    ],'''
new_tuple = '''    [
      "payment.instruction.ledger-posted",
      "PaymentInstructionLedgerPosted",
      "payment-instruction-ledger-posted.schema.json",
      "LEDGER_POSTED",
      undefined
    ],'''
if old_tuple not in spring_test:
    raise RuntimeError("springScaffold lifecycle tuple not found")
spring_test = spring_test.replace(old_tuple, new_tuple, 1)
spring_test = spring_test.replace(
    'assert.match(service, /eventType = "PaymentInstructionSettled"/);',
    'assert.match(service, /eventType = "PaymentInstructionLedgerPosted"/);',
    1,
)
spring_test = spring_test.replace(
    '  assert.match(service, /"ledgerPostedViaCoreBanking" to true/);',
    '  assert.match(service, /"ledgerPostedViaCoreBanking" to true/);\n  assert.match(service, /"externalSettlementCompleted" to false/);',
    1,
)
write(spring_test_path, spring_test)

event_test_path = "tests/eventEnvelopeRuntimeValidation.test.mjs"
event_test = read(event_test_path)
event_test = event_test.replace("assert.equal(evidence.eventSchemaCount, 17);", "assert.equal(evidence.eventSchemaCount, 18);")
event_test = event_test.replace("assert.equal(evidence.validatedEnvelopeCount, 17);", "assert.equal(evidence.validatedEnvelopeCount, 18);")
event_test = event_test.replace(
    '    "PaymentInstructionDeadLettered",',
    '    "PaymentInstructionLedgerPosted",\n    "PaymentInstructionDeadLettered",',
    1,
)
write(event_test_path, event_test)

# 9. Migration integration coverage.
migration_test = r'''package lab.banking.payment

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
'''
write(
    "services/payment-service/src/integrationTest/kotlin/lab/banking/payment/PaymentLedgerPostedSemanticsMigrationIntegrationTest.kt",
    migration_test,
)

# 10. Repository-level regression guard.
node_test = r'''import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("payment instruction state distinguishes internal ledger posting from external settlement", async () => {
  const models = await readFile("services/payment-service/src/main/kotlin/lab/banking/payment/domain/PaymentModels.kt", "utf8");
  const service = await readFile("services/payment-service/src/main/kotlin/lab/banking/payment/domain/PaymentInstructionService.kt", "utf8");
  const repository = await readFile("services/payment-service/src/main/kotlin/lab/banking/payment/persistence/PaymentRepository.kt", "utf8");
  const controller = await readFile("services/payment-service/src/main/kotlin/lab/banking/payment/api/PaymentController.kt", "utf8");
  const migration = await readFile("services/payment-service/src/main/resources/db/migration/V006__payment_ledger_posted_semantics.sql", "utf8");

  assert.match(models, /POSTING_REQUESTED,\s+LEDGER_POSTED,/u);
  assert.doesNotMatch(models, /PaymentInstructionStatus[\s\S]*?\bSETTLED\b/u);
  assert.match(service, /fun recordLedgerPosting/u);
  assert.match(service, /PaymentInstructionStatus\.LEDGER_POSTED/u);
  assert.match(service, /eventType = "PaymentInstructionLedgerPosted"/u);
  assert.match(service, /"externalSettlementCompleted" to false/u);
  assert.doesNotMatch(service, /eventType = "PaymentInstructionSettled"/u);
  assert.match(repository, /SET status = 'LEDGER_POSTED'/u);
  assert.match(controller, /\/instructions\/\{instructionId\}\/ledger-postings/u);
  assert.match(controller, /@Deprecated\("Use \/api\/payments\/instructions/u);
  assert.match(controller, /\/instructions\/\{instructionId\}\/settlements/u);
  assert.match(migration, /WHERE status = 'SETTLED'/u);
  assert.match(migration, /event_type = 'PaymentInstructionLedgerPosted'/u);
  assert.match(migration, /status IN \('POSTING_REQUESTED', 'LEDGER_POSTED', 'CANCELED', 'FAILED'\)/u);
});

test("canonical ledger-posted contracts coexist with explicit legacy compatibility contracts", async () => {
  const openapi = await readFile("contracts/openapi/payment-service.yaml", "utf8");
  const asyncapi = await readFile("contracts/asyncapi/banking-lab-events.yaml", "utf8");
  const canonical = JSON.parse(await readFile("contracts/events/payment-instruction-ledger-posted.schema.json", "utf8"));
  const legacy = JSON.parse(await readFile("contracts/events/payment-instruction-settled.schema.json", "utf8"));
  const publisher = await readFile("services/payment-service/src/main/kotlin/lab/banking/payment/eventing/PaymentKafkaModels.kt", "utf8");
  const application = await readFile("services/payment-service/src/main/resources/application.yml", "utf8");

  assert.match(openapi, /\/api\/payments\/instructions\/\{instructionId\}\/ledger-postings:/u);
  assert.match(openapi, /\/api\/payments\/instructions\/\{instructionId\}\/settlements:[\s\S]*?deprecated: true/u);
  assert.match(openapi, /enum: \[POSTING_REQUESTED, LEDGER_POSTED, CANCELED, FAILED\]/u);
  assert.match(asyncapi, /payment\.instruction\.ledger-posted/u);
  assert.match(asyncapi, /payment\.instruction\.settled/u);
  assert.equal(canonical.title, "PaymentInstructionLedgerPosted");
  assert.equal(canonical.properties.status.const, "LEDGER_POSTED");
  assert.equal(canonical.properties.externalSettlementCompleted.const, false);
  assert.equal(legacy.title, "PaymentInstructionSettled");
  assert.equal(legacy.properties.status.const, "SETTLED");
  assert.match(publisher, /PaymentInstructionLedgerPosted/u);
  assert.doesNotMatch(publisher, /PaymentInstructionSettled/u);
  assert.match(application, /PaymentInstructionLedgerPosted/u);
  assert.doesNotMatch(application, /PaymentInstructionSettled/u);
});
'''
write("tests/paymentLedgerPostedSemantics.test.mjs", node_test)

# 11. Portfolio-facing terminology documentation.
architecture_doc = '''# Payment ledger-posted semantics

## Decision

`PaymentInstructionStatus.LEDGER_POSTED` means that core-banking accepted the synthetic bill-payment command and returned a `TX-*` ledger transaction reference. It does **not** mean that an external biller, clearing institution, payment network, or payout account completed settlement.

The payment instruction lifecycle is therefore:

```text
POSTING_REQUESTED -> LEDGER_POSTED
                  -> FAILED
                  -> CANCELED (before ledger posting only)
```

External clearing and settlement will be modeled as a separate aggregate with its own value date, batch, gross/fee/VAT/net position, payout reference, and reconciliation result. The payment instruction must not reuse `LEDGER_POSTED` as evidence of that future process.

## Compatibility boundary

- Canonical callback: `POST /api/payments/instructions/{instructionId}/ledger-postings`.
- Deprecated compatibility callback: `POST /api/payments/instructions/{instructionId}/settlements`.
- Canonical domain event: `PaymentInstructionLedgerPosted` with `status=LEDGER_POSTED` and `externalSettlementCompleted=false`.
- Historical `PaymentInstructionSettled` schema remains checked in for already-published event compatibility, but active publishers no longer select it.
- Flyway V006 converts persisted `SETTLED` instruction/attempt/history rows and unpublished legacy events. Published historical events remain unchanged and continue to validate against the legacy schema.

All paths remain synthetic-only and do not use a real payment network or external financial institution API.
'''
write("docs/architecture/payment-ledger-posted-semantics.md", architecture_doc)

evidence_path = "docs/test-evidence/payment-service.md"
evidence = read(evidence_path)
section = '''## 2026-07-31 Ledger Posting Semantics Hardening

Payment instruction `SETTLED` terminology was replaced with `LEDGER_POSTED` across Kotlin state, PostgreSQL constraints/data migration, OpenAPI, the shared TypeScript client, AsyncAPI, active publisher configuration, and Testcontainers coverage. The canonical route is `/ledger-postings`; `/settlements` remains a deprecated compatibility alias. `PaymentInstructionLedgerPosted` explicitly carries `externalSettlementCompleted=false`, while the historical settled-event schema remains only for already-published compatibility.

This closes a portfolio credibility gap: an internal double-entry ledger posting is now distinguishable from future external clearing, value-date settlement, payout, and three-way reconciliation work.

'''
if "## 2026-07-31 Ledger Posting Semantics Hardening" not in evidence:
    evidence = evidence.replace("## Scope\n", section + "## Scope\n", 1)
for old, new in [
    ("settlement reference recording, and pre-settlement cancellation", "core-ledger posting reference recording, and pre-ledger-posting cancellation"),
    ("requests, settlement, autopay execution", "requests, internal ledger-posted outcomes, autopay execution"),
    ("payment instruction lifecycle events for settled, failed", "payment instruction lifecycle events for ledger-posted, failed"),
    ("records settlement, emits failed", "records the internal ledger posting result, emits failed"),
    ("autopay, settlement, due-execution", "autopay, ledger-posting-result, due-execution"),
    ("API-created payment can be settled as balanced core ledger postings", "API-created payment can be marked `LEDGER_POSTED` after balanced core ledger postings"),
]:
    evidence = evidence.replace(old, new)
write(evidence_path, evidence)

matrix_path = "docs/implementation-coverage-matrix.md"
matrix = read(matrix_path)
matrix = matrix.replace("Review date: 2026-06-10", "Review date: 2026-07-31", 1)
matrix_section = '''## Payment Ledger Posting Semantics

Payment instructions now use `LEDGER_POSTED` only after core-banking returns a synthetic `TX-*` double-entry transaction. This state explicitly excludes external clearing or settlement completion. The canonical `/ledger-postings` callback, V006 migration, `PaymentInstructionLedgerPosted` event contract, deprecated `/settlements` compatibility alias, and migration/integration tests are documented in `docs/architecture/payment-ledger-posted-semantics.md`.

'''
if "## Payment Ledger Posting Semantics" not in matrix:
    matrix = matrix.replace("## Phase 5 Ledger Projection Integrity Workflow\n", matrix_section + "## Phase 5 Ledger Projection Integrity Workflow\n", 1)
write(matrix_path, matrix)

# 12. Final static assertions before executing the real test suites.
models = read("services/payment-service/src/main/kotlin/lab/banking/payment/domain/PaymentModels.kt")
service = read(service_path)
publisher_models = read("services/payment-service/src/main/kotlin/lab/banking/payment/eventing/PaymentKafkaModels.kt")
application = read("services/payment-service/src/main/resources/application.yml")
if "LEDGER_POSTED" not in models or "    SETTLED," in models:
    raise RuntimeError("PaymentInstructionStatus enum was not migrated")
if "RecordPaymentSettlementRequest" in models:
    raise RuntimeError("legacy Kotlin settlement DTO remains")
if 'eventType = "PaymentInstructionSettled"' in service:
    raise RuntimeError("active legacy payment instruction event remains in service")
if "PaymentInstructionSettled" in publisher_models or "PaymentInstructionSettled" in application:
    raise RuntimeError("active publisher still selects legacy payment instruction event")
if "/ledger-postings" not in read(controller_path) or "/settlements" not in read(controller_path):
    raise RuntimeError("canonical or compatibility controller route is missing")

print("Payment ledger-posted semantics implementation applied")
