# RFP Functional Requirement Gap Analysis

Review date: 2026-06-12

Scope: four supplied RFP/Tender PDFs, the current git-tracked Banking Lab target-stack codebase, and repository governance rules in `PLAN.md`, `AGENTS.md`, and `BANKING_LAB_CODEX_PROMPT.md`.

This analysis preserves the project boundary: the system is a synthetic banking lab only. It must not integrate real customer money, real PII, real KYC, real sanctions/credit/payment providers, real payment networks, or real financial institution APIs.

## Source Material Read

| Source | Extracted text | Size | Primary focus |
| --- | ---: | ---: | --- |
| `RFP-FOR-CBS-CitizencreditBank.pdf` | `/tmp/banking-lab-rfp-analysis/citizencredit-cbs.txt` | 126 PDF pages, 5,410 extracted lines | Cooperative-bank CBS replacement: deposits, loans, GL, teller, clearing, channels, reports, interfaces, security, migration |
| `2. 여신사후관리시스템구축_제안요청서.pdf` | `/tmp/banking-lab-rfp-analysis/loan-aftercare-kr.txt` | 43 PDF pages, 1,536 extracted lines | Loan aftercare: delinquency, collections, legal actions, write-off, sale, restructuring, debtor protection notices |
| `우체국금융-차세대시스템-설계사업2.pdf` | `/tmp/banking-lab-rfp-analysis/post-office-next-system-kr.txt` | 64 PDF pages, 2,401 extracted lines | Next-generation financial platform design: omni-channel, 24x365, data platform, security, integration, cloud |
| `Tender-for-supply-Delivery-and-implementation-of-Core-Banking-System-and-ERP-FINAL.pdf` | `/tmp/banking-lab-rfp-analysis/cbs-erp-tender.txt` | 119 PDF pages, 5,203 extracted lines | CBS plus ERP: member registry, accounts, teller cash, loans, GL, channels, CRM, audit/risk, procurement, inventory, BI |

Required repository context was also read first: `PLAN.md`, `BANKING_LAB_CODEX_PROMPT.md`, `AGENTS.md`, migration parity docs, Node oracle tests under `tests/*.test.mjs`, architecture/ADR/evidence docs, OpenAPI/AsyncAPI contracts, Flyway migrations, Spring services, Next apps, shared packages, analytics code, and screen manifests.

Tracked implementation surface reviewed:

| Area | Evidence read |
| --- | --- |
| Target backend | Kotlin/Spring Boot under `services/core-banking`, `services/payment-service`, `services/notification-service`, `services/reporting-service` |
| DB schema | `db/migrations/V001..V041` plus service-specific migrations |
| Frontend | Next.js apps under `apps/customer-web`, `apps/staff-terminal`, `apps/complaint-portal`, `apps/ops-console`, `apps/audit-console`, `apps/fds-aml-console`, `apps/admin-console`, `apps/call-center-console` |
| Shared TS | `packages/api-client`, `packages/auth-client`, `packages/screen-engine`, `packages/form-engine`, `packages/channel-ui` |
| Analytics | `analytics/aml-fds-python` |
| Contracts | `contracts/openapi`, `contracts/events`, `contracts/asyncapi`, `contracts/temporal` |
| Platform | Docker Compose, Kubernetes, Helm, Terraform, Argo CD, observability, security scan configs |

## Consolidated Functional Requirements From The PDFs

The four PDFs overlap heavily. Consolidated requirement families are:

