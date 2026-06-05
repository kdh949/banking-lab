# Keycloak Live Realm Smoke Evidence

Date: 2026-06-06

## Scope

This evidence records the live Keycloak realm import, Spring JWKS authorization smoke, payment-service, notification-service, and reporting-service client-credentials service-token smokes, browser-based customer-web Authorization Code + PKCE propagation, staff-terminal branch/checker Authorization Code + PKCE propagation including privileged unmask, complaint-portal handler/checker Authorization Code + PKCE propagation, ops-console operator/checker Authorization Code + PKCE propagation, audit-console auditor Authorization Code + PKCE propagation, FDS/AML-console risk reviewer/checker Authorization Code + PKCE propagation, staff-terminal WebAuthn required-action completion with a Chromium virtual authenticator, and synthetic passkey policy/recovery segregation for the target stack. It does not mark Node retirement ready.

## Changes Proven

- Docker Compose `platform` profile starts Keycloak on an overrideable host port.
- `infra/keycloak/realm-banking-lab.json` imports the `banking-lab` synthetic realm with staff, complaint handler, ops operator, auditor, risk reviewer, compliance manager, manager, customer, TOTP MFA-required, and WebAuthn-required synthetic users.
- Public channel clients issue direct-grant test tokens for local synthetic smoke only.
- Channel clients add the `core-banking-api` audience to access tokens.
- Payment-facing channel clients (`customer-web`, `staff-terminal`, and
  `ops-console`) also add the `payment-service-api` audience so local
  payment-service smokes can use live Keycloak tokens instead of simulator
  tokens.
- The realm declares a confidential `payment-service-api` client with
  service-account client credentials, a synthetic `PAYMENT_SERVICE` realm role,
  and a `service-account-payment-service-api` principal mapped only to that role.
- The `payment-service-api` service token carries both `payment-service-api` and
  `core-banking-api` audiences so it can authorize payment-service dispatch
  routes and the core-banking posting bridge without simulator fallback.
- Notification-facing channel clients (`customer-web`, `audit-console`, and
  `admin-console`) also add the `notification-service-api` audience for local
  notification API smokes.
- The realm declares a confidential `notification-service-api` client with
  service-account client credentials, a synthetic `NOTIFICATION_SERVICE` realm
  role, and a `service-account-notification-service-api` principal mapped only
  to that role.
- The `notification-service-api` service token carries the
  `notification-service-api` audience so it can authorize notification event
  consumption routes without simulator fallback.
- The realm declares a confidential `reporting-service-api` client with
  service-account client credentials, a synthetic `REPORTING_ANALYST` realm
  role, and a `service-account-reporting-service-api` principal mapped only to
  that role.
- The `reporting-service-api` service token carries the
  `reporting-service-api` audience so it can authorize reporting catalog,
  artifact generation, and artifact list routes without simulator fallback.
