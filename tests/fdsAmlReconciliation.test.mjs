import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";
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

function today() {
  return new Date().toISOString().slice(0, 10);
}

function tomorrowOf(businessDate) {
  const date = new Date(`${businessDate}T00:00:00.000Z`);
  date.setUTCDate(date.getUTCDate() + 1);
  return date.toISOString().slice(0, 10);
}

test("high-risk transfer creates FDS case and release posts only after checker approval", async () => {
  await withServer(async ({ baseUrl, state }) => {
    const beforeCount = state.ledgerCore.transactions.length;
    const held = await postJson(`${baseUrl}/api/customer/transfers`, {
      fromAccountId: "ACC-SYN-001-001",
      toAccountId: "ACC-SYN-002-001",
      amountMinor: 5000000,
      idempotencyKey: "FDS-RELEASE-FLOW-001",
      requestedBy: "SYN-CUS-001",
      newDevice: true
    });
    const caseId = held.payload.item.caseId;
    const assigned = await postJson(`${baseUrl}/api/staff/fds-cases/${caseId}/assign`, {
      actorId: "fds01",
      owner: "fds01"
    });
    const releaseRequest = await postJson(`${baseUrl}/api/staff/fds-cases/${caseId}/release-requests`, {
      actorId: "fds01",
      requestedByRole: "FDS_REVIEWER",
      reason: "Synthetic FDS release after reviewer investigation"
    });
    const beforeApprovalResults = await (await fetch(`${baseUrl}/api/customer/transfers?customerId=SYN-CUS-001`)).json();
    const beforeApprovalTransactionCount = state.ledgerCore.transactions.length;
    const selfApproval = await postJson(`${baseUrl}/api/staff/approvals/${releaseRequest.payload.approval.approvalId}/approve`, {
      approvedBy: "fds01",
      approvedByRole: "FDS_REVIEWER"
    });
    const managerApproval = await postJson(`${baseUrl}/api/staff/approvals/${releaseRequest.payload.approval.approvalId}/approve`, {
      approvedBy: "manager01",
      approvedByRole: "BRANCH_MANAGER"
    });
    const afterApprovalResults = await (await fetch(`${baseUrl}/api/customer/transfers?customerId=SYN-CUS-001`)).json();

    assert.equal(held.response.status, 202);
    assert.equal(held.payload.item.status, "HELD");
    assert.equal(held.payload.fdsCase.alerts.some((alert) => alert.ruleId === "FDS-RULE-UNUSUAL-AMOUNT"), true);
    assert.equal(beforeApprovalTransactionCount, beforeCount);
    assert.equal(assigned.payload.item.status, "INVESTIGATING");
    assert.equal(releaseRequest.payload.item.status, "RELEASE_REQUESTED");
    assert.equal(beforeApprovalResults.items.find((item) => item.caseId === caseId).status, "HELD");
    assert.equal(selfApproval.response.status, 409);
    assert.equal(selfApproval.payload.error.code, "MAKER_CHECKER_SELF_APPROVAL_REJECTED");
    assert.equal(selfApproval.payload.error.policy, "MAKER_CHECKER_SEPARATION_OF_DUTIES");
    assert.equal(managerApproval.response.status, 200);
    assert.equal(managerApproval.payload.executed, true);
    assert.equal(managerApproval.payload.fdsCase.status, "RELEASED");
    assert.equal(managerApproval.payload.ledgerTransaction.transactionType, "INTERNAL_TRANSFER");
    assert.equal(afterApprovalResults.items.find((item) => item.caseId === caseId).status, "POSTED");
    assert.equal(state.ledgerCore.transactions.length, beforeCount + 1);
    assert.equal(state.ledgerCore.validateInvariants(), true);
  });
});

