# Synthetic Banking Lab SLOs

These SLOs are for the local synthetic banking lab. They are not production customer commitments and do not cover real money, real PII, real payment networks, real card networks, Open Banking, or KYC providers.

| Area | Metric | Target | Alert |
| --- | --- | --- | --- |
| Ledger command latency | `ledger_command_latency` from `banking_lab_ledger_command_latency_seconds` | p95 under 1s for 10 minutes | `LedgerCommandLatencyHigh` |
| Ledger command errors | `ledger_command_error_rate` from `banking_lab_ledger_command_errors_total` over command count | under 2% for 10 minutes | `LedgerCommandErrorRateHigh` |
| Idempotency replay | `idempotency_replay_count` from `banking_lab_idempotency_replay_count_total` | under 25 replays per 10 minutes | `IdempotencyReplaySpike` |
| Outbox backlog | `outbox_pending_count` from `banking_lab_outbox_pending_count` | under 100 pending rows for 15 minutes | `OutboxPendingBacklog` |
| Outbox terminal failure | `outbox_dead_letter_count` from `banking_lab_outbox_dead_letter_count` | zero dead-letter rows | `OutboxDeadLetterPresent` |
| Authorization denials | `authorization_denied_count` from `banking_lab_authorization_denied_count_total` | under 20 denials per 10 minutes | `AuthorizationDeniedSpike` |
| Audit append failures | `audit_append_failure_count` from `banking_lab_audit_append_failure_count_total` | zero failures | `AuditAppendFailure` |
| Payment instruction failures | `payment_instruction_failure_count` from payment publisher failure/dead-letter counters | zero terminal failures | `PaymentInstructionFailures` |
| Notification dead letters | `notification_dead_letter_count` from notification consumer failure/dead-letter signals | zero terminal failures | `NotificationDeadLetterPresent` |
| Report artifact failures | `report_artifact_generation_failure_count` from reporting publisher failure/dead-letter counters | zero terminal failures | `ReportArtifactGenerationFailures` |

Evidence must record exact commands run. Do not mark an SLO check passed unless Prometheus, actuator, or the relevant structural validator was actually run.
