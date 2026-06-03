import { readFile } from "node:fs/promises";
import { createServer } from "node:http";
import { extname, join } from "node:path";
import { fileURLToPath } from "node:url";
import {
  ApprovalStore,
  AuditLog,
  IdempotencyStore,
  inferApiError,
  isApiError,
  generateSyntheticDataset,
  loginMockUser,
  maskAccount,
  maskCustomer
} from "../packages/banking-domain/src/index.mjs";
import { LedgerCore } from "../legacy-node-reference/services/core-banking/src/index.mjs";
import {
  assignComplaint,
  classifyComplaint,
  closeComplaint,
  draftComplaintAnswer,
  markComplaintAnswered,
  receiveComplaint,
  startComplaintReview
} from "../legacy-node-reference/services/complaint-service/src/index.mjs";
import {
  assignFdsCase,
  createFdsCase,
  evaluateFdsRules,
  markFdsBlocked,
  markFdsReleased,
  requestFdsDecision
} from "../legacy-node-reference/services/fds-service/src/index.mjs";
import {
  addAmlComment,
  assignAmlCase,
  closeAmlCase,
  createAmlCase,
  deriveAmlRisk,
  evaluateAmlRules,
  requestAmlClosure
} from "../legacy-node-reference/services/aml-service/src/index.mjs";
import {
  markReconciliationAdjusted,
  requestReconciliationAdjustment,
  runDailyReconciliation
} from "../legacy-node-reference/services/reconciliation-service/src/index.mjs";
import { simulateExternalInstitutionFile } from "../legacy-node-reference/services/external-simulators/src/index.mjs";
import { filterManifestsByApp, loadManifests } from "../packages/screen-engine/src/index.mjs";

const __dirname = fileURLToPath(new URL(".", import.meta.url));
const repoRoot = join(__dirname, "..");

const MIME_TYPES = new Map([
  [".html", "text/html; charset=utf-8"],
  [".js", "text/javascript; charset=utf-8"],
  [".css", "text/css; charset=utf-8"],
  [".json", "application/json; charset=utf-8"]
]);

let requestSequence = 0;

function nextRequestId() {
  requestSequence += 1;
  return `REQ-${String(requestSequence).padStart(8, "0")}`;
}

