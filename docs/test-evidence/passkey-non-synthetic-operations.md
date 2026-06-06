# Non-Synthetic Passkey Operations Evidence Boundary

Date: 2026-06-03

Status: pass

## Scope

This evidence boundary covers the remaining passkey blocker for Node reference retirement. The data, users, accounts, and transactions must stay synthetic. The authenticator ceremony is the only part that must become non-synthetic: a real platform authenticator or hardware security key must complete the Keycloak WebAuthn registration/login path without browser virtual-authenticator APIs.

This document proves only the non-synthetic passkey operations gate. Final Node retirement still requires the final retirement review artifact and ready-gate validation.

## Current Result

Proven. `docs/test-evidence/generated/passkey-non-synthetic-evidence.json` records a `manual-live-passkey` operation completed with a real platform authenticator for the synthetic `manager-webauthn01` account, with simulator tokens disabled and without browser virtual-authenticator APIs or Playwright CDP WebAuthn.

Verification:

```text
npm run passkey:evidence:verify
Passkey non-synthetic evidence verification: pass
Verified manual-live-passkey artifact: docs/test-evidence/generated/passkey-non-synthetic-evidence.json
```

Existing Keycloak evidence also proves WebAuthn required-action blocking, the committed synthetic realm WebAuthn policy, passkey recovery role segregation, and a staff-terminal browser smoke with simulator tokens disabled. The browser smoke uses Chromium CDP `WebAuthn.enable` plus `WebAuthn.addVirtualAuthenticator`; that is valid local WebAuthn required-action evidence, but it is not non-synthetic passkey evidence.

The 2026-06-03 live-readiness run proved that the local manual passkey platform stack can start with simulator tokens disabled, Keycloak discovery/JWKS reachable, and Spring `/health` reporting the target Kotlin/Spring synthetic boundary. The later manual artifact above closes the non-synthetic authenticator portion of this gate.

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

