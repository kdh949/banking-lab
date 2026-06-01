# AGENTS.md — Bank-grade Core Banking Lab

## Mission

Build a Bank-grade Core Banking Lab: a simulated banking platform with core banking, customer web banking, staff integrated terminal, electronic complaint workflow, maker-checker control, AML/FDS simulation, reconciliation, audit evidence, and reliability testing.

This project must not handle real customer money, real personal data, or real payment networks. Use synthetic data and simulators only. The implementation should nevertheless model bank-grade reliability, auditability, and operational control.

## Non-negotiable principles

1. Ledger integrity comes first.
2. All financial movements must be represented as double-entry ledger transactions and postings.
3. Do not directly mutate balances except as a projection of postings.
4. Do not update or delete finalized ledger transactions. Use reversal or adjustment transactions.
5. Every externally retried command must be idempotent.
6. Staff access to customer or account data must produce audit events.
7. PII must be masked by default.
8. High-risk staff operations require maker-checker approval.
9. Screen count must be scaled through screen manifests, form engine, workflow engine, and reusable templates.
10. Every milestone must include tests and evidence documents.

## Target architecture

- apps/customer-web: customer web banking
- apps/staff-terminal: staff integrated terminal
- apps/complaint-portal: electronic complaint intake
- apps/ops-console: batch, reconciliation, incident operations
- apps/audit-console: audit and security review
- apps/fds-aml-console: fraud and AML review
- services/core-banking: customer, account, transfer, ledger
- services/workflow-service: state transitions and maker-checker
- services/complaint-service: electronic complaint workflow
- services/fds-service: fraud detection simulation
- services/aml-service: AML case simulation
- services/reconciliation-service: EOD closing and reconciliation
- services/external-simulators: KYC, open banking, SMS/email, regulator simulation

## Required invariant checks

- sum(postings by transaction) == 0
- balance == sum(postings by account)
- available_balance <= ledger_balance
- idempotent request creates at most one transaction
- closed day cannot be mutated directly
- reversal references original transaction
- no sensitive access without audit log
- no high-risk staff operation without approval policy

## Staff terminal requirements

The staff terminal must feel like a bank integrated terminal, not a generic admin page.

Required concepts:

- transaction code input
- tabbed business screens
- customer context panel
- masked PII
- reason-required customer lookup
- audit log panel
- maker-checker approval inbox
- screen manifest-based screen generation

## Screen template types

1. Inquiry Template: search, table, detail, masking, reason, audit.
2. Command Template: target, before/after, reason, validation, approval, audit.
3. Case Template: status, owner, SLA, comments, attachments, timeline.
4. Parameter Template: current value, scheduled value, effective date, approval, rollback.

## Development loop

For each task:

1. Identify domain invariants.
2. Minimize scope.
3. Add or update tests.
4. Implement.
5. Run tests.
6. Update documentation or evidence.
7. Report changed files, commands, test results, and remaining risk.

## Prohibited

- real financial API integration
- real customer PII
- direct balance mutation outside the ledger projection path
- untested ledger logic
- unlogged staff access to sensitive data
- one-off screens when a manifest can be used
- high-risk staff state change without maker-checker review
