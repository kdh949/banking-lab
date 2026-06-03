import assert from "node:assert/strict";
import test from "node:test";
import { loadManifests } from "../legacy-node-reference/packages/screen-engine/src/index.mjs";
import {
  fieldsFromManifest,
  validateManifestSubmission
} from "../legacy-node-reference/packages/form-engine/src/index.mjs";

test("form engine reads reusable field contracts from command and parameter manifests", async () => {
  const manifests = await loadManifests("screen-manifests");
  const transfer = manifests.find((manifest) => manifest.screenId === "CWB-201");
  const fdsParameter = manifests.find((manifest) => manifest.screenId === "FDS-301");

  assert.deepEqual(
    fieldsFromManifest(transfer).map((field) => field.name),
    ["fromAccountId", "toAccountId", "amountMinor", "idempotencyKey"]
  );
  assert.ok(fieldsFromManifest(fdsParameter).some((field) => field.name === "rollbackPlan"));
});

test("manifest submission validation applies required fields and reason convention", async () => {
  const manifests = await loadManifests("screen-manifests");
  const customerChange = manifests.find((manifest) => manifest.screenId === "CST-103");

  const invalid = validateManifestSubmission(customerChange, {
    customerId: "CUST-001",
    reason: "short"
  });
  assert.equal(invalid.ok, false);
  assert.ok(invalid.errors.some((error) => error.field === "reason"));

  const valid = validateManifestSubmission(customerChange, {
    customerId: "CUST-001",
    reason: "Customer requested contact information correction"
  });
  assert.equal(valid.ok, true);
});
