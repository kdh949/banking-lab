# Codex Implementation Instructions — Banking Lab Round 2 (Loan, Card, Controls, Ops)

You are a senior financial-systems engineer assigned to the `banking-lab` repository.

This document is the **authoritative work order** for the next implementation round. It assumes the prior round (`docs/codex/implementation_missing_features_goals.md`, Phases A–G) is already landed and reflected in `docs/implementation-coverage-matrix.md`. Do **not** redo completed work; build on it.

This repository is a **synthetic core-banking lab**. It never touches real customer money, real PII, real payment networks, real KYC, or real external bank APIs. Everything is synthetic data and simulators. Keep that boundary in every artifact you produce.

---

## 0. Absolute principles (do not violate)

1. No real funds, real PII, real financial networks, real KYC, or real external bank APIs. Synthetic data and simulators only.
2. The Node.js runtime under `legacy-node-reference/` is a **retired reference oracle**, not a target-path dependency. Never reintroduce it as a runtime dependency of the target stack.
3. All new business logic is written on the target stack only: **Kotlin/Spring Boot + PostgreSQL/Flyway + Next.js/TypeScript + Keycloak + Temporal + Redpanda/Outbox + OpenTelemetry**.
4. Do not break existing gates: retirement (`npm run node:retirement-gate`), parity, security evidence, passkey, Keycloak, Temporal, outbox, formal, or evidence-refresh.
5. Every high-risk command must have: **maker-checker approval, append-only audit, structured error, idempotency, authorization policy**.
6. Every money movement is a **balanced double-entry ledger transaction**. The ledger source rows are append-only — corrections happen via reversal or adjustment, never UPDATE/DELETE. Respect the closed-business-date guard.
7. Every new screen must declare exactly how far it is implemented: manifest → Next UI → api-client → Spring API → PostgreSQL → authZ → audit → maker-checker → test → evidence.
8. Update documentation and evidence after every phase. Never mark a non-executed test as passed.

Mandatory disclaimer to keep in all summaries:

> Implemented and verified the core controls and operating patterns of a banking system in a synthetic lab environment that uses no real financial network and no real customer data.

---

## 1. Reuse these existing patterns (read before coding)

Do **not** invent new structures where one already exists. Read and mirror:

- **Ledger command pipeline** — `services/core-banking/src/main/kotlin/lab/banking/core/ledger/application/LedgerCommandService.kt`. Note: command types (`DEPOSIT`, `WITHDRAWAL`, `INTERNAL_TRANSFER`, `REVERSAL`, `ADJUSTMENT`, `INTEREST`, `FEE`), `SERIALIZABLE` isolation, `pg_advisory_xact_lock` for idempotency serialization, the idempotency store with command-hash matching, and `requireBalanced()`.
- **Ledger domain + invariants** — `.../ledger/domain/LedgerModels.kt`, `.../ledger/domain/LedgerInvariants.kt`.
- **Maker-checker** — `.../approval/PersistentApprovalService.kt`, `.../approval/ApprovalController.kt` (maker ≠ checker, self-approval rejected, business_type + business_reference_id match).
- **Audit hash chain** — `.../audit/AuditEventAppender.kt`, `.../audit/AuditEventService.kt`.
- **Staff request-table pattern** — tables such as `account_hold_requests`, `transaction_correction_requests`; service `.../staff/StaffAccessService.kt`. Standard request columns: `request_id, business_reference_id, target_customer_id, target_account_id (nullable), target_transaction_id (nullable), requested_by, requested_role, reason, status, approval_id (nullable), created_at, updated_at, executed_at (nullable), metadata_json`.
- **Effective-dated parameter + posting-batch product pattern** — `.../product/DepositProductService.kt`, `.../product/FeePolicyService.kt`; migrations `db/migrations/V018__deposit_products_interest.sql`, `db/migrations/V019__fee_policies_posting.sql`. Note the `*_versions` tables (effective_from/to + approval_id) and the posting-batch tables that emit one balanced ledger transaction per batch.
- **Temporal case workflow** — `.../temporal/BankingCaseTemporalWorkflow.kt`, `.../temporal/BankingCaseTemporalWorker.kt`.
- **Durable outbox** — `.../eventing/DurableOutboxService.kt`, `.../eventing/KafkaOutboxPublisher.kt`.
- **Structured errors** — `.../api/StructuredApiError.kt` (e.g. `AUTHORIZATION_POLICY_VIOLATION`, `WORKFLOW_STATE_VIOLATION`, `LEDGER_REVERSAL_POLICY_VIOLATION`). Add new codes here.
- **Screen manifests** — `screen-manifests/<channel>/...`, validated by `npm run validate:manifests`. Renderer types in `packages/screen-engine/src/`.
- **Tests** — unit `services/core-banking/src/test/kotlin/...`, integration (Testcontainers + PostgreSQL) `services/core-banking/src/integrationTest/kotlin/...`, Playwright `apps/*/e2e/`.
- **Migrations** — `db/migrations/`. Current head is **V019**. New migrations start at **V020** and increment monotonically.