function json(response, statusCode, payload) {
  const headers = { "content-type": "application/json; charset=utf-8" };
  if (response.__requestId) {
    headers["x-request-id"] = response.__requestId;
  }
  let body = payload;
  if (statusCode >= 400 && payload?.error) {
    body = {
      ...payload,
      error: isApiError(payload.error)
        ? payload.error
        : inferApiError(payload.error, {
          statusCode,
          requestId: response.__requestId,
          route: response.__routePath
        })
    };
  }
  response.writeHead(statusCode, headers);
  response.end(JSON.stringify(body, null, 2));
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

function findCustomer(state, customerId) {
  return state.dataset.customers.find((customer) => customer.customerId === customerId);
}

function publicCustomerDetail(customer, unmasked = false) {
  if (!customer) {
    return null;
  }
  if (unmasked) {
    return {
      customerId: customer.customerId,
      name: customer.name,
      phone: customer.phone,
      address: customer.address,
      customerGrade: customer.customerGrade,
      riskGrade: customer.riskGrade,
      piiExposure: "UNMASKED_TIMEBOXED"
    };
  }
  return {
    ...maskCustomer(customer),
    piiExposure: "MASKED"
  };
}

function publicTransactionForAccount(transaction, accountId) {
  return {
    transactionId: transaction.id,
    transactionType: transaction.transactionType,
    businessDate: transaction.businessDate,
    status: transaction.status,
    requestedChannel: transaction.requestedChannel,
    postings: transaction.postings
      .filter((posting) => posting.accountId === accountId)
      .map((posting) => ({
        postingId: posting.id,
        direction: posting.direction,
        amountMinor: posting.amountMinor,
        currency: posting.currency,
        postingType: posting.postingType
      }))
  };
}

function findAccount(state, accountId) {
  return state.dataset.accounts.find((account) => account.accountId === accountId);
}

function requireCustomerAccount(state, customerId, accountId) {
  const account = findAccount(state, accountId);
  if (!account || account.customerId !== customerId) {
    throw new Error(`customer account not found: ${accountId}`);
  }
  return account;
}

function transferResult(state, idempotencyKey) {
  return state.transferResults.find((item) => item.idempotencyKey === idempotencyKey);
}

function appendTransferResult(state, input) {
  const existing = transferResult(state, input.idempotencyKey);
  if (existing) {
    return existing;
  }
  const item = {
    resultId: input.resultId || `TRR-${String(state.transferResults.length + 1).padStart(8, "0")}`,
    idempotencyKey: input.idempotencyKey,
    customerId: input.customerId,
    fromAccountId: input.fromAccountId,
    toAccountId: input.toAccountId,
    amountMinor: input.amountMinor,
    status: input.status,
    transactionId: input.transactionId || null,
    caseId: input.caseId || null,
    failureCode: input.failureCode || null,
    message: input.message,
    createdAt: input.createdAt || new Date().toISOString()
  };
  state.transferResults.push(item);
  return item;
}

function replaceTransferResult(state, updated) {
  const index = state.transferResults.findIndex((item) => item.resultId === updated.resultId);
  if (index === -1) {
    throw new Error(`transfer result not found: ${updated.resultId}`);
  }
  state.transferResults[index] = updated;
  return updated;
}

function findComplaint(state, caseId) {
  return state.complaints.find((complaint) => complaint.caseId === caseId);
}

function publicComplaint(complaint) {
  return {
    caseId: complaint.caseId,
    customerId: complaint.customerId,
    category: complaint.category,
    description: complaint.description,
    status: complaint.status,
    owner: complaint.owner ? "Assigned" : null,
    slaHours: complaint.slaHours,
    slaDueAt: complaint.slaDueAt,
    createdAt: complaint.createdAt,
    answer: complaint.answer,
    customerConfirmedAt: complaint.customerConfirmedAt,
    timeline: (complaint.timeline || []).map((entry) => ({
      from: entry.from,
      to: entry.to,
      type: entry.type,
      at: entry.at
    }))
  };
}

function replaceComplaint(state, updated) {
  const index = state.complaints.findIndex((complaint) => complaint.caseId === updated.caseId);
  if (index === -1) {
    throw new Error(`complaint not found: ${updated.caseId}`);
  }
  state.complaints[index] = updated;
  return updated;
}

function findFdsCase(state, caseId) {
  return state.fdsCases.find((caseRecord) => caseRecord.caseId === caseId);
}

function replaceFdsCase(state, updated) {
  const index = state.fdsCases.findIndex((caseRecord) => caseRecord.caseId === updated.caseId);
  if (index === -1) {
    throw new Error(`FDS case not found: ${updated.caseId}`);
  }
  state.fdsCases[index] = updated;
  return updated;
}

function findAmlCase(state, caseId) {
  return state.amlCases.find((caseRecord) => caseRecord.caseId === caseId);
}

function replaceAmlCase(state, updated) {
  const index = state.amlCases.findIndex((caseRecord) => caseRecord.caseId === updated.caseId);
  if (index === -1) {
    throw new Error(`AML case not found: ${updated.caseId}`);
  }
  state.amlCases[index] = updated;
  return updated;
}

function findReconciliationItem(state, itemId) {
  return state.reconciliationItems.find((item) => item.itemId === itemId);
}

function replaceReconciliationItem(state, updated) {
  const index = state.reconciliationItems.findIndex((item) => item.itemId === updated.itemId);
  if (index === -1) {
    throw new Error(`reconciliation item not found: ${updated.itemId}`);
  }
  state.reconciliationItems[index] = updated;
  return updated;
}

function nextBusinessDate(businessDate) {
  const date = new Date(`${businessDate}T00:00:00.000Z`);
  date.setUTCDate(date.getUTCDate() + 1);
  return date.toISOString().slice(0, 10);
}

function maybeCreateAmlCaseForTransfer(state, input) {
  const customer = findCustomer(state, input.customerId);
  const alerts = evaluateAmlRules(customer, {
    amountMinor: input.amountMinor
  }, {
    transactionVelocity24h: input.transactionVelocity24h || 0
  });
  if (alerts.length === 0) {
    return null;
  }
  const amlCase = createAmlCase({
    caseId: `AML-${String(state.amlCases.length + 1).padStart(8, "0")}`,
    customerId: input.customerId,
    riskDecision: deriveAmlRisk(customer, input.transactionVelocity24h || 0),
    source: "FDS_HELD_TRANSFER",
    relatedTransferResultId: input.transferResultId,
    relatedFdsCaseId: input.fdsCaseId,
    amountMinor: input.amountMinor,
    alerts
  });
  state.amlCases.push(amlCase);
  state.auditLog.append({
    eventType: "COMMAND_REQUESTED",
    actorType: "SYSTEM",
    actorId: "AML_ENGINE",
    actorRole: "AML_ENGINE",
    screenId: "AML-201",
    businessReferenceId: amlCase.caseId,
    customerId: input.customerId,
    reason: "Synthetic AML case candidate generated",
    payload: {
      fdsCaseId: input.fdsCaseId,
      alertCount: alerts.length,
      riskDecision: amlCase.riskDecision
    }
  });
  return amlCase;
}

function applyCustomerInfoChange(state, approval, actor) {
  if (state.executedApprovalIds.has(approval.approvalId) || approval.businessType !== "CUSTOMER_INFO_CHANGE") {
    return false;
  }
  const customer = findCustomer(state, approval.businessReferenceId);
  if (!customer) {
    throw new Error(`customer not found: ${approval.businessReferenceId}`);
  }
  const after = approval.afterSnapshot || {};
  for (const field of ["phone", "address", "customerGrade"]) {
    if (after[field] !== undefined) {
      customer[field] = after[field];
    }
  }
  const ledgerCustomer = state.ledgerCore.customers.find((item) => item.customerId === customer.customerId);
  if (ledgerCustomer) {
    Object.assign(ledgerCustomer, customer);
  }
  state.executedApprovalIds.add(approval.approvalId);
  state.auditLog.append({
    eventType: "COMMAND_EXECUTED",
    actorType: "STAFF",
    actorId: actor.actorId,
    actorRole: actor.actorRole,
    screenId: "CST-103",
    businessReferenceId: approval.approvalId,
    customerId: customer.customerId,
    reason: approval.requestReason,
    payload: {
      businessType: approval.businessType,
      changedFields: Object.keys(after)
    }
  });
  return true;
}

function applyComplaintAnswerApproval(state, approval, actor) {
  if (state.executedApprovalIds.has(approval.approvalId) || approval.businessType !== "COMPLAINT_ANSWER_SEND") {
    return false;
  }
  const complaint = findComplaint(state, approval.businessReferenceId);
  if (!complaint) {
    throw new Error(`complaint not found: ${approval.businessReferenceId}`);
  }
  if (complaint.status !== "WAITING_APPROVAL") {
    throw new Error(`complaint answer is not waiting approval: ${complaint.caseId}`);
  }
  const answered = markComplaintAnswered(complaint, {
    actorId: actor.actorId,
    approvalId: approval.approvalId,
    body: approval.afterSnapshot?.answerBody || complaint.answerDraft?.body
  });
  replaceComplaint(state, answered);
  state.executedApprovalIds.add(approval.approvalId);
  state.auditLog.append({
    eventType: "COMMAND_EXECUTED",
    actorType: "STAFF",
    actorId: actor.actorId,
    actorRole: actor.actorRole,
    screenId: "CMP-201",
    businessReferenceId: approval.approvalId,
    customerId: complaint.customerId,
    reason: approval.requestReason,
    payload: {
      caseId: complaint.caseId,
      businessType: approval.businessType
    }
  });
  return true;
}

async function applyFdsReleaseApproval(state, approval, actor) {
  if (state.executedApprovalIds.has(approval.approvalId) || approval.businessType !== "FDS_RELEASE") {
    return null;
  }
  const caseRecord = findFdsCase(state, approval.businessReferenceId);
  if (!caseRecord) {
    throw new Error(`FDS case not found: ${approval.businessReferenceId}`);
  }
  if (caseRecord.status !== "RELEASE_REQUESTED") {
    throw new Error(`FDS case is not waiting release approval: ${caseRecord.caseId}`);
  }
  const result = await state.ledgerCore.transfer({
    fromAccountId: caseRecord.fromAccountId,
    toAccountId: caseRecord.toAccountId,
    amountMinor: caseRecord.amountMinor,
    idempotencyKey: approval.afterSnapshot?.releaseIdempotencyKey || `FDS-RELEASE-${caseRecord.caseId}`,
    businessReferenceId: caseRecord.caseId,
    requestedBy: actor.actorId,
    requestedChannel: "FDS_REVIEW",
    description: "FDS released held customer transfer"
  });
  const transaction = result.value;
  const updatedCase = replaceFdsCase(state, markFdsReleased(caseRecord, {
    actorId: actor.actorId,
    approvalId: approval.approvalId,
    transactionId: transaction.id
  }));
  const transfer = state.transferResults.find((item) => item.resultId === caseRecord.transferResultId);
  if (transfer) {
    replaceTransferResult(state, {
      ...transfer,
      status: "POSTED",
      transactionId: transaction.id,
      message: "FDS reviewer released transfer to ledger"
    });
  }
  state.executedApprovalIds.add(approval.approvalId);
  state.auditLog.append({
    eventType: "COMMAND_EXECUTED",
    actorType: "STAFF",
    actorId: actor.actorId,
    actorRole: actor.actorRole,
    screenId: "FDS-201",
    businessReferenceId: approval.approvalId,
    customerId: caseRecord.customerId,
    accountId: caseRecord.fromAccountId,
    reason: approval.requestReason,
    payload: {
      caseId: caseRecord.caseId,
      transactionId: transaction.id,
      businessType: approval.businessType
    }
  });
  return {
    caseRecord: updatedCase,
    transaction
  };
}

function applyFdsBlockApproval(state, approval, actor) {
  if (state.executedApprovalIds.has(approval.approvalId) || approval.businessType !== "FDS_BLOCK") {
    return null;
  }
  const caseRecord = findFdsCase(state, approval.businessReferenceId);
  if (!caseRecord) {
    throw new Error(`FDS case not found: ${approval.businessReferenceId}`);
  }
  if (caseRecord.status !== "BLOCK_REQUESTED") {
    throw new Error(`FDS case is not waiting block approval: ${caseRecord.caseId}`);
  }
  const updatedCase = replaceFdsCase(state, markFdsBlocked(caseRecord, {
    actorId: actor.actorId,
    approvalId: approval.approvalId
  }));
  const transfer = state.transferResults.find((item) => item.resultId === caseRecord.transferResultId);
  if (transfer) {
    replaceTransferResult(state, {
      ...transfer,
      status: "BLOCKED",
      failureCode: "FDS_BLOCKED",
      message: "FDS reviewer blocked transfer"
    });
  }
  state.executedApprovalIds.add(approval.approvalId);
  state.auditLog.append({
    eventType: "COMMAND_EXECUTED",
    actorType: "STAFF",
    actorId: actor.actorId,
    actorRole: actor.actorRole,
    screenId: "FDS-201",
    businessReferenceId: approval.approvalId,
    customerId: caseRecord.customerId,
    accountId: caseRecord.fromAccountId,
    reason: approval.requestReason,
    payload: {
      caseId: caseRecord.caseId,
      businessType: approval.businessType
    }
  });
  return {
    caseRecord: updatedCase
  };
}

function applyAmlClosureApproval(state, approval, actor) {
  if (state.executedApprovalIds.has(approval.approvalId) || approval.businessType !== "AML_CASE_CLOSE") {
    return null;
  }
  const caseRecord = findAmlCase(state, approval.businessReferenceId);
  if (!caseRecord) {
    throw new Error(`AML case not found: ${approval.businessReferenceId}`);
  }
  if (caseRecord.status !== "CLOSURE_REQUESTED") {
    throw new Error(`AML case is not waiting closure approval: ${caseRecord.caseId}`);
  }
  const updatedCase = replaceAmlCase(state, closeAmlCase(caseRecord, {
    actorId: actor.actorId,
    approvalId: approval.approvalId,
    disposition: approval.afterSnapshot?.disposition,
    reportReferenceId: approval.afterSnapshot?.reportReferenceId
  }));
  state.executedApprovalIds.add(approval.approvalId);
  state.auditLog.append({
    eventType: "COMMAND_EXECUTED",
    actorType: "STAFF",
    actorId: actor.actorId,
    actorRole: actor.actorRole,
    screenId: "AML-201",
    businessReferenceId: approval.approvalId,
    customerId: caseRecord.customerId,
    reason: approval.requestReason,
    payload: {
      caseId: caseRecord.caseId,
      businessType: approval.businessType,
      disposition: approval.afterSnapshot?.disposition
    }
  });
  return {
    caseRecord: updatedCase
  };
}

async function applyReconciliationAdjustmentApproval(state, approval, actor) {
  if (state.executedApprovalIds.has(approval.approvalId) || approval.businessType !== "RECONCILIATION_ADJUSTMENT") {
    return null;
  }
  const item = findReconciliationItem(state, approval.businessReferenceId);
  if (!item) {
    throw new Error(`reconciliation item not found: ${approval.businessReferenceId}`);
  }
  if (item.status !== "ADJUSTMENT_REQUESTED") {
    throw new Error(`reconciliation item is not waiting adjustment approval: ${item.itemId}`);
  }
  const after = approval.afterSnapshot || {};
  const result = await state.ledgerCore.adjustment({
    accountId: after.accountId,
    direction: after.direction,
    amountMinor: after.amountMinor,
    idempotencyKey: after.idempotencyKey || `REC-ADJ-${item.itemId}`,
    businessReferenceId: item.itemId,
    businessDate: after.businessDate || nextBusinessDate(item.businessDate),
    requestedBy: actor.actorId,
    requestedChannel: "OPS_RECONCILIATION",
    reason: approval.requestReason
  });
  const transaction = result.value;
  const updatedItem = replaceReconciliationItem(state, markReconciliationAdjusted(item, {
    actorId: actor.actorId,
    transactionId: transaction.id
  }));
  state.executedApprovalIds.add(approval.approvalId);
  state.auditLog.append({
    eventType: "COMMAND_EXECUTED",
    actorType: "STAFF",
    actorId: actor.actorId,
    actorRole: actor.actorRole,
    screenId: "OPS-201",
    businessReferenceId: approval.approvalId,
    accountId: after.accountId,
    reason: approval.requestReason,
    payload: {
      itemId: item.itemId,
      businessType: approval.businessType,
      transactionId: transaction.id
    }
  });
  return {
    item: updatedItem,
    transaction
  };
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
    receiveComplaint({
      caseId: "CMP-SYN-0001",
      customerId: "SYN-CUS-001",
      category: "TRANSFER_DISPUTE",
      description: "Synthetic seeded complaint for workflow review"
    })
  ];
  const fdsCases = [];
  const amlCases = [];
  const transferResults = [];
  const dailyClosings = [];
  const reconciliationItems = [];
  const externalFiles = [];
  const executedApprovalIds = new Set();

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
    fdsCases,
    amlCases,
    transferResults,
    dailyClosings,
    reconciliationItems,
    externalFiles,
    executedApprovalIds
  };
}

