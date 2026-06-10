import { access, mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname } from "node:path";

type AuditStatus = "pass" | "partial" | "blocked" | "failed";

type AuditRequirement = {
  readonly id: string;
  readonly planCondition: string;
  readonly status: AuditStatus;
  readonly evidence: readonly string[];
  readonly finding: string;
  readonly nextStep: string;
};

type PortfolioCompletionAudit = {
  readonly reviewDate: "2026-06-11";
  readonly syntheticOnly: true;
  readonly issue: "https://github.com/kdh949/banking-lab/issues/86";
  readonly overallStatus: "complete" | "not-complete";
  readonly statusCounts: Record<AuditStatus, number>;
  readonly requireCompleteCommand: "npm run portfolio:completion-audit -- --require-complete";
  readonly requirements: readonly AuditRequirement[];
};

type PackageJson = {
  readonly scripts?: Record<string, string>;
};

type LiveRouteEvidence = {
  readonly syntheticOnly?: unknown;
  readonly status?: unknown;
  readonly skippedPlaywrightIsPassEvidence?: unknown;
};

type ContractRuntimeEvidence = {
  readonly syntheticOnly?: unknown;
  readonly status?: unknown;
  readonly openApi?: {
    readonly generatedDtoDiffGate?: {
      readonly status?: unknown;
      readonly expectedScript?: unknown;
    };
  };
  readonly asyncApi?: {
    readonly runtimeEventValidationGate?: {
      readonly status?: unknown;
      readonly expectedScript?: unknown;
    };
  };
};

type RuntimeEventEvidence = {
  readonly syntheticOnly?: unknown;
  readonly status?: unknown;
  readonly validatedEnvelopeCount?: unknown;
  readonly sourceMarkerFindings?: unknown;
};

type CallCenterEvidence = {
  readonly syntheticOnly?: unknown;
  readonly implemented?: {
    readonly nextShell?: unknown;
    readonly keycloakClient?: unknown;
  };
  readonly verification?: unknown;
};

const requireComplete = process.argv.includes("--require-complete");
const generatedPath = "docs/test-evidence/generated/portfolio-completion-audit.json";
const errors: string[] = [];

async function exists(path: string): Promise<boolean> {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
}

async function read(path: string): Promise<string> {
  try {
    return await readFile(path, "utf8");
  } catch (error) {
    errors.push(`missing or unreadable evidence path ${path}: ${(error as Error).message}`);
    return "";
  }
}

async function readJson<T>(path: string): Promise<T | undefined> {
  const source = await read(path);
  if (!source) return undefined;
  try {
    return JSON.parse(source) as T;
  } catch (error) {
    errors.push(`invalid JSON evidence at ${path}: ${(error as Error).message}`);
    return undefined;
  }
}

function includesAll(source: string, markers: readonly string[]): boolean {
  return markers.every((marker) => source.includes(marker));
}

function objectArray<T>(value: unknown): T[] {
  return Array.isArray(value) ? value.filter((item): item is T => typeof item === "object" && item !== null) : [];
}

function statusCounts(requirements: readonly AuditRequirement[]): Record<AuditStatus, number> {
  return {
    pass: requirements.filter((item) => item.status === "pass").length,
    partial: requirements.filter((item) => item.status === "partial").length,
    blocked: requirements.filter((item) => item.status === "blocked").length,
    failed: requirements.filter((item) => item.status === "failed").length
  };
}

function requirement(
  id: string,
  planCondition: string,
  status: AuditStatus,
  evidence: readonly string[],
  finding: string,
  nextStep: string
): AuditRequirement {
  return { id, planCondition, status, evidence, finding, nextStep };
}

const packageJson = await readJson<PackageJson>("package.json");
const readme = await read("README.md");
const matrix = await read("docs/implementation-coverage-matrix.md");
const hostedCi = await read("docs/test-evidence/ci-hosted-run-status.md");
const liveRouteDoc = await read("docs/test-evidence/live-route-api-execution.md");
const liveRoute = await readJson<LiveRouteEvidence>("docs/test-evidence/generated/live-route-api-execution.json");
const contractDoc = await read("docs/test-evidence/contract-runtime-evidence-boundary.md");
const contractRuntime = await readJson<ContractRuntimeEvidence>("docs/test-evidence/generated/contract-runtime-evidence.json");
const runtimeEvents = await readJson<RuntimeEventEvidence>("docs/test-evidence/generated/event-envelope-runtime-validation.json");
const callCenterDoc = await read("docs/test-evidence/call-center-console.md");
const callCenter = await readJson<CallCenterEvidence>("docs/test-evidence/generated/call-center-console.json");
const scorecard = await read("docs/test-evidence/final-hardening-scorecard.md");
const demoScript = await read("docs/demo-scenarios/demo-video-script.md");
const gapReport = await read("docs/test-evidence/evidence-gap-report.md");