- Customer tokens carry `banking_lab_customer_id` so Spring can enforce customer ownership.
- Spring fetches the live Keycloak JWKS and validates signed RS256 access tokens with simulator fallback disabled.
- Staff token access to masked staff customer detail succeeds.
- Customer token access to own account detail succeeds and access to another customer is denied with an authorization audit event.
- A synthetic manager with `CONFIGURE_TOTP` required action is blocked by Keycloak direct grant with `invalid_grant`, proving the imported MFA-required action affects login.
- A synthetic manager with `webauthn-register` required action is blocked by Keycloak direct grant with `invalid_grant`, proving the imported WebAuthn required action affects non-browser grant flows.
- The imported realm declares an explicit WebAuthn policy for the synthetic local lab: RP ID `localhost`, RP entity `Synthetic Banking Lab`, ES256 signatures, attestation conveyance `none`, user verification `required`, and duplicate-authenticator avoidance.
- The imported realm declares `PASSKEY_RECOVERY_ADMIN` and a synthetic `security-admin01` account that has recovery/compliance/auditor roles without customer, branch-staff, or branch-manager roles.
- `KeycloakRealmPolicyTest` locks the JSON realm policy and recovery segregation so future realm edits cannot silently remove the WebAuthn policy or over-broaden recovery authority.
- `staff-terminal` can complete a live Keycloak WebAuthn required action using a Chromium virtual authenticator, exchange the authorization code through the Next BFF route, and call Spring with the resulting signed Bearer token.
- `@banking-lab/auth-client` generates PKCE verifier/challenge pairs and Keycloak authorization URLs for browser login.
- `customer-web` redirects to live Keycloak, exchanges the authorization code through the Next BFF route `POST /api/auth/keycloak-token`, and stores the resulting Bearer token only in browser memory state for the smoke.
- Spring validates the browser login token through the live Keycloak JWKS with `BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false`.
- The browser renders masked account detail for `SYN-CUS-001` from `GET /api/customer/accounts/ACC-SYN-001-001/detail` using the Keycloak-issued access token.
- The browser uses the same Keycloak-issued access token to call `POST /api/customer/transfers` twice with one idempotency key and renders the replayed `POSTED` transaction ID.
- The browser uses the same Keycloak-issued access token to render structured `LEDGER_INSUFFICIENT_AVAILABLE_BALANCE`, read customer transfer history, read seeded held FDS status, and verify durable held/failed transfer statuses without unsafe postings.
- The browser uses the same Keycloak-issued access token to call `POST /api/customer/complaints` and `POST /api/customer/complaints/CMP-SYN-CONFIRM-001/confirm`.
- `staff-terminal` redirects to live Keycloak, exchanges branch and checker authorization codes through the Next BFF route `POST /api/auth/keycloak-token`, and uses the resulting signed Bearer tokens against Spring with simulator fallback disabled.
- Spring maps live Keycloak `preferred_username` to the synthetic actor subject for command actor binding while retaining `sub` fallback for non-Keycloak signed JWT tests.
- The staff browser smoke renders masked customer detail for `SYN-CUS-001` with a business reason using the `branch01` token, executes privileged unmask as `manager01`, then requests a customer information change as `branch01` and approves it as `manager01`.
- `complaint-portal` redirects to live Keycloak, exchanges complaint handler and checker authorization codes through the Next BFF route `POST /api/auth/keycloak-token`, and uses the resulting signed Bearer tokens against Spring with simulator fallback disabled.
- The complaint browser smoke renders `CMP-SYN-001` with the `complaint01` token, drafts an answer for `CMP-SYN-CMD-001` as `complaint01`, approves it as `manager01`, and renders the duplicate answer-draft `WORKFLOW_STATE_VIOLATION` for `CMP-SYN-FAIL-001`.
- `ops-console` redirects to live Keycloak, exchanges ops operator and checker authorization codes through the Next BFF route `POST /api/auth/keycloak-token`, and uses the resulting signed Bearer tokens against Spring with simulator fallback disabled.
- The ops browser smoke renders `REC-SYN-001` with the `ops01` token, requests a reconciliation adjustment for `REC-SYN-CMD-001` as `ops01`, approves it as `manager01`, and renders the adjusted-item `WORKFLOW_STATE_VIOLATION` for `REC-SYN-FAIL-001`.
- `audit-console` redirects to live Keycloak, exchanges an auditor authorization code through the Next BFF route `POST /api/auth/keycloak-token`, and uses the resulting signed Bearer token against Spring with simulator fallback disabled.
- The audit browser smoke renders hash-chain validity plus `AUD-SYN-SEED-001` with the `auditor01` token.
- `fds-aml-console` redirects to live Keycloak, exchanges risk reviewer and checker authorization codes through the Next BFF route `POST /api/auth/keycloak-token`, and uses the resulting signed Bearer tokens against Spring with simulator fallback disabled.
- The FDS/AML browser smoke renders `FDS-SYN-001` and `AML-SYN-001` with the `risk01` token, requests FDS release, FDS block, and AML closure as `risk01`, approves them as `compliance01`, and renders duplicate FDS/AML `WORKFLOW_STATE_VIOLATION` evidence.
- Docker Compose passes JWKS URI, issuer, and audience settings into `core-banking` so the container can run with simulator token fallback disabled.
- The payment outbox worker Compose smoke passes an explicit local
  split-horizon issuer allow-list into Spring so host-side direct-grant tokens
  and container-side client-credentials tokens are both accepted only by exact
  configured issuer match.

