# Payment Service Foundation Evidence

Status: partial target-stack progress.

Review date: 2026-06-06.

## Scope

This evidence covers the first synthetic Payment Service slice:

- `services/payment-service` Kotlin/Spring Boot module registration.
- Payment-service Flyway schema for synthetic billers, payment instructions,
  attempts, status history, idempotency, and durable payment Outbox events,
  with service-specific Flyway history that can initialize after core-banking
  has already populated the shared synthetic PostgreSQL schema.
- Payment instruction API and service logic for create, idempotent replay,
  settlement reference recording, and pre-settlement cancellation.
- Direct customer self-cancel remains allowed and audited, while direct staff or
  ops payment cancellation remains denied on the customer cancel endpoint.
- Staff and ops payment cancellation now goes through durable maker-checker
  correction requests with independent checker approval before the payment
  instruction is canceled.
- Event, AsyncAPI, and OpenAPI contracts for payment-to-core-ledger posting
  requests, settlement, autopay execution, and payment instruction cancellation.
- Core-banking bill-payment ledger command and service-to-service API for
  posting successful synthetic payments as balanced `PAYMENT` ledger entries.
- Payment-service outbox dispatcher that locks durable
  `PaymentLedgerPostingRequested` events, calls a core-banking posting port,
  records settlement, and marks retry/dead-letter state without real payment
  network integration.
- Payment-service Kafka outbox publisher that publishes non-ledger payment
  domain events to Redpanda/Kafka after durable persistence while leaving
  `PaymentLedgerPostingRequested` rows on the core-ledger dispatch path.
- Configurable payment domain-event publisher worker with bounded batch polling,
  Micrometer metrics, retry/dead-letter settings, and synthetic-only
  observability logs for durable non-ledger payment events.
- Live Docker Compose smoke coverage for the enabled payment domain-event
  publisher worker against disposable PostgreSQL and Redpanda services.
- Autopay agreement schema, APIs, status history, idempotent pause/resume/cancel
  commands, due execution, and `PaymentAutopayExecutionCreated` event contract.
- Channel contracts for customer bill payment/autopay, staff payment inquiry,
  staff payment cancellation approval, and ops payment outbox dispatch manifests,
  plus TypeScript API client methods for the payment-service OpenAPI operations.
- Customer Web API-backed payment panel that uses the payment-service client for
  idempotent bill-payment creation/replay, instruction read/outbox visibility,
  and customer autopay create/pause/resume/cancel smoke coverage.
- Staff Terminal API-backed PAY-101 payment instruction inquiry that requires a
  business reason and returns a durable `PAU-*` payment access audit id.
- Staff Terminal API-backed PAY-102 payment cancellation approval smoke that
  creates a durable `PCR-*` request, rejects maker self-approval, and cancels the
  instruction only after independent checker approval.
- Ops Console API-backed OPS-404 payment Outbox dispatch panel that calls the
  durable dispatch route and displays published, retry, dead-letter, or
  no-pending-event outcomes.
- Payment-service route-level authorization filter, signed JWKS JWT decoder,
  dev-only simulator token decoder, and route role policies for instruction,
  autopay, settlement, due-execution, Outbox dispatch, and staff cancellation
  maker-checker APIs.
- Live Keycloak client-credentials smoke coverage proving the imported
  `payment-service-api` service account receives a signed `PAYMENT_SERVICE`
  token with `payment-service-api` and `core-banking-api` audiences, and that
  payment-service accepts it on the dispatch route with simulator fallback
  disabled.
- Configurable payment Outbox worker runner that can drain durable
  `PaymentLedgerPostingRequested` events in bounded batches after commit.
- Live Docker Compose smoke coverage for `payment-service` plus
  `payment-outbox-worker` against live `core-banking`, proving an API-created
  payment can be settled as balanced core ledger postings through the worker.
- Docker Compose platform services for the payment REST API, the enabled
  payment ledger outbox worker, and the enabled payment domain-event publisher,
  all using `payment_flyway_schema_history`, the service-specific
  `payment-service-api` audience, shared-schema Flyway baseline version `0`,
  and explicit API-vs-worker publisher modes.
- Raw Kubernetes and Helm manifests for the payment REST API, ledger outbox
  worker, and domain-event publisher, using readiness/liveness probes,
  `payment_flyway_schema_history`, the synthetic core-banking service-token
  placeholder, Redpanda publisher configuration, and explicit
  `BANKING_LAB_PAYMENT_OUTBOX_WORKER_ENABLED` /
  `BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_ENABLED` mode splits.