Per-feature vertical slice (apply to every feature below):

```
screen manifest
 → Next.js channel panel
 → shared TypeScript api-client method
 → Spring Controller
 → Spring Service
 → Flyway migration / table
 → audit_events append
 → maker-checker approval (if high-risk)
 → structured error
 → integration test (Testcontainers)
 → Playwright smoke (if a screen exists)
 → coverage-matrix + evidence update
```

---

## 2. Scope of this round

In scope (build these): **Loan domain, Card domain, account-control depth (limit enforcement, EOD automation, statement/certificate), ops/admin depth (parameter-admin APIs, operational evidence hardening).**

Explicitly **out of scope** this round (do not build): external connectivity simulators — ISO 20022 (pain/pacs), KFTC open-banking, PG, SWIFT, and inter-bank settlement file exchange. Internal reconciliation already exists and stays.

Phase order is dependency-ordered. Phase 1 (limits + holds + EOD) is foundational for Phases 2–3.

---

## 3. Phase 1 — Account-control depth

### 1A. Limit enforcement at posting time

**Gap:** `account_limits` (V001) and `account_limit_change_requests` (V014) exist and staff can change limits, but `LedgerCommandService` never checks limits when posting. Limits are currently decorative.

**Implement:**
- Add a per-channel cumulative usage counter table: `V020__limit_usage_counters.sql` →
  `limit_usage_counters(account_id, channel, business_date, period_kind, used_amount_minor, version, updated_at)` where `period_kind ∈ {DAILY, MONTHLY}`. Unique on `(account_id, channel, period_kind, business_date_or_month)`.
- In the withdrawal / internal-transfer / customer-transfer command paths inside `LedgerCommandService`, evaluate within the same `SERIALIZABLE` transaction as the posting:
  - per-transaction limit, daily cumulative, monthly cumulative, per-channel limit (read from `account_limits`).
  - increment `limit_usage_counters` atomically (use the same row-lock discipline already used for balances).
- On breach, throw a new structured error `LIMIT_EXCEEDED` carrying `{ limitKind, channel, configuredMinor, attemptedMinor, remainingMinor }`. No posting, no side effect.
- On reversal of a counted transaction, **release** the used amount from the counter.

**Tests (integration):** over-limit withdrawal rejected with no ledger mutation; cumulative breach across several transactions; counter resets on the next business date / month; concurrent burst (extend the existing 120-thread concurrency test style in `LedgerCommandServiceIntegrationTest.kt`) never exceeds the configured daily limit; reversal releases the counter so a subsequent transaction succeeds.

**Invariants to add:** `used_amount(account, channel, period) == Σ posted debits in that period`; `used_amount ≤ configured limit` always. Add these to `formal/Ledger.tla` if feasible; otherwise document why not.

### 1B. End-of-day (EOD) closing pipeline

**Gap:** `daily_closings` is date-level only; the `OPS-101` daily-closing monitor has a close command but **no GET read route**; interest accrual, fee posting, and reconciliation are separate manual batches.