The generated artifact at `docs/test-evidence/generated/passkey-non-synthetic-evidence.json` uses this shape:

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
  "manualCeremony": {
    "browserOrigin": "http://localhost:3002",
    "keycloakIssuer": "http://localhost:18127/realms/banking-lab",
    "rpId": "localhost",
    "username": "manager-webauthn01",
    "authorizationFlow": "authorization-code-pkce",
    "browserAutomation": "ordinary-browser-no-virtual-authenticator",
    "operatorConfirmation": "real-platform-or-hardware-authenticator-used"
  },
  "commands": [
    {
      "command": "env COMPOSE_PROJECT_NAME=banking-lab-passkey-manual BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false docker compose --profile platform up -d --build postgres keycloak core-banking",
      "status": "pass",
      "exitCode": 0,
      "summary": "Compose platform stack started with simulator tokens disabled."
    },
    {
      "command": "curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://localhost:18127/realms/banking-lab/.well-known/openid-configuration",
      "status": "pass",
      "exitCode": 0,
      "summary": "Keycloak OIDC discovery endpoint returned successfully."
    },
    {
      "command": "curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18126/health",
      "status": "pass",
      "exitCode": 0,
      "summary": "Spring health endpoint returned successfully."
    },
    {
      "command": "manual browser sign-in completed with a real platform authenticator",
      "status": "pass",
      "exitCode": 0,
      "summary": "Operator completed Keycloak WebAuthn required action using a real platform authenticator."
    },
    {
      "command": "npm run passkey:evidence:record",
      "status": "pass",
      "exitCode": 0,
      "summary": "Passkey evidence recorder wrote the redacted artifact."
    }
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

## Evidence Template Preparation

Before the manual browser ceremony, prepare local redaction templates:

```bash
npm run passkey:evidence:prepare
```

By default, this writes ignored local files under `tmp/passkey-evidence-manual/`:

- `redacted-commands.template.json`
- `redacted-staff-panel.template.txt`
- `record-command.template.sh`

The generated command template intentionally contains `TODO_REPLACE_WITH_pass` and `TODO_REPLACE_WITH_0`, so it cannot be recorded as passing evidence without manual replacement after the real run. The staff-panel template also requires replacing the audit event placeholder with the redacted `AUD-...` value rendered by the staff terminal.

After preparing templates, verify that the local manual-run inputs still match the retirement boundary:

```bash
npm run passkey:evidence:readiness
```

This checks the prepared command template, staff-panel template, recorder command, Docker Compose platform service names and port overrides, Keycloak WebAuthn realm policy, local issuer `http://localhost:18127/realms/banking-lab`, Spring health URL `http://127.0.0.1:18126/health`, simulator-token-disabled startup, and the required masked staff-panel markers. It still does not prove non-synthetic passkey operations because the template values remain TODO placeholders until the real platform-authenticator or hardware-security-key ceremony is completed.

After the Compose platform stack is running, verify the live local endpoints before the browser ceremony:

```bash
npm run passkey:evidence:live-readiness
```

This checks the prepared command template against the live Keycloak discovery document, Keycloak JWKS endpoint, and Spring `/health` response. It requires the discovery issuer and JWKS URI to match the configured local `banking-lab` realm, the authorization-code endpoint to be present, at least one JWKS signing key, and Spring `/health` to return `status=ok`, `syntheticOnly=true`, `auditHashChainValid=true`, and `migrationTarget=kotlin-spring-boot`. It still does not prove non-synthetic passkey operations because no real platform-authenticator or hardware-security-key ceremony has been completed.

Start the staff terminal for the manual passkey ceremony with simulator-token smoke calls disabled:

```bash
NEXT_PUBLIC_BANKING_API_BASE_URL=http://127.0.0.1:18126 NEXT_PUBLIC_BANKING_KEYCLOAK_BASE_URL=http://localhost:18127 NEXT_PUBLIC_BANKING_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_KEYCLOAK_BASE_URL=http://localhost:18127 npm run next:staff-terminal
```

With this mode enabled, the simulator-token staff lookup, customer-change smoke, and privileged-unmask smoke show `simulator token smoke disabled` instead of attempting simulator Bearer tokens against Spring. The `Sign in WebAuthn manager with Keycloak` button remains enabled and is the intended entry point for the real authenticator ceremony.

## 2026-06-03 Live Platform Readiness Evidence

The following commands were run against an isolated local Compose project before the manual browser ceremony:

```bash
npm run passkey:evidence:prepare
npm run passkey:evidence:preflight
npm run passkey:evidence:readiness
COMPOSE_PROJECT_NAME=banking-lab-passkey-manual BANKING_LAB_POSTGRES_PORT=15477 BANKING_LAB_CORE_BANKING_PORT=18126 BANKING_LAB_KEYCLOAK_PORT=18127 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://localhost:18127/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://localhost:18127/realms/banking-lab/.well-known/openid-configuration
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18126/health
COMPOSE_PROJECT_NAME=banking-lab-passkey-manual docker compose --profile platform ps postgres keycloak core-banking
npm run passkey:evidence:live-readiness
NEXT_PUBLIC_BANKING_API_BASE_URL=http://127.0.0.1:18126 NEXT_PUBLIC_BANKING_KEYCLOAK_BASE_URL=http://localhost:18127 NEXT_PUBLIC_BANKING_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_KEYCLOAK_BASE_URL=http://localhost:18127 npm run next:staff-terminal
```

Results:

- The Compose command built and started `postgres`, `keycloak`, and `core-banking` under `banking-lab-passkey-manual`.
- Keycloak discovery returned issuer `http://localhost:18127/realms/banking-lab` and JWKS URI `http://localhost:18127/realms/banking-lab/protocol/openid-connect/certs`.
- Spring `/health` returned `status=ok`, `syntheticOnly=true`, `auditHashChainValid=true`, and `migrationTarget=kotlin-spring-boot`.
- `COMPOSE_PROJECT_NAME=banking-lab-passkey-manual docker compose --profile platform ps postgres keycloak core-banking` showed all three services running, with Postgres healthy and ports `15477`, `18127`, and `18126` published.
- The first sandboxed `npm run passkey:evidence:live-readiness` attempt failed because Node `fetch` could not reach loopback endpoints from the sandbox. The same command passed under the approved execution path against the live local endpoints.
- The first sandboxed staff-terminal dev-server start failed because the sandbox could not bind `0.0.0.0:3002`. The approved execution path started `http://localhost:3002` with `NEXT_PUBLIC_BANKING_SIMULATOR_TOKENS_ENABLED=false`.
- Browser inspection of `http://localhost:3002` showed `simulator token smoke disabled` for the simulator-token inquiry/change/unmask panels and exactly one enabled `Sign in WebAuthn manager with Keycloak` button.

This is platform-readiness evidence only. It does not create the generated passkey evidence artifact, does not assert `manual-live-passkey`, and must not be used to mark `non-synthetic-passkey-operations` passed.

## Manual Runbook Boundary

The future non-synthetic run should use the same Compose stack shape as the existing WebAuthn smoke, but the browser interaction must be manual or use only ordinary browser automation that does not install a virtual authenticator. The operator should sign in as `manager-webauthn01`, complete Keycloak passkey registration with a real authenticator, return to `staff-terminal`, and confirm the staff panel shows `Keycloak WebAuthn manager loaded`, `manager-webauthn01`, `Bearer`, `SYN-CUS-001`, masked phone output, and an `AUD-...` audit event ID.

After the run, replace the prepared command template with redacted passing command evidence and replace the staff panel template with the relevant redacted staff panel text. Each command evidence item must include `command`, `status: "pass"`, `exitCode: 0`, and a non-empty `summary`; duplicate commands and failed extra commands are rejected. The command evidence must include the live Compose startup with `BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false`, Keycloak discovery readiness, Spring `/health` readiness, a redacted manual real-authenticator sign-in attestation, and `npm run passkey:evidence:record`. Then run the prepared `tmp/passkey-evidence-manual/record-command.template.sh` command or this equivalent command:

```bash
env BANKING_LAB_PASSKEY_EVIDENCE_CONFIRMED=true \
  BANKING_LAB_PASSKEY_AUTHENTICATOR_KIND=platform \
  BANKING_LAB_PASSKEY_TEST_DATE=YYYY-MM-DD \
  BANKING_LAB_PASSKEY_BROWSER_ORIGIN=http://localhost:3002 \
  BANKING_LAB_PASSKEY_KEYCLOAK_ISSUER=http://localhost:18127/realms/banking-lab \
  BANKING_LAB_PASSKEY_RP_ID=localhost \
  BANKING_LAB_PASSKEY_USERNAME=manager-webauthn01 \
  BANKING_LAB_PASSKEY_AUTHORIZATION_FLOW=authorization-code-pkce \
  BANKING_LAB_PASSKEY_BROWSER_AUTOMATION=ordinary-browser-no-virtual-authenticator \
  BANKING_LAB_PASSKEY_OPERATOR_CONFIRMATION=real-platform-or-hardware-authenticator-used \
  BANKING_LAB_PASSKEY_USED_BROWSER_VIRTUAL_AUTHENTICATOR=false \
  BANKING_LAB_PASSKEY_USED_PLAYWRIGHT_CDP_WEBAUTHN=false \
  BANKING_LAB_PASSKEY_SIMULATOR_TOKENS_ENABLED=false \
  BANKING_LAB_PASSKEY_KEYCLOAK_REQUIRED_ACTION_COMPLETED=true \
  BANKING_LAB_PASSKEY_SPRING_SIGNED_TOKEN_ACCEPTED=true \
  BANKING_LAB_PASSKEY_SYNTHETIC_ONLY=true \
  BANKING_LAB_PASSKEY_REDACTION_CONFIRMED=true \
  BANKING_LAB_PASSKEY_COMMANDS_FILE=/path/to/redacted-commands.json \
  BANKING_LAB_PASSKEY_PANEL_SNAPSHOT_FILE=/path/to/redacted-staff-panel.txt \
  npm run passkey:evidence:record
```

The recorder writes `docs/test-evidence/generated/passkey-non-synthetic-evidence.json` only after confirming the run did not use virtual/CDP WebAuthn, simulator tokens were disabled, the browser origin was an `http://localhost` staff-terminal origin, the Keycloak issuer was an `http://localhost` `banking-lab` realm issuer, the RP ID was `localhost`, the synthetic WebAuthn user was `manager-webauthn01`, the flow used Authorization Code + PKCE, `BANKING_LAB_PASSKEY_BROWSER_AUTOMATION=ordinary-browser-no-virtual-authenticator`, `BANKING_LAB_PASSKEY_OPERATOR_CONFIRMATION=real-platform-or-hardware-authenticator-used`, the panel shows `Keycloak WebAuthn manager loaded`, `manager-webauthn01`, `Bearer`, `SYN-CUS-001`, masked phone output, and an `AUD-...` audit event, and the inputs do not contain obvious reusable tokens, cookies, passwords, credential IDs, attestation objects, or unmasked phone output.

After artifact generation, keep this document aligned with the exact commands and attach the generated JSON artifact above. Keep all screenshots and logs redacted before committing.

Then verify the generated artifact before asking for final Node retirement review:

```bash
npm run passkey:evidence:verify
```

This strict verifier fails when `docs/test-evidence/generated/passkey-non-synthetic-evidence.json` is missing, malformed, marked virtual/CDP/simulated, created with simulator tokens, missing required staff-panel assertions, missing passing command evidence, carrying duplicate or failed command evidence, or carrying obvious reusable tokens, cookies, credential IDs, attestation objects, passwords, or unmasked synthetic phone output.

## Controls

- PII masking remains default for staff customer detail.
- Business reason is required for the staff lookup and produces audit evidence.
- Simulator token fallback stays disabled for the live Spring validation path.
- Recovery role segregation prevents a passkey recovery admin from acting as customer, branch staff, or branch manager.
- The evidence remains synthetic-only and must not include real customer data, real money, real KYC, or real financial network data.

## Retirement Impact

Node retirement is now ready for the current synthetic lab scope because this evidence is proven and the final retirement review confirms no critical current behavior depends on Node-only code. Keep rerunning the verifier before future retirement-gate claims.
