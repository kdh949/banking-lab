# Observability

The synthetic lab exposes Spring Boot actuator Prometheus endpoints and local Prometheus/Grafana/Loki/Tempo scaffolding through Docker Compose. This is local operational evidence only.

## Metrics

- Core ledger: `ledger_command_latency`, `ledger_command_error_rate`, `idempotency_replay_count`.
- Core outbox: `outbox_pending_count`, `outbox_dead_letter_count`.
- Access control and audit: `authorization_denied_count`, `audit_append_failure_count`.
- Bounded-context signals: `payment_instruction_failure_count`, `notification_dead_letter_count`, `report_artifact_generation_failure_count`.

Spring metric names are exported in Prometheus format with the `banking_lab_` prefix, for example `banking_lab_ledger_command_latency_seconds`.

## Dashboards

- `infra/observability/grafana-dashboard-core-banking.json`
- `infra/observability/grafana-dashboard-outbox.json`
- `infra/observability/grafana/dashboards/temporal-workflow-observability.json`

## Alerts

Prometheus rules live in `infra/observability/prometheus-rules.yaml`. Each alert includes `syntheticOnly=true` and a runbook path. Alert routing to a real paging tool is intentionally not modeled in this lab.

## Validation

Run:

```bash
npm run observability:validate
```

The validator parses the rule and dashboard files and checks that every Phase 6 metric is represented in rules, dashboards, and operations docs. For live evidence, run the affected Spring actuator test or the Compose observability smoke and record the result under `docs/test-evidence/`.