**Implement:**
- A Temporal workflow `EndOfDayClosingWorkflow` (follow `BankingCaseTemporalWorkflow.kt`) orchestrating, idempotently per `business_date`: interest accrual run → interest posting batch → fee posting batch → reconciliation snapshot/match → mark `daily_closings` `CLOSED` with `ledger_total_hash`.
- `V021__eod_closing_steps.sql` → `eod_closing_steps(business_date, step, status, started_at, finished_at, result_json, version)` to back the monitor.
- API: `POST /api/ops/eod/close` (run, ops-operator maker + manager checker), `GET /api/ops/eod/{businessDate}` (monitor read model → fills the `OPS-101` GET gap). Wire `apps/ops-console` `OPS-101` to the GET.
- Re-running the same `business_date` is a no-op (idempotent). Emit outbox events + audit for each step.

**Tests:** full orchestration runs all steps in order; rerun of a closed date is idempotent; **worker-restart-mid-EOD drill** resumes to completion (add to `docs/failure-drills/`); posting into a closed date is rejected (already enforced — assert it still holds).

**Evidence:** `docs/test-evidence/eod-closing-pipeline.md`.

### 1C. Statement / certificate read models (CQRS)

**Gap:** No statement service (월명세서 / 거래확인증 / 잔액증명서). `CWB-401` security/access history has no customer read model.

**Implement (read-only projections — no new ledger writes):**
- `StatementService` building: period statement from `ledger_transactions` + `ledger_postings`; transfer confirmation (거래확인증) by transaction id; balance certificate (잔액증명서) at a date from balance projection / snapshot.
- API: `GET /api/customers/{id}/statements?from&to`, `GET /api/transactions/{id}/confirmation`, `GET /api/accounts/{id}/balance-certificate?date`. Enforce customer-ownership for customer tokens and staff role otherwise.
- Customer-web: extend `CWB-103` with statement download; add `CWB-401` access-history read model sourced from `audit_events` (own events only).

**Tests:** statement debit/credit sums reconcile to ledger postings for the period; certificate output is deterministic for fixed inputs; access-history returns only the requesting customer's events.

---

## 4. Phase 2 — Loan domain (`lab.banking.core.loan`)

**Gap:** Loans are entirely absent.

**Migrations (V022+):** `loan_products`, `loan_product_rate_versions` (effective-dated + approval_id), `loan_applications`, `loan_credit_assessments`, `loans`, `loan_repayment_schedules`, `loan_repayment_installments`, `loan_disbursements`, `loan_interest_accruals`, `loan_delinquencies`.

**Lifecycle:**
1. **Apply** — customer submits application (amount, term, product).
2. **Credit assessment** — deterministic **synthetic** score from synthetic income/credit features (reproducible; no real bureau). Store in `loan_credit_assessments`.
3. **Approval** — maker-checker on limit / rate / term via `PersistentApprovalService`.
4. **Agreement** — e-sign simulation (record consent + agreement hash; synthetic).
5. **Disbursement** — balanced ledger transaction via a new `DisburseLoanCommand` in `LedgerCommandService`: debit a loan-asset/clearing account, credit the customer deposit account. New posting types `LOAN_PRINCIPAL`, `LOAN_INTEREST`, `LOAN_REPAYMENT`. Idempotent.
6. **Schedule** — generate `loan_repayment_schedules` + installments. Support **원리금균등 (equal payment)** and **원금균등 (equal principal)**. Math must be reproducible.
7. **Repayment** — scheduled auto-debit via `LoanRepaymentCommand` (balanced posting reducing principal/interest).
8. **Delinquency** — missed installment triggers `loan_delinquencies` and 연체이자 (penalty interest) accrual.
9. **Early repayment** — 중도상환수수료 (prepayment fee) computed and posted.

Use a Temporal case workflow for application → assessment → approval → disbursement (mirror the complaint/FDS case pattern).

**Channels:** customer-web apply/status/repayment-schedule (`CWB-50x`); staff-terminal loan review/approve/disburse/delinquency (`LON-xxx`). Add manifests under `screen-manifests/customer-web/` and `screen-manifests/staff-terminal/`.

