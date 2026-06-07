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

test("customer-web Next page is manifest-driven and customer journey routes are form-backed", async () => {
  const page = await readFile("apps/customer-web/src/app/page.tsx", "utf8");
  const loader = await readFile("apps/customer-web/src/lib/manifestLoader.ts", "utf8");
  const panel = await readFile("apps/customer-web/src/components/ApiBackedCustomerPanel.tsx", "utf8");
  const selfService = await readFile("apps/customer-web/src/components/CustomerSelfService.tsx", "utf8");
  const routes = await readFile("apps/customer-web/src/components/workflow-routes.tsx", "utf8");
  const signupRoute = await readFile("apps/customer-web/src/app/signup/page.tsx", "utf8");
  const loginRoute = await readFile("apps/customer-web/src/app/login/page.tsx", "utf8");
  const accountsRoute = await readFile("apps/customer-web/src/app/accounts/page.tsx", "utf8");
  const transferRoute = await readFile("apps/customer-web/src/app/transfers/new/page.tsx", "utf8");
  const accountRoute = await readFile("apps/customer-web/src/app/accounts/[accountId]/page.tsx", "utf8");
  const client = await readFile("packages/api-client/src/index.ts", "utf8");
  const notificationPreferenceManifest = JSON.parse(await readFile("screen-manifests/customer-web/CWB-801.notification-preferences.json", "utf8"));
  const notificationDeliveryManifest = JSON.parse(await readFile("screen-manifests/customer-web/CWB-802.notification-delivery-history.json", "utf8"));

  assert.match(page, /loadCustomerWebManifests/);
  assert.match(page, /customerWorkflowRouteSummaries/);
  assert.match(loader, /screen-manifests/);
  assert.match(loader, /customer-web/);
  assert.match(loader, /manifest\.app === "customer-web"/);
  assert.match(routes, /CustomerWorkflowRoutePage/);
  assert.match(routes, /CWB-201/);
  assert.match(routes, /POSTED/);
  assert.match(routes, /HELD/);
  assert.match(routes, /FAILED/);
  assert.match(routes, /BLOCKED/);
  assert.match(routes, /STEP_UP_REQUIRED/);
  assert.match(routes, /synthetic demo fallback/);
  assert.match(signupRoute, /CustomerSignupForm/);
  assert.match(loginRoute, /CustomerLoginForm/);
  assert.match(accountsRoute, /CustomerAccountsView/);
  assert.match(transferRoute, /CustomerTransferForm/);
  assert.match(accountRoute, /CustomerAccountDetailView/);
  assert.match(selfService, /signupCustomer/);
  assert.match(selfService, /loginCustomer/);
  assert.match(selfService, /customerAccounts/);
  assert.match(selfService, /internalRecipientLookup/);
  assert.match(selfService, /requestCustomerTransfer/);
  assert.doesNotMatch(selfService, /SYN-CUS-001/);
  assert.doesNotMatch(selfService, /ACC-SYN-001-001/);
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
  const client = await readFile("packages/api-client/src/index.ts", "utf8");
  const loader = await readFile("apps/admin-console/src/lib/manifestLoader.ts", "utf8");
  const securityManifest = JSON.parse(await readFile("screen-manifests/admin-console/ADM-201.security-policy-parameters.json", "utf8"));
  const notificationTemplateManifest = JSON.parse(await readFile("screen-manifests/admin-console/ADM-401.notification-template-approval.json", "utf8"));
  const notificationPreferenceManifest = JSON.parse(await readFile("screen-manifests/admin-console/ADM-402.notification-preference-management.json", "utf8"));
  const evidenceCoverageManifest = JSON.parse(await readFile("screen-manifests/admin-console/ADM-501.platform-evidence-coverage.json", "utf8"));
  const systemStatusManifest = JSON.parse(await readFile("screen-manifests/admin-console/ADM-601.system-batch-status.json", "utf8"));
  const reportingManifest = JSON.parse(await readFile("screen-manifests/admin-console/ADM-701.reporting-artifact-management.json", "utf8"));

  assert.equal(appPackage.name, "@banking-lab/admin-console");
  assert.match(appPackage.scripts.dev, /3007/);
  assert.equal(rootPackage.scripts["next:admin-console:typecheck"], "npm --workspace @banking-lab/admin-console run typecheck");
  assert.match(page, /loadChannelManifests/);
  assert.match(page, /ApiBackedAdminPanel/);
  assert.match(panel, /adminEvidenceCoverage/);
  assert.match(panel, /adminSystemStatus/);
  assert.match(panel, /data-testid="api-backed-admin-evidence-coverage"/);
  assert.match(panel, /data-testid="api-backed-admin-system-status"/);
  assert.match(panel, /data-testid="api-backed-security-parameters"/);
  assert.match(panel, /securityParameters/);
  assert.match(panel, /requestSecurityParameterChange/);
  assert.match(panel, /Browser ADM-201 parameter change smoke/);
  assert.match(panel, /data-testid="api-backed-authorization-parameters"/);
  assert.match(panel, /authorizationParameters/);
  assert.match(panel, /requestAuthorizationParameterChange/);
  assert.match(panel, /Browser ADM-301 parameter change smoke/);
  assert.match(panel, /NEXT_PUBLIC_BANKING_NOTIFICATION_API_BASE_URL/);
  assert.match(panel, /listNotificationTemplates/);
  assert.match(panel, /listNotificationTemplateChangeRequests/);
  assert.match(panel, /listNotificationPreferences/);
  assert.match(panel, /Template Workflows/);
  assert.match(panel, /Workflow Timeline/);
  assert.match(panel, /data-testid="api-backed-notification-admin"/);
  assert.match(panel, /NEXT_PUBLIC_BANKING_REPORTING_API_BASE_URL/);
  assert.match(panel, /reportCatalog/);
  assert.match(panel, /generateReportArtifact/);
  assert.match(panel, /reportArtifacts/);
  assert.match(panel, /exportReportArtifact/);
  assert.match(panel, /Export Package/);
  assert.match(panel, /contentSha256/);
  assert.match(panel, /retentionPolicy/);
  assert.match(panel, /Artifact Workflow/);
  assert.match(panel, /workflowStatus/);
  assert.match(panel, /data-testid="api-backed-reporting-admin"/);
  assert.match(client, /ReportCatalogResponse/);
  assert.match(client, /artifactContent/);
  assert.match(client, /contentSha256/);
  assert.match(client, /ReportingWorkflowTimelineEntryDto/);
  assert.match(client, /ReportArtifactExportResponse/);
  assert.match(client, /exportReportArtifact/);
  assert.match(client, /ReportRetentionSweepResponse/);
  assert.match(client, /runReportRetentionSweep/);
  assert.match(client, /generateReportArtifact/);
  assert.match(client, /reportArtifacts/);
  assert.match(loader, /admin-console/);
  assert.equal(securityManifest.type, "PARAMETER");
  assert.equal(securityManifest.approval.makerChecker, true);
  assert.equal(notificationTemplateManifest.approval.makerChecker, true);
  assert.equal(notificationPreferenceManifest.audit.reasonRequired, true);
  assert.equal(evidenceCoverageManifest.type, "DASHBOARD");
  assert.equal(evidenceCoverageManifest.audit.reasonRequired, true);
  assert.equal(evidenceCoverageManifest.audit.eventTypes[0], "ADMIN_EVIDENCE_COVERAGE_VIEW");
  assert.equal(systemStatusManifest.type, "DASHBOARD");
  assert.equal(systemStatusManifest.audit.reasonRequired, true);
  assert.equal(systemStatusManifest.audit.eventTypes[0], "ADMIN_SYSTEM_STATUS_VIEW");
  assert.equal(reportingManifest.type, "COMMAND");
  assert.equal(reportingManifest.api.command, "POST /api/reports/artifacts");
  assert.equal(reportingManifest.audit.eventTypes[0], "REPORT_CATALOG_VIEW");
  assert.ok(reportingManifest.postActions.includes("storeContentSha256"));
  assert.ok(reportingManifest.postActions.includes("packageSyntheticJsonExport"));
  assert.ok(reportingManifest.postActions.includes("expirePastRetentionArtifacts"));
  assert.equal(reportingManifest.actions[3].target, "GET /api/reports/artifacts/{artifactId}/export");
  assert.equal(reportingManifest.actions[4].target, "POST /api/reports/retention/sweeps");
  assert.equal(reportingManifest.actions[0].target, "GET /api/reports/catalog");
  assert.equal(await exists("apps/admin-console/public/index.html"), false);
  assert.equal(await exists("legacy-node-reference/apps/admin-console/public/index.html"), true);
});

