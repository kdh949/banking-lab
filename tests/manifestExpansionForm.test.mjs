import assert from "node:assert/strict";
import test from "node:test";
import { loadManifests } from "../runtime/synthetic-reference/packages/screen-engine/src/index.mjs";
import {
  fieldsFromManifest,
  validateManifestSubmission
} from "../runtime/synthetic-reference/packages/form-engine/src/index.mjs";

test("form engine reads reusable field contracts from command and parameter manifests", async () => {
  const manifests = await loadManifests("screen-manifests");
  const transfer = manifests.find((manifest) => manifest.screenId === "CWB-201");
  const fdsParameter = manifests.find((manifest) => manifest.screenId === "FDS-301");

  assert.deepEqual(
    fieldsFromManifest(transfer).map((field) => field.name),
    ["fromAccountId", "recipientQuery", "toAccountId", "amountMinor", "idempotencyKey"]
  );
  assert.ok(fieldsFromManifest(fdsParameter).some((field) => field.name === "rollbackPlan"));
});

test("manifest submission validation applies required fields and reason convention", async () => {
  const manifests = await loadManifests("screen-manifests");
  const parameterChange = manifests.find((manifest) => manifest.screenId === "ADM-201");

  const invalid = validateManifestSubmission(parameterChange, {
    parameterKey: "SESSION_TIMEOUT_MINUTES",
    scheduledValue: "30",
    effectiveAt: "2026-06-10T09:00:00+09:00",
    rollbackPlan: "Restore previous value",
    reason: "short"
  });
  assert.equal(invalid.ok, false);
  assert.ok(invalid.errors.some((error) => error.field === "reason"));

  const valid = validateManifestSubmission(parameterChange, {
    parameterKey: "SESSION_TIMEOUT_MINUTES",
    scheduledValue: "30",
    effectiveAt: "2026-06-10T09:00:00+09:00",
    rollbackPlan: "Restore previous value",
    reason: "Security parameter review"
  });
  assert.equal(valid.ok, true);
});