export async function createLabHandler(state) {
  state = state || await createLabState();
  return async function handler(request, response) {
    response.__requestId = request.headers["x-request-id"] || nextRequestId();
    try {
      const url = new URL(request.url, `http://${request.headers.host || "127.0.0.1"}`);
      const pathname = url.pathname;
      response.__routePath = pathname;

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
            "fds01",
            "ops01",
            "compliance01"
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

      if (pathname === "/api/customer/login" && request.method === "POST") {
        const payload = await bodyJson(request);
        const session = loginMockUser({ userId: payload.userId || "customer01", auditLog: state.auditLog });
        if (session.actorType !== "CUSTOMER") {
          json(response, 403, { error: "mock user is not a customer" });
          return;
        }
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

      const customerDetailMatch = pathname.match(/^\/api\/staff\/customers\/([^/]+)\/detail$/);
      if (customerDetailMatch && request.method === "GET") {
        const customerId = decodeURIComponent(customerDetailMatch[1]);
        const reason = url.searchParams.get("reason");
        const customer = findCustomer(state, customerId);
        if (!customer) {
          json(response, 404, { error: "customer not found" });
          return;
        }
        if (!reason) {
          json(response, 400, { error: "CUSTOMER_DETAIL_VIEW requires a business reason" });
          return;
        }
        const event = state.auditLog.append({
          eventType: "CUSTOMER_DETAIL_VIEW",
          actorType: "STAFF",
          actorId: "branch01",
          actorRole: "BRANCH_STAFF",
          screenId: "CST-002",
          customerId,
          reason,
          payload: { piiExposure: "MASKED" }
        });
        json(response, 200, {
          auditEventId: event.auditEventId,
          item: publicCustomerDetail(customer)
        });
        return;
      }

      if (pathname === "/api/staff/pii/unmask" && request.method === "POST") {
        const payload = await bodyJson(request);
        const customer = findCustomer(state, payload.customerId);
        if (!customer) {
          json(response, 404, { error: "customer not found" });
          return;
        }
        if (!payload.reason) {
          json(response, 400, { error: "PII_UNMASK_REQUESTED requires a business reason" });
          return;
        }
        const actorRole = payload.actorRole || "BRANCH_MANAGER";
        if (!["BRANCH_MANAGER", "AUDITOR", "COMPLIANCE_MANAGER"].includes(actorRole)) {
          json(response, 403, { error: "actor role cannot unmask PII" });
          return;
        }
        const event = state.auditLog.append({
          eventType: "PII_UNMASK_REQUESTED",
          actorType: "STAFF",
          actorId: payload.requestedBy || "manager01",
          actorRole,
          screenId: payload.screenId || "CST-002",
          customerId: payload.customerId,
          reason: payload.reason,
          payload: {
            ttlSeconds: 300,
            scope: "SINGLE_CUSTOMER"
          }
        });
        json(response, 200, {
          auditEventId: event.auditEventId,
          expiresInSeconds: 300,
          item: publicCustomerDetail(customer, true)
        });
        return;
      }

      if (pathname === "/api/staff/accounts/search" && request.method === "GET") {
        const reason = url.searchParams.get("reason");
        if (!reason) {
          json(response, 400, { error: "ACCOUNT_VIEW requires a business reason" });
          return;
        }
        const customerId = url.searchParams.get("customerId");
        const accountId = url.searchParams.get("accountId");
        const balances = state.ledgerCore.getBalances();
        const accounts = state.ledgerCore.listAccounts(customerId)
          .filter((account) => !accountId || account.accountId === accountId)
          .map((account) => ({
            customerId: account.customerId,
            ...maskAccount(account),
            ...balances.find((balance) => balance.accountId === account.accountId)
          }));
        const event = state.auditLog.append({
          eventType: "ACCOUNT_VIEW",
          actorType: "STAFF",
          actorId: "branch01",
          actorRole: "BRANCH_STAFF",
          screenId: "ACC-101",
          customerId,
          accountId,
          reason,
          payload: { resultCount: accounts.length }
        });
        json(response, 200, {
          auditEventId: event.auditEventId,
          items: accounts
        });
        return;
      }

      if (pathname === "/api/staff/transactions/search" && request.method === "GET") {
        const reason = url.searchParams.get("reason");
        const accountId = url.searchParams.get("accountId");
        if (!reason) {
          json(response, 400, { error: "TRANSACTION_VIEW requires a business reason" });
          return;
        }
        if (!accountId) {
          json(response, 400, { error: "accountId is required" });
          return;
        }
        const transactions = state.ledgerCore.listTransactions(accountId)
          .map((transaction) => publicTransactionForAccount(transaction, accountId));
        const event = state.auditLog.append({
          eventType: "TRANSACTION_VIEW",
          actorType: "STAFF",
          actorId: "branch01",
          actorRole: "BRANCH_STAFF",
          screenId: "LED-101",
          accountId,
          reason,
          payload: { resultCount: transactions.length }
        });
        json(response, 200, {
          auditEventId: event.auditEventId,
          items: transactions
        });
        return;
      }

      const customerChangeMatch = pathname.match(/^\/api\/staff\/customers\/([^/]+)\/change-requests$/);
      if (customerChangeMatch && request.method === "POST") {
        const customerId = decodeURIComponent(customerChangeMatch[1]);
        const payload = await bodyJson(request);
        const customer = findCustomer(state, customerId);
        if (!customer) {
          json(response, 404, { error: "customer not found" });
          return;
        }
        if (!payload.reason) {
          json(response, 400, { error: "CUSTOMER_INFO_CHANGE requires a business reason" });
          return;
        }
        const allowed = {};
        for (const field of ["phone", "address", "customerGrade"]) {
          if (payload.afterSnapshot?.[field] !== undefined) {
            allowed[field] = payload.afterSnapshot[field];
          }
        }
        if (Object.keys(allowed).length === 0) {
          json(response, 400, { error: "afterSnapshot must include a supported customer field" });
          return;
        }
        const approval = state.approvalStore.submit({
          businessType: "CUSTOMER_INFO_CHANGE",
          businessReferenceId: customerId,
          requestedBy: payload.requestedBy || "branch01",
          requestedByRole: payload.requestedByRole || "BRANCH_STAFF",
          requestReason: payload.reason,
          beforeSnapshot: {
            phone: customer.phone,
            address: customer.address,
            customerGrade: customer.customerGrade
          },
          afterSnapshot: allowed,
          screenId: "CST-103"
        });
        json(response, 201, {
          item: approval,
          customer: publicCustomerDetail(customer)
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
        const actor = {
          actorId: payload.approvedBy,
          actorRole: payload.approvedByRole || "BRANCH_MANAGER"
        };
        const customerExecuted = applyCustomerInfoChange(state, approval, actor);
        const complaintExecuted = applyComplaintAnswerApproval(state, approval, actor);
        const fdsReleaseExecution = await applyFdsReleaseApproval(state, approval, actor);
        const fdsBlockExecution = applyFdsBlockApproval(state, approval, actor);
        const amlClosureExecution = applyAmlClosureApproval(state, approval, actor);
        const reconciliationExecution = await applyReconciliationAdjustmentApproval(state, approval, actor);
        json(response, 200, {
          item: approval,
          executed: Boolean(customerExecuted || complaintExecuted || fdsReleaseExecution || fdsBlockExecution || amlClosureExecution || reconciliationExecution),
          customer: approval.businessType === "CUSTOMER_INFO_CHANGE"
            ? publicCustomerDetail(findCustomer(state, approval.businessReferenceId))
            : null,
          complaint: approval.businessType === "COMPLAINT_ANSWER_SEND"
            ? findComplaint(state, approval.businessReferenceId)
            : null,
          fdsCase: approval.businessType === "FDS_RELEASE" || approval.businessType === "FDS_BLOCK"
            ? findFdsCase(state, approval.businessReferenceId)
            : null,
          amlCase: approval.businessType === "AML_CASE_CLOSE"
            ? findAmlCase(state, approval.businessReferenceId)
            : null,
          reconciliationItem: approval.businessType === "RECONCILIATION_ADJUSTMENT"
            ? findReconciliationItem(state, approval.businessReferenceId)
            : null,
          ledgerTransaction: fdsReleaseExecution?.transaction || reconciliationExecution?.transaction || null
        });
        return;
      }

      const approvalRejectMatch = pathname.match(/^\/api\/staff\/approvals\/([^/]+)\/reject$/);
      if (approvalRejectMatch && request.method === "POST") {
        const payload = await bodyJson(request);
        const approval = state.approvalStore.reject(approvalRejectMatch[1], payload);
        json(response, 200, {
          item: approval
        });
        return;
      }

      if (pathname === "/api/staff/fds-cases" && request.method === "GET") {
        json(response, 200, { items: state.fdsCases });
        return;
      }

      const fdsCaseActionMatch = pathname.match(/^\/api\/staff\/fds-cases\/([^/]+)\/([^/]+)$/);
      if (fdsCaseActionMatch && request.method === "POST") {
        const caseId = decodeURIComponent(fdsCaseActionMatch[1]);
        const action = decodeURIComponent(fdsCaseActionMatch[2]);
        const payload = await bodyJson(request);
        const caseRecord = findFdsCase(state, caseId);
        if (!caseRecord) {
          json(response, 404, { error: "FDS case not found" });
          return;
        }
        const actorId = payload.actorId || payload.requestedBy || "fds01";
        let updated;
        let approval = null;
        if (action === "assign") {
          updated = assignFdsCase(caseRecord, {
            actorId,
            owner: payload.owner || actorId
          });
        } else if (action === "release-requests" || action === "block-requests") {
          if (!payload.reason) {
            json(response, 400, { error: "FDS decision requires a business reason" });
            return;
          }
          const decision = action === "release-requests" ? "RELEASE" : "BLOCK";
          approval = state.approvalStore.submit({
            businessType: decision === "RELEASE" ? "FDS_RELEASE" : "FDS_BLOCK",
            businessReferenceId: caseId,
            requestedBy: actorId,
            requestedByRole: payload.requestedByRole || "FDS_REVIEWER",
            requestReason: payload.reason,
            beforeSnapshot: {
              status: caseRecord.status,
              transferResultId: caseRecord.transferResultId
            },
            afterSnapshot: {
              decision,
              releaseIdempotencyKey: `FDS-RELEASE-${caseId}`,
              blockReferenceId: `FDS-BLOCK-${caseId}`
            },
            screenId: "FDS-201"
          });
          updated = requestFdsDecision(caseRecord, {
            actorId,
            action: decision,
            approvalId: approval.approvalId,
            reason: payload.reason
          });
        } else {
          json(response, 404, { error: "unsupported FDS action" });
          return;
        }
        replaceFdsCase(state, updated);
        state.auditLog.append({
          eventType: "COMMAND_EXECUTED",
          actorType: "STAFF",
          actorId,
          actorRole: payload.actorRole || payload.requestedByRole || "FDS_REVIEWER",
          screenId: "FDS-201",
          businessReferenceId: caseId,
          customerId: updated.customerId,
          accountId: updated.fromAccountId,
          reason: payload.reason || `FDS ${action}`,
          payload: {
            action,
            status: updated.status,
            approvalId: approval?.approvalId || null
          }
        });
        json(response, 200, {
          item: updated,
          approval
        });
        return;
      }

      if (pathname === "/api/staff/aml-cases" && request.method === "GET") {
        json(response, 200, { items: state.amlCases });
        return;
      }

      const amlCaseActionMatch = pathname.match(/^\/api\/staff\/aml-cases\/([^/]+)\/([^/]+)$/);
      if (amlCaseActionMatch && request.method === "POST") {
        const caseId = decodeURIComponent(amlCaseActionMatch[1]);
        const action = decodeURIComponent(amlCaseActionMatch[2]);
        const payload = await bodyJson(request);
        const caseRecord = findAmlCase(state, caseId);
        if (!caseRecord) {
          json(response, 404, { error: "AML case not found" });
          return;
        }
        const actorId = payload.actorId || payload.requestedBy || "fds01";
        let updated;
        let approval = null;
        if (action === "assign") {
          updated = assignAmlCase(caseRecord, {
            actorId,
            owner: payload.owner || actorId
          });
        } else if (action === "comments") {
          updated = addAmlComment(caseRecord, {
            actorId,
            body: payload.body
          });
        } else if (action === "closure-requests") {
          if (!payload.reason) {
            json(response, 400, { error: "AML closure requires a business reason" });
            return;
          }
          approval = state.approvalStore.submit({
            businessType: "AML_CASE_CLOSE",
            businessReferenceId: caseId,
            requestedBy: actorId,
            requestedByRole: payload.requestedByRole || "AML_REVIEWER",
            requestReason: payload.reason,
            beforeSnapshot: {
              status: caseRecord.status,
              strSimulation: caseRecord.strSimulation
            },
            afterSnapshot: {
              disposition: payload.disposition || "FALSE_POSITIVE",
              reportReferenceId: payload.reportReferenceId || null
            },
            screenId: "AML-201"
          });
          updated = requestAmlClosure(caseRecord, {
            actorId,
            approvalId: approval.approvalId,
            disposition: payload.disposition || "FALSE_POSITIVE",
            reason: payload.reason
          });
        } else {
          json(response, 404, { error: "unsupported AML action" });
          return;
        }
        replaceAmlCase(state, updated);
        state.auditLog.append({
          eventType: "COMMAND_EXECUTED",
          actorType: "STAFF",
          actorId,
          actorRole: payload.actorRole || payload.requestedByRole || "AML_REVIEWER",
          screenId: "AML-201",
          businessReferenceId: caseId,
          customerId: updated.customerId,
          reason: payload.reason || `AML ${action}`,
          payload: {
            action,
            status: updated.status,
            approvalId: approval?.approvalId || null
          }
        });
        json(response, 200, {
          item: updated,
          approval
        });
        return;
      }

      if (pathname === "/api/ops/daily-closings" && request.method === "GET") {
        json(response, 200, { items: state.dailyClosings });
        return;
      }

      if (pathname === "/api/ops/daily-closings" && request.method === "POST") {
        const payload = await bodyJson(request);
        const businessDate = payload.businessDate || new Date().toISOString().slice(0, 10);
        const externalFile = payload.externalEntries
          ? {
            fileId: payload.fileId || `EXT-FILE-${String(state.externalFiles.length + 1).padStart(8, "0")}`,
            businessDate,
            entries: payload.externalEntries
          }
          : simulateExternalInstitutionFile({
            businessDate,
            transactions: state.ledgerCore.transactions,
            mode: payload.externalMode || "MISMATCH"
          });
        const report = runDailyReconciliation({
          closingId: `EOD-${String(state.dailyClosings.length + 1).padStart(8, "0")}`,
          businessDate,
          transactions: state.ledgerCore.transactions,
          accounts: state.ledgerCore.accounts,
          externalEntries: externalFile.entries,
          itemSeed: state.reconciliationItems.length + 1
        });
        state.ledgerCore.closeBusinessDay(businessDate);
        state.externalFiles.push(externalFile);
        state.dailyClosings.push(report.closing);
        state.reconciliationItems.push(...report.items);
        state.auditLog.append({
          eventType: "BATCH_STARTED",
          actorType: "STAFF",
          actorId: payload.requestedBy || "ops01",
          actorRole: payload.actorRole || "OPS_OPERATOR",
          screenId: "OPS-101",
          businessReferenceId: report.closing.closingId,
          payload: {
            businessDate,
            status: report.closing.status,
            unmatchedItemCount: report.closing.unmatchedItemCount
          }
        });
        json(response, 201, {
          item: report.closing,
          externalFile,
          reconciliationItems: report.items
        });
        return;
      }

      if (pathname === "/api/ops/reconciliation-items" && request.method === "GET") {
        json(response, 200, { items: state.reconciliationItems });
        return;
      }

      const reconciliationAdjustmentMatch = pathname.match(/^\/api\/ops\/reconciliation-items\/([^/]+)\/adjustment-requests$/);
      if (reconciliationAdjustmentMatch && request.method === "POST") {
        const itemId = decodeURIComponent(reconciliationAdjustmentMatch[1]);
        const payload = await bodyJson(request);
        const item = findReconciliationItem(state, itemId);
        if (!item) {
          json(response, 404, { error: "reconciliation item not found" });
          return;
        }
        if (!payload.reason) {
          json(response, 400, { error: "RECONCILIATION_ADJUSTMENT requires a business reason" });
          return;
        }
        const amountMinor = payload.amountMinor || Math.abs((item.externalAmountMinor || 0) - (item.internalAmountMinor || 0));
        const approval = state.approvalStore.submit({
          businessType: "RECONCILIATION_ADJUSTMENT",
          businessReferenceId: itemId,
          requestedBy: payload.requestedBy || "ops01",
          requestedByRole: payload.requestedByRole || "OPS_OPERATOR",
          requestReason: payload.reason,
          beforeSnapshot: {
            status: item.status,
            internalAmountMinor: item.internalAmountMinor,
            externalAmountMinor: item.externalAmountMinor
          },
          afterSnapshot: {
            accountId: payload.accountId || "ACC-SYN-001-001",
            direction: payload.direction || "CREDIT",
            amountMinor,
            businessDate: payload.businessDate || nextBusinessDate(item.businessDate),
            idempotencyKey: payload.idempotencyKey || `REC-ADJ-${itemId}`
          },
          screenId: "OPS-201"
        });
        const updated = requestReconciliationAdjustment(item, {
          actorId: payload.requestedBy || "ops01",
          approvalId: approval.approvalId,
          accountId: payload.accountId || "ACC-SYN-001-001",
          direction: payload.direction || "CREDIT",
          amountMinor,
          reason: payload.reason
        });
        replaceReconciliationItem(state, updated);
        state.auditLog.append({
          eventType: "COMMAND_EXECUTED",
          actorType: "STAFF",
          actorId: payload.requestedBy || "ops01",
          actorRole: payload.requestedByRole || "OPS_OPERATOR",
          screenId: "OPS-201",
          businessReferenceId: itemId,
          accountId: payload.accountId || "ACC-SYN-001-001",
          reason: payload.reason,
          payload: {
            status: updated.status,
            approvalId: approval.approvalId
          }
        });
        json(response, 201, {
          item: updated,
          approval
        });
        return;
      }

      if (pathname === "/api/ops/external-files" && request.method === "GET") {
        json(response, 200, { items: state.externalFiles });
        return;
      }

      if (pathname === "/api/risk/summary" && request.method === "GET") {
        json(response, 200, {
          fdsCases: state.fdsCases,
          amlCases: state.amlCases,
          reconciliationItems: state.reconciliationItems,
          dailyClosings: state.dailyClosings
        });
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

      const customerAccountDetailMatch = pathname.match(/^\/api\/customer\/accounts\/([^/]+)\/detail$/);
      if (customerAccountDetailMatch && request.method === "GET") {
        const accountId = decodeURIComponent(customerAccountDetailMatch[1]);
        const customerId = url.searchParams.get("customerId") || "SYN-CUS-001";
        let account;
        try {
          account = requireCustomerAccount(state, customerId, accountId);
        } catch (error) {
          json(response, 404, { error: error.message });
          return;
        }
        const balance = state.ledgerCore.getAccountBalance(accountId);
        const event = state.auditLog.append({
          eventType: "ACCOUNT_VIEW",
          actorType: "CUSTOMER",
          actorId: customerId,
          actorRole: "CUSTOMER",
          businessReferenceId: accountId,
          customerId,
          accountId,
          reason: "Customer self-service account detail",
          payload: { channel: "CUSTOMER_WEB" }
        });
        json(response, 200, {
          auditEventId: event.auditEventId,
          item: {
            customerId,
            ...maskAccount(account),
            ...balance
          }
        });
        return;
      }

      if (pathname === "/api/customer/transactions" && request.method === "GET") {
        const customerId = url.searchParams.get("customerId") || "SYN-CUS-001";
        const accountId = url.searchParams.get("accountId");
        if (!accountId) {
          json(response, 400, { error: "accountId is required" });
          return;
        }
        try {
          requireCustomerAccount(state, customerId, accountId);
        } catch (error) {
          json(response, 404, { error: error.message });
          return;
        }
        const transactions = state.ledgerCore.listTransactions(accountId)
          .map((transaction) => publicTransactionForAccount(transaction, accountId));
        const event = state.auditLog.append({
          eventType: "TRANSACTION_VIEW",
          actorType: "CUSTOMER",
          actorId: customerId,
          actorRole: "CUSTOMER",
          customerId,
          accountId,
          reason: "Customer self-service transaction history",
          payload: { resultCount: transactions.length }
        });
        json(response, 200, {
          auditEventId: event.auditEventId,
          items: transactions
        });
        return;
      }

      if (pathname === "/api/customer/transfers" && request.method === "GET") {
        const customerId = url.searchParams.get("customerId") || "SYN-CUS-001";
        json(response, 200, {
          items: state.transferResults.filter((item) => item.customerId === customerId)
        });
        return;
      }

      if (pathname === "/api/customer/transfers" && request.method === "POST") {
        const payload = await bodyJson(request);
        const customerId = payload.requestedBy || "SYN-CUS-001";
        const existingResult = transferResult(state, payload.idempotencyKey);
        if (existingResult) {
          json(response, 200, {
            replayed: true,
            item: existingResult
          });
          return;
        }
        try {
          requireCustomerAccount(state, customerId, payload.fromAccountId);
        } catch (error) {
          const failed = appendTransferResult(state, {
            idempotencyKey: payload.idempotencyKey,
            customerId,
            fromAccountId: payload.fromAccountId,
            toAccountId: payload.toAccountId,
            amountMinor: payload.amountMinor,
            status: "FAILED",
            failureCode: "ACCOUNT_NOT_FOUND",
            message: error.message
          });
          json(response, 404, {
            replayed: false,
            item: failed
          });
          return;
        }
        const fdsAlerts = evaluateFdsRules(payload, {
          newDevice: payload.newDevice === true,
          firstTimeBeneficiary: payload.firstTimeBeneficiary === true,
          transferVelocity10m: payload.transferVelocity10m || 0
        });
        if (fdsAlerts.length > 0) {
          const held = appendTransferResult(state, {
            idempotencyKey: payload.idempotencyKey,
            customerId,
            fromAccountId: payload.fromAccountId,
            toAccountId: payload.toAccountId,
            amountMinor: payload.amountMinor,
            status: "HELD",
            caseId: `FDS-${String(state.fdsCases.length + 1).padStart(8, "0")}`,
            message: "Transfer held for FDS review"
          });
          const caseRecord = createFdsCase({
            caseId: held.caseId,
            customerId,
            fromAccountId: payload.fromAccountId,
            toAccountId: payload.toAccountId,
            amountMinor: payload.amountMinor,
            idempotencyKey: payload.idempotencyKey,
            transferResultId: held.resultId,
            deviceFingerprint: payload.deviceFingerprint || "LAB-DEVICE",
            firstTimeBeneficiary: payload.firstTimeBeneficiary === true,
            transferVelocity10m: payload.transferVelocity10m || 0,
            alerts: fdsAlerts
          });
          state.fdsCases.push(caseRecord);
          maybeCreateAmlCaseForTransfer(state, {
            customerId,
            fdsCaseId: caseRecord.caseId,
            transferResultId: held.resultId,
            amountMinor: payload.amountMinor,
            transactionVelocity24h: payload.transactionVelocity24h || 0
          });
          state.auditLog.append({
            eventType: "COMMAND_REQUESTED",
            actorType: "CUSTOMER",
            actorId: customerId,
            actorRole: "CUSTOMER",
            screenId: "CWB-201",
            businessReferenceId: held.resultId,
            accountId: payload.fromAccountId,
            customerId,
            reason: "Customer transfer held for FDS review",
            payload: {
              idempotencyKey: payload.idempotencyKey,
              amountMinor: payload.amountMinor,
              caseId: caseRecord.caseId,
              alertCount: fdsAlerts.length
            }
          });
          json(response, 202, {
            replayed: false,
            item: held,
            fdsCase: caseRecord
          });
          return;
        }
        try {
          const result = await state.ledgerCore.transfer({
            fromAccountId: payload.fromAccountId,
            toAccountId: payload.toAccountId,
            amountMinor: payload.amountMinor,
            idempotencyKey: payload.idempotencyKey,
            requestedBy: customerId,
            requestedChannel: "CUSTOMER_WEB"
          });
          const transaction = result.value;
          const posted = appendTransferResult(state, {
            idempotencyKey: payload.idempotencyKey,
            customerId,
            fromAccountId: payload.fromAccountId,
            toAccountId: payload.toAccountId,
            amountMinor: payload.amountMinor,
            status: "POSTED",
            transactionId: transaction.id,
            message: "Transfer posted to ledger"
          });
          maybeCreateAmlCaseForTransfer(state, {
            customerId,
            transferResultId: posted.resultId,
            amountMinor: payload.amountMinor,
            transactionVelocity24h: payload.transactionVelocity24h || 0
          });
          state.auditLog.append({
            eventType: "COMMAND_EXECUTED",
            actorType: "CUSTOMER",
            actorId: customerId,
            actorRole: "CUSTOMER",
            businessReferenceId: transaction.id,
            accountId: payload.fromAccountId,
            customerId,
            reason: "Customer initiated synthetic transfer",
            payload: {
              idempotencyKey: payload.idempotencyKey,
              amountMinor: payload.amountMinor
            }
          });
          json(response, result.replayed ? 200 : 201, {
            replayed: result.replayed,
            item: posted
          });
          return;
        } catch (error) {
          const failed = appendTransferResult(state, {
            idempotencyKey: payload.idempotencyKey,
            customerId,
            fromAccountId: payload.fromAccountId,
            toAccountId: payload.toAccountId,
            amountMinor: payload.amountMinor,
            status: "FAILED",
            failureCode: "LEDGER_REJECTED",
            message: error.message
          });
          state.auditLog.append({
            eventType: "COMMAND_REJECTED",
            actorType: "CUSTOMER",
            actorId: customerId,
            actorRole: "CUSTOMER",
            businessReferenceId: failed.resultId,
            accountId: payload.fromAccountId,
            customerId,
            payload: {
              idempotencyKey: payload.idempotencyKey,
              amountMinor: payload.amountMinor,
              error: error.message
            }
          });
          json(response, 200, {
            replayed: false,
            item: failed
          });
          return;
        }
      }

      if (pathname === "/api/complaints" && request.method === "GET") {
        const customerId = url.searchParams.get("customerId");
        json(response, 200, {
          items: state.complaints
            .filter((complaint) => !customerId || complaint.customerId === customerId)
            .map(publicComplaint)
        });
        return;
      }

      if (pathname === "/api/complaints" && request.method === "POST") {
        const payload = await bodyJson(request);
        const complaint = receiveComplaint({
          caseId: `CMP-SYN-${String(state.complaints.length + 1).padStart(4, "0")}`,
          customerId: payload.customerId,
          category: payload.category,
          description: payload.description,
          attachments: payload.attachments || []
        });
        state.complaints.push(complaint);
        state.auditLog.append({
          eventType: "COMMAND_REQUESTED",
          actorType: "CUSTOMER",
          actorId: payload.customerId,
          actorRole: "CUSTOMER",
          screenId: "CMP-101",
          businessReferenceId: complaint.caseId,
          customerId: payload.customerId,
          reason: "Customer submitted complaint",
          payload: {
            category: payload.category,
            status: complaint.status
          }
        });
        json(response, 201, { item: publicComplaint(complaint) });
        return;
      }

      const complaintDetailMatch = pathname.match(/^\/api\/complaints\/([^/]+)$/);
      if (complaintDetailMatch && request.method === "GET") {
        const complaint = findComplaint(state, decodeURIComponent(complaintDetailMatch[1]));
        if (!complaint) {
          json(response, 404, { error: "complaint not found" });
          return;
        }
        json(response, 200, { item: publicComplaint(complaint) });
        return;
      }

      if (pathname === "/api/staff/complaints" && request.method === "GET") {
        json(response, 200, { items: state.complaints });
        return;
      }

      const staffComplaintActionMatch = pathname.match(/^\/api\/staff\/complaints\/([^/]+)\/([^/]+)$/);
      if (staffComplaintActionMatch && request.method === "POST") {
        const caseId = decodeURIComponent(staffComplaintActionMatch[1]);
        const action = decodeURIComponent(staffComplaintActionMatch[2]);
        const payload = await bodyJson(request);
        const complaint = findComplaint(state, caseId);
        if (!complaint) {
          json(response, 404, { error: "complaint not found" });
          return;
        }
        const actorId = payload.actorId || payload.requestedBy || "complaint01";
        let updated;
        let approval = null;
        if (action === "classify") {
          updated = classifyComplaint(complaint, {
            actorId,
            category: payload.category,
            classification: payload.classification,
            note: payload.note
          });
        } else if (action === "assign") {
          updated = assignComplaint(complaint, {
            actorId,
            owner: payload.owner || "complaint01"
          });
        } else if (action === "start-review") {
          updated = startComplaintReview(complaint, {
            actorId,
            note: payload.note
          });
        } else if (action === "answer-drafts") {
          if (!payload.body) {
            json(response, 400, { error: "answer draft body is required" });
            return;
          }
          if (!payload.reason) {
            json(response, 400, { error: "COMPLAINT_ANSWER_SEND requires a business reason" });
            return;
          }
          updated = draftComplaintAnswer(complaint, {
            actorId,
            body: payload.body
          });
          approval = state.approvalStore.submit({
            businessType: "COMPLAINT_ANSWER_SEND",
            businessReferenceId: caseId,
            requestedBy: actorId,
            requestedByRole: payload.requestedByRole || "COMPLAINT_HANDLER",
            requestReason: payload.reason,
            beforeSnapshot: {
              status: complaint.status,
              answer: complaint.answer
            },
            afterSnapshot: {
              answerBody: payload.body
            },
            screenId: "CMP-201"
          });
          updated = {
            ...updated,
            approvalId: approval.approvalId
          };
        } else {
          json(response, 404, { error: "unsupported complaint action" });
          return;
        }
        replaceComplaint(state, updated);
        state.auditLog.append({
          eventType: "COMMAND_EXECUTED",
          actorType: "STAFF",
          actorId,
          actorRole: payload.actorRole || payload.requestedByRole || "COMPLAINT_HANDLER",
          screenId: "CMP-201",
          businessReferenceId: caseId,
          customerId: updated.customerId,
          reason: payload.reason || `Complaint ${action}`,
          payload: {
            action,
            status: updated.status,
            approvalId: approval?.approvalId || null
          }
        });
        json(response, 200, {
          item: updated,
          approval
        });
        return;
      }

      const customerComplaintConfirmMatch = pathname.match(/^\/api\/customer\/complaints\/([^/]+)\/confirm$/);
      if (customerComplaintConfirmMatch && request.method === "POST") {
        const caseId = decodeURIComponent(customerComplaintConfirmMatch[1]);
        const payload = await bodyJson(request);
        const complaint = findComplaint(state, caseId);
        if (!complaint) {
          json(response, 404, { error: "complaint not found" });
          return;
        }
        const customerId = payload.customerId || complaint.customerId;
        if (complaint.customerId !== customerId) {
          json(response, 403, { error: "complaint does not belong to customer" });
          return;
        }
        const closed = closeComplaint(complaint, {
          actorId: customerId,
          note: payload.note
        });
        replaceComplaint(state, closed);
        state.auditLog.append({
          eventType: "COMMAND_EXECUTED",
          actorType: "CUSTOMER",
          actorId: customerId,
          actorRole: "CUSTOMER",
          screenId: "CMP-101",
          businessReferenceId: caseId,
          customerId,
          reason: "Customer confirmed complaint answer",
          payload: {
            status: closed.status
          }
        });
        json(response, 200, { item: publicComplaint(closed) });
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

      if (pathname === "/api/ledger/adjustments" && request.method === "POST") {
        const payload = await bodyJson(request);
        const result = await state.ledgerCore.adjustment(payload);
        state.auditLog.append({
          eventType: "COMMAND_EXECUTED",
          actorType: "STAFF",
          actorId: payload.requestedBy || "ops01",
          actorRole: "OPS_OPERATOR",
          businessReferenceId: result.value.id,
          accountId: payload.accountId,
          reason: payload.reason || "Synthetic ledger adjustment",
          payload: {
            idempotencyKey: payload.idempotencyKey,
            amountMinor: payload.amountMinor,
            direction: payload.direction
          }
        });
        json(response, result.replayed ? 200 : 201, result);
        return;
      }

      json(response, 404, { error: "not found" });
    } catch (error) {
      const apiError = inferApiError(error, {
        requestId: response.__requestId,
        route: response.__routePath
      });
      json(response, apiError.statusCode, { error: apiError });
    }
  };
}

export async function createLabHttpServer(state) {
  const handler = await createLabHandler(state);
  return createServer(handler);
}
