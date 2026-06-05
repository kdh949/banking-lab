import assert from "node:assert/strict";
import test from "node:test";
import { access, readFile } from "node:fs/promises";

async function exists(filePath) {
  try {
    await access(filePath);
    return true;
  } catch {
    return false;
  }
}

test("customer-web Next workspace keeps the Node reference shell outside target app", async () => {
  const rootPackage = JSON.parse(await readFile("package.json", "utf8"));
  const appPackage = JSON.parse(await readFile("apps/customer-web/package.json", "utf8"));

  assert.equal(appPackage.name, "@banking-lab/customer-web");
  assert.match(appPackage.scripts.dev, /next dev/);
  assert.match(appPackage.scripts.build, /next build/);
  assert.equal(rootPackage.scripts["next:customer-web:typecheck"], "npm --workspace @banking-lab/customer-web run typecheck");
  assert.equal(await exists("apps/customer-web/public/index.html"), false);
  assert.equal(await exists("legacy-node-reference/apps/customer-web/public/index.html"), true);
});

test("customer-web Next page is manifest-driven rather than one-off screen code", async () => {
  const page = await readFile("apps/customer-web/src/app/page.tsx", "utf8");
  const loader = await readFile("apps/customer-web/src/lib/manifestLoader.ts", "utf8");

  assert.match(page, /loadCustomerWebManifests/);
  assert.match(loader, /screen-manifests/);
  assert.match(loader, /customer-web/);
  assert.match(loader, /manifest\.app === "customer-web"/);
});

test("Next dependency lock uses the postcss security override", async () => {
  const rootPackage = JSON.parse(await readFile("package.json", "utf8"));
  const lock = JSON.parse(await readFile("package-lock.json", "utf8"));

  assert.equal(rootPackage.overrides.postcss, "8.5.10");
  assert.equal(rootPackage.overrides.next.postcss, "8.5.10");
  assert.equal(lock.packages["node_modules/postcss"].version, "8.5.10");
  assert.equal(lock.packages["node_modules/next"].dependencies.postcss, "8.5.10");
});

test("admin-console Next workspace renders manifests and has a dedicated port", async () => {
  const rootPackage = JSON.parse(await readFile("package.json", "utf8"));
  const appPackage = JSON.parse(await readFile("apps/admin-console/package.json", "utf8"));
  const page = await readFile("apps/admin-console/src/app/page.tsx", "utf8");
  const panel = await readFile("apps/admin-console/src/components/ApiBackedAdminPanel.tsx", "utf8");
  const loader = await readFile("apps/admin-console/src/lib/manifestLoader.ts", "utf8");
  const securityManifest = JSON.parse(await readFile("screen-manifests/admin-console/ADM-201.security-policy-parameters.json", "utf8"));
  const notificationTemplateManifest = JSON.parse(await readFile("screen-manifests/admin-console/ADM-401.notification-template-approval.json", "utf8"));
  const notificationPreferenceManifest = JSON.parse(await readFile("screen-manifests/admin-console/ADM-402.notification-preference-management.json", "utf8"));

  assert.equal(appPackage.name, "@banking-lab/admin-console");
  assert.match(appPackage.scripts.dev, /3007/);
  assert.equal(rootPackage.scripts["next:admin-console:typecheck"], "npm --workspace @banking-lab/admin-console run typecheck");
  assert.match(page, /loadChannelManifests/);
  assert.match(page, /ApiBackedAdminPanel/);
  assert.match(panel, /NEXT_PUBLIC_BANKING_NOTIFICATION_API_BASE_URL/);
  assert.match(panel, /listNotificationTemplates/);
  assert.match(panel, /listNotificationPreferences/);
  assert.match(panel, /data-testid="api-backed-notification-admin"/);
  assert.match(loader, /admin-console/);
  assert.equal(securityManifest.type, "PARAMETER");
  assert.equal(securityManifest.approval.makerChecker, true);
  assert.equal(notificationTemplateManifest.approval.makerChecker, true);
  assert.equal(notificationPreferenceManifest.audit.reasonRequired, true);
  assert.equal(await exists("apps/admin-console/public/index.html"), false);
  assert.equal(await exists("legacy-node-reference/apps/admin-console/public/index.html"), true);
});

test("audit-console exposes notification delivery history through manifests and API client", async () => {
  const rootPackage = JSON.parse(await readFile("package.json", "utf8"));
  const appPackage = JSON.parse(await readFile("apps/audit-console/package.json", "utf8"));
  const page = await readFile("apps/audit-console/src/app/page.tsx", "utf8");
  const panel = await readFile("apps/audit-console/src/components/ApiBackedAuditPanel.tsx", "utf8");
  const loader = await readFile("apps/audit-console/src/lib/manifestLoader.ts", "utf8");
  const deliveryManifest = JSON.parse(await readFile("screen-manifests/audit-console/AUD-301.notification-delivery-history.json", "utf8"));

  assert.equal(appPackage.name, "@banking-lab/audit-console");
  assert.equal(rootPackage.scripts["next:audit-console:typecheck"], "npm --workspace @banking-lab/audit-console run typecheck");
  assert.match(page, /loadChannelManifests/);
  assert.match(page, /ApiBackedAuditPanel/);
  assert.match(panel, /NEXT_PUBLIC_BANKING_NOTIFICATION_API_BASE_URL/);
  assert.match(panel, /listNotificationDeliveries/);
  assert.match(panel, /data-testid="api-backed-notification-delivery-history"/);
  assert.match(loader, /audit-console/);
  assert.equal(deliveryManifest.type, "INQUIRY");
  assert.equal(deliveryManifest.audit.reasonRequired, true);
  assert.equal(deliveryManifest.audit.piiAccess, true);
});

test("fds-aml-console exposes generated analytics evidence through the Spring analytics API", async () => {
  const page = await readFile("apps/fds-aml-console/src/app/page.tsx", "utf8");
  const panel = await readFile("apps/fds-aml-console/src/components/AnalyticsEvidencePanel.tsx", "utf8");
  const artifact = JSON.parse(await readFile("docs/test-evidence/generated/fds-aml-analytics.json", "utf8"));

  assert.match(page, /AnalyticsEvidencePanel/);
  assert.match(panel, /createBankingApiClient/);
  assert.match(panel, /fdsAnalyticsEvidence/);
  assert.doesNotMatch(panel, /fds-aml-analytics\.json/);
  assert.match(panel, /data-testid="analytics-evidence-panel"/);
  assert.equal(artifact.controls.realMoneyUsed, false);
  assert.equal(artifact.controls.realPiiUsed, false);
  assert.ok(artifact.results.length > 0);
  assert.ok(artifact.results.some((result) => result.riskBand === "HIGH"));
});

test("target Next app directories do not contain legacy static shells", async () => {
  const apps = [
    "admin-console",
    "audit-console",
    "complaint-portal",
    "customer-web",
    "fds-aml-console",
    "ops-console",
    "staff-terminal"
  ];

  for (const app of apps) {
    assert.equal(await exists(`apps/${app}/public/index.html`), false, `${app} should not keep legacy HTML under apps/`);
    assert.equal(await exists(`legacy-node-reference/apps/${app}/public/index.html`), true, `${app} should preserve its legacy shell under legacy-node-reference/`);
  }
});
