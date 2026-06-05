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
  const panel = await readFile("apps/customer-web/src/components/ApiBackedCustomerPanel.tsx", "utf8");
  const client = await readFile("packages/api-client/src/index.ts", "utf8");
  const notificationPreferenceManifest = JSON.parse(await readFile("screen-manifests/customer-web/CWB-801.notification-preferences.json", "utf8"));
  const notificationDeliveryManifest = JSON.parse(await readFile("screen-manifests/customer-web/CWB-802.notification-delivery-history.json", "utf8"));

  assert.match(page, /loadCustomerWebManifests/);
  assert.match(loader, /screen-manifests/);
  assert.match(loader, /customer-web/);
  assert.match(loader, /manifest\.app === "customer-web"/);
  assert.match(panel, /NEXT_PUBLIC_BANKING_PAYMENT_API_BASE_URL/);
  assert.match(panel, /data-testid="api-backed-customer-payment-domain"/);
  assert.match(panel, /createPaymentInstruction/);
  assert.match(panel, /createAutopayAgreement/);
  assert.match(panel, /pauseAutopayAgreement/);
  assert.match(panel, /resumeAutopayAgreement/);
  assert.match(panel, /cancelAutopayAgreement/);
  assert.match(panel, /NEXT_PUBLIC_BANKING_NOTIFICATION_API_BASE_URL/);
  assert.match(panel, /data-testid="api-backed-customer-notification-preferences"/);
  assert.match(panel, /data-testid="api-backed-customer-notification-delivery-history"/);
  assert.match(panel, /listCustomerNotificationPreferences/);
  assert.match(panel, /upsertCustomerNotificationPreference/);
  assert.match(panel, /listCustomerNotificationDeliveries/);
  assert.match(client, /createPaymentInstruction/);
  assert.match(client, /createAutopayAgreement/);
  assert.match(client, /listCustomerNotificationPreferences/);
  assert.match(client, /upsertCustomerNotificationPreference/);
  assert.match(client, /listCustomerNotificationDeliveries/);
  assert.equal(notificationPreferenceManifest.api.command, "PUT /api/notifications/customers/{customerId}/preferences");
  assert.equal(notificationPreferenceManifest.audit.selfService, true);
  assert.equal(notificationPreferenceManifest.audit.reasonRequired, false);
  assert.equal(notificationDeliveryManifest.query.endpoint, "GET /api/notifications/customers/{customerId}/deliveries");
  assert.equal(notificationDeliveryManifest.audit.selfService, true);
  assert.equal(notificationDeliveryManifest.audit.eventTypes[0], "NOTIFICATION_CUSTOMER_DELIVERY_HISTORY_VIEW");
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

test("complaint-portal exposes self-service complaint extensions through manifests and API client", async () => {
  const panel = await readFile("apps/complaint-portal/src/components/ApiBackedComplaintPanel.tsx", "utf8");
  const client = await readFile("packages/api-client/src/index.ts", "utf8");
  const materialManifest = JSON.parse(await readFile("screen-manifests/complaint-portal/CMP-103.additional-materials.json", "utf8"));
  const reopenManifest = JSON.parse(await readFile("screen-manifests/complaint-portal/CMP-106.reopen-request.json", "utf8"));
  const typeGuideManifest = JSON.parse(await readFile("screen-manifests/complaint-portal/CMP-107.complaint-type-guide.json", "utf8"));
  const transferDisputeManifest = JSON.parse(await readFile("screen-manifests/complaint-portal/CMP-109.transfer-dispute-intake.json", "utf8"));
  const cardDisputeManifest = JSON.parse(await readFile("screen-manifests/complaint-portal/CMP-110.card-dispute-intake.json", "utf8"));

  assert.match(panel, /data-testid="api-backed-complaint-self-service"/);
  assert.match(panel, /complaintTypeGuide/);
  assert.match(panel, /Run dispute intake smoke/);
  assert.match(panel, /TRR-SYN-CMP-001/);
  assert.match(panel, /CAUTH-SYN-CMP-001/);
  assert.match(client, /ComplaintSourceReferenceDto/);
  assert.match(panel, /submitCustomerComplaintMaterial/);
  assert.match(panel, /reopenCustomerComplaint/);
  assert.match(client, /submitCustomerComplaintMaterial/);
  assert.match(client, /reopenCustomerComplaint/);
  assert.match(client, /complaintTypeGuide/);
  assert.equal(transferDisputeManifest.sourceReference.allowedTypes[0], "CUSTOMER_TRANSFER");
  assert.equal(transferDisputeManifest.actions[0].target, "POST /api/customer/complaints");
  assert.equal(cardDisputeManifest.sourceReference.allowedTypes[0], "CARD_AUTHORIZATION");
  assert.equal(cardDisputeManifest.actions[0].target, "POST /api/customer/complaints");
  assert.equal(materialManifest.actions[0].target, "POST /api/customer/complaints/{caseId}/materials");
  assert.equal(reopenManifest.actions[0].target, "POST /api/customer/complaints/{caseId}/reopen-requests");
  assert.equal(typeGuideManifest.query.endpoint, "GET /api/customer/complaint-types");
});

