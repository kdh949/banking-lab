import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";

const statePath = process.env.BANKING_LAB_CHANNEL_DEMO_STATE_PATH ?? "/tmp/banking-lab-channel-demo-state.json";
const outputPath = "docs/test-evidence/generated/cross-channel-held-transfer-demo.json";
const screenshots = [
  "docs/assets/channel-workbench/customer-held-transfer.png",
  "docs/assets/channel-workbench/call-center-fds-handoff.png",
  "docs/assets/channel-workbench/staff-fds201-release-request.png",
  "docs/assets/channel-workbench/staff-apr101-checker-release.png",
  "docs/assets/channel-workbench/customer-posted-transfer.png",
  "docs/assets/channel-workbench/customer-transfer-notification.png"
];

if (!existsSync(statePath)) {
  throw new Error(`Cross-channel demo state is missing: ${statePath}`);
}
for (const screenshot of screenshots) {
  if (!existsSync(screenshot)) {
    throw new Error(`Cross-channel demo screenshot is missing: ${screenshot}`);
  }
}
const state = JSON.parse(readFileSync(statePath, "utf8"));
if (state.release?.status !== "POSTED" || state.block?.status !== "BLOCKED" || state.browserErrors?.length !== 0) {
  throw new Error("Cross-channel demo state is not a passing POSTED/BLOCKED run.");
}

const evidence = {
  reviewDate: "2026-08-10",
  executedAt: new Date().toISOString(),
  status: "pass",
  syntheticOnly: true,
  localOnly: true,
  hostedCiGreenClaim: false,
  command: "npm run demo:test:channels",
  scenario: "customer HELD -> call-center masked handoff -> FDS201 maker -> APR101 checker -> POSTED notification, plus BLOCKED zero-ledger branch",
  databaseVerification: "scripts/verify-cross-channel-held-transfer-demo.sql",
  screenshots,
  state,
  controls: [
    "HELD creates zero ledger transactions",
    "other-customer journey, transfer, and account reads are denied",
    "call-center lookup requires reason and returns masked context",
    "call-center note is redacted before persistence",
    "risk01 maker is separated from manager01 checker",
    "release retry leaves exactly one balanced double-entry transaction",
    "block leaves zero ledger transactions",
    "POSTED and BLOCKED each create one durable customer notification",
    "journey audit events retain actor, reason, requestId, and traceId"
  ]
};

mkdirSync(dirname(outputPath), { recursive: true });
writeFileSync(outputPath, `${JSON.stringify(evidence, null, 2)}\n`);
console.log(`Recorded passing cross-channel evidence at ${outputPath}`);