## Commands

```bash
env BANKING_LAB_KEYCLOAK_PORT=18085 docker compose --profile platform config
env BANKING_LAB_KEYCLOAK_PORT=18085 docker compose --profile platform up -d --force-recreate keycloak
curl -sS -i http://127.0.0.1:18085/realms/banking-lab/.well-known/openid-configuration
curl -sS -i -X POST http://127.0.0.1:18085/realms/banking-lab/protocol/openid-connect/token -H 'Content-Type: application/x-www-form-urlencoded' --data 'grant_type=password&client_id=staff-terminal&username=manager-mfa01&password=manager-mfa01-pass'
env BANKING_LAB_LIVE_KEYCLOAK_BASE_URL=http://127.0.0.1:18085 scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.security.LiveKeycloakRealmIntegrationTest
npm run packages:typecheck
npm run next:customer-web:typecheck
npm run next:customer-web:build
npm run test:e2e
env COMPOSE_PROJECT_NAME=banking-lab-keycloak-status-smoke BANKING_LAB_POSTGRES_PORT=15462 BANKING_LAB_CORE_BANKING_PORT=18106 BANKING_LAB_KEYCLOAK_PORT=18107 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://127.0.0.1:18107/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking
curl -fsS http://127.0.0.1:18107/realms/banking-lab/.well-known/openid-configuration
curl -fsS http://127.0.0.1:18106/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18106 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18107 npx playwright test apps/customer-web/e2e/customer-web-parity.spec.ts -g "interactive Keycloak"
curl -fsS http://127.0.0.1:18106/health
env COMPOSE_PROJECT_NAME=banking-lab-keycloak-status-smoke docker compose --profile platform down -v
npm run packages:typecheck
npm run next:staff-terminal:typecheck
npm run next:staff-terminal:build
scripts/run-core-banking-tests.sh :services:core-banking:bootJar
env COMPOSE_PROJECT_NAME=banking-lab-staff-keycloak-smoke BANKING_LAB_POSTGRES_PORT=15463 BANKING_LAB_CORE_BANKING_PORT=18108 BANKING_LAB_KEYCLOAK_PORT=18109 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://127.0.0.1:18109/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking
curl -fsS http://127.0.0.1:18109/realms/banking-lab/.well-known/openid-configuration
curl -fsS http://127.0.0.1:18108/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18108 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18109 npx playwright test apps/staff-terminal/e2e/staff-terminal-parity.spec.ts -g "interactive Keycloak"
curl -fsS http://127.0.0.1:18108/health
env COMPOSE_PROJECT_NAME=banking-lab-staff-keycloak-smoke docker compose --profile platform down -v
env COMPOSE_PROJECT_NAME=banking-lab-complaint-keycloak-smoke BANKING_LAB_POSTGRES_PORT=15464 BANKING_LAB_CORE_BANKING_PORT=18110 BANKING_LAB_KEYCLOAK_PORT=18111 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://127.0.0.1:18111/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18111/realms/banking-lab/.well-known/openid-configuration
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18110/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18110 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18111 npx playwright test apps/complaint-portal/e2e/complaint-portal-parity.spec.ts -g "interactive Keycloak"
curl -fsS http://127.0.0.1:18110/health
env COMPOSE_PROJECT_NAME=banking-lab-complaint-keycloak-smoke docker compose --profile platform down -v
env COMPOSE_PROJECT_NAME=banking-lab-ops-keycloak-smoke BANKING_LAB_POSTGRES_PORT=15465 BANKING_LAB_CORE_BANKING_PORT=18112 BANKING_LAB_KEYCLOAK_PORT=18113 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://127.0.0.1:18113/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18113/realms/banking-lab/.well-known/openid-configuration
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18112/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18112 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18113 npx playwright test apps/ops-console/e2e/ops-console-parity.spec.ts -g "interactive Keycloak"
curl -fsS http://127.0.0.1:18112/health
env COMPOSE_PROJECT_NAME=banking-lab-ops-keycloak-smoke docker compose --profile platform down -v
env COMPOSE_PROJECT_NAME=banking-lab-audit-keycloak-smoke BANKING_LAB_POSTGRES_PORT=15466 BANKING_LAB_CORE_BANKING_PORT=18114 BANKING_LAB_KEYCLOAK_PORT=18115 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://127.0.0.1:18115/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18115/realms/banking-lab/.well-known/openid-configuration
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18114/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18114 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18115 npx playwright test apps/audit-console/e2e/audit-console-parity.spec.ts -g "interactive Keycloak"
curl -fsS http://127.0.0.1:18114/health
env COMPOSE_PROJECT_NAME=banking-lab-audit-keycloak-smoke docker compose --profile platform down -v
npm run next:fds-aml-console:typecheck
npm run next:fds-aml-console:build
npm run test:e2e
env COMPOSE_PROJECT_NAME=banking-lab-risk-keycloak-smoke BANKING_LAB_POSTGRES_PORT=15476 BANKING_LAB_CORE_BANKING_PORT=18124 BANKING_LAB_KEYCLOAK_PORT=18125 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://127.0.0.1:18125/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18125/realms/banking-lab/.well-known/openid-configuration
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18124/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18124 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18125 npx playwright test apps/fds-aml-console/e2e/fds-aml-console-parity.spec.ts -g "interactive Keycloak"
curl -fsS http://127.0.0.1:18124/health
env COMPOSE_PROJECT_NAME=banking-lab-risk-keycloak-smoke docker compose --profile platform down -v
env COMPOSE_PROJECT_NAME=banking-lab-webauthn-smoke BANKING_LAB_POSTGRES_PORT=15477 BANKING_LAB_CORE_BANKING_PORT=18126 BANKING_LAB_KEYCLOAK_PORT=18127 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://localhost:18127/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://localhost:18127/realms/banking-lab/.well-known/openid-configuration
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18126/health
curl -sS -i -X POST http://localhost:18127/realms/banking-lab/protocol/openid-connect/token -H 'Content-Type: application/x-www-form-urlencoded' --data 'grant_type=password&client_id=staff-terminal&username=manager-webauthn-block01&password=manager-webauthn-block01-pass'
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18126 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://localhost:18127 npx playwright test apps/staff-terminal/e2e/staff-terminal-parity.spec.ts -g "WebAuthn"
env BANKING_LAB_LIVE_KEYCLOAK_BASE_URL=http://localhost:18127 scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.security.LiveKeycloakRealmIntegrationTest
curl -fsS http://127.0.0.1:18126/health
env COMPOSE_PROJECT_NAME=banking-lab-webauthn-smoke docker compose --profile platform down -v
node -e "const fs=require('fs'); JSON.parse(fs.readFileSync('infra/keycloak/realm-banking-lab.json','utf8')); console.log('realm ok')"
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test --tests lab.banking.core.security.KeycloakRealmPolicyTest
npm run test:payment-service:keycloak-service-token
npm run test:payment-service:unit -- --tests lab.banking.payment.core.CoreBankingServiceTokenProviderTest --rerun-tasks
npm run test:core-banking:integration -- --tests lab.banking.core.security.JwksAuthorizationIntegrationTest --rerun-tasks
npm run test:payment-service:integration -- --tests lab.banking.payment.LivePaymentOutboxWorkerComposeSmokeIntegrationTest --rerun-tasks
npm run test:payment-service:outbox-worker-compose
npm run test:notification-service:keycloak-service-token
npm run test:reporting-service:keycloak-service-token
env COMPOSE_PROJECT_NAME=banking-lab-passkey-policy-smoke BANKING_LAB_POSTGRES_PORT=15478 BANKING_LAB_CORE_BANKING_PORT=18128 BANKING_LAB_KEYCLOAK_PORT=18129 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://localhost:18129/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://localhost:18129/realms/banking-lab/.well-known/openid-configuration
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18128/health
curl -sS -i -X POST http://localhost:18129/realms/banking-lab/protocol/openid-connect/token -H 'Content-Type: application/x-www-form-urlencoded' --data 'grant_type=password&client_id=staff-terminal&username=manager-webauthn-block01&password=manager-webauthn-block01-pass'
env BANKING_LAB_LIVE_KEYCLOAK_BASE_URL=http://localhost:18129 scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.security.LiveKeycloakRealmIntegrationTest
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18128 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://localhost:18129 npx playwright test apps/staff-terminal/e2e/staff-terminal-parity.spec.ts -g "WebAuthn"
curl -fsS http://127.0.0.1:18128/health
env COMPOSE_PROJECT_NAME=banking-lab-passkey-policy-smoke docker compose --profile platform down -v
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.security.SecurityAuthorizationIntegrationTest
scripts/run-core-banking-tests.sh :services:core-banking:bootJar
env COMPOSE_PROJECT_NAME=banking-lab-staff-unmask-smoke BANKING_LAB_POSTGRES_PORT=15479 BANKING_LAB_CORE_BANKING_PORT=18130 BANKING_LAB_KEYCLOAK_PORT=18131 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://localhost:18131/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build --force-recreate postgres keycloak core-banking
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://localhost:18131/realms/banking-lab/.well-known/openid-configuration
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18130/health
env CI=1 BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18130 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://localhost:18131 npx playwright test apps/staff-terminal/e2e/staff-terminal-parity.spec.ts -g "interactive Keycloak"
curl -fsS http://127.0.0.1:18130/health
env COMPOSE_PROJECT_NAME=banking-lab-staff-unmask-smoke docker compose --profile platform down -v
```

