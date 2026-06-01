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
  let payload = null;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = text;
    }
  }
  return {
    status: response.statusCode,
    payload
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
  const login = await postJson(invoke, "/api/customer/login", { userId: "customer01" });
  const accountDetail = await invoke({ url: "/api/customer/accounts/ACC-SYN-001-001/detail?customerId=SYN-CUS-001" });
  const posted = await postJson(invoke, "/api/customer/transfers", {
    fromAccountId: "ACC-SYN-001-001",
    toAccountId: "ACC-SYN-002-001",
    amountMinor: 11111,
    idempotencyKey: "EVIDENCE-P4-POSTED",
    requestedBy: "SYN-CUS-001"
  });
  const postedReplay = await postJson(invoke, "/api/customer/transfers", {
    fromAccountId: "ACC-SYN-001-001",
    toAccountId: "ACC-SYN-002-001",
    amountMinor: 11111,
    idempotencyKey: "EVIDENCE-P4-POSTED",
    requestedBy: "SYN-CUS-001"
  });
  const customerHistory = await invoke({ url: "/api/customer/transactions?customerId=SYN-CUS-001&accountId=ACC-SYN-001-001" });
  const staffHistory = await invoke({ url: "/api/staff/transactions/search?accountId=ACC-SYN-001-001&reason=Evidence%20history%20compare" });
  const beforeHoldCount = state.ledgerCore.transactions.length;
  const held = await postJson(invoke, "/api/customer/transfers", {
    fromAccountId: "ACC-SYN-001-001",
    toAccountId: "ACC-SYN-002-001",
    amountMinor: 5000000,
    idempotencyKey: "EVIDENCE-P4-HELD",
    requestedBy: "SYN-CUS-001"
  });
  const failed = await postJson(invoke, "/api/customer/transfers", {
    fromAccountId: "ACC-SYN-001-001",
    toAccountId: "ACC-SYN-002-001",
    amountMinor: -1,
    idempotencyKey: "EVIDENCE-P4-FAILED",
    requestedBy: "SYN-CUS-001"
  });
  const transferResults = await invoke({ url: "/api/customer/transfers?customerId=SYN-CUS-001" });
  const screens = await invoke({ url: "/api/screens?app=customer-web" });
  const complaintPortal = await invoke({ url: "/complaint-portal" });
  const auditTypes = new Set(state.auditLog.all().map((event) => `${event.actorType}:${event.eventType}`));
  const transactionId = posted.payload.item.transactionId;

  return {
    generatedAt: new Date().toISOString(),
    scope: "Phase 4 Customer Web MVP",
    syntheticOnly: true,
    checks: [
      {
        id: "customer-login-account-detail",
        status: login.status === 200 && accountDetail.status === 200 && accountDetail.payload.item.maskedAccountNo === "LAB-***-0001" ? "pass" : "fail",
        detail: "Customer login and account detail are available"
      },
      {
        id: "posted-transfer-ledger-source",
        status: posted.status === 201
          && posted.payload.item.status === "POSTED"
          && customerHistory.payload.items.some((item) => item.transactionId === transactionId)
          && staffHistory.payload.items.some((item) => item.transactionId === transactionId)
          ? "pass"
          : "fail",
        detail: "Posted customer transfer appears in customer and staff history from one ledger source"
      },
      {
        id: "idempotent-transfer-result",
        status: postedReplay.status === 200 && postedReplay.payload.replayed === true && postedReplay.payload.item.transactionId === transactionId ? "pass" : "fail",
        detail: "Retry returned the original transfer result"
      },
      {
        id: "held-and-failed-status",
        status: held.status === 202
          && held.payload.item.status === "HELD"
          && failed.status === 200
          && failed.payload.item.status === "FAILED"
          && state.ledgerCore.transactions.length === beforeHoldCount
          ? "pass"
          : "fail",
        detail: "Held and failed transfers are represented without extra ledger postings"
      },
      {
        id: "transfer-result-history",
        status: ["POSTED", "HELD", "FAILED"].every((status) => transferResults.payload.items.some((item) => item.status === status)) ? "pass" : "fail",
        detail: "Customer transfer result list includes posted, held, and failed states"
      },
      {
        id: "complaint-entry",
        status: screens.payload.items.some((screen) => screen.screenId === "CWB-301") && complaintPortal.status === 200 ? "pass" : "fail",
        detail: "Customer web has complaint entry manifest and complaint portal shell"
      },
      {
        id: "customer-audit-coverage",
        status: ["CUSTOMER:LOGIN_SUCCESS", "CUSTOMER:ACCOUNT_VIEW", "CUSTOMER:TRANSACTION_VIEW", "CUSTOMER:COMMAND_EXECUTED", "CUSTOMER:COMMAND_REQUESTED", "CUSTOMER:COMMAND_REJECTED"].every((type) => auditTypes.has(type)) ? "pass" : "fail",
        detail: "Customer self-service actions generated expected audit event types"
      }
    ]
  };
});

const outputDir = "docs/test-evidence/generated";
const outputFile = `${outputDir}/phase-4-customer-web.json`;
await mkdir(outputDir, { recursive: true });
await writeFile(outputFile, `${JSON.stringify(evidence, null, 2)}\n`);

console.log(`Wrote ${outputFile}`);