test("audit-console exposes notification delivery history through manifests and API client", async () => {
  const rootPackage = JSON.parse(await readFile("package.json", "utf8"));
  const appPackage = JSON.parse(await readFile("apps/audit-console/package.json", "utf8"));
  const page = await readFile("apps/audit-console/src/app/page.tsx", "utf8");
  const panel = await readFile("apps/audit-console/src/components/ApiBackedAuditPanel.tsx", "utf8");
  const client = await readFile("packages/api-client/src/index.ts", "utf8");
  const loader = await readFile("apps/audit-console/src/lib/manifestLoader.ts", "utf8");
  const deliveryManifest = JSON.parse(await readFile("screen-manifests/audit-console/AUD-301.notification-delivery-history.json", "utf8"));
  const reportingManifest = JSON.parse(await readFile("screen-manifests/audit-console/AUD-401.reporting-artifact-history.json", "utf8"));

  assert.equal(appPackage.name, "@banking-lab/audit-console");
  assert.equal(rootPackage.scripts["next:audit-console:typecheck"], "npm --workspace @banking-lab/audit-console run typecheck");
  assert.match(page, /loadChannelManifests/);
  assert.match(page, /ApiBackedAuditPanel/);
  assert.match(panel, /NEXT_PUBLIC_BANKING_NOTIFICATION_API_BASE_URL/);
  assert.match(panel, /listNotificationDeliveries/);
  assert.match(panel, /data-testid="api-backed-notification-delivery-history"/);
  assert.match(panel, /NEXT_PUBLIC_BANKING_REPORTING_API_BASE_URL/);
  assert.match(panel, /reportArtifacts/);
  assert.match(panel, /contentSha256/);
  assert.match(panel, /retentionPolicy/);
  assert.match(panel, /Artifact Workflow/);
  assert.match(panel, /workflowTimeline/);
  assert.match(panel, /data-testid="api-backed-reporting-artifact-history"/);
  assert.match(panel, /data-testid="api-backed-audit-parameters"/);
  assert.match(panel, /auditParameters/);
  assert.match(panel, /requestAuditParameterChange/);
  assert.match(panel, /Browser AUD-201 parameter change smoke/);
  assert.match(client, /ReportArtifactListResponse/);
  assert.match(loader, /audit-console/);
  assert.equal(deliveryManifest.type, "INQUIRY");
  assert.equal(deliveryManifest.audit.reasonRequired, true);
  assert.equal(deliveryManifest.audit.piiAccess, true);
  assert.equal(reportingManifest.type, "INQUIRY");
  assert.equal(reportingManifest.query.endpoint, "GET /api/reports/artifacts");
  assert.equal(reportingManifest.audit.reasonRequired, true);
  assert.equal(reportingManifest.audit.eventTypes[0], "REPORT_ARTIFACT_LIST_VIEW");
  assert.ok(reportingManifest.resultTable.columns.includes("contentSha256"));
  assert.ok(reportingManifest.resultTable.columns.includes("retentionPolicy"));
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

test("staff-terminal exposes PAY102 payment cancellation approval through the payment service client", async () => {
  const panel = await readFile("apps/staff-terminal/src/components/ApiBackedStaffPanel.tsx", "utf8");
  const client = await readFile("packages/api-client/src/index.ts", "utf8");
  const manifest = JSON.parse(await readFile("screen-manifests/staff-terminal/PAY-102.payment-cancellation-approval.json", "utf8"));

  assert.match(panel, /data-testid="api-backed-staff-payment-cancellation"/);
  assert.match(panel, /Run payment cancellation approval smoke/);
  assert.match(panel, /requestPaymentCancellationApproval\(created\.item\.paymentInstructionId/);
  assert.match(panel, /PAYMENT_MAKER_CHECKER_SEPARATION_REQUIRED/);
  assert.match(panel, /approvePaymentCancellationRequest\(requested\.item\.cancellationRequestId/);
  assert.match(panel, /paymentCancellationState\.requested\.item\.makerId/);
  assert.match(panel, /paymentCancellationState\.approved\.item\.checkerId/);
  assert.match(client, /requestPaymentCancellationApproval\(instructionId: string/);
  assert.match(client, /approvePaymentCancellationRequest\(requestId: string/);
  assert.match(client, /rejectPaymentCancellationRequest\(requestId: string/);
  assert.equal(manifest.type, "COMMAND");
  assert.equal(manifest.highRisk, true);
  assert.equal(manifest.approval.required, true);
  assert.equal(manifest.approval.makerChecker, true);
  assert.equal(manifest.audit.reasonRequired, true);
  assert.equal(manifest.api.command, "POST /api/payments/instructions/{instructionId}/cancellation-requests");
  assert.equal(manifest.actions.some((action) => action.target === "POST /api/payments/cancellation-requests/{requestId}/approve"), true);
  assert.equal(manifest.actions.some((action) => action.target === "POST /api/payments/cancellation-requests/{requestId}/reject"), true);
  assert.equal(manifest.requiredRoles.includes("OPS_MANAGER"), true);
  assert.equal(manifest.audit.eventTypes.includes("PAYMENT_CANCELLATION_REJECTED"), true);
});

test("payment service contract exposes staff cancellation maker-checker APIs", async () => {
  const client = await readFile("packages/api-client/src/index.ts", "utf8");
  const contract = await readFile("contracts/openapi/payment-service.yaml", "utf8");

  assert.match(contract, /requestPaymentCancellationApproval/);
  assert.match(contract, /approvePaymentCancellationRequest/);
  assert.match(contract, /rejectPaymentCancellationRequest/);
  assert.match(contract, /PAYMENT_MAKER_CHECKER_REQUIRED|Maker\/checker|maker-checker/i);
  assert.match(client, /RequestPaymentCancellationApprovalRequest/);
  assert.match(client, /ReviewPaymentCancellationRequest/);
  assert.match(client, /PaymentCancellationRequestResponse/);
  assert.match(client, /requestPaymentCancellationApproval\(instructionId: string/);
  assert.match(client, /approvePaymentCancellationRequest\(requestId: string/);
  assert.match(client, /rejectPaymentCancellationRequest\(requestId: string/);
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

test("staff-terminal exposes route-backed reason and approval workflow pages", async () => {
  const routes = await readFile("apps/staff-terminal/src/components/workflow-routes.tsx", "utf8");
  const dashboard = await readFile("apps/staff-terminal/src/components/terminal-screens.tsx", "utf8");
  const customerRoute = await readFile("apps/staff-terminal/src/app/customers/[customerId]/page.tsx", "utf8");
  const approvalRoute = await readFile("apps/staff-terminal/src/app/approvals/page.tsx", "utf8");
  const txRoute = await readFile("apps/staff-terminal/src/app/tx/[transactionCode]/page.tsx", "utf8");

  assert.match(routes, /StaffWorkflowRoutePage/);
  assert.match(routes, /POLICY_REASON_REQUIRED/);
  assert.match(routes, /MAKER_CHECKER_SEPARATION_REQUIRED/);
  assert.match(routes, /AUTHORIZATION_DENIED/);
  assert.match(routes, /staffCustomerDetail/);
  assert.match(routes, /approveStaffApproval/);
  assert.match(routes, /staffWorkflowTimeline/);
  assert.match(dashboard, /staffWorkflowRouteSummaries/);
  assert.match(customerRoute, /routeKey: "customers"/);
  assert.match(approvalRoute, /routeKey: "approvals"/);
  assert.match(txRoute, /routeKey: "tx"/);
});

test("ops-console exposes OPS404 payment outbox dispatch through the payment service client", async () => {
  const panel = await readFile("apps/ops-console/src/components/ApiBackedOpsPanel.tsx", "utf8");
  const manifest = JSON.parse(await readFile("screen-manifests/ops-console/OPS-404.payment-outbox-dispatch.json", "utf8"));
  const driftManifest = JSON.parse(await readFile("screen-manifests/ops-console/OPS-LEDGER-101.projection-drift-monitor.json", "utf8"));
  const rebuildManifest = JSON.parse(await readFile("screen-manifests/ops-console/OPS-LEDGER-102.projection-rebuild-request.json", "utf8"));
  const evidenceManifest = JSON.parse(await readFile("screen-manifests/ops-console/OPS-LEDGER-103.projection-rebuild-evidence.json", "utf8"));
  const client = await readFile("packages/api-client/src/index.ts", "utf8");

  assert.match(panel, /NEXT_PUBLIC_BANKING_PAYMENT_API_BASE_URL/);
  assert.match(panel, /mismatchType/);
  assert.match(panel, /detectedReason/);
  assert.match(panel, /data-testid="api-backed-payment-outbox-dispatch"/);
  assert.match(panel, /dispatchNextPaymentLedgerPosting/);
  assert.match(panel, /Browser OPS-404 payment outbox dispatch smoke/);
  assert.match(panel, /data-testid="api-backed-reconciliation-parameters"/);
  assert.match(panel, /reconciliationParameters/);
  assert.match(panel, /requestReconciliationParameterChange/);
  assert.match(panel, /Browser OPS-301 parameter change smoke/);
  assert.match(panel, /data-testid="api-backed-ledger-projection-workflow"/);
  assert.match(panel, /startLedgerProjectionDriftRun/);
  assert.match(panel, /requestLedgerProjectionRebuild/);
  assert.match(panel, /approveLedgerProjectionRebuildRequest/);
  assert.match(panel, /executeLedgerProjectionRebuild/);
  assert.match(panel, /Browser OPS-LEDGER-101 projection drift smoke/);
  assert.match(client, /LedgerProjectionDriftRunResponse/);
  assert.match(client, /LedgerProjectionRebuildRunResponse/);
  assert.match(client, /ledgerProjectionRebuildRun/);
  assert.equal(manifest.api.command, "POST /api/payments/outbox/ledger-postings/dispatch-next");
  assert.equal(manifest.audit.reasonRequired, true);
  assert.equal(driftManifest.actions[0].target, "POST /api/ops/ledger/projection-drift-runs");
  assert.equal(driftManifest.audit.reasonRequired, true);
  assert.equal(rebuildManifest.api.command, "POST /api/ops/ledger/projection-rebuild-requests");
  assert.equal(rebuildManifest.approval.businessTypes[0], "LEDGER_PROJECTION_REBUILD");
  assert.equal(rebuildManifest.highRisk, true);
  assert.equal(evidenceManifest.actions[2].target, "POST /api/ops/ledger/projection-rebuild-requests/{requestId}/execute");
  assert.equal(evidenceManifest.workflow.name, "ledgerProjectionRebuildWorkflow");
});

test("fds-aml-console exposes generated analytics evidence through the Spring analytics API", async () => {
  const page = await readFile("apps/fds-aml-console/src/app/page.tsx", "utf8");
  const panel = await readFile("apps/fds-aml-console/src/components/AnalyticsEvidencePanel.tsx", "utf8");
  const riskPanel = await readFile("apps/fds-aml-console/src/components/ApiBackedRiskPanel.tsx", "utf8");
  const artifact = JSON.parse(await readFile("docs/test-evidence/generated/fds-aml-analytics.json", "utf8"));

  assert.match(page, /AnalyticsEvidencePanel/);
  assert.match(panel, /createBankingApiClient/);
  assert.match(panel, /fdsAnalyticsEvidence/);
  assert.doesNotMatch(panel, /fds-aml-analytics\.json/);
  assert.match(panel, /data-testid="analytics-evidence-panel"/);
  assert.match(riskPanel, /data-testid="api-backed-fds-parameters"/);
  assert.match(riskPanel, /fdsParameters/);
  assert.match(riskPanel, /requestFdsParameterChange/);
  assert.match(riskPanel, /Browser FDS-301 parameter change smoke/);
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
