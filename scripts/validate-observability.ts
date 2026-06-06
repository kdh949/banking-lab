import { readFile } from "node:fs/promises";
import path from "node:path";

const requiredMetrics = [
  "ledger_command_latency",
  "ledger_command_error_rate",
  "idempotency_replay_count",
  "outbox_pending_count",
  "outbox_dead_letter_count",
  "authorization_denied_count",
  "audit_append_failure_count",
  "payment_instruction_failure_count",
  "notification_dead_letter_count",
  "report_artifact_generation_failure_count"
];

const requiredRunbooks = [
  "docs/operations/runbooks/ledger-drift.md",
  "docs/operations/runbooks/outbox-dead-letter.md",
  "docs/operations/runbooks/authz-denial-spike.md",
  "docs/operations/runbooks/eod-failure.md",
  "docs/operations/runbooks/postgres-restore.md"
];

const requiredRunbookSections = [
  "## Symptoms",
  "## Detection",
  "## Immediate Containment",
  "## Diagnosis Queries",
  "## Recovery Steps",
  "## Evidence To Capture",
  "## Rollback",
  "## Escalation",
  "## Post-incident Review"
];

const requiredFiles = [
  "infra/observability/prometheus-rules.yaml",
  "infra/observability/grafana-dashboard-core-banking.json",
  "infra/observability/grafana-dashboard-outbox.json",
  "docs/operations/slo.md",
  "docs/operations/observability.md",
  ...requiredRunbooks
];

const errors: string[] = [];
const contents = new Map<string, string>();

for (const file of requiredFiles) {
  try {
    contents.set(file, await readFile(file, "utf8"));
  } catch {
    errors.push(`Missing observability file: ${file}`);
  }
}

for (const dashboard of [
  "infra/observability/grafana-dashboard-core-banking.json",
  "infra/observability/grafana-dashboard-outbox.json"
]) {
  const content = contents.get(dashboard);
  if (content) {
    try {
      const parsed = JSON.parse(content);
      if (!parsed.title || !Array.isArray(parsed.panels) || parsed.panels.length === 0) {
        errors.push(`${dashboard} must define title and non-empty panels.`);
      }
    } catch (error) {
      errors.push(`${dashboard} is not valid JSON: ${String(error)}`);
    }
  }
}

const combined = [...contents.values()].join("\n");
for (const metric of requiredMetrics) {
  if (!combined.includes(metric)) {
    errors.push(`Required metric ${metric} is missing from observability docs/assets.`);
  }
}

const prometheusRules = contents.get("infra/observability/prometheus-rules.yaml") ?? "";
for (const alert of [
  "LedgerCommandLatencyHigh",
  "LedgerCommandErrorRateHigh",
  "IdempotencyReplaySpike",
  "OutboxPendingBacklog",
  "OutboxDeadLetterPresent",
  "AuthorizationDeniedSpike",
  "AuditAppendFailure",
  "PaymentInstructionFailures",
  "NotificationDeadLetterPresent",
  "ReportArtifactGenerationFailures"
]) {
  if (!prometheusRules.includes(`alert: ${alert}`)) {
    errors.push(`Missing Prometheus alert ${alert}.`);
  }
}

for (const runbook of requiredRunbooks) {
  const referenceFound = prometheusRules.includes(runbook) || combined.includes(path.basename(runbook));
  if (!referenceFound) {
    errors.push(`Observability assets do not reference ${runbook}.`);
  }
  const runbookContent = contents.get(runbook) ?? "";
  for (const section of requiredRunbookSections) {
    if (!runbookContent.includes(section)) {
      errors.push(`${runbook} missing section ${section}.`);
    }
  }
}

if (errors.length > 0) {
  console.error("Observability validation failed:");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log(`Observability validation passed for ${requiredFiles.length} files and ${requiredMetrics.length} metrics.`);
