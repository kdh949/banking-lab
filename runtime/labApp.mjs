import { readFile } from "node:fs/promises";
import { createServer } from "node:http";
import { extname, join } from "node:path";
import { fileURLToPath } from "node:url";
import {
  ApprovalStore,
  AuditLog,
  IdempotencyStore,
  generateSyntheticDataset,
  loginMockUser,
  maskAccount,
  maskCustomer
} from "../packages/banking-domain/src/index.mjs";
import { LedgerCore } from "../services/core-banking/src/index.mjs";
import { filterManifestsByApp, loadManifests } from "../packages/screen-engine/src/index.mjs";

const __dirname = fileURLToPath(new URL(".", import.meta.url));
const repoRoot = join(__dirname, "..");

const MIME_TYPES = new Map([
  [".html", "text/html; charset=utf-8"],
  [".js", "text/javascript; charset=utf-8"],
  [".css", "text/css; charset=utf-8"],
  [".json", "application/json; charset=utf-8"]
]);

function json(response, statusCode, payload) {
  response.writeHead(statusCode, { "content-type": "application/json; charset=utf-8" });
  response.end(JSON.stringify(payload, null, 2));
}

async function bodyJson(request) {
  const chunks = [];
  for await (const chunk of request) {
    chunks.push(chunk);
  }
  if (chunks.length === 0) {
    return {};
  }
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}

function routeAppPath(pathname) {
  const routes = new Map([
    ["/", "staff-terminal"],
    ["/customer-web", "customer-web"],
    ["/customer-web/", "customer-web"],
    ["/staff-terminal", "staff-terminal"],
    ["/staff-terminal/", "staff-terminal"],
    ["/complaint-portal", "complaint-portal"],
    ["/complaint-portal/", "complaint-portal"],
    ["/ops-console", "ops-console"],
    ["/ops-console/", "ops-console"],
    ["/audit-console", "audit-console"],
    ["/audit-console/", "audit-console"],
    ["/fds-aml-console", "fds-aml-console"],
    ["/fds-aml-console/", "fds-aml-console"]
  ]);
  return routes.get(pathname);
}

function createSeededLedger(dataset) {
  return [...dataset.ledgerTransactions];
}

export async function createLabState() {
  const dataset = generateSyntheticDataset();
  const auditLog = new AuditLog();
  const approvalStore = new ApprovalStore({ auditLog });
  const idempotencyStore = new IdempotencyStore();
  const ledgerCore = new LedgerCore({
    customers: dataset.customers,
    accounts: dataset.accounts,
    transactions: createSeededLedger(dataset)
  });
  const ledgerTransactions = ledgerCore.transactions;
  const manifests = await loadManifests(join(repoRoot, "screen-manifests"));
  const complaints = [
    {
      caseId: "CMP-SYN-0001",
      customerId: "SYN-CUS-001",
      category: "TRANSFER_DISPUTE",
      status: "RECEIVED",
      owner: "complaint01",
      slaHours: 72,
      timeline: []
    }
  ];
  const fdsCases = [];

  approvalStore.submit({
    businessType: "ACCOUNT_HOLD",
    businessReferenceId: "ACC-SYN-003-001",
    requestedBy: "branch01",
    requestReason: "Synthetic high-risk account hold review",
    beforeSnapshot: { status: "ACTIVE" },
    afterSnapshot: { status: "HOLD_REQUESTED" },
    screenId: "ACC-103"
  });

  return {
    dataset,
    auditLog,
    approvalStore,
    idempotencyStore,
    ledgerCore,
    ledgerTransactions,
    manifests,
    complaints,
    fdsCases
  };
}

