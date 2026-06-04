#!/usr/bin/env node
import { existsSync } from "node:fs";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { Readable } from "node:stream";
import { createLabHandler, createLabState } from "../../runtime/labApp.mjs";

const defaultConfig = {
  iterations: 3,
  concurrency: 3,
  latencyBudgetMs: 5000,
  output: "docs/test-evidence/generated/load-test-summary.json",
  markdownOutput: "docs/test-evidence/load-test-summary.md"
};

const scenarioNames = [
  "customer-account-lookup",
  "customer-transfer-request",
  "idempotency-retry-burst",
  "staff-customer-lookup",
  "fds-held-transfer-lookup",
  "approval-inbox-lookup"
];

async function main() {
  const config = parseArgs(process.argv.slice(2));
  const state = await createLabState();
  const handler = await createLabHandler(state);
  const invoke = (request, latencyBudgetMs) => invokeHandler(handler, request, latencyBudgetMs);
  const setup = await seedOperationalQueues(invoke, config.latencyBudgetMs);
  const beforeTransactionCount = state.ledgerCore.transactions.length;
  const beforeAuditCount = state.auditLog.all().length;
  const workers = Array.from({ length: config.concurrency }, (_, index) =>
    runWorker({ workerIndex: index + 1, invoke, config, setup })
  );
  const results = (await Promise.all(workers)).flat();
  const summary = summarizeResults({
    config,
    setup,
    results,
    beforeTransactionCount,
    beforeAuditCount,
    state
  });

  await writeJsonPreservingTimestamp(config.output, summary);
  await writeTextIfChanged(config.markdownOutput, renderMarkdown(summary));

  if (summary.results.status !== "pass") {
    console.error(`Synthetic load smoke failed with ${summary.results.failedScenarioRuns} failed scenario runs.`);
    process.exitCode = 1;
    return;
  }
  console.log(
    `Synthetic load smoke passed: ${summary.results.totalRequests} requests, `
      + `${summary.results.scenarioRuns} scenario runs, `
      + `${summary.domainChecks.idempotencyReplayResponses} idempotency replays.`
  );
}

function parseArgs(argv) {
  const config = { ...defaultConfig };
  for (let index = 0; index < argv.length; index += 1) {
    const flag = argv[index];
    const value = argv[index + 1];
    if (flag === "--iterations") {
      config.iterations = parsePositiveInt(value, flag);
      index += 1;
    } else if (flag === "--concurrency") {
      config.concurrency = parsePositiveInt(value, flag);
      index += 1;
    } else if (flag === "--latency-budget-ms") {
      config.latencyBudgetMs = parsePositiveInt(value, flag);
      index += 1;
    } else if (flag === "--output") {
      config.output = requireValue(value, flag);
      index += 1;
    } else if (flag === "--markdown-output") {
      config.markdownOutput = requireValue(value, flag);
      index += 1;
    } else {
      throw new Error(`Unknown load-test argument: ${flag}`);
    }
  }
  return config;
}

function parsePositiveInt(value, flag) {
  const parsed = Number.parseInt(requireValue(value, flag), 10);
  if (!Number.isInteger(parsed) || parsed <= 0) {
    throw new Error(`${flag} must be a positive integer.`);
  }
  return parsed;
}

function requireValue(value, flag) {
  if (!value || value.startsWith("--")) {
    throw new Error(`${flag} requires a value.`);
  }
  return value;
}

