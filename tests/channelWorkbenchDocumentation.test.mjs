import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const requiredDocs = [
  "docs/product/channel-workbench.md",
  "docs/product/held-transfer-journey.md",
  "docs/product/screen-map.md",
  "docs/architecture/channel-security.md",
  "docs/architecture/payment-settlement-state-model.md",
  "docs/codex/channel-workbench-progress.md"
];

test("channel workbench documents keep settlement primary and define evidence labels", async () => {
  const [workbench, journey, screenMap, stateModel, progress] = await Promise.all([
    readFile(requiredDocs[0], "utf8"),
    readFile(requiredDocs[1], "utf8"),
    readFile(requiredDocs[2], "utf8"),
    readFile(requiredDocs[4], "utf8"),
    readFile(requiredDocs[5], "utf8")
  ]);

  assert.match(workbench, /Payment Settlement & Ledger Reliability slice remains the first message/);
  for (const label of ["LIVE_API", "SIMULATED", "REFERENCE", "PLANNED"]) {
    assert.equal(workbench.includes(`\`${label}\``), true, `${label} label is not documented`);
  }
  assert.match(journey, /journeyId/);
  assert.match(journey, /HELD[\s\S]*zero ledger transactions/);
  assert.match(journey, /POSTED[\s\S]*exactly one balanced transaction/);
  assert.match(screenMap, /`FDS201`/);
  assert.match(stateModel, /Neither state means an external institution moved money or final settlement occurred/);
  assert.match(progress, /all 12 Definition-of-Done items are complete/);
  assert.match(progress, /12\. One deterministic command reproduces the whole flow \| complete/);
});

test("channel security document forbids product bearer-token storage and internal customer disclosure", async () => {
  const security = await readFile("docs/architecture/channel-security.md", "utf8");

  assert.match(security, /Browser product JavaScript must not receive, read, log, or persist bearer tokens/);
  assert.match(security, /risk score, rule hits, FDS owner, maker\/checker identities/i);
  assert.match(security, /MAKER_CHECKER_SELF_APPROVAL_REJECTED/);
  assert.match(security, /W3C `traceparent`/);
});

test("agent and migration examples do not pin contributor home paths", async () => {
  const files = [
    ".agents/skills/ledger-invariant-review/SKILL.md",
    ".agents/skills/manifest-screen-generator/SKILL.md",
    "docs/migration/kotlin-next-playbook.md",
    "docs/migration/parity-scenarios.json"
  ];
  const contents = await Promise.all(files.map((file) => readFile(file, "utf8")));

  for (const [index, content] of contents.entries()) {
    assert.doesNotMatch(content, /\/Users\/donghyunkim/, `${files[index]} contains a contributor-machine path`);
  }
});
