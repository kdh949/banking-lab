import { spawnSync } from "node:child_process";
import { readFile } from "node:fs/promises";
import assert from "node:assert/strict";
import test from "node:test";

const evidencePath = "docs/test-evidence/generated/live-route-api-execution.json";
const docPath = "docs/test-evidence/live-route-api-execution.md";

test("live route API evidence generator records current customer and staff boundaries", async () => {
  const result = spawnSync(
    process.execPath,
    ["--experimental-strip-types", "scripts/check-live-route-api-evidence.ts"],
    { encoding: "utf8" }
  );

  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /Live route API evidence check passed/);

  const evidence = JSON.parse(await readFile(evidencePath, "utf8"));
  assert.equal(evidence.syntheticOnly, true);
  assert.equal(evidence.status, "partial");
  assert.equal(evidence.skippedPlaywrightIsPassEvidence, false);
  assert.match(evidence.statusReason, /customer-web and staff-terminal have route-backed live API tests gated by environment/);

  const customerWeb = evidence.applications.find((item) => item.app === "customer-web");
  assert.ok(customerWeb, "customer-web evidence is required");
  assert.equal(customerWeb.status, "route-backed-live-gated");
  assert.equal(customerWeb.liveRunEvidence, "source-present-env-gated-not-run-in-this-slice");
  assert.ok(customerWeb.routes.includes("/transfers/new"));
  assert.ok(customerWeb.routes.includes("/transfers/[resultId]"));
  assert.ok(customerWeb.requiredEnvironment.includes("BANKING_LAB_E2E_API_BASE_URL"));
  assert.ok(customerWeb.requiredEnvironment.includes("BANKING_LAB_E2E_STAFF_MAKER_BEARER_TOKEN"));
  assert.ok(customerWeb.apiMethods.includes("requestCustomerTransfer"));
  assert.ok(customerWeb.apiMethods.includes("customerTransactions"));
  assert.ok(customerWeb.states.includes("LEDGER_INSUFFICIENT_AVAILABLE_BALANCE"));
  assert.ok(customerWeb.controls.includes("idempotency replay assertion"));

  const staffTerminal = evidence.applications.find((item) => item.app === "staff-terminal");
  assert.ok(staffTerminal, "staff-terminal evidence is required");
  assert.equal(staffTerminal.status, "route-backed-live-gated");
  assert.equal(staffTerminal.liveRunEvidence, "source-present-compose-smoke-script-present");
  assert.deepEqual(staffTerminal.routes, ["/", "/api/terminal-status"]);
  assert.ok(staffTerminal.requiredEnvironment.includes("BANKING_LAB_E2E_API_BASE_URL"));
  assert.ok(staffTerminal.apiMethods.includes("staffCustomerDetail"));
  assert.ok(staffTerminal.apiMethods.includes("staffApprovals"));
  assert.ok(staffTerminal.controls.includes("bounded Spring API evidence panel"));
  assert.ok(staffTerminal.notes.some((note) => /does not restore the retired accounts/.test(note)));
});

test("live route API evidence document does not overclaim skipped or blocked work", async () => {
  const doc = await readFile(docPath, "utf8");

  assert.match(doc, /Status: partial/);
  assert.match(doc, /A skipped Playwright test is not pass evidence/);
  assert.match(doc, /bounded Spring API evidence panel/);
  assert.match(doc, /route-backed-live-gated/);
  assert.match(doc, /synthetic-only/);
  assert.match(doc, /real customer money, real personal data, real KYC\/AML providers, real payment or card networks, or external financial institution APIs/i);
  assert.doesNotMatch(doc, /staff-terminal.*hosted.*green/i);
  assert.doesNotMatch(doc, /Run status: green/);
});