test("FDS block closes held transfer without ledger posting", async () => {
  await withServer(async ({ baseUrl, state }) => {
    const beforeCount = state.ledgerCore.transactions.length;
    const held = await postJson(`${baseUrl}/api/customer/transfers`, {
      fromAccountId: "ACC-SYN-001-001",
      toAccountId: "ACC-SYN-002-001",
      amountMinor: 6000000,
      idempotencyKey: "FDS-BLOCK-FLOW-001",
      requestedBy: "SYN-CUS-001",
      firstTimeBeneficiary: true
    });
    const caseId = held.payload.item.caseId;
    await postJson(`${baseUrl}/api/staff/fds-cases/${caseId}/assign`, {
      actorId: "fds01",
      owner: "fds01"
    });
    const blockRequest = await postJson(`${baseUrl}/api/staff/fds-cases/${caseId}/block-requests`, {
      actorId: "fds01",
      reason: "Synthetic suspicious beneficiary block"
    });
    const approved = await postJson(`${baseUrl}/api/staff/approvals/${blockRequest.payload.approval.approvalId}/approve`, {
      approvedBy: "manager01",
      approvedByRole: "BRANCH_MANAGER"
    });
    const results = await (await fetch(`${baseUrl}/api/customer/transfers?customerId=SYN-CUS-001`)).json();

    assert.equal(blockRequest.payload.item.status, "BLOCK_REQUESTED");
    assert.equal(approved.payload.fdsCase.status, "BLOCKED");
    assert.equal(results.items.find((item) => item.caseId === caseId).status, "BLOCKED");
    assert.equal(state.ledgerCore.transactions.length, beforeCount);
  });
});

test("AML case is generated for high-risk customer and closes only after approval", async () => {
  await withServer(async ({ baseUrl, state }) => {
    const held = await postJson(`${baseUrl}/api/customer/transfers`, {
      fromAccountId: "ACC-SYN-003-001",
      toAccountId: "ACC-SYN-002-001",
      amountMinor: 5000000,
      idempotencyKey: "AML-HIGH-RISK-001",
      requestedBy: "SYN-CUS-003"
    });
    const amlCases = await (await fetch(`${baseUrl}/api/staff/aml-cases`)).json();
    const amlCase = amlCases.items[0];
    const assigned = await postJson(`${baseUrl}/api/staff/aml-cases/${amlCase.caseId}/assign`, {
      actorId: "fds01",
      owner: "fds01"
    });
    const comment = await postJson(`${baseUrl}/api/staff/aml-cases/${amlCase.caseId}/comments`, {
      actorId: "fds01",
      body: "Synthetic enhanced due diligence reviewed."
    });
    const closure = await postJson(`${baseUrl}/api/staff/aml-cases/${amlCase.caseId}/closure-requests`, {
      actorId: "fds01",
      reason: "Synthetic STR simulation disposition reviewed",
      disposition: "STR_SIMULATED",
      reportReferenceId: "STR-SIM-TEST-001"
    });
    const approved = await postJson(`${baseUrl}/api/staff/approvals/${closure.payload.approval.approvalId}/approve`, {
      approvedBy: "manager01",
      approvedByRole: "BRANCH_MANAGER"
    });

    assert.equal(held.response.status, 202);
    assert.equal(amlCase.alerts.some((alert) => alert.ruleId === "AML-RULE-HIGH-RISK-CUSTOMER"), true);
    assert.equal(assigned.payload.item.status, "INVESTIGATING");
    assert.equal(comment.payload.item.comments.length, 1);
    assert.equal(closure.payload.item.status, "CLOSURE_REQUESTED");
    assert.equal(approved.payload.amlCase.status, "CLOSED");
    assert.equal(approved.payload.amlCase.strSimulation.reported, true);
    assert.equal(state.auditLog.all().some((event) => event.screenId === "AML-201" && event.eventType === "COMMAND_EXECUTED"), true);
  });
});