The slice does not claim full Payment Service completion. The live Keycloak
service-token smoke proves route authorization for payment-service dispatch,
but the scheduled worker still uses an injected service token rather than
fetching client credentials itself.

## Commands Run

```bash
npm run test:payment-service:unit
npm run test:payment-service:integration
npm run test:payment-service:integration -- --tests lab.banking.payment.PaymentAuthorizationIntegrationTest --rerun-tasks
npm run test:payment-service:integration -- --tests lab.banking.payment.PaymentKafkaOutboxPublisherIntegrationTest --rerun-tasks
npm run test:payment-service:integration -- --tests lab.banking.payment.LivePaymentDomainEventPublisherComposeSmokeIntegrationTest --rerun-tasks
npm run test:payment-service:domain-publisher-compose
npm run test:payment-service:integration -- --tests lab.banking.payment.LivePaymentOutboxWorkerComposeSmokeIntegrationTest --rerun-tasks
npm run test:payment-service:outbox-worker-compose
npm run test:payment-service:keycloak-service-token
npm run test:payment-service:integration -- --rerun-tasks
npm run test:core-banking:integration -- --tests lab.banking.core.ledger.application.LedgerCommandServiceIntegrationTest --tests lab.banking.core.ledger.api.LedgerRuntimeApiParityIntegrationTest --rerun-tasks
npm run test:core-banking:unit -- --rerun-tasks
npm test
npm run node:retirement-gate
npm run evidence:refresh-check
npm run scripts:typecheck
npm run validate:manifests
npm run packages:typecheck
npm run next:customer-web:typecheck
npm run next:staff-terminal:typecheck
npm run next:ops-console:typecheck
npm run test:e2e -- apps/customer-web/e2e/customer-web-parity.spec.ts
npm run test:e2e -- apps/staff-terminal/e2e/staff-terminal-parity.spec.ts
npm run test:e2e -- apps/ops-console/e2e/ops-console-parity.spec.ts
docker compose --profile platform config
npm run k8s:validate
npm run helm:template
npm run security:posture-check
node --test tests/springScaffold.test.mjs
node --test tests/springScaffold.test.mjs tests/reportingServiceScaffold.test.mjs
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

- `npm run test:payment-service:unit`: pass after sandbox escalation;
  `PaymentDomainEventPublisherWorkerTest` compiled and verified publisher
  configuration delegation, bounded batch size, metrics counters, and disabled
  worker start behavior.
- `npm run test:payment-service:integration`: pass; PostgreSQL Testcontainers
  ran `PaymentInstructionIntegrationTest`, `PaymentAutopayIntegrationTest`, and
  `PaymentOutboxDispatcherIntegrationTest`, and
  `PaymentOutboxWorkerIntegrationTest`, `PaymentKafkaOutboxPublisherIntegrationTest`,
  and `PaymentAuthorizationIntegrationTest`.
- `npm run test:payment-service:integration -- --tests lab.banking.payment.PaymentAuthorizationIntegrationTest --rerun-tasks`:
  first sandboxed Gradle run failed with `java.net.SocketException: Operation
  not permitted`; rerun after sandbox escalation passed and proved the staff
  cancellation maker-checker route policy and separation controls.
- `npm run test:payment-service:integration -- --tests lab.banking.payment.PaymentKafkaOutboxPublisherIntegrationTest --rerun-tasks`:
  pass after sandbox escalation; PostgreSQL and Redpanda Testcontainers proved
  payment-domain event publication without publishing ledger command events.
- `npm run test:payment-service:integration -- --tests lab.banking.payment.LivePaymentDomainEventPublisherComposeSmokeIntegrationTest --rerun-tasks`:
  pass after sandbox escalation; the live smoke test compiled and skipped the
  runtime path because `BANKING_LAB_LIVE_PAYMENT_DOMAIN_PUBLISHER_COMPOSE_PROJECT`
  was not set for the compile/skip check.
- `npm run test:payment-service:domain-publisher-compose`: pass after sandbox
  escalation; the wrapper built the payment-service boot jar, started
  disposable PostgreSQL, Redpanda, and `payment-domain-event-publisher` Compose
  services, and ran the gated live smoke.
- `npm run test:payment-service:integration -- --tests lab.banking.payment.LivePaymentOutboxWorkerComposeSmokeIntegrationTest --rerun-tasks`:
  pass after sandbox escalation; the live worker smoke test compiled and
  skipped the runtime path because `BANKING_LAB_LIVE_PAYMENT_OUTBOX_WORKER_COMPOSE_PROJECT`
  was not set for the compile/skip check.
- `npm run test:payment-service:outbox-worker-compose`: pass after sandbox
  escalation; the wrapper built core-banking and payment-service boot jars,
  started disposable PostgreSQL, `core-banking`, `payment-service`, and
  `payment-outbox-worker` Compose services, and ran the gated live worker smoke.
  The first live attempts failed because payment-service Flyway refused to
  initialize `payment_flyway_schema_history` after core-banking had populated
  the shared `public` schema; the fix sets service Flyway baseline-on-migrate at
  version `0`, allowing payment V001 and later migrations to run in the shared
  synthetic schema.
- `npm run test:payment-service:keycloak-service-token`: pass after sandbox
  escalation; the wrapper built core-banking and payment-service boot jars,
  started disposable PostgreSQL, Keycloak, `core-banking`, and `payment-service`
  with simulator tokens disabled, obtained a live Keycloak client-credentials
  token for `payment-service-api`, asserted the `PAYMENT_SERVICE` role plus
  `payment-service-api` and `core-banking-api` audiences, and called the payment
  dispatch route successfully with `NO_PENDING_EVENT`.
- `npm run test:payment-service:integration -- --rerun-tasks`: pass after
  sandbox escalation; the full payment-service integration suite passed with
  the cancellation approval migration and payment Kafka publisher included.
- `npm run test:core-banking:integration -- --tests ...LedgerCommandServiceIntegrationTest --tests ...LedgerRuntimeApiParityIntegrationTest --rerun-tasks`:
  pass; PostgreSQL Testcontainers verified bill-payment settlement postings,
  idempotent replay, structured API access, and service-role denial.
- `npm run test:core-banking:unit -- --rerun-tasks`: pass; Kotlin unit suite
  compiled and ran after sandbox escalation.
- `npm test`: pass; 165 Node oracle and evidence tests passed.
- `npm run node:retirement-gate`: pass; gate remains ready from existing
  verified passkey/final-review evidence.
- `npm run evidence:refresh-check`: pass.
- `npm run scripts:typecheck`: pass.
- `npm run validate:manifests`: pass; 104 screen manifests validated,
  including `CWB-701`, `CWB-702`, `CWB-703`, `PAY-101`, `PAY-102`, and
  `OPS-404`.
- `npm run packages:typecheck`: pass; screen/form/api/auth clients compiled,
  including the new payment API client contract.
- `npm run next:customer-web:typecheck`: pass; Customer Web compiled with the
  payment-service panel and environment variable fallback.
- `npm run next:staff-terminal:typecheck`: pass; Staff Terminal compiled with
  the PAY-101 reason-required payment inquiry panel and PAY-102 cancellation
  approval smoke panel.
- `npm run next:ops-console:typecheck`: pass; Ops Console compiled with the
  OPS-404 payment Outbox dispatch panel.
- `npm run test:e2e -- apps/customer-web/e2e/customer-web-parity.spec.ts`: pass;
  local shell and API-gated customer E2E coverage ran, with payment-service
  smoke skipped unless `BANKING_LAB_E2E_PAYMENT_API_BASE_URL` is configured.
- `npm run test:e2e -- apps/staff-terminal/e2e/staff-terminal-parity.spec.ts`:
  pass; 5 local shell tests passed and 18 API/Keycloak/payment-service smokes
  were skipped because E2E base URLs were not configured, including PAY-101 and
  PAY-102 unless `BANKING_LAB_E2E_PAYMENT_API_BASE_URL` is set.
- `npm run test:e2e -- apps/ops-console/e2e/ops-console-parity.spec.ts`: pass;
  local shell and API-gated ops E2E coverage ran, with OPS-404 payment-service
  smoke skipped unless `BANKING_LAB_E2E_PAYMENT_API_BASE_URL` is configured.
- `docker compose --profile platform config`: pass; platform profile renders
  `payment-service`, `payment-outbox-worker`, and
  `payment-domain-event-publisher` with `payment_flyway_schema_history`,
  `payment-service-api`, Redpanda publisher settings, core-banking service URL,
  and explicit ledger-worker/domain-publisher mode splits.
- `npm run k8s:validate`: pass; structural validation covers
  `payment-service`, `payment-outbox-worker`, and
  `payment-domain-event-publisher` deployments with readiness/liveness probes,
  service-specific Flyway history, synthetic-only core-banking service-token
  placeholder, Redpanda bootstrap configuration, and the expected worker mode
  splits.
- `npm run helm:template`: pass; Helm renders the payment REST API
  Deployment/Service, outbox-worker Deployment, and domain-event publisher
  Deployment with the same audience, Flyway, token-reference, Redpanda
  publisher, and worker-mode controls.
- `npm run security:posture-check`: pass; static posture checks include the
  payment application synthetic-only payment network default and raw/Helm
  payment API, outbox-worker, and domain-event publisher deployment controls.
- `node --test tests/springScaffold.test.mjs`: pass; 6 static Spring scaffold
  checks passed, including payment-service `PaymentInstructionCanceled` AsyncAPI
  contract coverage.
- `node --test tests/springScaffold.test.mjs tests/reportingServiceScaffold.test.mjs`:
  pass; 8 static scaffold checks passed, including payment, notification, and
  reporting service shared-schema Flyway baseline controls.

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
- `PAY-102` declares a high-risk staff payment cancellation command with
  maker-checker approval, reason-required audit, approval/rejection actions, and
  payment cancellation event metadata;
- `OPS-404` declares ops payment Outbox dispatch with retry/dead-letter result
  metadata;
- `@banking-lab/api-client` exposes typed payment instruction, settlement,
  outbox dispatch, staff cancellation maker-checker, and autopay methods
  matching the payment-service OpenAPI operation set.
- `contracts/asyncapi/banking-lab-events.yaml` now declares
  `payment.instruction.canceled` with
  `contracts/events/payment-instruction-canceled.schema.json`, matching the
  durable `PaymentInstructionCanceled` outbox rows emitted by customer and
  checker-approved staff cancellation paths.
- Customer Web renders `data-testid="api-backed-customer-payment-domain"` and
  uses `NEXT_PUBLIC_BANKING_PAYMENT_API_BASE_URL` with the standard
  `NEXT_PUBLIC_BANKING_API_BASE_URL` fallback to exercise CWB-701/CWB-702/CWB-703
  through the typed API client when a payment-service runtime is configured.
- Staff Terminal renders `data-testid="api-backed-staff-payment-inquiry"` and
  uses the same payment-service URL convention to exercise PAY-101 lookup with a
  business reason, `PAYMENT_INSTRUCTION_VIEW` audit, and `PAU-*` audit id.
- Staff Terminal renders `data-testid="api-backed-staff-payment-cancellation"`
  and uses the same payment-service URL convention to exercise PAY-102
  cancellation maker-checker approval, including self-approval rejection and an
  independent checker-canceled instruction.
- Ops Console renders `data-testid="api-backed-payment-outbox-dispatch"` and
  uses the same payment-service URL convention to exercise OPS-404 durable
  Outbox dispatch with an operator reason and retry/dead-letter status display.

`PaymentAuthorizationIntegrationTest` verifies:

- missing bearer tokens are rejected with
  `PAYMENT_AUTHORIZATION_POLICY_VIOLATION`;
- branch staff cannot create customer payment instructions, while `CUSTOMER`
  tokens can create and staff roles can read payment instructions;
- staff payment instruction reads without a business reason are rejected with
  `PAYMENT_LOOKUP_REASON_REQUIRED`;
- reasoned staff payment instruction reads write `payment_access_audit_events`
  rows and return `PAU-*` `auditEventId` values for PAY-101 evidence;
- branch staff direct payment cancellation is rejected with
  `PAYMENT_AUTHORIZATION_POLICY_VIOLATION`, while customer self-cancel remains
  allowed for pre-settlement instructions;
- staff/ops cancellation correction creation is forbidden to CUSTOMER tokens and
  allowed to staff/ops maker roles only;
- maker cancellation requests persist durable `PCR-*` pending rows without
  mutating the payment instruction;
- idempotent maker request replay returns the same `PCR-*` request without a
  duplicate pending row;
- self-approval is rejected with `PAYMENT_MAKER_CHECKER_SEPARATION_REQUIRED`
  before instruction mutation;
- an independent manager approval marks the request `APPROVED`, cancels the
  pre-settlement instruction, appends `CANCELED` status history with the checker
  actor, and emits one `PaymentInstructionCanceled` durable Outbox event;
- idempotent checker approval replay returns the same approved result without a
  duplicate cancellation event;
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

`LivePaymentOutboxWorkerComposeSmokeIntegrationTest` verifies:

- `core-banking`, `payment-service`, and `payment-outbox-worker` can start in
  one shared PostgreSQL Compose project after service-specific Flyway history is
  initialized at baseline version `0`;
- a synthetic customer/account shell is funded through live core-banking
  `POST /api/ledger/deposits`, preserving posting-derived balances rather than
  direct balance mutation;
- a live payment-service `POST /api/payments/instructions` call creates a
  `POSTING_REQUESTED` instruction and a pending
  `PaymentLedgerPostingRequested` outbox row;
- after the live `payment-outbox-worker` is restarted, the worker calls the
  core-banking payment-posting API with a dev-only simulator
  `PAYMENT_SERVICE` token, marks the payment outbox row `PUBLISHED`, records
  the payment as `SETTLED`, and stores the returned `TX-*` id;
- core-banking persists a `BILL_PAYMENT` ledger transaction with two balanced
  `PAYMENT` postings, debiting the synthetic customer account and crediting
  `BANK-SETTLEMENT`;
- account projections reflect the postings, and core-banking writes one
  durable `PaymentLedgerPostingSettled` outbox event.

`PaymentKafkaOutboxPublisherIntegrationTest` verifies:

- `PaymentKafkaOutboxPublisher` publishes durable non-ledger payment domain
  events to Redpanda with an idempotent producer and synthetic-only headers;
- `PaymentInstructionCanceled` is published as a Kafka envelope containing the
  persisted outbox id, aggregate id, idempotency key, payload, and
  `sourceService=payment-service`;
- `PaymentLedgerPostingRequested` remains `PENDING` and is not published by the
  domain-event publisher, preserving the existing core-banking ledger dispatch
  boundary;
- successful broker acknowledgement marks only the published payment domain
  event `PUBLISHED`.

`LivePaymentDomainEventPublisherComposeSmokeIntegrationTest` verifies:

- the live Compose `payment-domain-event-publisher` process can be killed,
  restarted, and used to drain a durable pending `PaymentInstructionCanceled`
  outbox row from the shared synthetic PostgreSQL database;
- the publisher emits the payment-domain event to Redpanda with a
  `PaymentOutboxKafkaEnvelope`, `syntheticOnly` header, `sourceService=payment-service`,
  and `directLedgerWrite=false` payload;
- a colocated pending `PaymentLedgerPostingRequested` row remains `PENDING`,
  preserving the separate core-ledger dispatch boundary;
- the worker writes a synthetic-only batch observability log with attempted,
  published, failed, and dead-letter counters for the configured topic.

`PaymentDomainEventPublisherWorkerTest` verifies:

- worker configuration maps the configured Redpanda bootstrap servers, topic,
  client id, publish timeout, retry delay, dead-letter threshold, event type
  allow-list, and bounded batch size into the publisher port;
- Micrometer counters record attempted, published, failed, and batch counts;
- disabled worker configuration is inert and does not start the lifecycle.

## Synthetic Boundary

The migration seeds only `SYN-BILLER-*` billers with
`network_kind='SYNTHETIC_BILLER_SIMULATOR'` and `synthetic_only=true`.
No real biller, payment network, payment provider, customer PII, financial
institution API, or real money path is configured.
The application keeps `real-payment-network-enabled: false`; Compose,
Kubernetes, and Helm add only synthetic payment-service API/worker/runtime
settings, Redpanda publisher settings, and a replaceable local synthetic
service-token placeholder for the core-banking posting bridge.
The live payment outbox worker smoke uses dev-only simulator tokens with
security enabled. The separate live Keycloak service-token smoke uses a signed
Keycloak client-credentials token with simulator fallback disabled, but it does
not yet make the scheduled worker fetch that token by itself.
Staff cancellation approval creates only synthetic `payment_cancellation_requests`
rows and a durable `PaymentInstructionCanceled` Outbox event after independent
checker approval; it never writes core ledger tables directly.

## Remaining Risk

This is still a partial slice. A successful bill payment can now be created
from Customer Web, queried from Staff Terminal with reason-required audit,
dispatched from Ops Console through durable payment-service outbox state to a
core-banking posting port, settled idempotently, and created from durable
autopay schedules. Staff payment cancellation now has an API-backed
maker-checker correction path, PAY-102 staff-terminal smoke panel, and
live Compose payment domain-event publisher and ledger outbox worker smoke
evidence. Live payment-service Keycloak service-token route smoke evidence now
passes for dispatch authorization.
The new Compose/Kubernetes/Helm surface is structurally validated, and the
domain-event publisher plus ledger outbox worker now have live Compose proof.
The remaining service-token gap is scheduled worker token acquisition/rotation,
not route acceptance of a Keycloak-issued `PAYMENT_SERVICE` token.