**Tests (integration):** schedule math reproducible for both methods; disbursement transaction is balanced; approval gating (no disbursement before approval); repayment reduces outstanding principal; delinquency produces penalty accrual; early-repayment fee computed and posted; disbursement is idempotent under retried idempotency key; closed-day guard holds.

**Evidence:** `docs/test-evidence/loan-domain.md` + a reconciliation report under `docs/reconciliation-reports/`.

---

## 5. Phase 3 — Card domain (`lab.banking.core.card`)

**Gap:** Cards are entirely absent. This phase **depends on Phase 1A/holds**.

**Migrations:** `card_products`, `cards` (store **tokenized PAN + last4 only**, plus expiry/status — never raw PAN), `card_authorizations`, `card_captures`, `card_limits`, `card_lifecycle_events`, optional `card_disputes`.

**Lifecycle:**
1. **Apply / issue** — synthetic card number tokenized; status `ISSUED`.
2. **Activate** — `ACTIVE`.
3. **Authorization** — places a **HOLD** on the funding deposit account using the existing `account_holds` / hold-amount machinery and the Phase-1 limit check. No ledger posting at auth time; available balance drops by the hold.
4. **Capture / 매입** — converts the hold into a real balanced ledger transaction (debit cardholder deposit, credit a card-settlement/clearing suspense account) and releases the hold.
5. **Reversal** — auth reversal releases the hold; capture reversal posts a balanced reversal.
6. **Limits** — 일시불 / 할부 / 현금서비스 limits in `card_limits`; reuse `LIMIT_EXCEEDED`.
7. **Lost/stolen** — blocks further authorizations.
8. **3DS-sim** — additional-verification flag on card-not-present authorizations (synthetic challenge result).
9. **Dispute/chargeback (optional)** — case workflow if time allows.

**PCI-sim note:** persist only tokenized PAN + last4; mark all card data synthetic. No raw card numbers anywhere.

**Channels:** customer-web card list/status/lost-report (`CWB-60x`); staff-terminal card issue/status/auth-history/lost (`CRD-xxx`). Card authorizations should feed the existing FDS path.

**Tests (integration):** authorization reduces available balance via hold; capture converts hold → balanced posting and releases the hold; auth reversal releases the hold; over-limit authorization rejected with `LIMIT_EXCEEDED`; lost card blocks authorization; authorization idempotent under retried key; concurrent authorizations never overspend the available balance/hold.

**Evidence:** `docs/test-evidence/card-domain.md`.

---

## 6. Phase 4 — Parameter-admin APIs (manifest-only → api-backed-command)

**Gap (from coverage matrix):** these screens are manifest-only with no API and missing parameter tables — `OPS-301` reconciliation parameters, `AUD-201` audit retention, `FDS-301` FDS rule parameters, `ADM-201` security policy, `ADM-301` menu/role. Also missing: `ACC-102`/`LED-102` detail GET routes, and `CMP-103`/`CMP-106`/`CMP-107`.

**Implement — generic effective-dated parameter pattern** (mirror `fee_policy_versions`): one `*_parameters` + `*_parameter_versions` pair per domain, with maker-checker, scheduled `effective_from`, rollback-as-new-version, and audit on both view and change:
- `reconciliation_parameters(_versions)`, `audit_retention_parameters(_versions)`, `fds_rule_parameters(_versions)`, `security_policy_parameters(_versions)`, `menu_role_parameters(_versions)`.
- API per domain: `GET /api/.../parameters` (current + scheduled) and `POST /api/.../parameter-change-requests` (maker) → approve via `PersistentApprovalService` → apply on/after `effective_from`.
- **FDS rule parameters must be wired into `FdsCaseService` detection thresholds** so a parameter change provably changes behaviour — not cosmetic.

**Also:** add `ACC-102`/`LED-102` account/ledger detail GET routes (read models over existing tables); `CMP-103` additional-materials upload, `CMP-106` reopen command, `CMP-107` type-guide (static read).

