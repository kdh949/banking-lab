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
  const created = await postJson(invoke, "/api/complaints", {
    customerId: "SYN-CUS-001",
    category: "TRANSFER_DISPUTE",
    description: "Evidence complaint workflow"
  });
  const caseId = created.payload.item.caseId;
  const customerList = await invoke({ url: "/api/complaints?customerId=SYN-CUS-001" });
  const staffList = await invoke({ url: "/api/staff/complaints" });
  const classified = await postJson(invoke, `/api/staff/complaints/${caseId}/classify`, {
    actorId: "complaint01",
    classification: "TRANSFER_DISPUTE"
  });
  const assigned = await postJson(invoke, `/api/staff/complaints/${caseId}/assign`, {
    actorId: "complaint01",
    owner: "complaint01"
  });
  const review = await postJson(invoke, `/api/staff/complaints/${caseId}/start-review`, {
    actorId: "complaint01"
  });
  const draft = await postJson(invoke, `/api/staff/complaints/${caseId}/answer-drafts`, {
    actorId: "complaint01",
    requestedByRole: "COMPLAINT_HANDLER",
    reason: "Evidence answer approval",
    body: "Evidence approved complaint answer"
  });
  const beforeApproval = await invoke({ url: `/api/complaints/${caseId}` });
  const approved = await postJson(invoke, `/api/staff/approvals/${draft.payload.approval.approvalId}/approve`, {
    approvedBy: "manager01",
    approvedByRole: "BRANCH_MANAGER"
  });
  const confirmed = await postJson(invoke, `/api/customer/complaints/${caseId}/confirm`, {
    customerId: "SYN-CUS-001",
    note: "Evidence customer confirmation"
  });
  const staffScreens = await invoke({ url: "/api/screens?app=staff-terminal" });
  const portalScreens = await invoke({ url: "/api/screens?app=complaint-portal" });
  const auditTypes = new Set(state.auditLog.all().map((event) => `${event.actorType}:${event.eventType}:${event.screenId || ""}`));

  return {
    generatedAt: new Date().toISOString(),
    scope: "Phase 5 Complaint Workflow",
    syntheticOnly: true,
    checks: [
      {
        id: "shared-case-source",
        status: created.status === 201
          && customerList.payload.items.some((item) => item.caseId === caseId)
          && staffList.payload.items.some((item) => item.caseId === caseId)
          ? "pass"
          : "fail",
        detail: "Customer portal and staff terminal read the same complaint case"
      },
      {
        id: "workflow-transitions",
        status: classified.payload.item.status === "CLASSIFIED"
          && assigned.payload.item.status === "ASSIGNED"
          && review.payload.item.status === "IN_REVIEW"
          && draft.payload.item.status === "WAITING_APPROVAL"
          ? "pass"
          : "fail",
        detail: "Complaint moved through classify, assign, review, and waiting approval"
      },
      {
        id: "approval-before-answer",
        status: beforeApproval.payload.item.answer === null
          && !("answerDraft" in beforeApproval.payload.item)
          && !("approvalId" in beforeApproval.payload.item)
          && beforeApproval.payload.item.timeline.every((entry) => !("actorId" in entry))
          && approved.payload.executed === true
          && approved.payload.complaint.status === "ANSWERED"
          ? "pass"
          : "fail",
        detail: "Answer draft and internal approval fields were not visible until maker-checker approval executed"
      },
      {
        id: "customer-confirmation",
        status: confirmed.status === 200 && confirmed.payload.item.status === "CLOSED" && Boolean(confirmed.payload.item.customerConfirmedAt) ? "pass" : "fail",
        detail: "Customer confirmed answer and closed the case"
      },
      {
        id: "sla-and-timeline",
        status: Boolean(created.payload.item.slaDueAt) && confirmed.payload.item.timeline.length >= 7 ? "pass" : "fail",
        detail: "Case includes SLA due date and workflow timeline"
      },
      {
        id: "manifest-coverage",
        status: staffScreens.payload.items.some((screen) => screen.screenId === "CMP-201")
          && portalScreens.payload.items.some((screen) => screen.screenId === "CMP-101")
          && portalScreens.payload.items.some((screen) => screen.screenId === "CMP-102")
          ? "pass"
          : "fail",
        detail: "Complaint workflow manifests cover intake, status, and staff detail"
      },
      {
        id: "audit-coverage",
        status: ["CUSTOMER:COMMAND_REQUESTED:CMP-101", "STAFF:COMMAND_REQUESTED:CMP-201", "STAFF:COMMAND_APPROVED:", "STAFF:COMMAND_EXECUTED:CMP-201", "CUSTOMER:COMMAND_EXECUTED:CMP-101"].every((type) => auditTypes.has(type)) ? "pass" : "fail",
        detail: "Complaint workflow produced customer, staff, approval, and execution audit events"
      }
    ]
  };
});

const outputDir = "docs/test-evidence/generated";
const outputFile = `${outputDir}/phase-5-complaint-workflow.json`;
await mkdir(outputDir, { recursive: true });
await writeFile(outputFile, `${JSON.stringify(evidence, null, 2)}\n`);

console.log(`Wrote ${outputFile}`);