const packageScripts = packageJson?.scripts ?? {};
const requirements: AuditRequirement[] = [];

const documentationAligned = includesAll(matrix, [
  "route-backed-live-gated",
  "backend-control-covered",
  "partial"
]) && includesAll(readme, [
  "All data is synthetic",
  "CI is defined",
  "call-center"
]) && includesAll(gapReport, [
  "Recommended Next Evidence Slice",
  "hosted GitHub Actions run URLs",
  "live route-to-API execution evidence"
]);
requirements.push(requirement(
  "documentation-status-alignment",
  "README, coverage matrix, and evidence/status documents have no stale status mismatch.",
  documentationAligned ? "pass" : "failed",
  [
    "README.md",
    "docs/implementation-coverage-matrix.md",
    "docs/test-evidence/evidence-gap-report.md"
  ],
  documentationAligned
    ? "Status vocabulary and remaining evidence slices are explicitly documented without production-readiness claims."
    : "Documentation status vocabulary or remaining-slice markers are missing.",
  "Keep README, coverage matrix, and evidence gap report updated after every hardening PR."
));

const hostedCiBlocked = includesAll(hostedCi, [
  "Run status: blocked",
  "runner_id: 0",
  "steps: 0",
  "issues/83",
  "Local fallback commands are not hosted CI green"
]);
requirements.push(requirement(
  "hosted-ci-evidence",
  "Hosted GitHub Actions is green or the external blocker is clearly documented.",
  hostedCiBlocked ? "blocked" : "failed",
  ["docs/test-evidence/ci-hosted-run-status.md", "https://github.com/kdh949/banking-lab/issues/83"],
  hostedCiBlocked
    ? "Hosted CI remains blocked before runner startup and is separated from local fallback evidence."
    : "Hosted CI blocker evidence is missing or no longer clearly separated from local fallback commands.",
  "Resolve #83, rerun hosted CI, and replace blocked status only after jobs execute runner steps."
));

const liveRoutePartial = liveRoute?.syntheticOnly === true
  && liveRoute.status === "partial"
  && liveRoute.skippedPlaywrightIsPassEvidence === false
  && liveRouteDoc.includes("npm run test:staff-terminal:api-e2e-compose");
requirements.push(requirement(
  "live-route-api-execution",
  "Customer-web and staff-terminal live API route execution evidence exists.",
  liveRoutePartial ? "partial" : "failed",
  [
    "docs/test-evidence/live-route-api-execution.md",
    "docs/test-evidence/generated/live-route-api-execution.json"
  ],
  liveRoutePartial
    ? "Evidence exists, but customer/staff route execution is still environment-gated and partial."
    : "Live route/API evidence is missing or overclaims skipped Playwright as pass evidence.",
  "Rerun customer-web and staff-terminal live API/Keycloak smokes against a fresh synthetic stack."
));

const openApiDiffPresent = packageScripts["contracts:diff-openapi"] === "node --experimental-strip-types scripts/check-openapi-generated-diff.ts"
  && await exists("scripts/check-openapi-generated-diff.ts")
  && await exists("docs/test-evidence/generated/openapi/core-banking.generated.json")
  && await exists("docs/test-evidence/generated/openapi/payment-service.generated.json")
  && await exists("docs/test-evidence/generated/openapi/notification-service.generated.json")
  && await exists("docs/test-evidence/generated/openapi/reporting-service.generated.json")
  && contractRuntime?.openApi?.generatedDtoDiffGate?.expectedScript === "contracts:diff-openapi";
requirements.push(requirement(
  "openapi-dto-diff-gate",
  "OpenAPI DTO-level diffing gate exists.",
  openApiDiffPresent ? "pass" : "failed",
  [
    "scripts/check-openapi-generated-diff.ts",
    "docs/test-evidence/generated/openapi/*.generated.json",
    "docs/test-evidence/contract-runtime-evidence-boundary.md"
  ],
  openApiDiffPresent
    ? "Kotlin controller source diffing and generated DTO snapshots are wired, with documented core-banking scope limits."
    : "OpenAPI generated diff script, snapshots, or evidence linkage is missing.",
  "Extend generated DTO parity to the remaining core-banking API subsets."
));