## Result

Passed. Keycloak imported the `banking-lab` realm, exposed OIDC discovery over the dev HTTP endpoint, issued signed access tokens for staff/customer clients, and blocked the TOTP MFA-required synthetic manager with `invalid_grant`.

The payment-service client-credentials slice also passed. A disposable Compose
stack started PostgreSQL, Keycloak, `core-banking`, and `payment-service` with
simulator tokens disabled. `payment-service-api` received a signed
client-credentials token containing the `PAYMENT_SERVICE` realm role and both
`payment-service-api` and `core-banking-api` audiences, and payment-service
accepted that token on `POST /api/payments/outbox/ledger-postings/dispatch-next`
with a safe `NO_PENDING_EVENT` response.

The payment outbox worker client-credentials slice also passed. A disposable
Compose stack started PostgreSQL, Keycloak, `core-banking`, `payment-service`,
and `payment-outbox-worker` with simulator tokens disabled. The test obtained
live Keycloak ops/customer tokens for host-side funding and payment creation,
then restarted the worker and proved it fetched its own
`payment-service-api` client-credentials token before settling the payment as a
balanced core-banking `BILL_PAYMENT` ledger transaction.

The notification-service client-credentials slice also passed. A disposable
Compose stack started PostgreSQL, Redpanda, Keycloak, and
`notification-service` with simulator tokens disabled. `notification-service-api`
received a signed client-credentials token containing the
`NOTIFICATION_SERVICE` realm role and `notification-service-api` audience, and
notification-service accepted that token on `POST /api/notifications/events` to
create one masked `PENDING` synthetic delivery item.

