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
- Core-banking bill-payment ledger command and service-to-service API for
  posting successful synthetic payments as balanced `PAYMENT` ledger entries.
- Payment-service outbox dispatcher that locks durable
  `PaymentLedgerPostingRequested` events, calls a core-banking posting port,
  records settlement, and marks retry/dead-letter state without real payment
  network integration.
- Autopay agreement schema, APIs, status history, idempotent pause/resume/cancel
  commands, due execution, and `PaymentAutopayExecutionCreated` event contract.

The slice does not claim full Payment Service completion. Runtime publication to
Kafka/Redpanda, a scheduled/background worker runner around the dispatcher,
customer/staff screens, and staff payment correction maker-checker flows remain
future work.

## Commands Run

```bash
npm run test:payment-service:unit
npm run test:payment-service:integration
npm run test:core-banking:integration -- --tests lab.banking.core.ledger.application.LedgerCommandServiceIntegrationTest --tests lab.banking.core.ledger.api.LedgerRuntimeApiParityIntegrationTest --rerun-tasks
npm run test:core-banking:unit -- --rerun-tasks
npm test
npm run node:retirement-gate
npm run evidence:refresh-check
npm run scripts:typecheck
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
  with no unit test sources.
- `npm run test:payment-service:integration`: pass; PostgreSQL Testcontainers
  ran `PaymentInstructionIntegrationTest`, `PaymentAutopayIntegrationTest`, and
  `PaymentOutboxDispatcherIntegrationTest`.
- `npm run test:core-banking:integration -- --tests ...LedgerCommandServiceIntegrationTest --tests ...LedgerRuntimeApiParityIntegrationTest --rerun-tasks`:
  pass; PostgreSQL Testcontainers verified bill-payment settlement postings,
  idempotent replay, structured API access, and service-role denial.
- `npm run test:core-banking:unit -- --rerun-tasks`: pass; Kotlin unit suite
  compiled and ran after sandbox escalation.
- `npm test`: pass; 151 Node oracle and evidence tests passed.
- `npm run node:retirement-gate`: pass; gate remains ready from existing
  verified passkey/final-review evidence.
- `npm run evidence:refresh-check`: pass.
- `npm run scripts:typecheck`: pass.

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

`LedgerCommandServiceIntegrationTest` and `LedgerRuntimeApiParityIntegrationTest`
now verify the core-banking settlement bridge:

- `BillPaymentCommand` debits the synthetic customer account and credits
  `BANK-SETTLEMENT` with two balanced `PAYMENT` postings;
- `BILL_PAYMENT` uses the payment instruction id as the ledger business
  reference and writes a `PaymentLedgerPostingSettled` durable Outbox event;
- duplicate settlement idempotency keys replay the original transaction without
  duplicate postings or Outbox events;
- insufficient funds leave no idempotency row, ledger transaction, or settlement
  movement;
- reversing a bill-payment ledger transaction releases counted
  `PAYMENT_SERVICE` limit usage before a later payment consumes the limit again;
- `POST /api/ledger/payment-postings` is restricted to `PAYMENT_SERVICE` or
  ops operator roles, and customer-role access is rejected.

`PaymentOutboxDispatcherIntegrationTest` verifies:

- the dispatcher reads the next due `PaymentLedgerPostingRequested` outbox row
  with row locking and calls the `CoreLedgerPostingClient` port;
- successful dispatch records `SETTLED` payment state, marks the source outbox
  row `PUBLISHED`, and emits `PaymentInstructionSettled`;
- retry failure persists `FAILED`, increments `retry_count`, preserves a stable
  core ledger idempotency key, and later settles the same outbox event;
- dead-letter threshold failure marks `DEAD_LETTER` without settlement mutation.

`PaymentAutopayIntegrationTest` verifies:

- autopay creation persists `payment_autopay_agreements` and
  `payment_autopay_status_history`, and duplicate idempotency keys replay the
  same agreement;
- pause, resume, and cancel commands are idempotent and append status history;
- canceled agreements cannot be resumed;
- due execution creates a payment instruction, durable
  `PaymentLedgerPostingRequested` outbox event, durable
  `PaymentAutopayExecutionCreated` outbox event, and advances the schedule from
  January 31, 2026 to February 28, 2026;
- paused and canceled agreements are skipped by due execution;
- non-synthetic biller identifiers are rejected before autopay persistence.

## Synthetic Boundary

The migration seeds only `SYN-BILLER-*` billers with
`network_kind='SYNTHETIC_BILLER_SIMULATOR'` and `synthetic_only=true`.
No real biller, payment network, payment provider, customer PII, financial
institution API, or real money path is configured.

## Remaining Risk

This is still a partial slice. A successful bill payment can now be dispatched
from durable payment-service outbox state to a core-banking posting port,
settled idempotently, and created from durable autopay schedules, but
Kafka/Redpanda runtime publication, a scheduled worker runner, UI/API client
coverage, and staff correction maker-checker flows are still pending.