test("staff-terminal exposes PAY101 audited payment inquiry through the payment service client", async () => {
  const panel = await readFile("apps/staff-terminal/src/components/ApiBackedStaffPanel.tsx", "utf8");
  const client = await readFile("packages/api-client/src/index.ts", "utf8");
  const manifest = JSON.parse(await readFile("screen-manifests/staff-terminal/PAY-101.payment-instruction-inquiry.json", "utf8"));

  assert.match(panel, /NEXT_PUBLIC_BANKING_PAYMENT_API_BASE_URL/);
  assert.match(panel, /data-testid="api-backed-staff-payment-inquiry"/);
  assert.match(panel, /Run payment inquiry smoke/);
  assert.match(panel, /getPaymentInstruction\(created\.item\.paymentInstructionId, lookupReason\)/);
  assert.match(client, /getPaymentInstruction\(instructionId: string, reason\?: string\)/);
  assert.equal(manifest.audit.reasonRequired, true);
  assert.equal(manifest.audit.eventTypes[0], "PAYMENT_INSTRUCTION_VIEW");
});

test("staff-terminal exposes WRK002 operational retry queue through the Spring API client", async () => {
  const panel = await readFile("apps/staff-terminal/src/components/ApiBackedStaffPanel.tsx", "utf8");
  const client = await readFile("packages/api-client/src/index.ts", "utf8");
  const manifest = JSON.parse(await readFile("screen-manifests/staff-terminal/WRK-002.operational-retry-queue.json", "utf8"));

  assert.match(panel, /data-testid="api-backed-operational-retry-queue"/);
  assert.match(panel, /staffOperationalRetryQueue/);
  assert.match(panel, /First event/);
  assert.match(client, /OperationalRetryQueueItemDto/);
  assert.match(client, /staffOperationalRetryQueue\(reason: string, status\?: string\)/);
  assert.equal(manifest.query.endpoint, "GET /api/staff/operations/retry-queue");
  assert.equal(manifest.audit.reasonRequired, true);
  assert.equal(manifest.audit.eventTypes[0], "OPERATIONAL_RETRY_QUEUE_VIEW");
});

test("staff-terminal exposes WRK003 workflow timeline through the Spring API client", async () => {
  const renderer = await readFile("apps/staff-terminal/src/components/manifest-renderer.tsx", "utf8");
  const client = await readFile("packages/api-client/src/index.ts", "utf8");
  const dashboard = JSON.parse(await readFile("screen-manifests/staff-terminal/WRK-001.integrated-workstation-dashboard.json", "utf8"));
  const manifest = JSON.parse(await readFile("screen-manifests/staff-terminal/WRK-003.workflow-timeline.json", "utf8"));

  assert.match(renderer, /data-testid="manifest-workflow-timeline-api-panel"/);
  assert.match(renderer, /staffWorkflowTimeline/);
  assert.match(renderer, /TX-SYN-CORR-001/);
  assert.match(client, /StaffWorkflowTimelineEntryDto/);
  assert.match(client, /staffWorkflowTimeline\(businessReferenceId: string, reason: string\)/);
  assert.equal(manifest.query.endpoint, "GET /api/staff/workflows/{businessReferenceId}/timeline");
  assert.equal(manifest.audit.reasonRequired, true);
  assert.equal(manifest.audit.eventTypes[0], "WORKFLOW_TIMELINE_VIEW");
  assert.equal(dashboard.actions.some((action) => action.target === "WRK-003"), true);
});

test("ops-console exposes OPS404 payment outbox dispatch through the payment service client", async () => {
  const panel = await readFile("apps/ops-console/src/components/ApiBackedOpsPanel.tsx", "utf8");
  const manifest = JSON.parse(await readFile("screen-manifests/ops-console/OPS-404.payment-outbox-dispatch.json", "utf8"));

  assert.match(panel, /NEXT_PUBLIC_BANKING_PAYMENT_API_BASE_URL/);
  assert.match(panel, /data-testid="api-backed-payment-outbox-dispatch"/);
  assert.match(panel, /dispatchNextPaymentLedgerPosting/);
  assert.match(panel, /Browser OPS-404 payment outbox dispatch smoke/);
  assert.equal(manifest.api.command, "POST /api/payments/outbox/ledger-postings/dispatch-next");
  assert.equal(manifest.audit.reasonRequired, true);
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
