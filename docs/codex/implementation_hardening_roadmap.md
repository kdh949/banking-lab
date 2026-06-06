# Codex Implementation Instructions — Banking Lab Hardening Roadmap

You are a senior financial-systems engineer assigned to the `banking-lab` repository.

This is the **companion work order** to `docs/codex/implementation_loan_card_controls.md` (the "domains" doc: Loan, Card, controls, ops-evidence). This document is the **hardening roadmap**, derived from a maturity assessment of the current code.

**Sequencing decision: this hardening roadmap (H1–H8) is executed BEFORE the Loan/Card domains in the companion doc.** Rationale from the assessment: lock the Spring-canonical core and shrink the Node reference first, then strengthen operational security, ledger DB integrity, and resilience — only then add new business domains.

## 0. Maturity baseline (why this roadmap exists)

Independent review scored the repo: **synthetic banking lab / portfolio ≈ 70–75 / 100**, **regulated-bank production ≈ 25–35 / 100**. The core ledger and internal controls are strong (double-entry, idempotency, reversal, balance projection, closed-day guard, audit hash chain, maker-checker). The production-shaped gaps are: canonical-stack ambiguity (Node + Spring coexist), security defaults still lab-grade (simulator tokens, security-off default), DB-level ledger integrity, HA/DR proof, operational security, AML/FDS depth, data platform, and regulatory artifact automation.

This roadmap raises those controls **within the synthetic boundary**. It does not turn the lab into a real bank.

## 0.1 Absolute principles (unchanged, non-negotiable)

1. No real funds, real PII, real financial networks, real KYC, or real external bank APIs — ever. Everything synthetic / simulated.
2. **Every "real-world" item in the assessment is implemented as a lab-grade simulator with realistic patterns, not a real integration.** "Sanctions list", "SIEM", "HSM/KMS", "PAM", "WORM storage", "regulatory report" all mean synthetic, in-repo simulations.
3. Target stack only: Kotlin/Spring Boot + PostgreSQL/Flyway + Next.js/TS + Keycloak + Temporal + Redpanda/Outbox + OpenTelemetry. The Node runtime under `legacy-node-reference/` stays a retired reference oracle.
4. Do not break existing gates (retirement, parity, security-evidence, passkey, Keycloak, Temporal, outbox, formal, evidence-refresh). Update `docs/implementation-coverage-matrix.md` + `docs/test-evidence/evidence-gap-report.md` after each phase.
5. Reuse the existing patterns documented in §1 of the companion doc (`LedgerCommandService`, `PersistentApprovalService`, `AuditEventAppender`, the request-table pattern, the effective-dated parameter pattern, Temporal case workflows, structured errors). New Flyway migrations continue the monotonic sequence after whatever head exists at the time (companion doc reserves V020+; coordinate numbering — do not collide).

## 0.2 Scope of this roadmap

In scope: H1 Spring-canonical lock & Node reduction · H2 security-on & auth hardening · H3 DB-level ledger integrity · H4 HA/DR proof · H5 operational-security lab · H6 AML/FDS depth + sanctions-sim + STR/regulatory reporting · H7 data platform · H8 regulatory & governance artifact automation.

**Out of scope (user-confirmed):** payment-network connectivity simulators — ISO 20022 (pain/pacs), KFTC open-banking, inter-bank settlement/clearing message exchange, SWIFT. Only the **AML/sanctions + regulatory-reporting** slice of the "external" gap is included (H6). Internal reconciliation already exists and stays.

---

## H1 — Lock Spring Boot as canonical; reduce the Node reference

**Gap:** Node `.mjs` runtime and Spring Boot core coexist; the canonical money path must be unambiguous.

**Implement:**
- Make `services/core-banking` (Spring) the **single source of truth** for ledger/account/transfer/closing. Confirm every channel (`apps/*`) command/read path targets Spring REST endpoints (`LedgerController` etc.), not the Node `runtime/server.mjs` path. Migrate any remaining target-path reliance on Node to Spring.
- Demote `legacy-node-reference/` to **oracle-only**: used by parity tests as a regression comparator, never as a runtime dependency of the target stack. Add a guard (lint/CI check) that fails if target code imports or calls the Node runtime.
- Strengthen `npm run node:retirement-gate` to assert "no target-path dependency on Node" and keep the parity suite (`docs/migration/parity-scenarios.json`) green as the regression oracle.

**Verify:** `npm run node:retirement-gate` reports ready; parity suite green; each `npm run next:<app>:build` resolves against Spring contracts; CI guard rejects a planted Node import from target code.

---

## H2 — Security on by default; production-shaped auth (synthetic)

**Gap:** `BANKING_LAB_SECURITY_ENABLED` defaults false and simulator token is default-true in compose; `KeycloakSimulatorTokenDecoder` coexists with `SignedJwtJwksTokenDecoder`.

