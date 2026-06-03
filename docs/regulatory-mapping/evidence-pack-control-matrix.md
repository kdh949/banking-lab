# Evidence Pack Control Matrix

| Control | Implementation | Automated evidence |
| --- | --- | --- |
| Double-entry posting balance | `packages/banking-domain/src/ledger.mjs` | `tests/ledger.test.mjs`, Phase 2 evidence |
| Balance projection from postings | `projectBalances` | ledger tests |
| Idempotent transfer commands | `IdempotencyStore`, transfer result store | customer/runtime tests |
| Reversal instead of mutation | `createReversalTransaction` | ledger core tests |
| Closed day guard | `LedgerCore.assertBusinessDateOpen` | Phase 6 reconciliation test |
| Staff sensitive access audit | staff APIs append audit events, `StaffAccessService` | Phase 3 tests, staff access API integration test |
| Role and ownership authorization | `BankingLabAuthorizationFilter`, `BankingLabTokenDecoder`, `SignedJwtJwksTokenDecoder`, `BankingLabAuthContext`, `CustomerAccountService` | security authorization integration test, JWKS authorization integration test |
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
