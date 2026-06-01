# Phase 1 Control Mapping

| Control area | Phase 1 evidence |
| --- | --- |
| Ledger integrity | `packages/banking-domain/src/ledger.mjs`, `tests/ledger.test.mjs` |
| Balance projection only | `projectBalances`, `account_balances` migration comments and tests |
| Idempotent external commands | `IdempotencyStore`, transfer runtime test |
| Reversal instead of mutation | `createReversalTransaction`, append-only SQL triggers |
| Staff access audit | `AuditLog`, reason-required staff search API, runtime test |
| PII masked by default | `masking.mjs`, staff customer search response |
| Maker-checker | `ApprovalStore`, `operator_approvals`, tests |
| Screen manifest scaling | `screen-manifests/*`, `packages/screen-engine`, manifest tests |
| Evidence pack | `docs/test-evidence`, `scripts/generate-phase1-evidence.mjs` |

## Phase 2 Additions

| Control area | Phase 2 evidence |
| --- | --- |
| Deposit/withdraw/transfer controls | `services/core-banking/src/ledgerCore.mjs`, `tests/ledgerCore.test.mjs` |
| Concurrent withdrawal protection | `CommandLock`, concurrent withdrawal test |
| Closed business day guard | `closeBusinessDay`, `assertBusinessDateOpen`, closed-day test |
| Ledger idempotency replay | `LedgerCore` idempotency tests |
| Reversal duplicate prevention | `reverseTransaction`, reversal tests |
| Runtime ledger API evidence | `tests/runtime.test.mjs`, `scripts/generate-phase2-evidence.mjs` |

## Phase 3 Additions

| Control area | Phase 3 evidence |
| --- | --- |
| Reason-required staff inquiry | `runtime/labApp.mjs`, `tests/staffTerminal.test.mjs` |
| Masked PII default | `publicCustomerDetail`, staff detail tests |
| Privileged unmask audit | `/api/staff/pii/unmask`, `PII_UNMASK_REQUESTED` tests |
| Customer info maker-checker | `CUSTOMER_INFO_CHANGE` approval flow tests |
| Staff manifest coverage | `screen-manifests/staff-terminal/*`, `validate:manifests` |

## Phase 4 Additions

| Control area | Phase 4 evidence |
| --- | --- |
| Customer account self-service audit | `/api/customer/accounts/{accountId}/detail`, `tests/customerWeb.test.mjs` |
| Shared ledger source by channel | Customer/staff transaction history comparison test |
| Customer transfer idempotency | Transfer retry test and `transferResults` records |
| Held transfer without unsafe posting | FDS-held transfer test |
| Failed transfer without unsafe posting | Failed transfer test |
| Complaint entry | `CWB-301` manifest and portal shell test |

## Phase 5 Additions

| Control area | Phase 5 evidence |
| --- | --- |
| Shared complaint case source | Customer/staff complaint list test |
| Complaint SLA and timeline | `receiveComplaint`, workflow evidence |
| Ordered complaint state transitions | `services/complaint-service`, workflow tests |
| Answer approval before send | `COMPLAINT_ANSWER_SEND` approval test |
| Customer answer confirmation | Customer confirmation close test |
| Complaint workflow manifests | `CMP-101`, `CMP-102`, `CMP-201` manifest validation |

## Explicit Non-Production Boundary

No real banking network, real KYC, real payment processor, real customer PII, or real funds are connected. External providers remain simulators.
