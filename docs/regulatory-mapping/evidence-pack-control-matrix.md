# Evidence Pack Control Matrix

| Control | Implementation | Automated evidence |
| --- | --- | --- |
| Double-entry posting balance | `packages/banking-domain/src/ledger.mjs` | `tests/ledger.test.mjs`, Phase 2 evidence |
| Balance projection from postings | `projectBalances` | ledger tests |
| Idempotent transfer commands | `IdempotencyStore`, transfer result store | customer/runtime tests |
| Reversal instead of mutation | `createReversalTransaction` | ledger core tests |
| Closed day guard | `LedgerCore.assertBusinessDateOpen` | Phase 6 reconciliation test |
| Staff sensitive access audit | staff APIs append audit events | Phase 3 tests |
| Masked PII by default | `maskCustomer`, `maskAccount` | audit/staff tests |
| Maker-checker for high-risk operations | `ApprovalStore` | maker-checker, complaint, FDS/AML/recon tests |
| Complaint answer approval | complaint workflow runtime | Phase 5 tests |
| FDS hold no posting | FDS runtime path | Phase 6 tests |
| AML case closure approval | AML runtime path | Phase 6 tests |
| Reconciliation adjustment as ledger transaction | `LedgerCore.adjustment` | Phase 6 tests |
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
docker compose config
```
