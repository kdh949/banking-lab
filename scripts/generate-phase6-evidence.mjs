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

function today() {
  return new Date().toISOString().slice(0, 10);
}

function tomorrowOf(businessDate) {
  const date = new Date(`${businessDate}T00:00:00.000Z`);
  date.setUTCDate(date.getUTCDate() + 1);
  return date.toISOString().slice(0, 10);
}

const evidence = await withHandler(async ({ invoke, state }) => {
  const fdsBeforeCount = state.ledgerCore.transactions.length;
  const held = await postJson(invoke, "/api/customer/transfers", {
    fromAccountId: "ACC-SYN-001-001",
    toAccountId: "ACC-SYN-002-001",
    amountMinor: 5000000,
    idempotencyKey: "EVID-FDS-RELEASE-001",
    requestedBy: "SYN-CUS-001",
    newDevice: true
  });
  const releaseCaseId = held.payload.item.caseId;
  await postJson(invoke, `/api/staff/fds-cases/${releaseCaseId}/assign`, {
    actorId: "fds01",
    owner: "fds01"
  });
  const releaseRequest = await postJson(invoke, `/api/staff/fds-cases/${releaseCaseId}/release-requests`, {
    actorId: "fds01",
    reason: "Evidence FDS release"
  });
  const fdsBeforeApprovalCount = state.ledgerCore.transactions.length;
  const releaseApproval = await postJson(invoke, `/api/staff/approvals/${releaseRequest.payload.approval.approvalId}/approve`, {
    approvedBy: "manager01",
    approvedByRole: "BRANCH_MANAGER"
  });
  const releaseTransferResults = await invoke({ url: "/api/customer/transfers?customerId=SYN-CUS-001" });

  const blockBeforeCount = state.ledgerCore.transactions.length;
  const blockHeld = await postJson(invoke, "/api/customer/transfers", {
    fromAccountId: "ACC-SYN-001-001",
    toAccountId: "ACC-SYN-002-001",
    amountMinor: 6000000,
    idempotencyKey: "EVID-FDS-BLOCK-001",
    requestedBy: "SYN-CUS-001",
    firstTimeBeneficiary: true
  });
  const blockCaseId = blockHeld.payload.item.caseId;
  await postJson(invoke, `/api/staff/fds-cases/${blockCaseId}/assign`, {
    actorId: "fds01",
    owner: "fds01"
  });
  const blockRequest = await postJson(invoke, `/api/staff/fds-cases/${blockCaseId}/block-requests`, {
    actorId: "fds01",
    reason: "Evidence FDS block"
  });
  const blockApproval = await postJson(invoke, `/api/staff/approvals/${blockRequest.payload.approval.approvalId}/approve`, {
    approvedBy: "manager01",
    approvedByRole: "BRANCH_MANAGER"
  });
  const blockAfterApprovalCount = state.ledgerCore.transactions.length;

  const amlHeld = await postJson(invoke, "/api/customer/transfers", {
    fromAccountId: "ACC-SYN-003-001",
    toAccountId: "ACC-SYN-002-001",
    amountMinor: 5000000,
    idempotencyKey: "EVID-AML-001",
    requestedBy: "SYN-CUS-003"
  });
  const amlList = await invoke({ url: "/api/staff/aml-cases" });
  const amlCase = amlList.payload.items.find((item) => item.relatedFdsCaseId === amlHeld.payload.item.caseId);
  await postJson(invoke, `/api/staff/aml-cases/${amlCase.caseId}/assign`, {
    actorId: "fds01",
    owner: "fds01"
  });
  await postJson(invoke, `/api/staff/aml-cases/${amlCase.caseId}/comments`, {
    actorId: "fds01",
    body: "Evidence AML review comment"
  });
  const amlClosure = await postJson(invoke, `/api/staff/aml-cases/${amlCase.caseId}/closure-requests`, {
    actorId: "fds01",
    reason: "Evidence AML closure",
    disposition: "STR_SIMULATED",
    reportReferenceId: "STR-SIM-EVID-001"
  });
  const amlApproval = await postJson(invoke, `/api/staff/approvals/${amlClosure.payload.approval.approvalId}/approve`, {
    approvedBy: "manager01",
    approvedByRole: "BRANCH_MANAGER"
  });

  const businessDate = today();
  const nextDate = tomorrowOf(businessDate);
  await postJson(invoke, "/api/ledger/transfers", {
    fromAccountId: "ACC-SYN-001-001",
    toAccountId: "ACC-SYN-002-001",
    amountMinor: 4321,
    idempotencyKey: "EVID-RECON-TRF-001",
    businessDate,
    requestedBy: "ops01",
    requestedChannel: "OPS_CONSOLE",
    reason: "Evidence reconciliation seed transfer"
  });
  const closing = await postJson(invoke, "/api/ops/daily-closings", {
    businessDate,
    requestedBy: "ops01",
    externalMode: "MISMATCH"
  });
  const closedDayMutation = await postJson(invoke, "/api/ledger/transfers", {
    fromAccountId: "ACC-SYN-001-001",
    toAccountId: "ACC-SYN-002-001",
    amountMinor: 100,
    idempotencyKey: "EVID-CLOSED-DAY-001",
    businessDate,
    requestedBy: "ops01",
    requestedChannel: "OPS_CONSOLE",
    reason: "Evidence closed day mutation rejection"
  });
  const reconciliationItem = closing.payload.reconciliationItems[0];
  const adjustmentRequest = await postJson(invoke, `/api/ops/reconciliation-items/${reconciliationItem.itemId}/adjustment-requests`, {
    requestedBy: "ops01",
    reason: "Evidence reconciliation adjustment",
    accountId: "ACC-SYN-001-001",
    direction: "CREDIT",
    amountMinor: 1000,
    businessDate: nextDate,
    idempotencyKey: "EVID-RECON-ADJ-001"
  });
  const adjustmentApproval = await postJson(invoke, `/api/staff/approvals/${adjustmentRequest.payload.approval.approvalId}/approve`, {
    approvedBy: "manager01",
    approvedByRole: "BRANCH_MANAGER"
  });
  const fdsScreens = await invoke({ url: "/api/screens?app=fds-aml-console" });
  const opsScreens = await invoke({ url: "/api/screens?app=ops-console" });
  const auditTypes = new Set(state.auditLog.all().map((event) => `${event.eventType}:${event.screenId || ""}`));

  return {
    generatedAt: new Date().toISOString(),
    scope: "Phase 6 FDS AML Reconciliation",
    syntheticOnly: true,
    checks: [
      {
        id: "fds-hold-no-posting-before-approval",
        status: held.status === 202
          && held.payload.item.status === "HELD"
          && fdsBeforeApprovalCount === fdsBeforeCount
          ? "pass"
          : "fail",
        detail: "High-risk transfer created an FDS hold without posting to the ledger"
      },
      {
        id: "fds-release-maker-checker",
        status: releaseApproval.payload.executed === true
          && releaseApproval.payload.fdsCase.status === "RELEASED"
          && releaseApproval.payload.ledgerTransaction.transactionType === "INTERNAL_TRANSFER"
          && releaseTransferResults.payload.items.find((item) => item.caseId === releaseCaseId)?.status === "POSTED"
          ? "pass"
          : "fail",
        detail: "FDS release posted the transfer only after checker approval"
      },
      {
        id: "fds-block-no-posting",
        status: blockApproval.payload.fdsCase.status === "BLOCKED"
          && blockAfterApprovalCount === blockBeforeCount
          ? "pass"
          : "fail",
        detail: "FDS block updated case and transfer result without adding a ledger posting"
      },
      {
        id: "aml-case-lifecycle",
        status: amlApproval.payload.amlCase.status === "CLOSED"
          && amlApproval.payload.amlCase.strSimulation.reported === true
          ? "pass"
          : "fail",
        detail: "High-risk customer transfer generated an AML case and closed through approval"
      },
      {
        id: "eod-reconciliation-unmatched-item",
        status: closing.payload.item.status === "UNMATCHED"
          && closing.payload.item.ledgerInvariantValid === true
          && reconciliationItem.owner === "ops01"
          ? "pass"
          : "fail",
        detail: "Daily closing validated ledger totals and created an owned unmatched item"
      },
      {
        id: "closed-day-and-adjustment-control",
        status: closedDayMutation.status === 409
          && closedDayMutation.payload.error?.code === "LEDGER_CLOSED_DAY_IMMUTABLE"
          && /closed day/.test(closedDayMutation.payload.error?.invariant || "")
          && adjustmentApproval.payload.reconciliationItem.status === "ADJUSTED"
          && adjustmentApproval.payload.ledgerTransaction.transactionType === "ADJUSTMENT"
          && adjustmentApproval.payload.ledgerTransaction.businessDate === nextDate
          ? "pass"
          : "fail",
        detail: "Closed business day rejected direct mutation and adjustment posted on the next open day"
      },
      {
        id: "manifest-coverage",
        status: fdsScreens.payload.items.some((screen) => screen.screenId === "FDS-201")
          && fdsScreens.payload.items.some((screen) => screen.screenId === "AML-201")
          && opsScreens.payload.items.some((screen) => screen.screenId === "OPS-201")
          ? "pass"
          : "fail",
        detail: "FDS, AML, and reconciliation case screens are manifest-declared"
      },
      {
        id: "audit-coverage",
        status: ["COMMAND_REQUESTED:FDS-201", "COMMAND_EXECUTED:FDS-201", "COMMAND_REQUESTED:AML-201", "COMMAND_EXECUTED:AML-201", "BATCH_STARTED:OPS-101", "COMMAND_REQUESTED:OPS-201", "COMMAND_EXECUTED:OPS-201"].every((type) => auditTypes.has(type))
          ? "pass"
          : "fail",
        detail: "Risk and reconciliation workflows produced approval, execution, and batch audit events"
      }
    ]
  };
});

const outputDir = "docs/test-evidence/generated";
const outputFile = `${outputDir}/phase-6-fds-aml-reconciliation.json`;
await mkdir(outputDir, { recursive: true });
await writeFile(outputFile, `${JSON.stringify(evidence, null, 2)}\n`);

console.log(`Wrote ${outputFile}`);
