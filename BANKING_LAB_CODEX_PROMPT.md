# Banking Lab Codex Prompt

1. Read `PLAN.md`, `SPEC.md`, `AGENTS.md`, and migration docs before implementation.
2. Build the target stack only: Kotlin/Spring Boot, PostgreSQL, Kafka/Redpanda Outbox, Temporal, TypeScript/Next.js, Keycloak, OpenTelemetry, Kubernetes/Helm/Terraform/Argo CD.
3. Treat Node.js runtime code as archived regression/reference material only; do not add target business features there.
4. Use synthetic data only. Never integrate real money, real PII, real KYC, real payment networks, or real bank APIs.
5. Ledger integrity comes first: every financial movement must be balanced double-entry postings.
6. Balances are projections from postings, never mutable source-of-truth fields.
7. Finalized transactions are append-only; use reversal or approved balanced adjustment.
8. Every externally retried command must be idempotent with persisted command hash.
9. Closed business dates reject direct posting.
10. Critical ledger paths require PostgreSQL transaction/isolation tests.
11. Staff access to customer/account/transaction data requires business reason and audit.
12. PII and account identifiers are masked by default in UI, API responses, logs, and audit payloads.
13. High-risk staff operations require maker-checker approval and separation of duties.
14. Customer self-service account-opening requests must not create accounts or ledger postings before staff approval/execution.
15. Customer routes must use token-owned `customerId`; never trust arbitrary customer query params for ownership.
16. Long-running workflows must use Temporal or a documented state machine until Temporal is wired.
17. Domain events must use durable Outbox persistence before Kafka/Redpanda publication.
18. Reconciliation corrections must use balanced adjustment transactions, not balance edits.
19. Screen work must use reusable manifests/templates; avoid copy-paste one-off banking screens.
20. Manifests must declare roles, authorization, audit, masking, approval/workflow metadata, and synthetic-only boundaries.
21. Structured API errors must follow `docs/migration/structured-api-error-contract.md`.
22. Add or update OpenAPI, API client, backend tests, frontend tests, manifests, and evidence together.
23. Do not claim parity, durability, or security controls without running the relevant command.
24. Keep generated evidence honest: skipped/blocked tests need explicit reasons.
25. Preserve existing user changes; never revert unrelated files.
26. Prefer small vertical slices with migrations, service logic, contracts, UI, tests, and evidence.
27. Use JUnit/Testcontainers for backend, Playwright for frontend, Semgrep/Trivy/SBOM for security evidence.
28. After each task report changed files, commands run, passing tests, skipped/failing tests, invariants, security/control impact, remaining risk, and next safe task.