**Implement:**
- Flip defaults: **security enabled by default**; **simulator token disabled by default**, allowed only behind an explicit `BANKING_LAB_DEV_SIMULATOR_TOKEN=true` opt-in used solely by local tests. Production-shaped profile requires signed JWT validated via JWKS against Keycloak.
- Enforce MFA/passkey on high-risk flows using the already-configured Keycloak TOTP + WebAuthn required-actions; add **step-up re-authentication** for privileged staff/ops commands (PII unmask, approvals, parameter changes, EOD).
- Add **trusted-device binding** (`trusted_devices` table: customer/staff, device fingerprint, status, registered_at) and enforce on customer transfer + staff login. Add session timeout + forced-logout + session revocation.
- Update `docker-compose.yml`, k8s configmaps, and docs to reflect secure defaults; provide a clearly-named test profile so existing suites still run.

**Tests:** request without valid signed JWT rejected when security on; simulator token refused unless dev opt-in set; high-risk command without step-up rejected (`AUTHORIZATION_POLICY_VIOLATION` / new `STEP_UP_REQUIRED`); unknown device blocked until bound. **Regression watch:** ensure the full existing suite passes with secure defaults (gate simulator paths behind the dev flag rather than deleting them).

---

## H3 — DB-level ledger integrity & accounting structure

**Gap:** balance==Σpostings and Σpostings==0 are enforced in the service layer; not at the DB. No formal chart of accounts / clearing / suspense; no partitioning or archival policy.

**Implement:**
- **DB-enforced double-entry:** a deferred-constraint trigger (or constraint trigger) that validates, at transaction commit, `Σ postings == 0 per currency` and `≥ 2 postings` for each `ledger_transaction`. This complements `requireBalanced()` so a direct/erroneous SQL insert cannot leave the ledger unbalanced. New migration (coordinate number with companion doc).
- **Chart of accounts:** add `account_class` (ASSET / LIABILITY / EQUITY / INCOME / EXPENSE) to accounts; seed system **clearing**, **suspense**, **settlement**, and **interest/fee income/expense** accounts. Route interest/fee/loan/card postings through the correct system accounts.
- **Partitioning & retention:** partition `ledger_postings` / `ledger_transactions` by `business_date` (range), document an archival/retention policy, and add an initial partition migration + routing.

**Tests:** direct SQL insert of an unbalanced posting set is rejected by the DB trigger; system clearing/suspense accounts net to expected after a synthetic settlement cycle; partition routing places rows in the correct partition; existing ledger invariants still hold.

---

## H4 — HA/DR proof (lab-grade, executed)

**Gap:** Compose + structural validation only; no multi-instance / failover / measured RTO-RPO evidence.

**Implement & run:**
- Run **multiple core-banking instances** (compose scale or k8s replicas) behind the gateway; prove idempotency + `pg_advisory_xact_lock` + `SERIALIZABLE` correctness **across instances** (cross-instance concurrent-withdrawal / double-spend test).
- **Fault-injection drills:** kill an instance mid-traffic, Postgres restart/failover-sim, Redpanda lag, Temporal worker restart — assert no lost committed transaction, no ledger imbalance. Extend `docs/failure-drills/`.
- **Live backup/restore:** `npm run postgres:backup-drill -- --mode=live` against disposable Postgres; verify ledger count, balance invariant, audit hash-chain continuity, approval/workflow/transfer-result parity.
- **RTO/RPO evidence doc** with target vs measured.

**Verify:** cross-instance concurrency test green; drill evidence docs added; backup-drill records `postgresLive=true`; RTO/RPO doc present. If a tool is unavailable, record the exact command + reason + the structural check run instead — never mark a non-run as passed.

---

## H5 — Operational-security lab (synthetic SIEM / KMS / PAM / WORM)

**Gap:** audit + hash chain exist, but no immutable export, SIEM pipeline, secret rotation, key-management, or privileged-access controls.

**Implement (all synthetic/lab-grade):**
- **WORM/immutable audit export:** periodic append-only export of `audit_events` with a hash anchor; tamper-detection check that fails if a prior segment is altered.
- **SIEM pipeline:** ship audit/security logs to Loki (already provisioned); add alert rules for failed-auth bursts, privilege changes, mass-PII access, break-glass usage (extend `infra/observability/`).
- **Secret rotation:** abstraction for Keycloak client secrets + DB creds via env/secret with a documented rotation procedure; no secrets in code.
- **KMS/HSM-sim:** a key-management abstraction for signing/tokenization keys (used by audit anchoring and card-PAN tokenization later) with rotation; keys synthetic.
- **PAM / break-glass:** time-boxed elevated operator role granting requiring a mandatory post-hoc review case (reuse approval + case patterns).

**Tests:** tamper on an exported audit segment is detected; a simulated attack pattern fires the Loki alert; break-glass elevation auto-opens a post-review case and expires on schedule; rotation procedure swaps a key without breaking signature verification.

---

## H6 — AML/FDS depth: sanctions-sim, rule/model governance, STR & regulatory reporting

**Gap:** AML/FDS are rule simulations; no sanctions screening, rule versioning, model governance, case evidence, or regulatory reporting. (This is the **AML/reporting slice only** of the external gap — no payment-network connectivity.)

