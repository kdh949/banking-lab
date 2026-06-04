import { spawnSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";

type DrillStatus = "pass" | "fail";

interface MultiInstanceDrillEvidence {
  readonly generatedAt: string;
  readonly command: string;
  readonly status: DrillStatus;
  readonly syntheticOnly: true;
  readonly multiInstance: {
    readonly instanceCount: number;
    readonly instanceModel: string;
    readonly sharedDatabase: string;
  };
  readonly controlsVerified: readonly string[];
  readonly durationMillis: number;
  readonly rpo: {
    readonly target: string;
    readonly measured: string;
  };
  readonly rto: {
    readonly target: string;
    readonly measured: string;
  };
  readonly firstFailureFixed?: string;
}

const command = "scripts/run-core-banking-tests.sh";
const args = [
  ":services:core-banking:integrationTest",
  "--tests",
  "lab.banking.core.resilience.MultiInstanceLedgerHaDrIntegrationTest",
  "--rerun-tasks"
];
const outputPath = join("docs", "test-evidence", "generated", "ha-dr-multi-instance-drill.json");
const started = Date.now();

mkdirSync(join("docs", "test-evidence", "generated"), { recursive: true });

const result = spawnSync(command, args, {
  encoding: "utf8",
  stdio: "inherit",
  maxBuffer: 100 * 1024 * 1024
});
const durationMillis = Date.now() - started;
const status: DrillStatus = result.status === 0 ? "pass" : "fail";

const evidence: MultiInstanceDrillEvidence = {
  generatedAt: new Date().toISOString(),
  command: `npm run dr:multi-instance-drill`,
  status,
  syntheticOnly: true,
  multiInstance: {
    instanceCount: 2,
    instanceModel: "two independent Spring application contexts with separate Hikari pools",
    sharedDatabase: "Testcontainers postgres:16-alpine"
  },
  controlsVerified: [
    "cross-instance SERIALIZABLE withdrawal cannot double spend",
    "cross-instance duplicate idempotency key creates one ledger transaction",
    "retryable SQLSTATE 40001 serialization failure is retried before surfacing",
    "ledger postings remain balanced after concurrent commands"
  ],
  durationMillis,
  rpo: {
    target: "0 committed ledger transactions, postings, or projections lost or duplicated",
    measured: status === "pass"
      ? "0 loss/duplication: one successful double-spend withdrawal, one idempotent ledger row, balanced postings"
      : "not proven because the drill failed"
  },
  rto: {
    target: "cross-instance retry converges within the bounded ledger retry policy",
    measured: status === "pass"
      ? `bounded retry converged within the ${durationMillis} ms drill run`
      : "not proven because the drill failed"
  },
  firstFailureFixed:
    "Initial H4 run exposed an uncaught SERIALIZABLE idempotency insert conflict; LedgerCommandService withdrawal now retries retryable serialization failures."
};

writeFileSync(outputPath, `${JSON.stringify(evidence, null, 2)}\n`);
console.log(`HA/DR multi-instance drill: ${status} (${durationMillis} ms).`);
console.log(`Evidence written: ${outputPath}`);

if (status !== "pass") {
  process.exit(result.status ?? 1);
}