export async function createLabHandler(state) {
  state = state || await createLabState();
  return async function handler(request, response) {
    try {
      const url = new URL(request.url, `http://${request.headers.host || "127.0.0.1"}`);
      const pathname = url.pathname;

      if (pathname === "/health") {
        json(response, 200, {
          status: "ok",
          syntheticOnly: true,
          auditHashChainValid: state.auditLog.verifyHashChain()
        });
        return;
      }

      if (pathname === "/assets/app.js" || pathname === "/assets/lab.css") {
        const fileName = pathname.endsWith(".js") ? "app.js" : "lab.css";
        const content = await readFile(join(repoRoot, "packages", "ui", "public", fileName), "utf8");
        response.writeHead(200, { "content-type": MIME_TYPES.get(extname(fileName)) });
        response.end(content);
        return;
      }

      const app = routeAppPath(pathname);
      if (app) {
        const content = await readFile(join(repoRoot, "apps", app, "public", "index.html"), "utf8");
        response.writeHead(200, { "content-type": "text/html; charset=utf-8" });
        response.end(content);
        return;
      }

      if (pathname === "/api/auth/users" && request.method === "GET") {
        json(response, 200, {
          items: [
            "customer01",
            "branch01",
            "manager01",
            "complaint01",
            "auditor01",
            "fds01"
          ]
        });
        return;
      }

      if (pathname === "/api/auth/mock-login" && request.method === "POST") {
        const payload = await bodyJson(request);
        const session = loginMockUser({ userId: payload.userId, auditLog: state.auditLog });
        json(response, 200, { session });
        return;
      }

      if (pathname === "/api/screens" && request.method === "GET") {
        const appName = url.searchParams.get("app");
        json(response, 200, {
          items: appName ? filterManifestsByApp(state.manifests, appName) : state.manifests
        });
        return;
      }

      if (pathname === "/api/staff/customers/search" && request.method === "GET") {
        const reason = url.searchParams.get("reason");
        if (!reason) {
          json(response, 400, { error: "CUSTOMER_SEARCH requires a business reason" });
          return;
        }
        const event = state.auditLog.append({
          eventType: "CUSTOMER_SEARCH",
          actorType: "STAFF",
          actorId: "branch01",
          actorRole: "BRANCH_STAFF",
          screenId: "CST-001",
          reason,
          payload: {
            query: url.searchParams.get("query") || ""
          }
        });
        json(response, 200, {
          auditEventId: event.auditEventId,
          items: state.dataset.customers.map(maskCustomer)
        });
        return;
      }

      if (pathname === "/api/staff/audit-events" && request.method === "GET") {
        json(response, 200, { items: state.auditLog.all() });
        return;
      }

      if (pathname === "/api/staff/approvals" && request.method === "GET") {
        json(response, 200, { items: state.approvalStore.list() });
        return;
      }

      if (pathname === "/api/staff/approvals" && request.method === "POST") {
        const payload = await bodyJson(request);
        const approval = state.approvalStore.submit(payload);
        json(response, 201, { item: approval });
        return;
      }

      const approvalMatch = pathname.match(/^\/api\/staff\/approvals\/([^/]+)\/approve$/);
      if (approvalMatch && request.method === "POST") {
        const payload = await bodyJson(request);
        const approval = state.approvalStore.approve(approvalMatch[1], payload);
        json(response, 200, { item: approval });
        return;
      }

      if (pathname === "/api/customer/accounts" && request.method === "GET") {
        const customerId = url.searchParams.get("customerId") || "SYN-CUS-001";
        const balances = state.ledgerCore.getBalances();
        const items = state.dataset.accounts
          .filter((account) => account.customerId === customerId)
          .map((account) => ({
            ...maskAccount(account),
            ...balances.find((balance) => balance.accountId === account.accountId)
          }));
        json(response, 200, { items });
        return;
      }

      if (pathname === "/api/customer/transfers" && request.method === "POST") {
        const payload = await bodyJson(request);
        let result;
        if (payload.amountMinor >= 5000000) {
          result = state.idempotencyStore.run(payload.idempotencyKey, () => {
            const caseRecord = {
              caseId: `FDS-${String(state.fdsCases.length + 1).padStart(8, "0")}`,
              status: "HELD",
              rule: "UNUSUAL_AMOUNT",
              fromAccountId: payload.fromAccountId,
              toAccountId: payload.toAccountId,
              amountMinor: payload.amountMinor
            };
            state.fdsCases.push(caseRecord);
            return caseRecord;
          });
        } else {
          result = await state.ledgerCore.transfer({
            fromAccountId: payload.fromAccountId,
            toAccountId: payload.toAccountId,
            amountMinor: payload.amountMinor,
            idempotencyKey: payload.idempotencyKey,
            requestedBy: payload.requestedBy || "customer01",
            requestedChannel: "CUSTOMER_WEB"
          });
          const transaction = result.value;
          state.auditLog.append({
            eventType: "COMMAND_EXECUTED",
            actorType: "CUSTOMER",
            actorId: payload.requestedBy || "customer01",
            actorRole: "CUSTOMER",
            businessReferenceId: transaction.id,
            accountId: payload.fromAccountId,
            reason: "Customer initiated synthetic transfer",
            payload: {
              idempotencyKey: payload.idempotencyKey,
              amountMinor: payload.amountMinor
            }
          });
        }
        json(response, result.replayed ? 200 : 201, {
          replayed: result.replayed,
          item: result.value
        });
        return;
      }

      if (pathname === "/api/complaints" && request.method === "GET") {
        json(response, 200, { items: state.complaints });
        return;
      }

      if (pathname === "/api/complaints" && request.method === "POST") {
        const payload = await bodyJson(request);
        const complaint = {
          caseId: `CMP-SYN-${String(state.complaints.length + 1).padStart(4, "0")}`,
          customerId: payload.customerId,
          category: payload.category,
          description: payload.description,
          status: "RECEIVED",
          owner: null,
          slaHours: 72,
          timeline: [
            {
              from: "SUBMITTED",
              to: "RECEIVED",
              actorId: payload.customerId,
              at: new Date().toISOString()
            }
          ]
        };
        state.complaints.push(complaint);
        json(response, 201, { item: complaint });
        return;
      }

      if (pathname === "/api/ledger/balances" && request.method === "GET") {
        json(response, 200, {
          items: state.ledgerCore.getBalances()
        });
        return;
      }

      if (pathname === "/api/ledger/transactions" && request.method === "GET") {
        json(response, 200, {
          items: state.ledgerCore.listTransactions(url.searchParams.get("accountId"))
        });
        return;
      }

      if (pathname === "/api/ledger/deposits" && request.method === "POST") {
        const payload = await bodyJson(request);
        const result = await state.ledgerCore.deposit(payload);
        state.auditLog.append({
          eventType: "COMMAND_EXECUTED",
          actorType: "STAFF",
          actorId: payload.requestedBy || "branch01",
          actorRole: "BRANCH_STAFF",
          businessReferenceId: result.value.id,
          accountId: payload.accountId,
          reason: payload.reason || "Synthetic ledger deposit",
          payload: {
            idempotencyKey: payload.idempotencyKey,
            amountMinor: payload.amountMinor
          }
        });
        json(response, result.replayed ? 200 : 201, result);
        return;
      }

      if (pathname === "/api/ledger/withdrawals" && request.method === "POST") {
        const payload = await bodyJson(request);
        const result = await state.ledgerCore.withdraw(payload);
        state.auditLog.append({
          eventType: "COMMAND_EXECUTED",
          actorType: "STAFF",
          actorId: payload.requestedBy || "branch01",
          actorRole: "BRANCH_STAFF",
          businessReferenceId: result.value.id,
          accountId: payload.accountId,
          reason: payload.reason || "Synthetic ledger withdrawal",
          payload: {
            idempotencyKey: payload.idempotencyKey,
            amountMinor: payload.amountMinor
          }
        });
        json(response, result.replayed ? 200 : 201, result);
        return;
      }

      if (pathname === "/api/ledger/transfers" && request.method === "POST") {
        const payload = await bodyJson(request);
        const result = await state.ledgerCore.transfer(payload);
        state.auditLog.append({
          eventType: "COMMAND_EXECUTED",
          actorType: "STAFF",
          actorId: payload.requestedBy || "branch01",
          actorRole: "BRANCH_STAFF",
          businessReferenceId: result.value.id,
          accountId: payload.fromAccountId,
          reason: payload.reason || "Synthetic ledger transfer",
          payload: {
            idempotencyKey: payload.idempotencyKey,
            amountMinor: payload.amountMinor
          }
        });
        json(response, result.replayed ? 200 : 201, result);
        return;
      }

      if (pathname === "/api/ledger/reversals" && request.method === "POST") {
        const payload = await bodyJson(request);
        const result = await state.ledgerCore.reverseTransaction(payload);
        state.auditLog.append({
          eventType: "COMMAND_EXECUTED",
          actorType: "STAFF",
          actorId: payload.requestedBy || "branch01",
          actorRole: "BRANCH_STAFF",
          businessReferenceId: result.value.id,
          reason: payload.reason || "Synthetic ledger reversal",
          payload: {
            idempotencyKey: payload.idempotencyKey,
            originalTransactionId: payload.originalTransactionId
          }
        });
        json(response, result.replayed ? 200 : 201, result);
        return;
      }

      json(response, 404, { error: "not found" });
    } catch (error) {
      json(response, 500, { error: error.message });
    }
  };
}

export async function createLabHttpServer(state) {
  const handler = await createLabHandler(state);
  return createServer(handler);
}
