# Payment Service Foundation Evidence

Status: partial target-stack progress.

Review date: 2026-06-05.

## Scope

This evidence covers the first synthetic Payment Service slice:

- `services/payment-service` Kotlin/Spring Boot module registration.
- Payment-service Flyway schema for synthetic billers, payment instructions,
  attempts, status history, idempotency, and durable payment Outbox events.
- Payment instruction API and service logic for create, idempotent replay,
  settlement reference recording, and pre-settlement cancellation.
- Event and OpenAPI contracts for payment-to-core-ledger posting requests.

The slice does not claim full Payment Service completion. Runtime publication to
Kafka/Redpanda, a core-banking ledger-command consumer, customer/staff screens,
Keycloak route enforcement, autopay, retries/dead-letter workers, and staff
payment correction maker-checker flows remain future work.

## Commands Run

```bash
npm run test:payment-service:unit
npm run test:payment-service:integration
```

Both commands were first attempted inside the managed sandbox and failed before
Gradle startup with:

```text
Could not create service of type FileLockContentionHandler
java.net.SocketException: Operation not permitted
```

The commands were rerun with approved escalation because Gradle/Testcontainers
need local file-lock socket and Docker access.

## Passing Tests

- `npm run test:payment-service:unit`: pass; payment-service Kotlin compiled
  with no test sources.
- `npm run test:payment-service:integration`: pass; PostgreSQL Testcontainers
  ran `PaymentInstructionIntegrationTest`.

## Integration Coverage

`PaymentInstructionIntegrationTest` verifies:

- payment instruction creation persists `payment_instructions`,
  `payment_attempts`, `payment_status_history`, `payment_idempotency_keys`, and
  `payment_outbox_events`;
- `PaymentLedgerPostingRequested` is durable Outbox state with
  `syntheticOnly=true`, `directLedgerWrite=false`, `realPaymentNetworkUsed=false`,
  and `ledgerCommandContract=core-banking.ledger.posting-request.v1`;
- the payment-service schema does not create or write `ledger_transactions`;
- duplicate idempotency keys replay the same business result;
- conflicting payload reuse is rejected with `PAYMENT_IDEMPOTENCY_CONFLICT`;
- unknown real-network biller identifiers are rejected;
- settlement records a `TX-*` core-banking ledger transaction reference;
- settled instructions cannot be canceled;
- pre-settlement cancellation is idempotent and audited through status history.

## Synthetic Boundary

The migration seeds only `SYN-BILLER-*` billers with
`network_kind='SYNTHETIC_BILLER_SIMULATOR'` and `synthetic_only=true`.
No real biller, payment network, payment provider, customer PII, financial
institution API, or real money path is configured.

## Remaining Risk

This is a foundation slice. A successful bill payment is not yet automatically
posted by core-banking because the ledger-command consumer and runtime event
publisher are not implemented in this iteration. UI/API-client coverage and
Keycloak route evidence are also pending.
