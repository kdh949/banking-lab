import assert from "node:assert/strict";
import test from "node:test";
import { createLabHttpServer, createLabState } from "../runtime/labApp.mjs";

async function withServer(fn) {
  const state = await createLabState();
  const server = await createLabHttpServer(state);
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const address = server.address();
  const baseUrl = `http://127.0.0.1:${address.port}`;
  try {
    return await fn({ baseUrl, state });
  } finally {
    await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
  }
}

async function postJson(url, body) {
  const response = await fetch(url, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body)
  });
  return {
    response,
    payload: await response.json()
  };
}

test("customer and staff share the same complaint case with SLA and timeline", async () => {
  await withServer(async ({ baseUrl }) => {
    const created = await postJson(`${baseUrl}/api/complaints`, {
      customerId: "SYN-CUS-001",
      category: "TRANSFER_DISPUTE",
      description: "Synthetic complaint workflow test"
    });
    const customerList = await fetch(`${baseUrl}/api/complaints?customerId=SYN-CUS-001`);
    const staffList = await fetch(`${baseUrl}/api/staff/complaints`);
    const customerPayload = await customerList.json();
    const staffPayload = await staffList.json();

    assert.equal(created.response.status, 201);
    assert.equal(created.payload.item.status, "RECEIVED");
    assert.ok(created.payload.item.slaDueAt);
    assert.equal(created.payload.item.timeline.length >= 1, true);
    assert.equal(customerPayload.items.some((item) => item.caseId === created.payload.item.caseId), true);
    assert.equal(staffPayload.items.some((item) => item.caseId === created.payload.item.caseId), true);
  });
});

test("complaint answer is sent only after maker-checker approval", async () => {
  await withServer(async ({ baseUrl, state }) => {
    const created = await postJson(`${baseUrl}/api/complaints`, {
      customerId: "SYN-CUS-001",
      category: "ACCOUNT_ACCESS",
      description: "Cannot see account history"
    });
    const caseId = created.payload.item.caseId;
    const classified = await postJson(`${baseUrl}/api/staff/complaints/${caseId}/classify`, {
      actorId: "complaint01",
      classification: "ACCOUNT_ACCESS",
      note: "Classified by complaint handler"
    });
    const assigned = await postJson(`${baseUrl}/api/staff/complaints/${caseId}/assign`, {
      actorId: "complaint01",
      owner: "complaint01"
    });
    const review = await postJson(`${baseUrl}/api/staff/complaints/${caseId}/start-review`, {
      actorId: "complaint01",
      note: "Review started"
    });
    const draft = await postJson(`${baseUrl}/api/staff/complaints/${caseId}/answer-drafts`, {
      actorId: "complaint01",
      requestedByRole: "COMPLAINT_HANDLER",
      reason: "Prepare customer complaint answer",
      body: "Synthetic answer explains the account history path."
    });
    const beforeApproval = await (await fetch(`${baseUrl}/api/complaints/${caseId}`)).json();
    const selfApproval = await postJson(`${baseUrl}/api/staff/approvals/${draft.payload.approval.approvalId}/approve`, {
      approvedBy: "complaint01",
      approvedByRole: "COMPLAINT_HANDLER"
    });
    const managerApproval = await postJson(`${baseUrl}/api/staff/approvals/${draft.payload.approval.approvalId}/approve`, {
      approvedBy: "manager01",
      approvedByRole: "BRANCH_MANAGER"
    });

    assert.equal(classified.payload.item.status, "CLASSIFIED");
    assert.equal(assigned.payload.item.status, "ASSIGNED");
    assert.equal(review.payload.item.status, "IN_REVIEW");
    assert.equal(draft.payload.item.status, "WAITING_APPROVAL");
    assert.equal(beforeApproval.item.answer, null);
    assert.equal("answerDraft" in beforeApproval.item, false);
    assert.equal("approvalId" in beforeApproval.item, false);
    assert.equal(beforeApproval.item.timeline.some((entry) => "actorId" in entry), false);
    assert.equal(selfApproval.response.status, 500);
    assert.equal(managerApproval.response.status, 200);
    assert.equal(managerApproval.payload.executed, true);
    assert.equal(managerApproval.payload.complaint.status, "ANSWERED");
    assert.equal(managerApproval.payload.complaint.answer.body, "Synthetic answer explains the account history path.");
    assert.equal(state.auditLog.all().some((event) => event.eventType === "COMMAND_REQUESTED" && event.payload.businessType === "COMPLAINT_ANSWER_SEND"), true);
    assert.equal(state.auditLog.all().some((event) => event.eventType === "COMMAND_APPROVED"), true);
    assert.equal(state.auditLog.all().some((event) => event.eventType === "COMMAND_EXECUTED" && event.screenId === "CMP-201"), true);
  });
});

test("customer can confirm an answered complaint and close the case", async () => {
  await withServer(async ({ baseUrl }) => {
    const created = await postJson(`${baseUrl}/api/complaints`, {
      customerId: "SYN-CUS-001",
      category: "FEE_INQUIRY",
      description: "Fee explanation requested"
    });
    const caseId = created.payload.item.caseId;
    await postJson(`${baseUrl}/api/staff/complaints/${caseId}/classify`, {
      actorId: "complaint01",
      classification: "FEE_INQUIRY"
    });
    await postJson(`${baseUrl}/api/staff/complaints/${caseId}/assign`, {
      actorId: "complaint01",
      owner: "complaint01"
    });
    await postJson(`${baseUrl}/api/staff/complaints/${caseId}/start-review`, {
      actorId: "complaint01"
    });
    const draft = await postJson(`${baseUrl}/api/staff/complaints/${caseId}/answer-drafts`, {
      actorId: "complaint01",
      reason: "Prepare fee inquiry answer",
      body: "Synthetic fee answer"
    });
    await postJson(`${baseUrl}/api/staff/approvals/${draft.payload.approval.approvalId}/approve`, {
      approvedBy: "manager01",
      approvedByRole: "BRANCH_MANAGER"
    });
    const confirmed = await postJson(`${baseUrl}/api/customer/complaints/${caseId}/confirm`, {
      customerId: "SYN-CUS-001",
      note: "Customer accepted answer"
    });

    assert.equal(confirmed.response.status, 200);
    assert.equal(confirmed.payload.item.status, "CLOSED");
    assert.ok(confirmed.payload.item.customerConfirmedAt);
    assert.equal(confirmed.payload.item.timeline.some((entry) => entry.type === "CLOSED"), true);
  });
});

test("complaint workflow manifests cover staff and customer views", async () => {
  await withServer(async ({ baseUrl }) => {
    const staffScreens = await (await fetch(`${baseUrl}/api/screens?app=staff-terminal`)).json();
    const portalScreens = await (await fetch(`${baseUrl}/api/screens?app=complaint-portal`)).json();

    assert.equal(staffScreens.items.some((screen) => screen.screenId === "CMP-201"), true);
    assert.equal(portalScreens.items.some((screen) => screen.screenId === "CMP-101"), true);
    assert.equal(portalScreens.items.some((screen) => screen.screenId === "CMP-102"), true);
  });
});