The reporting-service client-credentials slice also passed. A disposable
Compose stack started PostgreSQL, Keycloak, and `reporting-service` with
simulator tokens disabled. `reporting-service-api` received a signed
client-credentials token containing the `REPORTING_ANALYST` realm role and
`reporting-service-api` audience, and reporting-service accepted that token on
catalog, artifact generation, and artifact list routes while preserving
reason-required audit and `maskedByDefault=true` metadata.

The customer-web browser propagation slice also passed. Baseline `npm run test:e2e` passed 12 manifest-shell tests with 23 API-backed tests skipped when no API or Keycloak URL was configured. The targeted live browser smoke passed 1 Playwright test against `http://127.0.0.1:18107` Keycloak and `http://127.0.0.1:18106` Spring, with simulator tokens disabled in Spring. The page rendered `Keycloak account loaded`, `Bearer`, `SYN-CUS-001`, `LAB-***-0001`, `100000000 KRW`, `Keycloak transfer replayed`, `CWB-OIDC-TRF-...`, `TX-...`, `POSTED`, `same transaction id`, `Keycloak transfer rejected`, `LEDGER_INSUFFICIENT_AVAILABLE_BALANCE`, `Keycloak history and held status loaded`, `CWB-OIDC-HIST-...`, `FDS-SYN-001`, `HELD`, `Keycloak held and failed statuses loaded`, `CWB-OIDC-HELD-...`, `CWB-OIDC-FAILED-...`, `FAILED`, `REQUEST_VALIDATION_FAILED`, `Keycloak complaint received`, `ACCOUNT_ACCESS`, `RECEIVED`, `Keycloak complaint closed`, `CMP-SYN-CONFIRM-001`, and `CLOSED`.