test("EOD reconciliation creates owned mismatch item and adjustment posts on open day", async () => {
  await withServer(async ({ baseUrl, state }) => {
    const businessDate = today();
    const nextDate = tomorrowOf(businessDate);
    const transfer = await postJson(`${baseUrl}/api/ledger/transfers`, {
      fromAccountId: "ACC-SYN-001-001",
      toAccountId: "ACC-SYN-002-001",
      amountMinor: 1234,
      idempotencyKey: "RECON-TRF-001",
      businessDate,
      requestedBy: "ops01",
      requestedChannel: "OPS_CONSOLE",
      reason: "Seed reconciliation internal transfer"
    });
    const closing = await postJson(`${baseUrl}/api/ops/daily-closings`, {
      businessDate,
      requestedBy: "ops01",
      externalMode: "MISMATCH"
    });
    const closedDayMutation = await postJson(`${baseUrl}/api/ledger/transfers`, {
      fromAccountId: "ACC-SYN-001-001",
      toAccountId: "ACC-SYN-002-001",
      amountMinor: 100,
      idempotencyKey: "RECON-CLOSED-DAY-001",
      businessDate,
      requestedBy: "ops01",
      requestedChannel: "OPS_CONSOLE",
      reason: "Should fail after close"
    });
    const item = closing.payload.reconciliationItems[0];
    const adjustmentRequest = await postJson(`${baseUrl}/api/ops/reconciliation-items/${item.itemId}/adjustment-requests`, {
      requestedBy: "ops01",
      reason: "Synthetic reconciliation adjustment",
      accountId: "ACC-SYN-001-001",
      direction: "CREDIT",
      amountMinor: 1000,
      businessDate: nextDate,
      idempotencyKey: "RECON-ADJ-001"
    });
    const approved = await postJson(`${baseUrl}/api/staff/approvals/${adjustmentRequest.payload.approval.approvalId}/approve`, {
      approvedBy: "manager01",
      approvedByRole: "BRANCH_MANAGER"
    });

    assert.equal(transfer.response.status, 201);
    assert.equal(closing.response.status, 201);
    assert.equal(closing.payload.item.ledgerInvariantValid, true);
    assert.equal(closing.payload.item.status, "UNMATCHED");
    assert.equal(item.status, "OPEN");
    assert.equal(item.owner, "ops01");
    assert.equal(closedDayMutation.response.status, 409);
    assert.equal(closedDayMutation.payload.error.code, "LEDGER_CLOSED_DAY_IMMUTABLE");
    assert.match(closedDayMutation.payload.error.invariant, /closed day/);
    assert.equal(adjustmentRequest.payload.item.status, "ADJUSTMENT_REQUESTED");
    assert.equal(approved.payload.reconciliationItem.status, "ADJUSTED");
    assert.equal(approved.payload.ledgerTransaction.transactionType, "ADJUSTMENT");
    assert.equal(approved.payload.ledgerTransaction.businessDate, nextDate);
    assert.equal(state.ledgerCore.validateInvariants(), true);
  });
});

test("target reconciliation schema and API expose synthetic mismatch taxonomy", async () => {
  const migration = await readFile("db/migrations/V033__reconciliation_mismatch_taxonomy.sql", "utf8");
  const service = await readFile("services/core-banking/src/main/kotlin/lab/banking/core/reconciliation/ReconciliationOpsService.kt", "utf8");
  const models = await readFile("services/core-banking/src/main/kotlin/lab/banking/core/reconciliation/ReconciliationOpsModels.kt", "utf8");
  const client = await readFile("packages/api-client/src/client.ts", "utf8");

  assert.match(migration, /mismatch_type TEXT NOT NULL/);
  assert.match(migration, /DUPLICATE_EXTERNAL/);
  assert.match(migration, /STALE_EXTERNAL/);
  assert.match(migration, /feed_file_id/);
  assert.match(service, /normalizedMode == "DUPLICATE"/);
  assert.match(service, /normalizedMode == "STALE"/);
  assert.match(service, /mismatchType = "UNEXPECTED_EXTERNAL"/);
  assert.match(models, /val mismatchType: String/);
  assert.match(models, /val detectedReason: String/);
  assert.match(client, /readonly mismatchType: string/);
  assert.match(client, /readonly detectedReason: string/);
});
