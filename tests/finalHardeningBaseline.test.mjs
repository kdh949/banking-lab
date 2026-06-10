import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("final hardening baseline reflects current implementation without overclaiming", async () => {
  const baseline = await readFile("docs/codex/final-hardening-baseline.md", "utf8");
  const forbiddenReadinessClaims = new RegExp(
    [
      ["production", "ready"].join("-"),
      ["real banking", "ready"].join(" "),
      ["actual payment network", "ready"].join(" ")
    ].join("|"),
    "i"
  );

  assert.match(baseline, /Review date: 2026-06-10/);
  assert.match(baseline, /PR #69 through\s+PR #73/);
  assert.match(baseline, /Node oracle tests under `tests\/\*\.test\.mjs`/);

  assert.match(baseline, /call-center console/);
  assert.match(baseline, /`call-center-console` now has a dedicated Next\.js shell/);
  assert.match(baseline, /manifests `CALL-101` through `CALL-106`/);
  assert.match(baseline, /reason-required access audit/);
  assert.match(baseline, /Keycloak public client, synthetic\s+call-center agent\/manager users, a Next token exchange route/);
  assert.match(baseline, /local\s+disposable Compose evidence for live Keycloak\/JWKS agent\/manager propagation/);
  assert.match(baseline, /maker-checker escalation remains open/);
  assert.doesNotMatch(baseline, /call-center agent workflow is missing/i);

  assert.match(baseline, /DTO-level OpenAPI diffing and runtime event-envelope validation now exist/);
  assert.match(baseline, /Full core-banking\s+Spring\/Jackson\/springdoc DTO parity and exhaustive live broker envelope\s+coverage remain open/);

  assert.match(baseline, /Hosted GitHub Actions/);
  assert.match(baseline, /blocked hosted-CI condition,\s+not a green CI result/);
  assert.match(baseline, /A skipped live API test is\s+not proof of route-to-live-API execution/);

  assert.match(baseline, /no real customer money/);
  assert.match(baseline, /no real personal data/);
  assert.match(baseline, /no real KYC, sanctions, credit, card, payment, Open Banking, or financial\s+network provider/);
  assert.doesNotMatch(baseline, forbiddenReadinessClaims);
});