const runtimeEventGatePresent = packageScripts["contracts:validate-runtime-events"] === "node --experimental-strip-types scripts/validate-event-envelope-runtime.ts"
  && runtimeEvents?.syntheticOnly === true
  && runtimeEvents.status === "pass"
  && typeof runtimeEvents.validatedEnvelopeCount === "number"
  && objectArray(runtimeEvents.sourceMarkerFindings).length >= 4
  && contractRuntime?.asyncApi?.runtimeEventValidationGate?.expectedScript === "contracts:validate-runtime-events"
  && contractDoc.includes("Do not claim this fixture/schema/source-marker gate is equivalent to exhaustive live broker producer/consumer coverage");
requirements.push(requirement(
  "event-envelope-runtime-gate",
  "Event envelope runtime validation gate exists.",
  runtimeEventGatePresent ? "pass" : "failed",
  [
    "scripts/validate-event-envelope-runtime.ts",
    "docs/test-evidence/generated/event-envelope-runtime-validation.json",
    "docs/test-evidence/contract-runtime-evidence-boundary.md"
  ],
  runtimeEventGatePresent
    ? "Runtime envelope fixture validation and source-marker checks are wired without claiming exhaustive live broker coverage."
    : "Runtime event validation script, generated evidence, or overclaim guard is missing.",
  "Broaden live broker producer/consumer envelope validation in service integration tests."
));

const callCenterImplemented = callCenter?.syntheticOnly === true
  && callCenter?.implemented?.nextShell === "@banking-lab/call-center-console"
  && callCenter?.implemented?.keycloakClient === "call-center-console"
  && includesAll(callCenterDoc, [
    "GET /api/staff/call-center/customers/search",
    "POST /api/staff/call-center/interactions",
    "POST /api/staff/call-center/interactions/{interactionId}/notes",
    "POST /api/staff/call-center/interactions/{interactionId}/aftercall-tasks",
    "POST /api/staff/call-center/interactions/{interactionId}/escalations",
    "GET /api/staff/call-center/customers/{customerId}/history"
  ]);
requirements.push(requirement(
  "call-center-api-backed-workflow",
  "Customer-service call-center console is API-backed.",
  callCenterImplemented ? "pass" : "failed",
  [
    "docs/test-evidence/call-center-console.md",
    "docs/test-evidence/generated/call-center-console.json",
    "apps/call-center-console"
  ],
  callCenterImplemented
    ? "Dedicated call-center shell, Spring API routes, API client, and local Keycloak evidence are documented."
    : "Call-center API-backed shell or Spring route evidence is incomplete.",
  "Keep the call-center live Compose wrapper fresh before demo or release evidence."
));

const callCenterControls = includesAll(callCenterDoc, [
  "Customer search and history expose masked customer data only",
  "note bodies are redacted",
  "aftercall tasks",
  "CALL_CENTER_ESCALATION",
  "maker-checker",
  "call_center_access_audit"
]);
requirements.push(requirement(
  "call-center-controls",
  "Call-center history, notes, aftercall, escalation, and audit are implemented.",
  callCenterControls ? "pass" : "failed",
  ["docs/test-evidence/call-center-console.md", "services/core-banking/src/main/kotlin"],
  callCenterControls
    ? "Call-center notes are redacted, history and aftercall tasks are present, and escalation is maker-checker protected."
    : "Call-center control evidence is missing required history/note/aftercall/escalation/audit markers.",
  "Add targeted failure-state tests only when they close a demo or control gap."
));

const syntheticBoundary = includesAll(scorecard, [
  "Synthetic boundary",
  "no real customer money",
  "real PII",
  "real KYC/AML provider",
  "payment network"
]) && includesAll(demoScript, [
  "synthetic banking lab",
  "real money",
  "real PII"
]);
requirements.push(requirement(
  "synthetic-only-boundary",
  "All new features preserve the synthetic-only boundary.",
  syntheticBoundary ? "pass" : "failed",
  [
    "docs/test-evidence/final-hardening-scorecard.md",
    "docs/demo-scenarios/demo-video-script.md"
  ],
  syntheticBoundary
    ? "Final portfolio evidence explicitly excludes real money, real PII, real providers, and real financial networks."
    : "Synthetic-only exclusions are missing from final portfolio evidence.",
  "Keep real-provider adapters and real data out of this repository."
));

const highRiskControls = includesAll(scorecard, [
  "reason-required",
  "maker-checker",
  "audit"
]) && includesAll(matrix, [
  "CALL_CENTER_ESCALATION",
  "structured-error",
  "self-approval rejection",
  "backend-control-covered"
]);
requirements.push(requirement(
  "high-risk-control-consistency",
  "New high-risk commands have reason, authorization, maker-checker, audit, and structured error controls.",
  highRiskControls ? "pass" : "failed",
  [
    "docs/implementation-coverage-matrix.md",
    "docs/test-evidence/final-hardening-scorecard.md"
  ],
  highRiskControls
    ? "High-risk controls are represented across backend controls and call-center escalation evidence."
    : "High-risk command control evidence is incomplete or inconsistent.",
  "Keep adding control evidence with every new high-risk command."
));

