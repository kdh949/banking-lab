"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import {
  BankingApiError,
  createBankingApiClient,
  type ExternalSettlementImportResponse,
  type PaymentReconciliationExceptionListResponse,
  type PaymentReconciliationResultDto,
  type PaymentReconciliationRunResponse,
  type SettlementBatchRunResponse
} from "@banking-lab/api-client";
import { createSimulatorBearerToken } from "@banking-lab/auth-client";

const paymentApiBaseUrl = process.env.NEXT_PUBLIC_BANKING_PAYMENT_API_BASE_URL ?? "";
const operatorId = "ops01";
const businessDate = "2026-02-03";
const expectedValueDate = "2026-02-04";
const defaultCsv = [
  "external_reference,payment_instruction_id,biller_id,amount_minor,currency,business_date,value_date,status",
  "EXT-DEMO-RUN-001,PAY-SYN-SETTLE-001,SYN-BILLER-UTILITY,100000,KRW,2026-02-03,2026-02-04,ACCEPTED",
  "EXT-DEMO-RUN-002,PAY-SYN-SETTLE-002,SYN-BILLER-UTILITY,50000,KRW,2026-02-03,2026-02-04,ACCEPTED",
  "EXT-DEMO-RUN-003,PAY-SYN-SETTLE-003,SYN-BILLER-TELCO,20000,KRW,2026-02-03,2026-02-05,ACCEPTED",
  "EXT-DEMO-RUN-004,PAY-SYN-SETTLE-004,SYN-BILLER-TELCO,30000,KRW,2026-02-03,2026-02-04,REJECTED"
].join("\n");

type AsyncState<T> =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | { readonly status: "complete"; readonly data: T }
  | { readonly status: "failed"; readonly message: string };

type StepStatus = "ready" | "running" | "complete" | "blocked" | "failed";