async function seedOperationalQueues(invoke, latencyBudgetMs) {
  const fdsSeed = await postJson(invoke, "/api/customer/transfers", {
    fromAccountId: "ACC-SYN-001-001",
    toAccountId: "ACC-SYN-002-001",
    amountMinor: 5000000,
    idempotencyKey: "LOAD-FDS-SEED-001",
    requestedBy: "SYN-CUS-001",
    newDevice: true
  }, latencyBudgetMs);
  const approvalSeed = await postJson(invoke, "/api/staff/approvals", {
    businessType: "FEE_WAIVER",
    businessReferenceId: "LOAD-FEE-WAIVER-001",
    requestedBy: "branch01",
    requestedByRole: "BRANCH_STAFF",
    requestReason: "Synthetic load smoke approval queue seed",
    screenId: "APR-001",
    afterSnapshot: {
      customerId: "SYN-CUS-001",
      accountId: "ACC-SYN-001-001",
      feePolicyId: "SYN-FEE-POLICY-LOAD",
      syntheticOnly: true
    }
  }, latencyBudgetMs);

  const errors = [];
  if (fdsSeed.status !== 202 || fdsSeed.payload?.item?.status !== "HELD") {
    errors.push("FDS seed transfer did not create a HELD result.");
  }
  if (approvalSeed.status !== 201 || approvalSeed.payload?.item?.status !== "PENDING") {
    errors.push("Approval seed did not create a PENDING approval.");
  }
  if (errors.length > 0) {
    throw new Error(errors.join(" "));
  }

  return {
    requestCount: 2,
    fdsCaseId: fdsSeed.payload.item.caseId,
    approvalId: approvalSeed.payload.item.approvalId
  };
}

async function runWorker({ workerIndex, invoke, config, setup }) {
  const results = [];
  for (let iteration = 1; iteration <= config.iterations; iteration += 1) {
    const context = {
      workerIndex,
      iteration,
      suffix: `W${String(workerIndex).padStart(2, "0")}-I${String(iteration).padStart(3, "0")}`,
      invoke,
      latencyBudgetMs: config.latencyBudgetMs,
      setup
    };
    results.push(await customerAccountLookup(context));
    results.push(await customerTransferRequest(context));
    results.push(await idempotencyRetryBurst(context));
    results.push(await staffCustomerLookup(context));
    results.push(await fdsHeldTransferLookup(context));
    results.push(await approvalInboxLookup(context));
  }
  return results;
}

async function customerAccountLookup(context) {
  const request = await getJson(
    context.invoke,
    "/api/customer/accounts/ACC-SYN-001-001/detail?customerId=SYN-CUS-001",
    context.latencyBudgetMs
  );
  return scenarioResult("customer-account-lookup", [request], [
    request.status === 200,
    request.payload?.item?.maskedAccountNo === "LAB-***-0001",
    request.payload?.item?.piiExposure === undefined
  ]);
}

async function customerTransferRequest(context) {
  const request = await postJson(context.invoke, "/api/customer/transfers", {
    fromAccountId: "ACC-SYN-001-001",
    toAccountId: "ACC-SYN-002-001",
    amountMinor: 10,
    idempotencyKey: `LOAD-TRF-${context.suffix}`,
    requestedBy: "SYN-CUS-001"
  }, context.latencyBudgetMs);
  return scenarioResult("customer-transfer-request", [request], [
    request.status === 201,
    request.payload?.item?.status === "POSTED",
    /^TX-TRF-/.test(request.payload?.item?.transactionId || "")
  ]);
}

async function idempotencyRetryBurst(context) {
  const body = {
    fromAccountId: "ACC-SYN-001-001",
    toAccountId: "ACC-SYN-002-001",
    amountMinor: 5,
    idempotencyKey: `LOAD-IDEMP-${context.suffix}`,
    requestedBy: "SYN-CUS-001"
  };
  const first = await postJson(context.invoke, "/api/customer/transfers", body, context.latencyBudgetMs);
  const retries = await Promise.all([
    postJson(context.invoke, "/api/customer/transfers", body, context.latencyBudgetMs),
    postJson(context.invoke, "/api/customer/transfers", body, context.latencyBudgetMs)
  ]);
  return scenarioResult("idempotency-retry-burst", [first, ...retries], [
    first.status === 201,
    retries.every((request) => request.status === 200),
    retries.every((request) => request.payload?.replayed === true),
    retries.every((request) => request.payload?.item?.transactionId === first.payload?.item?.transactionId)
  ]);
}

async function staffCustomerLookup(context) {
  const request = await getJson(
    context.invoke,
    `/api/staff/customers/search?query=SYN-CUS-001&reason=${encodeURIComponent("Synthetic load staff lookup")}`,
    context.latencyBudgetMs
  );
  return scenarioResult("staff-customer-lookup", [request], [
    request.status === 200,
    Array.isArray(request.payload?.items),
    request.payload?.items?.some((item) => item.customerId === "SYN-CUS-001")
  ]);
}

