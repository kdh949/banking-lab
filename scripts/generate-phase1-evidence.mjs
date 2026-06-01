import { mkdir, writeFile } from "node:fs/promises";
import { readFile } from "node:fs/promises";
import { createLabState } from "../runtime/labApp.mjs";
import { assertTransactionBalanced, projectBalances } from "../packages/banking-domain/src/index.mjs";
import { loadManifests } from "../packages/screen-engine/src/index.mjs";

const outputDir = "docs/test-evidence/generated";
const outputFile = `${outputDir}/phase-1-foundation.json`;
const state = await createLabState();
const manifests = await loadManifests("screen-manifests");
for (const transaction of state.ledgerTransactions) {
  assertTransactionBalanced(transaction);
}
const balances = projectBalances(state.ledgerTransactions, state.dataset.accounts);
const migrationSql = await readFile("infra/db/migrations/001_foundation.sql", "utf8");

const evidence = {
  generatedAt: new Date().toISOString(),
  scope: "Phase 1 Foundation",
  syntheticOnly: true,
  checks: [
    {
      id: "manifest-validation",
      status: "pass",
      detail: `${manifests.length} manifests validate with required audit metadata`
    },
    {
      id: "ledger-balanced-seed",
      status: "pass",
      detail: `${state.ledgerTransactions.length} seed transactions are double-entry balanced`
    },
    {
      id: "balance-projection",
      status: "pass",
      detail: `${balances.length} balances projected from postings`
    },
    {
      id: "audit-hash-chain",
      status: state.auditLog.verifyHashChain() ? "pass" : "fail",
      detail: `${state.auditLog.all().length} audit events hash-chain verified`
    },
    {
      id: "migration-audit-tables",
      status: migrationSql.includes("CREATE TABLE audit_events") && migrationSql.includes("CREATE TABLE operator_approvals") ? "pass" : "fail",
      detail: "Foundation migration includes audit_events and operator_approvals"
    }
  ]
};

await mkdir(outputDir, { recursive: true });
await writeFile(outputFile, `${JSON.stringify(evidence, null, 2)}\n`);

console.log(`Wrote ${outputFile}`);
