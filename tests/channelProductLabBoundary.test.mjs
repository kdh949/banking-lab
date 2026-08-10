import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

async function read(path) {
  return readFile(path, "utf8");
}

test("customer product route excludes manifest and raw API evidence surfaces", async () => {
  const [productPage, manifestCatalog, evidencePage] = await Promise.all([
    read("apps/customer-web/src/app/page.tsx"),
    read("apps/customer-web/src/components/CustomerManifestCatalog.tsx"),
    read("apps/customer-web/src/app/lab/evidence/page.tsx")
  ]);

  assert.doesNotMatch(productPage, /ApiBackedCustomerPanel|loadCustomerWebManifests|LAB_ONLY/);
  assert.match(productPage, /Customer Dashboard/);
  assert.match(manifestCatalog, /loadCustomerWebManifests|LAB_ONLY/);
  assert.match(evidencePage, /ApiBackedCustomerPanel/);
  assert.match(evidencePage, /LAB_ONLY/);
});

test("call-center product route excludes manifest and raw API evidence surfaces", async () => {
  const [productPage, workspace, manifestCatalog, evidencePage] = await Promise.all([
    read("apps/call-center-console/src/app/page.tsx"),
    read("apps/call-center-console/src/components/CallCenterWorkspace.tsx"),
    read("apps/call-center-console/src/components/CallCenterManifestCatalog.tsx"),
    read("apps/call-center-console/src/app/lab/evidence/page.tsx")
  ]);

  assert.doesNotMatch(productPage, /ApiBackedCallCenterPanel|loadChannelManifests|LAB_ONLY/);
  assert.match(productPage, /CallCenterWorkspace/);
  assert.match(workspace, /Agent Workspace/);
  assert.match(manifestCatalog, /loadChannelManifests|LAB_ONLY/);
  assert.match(evidencePage, /ApiBackedCallCenterPanel/);
  assert.match(evidencePage, /LAB_ONLY/);
});

test("staff portal excludes API evidence widget while lab route preserves it", async () => {
  const [portalScreens, evidencePage] = await Promise.all([
    read("apps/staff-terminal/src/components/terminal/screens.tsx"),
    read("apps/staff-terminal/src/app/lab/evidence/page.tsx")
  ]);

  assert.doesNotMatch(portalScreens, /StaffApiEvidencePanel/);
  assert.match(evidencePage, /StaffApiEvidencePanel/);
  assert.match(evidencePage, /LAB_ONLY/);
});