1. Customer/member registry, KYC/CDD, duplicate identity prevention, customer 360 view, account relationship management.
2. Account opening, closure, dormant/reactivation, account holds/freezes, branch/home-branch transfer, customer statements and certificates.
3. Deposit products, fixed/recurring/compulsory deposits, rate versions, interest accrual/posting, maturity, premature closure, charges and penalties.
4. Loan origination, approval, disbursement, repayment, prepayment, schedule generation, arrears, NPA/delinquency, provisioning, write-off, guarantor and collateral controls.
5. Loan aftercare: collections, legal/court case tracking, property investigation, debt restructuring, bankruptcy/rehabilitation, claim sale, write-off, notification/delivery tracking.
6. Ledger/GL: chart of accounts, journals, trial balance, budget, AR/AP, GL controls, suspense/control accounts, branch/inter-branch reconciliation.
7. Teller/cashier: cash drawer/float, teller-to-teller transfers, chief cashier controls, teller close-out, cash discrepancy, serialized receipts.
8. Payments and clearing: cheque clearing, bankers cheque, ECS/ACH, RTGS/NEFT/IMPS/UPI, cards/ATM/POS, mobile money, autopay, settlement accounts, failed-transaction reversal.
9. Channels: web, mobile app, internet banking, ATM/POS, kiosk, agency banking, call center, omni-channel customer experience, HTML5/cross-browser UI.
10. CRM, complaints, call center, case management, SLA/TAT, assignment, escalation, comments, attachments, child cases, customer communications.
11. AML/FDS/fraud/risk: unusual transaction rules, sanctions/PEP, AML/KYC reports, risk dashboards, model cards, blacklist/suspicious transaction flags.
12. Reporting/BI: statutory reports, audit reports, exception reports, daily/monthly/weekly reports, ad hoc reports, drill-down dashboards, export controls.
13. Security/control: RBAC/ABAC, SSO/OIDC, MFA/WebAuthn/biometric, maker-checker/four-eyes, immutable audit trail, masking, encryption, secrets management, session controls.
14. Eventing/integration: internal interface standards, external provider adapters, EDMS/PPR/UMS/EDW, data exchange contracts, durable event delivery.
15. Data migration and data platform: data mapping, gap analysis, migration scripts, reconciliation reports, data quality, lineage, marts/DW, CDC/ETL.
16. Workflow/orchestration: approval workflows, long-running case workflows, Temporal/state-machine visibility, retry and dead-letter handling.
17. Operations/resilience: EOD/SOD, backup/restore, DR, HA, 24x365 availability, monitoring, observability, incident handling.
18. ERP modules: finance/accounting, procurement, supplier portal, inventory/stores, fixed assets, HR/payroll, staff allowances/advances.
19. Project/evidence controls: UAT, SIT, training, manuals, traceability matrix, quality gates, security scans, audit evidence.

## Current Codebase Coverage Summary

Legend:

- `covered`: meaningful target-stack implementation and tests exist for the synthetic lab scope.
- `partial`: meaningful implementation exists, but it is shallow, synthetic, route-gated, or missing major RFP depth.
- `missing`: no dedicated target implementation found.
- `out-of-scope`: real-world integration should not be implemented; a simulator or evidence-only model is appropriate.

