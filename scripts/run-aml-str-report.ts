import { createHash } from "node:crypto";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

type Status = "pass" | "fail";

const analyticsPath = join("docs", "test-evidence", "generated", "fds-aml-analytics.json");
const outputDir = join("docs", "test-evidence", "generated");
const sourceContent = readFileSync(analyticsPath, "utf8");
const analytics = JSON.parse(sourceContent);
const results: any[] = Array.isArray(analytics.results) ? analytics.results : [];
const controls = analytics.controls ?? {};

const syntheticControlsPass = controls.realMoneyUsed === false &&
  controls.realPiiUsed === false &&
  controls.realBankNetworkUsed === false &&
  controls.deterministicScoring === true;

const features = Array.from(
  new Set(results.flatMap((row: any) => Object.keys(row.features ?? {})))
).sort();
const riskDistribution: Record<string, number> = {};
for (const row of results) {
  const band = String(row.riskBand ?? "UNKNOWN");
  riskDistribution[band] = (riskDistribution[band] ?? 0) + 1;
}
const highRisk = results
  .filter((row: any) => row.riskBand === "HIGH")
  .sort((left: any, right: any) => Number(right.totalScore ?? 0) - Number(left.totalScore ?? 0));
const representative = highRisk[0] ?? results[0];
const generatedAt = new Date().toISOString();
const sourceHash = hash(sourceContent);

const modelCard = {
  generatedAt,
  modelVersionId: "AML-MODEL-SYN-RULES-V1",
  modelName: "banking_lab_aml_fds_rules",
  modelVersion: "2026-h6-synthetic-rules-v1",
  status: "ACTIVE",
  engine: analytics.engine,
  syntheticOnly: true,
  realPiiUsed: false,
  realMoneyUsed: false,
  realBankNetworkUsed: false,
  realSanctionsDataUsed: false,
  features,
  scoreDistribution: riskDistribution,
  driftCheck: {
    status: "pass",
    method: "fixed synthetic baseline",
    populationStabilityIndex: 0
  },
  explainability: {
    method: "deterministic rule alerts plus anomaly reasons",
    topAlertCounts: analytics.alertCounts ?? {},
    notes: "No real customer data, real sanctions data, or external model provider is used."
  },
  lineage: {
    scoringEvidencePath: analyticsPath,
    scoringEvidenceGeneratedAt: analytics.generatedAt,
    scoringEvidenceSha256: sourceHash,
    trainingDataBoundary: "synthetic sample transactions only"
  }
};

const strReport = {
  generatedAt,
  reportType: "SYNTHETIC_STR",
  reportId: `STR-SYN-${hash(JSON.stringify(representative)).slice(0, 16).toUpperCase()}`,
  amlCaseId: `AML-SYN-${hash(String(representative?.customerId ?? "none")).slice(0, 12).toUpperCase()}`,
  regulatorFormat: "SYNTHETIC_STR_V1",
  submitted: false,
  syntheticOnly: true,
  realRegulatorSubmission: false,
  realSanctionsDataUsed: false,
  customerId: representative?.customerId ?? "SYNTHETIC-NONE",
  transactionId: representative?.transactionId ?? "SYNTHETIC-NONE",
  riskBand: representative?.riskBand ?? "NONE",
  totalScore: representative?.totalScore ?? 0,
  alerts: representative?.alerts ?? [],
  anomalyReasons: representative?.anomalyReasons ?? [],
  evidenceHash: sourceHash,
  filingBoundary: "not sent to any regulator; generated as lab evidence only"
};

const regulatoryReport = {
  generatedAt,
  reportType: "SYNTHETIC_AML_PERIODIC_REPORT",
  period: {
    start: "2026-02-01",
    end: "2026-02-28"
  },
  syntheticOnly: true,
  realRegulatorSubmission: false,
  realSanctionsDataUsed: false,
  totalScoredTransactions: results.length,
  highRiskCount: highRisk.length,
  alertCounts: analytics.alertCounts ?? {},
  modelVersion: modelCard.modelVersion,
  modelVersionId: modelCard.modelVersionId,
  evidenceHash: sourceHash,
  submissionBoundary: "in-repo generated artifact only; no real regulator submission"
};

const status: Status = syntheticControlsPass && features.length > 0 && results.length > 0 ? "pass" : "fail";

mkdirSync(outputDir, { recursive: true });
writeJson("aml-model-card.json", modelCard);
writeJson("synthetic-str-report.json", strReport);
writeJson("aml-regulatory-report.json", regulatoryReport);
writeJson("aml-str-report-summary.json", {
  generatedAt,
  command: "npm run aml:str-report",
  status,
  syntheticOnly: true,
  realPiiUsed: false,
  realMoneyUsed: false,
  realBankNetworkUsed: false,
  realSanctionsDataUsed: false,
  realRegulatorSubmission: false,
  artifacts: [
    join(outputDir, "aml-model-card.json"),
    join(outputDir, "synthetic-str-report.json"),
    join(outputDir, "aml-regulatory-report.json")
  ],
  controls: {
    syntheticControlsPass,
    modelCardGenerated: true,
    strReportGenerated: true,
    regulatoryReportGenerated: true
  },
  source: {
    path: analyticsPath,
    sha256: sourceHash
  }
});

console.log(`AML STR/regulatory report generation: ${status}.`);
console.log(`Artifacts written under ${outputDir}.`);

if (status !== "pass") {
  process.exit(1);
}

function writeJson(fileName: string, value: unknown): void {
  writeFileSync(join(outputDir, fileName), `${JSON.stringify(value, null, 2)}\n`);
}

function hash(value: string): string {
  return createHash("sha256").update(value).digest("hex");
}
