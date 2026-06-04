# OWASP ASVS Mapping

This mapping is scoped to the current synthetic local lab. It is not a certification claim.

| ASVS area | Current lab control | Evidence |
| --- | --- | --- |
| V1 Architecture | service boundaries, manifest-driven screens, ADRs | `docs/architecture`, `docs/adr` |
| V2 Authentication | Spring security defaults on, signed JWT/JWKS Bearer token enforcement, simulator tokens disabled unless both simulator and dev opt-ins are set, high-risk MFA/WebAuthn step-up checks, and live Keycloak realm smoke | `SecurityDefaultsIntegrationTest`, `JwksAuthorizationIntegrationTest`, `LiveKeycloakRealmIntegrationTest`, `docs/test-evidence/hardening-h2-secure-auth.md` |
| V3 Session Management | synthetic trusted-device registry, revoked-session registry, `/api/auth/session` validation, and pre-domain denial for missing/expired/revoked sessions on protected session paths | `V026__security_auth_hardening_controls.sql`, `JwksAuthorizationIntegrationTest`, `docs/test-evidence/generated/security-posture-check.json` |
| V4 Access Control | role declarations in screen manifests, Spring route role gates, customer ownership check, manager-only approval policy, high-risk command step-up/device/session policy, and H5 `/api/ops/security/*` role/step-up gates | `screen-manifests`, manifest validation, `SecurityAuthorizationIntegrationTest`, `JwksAuthorizationIntegrationTest`, `OperationalSecurityIntegrationTest` |
| V5 Validation | reusable form validation helpers and ledger command validation | `packages/form-engine`, ledger tests |
| V7 Error Handling | API errors return structured JSON | runtime tests |
| V8 Data Protection | masked PII by default, unmask reason and role gate, WORM-style synthetic audit export, and synthetic KMS key rotation without real key material | staff terminal tests, `OperationalSecurityIntegrationTest`, `docs/security/synthetic-secret-rotation-runbook.md` |
| V10 Malicious Code | no third-party runtime service integration | synthetic-only boundary |
| V11 Business Logic | service and DB-enforced double-entry ledger, idempotency, reversal, maker-checker, typed synthetic chart-of-accounts routing, and cross-instance ledger serialization/idempotency convergence | Phase 2/3/6 tests, `LedgerDatabaseIntegrityIntegrationTest`, `MultiInstanceLedgerHaDrIntegrationTest`, `docs/test-evidence/hardening-h3-ledger-db-integrity.md`, `docs/test-evidence/hardening-h4-ha-dr-proof.md` |
| V12 Files | attachment fields are modeled but not stored | Phase 5 residual risk |
| V13 API | HTTP APIs for customer, staff, complaint, risk, ops | runtime tests |
| V14 Configuration | Docker Compose config validates local runtime; security evidence wrapper records SCA/SAST/container/SBOM/DAST status; Spring HTTP, all-current-case Temporal signal/completion workflow trace/log correlation, and live Temporal rejection/self-approval failure trace/log correlation are proven through OTLP trace export to Tempo; Temporal workflow failure logs are ingested by Loki with a firing local ruler alert and provisioned Grafana dashboard; disposable PostgreSQL backup/restore and two-instance HA-shaped ledger drills record scoped RPO/RTO evidence; H5 adds local Loki operational-security alert rules and deterministic SIEM drill evidence | `docker compose config`, `npm run security:evidence`, `npm run postgres:backup-drill:docker-live`, `npm run dr:multi-instance-drill`, `npm run siem:alert-drill`, `docs/test-evidence/opentelemetry-trace-log-correlation.md`, `docs/test-evidence/temporal-workflow-trace-log-correlation.md`, `docs/test-evidence/observability-stack-smoke.md`, `docs/test-evidence/hardening-h4-ha-dr-proof.md`, `docs/test-evidence/hardening-h5-operational-security.md` |

## Residual ASVS Gaps

- Signed JWT/JWKS validation is proven locally, and live Keycloak realm import, token propagation, and TOTP required-action blocking are proven through `LiveKeycloakRealmIntegrationTest`.
- Browser-based channel login propagation, a synthetic WebAuthn required-action completion smoke, explicit local WebAuthn policy, and passkey recovery role segregation are proven for current slices; non-synthetic passkey operations are not claimed.
- Synthetic trusted-device and revoked-session tables exist; production-grade centralized session lifecycle, device intelligence, and adaptive risk controls are not claimed.
- No encrypted database persistence.
- SCA/SAST/container/SBOM/DAST evidence is present through `npm audit --audit-level=high`, Semgrep, Trivy filesystem scan, CycloneDX SBOM generation, and ZAP baseline against a live local synthetic Spring target.
- No production-grade rate limiting or WAF simulation.