| Requirement family | Current code evidence | Status | Gap |
| --- | --- | --- | --- |
| Core ledger, postings, idempotency, reversal, adjustment, closed day | `LedgerCommandService.kt`, `LedgerModels.kt`, `LedgerController.kt`, `V001__foundation.sql`, `V002__ledger_constraints.sql`, `V027__ledger_db_integrity_and_accounting_structure.sql`, `LedgerCommandServiceIntegrationTest.kt`, `LedgerDatabaseIntegrityIntegrationTest.kt` | covered | Strong for synthetic double-entry ledger; still not a full commercial GL/sub-ledger product with budget, AR/AP, teller cash and branch vault subledgers. |
| Balance projections | `account_balance_projections`, `LedgerProjectionIntegrityService.kt`, `OPS-LEDGER-*` manifests | covered | Projection drift/rebuild exists; richer historical balance snapshots and native partitioning would be needed for larger production-like data volumes. |
| Customer self-service onboarding/login | `CustomerAuthService.kt`, `CustomerOnboardingService.kt`, `AccountOpeningService.kt`, `CustomerAccountService.kt`, customer web manifests/routes | partial | Synthetic signup/login and staff onboarding exist; no full KYC document workflow, CKYC/eKYC simulator, duplicate identity scoring, household/group membership, or branch transfer workflow. |
| Customer 360/account views/statements | `CustomerAccountController.kt`, `StatementService.kt`, `CWB-101..103`, `CWB-401` | partial | Account detail, transaction history and certificates exist; no full 360 view with loans, collateral, guarantors, communications, complaints, risk, and marketing consent in one canonical read model. |
| Deposits and account products | `DepositProductService.kt`, `FeePolicyService.kt`, `V018__deposit_products_interest.sql`, `V019__fee_policies_posting.sql` | partial | Product/rate/fee slices exist; no full fixed/recurring/compulsory deposit maturity, auto-renewal, premature closure, dividend/share account, TDS/tax certificates, or cheque book controls. |
| Account state controls | `StaffAccessService.kt`, account hold/release and limit change request APIs, account holds tables | partial | Holds and limits exist; no complete dormant/reactivation, DEAF/unclaimed deposit, total/debit/credit freeze semantics across every posting path, or closure fee/liquidation workflow. |
| Loan origination and servicing | `LoanService.kt`, `V022__loan_domain.sql`, `LoanDomainIntegrationTest.kt`, customer loan manifests | partial | Synthetic application, approval, disbursement, repayment, prepayment, accrual exist; no collateral/guarantor model, delinquency aging, NPA/provisioning, schedule recast, write-off, refinance, restructuring, or legal aftercare. |
| Loan aftercare and collections | No dedicated module; adjacent code: `LoanService.kt`, `ComplaintCaseService.kt`, `FdsCaseService.kt`, `AmlCaseService.kt`, `ReconciliationOpsService.kt` | missing | The Korean aftercare RFP is mostly uncovered: collections case, legal deadlines, property investigation, auction/public sale, debt restructuring, bankruptcy/rehabilitation, claim sale/write-off allocation, notice delivery tracking. |
| Collateral and guarantors | No dedicated tables/services found | missing | Required by CBS/ERP and aftercare RFPs for loan eligibility, recovery, guarantee substitution, collateral valuation, duplicate pledge prevention, legal enforcement. |
| Teller/cashier and branch vault | Staff terminal has transaction-code shell; ledger has deposits/withdrawals | partial | No teller drawer, cash float, head cashier, teller-to-teller transfer, cash replenishment, close-out, cash discrepancy, serialized cash receipt, or GL cash reconciliation module. |
| Branch/ABB/IBR reconciliation | `ReconciliationOpsService.kt`, `V005__fds_aml_reconciliation.sql`, `OPS-201`, `OPS-202`, `OPS-301` | partial | Reconciliation items and adjustment requests exist; no full inter-branch accounting, teller-vault reconciliation, payment-network settlement reconciliation, or clearing-house settlement lifecycle. |
| EOD/SOD | `EodClosingService.kt`, `EndOfDayClosingWorkflow.kt`, `V021__eod_closing_steps.sql`, `OPS-101` | partial | Durable EOD pipeline exists; SOD opening, branch day control, 24x365 cutover strategy, product-specific close calendars, and teller/vault close integration are missing. |
| Payments/autopay/bill payment | `payment-service`, `PaymentInstructionService.kt`, `PaymentAutopayService.kt`, `CWB-701..703`, payment OpenAPI | partial | Good synthetic bounded context; no real or simulated RTGS/NEFT/UPI/ACH/cheque/ATM settlement rails with file/message lifecycles and reconciliation. |
| Cards/3DS/authorization/capture | `CardService.kt`, `V023__card_domain.sql`, `CardDomainIntegrationTest.kt`, `CWB-601..606` | partial | Basic synthetic card authorization/capture/loss controls exist; no ATM/POS switch simulator, merchant/category rules, partial capture, chargeback/dispute, card replacement, PIN lifecycle. |
| Cheque clearing/bankers cheque | No dedicated clearing module found | missing | CitizenCredit and Nyati require inward/outward clearing, cheque images/MICR, bankers cheque control account posting, CTS/NACH style processing. |
| Mobile/internet banking and channels | Next customer web, staff terminal, complaint portal, ops/audit/admin/FDS/AML/call-center consoles | partial | Web channels exist; no native mobile app, ATM/POS/kiosk/agency banking simulator, mobile device binding, SIM swap workflow beyond security concepts, or channel transaction monitoring depth. |
| Screen platform/templates | `screen-manifests/*`, `packages/screen-engine`, `packages/form-engine` | covered | Good manifest coverage; some large panels are hand-coded and staff-terminal is an integrated terminal boundary rather than manifest-driven staff screens. |
| Staff terminal | `apps/staff-terminal/src/components/terminal/*`, staff APIs | partial | Strong iWorks shell and selected API workflows; no complete teller/cash, branch operations, GL journal workbench, loan aftercare workbench, or all RFP transaction codes. |
| Complaint/CRM/call center | `ComplaintCaseService.kt`, `CallCenterService.kt`, complaint and call-center manifests/apps | partial | Complaint and call-center flows exist; no full CRM with sales pipeline, child cases, Facebook/social channels, EDMS attachment repository, contact-center CTI integration, or case locking depth. |
| AML/FDS/fraud | `FdsCaseService.kt`, `AmlCaseService.kt`, `AmlFdsGovernanceService.kt`, `analytics/aml-fds-python` | partial | Synthetic rules, model-card, STR artifacts, case workflows exist; no real provider integration, full model-risk governance, CFT typologies, full risk scoring warehouse, or broad channel fraud detection. Real provider integration is out-of-scope. |
| Audit, masking, maker-checker | `AuditEventService.kt`, `AuditEventAppender.kt`, `Approval*`, structured error/security filters, tests | covered | Strong synthetic controls; add domain-specific audit event taxonomies for teller, loan aftercare, clearing, procurement if those domains are added. |
| Identity/OIDC/RBAC/ABAC/MFA | `infra/keycloak/realm-banking-lab.json`, `SecurityConfig.kt`, `BankingLabAuthorizationFilter.kt`, service security packages | covered | Good synthetic Keycloak/JWKS path; no enterprise IAM connector, AD integration, periodic access review workflow beyond generated governance evidence. Real AD is out-of-scope. |
| Notification/UMS | `notification-service`, notification preferences/delivery/templates, `CWB-801..802`, `AUD-301`, `ADM-401..402` | partial | Synthetic notification service exists; no full UMS with postal mail, certified mail, fax, voice, EDMS attachment generation, content delivery proof, or aftercare legal notice service. |
| Reporting/BI | `reporting-service`, `FdsAnalyticsEvidenceService.kt`, `analytics/aml-fds-python`, admin/audit manifests | partial | Reporting artifacts, exports, retention, data marts exist; no broad statutory catalog, trial balance suite, teller reports, branch performance, audit scenario library, user-defined report builder, or enterprise BI portal. |
| Data migration | Flyway migrations and migration docs exist | partial | Schema migration exists; no legacy data ingestion toolkit, mapping spec, cutover reconciliation suite, image/document migration, migration audit script export, or UAT data comparator. |
| Data platform/DW | DuckDB/Parquet analytics under `analytics/aml-fds-python` | partial | Local synthetic marts and DQ checks exist; no CDC from PostgreSQL, enterprise metadata/catalog, streaming ETL, EDW, CRM/marketing lake, or real-time integrated mart. |
| Outbox/eventing/Kafka | `DurableOutboxService.kt`, `KafkaOutboxPublisher.kt`, payment/reporting/notification event workers, AsyncAPI | covered | Good event backbone; add more domain events when teller, aftercare, clearing, procurement, and reports are implemented. |
| Temporal/workflows | `BankingCaseTemporalWorkflow.kt`, `EndOfDayClosingWorkflow.kt`, workflow persistence tests | partial | Core case workflows exist; no Temporal workflows for loan aftercare, legal notices, collateral enforcement, teller close, clearing settlement, procurement approvals. |
| Platform/observability/security verification | Docker Compose, K8s, Helm, Argo CD, Prometheus/Grafana/Loki/Tempo, Semgrep/Trivy/SBOM/ZAP configs | partial | Strong structural/local evidence; hosted CI remains separately documented as blocked in prior evidence, and production-grade secret store, ingress TLS, multi-node failover, canary promotion are not proven. |
| ERP procurement/inventory/fixed assets/HR/payroll | No dedicated services found | missing | The Nyati ERP tender requires these, but they are outside the current banking-lab core scope. Decide explicit out-of-scope or add separate bounded contexts later. |
| Treasury/Forex/GST/tax/TDS/statutory regional reports | Minimal or no dedicated implementation | missing | Required by CitizenCredit; these are jurisdiction-specific. In this lab they should be simulated only if useful for ledger/reporting breadth. |
| Real external provider/network APIs | Deliberately simulator-only | out-of-scope | Must stay simulator-only by AGENTS.md. Build adapters/contracts for synthetic RTGS/UPI/credit bureau/sanctions/court/EDMS providers, not real integrations. |

