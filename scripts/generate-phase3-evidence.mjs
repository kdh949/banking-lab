import { mkdir, writeFile } from "node:fs/promises";
import { Readable } from "node:stream";
import { createLabHandler, createLabState } from "../runtime/labApp.mjs";

async function withHandler(fn) {
  const state = await createLabState();
  const handler = await createLabHandler(state);
  return fn({ invoke: (request) => invokeHandler(handler, request), state });
}

async function invokeHandler(handler, request) {
  const chunks = [];
  const body = request.body === undefined ? [] : [Buffer.from(JSON.stringify(request.body))];
  const readable = Readable.from(body);
  readable.url = request.url;
  readable.method = request.method || "GET";
  readable.headers = {
    host: "127.0.0.1",
    "content-type": "application/json"
  };
  const response = {
    statusCode: null,
    headers: null,
    writeHead(statusCode, headers) {
      this.statusCode = statusCode;
      this.headers = headers;
    },
    end(chunk) {
      if (chunk) {
        chunks.push(Buffer.from(chunk));
      }
    }
  };
  await handler(readable, response);
  const text = Buffer.concat(chunks).toString("utf8");
  return {
    status: response.statusCode,
    payload: text ? JSON.parse(text) : null
  };
}

async function postJson(invoke, url, body) {
  return invoke({
    method: "POST",
    url,
    body
  });
}

const evidence = await withHandler(async ({ invoke, state }) => {
  const detailDenied = await invoke({ url: "/api/staff/customers/SYN-CUS-001/detail" });
  const detailAllowed = await invoke({ url: "/api/staff/customers/SYN-CUS-001/detail?reason=Evidence%20detail%20lookup" });
  const detailPayload = detailAllowed.payload;
  const unmaskDenied = await postJson(invoke, "/api/staff/pii/unmask", {
    customerId: "SYN-CUS-001",
    requestedBy: "branch01",
    actorRole: "BRANCH_STAFF",
    reason: "Evidence unmask denied"
  });
  const unmaskAllowed = await postJson(invoke, "/api/staff/pii/unmask", {
    customerId: "SYN-CUS-001",
    requestedBy: "manager01",
    actorRole: "BRANCH_MANAGER",
    reason: "Evidence manager unmask"
  });
  const accountAllowed = await invoke({ url: "/api/staff/accounts/search?customerId=SYN-CUS-001&reason=Evidence%20account%20lookup" });
  const accountPayload = accountAllowed.payload;
  const transactionAllowed = await invoke({ url: "/api/staff/transactions/search?accountId=ACC-SYN-001-001&reason=Evidence%20transaction%20lookup" });
  const transactionPayload = transactionAllowed.payload;
  const beforePhone = state.dataset.customers.find((customer) => customer.customerId === "SYN-CUS-001").phone;
  const changeRequest = await postJson(invoke, "/api/staff/customers/SYN-CUS-001/change-requests", {
    requestedBy: "branch01",
    reason: "Evidence customer phone update",
    afterSnapshot: {
      phone: "010-0000-1777"
    }
  });
  const pendingPhone = state.dataset.customers.find((customer) => customer.customerId === "SYN-CUS-001").phone;
  const approval = await postJson(invoke, `/api/staff/approvals/${changeRequest.payload.item.approvalId}/approve`, {
    approvedBy: "manager01",
    approvedByRole: "BRANCH_MANAGER"
  });
  const afterPhone = state.dataset.customers.find((customer) => customer.customerId === "SYN-CUS-001").phone;
  const screens = await invoke({ url: "/api/screens?app=staff-terminal" });
  const staffManifestIds = screens.payload.items.map((screen) => screen.screenId);
  const auditTypes = new Set(state.auditLog.all().map((event) => event.eventType));

  return {
    generatedAt: new Date().toISOString(),
    scope: "Phase 3 Staff Terminal MVP",
    syntheticOnly: true,
    checks: [
      {
        id: "reason-required-customer-detail",
        status: detailDenied.status === 400 && detailAllowed.status === 200 && detailPayload.item.piiExposure === "MASKED" ? "pass" : "fail",
        detail: "Customer detail rejects missing reason and returns masked data with reason"
      },
      {
        id: "unmask-policy",
        status: unmaskDenied.status === 403 && unmaskAllowed.status === 200 && unmaskAllowed.payload.item.piiExposure === "UNMASKED_TIMEBOXED" ? "pass" : "fail",
        detail: "Unmask is denied for branch staff and allowed for manager with reason"
      },
      {
        id: "account-transaction-audit",
        status: accountAllowed.status === 200 && transactionAllowed.status === 200 && accountPayload.items.length > 0 && transactionPayload.items.length > 0 ? "pass" : "fail",
        detail: "Account and transaction inquiry return staff results"
      },
      {
        id: "maker-checker-customer-change",
        status: beforePhone === pendingPhone && afterPhone === "010-0000-1777" && approval.payload.executed === true ? "pass" : "fail",
        detail: "Customer phone changes only after manager approval"
      },
      {
        id: "staff-manifest-coverage",
        status: ["CST-001", "CST-002", "CST-103", "ACC-101", "LED-101", "APR-001", "AUD-001"].every((id) => staffManifestIds.includes(id)) ? "pass" : "fail",
        detail: `${staffManifestIds.length} staff manifests available`
      },
      {
        id: "audit-event-coverage",
        status: ["CUSTOMER_DETAIL_VIEW", "PII_UNMASK_REQUESTED", "ACCOUNT_VIEW", "TRANSACTION_VIEW", "COMMAND_REQUESTED", "COMMAND_APPROVED", "COMMAND_EXECUTED"].every((type) => auditTypes.has(type)) ? "pass" : "fail",
        detail: "Phase 3 staff actions produced required audit event types"
      }
    ]
  };
});

const outputDir = "docs/test-evidence/generated";
const outputFile = `${outputDir}/phase-3-staff-terminal.json`;
await mkdir(outputDir, { recursive: true });
await writeFile(outputFile, `${JSON.stringify(evidence, null, 2)}\n`);

console.log(`Wrote ${outputFile}`);