The staff-terminal browser propagation slice also passed. The targeted live browser smoke passed 1 Playwright test against `http://127.0.0.1:18109` Keycloak and `http://127.0.0.1:18108` Spring, with simulator tokens disabled in Spring. The page rendered `Keycloak staff detail loaded`, `Bearer`, `SYN-CUS-001`, `010-****-1001`, `AUD-...`, `Keycloak checker loaded`, `manager01`, `Keycloak customer change approved`, `APR-...`, `branch01`, `manager01`, `SYN-CUS-CMD-001`, `010-****-1499`, and `Keycloak customer change executed`.

The staff-terminal privileged unmask propagation slice also passed. A fresh stack on ports `15479`, `18130`, and `18131` ran with simulator-token fallback disabled. The targeted staff-terminal Keycloak run passed the staff/checker flow and WebAuthn flow; the staff/checker flow rendered `Keycloak unmask approved`, `UNMASKED_TIMEBOXED`, `010-0000-1001`, `300`, and `AUD-...` using the `manager01` signed token before executing customer-change approval. `SecurityAuthorizationIntegrationTest` also passed with a CORS assertion for browser-readable auth denials on `POST /api/staff/pii/unmask`.

The complaint-portal browser propagation slice also passed. The targeted live browser smoke passed 1 Playwright test against `http://127.0.0.1:18111` Keycloak and `http://127.0.0.1:18110` Spring, with simulator tokens disabled in Spring. The page rendered `Keycloak complaint case loaded`, `Bearer`, `CMP-SYN-001`, `SYN-CUS-001`, `IN_REVIEW`, `Keycloak complaint checker loaded`, `manager01`, `Keycloak answer approved`, `APR-...`, `complaint01`, `manager01`, `CMP-SYN-CMD-001`, `ANSWERED`, `Keycloak workflow state rejected`, `CMP-SYN-FAIL-001`, `WORKFLOW_STATE_VIOLATION`, domain `workflow`, status `409`, and route `/api/staff/complaints/CMP-SYN-FAIL-001/answer-drafts`.

The ops-console browser propagation slice also passed. The targeted live browser smoke passed 1 Playwright test against `http://127.0.0.1:18113` Keycloak and `http://127.0.0.1:18112` Spring, with simulator tokens disabled in Spring. The page rendered `Keycloak reconciliation item loaded`, `Bearer`, `REC-SYN-001`, `OPEN`, `12000 KRW`, `Keycloak reconciliation checker loaded`, `manager01`, `Keycloak reconciliation adjusted`, `APR-...`, `ops01`, `manager01`, `REC-SYN-CMD-001`, `ADJUSTED`, `TX-...`, `workflow state rejected`, `REC-SYN-FAIL-001`, `WORKFLOW_STATE_VIOLATION`, domain `workflow`, status `409`, and route `/api/ops/reconciliation-items/REC-SYN-FAIL-001/adjustment-requests`.

The audit-console browser propagation slice also passed. The targeted live browser smoke passed 1 Playwright test against `http://127.0.0.1:18115` Keycloak and `http://127.0.0.1:18114` Spring, with simulator tokens disabled in Spring. The page rendered `Keycloak audit loaded`, `Bearer`, `valid`, `AUD-SYN-SEED-001`, and `SYNTHETIC_SEED`.