async function fdsHeldTransferLookup(context) {
  const request = await getJson(context.invoke, "/api/staff/fds-cases", context.latencyBudgetMs);
  return scenarioResult("fds-held-transfer-lookup", [request], [
    request.status === 200,
    request.payload?.items?.some((item) => item.caseId === context.setup.fdsCaseId)
  ]);
}

async function approvalInboxLookup(context) {
  const request = await getJson(context.invoke, "/api/staff/approvals", context.latencyBudgetMs);
  return scenarioResult("approval-inbox-lookup", [request], [
    request.status === 200,
    request.payload?.items?.some((item) => item.approvalId === context.setup.approvalId && item.status === "PENDING")
  ]);
}

async function getJson(invoke, pathname, latencyBudgetMs) {
  return requestJson(invoke, { method: "GET", url: pathname }, latencyBudgetMs);
}

async function postJson(invoke, pathname, body, latencyBudgetMs) {
  return requestJson(invoke, {
    method: "POST",
    url: pathname,
    body: JSON.stringify(body)
  }, latencyBudgetMs);
}

async function requestJson(invoke, request, latencyBudgetMs) {
  return invoke(request, latencyBudgetMs);
}

async function invokeHandler(handler, request, latencyBudgetMs) {
  const started = performance.now();
  const chunks = [];
  const body = request.body === undefined ? [] : [Buffer.from(request.body)];
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
  const elapsedMs = performance.now() - started;
  const text = Buffer.concat(chunks).toString("utf8");
  let payload = null;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    payload = { parseError: true };
  }
  return {
    status: response.statusCode,
    latencyBudgetBreached: elapsedMs > latencyBudgetMs,
    payload
  };
}

function scenarioResult(name, requests, checks) {
  const errors = [];
  if (requests.some((request) => request.latencyBudgetBreached)) {
    errors.push("latency budget breached");
  }
  checks.forEach((passed, index) => {
    if (!passed) {
      errors.push(`check ${index + 1} failed`);
    }
  });
  return {
    name,
    requests,
    success: errors.length === 0,
    errors
  };
}

function summarizeResults({ config, setup, results, beforeTransactionCount, beforeAuditCount, state }) {
  const scenarioStats = scenarioNames.map((name) => {
    const scenarioRuns = results.filter((result) => result.name === name);
    const requests = scenarioRuns.flatMap((result) => result.requests);
    return {
      name,
      runs: scenarioRuns.length,
      requests: requests.length,
      failures: scenarioRuns.filter((result) => !result.success).length,
      latencyBudgetBreaches: requests.filter((request) => request.latencyBudgetBreached).length,
      statusCounts: countStatuses(requests)
    };
  });
  const allRequests = results.flatMap((result) => result.requests);
  const failedScenarioRuns = results.filter((result) => !result.success).length;
  const idempotencyReplayResponses = results
    .filter((result) => result.name === "idempotency-retry-burst")
    .flatMap((result) => result.requests)
    .filter((request) => request.payload?.replayed === true)
    .length;
  const approvals = state.approvalStore.list();
  const auditEventsAfterLoad = state.auditLog.all().length;
  const fdsHeldCaseVisible = state.fdsCases.some((item) => item.caseId === setup.fdsCaseId);
  const approvalInboxVisible = approvals.some((item) => item.approvalId === setup.approvalId && item.status === "PENDING");
  const ledgerInvariantValid = state.ledgerCore.validateInvariants();
  const auditHashChainValid = state.auditLog.verifyHashChain();
  const ledgerTransactionsAdded = state.ledgerCore.transactions.length - beforeTransactionCount;

  return {
    schemaVersion: 1,
    command: "npm run load:synthetic",
    scenario: "mixed-banking-traffic",
    syntheticDataOnly: true,
    targetMode: "local-synthetic-handler",
    evidenceBoundary: [
      "Synthetic smoke load only; not a capacity benchmark or production readiness claim.",
      "The default runner invokes the in-process lab request handler and uses synthetic seed data only.",
      "No real money, real PII, real KYC, payment network, or external financial institution API is used."
    ],
    config: {
      iterations: config.iterations,
      concurrency: config.concurrency,
      latencyBudgetMs: config.latencyBudgetMs,
      scenarios: scenarioNames
    },
    setup: {
      requests: setup.requestCount,
      fdsCaseSeeded: true,
      approvalInboxSeeded: true
    },
    results: {
      status: failedScenarioRuns === 0
        && allRequests.every((request) => !request.latencyBudgetBreached)
        && ledgerInvariantValid
        && auditHashChainValid
        && fdsHeldCaseVisible
        && approvalInboxVisible
        ? "pass"
        : "fail",
      scenarioRuns: results.length,
      totalRequests: allRequests.length + setup.requestCount,
      failedScenarioRuns,
      latencyBudgetBreaches: allRequests.filter((request) => request.latencyBudgetBreached).length,
      statusCounts: countStatuses(allRequests),
      scenarios: scenarioStats
    },
    domainChecks: {
      ledgerInvariantValid,
      auditHashChainValid,
      ledgerTransactionsAdded,
      idempotencyReplayResponses,
      fdsHeldCaseVisible,
      approvalInboxVisible,
      auditEventsAppended: auditEventsAfterLoad - beforeAuditCount
    }
  };
}

