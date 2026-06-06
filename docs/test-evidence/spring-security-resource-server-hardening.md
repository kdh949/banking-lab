# Spring Security Resource Server Hardening Evidence

Review date: 2026-06-06

Branch: `codex/remaining-hardening-phase-2-security`

Scope: Phase 2 of `docs/codex/remaining-hardening-goals.md`. This evidence covers the synthetic banking lab only. It does not add real customer money, real personal data, real financial-network integration, real card-network integration, Open Banking, or real KYC/provider calls.

## Implemented Controls

- Added Spring Security and OAuth2 Resource Server dependencies to `core-banking`, `payment-service`, `notification-service`, and `reporting-service`.
- Added stateless Spring `SecurityFilterChain` configuration in all four Spring services.
- Configured health endpoints and `OPTIONS` preflight as unauthenticated, while protecting service API routes through Spring Security when `banking-lab.security.enabled=true`.
- Added Resource Server `JwtDecoder` adapters that verify signed tokens through Spring/Nimbus JWKS support and validate configured issuer/audience claims.
- Preserved existing route-level RBAC/ABAC, customer ownership, step-up, trusted-device, session revocation, structured error, and audit-denial behavior by retaining existing authorization filters as policy/audit layers after Spring authentication.
- Kept simulator `lab.*.sig` tokens as explicit dev/test compatibility only. Simulator tokens require both `banking-lab.security.simulator-tokens-enabled=true` and `banking-lab.security.dev-simulator-token-enabled=true`.
- Added prod-like profile guards so simulator tokens fail startup when enabled with `prod`, `production`, `prod-like`, or `prodlike` profiles.
- Added method-level authorization with `@PreAuthorize` on selected high-risk core APIs: privileged PII unmask, account hold/release requests, transfer-limit change requests, transaction correction requests, approval approve/reject, ledger adjustment, daily closing, audit export, synthetic KMS rotation, break-glass request, and AML/FDS false-positive disposition.
- Extracted the core route-role matrix from `BankingLabAuthorizationFilter` into `BankingLabRouteAuthorizationManager`.

## Verification Results

| Command | Result | Notes |
| --- | --- | --- |
| `scripts/run-core-banking-tests.sh :services:core-banking:compileKotlin :services:payment-service:compileKotlin :services:notification-service:compileKotlin :services:reporting-service:compileKotlin` | sandbox failed, escalated pass | Sandbox failed with Gradle file-lock socket denial; approved rerun compiled all four Spring services. |
| `scripts/run-core-banking-tests.sh :services:core-banking:test --tests '*SpringSecurityResourceServerTest'` | pass | Verified claim-to-authority conversion and prod-like simulator-token profile guard. |
| `scripts/run-core-banking-tests.sh :services:core-banking:integrationTest --tests '*Security*' --tests '*Authorization*' --tests '*Jwks*'` | first run failed, rerun pass | First run exposed generic `JwtException` wrapping and raw epoch timestamp claim issues; rerun passed after switching invalid tokens to `BadJwtException` and normalizing registered JWT timestamps. |
| `scripts/run-core-banking-tests.sh :services:payment-service:integrationTest --tests '*Authorization*' :services:notification-service:integrationTest --tests '*Authorization*' :services:reporting-service:integrationTest` | pass | Verified bounded-context authorization tests still pass behind the Spring Security Resource Server filter chain. |
| `npm run scripts:typecheck` | pass | Verified TypeScript script project after documentation/status updates. |
| `npm test` | first run failed, rerun pass | First run exposed a scaffold test that still expected AML route roles in the old filter source; rerun passed after pointing it at `BankingLabRouteAuthorizationManager`. |
| `scripts/run-core-banking-tests.sh :services:core-banking:test :services:payment-service:test :services:notification-service:test :services:reporting-service:test` | first run failed, rerun pass | First run exposed default Spring Security in the `HealthController` MVC slice; rerun passed after adding explicit health/metadata route permits and a permit-all test filter chain for the MVC slice. |
| `scripts/run-core-banking-tests.sh :services:core-banking:integrationTest` | first run failed twice, rerun pass | First full run exposed method-security denials when `banking-lab.security.enabled=false` and non-web context loading of servlet security config; second run exposed an AML reviewer workflow role mismatch. Final rerun passed after property-aware method security bypass, servlet-only `SecurityConfig`, structured method-security error handling, and AML role alignment. |
| `scripts/run-core-banking-tests.sh :services:payment-service:integrationTest :services:notification-service:integrationTest :services:reporting-service:integrationTest` | pass | Full bounded-context integration tasks passed after the core suite was fixed. |

## Failure Handling Notes

- The first core integration run failed with `AuthenticationServiceException` because custom Resource Server decoders threw generic `JwtException`. The implementation now throws `BadJwtException` for invalid or rejected bearer tokens so Spring Resource Server converts them into structured 401 responses.
- The first core integration run also failed on simulator JWT timestamp claims. Simulator compatibility decoding now normalizes registered JWT timestamp claims (`iat`, `exp`, `nbf`) to `Instant`.
- The broader core integration run initially failed because method security was active even when tests deliberately set `banking-lab.security.enabled=false`; method gates now check `BankingLabMethodSecurityPolicy.securityDisabled()` before role checks.
- Non-web multi-instance resilience tests initially failed because servlet `SecurityConfig` was loaded in a `WebApplicationType.NONE` context; the config is now servlet-web-only.
- No failed command is recorded as passing.

## Remaining Phase 2 Limits

- Existing compatibility decoders remain in the codebase for fallback/dev-test behavior. The signed JWT operating path now goes through Spring/Nimbus, but complete removal of legacy decoder classes is intentionally deferred to avoid destabilizing existing tests.
- Method-level authorization is applied to selected high-risk controller entry points and follows the existing maker/checker workflow roles, for example AML reviewers may submit false-positive disposition commands while the service still requires an independent compliance checker in the command body. A complete annotation audit across every high-risk service method should remain a later tightening task.
- Payment, notification, and reporting services now use Spring Resource Server authentication and structured authorization envelopes, but they still do not append authorization-denied audit rows in every denial path. Bounded-context audit expansion remains assigned to later service hardening.