const finalDocsCurrent = includesAll(scorecard, [
  "Hosted CI",
  "Contract/event validation",
  "Call-center",
  "Verification Commands To Refresh Before Recording"
]) && includesAll(demoScript, [
  "hosted CI blocked status, not green",
  "dedicated Next.js call-center shell",
  "runtime event envelope fixture/source-marker gate"
]);
requirements.push(requirement(
  "final-scorecard-demo-current",
  "Final scorecard and demo script are current.",
  finalDocsCurrent ? "pass" : "failed",
  [
    "docs/test-evidence/final-hardening-scorecard.md",
    "docs/demo-scenarios/demo-video-script.md"
  ],
  finalDocsCurrent
    ? "Final scorecard and demo script disclose hosted CI, call-center, and contract/runtime boundaries."
    : "Final scorecard or demo script is missing current boundary markers.",
  "Refresh both documents after each portfolio-hardening merge."
));

const commandCoverage = includesAll(scorecard, [
  "npm test",
  "npm run validate:manifests",
  "npm run packages:typecheck",
  "npm run scripts:typecheck",
  "npm run contracts:lint",
  "npm run contracts:check-client",
  "npm run contracts:check-events",
  "npm run contracts:diff-openapi",
  "npm run contracts:validate-runtime-events",
  "npm run platform:validate",
  "npm run security:posture-check",
  "npm run formal:ledger",
  "npm run evidence:pack"
]);
requirements.push(requirement(
  "final-command-refresh",
  "Required final commands pass or have explicit blocked evidence.",
  commandCoverage ? "partial" : "failed",
  ["docs/test-evidence/final-hardening-scorecard.md"],
  commandCoverage
    ? "Final command list is documented, but this audit does not prove all commands were freshly rerun in the current branch."
    : "Final command list is missing required PLAN commands.",
  "Run the full local refresh list and record command results in the next release evidence pack."
));

const limitationsDocumented = includesAll(scorecard, [
  "Remaining limitation",
  "not hosted CI",
  "production identity deployment",
  "not green"
]) && includesAll(gapReport, [
  "Evidence Quality Risks",
  "Recommended Next Evidence Slice"
]);
requirements.push(requirement(
  "limitations-preserved",
  "Remaining limitations are documented instead of deleted.",
  limitationsDocumented ? "pass" : "failed",
  ["docs/test-evidence/final-hardening-scorecard.md", "docs/test-evidence/evidence-gap-report.md"],
  limitationsDocumented
    ? "Remaining risks and evidence-quality limits are preserved in the final portfolio documents."
    : "Limitations are missing from scorecard or evidence gap report.",
  "Keep limitation rows specific and update them when evidence improves."
));

for (const error of errors) {
  requirements.push(requirement(
    `source-read-error-${requirements.length + 1}`,
    "All audit source evidence files are readable.",
    "failed",
    [],
    error,
    "Restore the missing or invalid evidence file."
  ));
}

const counts = statusCounts(requirements);
const complete = counts.failed === 0 && counts.blocked === 0 && counts.partial === 0;
const audit: PortfolioCompletionAudit = {
  reviewDate: "2026-06-11",
  syntheticOnly: true,
  issue: "https://github.com/kdh949/banking-lab/issues/86",
  overallStatus: complete ? "complete" : "not-complete",
  statusCounts: counts,
  requireCompleteCommand: "npm run portfolio:completion-audit -- --require-complete",
  requirements
};

await mkdir(dirname(generatedPath), { recursive: true });
await writeFile(generatedPath, `${JSON.stringify(audit, null, 2)}\n`);

console.log(`Portfolio completion audit: ${audit.overallStatus}`);
console.log(`- pass: ${counts.pass}`);
console.log(`- partial: ${counts.partial}`);
console.log(`- blocked: ${counts.blocked}`);
console.log(`- failed: ${counts.failed}`);
for (const item of requirements) {
  console.log(`- ${item.id}: ${item.status} (${item.finding})`);
}

if (counts.failed > 0) {
  console.error("Portfolio completion audit failed:");
  for (const item of requirements.filter((entry) => entry.status === "failed")) {
    console.error(`- ${item.id}: ${item.finding}`);
  }
  process.exit(1);
}

if (requireComplete && !complete) {
  console.error("Portfolio completion is not complete:");
  for (const item of requirements.filter((entry) => entry.status === "blocked" || entry.status === "partial")) {
    console.error(`- ${item.id}: ${item.status} - ${item.nextStep}`);
  }
  process.exit(1);
}