## Highest-Impact Gaps

### 1. Loan Aftercare Is Almost Entirely Missing

The second PDF is not just "loan servicing"; it is a full distressed-credit operating platform. Current `LoanService` covers normal synthetic loan lifecycle, but the RFP asks for:

- delinquency and non-performing loan case monitoring;
- daily work queues and assigned officer TO-DOs;
- debtor notices and delivery tracking;
- property investigation and collateral preservation;
- court/legal deadline tracking;
- auction/public sale and distribution of proceeds;
- write-off, special debt, debt forgiveness/waiver, debt sale;
- personal rehabilitation, bankruptcy, workout, corporate rehabilitation;
- document/e-approval records for committees;
- accounting/ledger events tied back to original loan claims.

Current gap: no `loan-aftercare` bounded context, no aftercare state machine, no collateral/guarantor/legal case model, no ledger commands for write-off/sale/recovery allocation.

### 2. Teller/Cashier And Branch Cash Controls Are Not Implemented

The RFPs treat teller cash as a first-class core-banking module. Current code has ledger deposit/withdrawal commands and staff-terminal screens, but not:

- teller drawer opening/closing;
- cash float issue/replenishment/return;
- teller-to-teller and teller-to-chief-cashier transfers;
- cash vault balances as GL-backed accounts;
- close-out mismatch handling;
- serialized receipts and cash discrepancy reports.