**Tests:** parameter change requires approval before taking effect; effective-date scheduling honored; rollback restores prior version; unauthorized actor rejected; an integration test proves changing an FDS threshold changes whether a given transfer is held.

**Coverage matrix:** flip each of these rows from `manifest-only`/`partial` to `api-backed-command` with the backing evidence.

---

## 7. Phase 5 — Operational evidence hardening

**Gap:** several controls are documented but not executed. Convert them to real, executed evidence. If a required tool is genuinely unavailable in the environment, record the exact command, the reason it could not run, and what structural check ran instead — **never record a non-run as a pass.**

1. **Live PostgreSQL backup/restore** — run `npm run postgres:backup-drill -- --mode=live` against a disposable Postgres (via docker compose). Verify after restore: ledger transaction count, balance invariant (`balance == Σ postings`), audit hash-chain continuity, and approval / workflow / `customer_transfer_results` parity. Update `docs/test-evidence/postgres-backup-restore-drill.md` with `postgresLive=true`.
2. **DAST** — stand up the synthetic lab over docker compose, run `BANKING_LAB_DAST_URL=<url> npm run security:evidence:docker`, and record the ZAP baseline result. Update the security evidence summary.
3. **Live Kubernetes deployment** — apply `infra/k8s` (or Helm install `infra/helm/banking-lab`) to a disposable kind/minikube cluster; confirm readiness/liveness probes green; smoke the in-cluster service; uninstall. Flip the platform "Live cluster deployment validation" row from `missing` to `complete`.
4. **Real TLC** — run actual `tlc` (local binary or a TLA+ Docker image) for `formal/Ledger.tla` and `formal/Idempotency.tla`; fail CI on any invariant violation; disallow static-only as a CI pass. Flip the formal row from `partial` to `complete`.
5. **FDS/AML analytics Spring read API** — expose `GET /api/fds/analytics` reading the generated DuckDB artifact so the FDS/AML console reads via API instead of a file. Flip the analytics row from `api-backed-read` to `complete`.

---

## 8. Definition of Done (per phase) and global gates

For **each** phase: new tests pass, baseline gates still pass, `docs/implementation-coverage-matrix.md` and `docs/test-evidence/evidence-gap-report.md` updated, a phase evidence doc added, and the synthetic-only boundary preserved. Commit per phase.

**Baseline (run before starting and after each phase, in available environments):**

```bash
npm ci
npm test
npm run validate:manifests
npm run test:screen-engine
npm run packages:typecheck
npm run scripts:typecheck
docker compose config
docker compose --profile platform config
```

**Backend changes:**

```bash
npm run test:core-banking
npm run test:core-banking:integration   # name the new suites, e.g. --tests lab.banking.core.loan.*
```

**Frontend changes:** `npm run next:<app>:typecheck` and `:build` for each touched app, then `npm run test:e2e`.

**Controls / evidence:**

```bash
npm run formal:ledger
npm run security:evidence
npm run evidence:refresh-check
npm run node:retirement-gate
npm run goal:completion-audit -- --require-complete
```

**New scripts to add this round** (mark clearly as new): `eod:drill` (Phase 1B), `loan:scenario` (Phase 2), `card:scenario` (Phase 3). Reuse existing `postgres:backup-drill`, `security:evidence:docker`, `k8s:validate`, `helm:template`, `analytics:fds-aml`.

**Quality gates that must never regress:** append-only ledger; double-entry balance; maker ≠ checker; structured errors on every new command; audit event on every sensitive view/change; idempotent commands produce no duplicate side effects; no raw PAN / real PII / real network; Node stays retired.

---

## 9. Final response must include

1. Features implemented (per phase). 2. Key files changed. 3. New Flyway migrations (V020+). 4. New APIs. 5. New screens/manifests. 6. New tests. 7. Commands run + results. 8. Commands **not** run + exact reason. 9. Remaining limitations. 10. Recommended next slice.

Do not overstate. Keep the synthetic-lab disclaimer from §0.
