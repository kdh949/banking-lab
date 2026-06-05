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
- Channel contracts for customer bill payment/autopay, staff payment inquiry,
  and ops payment outbox dispatch manifests, plus TypeScript API client methods
  for the payment-service OpenAPI operations.
- Customer Web API-backed payment panel that uses the payment-service client for
  idempotent bill-payment creation/replay, instruction read/outbox visibility,
  and customer autopay create/pause/resume/cancel smoke coverage.
- Payment-service route-level authorization filter, signed JWKS JWT decoder,
  dev-only simulator token decoder, and route role policies for instruction,
  autopay, settlement, due-execution, and Outbox dispatch APIs.
- Configurable payment Outbox worker runner that can drain durable
  `PaymentLedgerPostingRequested` events in bounded batches after commit.

The slice does not claim full Payment Service completion. Runtime publication to
Kafka/Redpanda, staff/ops dedicated payment panels, and staff payment correction
maker-checker flows remain future work.

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
npm run validate:manifests
npm run packages:typecheck
npm run next:customer-web:typecheck
npm run test:e2e -- apps/customer-web/e2e/customer-web-parity.spec.ts
```

The Gradle-backed commands were first attempted inside the managed sandbox and
failed before Gradle startup with:

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
  `PaymentOutboxDispatcherIntegrationTest`, and
  `PaymentOutboxWorkerIntegrationTest`, and `PaymentAuthorizationIntegrationTest`.
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
- `npm run validate:manifests`: pass; 95 manifests validated, including
  `CWB-701`, `CWB-702`, `CWB-703`, `PAY-101`, and `OPS-404`.
- `npm run packages:typecheck`: pass; screen/form/api/auth clients compiled,
  including the new payment API client contract.
- `npm run next:customer-web:typecheck`: pass; Customer Web compiled with the
  payment-service panel and environment variable fallback.
- `npm run test:e2e -- apps/customer-web/e2e/customer-web-parity.spec.ts`: pass;
  local shell and API-gated customer E2E coverage ran, with payment-service
  smoke skipped unless `BANKING_LAB_E2E_PAYMENT_API_BASE_URL` is configured.

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

Manifest and API client coverage verifies:

- `CWB-701` declares customer bill payment command fields, reason-required
  self-service audit, and durable payment Outbox status follow-up metadata;
- `CWB-702` and `CWB-703` declare autopay agreement creation and
  pause/resume/cancel management through reusable command templates;
- `PAY-101` declares reason-required staff payment instruction lookup with
  account masking policy and `PAYMENT_INSTRUCTION_VIEW` audit metadata;
- `OPS-404` declares ops payment Outbox dispatch with retry/dead-letter result
  metadata;
- `@banking-lab/api-client` exposes typed payment instruction, settlement,
  outbox dispatch, and autopay methods matching the payment-service OpenAPI
  operation set.
- Customer Web renders `data-testid="api-backed-customer-payment-domain"` and
  uses `NEXT_PUBLIC_BANKING_PAYMENT_API_BASE_URL` with the standard
  `NEXT_PUBLIC_BANKING_API_BASE_URL` fallback to exercise CWB-701/CWB-702/CWB-703
  through the typed API client when a payment-service runtime is configured.

`PaymentAuthorizationIntegrationTest` verifies:

- missing bearer tokens are rejected with
  `PAYMENT_AUTHORIZATION_POLICY_VIOLATION`;
- branch staff cannot create customer payment instructions, while `CUSTOMER`
  tokens can create and staff roles can read payment instructions;
- customer tokens cannot record settlement callbacks or run operational
  dispatch/due-execution APIs;
- `PAYMENT_SERVICE` tokens can record payment settlement, execute due autopay,
  and dispatch the durable payment Outbox route;
- customer autopay pause/resume/cancel paths stay customer-role scoped;
- authorization tests use only dev-enabled simulator tokens, while the runtime
  also supports signed JWKS JWT validation through `banking-lab.security.jwt.*`.

`PaymentOutboxWorkerIntegrationTest` verifies:

- the worker is property-gated through
  `banking-lab.payment-service.outbox-worker.enabled`;
- worker dispatch uses configured actor, reason, dead-letter threshold, and
  maximum batch size;
- a batch of three durable ledger posting events is drained as two postings on
  the first run and one posting on the second run when `max-batch-size=2`;
- each worker-dispatched event settles the payment instruction and marks the
  source durable outbox row `PUBLISHED`;
- the third empty run returns `noPendingEvent=true` without external payment
  network integration.

## Synthetic Boundary

The migration seeds only `SYN-BILLER-*` billers with
`network_kind='SYNTHETIC_BILLER_SIMULATOR'` and `synthetic_only=true`.
No real biller, payment network, payment provider, customer PII, financial
institution API, or real money path is configured.

## Remaining Risk

This is still a partial slice. A successful bill payment can now be created
from Customer Web, dispatched from durable payment-service outbox state to a
core-banking posting port, settled idempotently, and created from durable
autopay schedules, but Kafka/Redpanda runtime publication, staff/ops dedicated
payment panels, live payment-service Keycloak realm smoke evidence, and staff
correction maker-checker flows are still pending.