This is a core gap because it exercises double-entry, maker-checker, audit, EOD, and branch reconciliation together.

### 3. Loan Depth Stops Before Collateral, Guarantors, NPA, Provisioning

The current synthetic loan domain is useful, but RFP-grade loans require:

- collateral and guarantor eligibility;
- guarantee release/substitution;
- product-specific repayment methods;
- schedule recast after partial prepayment or restructuring;
- delinquency aging and NPA classification;
- provisioning and ECL/IFRS9/ECL-style simulation;
- write-off and recovery accounting.

### 4. Clearing And Payment Network Simulation Is Too Thin

Payment-service supports synthetic bill payment/autopay and outbox dispatch, but RFP requirements include cheque clearing, CTS/NACH, RTGS/NEFT/IMPS/UPI, ATM/POS, mobile money, failed transaction reversals, settlement accounts, unposted transaction logs, and reconciliation. Real rails must not be integrated, but synthetic rail simulators should exist if the lab aims to model bank-grade operations.

### 5. Reporting Is Broad But Not Bank-Report Complete

Reporting-service can create/export synthetic artifacts, and analytics generate limited risk/FDS outputs. Missing are:

- day book, trial balance, branch balance sheet, teller cash reports;
- statutory/regulatory report catalog;
- audit exception scenarios such as same ID duplicate accounts, no-double-entry accounts, odd-hour transactions, GL-vs-subledger differences;
- user-defined reports with report-level RBAC and print/export controls;
- report lineage to source tables for each regulatory/report artifact.

