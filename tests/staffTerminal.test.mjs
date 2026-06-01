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

test("staff customer detail requires reason and returns masked PII", async () => {
  await withServer(async ({ baseUrl, state }) => {
    const denied = await fetch(`${baseUrl}/api/staff/customers/SYN-CUS-001/detail`);
    const allowed = await fetch(`${baseUrl}/api/staff/customers/SYN-CUS-001/detail?reason=Branch%20service%20request`);
    const payload = await allowed.json();

    assert.equal(denied.status, 400);
    assert.equal(allowed.status, 200);
    assert.equal(payload.item.customerId, "SYN-CUS-001");
    assert.equal(payload.item.piiExposure, "MASKED");
    assert.equal(payload.item.name, undefined);
    assert.match(payload.item.maskedPhone, /\*/);
    assert.equal(state.auditLog.all().some((event) => event.eventType === "CUSTOMER_DETAIL_VIEW"), true);
  });
});

test("staff unmask requires reason and privileged role", async () => {
  await withServer(async ({ baseUrl, state }) => {
    const deniedRole = await fetch(`${baseUrl}/api/staff/pii/unmask`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        customerId: "SYN-CUS-001",
        requestedBy: "branch01",
        actorRole: "BRANCH_STAFF",
        reason: "Branch service request"
      })
    });
    const allowed = await fetch(`${baseUrl}/api/staff/pii/unmask`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        customerId: "SYN-CUS-001",
        requestedBy: "manager01",
        actorRole: "BRANCH_MANAGER",
        reason: "Manager verified customer request"
      })
    });
    const payload = await allowed.json();

    assert.equal(deniedRole.status, 403);
    assert.equal(allowed.status, 200);
    assert.equal(payload.item.piiExposure, "UNMASKED_TIMEBOXED");
    assert.equal(payload.item.phone, "010-0000-1001");
    assert.equal(state.auditLog.all().some((event) => event.eventType === "PII_UNMASK_REQUESTED"), true);
  });
});

test("staff account and transaction inquiry require reasons and create audit events", async () => {
  await withServer(async ({ baseUrl, state }) => {
    const accountDenied = await fetch(`${baseUrl}/api/staff/accounts/search?customerId=SYN-CUS-001`);
    const accountAllowed = await fetch(`${baseUrl}/api/staff/accounts/search?customerId=SYN-CUS-001&reason=Customer%20asked%20balance`);
    const accountPayload = await accountAllowed.json();
    const txDenied = await fetch(`${baseUrl}/api/staff/transactions/search?accountId=ACC-SYN-001-001`);
    const txAllowed = await fetch(`${baseUrl}/api/staff/transactions/search?accountId=ACC-SYN-001-001&reason=Customer%20asked%20history`);
    const txPayload = await txAllowed.json();

    assert.equal(accountDenied.status, 400);
    assert.equal(accountAllowed.status, 200);
    assert.equal(accountPayload.items[0].maskedAccountNo, "LAB-***-0001");
    assert.equal(txDenied.status, 400);
    assert.equal(txAllowed.status, 200);
    assert.equal(txPayload.items.length >= 1, true);
    assert.equal(state.auditLog.all().some((event) => event.eventType === "ACCOUNT_VIEW"), true);
    assert.equal(state.auditLog.all().some((event) => event.eventType === "TRANSACTION_VIEW"), true);
  });
});

test("customer information change applies only after maker-checker approval", async () => {
  await withServer(async ({ baseUrl, state }) => {
    const before = state.dataset.customers.find((customer) => customer.customerId === "SYN-CUS-001").phone;
    const request = await fetch(`${baseUrl}/api/staff/customers/SYN-CUS-001/change-requests`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        requestedBy: "branch01",
        reason: "Customer requested phone update",
        afterSnapshot: {
          phone: "010-0000-1999",
          address: "Seoul Synthetic Updated"
        }
      })
    });
    const requestPayload = await request.json();
    const stillBefore = state.dataset.customers.find((customer) => customer.customerId === "SYN-CUS-001").phone;
    const selfApprove = await fetch(`${baseUrl}/api/staff/approvals/${requestPayload.item.approvalId}/approve`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        approvedBy: "branch01",
        approvedByRole: "BRANCH_STAFF"
      })
    });
    const managerApprove = await fetch(`${baseUrl}/api/staff/approvals/${requestPayload.item.approvalId}/approve`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        approvedBy: "manager01",
        approvedByRole: "BRANCH_MANAGER"
      })
    });
    const approvePayload = await managerApprove.json();
    const after = state.dataset.customers.find((customer) => customer.customerId === "SYN-CUS-001").phone;

    assert.equal(request.status, 201);
    assert.equal(stillBefore, before);
    assert.equal(selfApprove.status, 500);
    assert.equal(managerApprove.status, 200);
    assert.equal(approvePayload.executed, true);
    assert.equal(after, "010-0000-1999");
    assert.equal(state.auditLog.all().some((event) => event.eventType === "COMMAND_REQUESTED"), true);
    assert.equal(state.auditLog.all().some((event) => event.eventType === "COMMAND_APPROVED"), true);
    assert.equal(state.auditLog.all().some((event) => event.eventType === "COMMAND_EXECUTED" && event.screenId === "CST-103"), true);
  });
});