**Implement (synthetic):**
- **Sanctions/PEP screening-sim:** a synthetic watchlist + screening service over customers and transfers, producing AML hits with disposition. No real sanctions data.
- **Rule & model governance:** a rule/version registry (ties into the companion doc's `fds_rule_parameters` — coordinate, don't duplicate); for the `analytics/aml-fds-python` DuckDB scoring, add a **model card** (version, features, score distribution, drift check, explainability notes) and scoring lineage.
- **Case evidence + STR/regulatory reporting:** package FDS/AML case evidence; generate a synthetic **STR (Suspicious Transaction Report)** and a periodic AML/regulatory report artifact (synthetic regulator format) into `docs/test-evidence/generated/`. Add a false-positive disposition workflow.

**Tests:** a synthetic sanctioned party is flagged on onboarding/transfer; a rule/model version change is auditable and reproducible; STR + regulatory report artifacts are generated and schema-validated; false-positive disposition recorded with reason + approver.

---

## H7 — Data platform (analytical: DW/lakehouse, lineage, DQ)

**Gap:** OLTP-centric; no analytical layer, lineage, or data-quality gates.

**Implement (extend `analytics/`):**
- **Marts:** synthetic ELT from OLTP (Postgres) into DuckDB/parquet marts for risk/finance reporting (build on the existing DuckDB analytics).
- **Lineage:** capture source → mart → report lineage so any report field is traceable (BCBS 239-style, synthetic).
- **Data quality:** DQ checks (null/dup/range, and a ledger-reconciliation check that a balance mart equals the ledger projection); a DQ gate that fails on injected bad data.
- **Risk reporting:** at least one reproducible synthetic risk report (e.g., liquidity/exposure) generated from the marts.

**Tests:** balance mart reconciles to ledger; DQ gate fails on injected dirty data; lineage resolves for a chosen report field; risk report is reproducible for fixed inputs.

---

## H8 — Regulatory & governance artifact automation

**Gap:** evidence is partly manual/document-only; production posture needs automated governance artifacts.

**Implement (generators + drills, synthetic):**
- **Access-rights review:** generator listing who holds which role, last-reviewed date, and over-privilege flags.
- **Deployment-approval evidence:** capture approval + change record per release.
- **Incident response:** runbook + a drill log artifact (tie to H4 drills).
- **Vulnerability-remediation tracker:** derive from `npm run security:evidence` outputs (npm audit / Semgrep / Trivy / SBOM) with status over time.
- **Regulatory mapping refresh:** keep `docs/regulatory-mapping/` (전자금융감독규정 / OWASP ASVS) current with the new controls from H1–H7.

**Verify:** each generator produces an artifact under `docs/test-evidence/` or `docs/regulatory-mapping/`; `npm run evidence:refresh-check` green; mapping references real, implemented controls only.

---

## Sequencing & relationship to the companion doc

1. **H1–H5 first** (canonical lock, security, DB integrity, HA/DR, ops-security) — these de-risk everything else.
2. **H6–H8** (AML depth, data platform, governance artifacts) — can run in parallel once H1–H3 are stable.
3. **Then** the companion doc's domains: note that companion Phase 1 (limit/hold enforcement + EOD) is a prerequisite for Card; H3's clearing/suspense accounts and H2's auth hardening should be in place before Loan/Card money paths land.
4. Coordinate Flyway numbering and the `fds_rule_parameters` work across both docs to avoid collisions/duplication.

## Definition of Done (per phase) & gates

For each H-phase: new tests pass; baseline gates still pass; `docs/implementation-coverage-matrix.md` + `docs/test-evidence/evidence-gap-report.md` updated; a phase evidence doc added; synthetic-only boundary preserved; commit per phase.

Baseline / backend / frontend / controls command lists are identical to the companion doc §8 — reuse them. New scripts this roadmap may add (mark as new): `security:posture-check` (H2 secure-defaults assertion), `ledger:integrity-check` (H3 DB trigger test harness), `dr:multi-instance-drill` (H4), `siem:alert-drill` (H5), `aml:sanctions-scenario` / `aml:str-report` (H6), `data:dq-check` (H7), `governance:evidence` (H8).

**Quality gates that must never regress:** append-only ledger; DB-enforced + service-enforced double-entry; maker ≠ checker; structured errors; audit on every sensitive view/change; idempotent commands; no raw PAN / real PII / real network / real sanctions data; Node stays retired; secure defaults stay on (simulator paths only behind the explicit dev flag).

## Final response must include

1. Phases implemented (H-id). 2. Key files changed. 3. New migrations. 4. New/changed APIs and security defaults. 5. New simulators (sanctions/SIEM/KMS/PAM/WORM) and their synthetic boundary note. 6. New tests + generated evidence paths. 7. Commands run + results. 8. Commands not run + exact reason. 9. Remaining limitations. 10. Recommended next slice.

Keep the synthetic-lab disclaimer:

> Implemented and verified production-shaped banking controls — security, ledger integrity, resilience, AML, data governance — as lab-grade simulations in a synthetic environment that uses no real financial network and no real customer data.