### 6. ERP Is Out Of Current Scope But Not Explicitly Retired

Nyati's tender includes procurement, stores/inventory, supplier portal, HR, payroll, fixed assets, allowances, and advances. Banking Lab currently does not implement those bounded contexts. This should be a deliberate scope decision:

- mark ERP modules out-of-scope for the core banking lab; or
- implement them later as separate bounded contexts that post only approved accounting entries to core ledger.

### 7. Data Migration Is Mostly Schema Migration, Not Business Data Migration

Flyway is mature, but RFPs ask for legacy data cutover with mapping, quality checks, reconciliation, sample branch migration, UAT comparison, and migration audit scripts. The repo does not yet have a reusable synthetic legacy-data migration harness.

## Recommended Target Design

### Bounded Contexts To Add Or Clarify

| Context | Purpose | Owns | Publishes |
| --- | --- | --- | --- |
| `loan-aftercare-service` or `core-banking/aftercare` | Distressed credit lifecycle | aftercare cases, collection tasks, legal deadlines, notices, write-off/sale/restructuring approvals | `AftercareCaseOpened`, `NoticeDelivered`, `DebtRestructured`, `LoanWrittenOff`, `RecoveryAllocated` |
| `collateral-guarantee-service` or core module | Collateral/guarantor registry | collateral assets, valuations, liens, guarantor obligations, releases/substitutions | `CollateralValued`, `GuaranteeReleased`, `CollateralEnforcementStarted` |
| `teller-service` or core module | Cash operations | teller drawers, vault accounts, cash float, cash receipts, close-out discrepancies | `TellerDrawerOpened`, `CashTransferPosted`, `TellerCloseOutCompleted` |
| `clearing-simulator-service` | Synthetic payment rails | cheque/ACH/RTGS/UPI/ATM/mobile settlement batches and exceptions | `ClearingBatchReceived`, `SettlementPosted`, `NetworkReversalRequested` |
| `reporting-catalog-service` extension | Bank report catalog | report definitions, source lineage, export permissions, statutory/exception report runs | `ReportRunCompleted`, `ReportExceptionDetected` |
| `migration-workbench` | Synthetic cutover proof | source extracts, mappings, migration runs, reconciliation results | `MigrationRunCompleted`, `MigrationMismatchDetected` |

### First Safe Vertical Slice

Build loan aftercare first because it is the largest missing RFP domain and reuses existing controls.

1. Add Flyway tables:
   - `loan_aftercare_cases`
   - `loan_aftercare_tasks`
   - `delinquency_events`
   - `collection_notices`
   - `collateral_assets`
   - `loan_restructuring_cases`
   - `loan_write_off_requests`
   - `loan_recovery_allocations`
2. Add Kotlin service/controller with reason-required access and maker-checker for high-risk actions.
3. Add Temporal workflow for aftercare case lifecycle:
   - `OPENED -> ASSIGNED -> NOTICE_PENDING -> NEGOTIATION -> RESTRUCTURING_REQUESTED -> APPROVED/REJECTED -> RECOVERY/LEGAL/WRITE_OFF -> CLOSED`
4. Add ledger commands only for accounting movements:
   - recovery allocation;
   - write-off adjustment;
   - sale proceeds allocation;
   - legal cost recovery;
   - restructuring capitalization/fee reversal where applicable.
5. Add screen manifests:
   - `AFR-101.aftercare-case-dashboard`
   - `AFR-102.collection-task-queue`
   - `AFR-201.notice-delivery`
   - `AFR-301.restructuring-review`
   - `AFR-401.write-off-approval`
