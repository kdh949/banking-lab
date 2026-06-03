# Non-Synthetic Passkey Operations Evidence Boundary

Date: 2026-06-03

Status: blocked

## Scope

This evidence boundary covers the remaining passkey blocker for Node reference retirement. The data, users, accounts, and transactions must stay synthetic. The authenticator ceremony is the only part that must become non-synthetic: a real platform authenticator or hardware security key must complete the Keycloak WebAuthn registration/login path without browser virtual-authenticator APIs.

This document does not mark Node retirement ready.

## Current Result

Not proven. Existing Keycloak evidence proves WebAuthn required-action blocking, the committed synthetic realm WebAuthn policy, passkey recovery role segregation, and a staff-terminal browser smoke with simulator tokens disabled. The browser smoke uses Chromium CDP `WebAuthn.enable` plus `WebAuthn.addVirtualAuthenticator`; that is valid local WebAuthn required-action evidence, but it is not non-synthetic passkey evidence.

## Required Evidence Before Passing

- A live Keycloak realm is imported from `infra/keycloak/realm-banking-lab.json`.
- Spring runs with `BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false`.
- The WebAuthn browser origin matches the local RP ID used by the realm.
- A real platform authenticator or hardware security key completes the `webauthn-register` required action for the synthetic `manager-webauthn01` account.
- The browser flow must not use Playwright CDP `WebAuthn.enable`, `WebAuthn.addVirtualAuthenticator`, browser virtual-authenticator APIs, seeded passkey credentials, or simulator tokens.
- The Next BFF exchanges the authorization code and Spring accepts the Keycloak-signed token through JWKS validation.
- Staff customer detail returns only masked PII by default and writes a reason-required audit event.
- Recovery authority remains segregated: `security-admin01` must not gain customer, branch staff, or branch manager roles.
- Evidence must redact tokens, cookies, session IDs, credential IDs, challenge values, attestation objects, and authenticator metadata that could be reused.

## Required Artifact

When the gate is genuinely proven, create `docs/test-evidence/generated/passkey-non-synthetic-evidence.json` with this shape:

```json
{
  "schemaVersion": 1,
  "status": "pass",
  "testDate": "YYYY-MM-DD",
  "evidenceKind": "manual-live-passkey",
  "authenticatorKind": "platform",
  "usedBrowserVirtualAuthenticator": false,
  "usedPlaywrightCdpWebAuthn": false,
  "simulatorTokensEnabled": false,
  "keycloakRequiredActionCompleted": true,
  "springSignedTokenAccepted": true,
  "syntheticOnly": true,
  "redactionConfirmed": true,
  "commands": [
    "commands actually run"
  ],
  "staffPanelAssertions": {
    "webAuthnLoaded": true,
    "managerSubjectObserved": true,
    "bearerTokenTypeObserved": true,
    "syntheticCustomerObserved": true,
    "maskedPiiObserved": true,
    "auditEventObserved": true
  }
}
```

`authenticatorKind` must be `platform` or `hardware-security-key`. Values such as `virtual`, `simulated`, or `cdp` are rejected by `npm run node:retirement-gate`.

## Existing Synthetic Commands