The FDS/AML-console browser propagation slice also passed. The targeted live browser smoke passed 1 Playwright test against `http://127.0.0.1:18125` Keycloak and `http://127.0.0.1:18124` Spring, with simulator tokens disabled in Spring. The page rendered `Keycloak risk cases loaded`, `Bearer`, `FDS-SYN-001`, `AML-SYN-001`, `INVESTIGATING`, `Keycloak risk checker loaded`, `compliance01`, `Keycloak risk approvals completed`, `risk01`, `FDS-SYN-OIDC-REL-001`, `RELEASED`, `TX-...`, `FDS-SYN-OIDC-BLOCK-001`, `BLOCKED`, `not posted`, `AML-SYN-OIDC-CLOSE-001`, `CLOSED`, `STR_SIMULATED`, `workflow state rejected`, `FDS-SYN-FAIL-001`, `AML-SYN-FAIL-001`, `WORKFLOW_STATE_VIOLATION`, domain `workflow`, and status `409`. Post-smoke `/health` still returned `auditHashChainValid=true`.

The WebAuthn browser slice also passed. The live stack used `http://localhost:18127` as the Keycloak issuer so the browser WebAuthn RP domain matched the login origin, while Spring still fetched JWKS through the container network. The direct grant for `manager-webauthn-block01` returned `400 invalid_grant` with `Account is not fully set up`. The targeted Playwright smoke passed 1 Chromium test using a CDP virtual authenticator: `manager-webauthn01` completed the Keycloak `webauthn-register` required action, the Next BFF exchanged the returned authorization code, Spring accepted the signed token with simulator tokens disabled, and the staff panel rendered `Keycloak WebAuthn manager loaded`, `manager-webauthn01`, `Bearer`, `SYN-CUS-001`, `010-****-1001`, and `AUD-...`. `LiveKeycloakRealmIntegrationTest` also passed against the same live realm and now asserts both TOTP and WebAuthn required-action blocking.

The passkey policy and recovery segregation slice also passed. `KeycloakRealmPolicyTest` verifies the committed realm contains the explicit local WebAuthn policy and the `PASSKEY_RECOVERY_ADMIN` role, and that `security-admin01` has only recovery/compliance/auditor authority instead of customer, branch staff, or branch manager authority. A fresh live stack on ports `15478`, `18128`, and `18129` imported the realm, Spring `/health` returned `auditHashChainValid=true`, direct grant for `manager-webauthn-block01` still returned `400 invalid_grant`, `LiveKeycloakRealmIntegrationTest` decoded the `security-admin01` token roles and used that signed token against Spring with simulator tokens disabled, and the WebAuthn browser smoke passed again against the same realm.

During the customer-web smoke, a direct Keycloak token diagnostic initially returned 401 until `docker-compose.yml` was fixed to pass `BANKING_LAB_SECURITY_JWKS_URI`, `BANKING_LAB_SECURITY_ISSUER`, and `BANKING_LAB_SECURITY_AUDIENCE` into the `core-banking` container. During the staff-terminal smoke, a direct token diagnostic initially returned `AUTHORIZATION_POLICY_VIOLATION` for actor binding until `SignedJwtJwksTokenDecoder` was updated to use Keycloak `preferred_username` as the command actor subject. After recreating `core-banking`, the branch-maker/manager-checker command path returned 201/200 and the browser smoke passed. Post-smoke `/health` still returned `auditHashChainValid=true`.

## Remaining Gaps

- WebAuthn required-action, local virtual-authenticator browser completion, synthetic WebAuthn policy, and recovery role segregation are proven for the synthetic staff-terminal/local lab path. Non-synthetic passkey operations, hardware attestation policy, enterprise recovery runbooks, and production deployment evidence are not claimed.
- Browser-based Next.js login and role propagation are covered for the current customer-web API-backed smoke paths: masked account detail, transfer retry, transfer failure, transfer history/status, held/failed status, complaint entry, and complaint confirmation.
- Staff-terminal browser login and role propagation are covered for masked customer lookup and branch-maker/manager-checker customer-change approval.
- FDS/AML-console browser login and role propagation are covered for risk read-model access, FDS release/block approval, AML closure approval, and duplicate workflow failure-state rendering.
- Node retirement remains blocked until non-synthetic passkey operations and final retirement review are complete.
