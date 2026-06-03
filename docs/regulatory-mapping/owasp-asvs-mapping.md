# OWASP ASVS Mapping

This mapping is scoped to the current synthetic local lab. It is not a certification claim.

| ASVS area | Current lab control | Evidence |
| --- | --- | --- |
| V1 Architecture | service boundaries, manifest-driven screens, ADRs | `docs/architecture`, `docs/adr` |
| V2 Authentication | mock role-shaped sessions plus Spring signed JWT/JWKS Bearer token enforcement with simulator fallback coverage and live Keycloak realm smoke | `packages/banking-domain/src/auth.mjs`, Phase 1 tests, `SecurityAuthorizationIntegrationTest`, `JwksAuthorizationIntegrationTest`, `LiveKeycloakRealmIntegrationTest` |
| V3 Session Management | local mock session id only | `loginMockUser` |
| V4 Access Control | role declarations in screen manifests, Spring route role gates, customer ownership check, manager-only approval policy | `screen-manifests`, manifest validation, `SecurityAuthorizationIntegrationTest` |
| V5 Validation | reusable form validation helpers and ledger command validation | `packages/form-engine`, ledger tests |
| V7 Error Handling | API errors return structured JSON | runtime tests |
| V8 Data Protection | masked PII by default, unmask reason and role gate | staff terminal tests |
| V10 Malicious Code | no third-party runtime service integration | synthetic-only boundary |
| V11 Business Logic | double-entry ledger, idempotency, reversal, maker-checker | Phase 2/3/6 tests |
| V12 Files | attachment fields are modeled but not stored | Phase 5 residual risk |
| V13 API | HTTP APIs for customer, staff, complaint, risk, ops | runtime tests |
| V14 Configuration | Docker Compose config validates local runtime; security evidence wrapper records SCA/SAST/container/SBOM/DAST status; Spring HTTP, all-current-case Temporal signal/completion workflow trace/log correlation, and live Temporal rejection/self-approval failure trace/log correlation are proven through OTLP trace export to Tempo; Temporal workflow failure logs are ingested by Loki with a firing local ruler alert and provisioned Grafana dashboard | `docker compose config`, `npm run security:evidence`, `docs/test-evidence/opentelemetry-trace-log-correlation.md`, `docs/test-evidence/temporal-workflow-trace-log-correlation.md`, `docs/test-evidence/observability-stack-smoke.md` |

## Residual ASVS Gaps

- Signed JWT/JWKS validation is proven locally, and live Keycloak realm import, token propagation, and TOTP required-action blocking are proven through `LiveKeycloakRealmIntegrationTest`.
- Browser-based channel login propagation, a synthetic WebAuthn required-action completion smoke, explicit local WebAuthn policy, and passkey recovery role segregation are proven for current slices; non-synthetic passkey operations are not claimed.
- No persistent session store.
- No encrypted database persistence.
- SCA/SAST/container/SBOM/DAST evidence is present through `npm audit --audit-level=high`, Semgrep, Trivy filesystem scan, CycloneDX SBOM generation, and ZAP baseline against a live local synthetic Spring target.
- No production-grade rate limiting or WAF simulation.