These commands are already represented in `docs/test-evidence/keycloak-live-realm-smoke.md`; they remain useful regression evidence but do not close this blocker:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-webauthn-smoke BANKING_LAB_POSTGRES_PORT=15477 BANKING_LAB_CORE_BANKING_PORT=18126 BANKING_LAB_KEYCLOAK_PORT=18127 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://localhost:18127/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://localhost:18127/realms/banking-lab/.well-known/openid-configuration
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18126/health
curl -sS -i -X POST http://localhost:18127/realms/banking-lab/protocol/openid-connect/token -H 'Content-Type: application/x-www-form-urlencoded' --data 'grant_type=password&client_id=staff-terminal&username=manager-webauthn-block01&password=manager-webauthn-block01-pass'
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18126 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://localhost:18127 npx playwright test apps/staff-terminal/e2e/staff-terminal-parity.spec.ts -g "WebAuthn"
env BANKING_LAB_LIVE_KEYCLOAK_BASE_URL=http://localhost:18127 scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.security.LiveKeycloakRealmIntegrationTest
env COMPOSE_PROJECT_NAME=banking-lab-webauthn-smoke docker compose --profile platform down -v
```

The Playwright command above uses a Chromium virtual authenticator by design, so it is not non-synthetic passkey evidence.

## Preflight Check

Before scheduling the future manual run, use:

```bash
npm run passkey:evidence:preflight
```

This checks that the retirement gate is still blocked, the passkey evidence runbook still requires `manual-live-passkey`, the recorder rejects virtual/CDP WebAuthn and simulator-token evidence, the Keycloak realm still contains `webauthn-register` for `manager-webauthn01`, `security-admin01` remains segregated from customer/branch roles, and the staff terminal still exposes the masked/audited WebAuthn manager flow.

The preflight command does not create `docs/test-evidence/generated/passkey-non-synthetic-evidence.json`, does not prove non-synthetic passkey operations, and must not be used to mark this gate passed.

## Manual Runbook Boundary

The future non-synthetic run should use the same Compose stack shape as the existing WebAuthn smoke, but the browser interaction must be manual or use only ordinary browser automation that does not install a virtual authenticator. The operator should sign in as `manager-webauthn01`, complete Keycloak passkey registration with a real authenticator, return to `staff-terminal`, and confirm the staff panel shows `Keycloak WebAuthn manager loaded`, `manager-webauthn01`, `Bearer`, `SYN-CUS-001`, masked phone output, and an `AUD-...` audit event ID.

After the run, copy the redacted command list to a local text file and copy the relevant staff panel text to a redacted snapshot file. Then run:

```bash
env BANKING_LAB_PASSKEY_EVIDENCE_CONFIRMED=true \
  BANKING_LAB_PASSKEY_AUTHENTICATOR_KIND=platform \
  BANKING_LAB_PASSKEY_TEST_DATE=YYYY-MM-DD \
  BANKING_LAB_PASSKEY_USED_BROWSER_VIRTUAL_AUTHENTICATOR=false \
  BANKING_LAB_PASSKEY_USED_PLAYWRIGHT_CDP_WEBAUTHN=false \
  BANKING_LAB_PASSKEY_SIMULATOR_TOKENS_ENABLED=false \
  BANKING_LAB_PASSKEY_KEYCLOAK_REQUIRED_ACTION_COMPLETED=true \
  BANKING_LAB_PASSKEY_SPRING_SIGNED_TOKEN_ACCEPTED=true \
  BANKING_LAB_PASSKEY_SYNTHETIC_ONLY=true \
  BANKING_LAB_PASSKEY_REDACTION_CONFIRMED=true \
  BANKING_LAB_PASSKEY_COMMANDS_FILE=/path/to/redacted-commands.txt \
  BANKING_LAB_PASSKEY_PANEL_SNAPSHOT_FILE=/path/to/redacted-staff-panel.txt \
  npm run passkey:evidence:record
```

The recorder writes `docs/test-evidence/generated/passkey-non-synthetic-evidence.json` only after confirming the run did not use virtual/CDP WebAuthn, simulator tokens were disabled, the panel shows `Keycloak WebAuthn manager loaded`, `manager-webauthn01`, `Bearer`, `SYN-CUS-001`, masked phone output, and an `AUD-...` audit event, and the inputs do not contain obvious reusable tokens, cookies, passwords, credential IDs, attestation objects, or unmasked phone output.

After artifact generation, update this document with the exact commands and attach the generated JSON artifact above. Keep all screenshots and logs redacted before committing.

## Controls

- PII masking remains default for staff customer detail.
- Business reason is required for the staff lookup and produces audit evidence.
- Simulator token fallback stays disabled for the live Spring validation path.
- Recovery role segregation prevents a passkey recovery admin from acting as customer, branch staff, or branch manager.
- The evidence remains synthetic-only and must not include real customer data, real money, real KYC, or real financial network data.

## Retirement Impact

Node retirement remains blocked until this evidence is proven and the final retirement review confirms no critical behavior depends on Node-only code.
