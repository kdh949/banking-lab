# OWASP ASVS Mapping

This mapping is scoped to the current synthetic local lab. It is not a certification claim.

| ASVS area | Current lab control | Evidence |
| --- | --- | --- |
| V1 Architecture | service boundaries, manifest-driven screens, ADRs | `docs/architecture`, `docs/adr` |
| V2 Authentication | mock role-shaped sessions only | `packages/banking-domain/src/auth.mjs`, Phase 1 tests |
| V3 Session Management | local mock session id only | `loginMockUser` |
| V4 Access Control | role declarations in screen manifests | `screen-manifests`, manifest validation |
| V5 Validation | reusable form validation helpers and ledger command validation | `packages/form-engine`, ledger tests |
| V7 Error Handling | API errors return structured JSON | runtime tests |
| V8 Data Protection | masked PII by default, unmask reason and role gate | staff terminal tests |
| V10 Malicious Code | no third-party runtime service integration | synthetic-only boundary |
| V11 Business Logic | double-entry ledger, idempotency, reversal, maker-checker | Phase 2/3/6 tests |
| V12 Files | attachment fields are modeled but not stored | Phase 5 residual risk |
| V13 API | HTTP APIs for customer, staff, complaint, risk, ops | runtime tests |
| V14 Configuration | Docker Compose config validates local runtime | `docker compose config` |

## Residual ASVS Gaps

- No production OAuth2/OIDC provider.
- No MFA/WebAuthn implementation.
- No persistent session store.
- No encrypted database persistence.
- No SAST/SCA/SBOM automation yet.
- No production-grade rate limiting or WAF simulation.