export function PaymentSettlementWorkbench() {
  const [hydrated, setHydrated] = useState(false);
  const [csvContent, setCsvContent] = useState(defaultCsv);
  const [importState, setImportState] = useState<AsyncState<ExternalSettlementImportResponse>>({ status: "idle" });
  const [batchState, setBatchState] = useState<AsyncState<SettlementBatchRunResponse>>({ status: "idle" });
  const [reconciliationState, setReconciliationState] = useState<AsyncState<PaymentReconciliationRunResponse>>({ status: "idle" });
  const [exceptionState, setExceptionState] = useState<AsyncState<PaymentReconciliationExceptionListResponse>>({ status: "idle" });
  const runTokenRef = useRef("");

  useEffect(() => setHydrated(true), []);

  const client = useMemo(
    () =>
      paymentApiBaseUrl
        ? createBankingApiClient({
            baseUrl: paymentApiBaseUrl,
            bearerToken: createSimulatorBearerToken({
              subject: operatorId,
              roles: ["OPS_OPERATOR"]
            })
          })
        : null,
    []
  );

  const runToken = () => {
    if (!runTokenRef.current) {
      runTokenRef.current = `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
    }
    return runTokenRef.current;
  };

  const importExternalCsv = async () => {
    const token = runToken();
    setImportState({ status: "running" });
    setBatchState({ status: "idle" });
    setReconciliationState({ status: "idle" });
    setExceptionState({ status: "idle" });
    try {
      const response = client
        ? await client.importExternalSettlementCsv({
            originalFileName: `portfolio-settlement-${token}.csv`,
            institutionCode: "SYN-CLEARING-HOUSE",
            businessDate,
            csvContent: csvContent.replaceAll("DEMO-RUN", token),
            idempotencyKey: `portfolio-import-${token}`,
            requestedBy: operatorId,
            reason: "Portfolio settlement provenance walkthrough"
          })
        : fixtureImport(token);
      requireSynthetic(response.item.syntheticOnly, "external import");
      setImportState({ status: "complete", data: response });
    } catch (error: unknown) {
      setImportState({ status: "failed", message: errorMessage(error) });
    }
  };

  const buildSettlementBatch = async () => {
    if (importState.status !== "complete") return;
    const token = runToken();
    setBatchState({ status: "running" });
    setReconciliationState({ status: "idle" });
    setExceptionState({ status: "idle" });
    try {
      const response = client
        ? await client.createSettlementBatchRun({
            externalSettlementImportId: importState.data.item.externalSettlementImportId,
            feeRateBps: 100,
            vatRateBps: 1_000,
            cutoffAt: "2026-02-03T15:30:00+09:00",
            idempotencyKey: `portfolio-batch-${token}`,
            requestedBy: operatorId,
            reason: "Portfolio gross fee VAT net walkthrough"
          })
        : fixtureBatch(importState.data.item.externalSettlementImportId);
      requireSynthetic(response.syntheticOnly, "settlement batch");
      setBatchState({ status: "complete", data: response });
    } catch (error: unknown) {
      setBatchState({ status: "failed", message: errorMessage(error) });
    }
  };

  const runThreeWayReconciliation = async () => {
    if (importState.status !== "complete" || batchState.status !== "complete") return;
    const token = runToken();
    setReconciliationState({ status: "running" });
    setExceptionState({ status: "idle" });
    try {
      const response = client
        ? await client.createPaymentReconciliationRun({
            externalSettlementImportId: importState.data.item.externalSettlementImportId,
            expectedValueDate,
            allowedValueDateLagDays: 0,
            slaDays: 2,
            ownerId: operatorId,
            idempotencyKey: `portfolio-reconciliation-${token}`,
            requestedBy: operatorId,
            reason: "Portfolio payment ledger external three-way walkthrough"
          })
        : fixtureReconciliation(importState.data.item.externalSettlementImportId);
      requireSynthetic(response.syntheticOnly, "three-way reconciliation");
      setReconciliationState({ status: "complete", data: response });
    } catch (error: unknown) {
      setReconciliationState({ status: "failed", message: errorMessage(error) });
    }
  };

  const refreshExceptionQueue = async () => {
    if (reconciliationState.status !== "complete") return;
    setExceptionState({ status: "running" });
    try {
      const response = client
        ? await client.paymentReconciliationExceptions({
            ownerId: operatorId,
            status: "OPEN",
            reason: "Portfolio exception owner and SLA walkthrough"
          })
        : fixtureExceptions(reconciliationState.data.results);
      requireSynthetic(response.syntheticOnly, "reconciliation exception queue");
      setExceptionState({ status: "complete", data: response });
    } catch (error: unknown) {
      setExceptionState({ status: "failed", message: errorMessage(error) });
    }
  };

  const imported = importState.status === "complete" ? importState.data.item : null;
  const batches = batchState.status === "complete" ? batchState.data.items : [];
  const reconciliation = reconciliationState.status === "complete" ? reconciliationState.data : null;
  const exceptions = exceptionState.status === "complete" ? exceptionState.data.items : [];

  return (
    <section
      className="settlement-workbench"
      data-testid="payment-settlement-workbench"
      data-hydrated={hydrated}
      aria-labelledby="settlement-workbench-title"
    >
      <header className="settlement-workbench__header">
        <div>
          <p className="settlement-workbench__eyebrow">PORTFOLIO VERTICAL SLICE</p>
          <h2 id="settlement-workbench-title">Payment settlement control room</h2>
          <p>
            Independent CSV → gross / fee / VAT / net batch → payment · ledger · external 3-way → owner and SLA exception queue
          </p>
        </div>
        <span className={`settlement-mode ${client ? "settlement-mode--live" : "settlement-mode--fixture"}`}>
          {client ? "Spring API connected" : "Guided synthetic fixture"}
        </span>
      </header>

      <ol className="settlement-steps" aria-label="Settlement workflow stages">
        <WorkflowStep number="01" label="External evidence" status={stepStatus(importState)} />
        <WorkflowStep number="02" label="Settlement position" status={dependentStepStatus(importState, batchState)} />
        <WorkflowStep number="03" label="Three-way match" status={dependentStepStatus(batchState, reconciliationState)} />
        <WorkflowStep number="04" label="Exception SLA" status={dependentStepStatus(reconciliationState, exceptionState)} />
      </ol>

      <div className="settlement-stage-grid">
        <article className="settlement-stage" data-testid="settlement-import-stage">
          <div className="settlement-stage__heading">
            <div>
              <span>01 · PROVENANCE</span>
              <h3>Receive independent clearing CSV</h3>
            </div>
            <StageBadge status={stepStatus(importState)} />
          </div>
          <label className="settlement-field">
            <span>External institution file</span>
            <textarea
              aria-label="Independent external settlement CSV"
              value={csvContent}
              onChange={(event) => setCsvContent(event.target.value)}
              rows={7}
              spellCheck={false}
            />
          </label>
          <button type="button" onClick={() => void importExternalCsv()} disabled={importState.status === "running"}>
            {importState.status === "running" ? "Preserving provenance…" : "Import independent CSV"}
          </button>
          {imported ? (
            <dl className="settlement-metrics">
              <Metric term="Import" value={imported.externalSettlementImportId} />
              <Metric term="SHA-256" value={shortHash(imported.fileSha256)} />
              <Metric term="Rows" value={`${imported.lineCount} total · ${imported.acceptedLineCount} accepted`} />
              <Metric term="Source" value={`${imported.institutionCode} · ${imported.fileByteSize} bytes`} />
            </dl>
          ) : null}
          <Failure state={importState} />
        </article>

        <article className="settlement-stage" data-testid="settlement-batch-stage">
          <div className="settlement-stage__heading">
            <div>
              <span>02 · POSITION</span>
              <h3>Calculate biller settlement batches</h3>
            </div>
            <StageBadge status={dependentStepStatus(importState, batchState)} />
          </div>
          <p className="settlement-stage__description">
            Accepted rows are grouped by biller, currency, business date, and value date. INCLUDED_IN_BATCH does not claim payout finality.
          </p>
          <button
            type="button"
            onClick={() => void buildSettlementBatch()}
            disabled={importState.status !== "complete" || batchState.status === "running"}
          >
            {batchState.status === "running" ? "Calculating position…" : "Build gross / fee / VAT / net batch"}
          </button>
          {batches.length > 0 ? (
            <div className="settlement-table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Biller</th>
                    <th>Items</th>
                    <th>Gross</th>
                    <th>Fee</th>
                    <th>VAT</th>
                    <th>Net</th>
                  </tr>
                </thead>
                <tbody>
                  {batches.map((batch) => (
                    <tr key={batch.settlementBatchId}>
                      <td>{batch.billerId.replace("SYN-BILLER-", "")}</td>
                      <td>{batch.itemCount}</td>
                      <td>{money(batch.grossAmountMinor, batch.currency)}</td>
                      <td>{money(batch.feeAmountMinor, batch.currency)}</td>
                      <td>{money(batch.vatAmountMinor, batch.currency)}</td>
                      <td className="settlement-table__emphasis">{money(batch.netAmountMinor, batch.currency)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <EmptyState>Import the external file before calculating a position.</EmptyState>
          )}
          <Failure state={batchState} />
        </article>
      </div>

      <article className="settlement-stage settlement-stage--wide" data-testid="settlement-reconciliation-stage">
        <div className="settlement-stage__heading">
          <div>
            <span>03 · CONTROL</span>
            <h3>Compare payment, balanced ledger evidence, and external line</h3>
          </div>
          <StageBadge status={dependentStepStatus(batchState, reconciliationState)} />
        </div>
        <div className="settlement-command-row">
          <button
            type="button"
            onClick={() => void runThreeWayReconciliation()}
            disabled={batchState.status !== "complete" || reconciliationState.status === "running"}
          >
            {reconciliationState.status === "running" ? "Fetching ledger evidence…" : "Run payment · ledger · external 3-way"}
          </button>
          {reconciliation ? (
            <div className="settlement-run-summary" aria-label="Reconciliation summary">
              <strong>{reconciliation.item.paymentReconciliationRunId}</strong>
              <span>{reconciliation.item.matchedCount} matched</span>
              <span>{reconciliation.item.exceptionCount} exceptions</span>
              <span>{reconciliation.item.ledgerEvidenceCount} ledger evidence rows</span>
            </div>
          ) : null}
        </div>
        {reconciliation ? (
          <div className="settlement-table-wrap settlement-table-wrap--matrix">
            <table>
              <thead>
                <tr>
                  <th>Payment</th>
                  <th>Ledger TX</th>
                  <th>Internal</th>
                  <th>Ledger</th>
                  <th>External</th>
                  <th>Result</th>
                </tr>
              </thead>
              <tbody>
                {reconciliation.results.map((result) => (
                  <ReconciliationRow key={result.paymentReconciliationResultId} result={result} />
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyState>The run starts only after a durable external import and settlement position exist.</EmptyState>
        )}
        <Failure state={reconciliationState} />
      </article>

      <article className="settlement-stage settlement-stage--wide" data-testid="settlement-exception-stage">
        <div className="settlement-stage__heading">
          <div>
            <span>04 · OPERATIONS</span>
            <h3>Route mismatches by owner, aging, and SLA</h3>
          </div>
          <StageBadge status={dependentStepStatus(reconciliationState, exceptionState)} />
        </div>
        <div className="settlement-command-row">
          <button
            type="button"
            onClick={() => void refreshExceptionQueue()}
            disabled={reconciliationState.status !== "complete" || exceptionState.status === "running"}
          >
            {exceptionState.status === "running" ? "Refreshing queue…" : "Refresh exception queue"}
          </button>
          <p>Financial correction remains outside this P0 surface; approval and resolution fields are shown as explicit next-step handoffs.</p>
        </div>
        {exceptions.length > 0 ? (
          <div className="settlement-exception-grid">
            {exceptions.map((item) => (
              <section key={item.paymentReconciliationResultId} className="settlement-exception-card">
                <div>
                  <span className={`settlement-result settlement-result--${item.overdue ? "danger" : "warning"}`}>
                    {item.mismatchType}
                  </span>
                  <strong>{item.paymentInstructionId}</strong>
                </div>
                <dl>
                  <Metric term="Owner" value={item.ownerId} />
                  <Metric term="Aging" value={`${item.agingDays} day${item.agingDays === 1 ? "" : "s"}`} />
                  <Metric term="Due" value={item.dueAt ? item.dueAt.slice(0, 10) : "not set"} />
                  <Metric term="Status" value={`${item.resultStatus}${item.overdue ? " · OVERDUE" : ""}`} />
                </dl>
                <p>{item.detectedReason}</p>
              </section>
            ))}
          </div>
        ) : (
          <EmptyState>Run reconciliation, then load the reason-audited exception queue.</EmptyState>
        )}
        <Failure state={exceptionState} />
      </article>
    </section>
  );
}

function WorkflowStep({ number, label, status }: { readonly number: string; readonly label: string; readonly status: StepStatus }) {
  return (
    <li data-status={status}>
      <span>{number}</span>
      <strong>{label}</strong>
      <small>{status}</small>
    </li>
  );
}

function StageBadge({ status }: { readonly status: StepStatus }) {
  return <span className={`settlement-stage-badge settlement-stage-badge--${status}`}>{status}</span>;
}

function Metric({ term, value }: { readonly term: string; readonly value: string }) {
  return (
    <div>
      <dt>{term}</dt>
      <dd>{value}</dd>
    </div>
  );
}

function EmptyState({ children }: { readonly children: React.ReactNode }) {
  return <p className="settlement-empty">{children}</p>;
}

function Failure<T>({ state }: { readonly state: AsyncState<T> }) {
  return state.status === "failed" ? <p className="settlement-error">{state.message}</p> : null;
}

function ReconciliationRow({ result }: { readonly result: PaymentReconciliationResultDto }) {
  const currency = result.currency ?? "KRW";
  return (
    <tr>
      <td>{result.paymentInstructionId}</td>
      <td>{result.ledgerTransactionId ?? "missing"}</td>
      <td>{nullableMoney(result.internalAmountMinor, currency)}</td>
      <td>{nullableMoney(result.ledgerAmountMinor, currency)}</td>
      <td>{nullableMoney(result.externalAmountMinor, currency)}</td>
      <td>
        <span className={`settlement-result settlement-result--${result.mismatchType === "MATCHED" ? "ok" : "warning"}`}>
          {result.mismatchType}
        </span>
      </td>
    </tr>
  );
}

function stepStatus<T>(state: AsyncState<T>): StepStatus {
  if (state.status === "idle") return "ready";
  if (state.status === "running") return "running";
  if (state.status === "failed") return "failed";
  return "complete";
}

function dependentStepStatus<T, U>(dependency: AsyncState<T>, state: AsyncState<U>): StepStatus {
  if (dependency.status !== "complete" && state.status === "idle") return "blocked";
  return stepStatus(state);
}

function requireSynthetic(value: boolean, label: string) {
  if (!value) {
    throw new Error(`${label} returned a non-synthetic result`);
  }
}

function errorMessage(error: unknown): string {
  if (error instanceof BankingApiError) {
    try {
      const parsed = JSON.parse(error.body) as { readonly error?: { readonly message?: string }; readonly message?: string };
      return parsed.error?.message ?? parsed.message ?? error.message;
    } catch {
      return `${error.message}: ${error.body}`;
    }
  }
  return error instanceof Error ? error.message : "Unknown settlement workflow failure";
}

function money(amountMinor: number, currency: string): string {
  return new Intl.NumberFormat("ko-KR", { style: "currency", currency, maximumFractionDigits: 0 }).format(amountMinor);
}

function nullableMoney(amountMinor: number | null | undefined, currency: string): string {
  return amountMinor === null || amountMinor === undefined ? "missing" : money(amountMinor, currency);
}

function shortHash(value: string): string {
  return `${value.slice(0, 12)}…${value.slice(-8)}`;
}

function fixtureImport(token: string): ExternalSettlementImportResponse {
  const importId = `ESI-PORTFOLIO-${token}`;
  const createdAt = "2026-02-03T09:03:00+09:00";
  const rows = [
    ["001", "PAY-SYN-SETTLE-001", "SYN-BILLER-UTILITY", 100_000, "2026-02-04", "ACCEPTED"],
    ["002", "PAY-SYN-SETTLE-002", "SYN-BILLER-UTILITY", 50_000, "2026-02-04", "ACCEPTED"],
    ["003", "PAY-SYN-SETTLE-003", "SYN-BILLER-TELCO", 20_000, "2026-02-05", "ACCEPTED"],
    ["004", "PAY-SYN-SETTLE-004", "SYN-BILLER-TELCO", 30_000, "2026-02-04", "REJECTED"]
  ] as const;
  return {
    replayed: false,
    item: {
      externalSettlementImportId: importId,
      originalFileName: `portfolio-settlement-${token}.csv`,
      institutionCode: "SYN-CLEARING-HOUSE",
      fileSha256: "7f2bd2f5a1ee5fc41c8329b6eaa3d5dd645e58cb351735ae14999c6520e01c42",
      fileByteSize: 612,
      businessDate,
      status: "IMPORTED",
      lineCount: rows.length,
      acceptedLineCount: 3,
      rejectedLineCount: 1,
      requestedBy: operatorId,
      reason: "Portfolio settlement provenance walkthrough",
      syntheticOnly: true,
      receivedAt: createdAt,
      lines: rows.map(([suffix, paymentInstructionId, billerId, amountMinor, valueDate, status], index) => ({
        externalSettlementLineId: `ESL-PORTFOLIO-${suffix}`,
        lineNumber: index + 2,
        externalReference: `EXT-${token}-${suffix}`,
        paymentInstructionId,
        billerId,
        amountMinor,
        currency: "KRW",
        businessDate,
        valueDate,
        status,
        rawLine: `EXT-${token}-${suffix},${paymentInstructionId},${billerId},${amountMinor},KRW,${businessDate},${valueDate},${status}`,
        syntheticOnly: true,
        createdAt
      }))
    }
  };
}

function fixtureBatch(importId: string): SettlementBatchRunResponse {
  const common = {
    settlementBatchRunId: "SBR-PORTFOLIO-001",
    externalSettlementImportId: importId,
    currency: "KRW",
    businessDate,
    cutoffAt: "2026-02-03T15:30:00+09:00",
    feeRateBps: 100,
    vatRateBps: 1_000,
    adjustmentAmountMinor: 0,
    status: "INCLUDED_IN_BATCH" as const,
    externalPayoutReference: null,
    syntheticOnly: true,
    createdAt: "2026-02-03T15:31:00+09:00"
  };
  return {
    settlementBatchRunId: common.settlementBatchRunId,
    externalSettlementImportId: importId,
    replayed: false,
    syntheticOnly: true,
    items: [
      {
        ...common,
        settlementBatchId: "STB-PORTFOLIO-UTILITY",
        billerId: "SYN-BILLER-UTILITY",
        valueDate: "2026-02-04",
        grossAmountMinor: 150_000,
        feeAmountMinor: 1_500,
        vatAmountMinor: 150,
        netAmountMinor: 148_350,
        itemCount: 2
      },
      {
        ...common,
        settlementBatchId: "STB-PORTFOLIO-TELCO",
        billerId: "SYN-BILLER-TELCO",
        valueDate: "2026-02-05",
        grossAmountMinor: 20_000,
        feeAmountMinor: 200,
        vatAmountMinor: 20,
        netAmountMinor: 19_780,
        itemCount: 1
      }
    ]
  };
}

function fixtureReconciliation(importId: string): PaymentReconciliationRunResponse {
  const runId = "PRR-PORTFOLIO-001";
  const common = {
    paymentReconciliationRunId: runId,
    businessDate,
    expectedValueDate,
    ownerId: operatorId,
    detectedAt: "2026-02-03T16:00:00+09:00",
    dueAt: "2026-02-05T16:00:00+09:00",
    agingDays: 1,
    overdue: false,
    resolution: null,
    approvalId: null,
    resolvedAt: null,
    syntheticOnly: true
  };
  return {
    item: {
      paymentReconciliationRunId: runId,
      externalSettlementImportId: importId,
      businessDate,
      expectedValueDate,
      allowedValueDateLagDays: 0,
      slaDays: 2,
      ownerId: operatorId,
      status: "EXCEPTIONS_OPEN",
      externalLineCount: 4,
      ledgerEvidenceCount: 3,
      matchedCount: 1,
      exceptionCount: 2,
      attemptCount: 1,
      failureMessage: null,
      syntheticOnly: true,
      createdAt: "2026-02-03T16:00:00+09:00",
      updatedAt: "2026-02-03T16:00:02+09:00",
      completedAt: "2026-02-03T16:00:02+09:00"
    },
    results: [
      {
        ...common,
        paymentReconciliationResultId: "PRC-PORTFOLIO-001",
        externalSettlementLineId: "ESL-PORTFOLIO-001",
        paymentInstructionId: "PAY-SYN-SETTLE-001",
        ledgerTransactionId: "TX-SYN-SETTLE-001",
        mismatchType: "MATCHED",
        resultStatus: "MATCHED",
        internalAmountMinor: 100_000,
        ledgerAmountMinor: 100_000,
        externalAmountMinor: 100_000,
        currency: "KRW",
        externalStatus: "ACCEPTED",
        actualValueDate: "2026-02-04",
        detectedReason: "All three independent sources agree"
      },
      {
        ...common,
        paymentReconciliationResultId: "PRC-PORTFOLIO-002",
        externalSettlementLineId: "ESL-PORTFOLIO-002",
        paymentInstructionId: "PAY-SYN-SETTLE-002",
        ledgerTransactionId: "TX-SYN-SETTLE-002",
        mismatchType: "AMOUNT_MISMATCH",
        resultStatus: "OPEN",
        internalAmountMinor: 50_000,
        ledgerAmountMinor: 50_000,
        externalAmountMinor: 49_000,
        currency: "KRW",
        externalStatus: "ACCEPTED",
        actualValueDate: "2026-02-04",
        detectedReason: "External amount differs from payment and balanced ledger evidence"
      },
      {
        ...common,
        paymentReconciliationResultId: "PRC-PORTFOLIO-003",
        externalSettlementLineId: "ESL-PORTFOLIO-003",
        paymentInstructionId: "PAY-SYN-SETTLE-003",
        ledgerTransactionId: null,
        mismatchType: "MISSING_LEDGER",
        resultStatus: "OPEN",
        internalAmountMinor: 20_000,
        ledgerAmountMinor: null,
        externalAmountMinor: 20_000,
        currency: "KRW",
        externalStatus: "ACCEPTED",
        actualValueDate: "2026-02-05",
        detectedReason: "Payment and external line exist but balanced ledger evidence is missing"
      }
    ],
    replayed: false,
    auditEventId: "PRA-PORTFOLIO-RUN-001",
    syntheticOnly: true
  };
}

function fixtureExceptions(results: readonly PaymentReconciliationResultDto[]): PaymentReconciliationExceptionListResponse {
  return {
    items: results.filter((result) => result.resultStatus === "OPEN"),
    auditEventId: "PRA-PORTFOLIO-QUEUE-001",
    syntheticOnly: true
  };
}
