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

test("customer login and account detail use customer self-service audit", async () => {
  await withServer(async ({ baseUrl, state }) => {
    const login = await postJson(`${baseUrl}/api/customer/login`, { userId: "customer01" });
    const detail = await fetch(`${baseUrl}/api/customer/accounts/ACC-SYN-001-001/detail?customerId=SYN-CUS-001`);
    const detailPayload = await detail.json();

    assert.equal(login.response.status, 200);
    assert.equal(login.payload.session.actorType, "CUSTOMER");
    assert.equal(detail.status, 200);
    assert.equal(detailPayload.item.maskedAccountNo, "LAB-***-0001");
    assert.equal(state.auditLog.all().some((event) => event.eventType === "LOGIN_SUCCESS" && event.actorType === "CUSTOMER"), true);
    assert.equal(state.auditLog.all().some((event) => event.eventType === "ACCOUNT_VIEW" && event.actorType === "CUSTOMER"), true);
  });
});

test("customer transaction history and staff transaction history share ledger source", async () => {
  await withServer(async ({ baseUrl }) => {
    const transfer = await postJson(`${baseUrl}/api/customer/transfers`, {
      fromAccountId: "ACC-SYN-001-001",
      toAccountId: "ACC-SYN-002-001",
      amountMinor: 12345,
      idempotencyKey: "CUSTOMER-WEB-TRF-001",
      requestedBy: "SYN-CUS-001"
    });
    const customerHistory = await fetch(`${baseUrl}/api/customer/transactions?customerId=SYN-CUS-001&accountId=ACC-SYN-001-001`);
    const staffHistory = await fetch(`${baseUrl}/api/staff/transactions/search?accountId=ACC-SYN-001-001&reason=Compare%20customer%20source`);
    const customerPayload = await customerHistory.json();
    const staffPayload = await staffHistory.json();

    assert.equal(transfer.response.status, 201);
    assert.equal(transfer.payload.item.status, "POSTED");
    assert.match(transfer.payload.item.transactionId, /^TX-TRF-/);
    assert.equal(customerHistory.status, 200);
    assert.equal(staffHistory.status, 200);
    assert.equal(customerPayload.items.some((item) => item.transactionId === transfer.payload.item.transactionId), true);
    assert.equal(staffPayload.items.some((item) => item.transactionId === transfer.payload.item.transactionId), true);
  });
});

test("customer transfer retry returns the same posted result without duplicate transaction", async () => {
  await withServer(async ({ baseUrl, state }) => {
    const beforeCount = state.ledgerCore.transactions.length;
    const body = {
      fromAccountId: "ACC-SYN-001-001",
      toAccountId: "ACC-SYN-002-001",
      amountMinor: 1000,
      idempotencyKey: "CUSTOMER-WEB-IDEMP-001",
      requestedBy: "SYN-CUS-001"
    };
    const first = await postJson(`${baseUrl}/api/customer/transfers`, body);
    const second = await postJson(`${baseUrl}/api/customer/transfers`, body);

    assert.equal(first.response.status, 201);
    assert.equal(second.response.status, 200);
    assert.equal(second.payload.replayed, true);
    assert.equal(second.payload.item.transactionId, first.payload.item.transactionId);
    assert.equal(state.ledgerCore.transactions.length, beforeCount + 1);
  });
});

test("customer web represents held and failed transfer statuses without unsafe postings", async () => {
  await withServer(async ({ baseUrl, state }) => {
    const beforeCount = state.ledgerCore.transactions.length;
    const held = await postJson(`${baseUrl}/api/customer/transfers`, {
      fromAccountId: "ACC-SYN-001-001",
      toAccountId: "ACC-SYN-002-001",
      amountMinor: 5000000,
      idempotencyKey: "CUSTOMER-WEB-HELD-001",
      requestedBy: "SYN-CUS-001"
    });
    const failed = await postJson(`${baseUrl}/api/customer/transfers`, {
      fromAccountId: "ACC-SYN-001-001",
      toAccountId: "ACC-SYN-002-001",
      amountMinor: -1,
      idempotencyKey: "CUSTOMER-WEB-FAILED-001",
      requestedBy: "SYN-CUS-001"
    });
    const results = await fetch(`${baseUrl}/api/customer/transfers?customerId=SYN-CUS-001`);
    const resultPayload = await results.json();

    assert.equal(held.response.status, 202);
    assert.equal(held.payload.item.status, "HELD");
    assert.equal(failed.response.status, 200);
    assert.equal(failed.payload.item.status, "FAILED");
    assert.equal(state.ledgerCore.transactions.length, beforeCount);
    assert.equal(resultPayload.items.some((item) => item.status === "HELD"), true);
    assert.equal(resultPayload.items.some((item) => item.status === "FAILED"), true);
  });
});

test("customer web exposes complaint entry manifest and portal shell", async () => {
  await withServer(async ({ baseUrl }) => {
    const screens = await fetch(`${baseUrl}/api/screens?app=customer-web`);
    const screenPayload = await screens.json();
    const complaintPortal = await fetch(`${baseUrl}/complaint-portal`);
    const html = await complaintPortal.text();

    assert.equal(screenPayload.items.some((screen) => screen.screenId === "CWB-301"), true);
    assert.equal(complaintPortal.status, 200);
    assert.match(html, /complaint-portal/);
  });
});