function countStatuses(requests) {
  return requests.reduce((counts, request) => {
    const key = String(request.status);
    counts[key] = (counts[key] || 0) + 1;
    return counts;
  }, {});
}

function renderMarkdown(summary) {
  const scenarioRows = summary.results.scenarios
    .map((scenario) =>
      `| ${scenario.name} | ${scenario.runs} | ${scenario.requests} | ${scenario.failures} | ${scenario.latencyBudgetBreaches} |`
    )
    .join("\n");
  return `# Synthetic Load Test Summary

## Verdict

Status: ${summary.results.status}

This is a synthetic smoke load, not a capacity benchmark. The default runner invokes the in-process lab request handler and uses synthetic seed data only. No real money, real PII, real KYC, payment networks, or external financial institution APIs are used.

## Command

\`\`\`bash
npm run load:synthetic
\`\`\`

## Scenario Mix

| Scenario | Runs | Requests | Failures | Latency budget breaches |
| --- | ---: | ---: | ---: | ---: |
${scenarioRows}

## Domain Checks

- Ledger invariant valid: ${summary.domainChecks.ledgerInvariantValid}
- Audit hash chain valid: ${summary.domainChecks.auditHashChainValid}
- Ledger transactions added: ${summary.domainChecks.ledgerTransactionsAdded}
- Idempotency replay responses: ${summary.domainChecks.idempotencyReplayResponses}
- FDS held case visible: ${summary.domainChecks.fdsHeldCaseVisible}
- Approval inbox visible: ${summary.domainChecks.approvalInboxVisible}
- Audit events appended: ${summary.domainChecks.auditEventsAppended}

## Evidence Boundary

- This evidence proves the load script, scenario coverage, idempotency retry handling, FDS held-case visibility, approval inbox visibility, ledger invariant preservation, and audit hash-chain continuity for a local synthetic smoke run.
- It does not prove production capacity, live Kubernetes deployment behavior, external Keycloak latency, real PostgreSQL backup/restore, or disaster recovery.
`;
}

async function writeJsonPreservingTimestamp(filePath, payload) {
  await mkdir(path.dirname(filePath), { recursive: true });
  const existing = existsSync(filePath) ? JSON.parse(await readFile(filePath, "utf8")) : undefined;
  const generatedAt = sameExceptGeneratedAt(existing, payload)
    ? existing.generatedAt
    : new Date().toISOString();
  const next = `${JSON.stringify({ generatedAt, ...payload }, null, 2)}\n`;
  if (!existing || next !== `${JSON.stringify(existing, null, 2)}\n`) {
    await writeFile(filePath, next, "utf8");
  }
}

async function writeTextIfChanged(filePath, content) {
  await mkdir(path.dirname(filePath), { recursive: true });
  const existing = existsSync(filePath) ? await readFile(filePath, "utf8") : "";
  if (existing !== content) {
    await writeFile(filePath, content, "utf8");
  }
}

function sameExceptGeneratedAt(existing, payload) {
  if (!existing) {
    return false;
  }
  const { generatedAt: _generatedAt, ...withoutGeneratedAt } = existing;
  return JSON.stringify(withoutGeneratedAt) === JSON.stringify(payload);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
