import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

async function read(path) {
  return readFile(path, "utf8");
}

test("API client keeps root compatibility and exposes constrained domain entry points", async () => {
  const [packageJsonSource, index, client, customer, callCenter, staff, risk, operations] = await Promise.all([
    read("packages/api-client/package.json"),
    read("packages/api-client/src/index.ts"),
    read("packages/api-client/src/client.ts"),
    read("packages/api-client/src/domains/customer.ts"),
    read("packages/api-client/src/domains/call-center.ts"),
    read("packages/api-client/src/domains/staff.ts"),
    read("packages/api-client/src/domains/risk.ts"),
    read("packages/api-client/src/domains/operations.ts")
  ]);
  const packageJson = JSON.parse(packageJsonSource);

  assert.match(index, /export \* from "\.\/client"/);
  assert.match(client, /export function createBankingApiClient/);
  for (const domain of ["customer", "call-center", "staff", "risk", "operations"]) {
    assert.ok(packageJson.exports[`./${domain}`], `missing API client domain export: ${domain}`);
  }
  assert.match(customer, /createCustomerApiClient/);
  assert.match(callCenter, /createCallCenterApiClient/);
  assert.match(staff, /createStaffApiClient/);
  assert.match(risk, /createRiskApiClient/);
  assert.match(operations, /createOperationsApiClient/);
  for (const source of [customer, callCenter, staff, risk, operations]) {
    assert.match(source, /selectClientMethods/);
  }
});

test("channel UI uses one token and primitive source with customer and operator entries", async () => {
  const [tokens, primitives, customerUi, operatorUi, root] = await Promise.all([
    read("packages/channel-ui/src/design-tokens.css"),
    read("packages/channel-ui/src/primitives.tsx"),
    read("packages/channel-ui/src/customer-ui.tsx"),
    read("packages/channel-ui/src/operator-workbench.tsx"),
    read("packages/channel-ui/src/index.tsx")
  ]);

  assert.match(tokens, /--primary:/);
  assert.match(primitives, /export function ChannelShell/);
  assert.match(customerUi, /from "\.\/primitives"/);
  assert.match(operatorUi, /from "\.\/primitives"/);
  assert.match(root, /export \* from "\.\/primitives"/);
});

test("staff terminal separates static navigation from API workbench screens", async () => {
  const [screens, apiScreens, app] = await Promise.all([
    read("apps/staff-terminal/src/components/terminal/screens.tsx"),
    read("apps/staff-terminal/src/components/terminal/api-screens.tsx"),
    read("apps/staff-terminal/src/components/terminal/IntegratedTerminalApp.tsx")
  ]);

  assert.ok(screens.split("\n").length < 700, "static screen module regrew beyond its boundary");
  assert.match(screens, /from "\.\/api-screens"/);
  assert.match(apiScreens, /@banking-lab\/api-client\/staff/);
  assert.match(apiScreens, /@banking-lab\/api-client\/call-center/);
  assert.match(app, /from "\.\/screens"/);
});