6. Add tests:
   - state transition tests;
   - maker-checker separation;
   - no direct finalized ledger mutation;
   - balanced postings for write-off/recovery;
   - idempotent notice and accounting commands;
   - masked borrower data and reason-required lookup;
   - outbox event persistence.

### Second Safe Vertical Slice

Add teller/cash controls:

1. `teller_drawers`, `cash_vaults`, `cash_movements`, `teller_closeouts`, `cash_discrepancies`.
2. Treat vault and teller drawer balances as projections from ledger postings, not mutable balances.
3. Require maker-checker for cash discrepancy adjustment and drawer override.
4. Integrate with EOD close: EOD cannot close with unresolved cash discrepancy.
5. Add staff-terminal transaction codes for drawer open, deposit, withdrawal, transfer, close-out.

### Third Safe Vertical Slice

Add clearing/payment rail simulators:

1. Synthetic cheque clearing batch with inward/outward states.
2. Synthetic RTGS/NEFT/UPI rail contracts, never real network calls.
3. Settlement account postings through ledger.
4. Reconciliation items for unmatched settlement, duplicate file, delayed reversal.
5. Outbox events and idempotent consumers.

## Concrete Roadmap

| Priority | Work | Why |
| --- | --- | --- |
| P0 | Create an RFP requirement registry under `docs/requirements/rfp-requirement-register.md` or JSON/YAML | Prevent future overclaiming; each requirement should map to code, tests, evidence, or explicit out-of-scope decision. |
| P0 | Implement `loan-aftercare` vertical slice | Largest functional gap from the supplied PDFs; exercises workflow, audit, maker-checker, ledger, notices, reports. |
| P0 | Implement teller/cash drawer module | Core CBS gap and strong ledger/control demonstration. |
| P0 | Expand loan domain with collateral, guarantor, delinquency, NPA/provisioning simulation | Required by all CBS/ERP sources and aftercare flow. |
| P0 | Add report catalog rows for trial balance, day book, teller cash, aftercare aging, odd-hour transactions, overdrawn accounts | Converts reporting from artifact engine to bank-operational reporting. |
| P1 | Add clearing/payment rail simulators | Models external rails safely without violating synthetic-only boundary. |
| P1 | Add migration workbench and synthetic legacy extracts | Satisfies data migration RFP evidence without real bank data. |
| P1 | Add channel transaction monitoring dashboard | Covers ATM/mobile/API channel failure and fraud-monitoring requirements. |
| P1 | Add EDMS/document-template simulator for notices and approvals | Covers legal/aftercare/CRM document requirements without real EDMS. |
| P2 | Decide ERP scope explicitly | Either add procurement/inventory/HR/payroll contexts later or document them as out-of-scope. |
| P2 | Add treasury/forex/GST/tax simulators only if needed for report/ledger breadth | These are jurisdiction-specific and should not distract from core ledger/aftercare/teller gaps. |

## Acceptance Criteria For Future Claims

Do not claim a requirement is complete unless all are true:

1. Spring/Kotlin domain code exists in target stack, not Node reference code.
2. PostgreSQL schema exists and uses durable state.
3. Financial movements use balanced ledger postings.
4. Idempotency is tested for externally retryable commands.
5. High-risk operations require maker-checker with separation of duties.
6. Sensitive reads require reason and produce audit events.
7. PII is masked by default in APIs and screens.
8. Events are persisted to outbox before publication.
9. Runtime or integration tests pass and are recorded as current evidence.
10. Screen coverage is API-backed or explicitly marked manifest-only.
11. External dependencies are simulator-only and documented as such.

## Immediate Next Smallest Safe Task

Add a machine-readable requirement register for the four PDFs with these fields:

- `requirementId`
- `sourcePdf`
- `sourcePageOrSection`
- `capability`
- `priority`
- `currentStatus`
- `currentCodeEvidence`
- `missingImplementation`
- `recommendedSlice`
- `syntheticBoundary`
- `testEvidenceNeeded`

Then start the `loan-aftercare` P0 slice from that register.
