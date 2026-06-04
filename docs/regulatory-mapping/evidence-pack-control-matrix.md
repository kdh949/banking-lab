# Evidence Pack Control Matrix

| Control | Implementation | Automated evidence |
| --- | --- | --- |
| Double-entry posting balance | `legacy-node-reference/packages/banking-domain/src/ledger.mjs` | `tests/ledger.test.mjs`, Phase 2 evidence |
| DB-enforced posted transaction balance | `validate_ledger_transaction_balance_at_commit`, `ledger_transactions_balance_at_commit`, `ledger_postings_balance_at_commit` | `LedgerDatabaseIntegrityIntegrationTest`, `npm run ledger:integrity-check` |
| Chart of accounts and system routing | `accounts.account_class`, `accounts.system_account_kind`, `LedgerCommandService` system account routing | `LedgerDatabaseIntegrityIntegrationTest`, product/loan/card integration regression |
| Cross-instance ledger HA/DR convergence | `LedgerCommandService` bounded retry around SERIALIZABLE/REPEATABLE READ withdrawal transactions, shared PostgreSQL source of truth | `MultiInstanceLedgerHaDrIntegrationTest`, `npm run dr:multi-instance-drill`, `docs/test-evidence/generated/ha-dr-multi-instance-drill.json` |
| PostgreSQL backup/restore RPO | live disposable source/restore PostgreSQL containers and canonical restore parity checks | `npm run postgres:backup-drill:docker-live`, `docs/test-evidence/generated/postgres-backup-restore-drill.json`, `docs/test-evidence/postgres-backup-restore-drill.md` |
| Balance projection from postings | `projectBalances` | ledger tests |
| Idempotent transfer commands | `IdempotencyStore`, transfer result store | customer/runtime tests |
| Reversal instead of mutation | `createReversalTransaction` | ledger core tests |
| Closed day guard | `LedgerCore.assertBusinessDateOpen` | Phase 6 reconciliation test |
| Staff sensitive access audit | staff APIs append audit events, `StaffAccessService` | Phase 3 tests, staff access API integration test |
| Role and ownership authorization | `BankingLabAuthorizationFilter`, `BankingLabTokenDecoder`, `SignedJwtJwksTokenDecoder`, `BankingLabAuthContext`, `CustomerAccountService` | security authorization integration test, JWKS authorization integration test |
| Secure auth defaults and session controls | `BankingLabSecurityPolicyEnforcer`, `AuthSessionController`, `trusted_devices`, `revoked_sessions`, secure Compose/Kubernetes/Helm defaults | security posture check, security defaults integration test, JWKS step-up/trusted-device/session integration test |
| Operational-security WORM export | `audit_worm_export_segments`, `OperationalSecurityService.exportAuditSegment`, segment hash verification | `OperationalSecurityIntegrationTest`, `docs/test-evidence/hardening-h5-operational-security.md` |
| Synthetic KMS/HSM rotation | `synthetic_kms_keys`, `/api/ops/security/kms/rotate`, synthetic key hashes without real key material | `OperationalSecurityIntegrationTest`, `docs/security/synthetic-secret-rotation-runbook.md` |
| PAM break-glass post-review | `break_glass_grants`, `break_glass_review_cases`, `/api/ops/security/break-glass/*` | `OperationalSecurityIntegrationTest` |
| Synthetic SIEM alert drill | `infra/observability/loki/rules/fake/operational-security-alerts.yml`, `scripts/run-siem-alert-drill.ts` | `npm run siem:alert-drill`, `docs/test-evidence/generated/siem-alert-drill.json` |
| Synthetic sanctions and PEP screening | `synthetic_watchlist_entries`, `sanctions_screening_hits`, `AmlFdsGovernanceService` | `AmlFdsGovernanceIntegrationTest`, `tests/amlGovernance.test.mjs` |
| AML/FDS model governance artifact | `aml_model_versions`, `scripts/run-aml-str-report.ts` model-card lineage | `npm run aml:str-report`, `docs/test-evidence/generated/aml-model-card.json` |
| Synthetic STR and AML regulatory reporting | `synthetic_str_reports`, `synthetic_aml_regulatory_reports`, `scripts/run-aml-str-report.ts` | `npm run aml:str-report`, `docs/test-evidence/generated/synthetic-str-report.json`, `docs/test-evidence/generated/aml-regulatory-report.json` |
| Sanctions false-positive disposition approval | `AmlFdsGovernanceService.falsePositiveDisposition`, `sanctions_screening_hits` disposition fields | `AmlFdsGovernanceIntegrationTest` |
| Analytical mart reconciliation | `banking_lab_analytics.data_platform`, `finance_balance_mart`, `risk_exposure_mart` | `npm run data:dq-check`, `analytics/aml-fds-python/tests/test_data_platform.py` |
| Data-platform dirty-data DQ gate | `run_data_platform(..., inject_dirty_data=True)`, null/duplicate/range/reconciliation checks | `analytics/aml-fds-python/tests/test_data_platform.py`, injected-dirty CLI run |
| Report field lineage | `docs/test-evidence/generated/data-platform/data-lineage.json`, `resolve_lineage` | `tests/dataPlatform.test.mjs`, `analytics/aml-fds-python/tests/test_data_platform.py` |
| Synthetic liquidity/exposure report reproducibility | `docs/test-evidence/generated/data-platform/synthetic-risk-report.json`, report hash | `analytics/aml-fds-python/tests/test_data_platform.py` |
| Masked PII by default | `maskCustomer`, `maskAccount`, `StaffAccessService` | audit/staff tests, staff access API integration test |
| Maker-checker for high-risk operations | `ApprovalStore`, `PersistentApprovalService`, `/api/approvals`, `/api/staff/approvals`, `operator_approvals` | maker-checker, complaint, FDS/AML/recon tests, approval persistence/API integration tests, customer change, complaint answer, FDS execution, AML closure, and reconciliation adjustment integration tests |
| Complaint answer approval | complaint workflow runtime, `ComplaintCaseService` | Phase 5 tests, complaint API integration test |
| FDS hold no posting | FDS runtime path, `FdsCaseService` block path | Phase 6 tests, FDS API integration test |
| AML case closure approval | AML runtime path, `AmlCaseService` | Phase 6 tests, AML case API integration test |
| Reconciliation adjustment as ledger transaction | legacy `LedgerCore.adjustment`, `ReconciliationOpsService`, `LedgerCommandService.adjustment` | Phase 6 tests, reconciliation API integration test |
| Temporal long-running workflow contracts | `BankingCaseTemporalWorkflow`, `BankingCaseTemporalWorker` | Temporal test-environment integration test, live Temporal worker smoke, SDK worker restart drill, Compose worker container restart drills for all current Temporal case types, representative Compose Temporal server restart drill, and representative Compose PostgreSQL restart drill |
| Durable outbox delivery | `outbox_events`, `KafkaOutboxPublisher`, `KafkaInboxConsumer` | outbox state-transition integration test, Redpanda outbox delivery integration test |
| OpenTelemetry trace/log correlation | `TraceLogCorrelationFilter`, `TemporalWorkflowTraceLogger`, Micrometer Tracing, OTLP trace export to Tempo, Promtail log shipping to Loki, Loki ruler alert, Grafana workflow-failure dashboard | observability actuator integration test, Temporal all-current-case signal/completion plus rejection/failure workflow trace/log integration test, OpenTelemetry trace/log correlation smoke, Temporal workflow trace/log correlation smoke, observability stack smoke with Loki ingestion, firing `TemporalWorkflowFailed` alert, and dashboard provisioning |
| Security evidence | `scripts/run-security-evidence.ts`, `infra/security` | npm audit high, Semgrep SAST, Trivy filesystem scan, CycloneDX SBOM, and ZAP baseline DAST evidence |
| Manifest screen scaling | `packages/screen-engine` | manifest validation |
| Audit hash chain | `AuditLog.verifyHashChain` | audit/runtime tests |

## Evidence Commands

```bash
npm test
npm run validate:manifests
npm run evidence:phase1
npm run evidence:phase2
npm run evidence:phase3
npm run evidence:phase4
npm run evidence:phase5
npm run evidence:phase6
npm run evidence:pack
npm run security:evidence
docker compose config
```
