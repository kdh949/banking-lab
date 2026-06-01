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

test("runtime health exposes valid audit hash chain", async () => {
  await withServer(async ({ baseUrl }) => {
    const response = await fetch(`${baseUrl}/health`);
    const payload = await response.json();

    assert.equal(response.status, 200);
    assert.equal(payload.status, "ok");
    assert.equal(payload.syntheticOnly, true);
    assert.equal(payload.auditHashChainValid, true);
  });
});

test("staff customer search enforces reason and records audit event", async () => {
  await withServer(async ({ baseUrl, state }) => {
    const denied = await fetch(`${baseUrl}/api/staff/customers/search`);
    assert.equal(denied.status, 400);

    const allowed = await fetch(`${baseUrl}/api/staff/customers/search?query=alpha&reason=Customer%20requested%20support`);
    const payload = await allowed.json();

    assert.equal(allowed.status, 200);
    assert.equal(payload.items.length, 3);
    assert.match(payload.auditEventId, /^AUD-/);
    assert.equal(state.auditLog.all().some((event) => event.eventType === "CUSTOMER_SEARCH"), true);
  });
});

test("customer transfer endpoint is idempotent", async () => {
  await withServer(async ({ baseUrl, state }) => {
    const beforeCount = state.ledgerTransactions.length;
    const body = JSON.stringify({
      fromAccountId: "ACC-SYN-001-001",
      toAccountId: "ACC-SYN-002-001",
      amountMinor: 1200,
      idempotencyKey: "RUNTIME-IDEMP-001",
      requestedBy: "SYN-CUS-001"
    });
    const first = await fetch(`${baseUrl}/api/customer/transfers`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body
    });
    const second = await fetch(`${baseUrl}/api/customer/transfers`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body
    });
    const secondPayload = await second.json();

    assert.equal(first.status, 201);
    assert.equal(second.status, 200);
    assert.equal(secondPayload.replayed, true);
    assert.equal(state.ledgerTransactions.length, beforeCount + 1);
  });
});

test("ledger withdrawal and reversal endpoints preserve invariants", async () => {
  await withServer(async ({ baseUrl, state }) => {
    const withdrawal = await fetch(`${baseUrl}/api/ledger/withdrawals`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        accountId: "ACC-SYN-001-001",
        amountMinor: 3000,
        idempotencyKey: "RUNTIME-WDR-001",
        requestedBy: "branch01",
        requestedChannel: "STAFF_TERMINAL",
        reason: "Runtime ledger withdrawal test"
      })
    });
    const withdrawalPayload = await withdrawal.json();

    const reversal = await fetch(`${baseUrl}/api/ledger/reversals`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        originalTransactionId: withdrawalPayload.value.id,
        idempotencyKey: "RUNTIME-REV-001",
        requestedBy: "branch01",
        requestedChannel: "STAFF_TERMINAL",
        reason: "Runtime ledger reversal test"
      })
    });
    const reversalPayload = await reversal.json();

    assert.equal(withdrawal.status, 201);
    assert.equal(reversal.status, 201);
    assert.equal(reversalPayload.value.originalTransactionId, withdrawalPayload.value.id);
    assert.equal(state.ledgerCore.validateInvariants(), true);
  });
});
