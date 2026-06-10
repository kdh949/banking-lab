import assert from "node:assert/strict";
import test from "node:test";
import { API_ERROR_CONTRACT_VERSION, inferApiError } from "../runtime/synthetic-reference/packages/banking-domain/src/index.mjs";
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

function assertErrorShape(error) {
  assert.equal(error.contractVersion, API_ERROR_CONTRACT_VERSION);
  assert.equal(typeof error.code, "string");
  assert.equal(typeof error.message, "string");
  assert.equal(typeof error.statusCode, "number");
  assert.equal(typeof error.domain, "string");
  assert.ok(Object.hasOwn(error, "invariant"));
  assert.ok(Object.hasOwn(error, "policy"));
  assert.equal(typeof error.cause, "string");
  assert.equal(typeof error.fix, "string");
  assert.equal(typeof error.requestId, "string");
  assert.equal(typeof error.correlationId, "string");
  assert.equal(error.docs, "docs/migration/structured-api-error-contract.md");
  assert.equal(error.syntheticOnly, true);
}

test("structured error inference identifies ledger invariant failures", () => {
  const error = inferApiError(new Error("business day is closed: 2026-01-31"), {
    requestId: "REQ-TEST-CLOSED-DAY",
    route: "/api/ledger/deposits"
  });

  assertErrorShape(error);
  assert.equal(error.code, "LEDGER_CLOSED_DAY_IMMUTABLE");
  assert.equal(error.statusCode, 409);
  assert.equal(error.domain, "ledger");
  assert.match(error.invariant, /closed day/);
  assert.equal(error.requestId, "REQ-TEST-CLOSED-DAY");
});

test("runtime policy failures return structured API errors with request id", async () => {
  await withServer(async ({ baseUrl }) => {
    const response = await fetch(`${baseUrl}/api/staff/customers/search`, {
      headers: { "x-request-id": "REQ-TEST-REASON" }
    });
    const payload = await response.json();

    assert.equal(response.status, 400);
    assert.equal(response.headers.get("x-request-id"), "REQ-TEST-REASON");
    assertErrorShape(payload.error);
    assert.equal(payload.error.code, "POLICY_REASON_REQUIRED");
    assert.equal(payload.error.policy, "REASON_REQUIRED");
    assert.equal(payload.error.route, "/api/staff/customers/search");
    assert.equal(payload.error.requestId, "REQ-TEST-REASON");
  });
});

test("runtime ledger failures return invariant-specific status and error code", async () => {
  await withServer(async ({ baseUrl }) => {
    const response = await fetch(`${baseUrl}/api/ledger/withdrawals`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-request-id": "REQ-TEST-LEDGER"
      },
      body: JSON.stringify({
        accountId: "ACC-SYN-001-001",
        amountMinor: 999999999999,
        idempotencyKey: "ERR-CONTRACT-WDR-001",
        requestedBy: "branch01",
        requestedChannel: "STAFF_TERMINAL",
        reason: "Structured error contract test"
      })
    });
    const payload = await response.json();

    assert.equal(response.status, 409);
    assertErrorShape(payload.error);
    assert.equal(payload.error.code, "LEDGER_INSUFFICIENT_AVAILABLE_BALANCE");
    assert.match(payload.error.invariant, /available_balance/);
    assert.equal(payload.error.requestId, "REQ-TEST-LEDGER");
  });
});
