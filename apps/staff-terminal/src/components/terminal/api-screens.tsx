"use client";

import { useState, type ReactNode } from "react";
import {
  BankingApiError,
  createStaffApiClient,
  type MaskedCustomerDto,
  type OperationalRetryQueueItemDto,
  type OperatorApproval,
  type StaffAccountDto,
  type StaffCustomerDetailDto,
  type StaffJourneyResponse,
  type StaffTransactionDto,
  type StaffWorkflowTimelineEntryDto
} from "@banking-lab/api-client/staff";
import {
  createCallCenterApiClient,
  type CallCenterCustomerSummaryDto,
  type CallCenterInteractionDto
} from "@banking-lab/api-client/call-center";
import { createRiskApiClient, type FdsCaseDto } from "@banking-lab/api-client/risk";
import { DataTable, MaterialIcon, Panel } from "./primitives";
import type { IconName, TableColumn, TableRow } from "./types";

const apiReady = true;

type ApiState<T> =
  | { readonly status: "idle"; readonly message?: string }
  | { readonly status: "running"; readonly message: string }
  | { readonly status: "loaded"; readonly data: T; readonly message?: string }
  | { readonly status: "failed"; readonly error: unknown };

type StaffActor = "staff" | "manager";

type CustomerResult = {
  readonly auditEventId: string;
  readonly customers: readonly MaskedCustomerDto[];
  readonly detail?: StaffCustomerDetailDto;
};

type AccountResult = {
  readonly auditEventId: string;
  readonly accounts: readonly StaffAccountDto[];
};

type TransactionResult = {
  readonly auditEventId: string;
  readonly transactions: readonly StaffTransactionDto[];
  readonly journey?: StaffJourneyResponse;
};

type ApprovalResult = {
  readonly approvals: readonly OperatorApproval[];
  readonly selected?: OperatorApproval;
  readonly journey?: StaffJourneyResponse;
  readonly action?: {
    readonly status: string;
    readonly approvalId: string;
    readonly businessType: string;
    readonly executed?: boolean;
    readonly rejected?: boolean;
    readonly auditEventId?: string | null;
    readonly journeyId?: string | null;
    readonly fdsStatus?: string | null;
    readonly ledgerTransactionId?: string | null;
    readonly ledgerMutation?: string;
  };
};

type FdsReviewResult = {
  readonly cases: readonly FdsCaseDto[];
  readonly selected?: FdsCaseDto;
  readonly journey?: StaffJourneyResponse;
  readonly approvalId?: string | null;
  readonly lastAction?: string;
};

type CommandResult = {
  readonly commandType: string;
  readonly requestId?: string;
  readonly approvalId?: string | null;
  readonly status?: string;
  readonly idempotencyKey: string;
  readonly makerActor: string;
  readonly checkerActor: string;
  readonly ledgerMutation: string;
  readonly businessReferenceId?: string;
};

type CallCenterResult = {
  readonly auditEventId?: string;
  readonly customers?: readonly CallCenterCustomerSummaryDto[];
  readonly interaction?: CallCenterInteractionDto;
  readonly history?: readonly { readonly interactionId: string; readonly status: string; readonly channel: string; readonly startedAt: string }[];
  readonly lastAction?: string;
  readonly approvalId?: string | null;
  readonly redaction?: string;
};

function TerminalApiClientProvider({ children }: { readonly children: ReactNode }) {
  return (
    <div className="api-workbench">
      <div className={`api-connection-strip ${apiReady ? "is-ready" : "is-offline"}`} data-testid="terminal-api-client-provider">
        <span>
          <MaterialIcon name="language" /> Same-origin BFF
        </span>
        <span>HttpOnly opaque session</span>
        <span>browser bearer 없음</span>
      </div>
      {children}
    </div>
  );
}

function staffClient(actor: StaffActor) {
  void actor;
  return createStaffApiClient({
    baseUrl: window.location.origin
  });
}

function callCenterClient() {
  return createCallCenterApiClient({
    baseUrl: window.location.origin
  });
}

function riskClient(actor: "reviewer" | "manager" = "reviewer") {
  void actor;
  return createRiskApiClient({
    baseUrl: window.location.origin
  });
}

function ReasonRequiredPanel({ reason, onReasonChange }: { readonly reason: string; readonly onReasonChange: (value: string) => void }) {
  return (
    <label className="reason-required-panel">
      <span>업무사유</span>
      <input aria-label="업무사유" value={reason} onChange={(event) => onReasonChange(event.target.value)} placeholder="reason-required audit" />
    </label>
  );
}

function StructuredErrorPanel({ error }: { readonly error: unknown }) {
  if (!error) {
    return null;
  }
  const structured = structuredError(error);
  return (
    <section className="structured-error-panel" aria-label="structured error">
      <strong>{structured.title}</strong>
      <dl>
        <div>
          <dt>HTTP</dt>
          <dd>{structured.status}</dd>
        </div>
        <div>
          <dt>code</dt>
          <dd>{structured.code}</dd>
        </div>
        <div>
          <dt>message</dt>
          <dd>{structured.message}</dd>
        </div>
        <div>
          <dt>audit</dt>
          <dd>{structured.auditEventId ?? "-"}</dd>
        </div>
      </dl>
    </section>
  );
}

function ApprovalActionPanel({
  selected,
  onApprove,
  onReject,
  disabled
}: {
  readonly selected?: OperatorApproval;
  readonly onApprove: () => void;
  readonly onReject: () => void;
  readonly disabled?: boolean;
}) {
  return (
    <div className="approval-action-panel">
      <dl>
        <div>
          <dt>maker</dt>
          <dd>{selected?.requestedBy ?? "-"}</dd>
        </div>
        <div>
          <dt>checker</dt>
          <dd>manager01</dd>
        </div>
        <div>
          <dt>approvalId</dt>
          <dd>{selected?.approvalId ?? "-"}</dd>
        </div>
        <div>
          <dt>self approval</dt>
          <dd>{selected?.requestedBy === "manager01" ? "차단 대상" : "분리"}</dd>
        </div>
      </dl>
      <div>
        <button type="button" onClick={onApprove} disabled={!selected || disabled}>
          승인
        </button>
        <button type="button" onClick={onReject} disabled={!selected || disabled}>
          반려
        </button>
      </div>
    </div>
  );
}

function TimelinePanel({ entries }: { readonly entries: readonly StaffWorkflowTimelineEntryDto[] }) {
  return (
    <Panel title="workflow/audit/approval timeline" className="api-result-panel">
      <DataTable
        columns={[
          { key: "occurredAt", label: "시각", width: "170px" },
          { key: "sourceType", label: "source", width: "120px" },
          { key: "eventType", label: "event", width: "180px" },
          { key: "status", label: "상태", width: "120px" },
          { key: "actor", label: "actor", width: "170px" },
          { key: "reference", label: "businessReferenceId" },
          { key: "reason", label: "사유", width: "220px" }
        ]}
        rows={entries.map((entry) => ({
          occurredAt: formatDateTimeValue(entry.occurredAt),
          sourceType: entry.sourceType,
          eventType: entry.eventType,
          status: entry.status ?? "-",
          actor: [entry.actorRole, entry.actorId].filter(Boolean).join(" / ") || "-",
          reference: entry.businessReferenceId,
          reason: entry.reason ?? "-"
        }))}
        minRows={4}
      />
    </Panel>
  );
}

function JourneyPanel({ journey }: { readonly journey?: StaffJourneyResponse }) {
  return (
    <Panel title="cross-channel journey" className="api-result-panel">
      <KeyValueGrid
        rows={[
          ["journeyId", journey?.item.journeyId ?? "-"],
          ["journeyStatus", journey?.item.status ?? "-"],
          ["journeyType", journey?.item.journeyType ?? "-"],
          ["customerId", journey?.item.customerId ?? "-"],
          ["auditEventId", journey?.auditEventId ?? "-"]
        ]}
      />
      <DataTable
        columns={[
          { key: "sequence", label: "순서", width: "64px" },
          { key: "eventType", label: "event", width: "190px" },
          { key: "status", label: "상태", width: "130px" },
          { key: "reference", label: "참조", width: "250px" },
          { key: "actor", label: "actor", width: "180px" },
          { key: "reason", label: "사유" }
        ]}
        rows={journey?.item.events.map((event) => ({
          sequence: String(event.sequence),
          eventType: event.eventType,
          status: event.status,
          reference: [event.sourceReferenceType, event.sourceReferenceId].filter(Boolean).join(" / ") || "-",
          actor: event.actorRole ?? "-",
          reason: event.reason ?? "-"
        })) ?? []}
        minRows={4}
      />
    </Panel>
  );
}

export function StaffCustomerInquiryScreen() {
  const [query, setQuery] = useState("SYN");
  const [customerId, setCustomerId] = useState("SYN-CUS-001");
  const [reason, setReason] = useState("통합 단말 CUS101 고객 요청 응대");
  const [state, setState] = useState<ApiState<CustomerResult>>({ status: "idle" });

  const runSearch = async () => {
    setState({ status: "running", message: "CUS101 조회 중" });
    try {
      // Both reads append to the global audit hash chain. Keep them ordered so
      // SERIALIZABLE audit writes cannot race each other in the same screen action.
      const search = await staffClient("staff").staffCustomerSearch(query, reason);
      const detail = await staffClient("staff").staffCustomerDetail(customerId, reason);
      setState({ status: "loaded", data: { auditEventId: detail.auditEventId || search.auditEventId, customers: search.items, detail: detail.item } });
    } catch (error) {
      setState({ status: "failed", error });
    }
  };

  return (
    <TerminalApiClientProvider>
      <ApiForm title="CUS101 고객 상세 조회" icon="person_search">
        <ApiTextField label="검색어" value={query} onChange={setQuery} />
        <ApiTextField label="고객번호" value={customerId} onChange={setCustomerId} />
        <ReasonRequiredPanel reason={reason} onReasonChange={setReason} />
        <ApiButton onClick={runSearch} loading={state.status === "running"}>
          조회
        </ApiButton>
      </ApiForm>
      <StructuredErrorPanel error={state.status === "failed" ? state.error : null} />
      <Panel title="고객 검색 결과" className="api-result-panel">
        <DataTable
          columns={[
            { key: "customerId", label: "고객번호", width: "150px" },
            { key: "maskedName", label: "고객명", width: "120px" },
            { key: "maskedPhone", label: "전화", width: "150px" },
            { key: "maskedAddress", label: "주소" },
            { key: "grade", label: "등급", width: "110px" },
            { key: "risk", label: "위험", width: "110px" }
          ]}
          rows={state.status === "loaded" ? state.data.customers.map(customerRow) : []}
          minRows={4}
        />
      </Panel>
      <Panel title="마스킹 상세 및 감사" className="api-result-panel">
        <KeyValueGrid
          rows={[
            ["auditEventId", state.status === "loaded" ? state.data.auditEventId : "-"],
            ["customerId", state.status === "loaded" ? state.data.detail?.customerId ?? "-" : "-"],
            ["piiExposure", state.status === "loaded" ? state.data.detail?.piiExposure ?? "-" : "-"],
            ["maskedName", state.status === "loaded" ? state.data.detail?.maskedName ?? "-" : "-"],
            ["maskedPhone", state.status === "loaded" ? state.data.detail?.maskedPhone ?? "-" : "-"],
            ["riskGrade", state.status === "loaded" ? state.data.detail?.riskGrade ?? "-" : "-"]
          ]}
        />
      </Panel>
    </TerminalApiClientProvider>
  );
}

export function StaffAccountInquiryScreen() {
  const [customerId, setCustomerId] = useState("SYN-CUS-001");
  const [accountId, setAccountId] = useState("");
  const [reason, setReason] = useState("통합 단말 ACC101 고객 계좌 확인");
  const [state, setState] = useState<ApiState<AccountResult>>({ status: "idle" });

  const runSearch = async () => {
    setState({ status: "running", message: "ACC101 조회 중" });
    try {
      const response = await staffClient("staff").staffAccountSearch({ customerId, accountId, reason });
      setState({ status: "loaded", data: { auditEventId: response.auditEventId, accounts: response.items } });
    } catch (error) {
      setState({ status: "failed", error });
    }
  };

  return (
    <TerminalApiClientProvider>
      <ApiForm title="ACC101 계좌 조회" icon="account_balance_wallet">
        <ApiTextField label="고객번호" value={customerId} onChange={setCustomerId} />
        <ApiTextField label="계좌ID" value={accountId} onChange={setAccountId} />
        <ReasonRequiredPanel reason={reason} onReasonChange={setReason} />
        <ApiButton onClick={runSearch} loading={state.status === "running"}>
          조회
        </ApiButton>
      </ApiForm>
      <StructuredErrorPanel error={state.status === "failed" ? state.error : null} />
      <Panel title="계좌 결과" className="api-result-panel">
        <DataTable columns={accountColumns} rows={state.status === "loaded" ? state.data.accounts.map(accountRow) : []} minRows={6} />
      </Panel>
      <KeyValuePanel title="감사" rows={[["auditEventId", state.status === "loaded" ? state.data.auditEventId : "-"], ["마스킹", "maskedAccountNo only"]]} />
    </TerminalApiClientProvider>
  );
}

export function StaffTransactionInquiryScreen() {
  const [accountId, setAccountId] = useState("ACC-RUNTIME-FROM");
  const [journeyId, setJourneyId] = useState("");
  const [reason, setReason] = useState("통합 단말 TX101 거래 확인");
  const [state, setState] = useState<ApiState<TransactionResult>>({ status: "idle" });

  const runSearch = async () => {
    setState({ status: "running", message: "TX101 조회 중" });
    try {
      const client = staffClient("staff");
      const [response, journey] = await Promise.all([
        client.staffTransactionSearch({ accountId, reason }),
        journeyId ? client.staffJourney(journeyId, reason) : Promise.resolve(undefined)
      ]);
      setState({ status: "loaded", data: { auditEventId: response.auditEventId, transactions: response.items, journey } });
    } catch (error) {
      setState({ status: "failed", error });
    }
  };

  return (
    <TerminalApiClientProvider>
      <ApiForm title="TX101 거래 조회" icon="receipt_long">
        <ApiTextField label="계좌ID" value={accountId} onChange={setAccountId} />
        <ApiTextField label="journeyId" value={journeyId} onChange={setJourneyId} />
        <ReasonRequiredPanel reason={reason} onReasonChange={setReason} />
        <ApiButton onClick={runSearch} loading={state.status === "running"}>
          조회
        </ApiButton>
      </ApiForm>
      <StructuredErrorPanel error={state.status === "failed" ? state.error : null} />
      <Panel title="거래 결과" className="api-result-panel">
        <DataTable
          columns={[
            { key: "ledgerTransactionId", label: "거래ID", width: "170px" },
            { key: "type", label: "유형", width: "110px" },
            { key: "status", label: "상태", width: "90px" },
            { key: "date", label: "영업일", width: "110px" },
            { key: "account", label: "계좌ID", width: "150px" },
            { key: "amount", label: "금액", width: "120px" },
            { key: "channel", label: "채널", width: "120px" },
            { key: "reason", label: "사유" }
          ]}
          rows={state.status === "loaded" ? state.data.transactions.map(transactionRow) : []}
          minRows={6}
        />
      </Panel>
      <JourneyPanel journey={state.status === "loaded" ? state.data.journey : undefined} />
      <KeyValuePanel title="감사" rows={[["auditEventId", state.status === "loaded" ? state.data.auditEventId : "-"]]} />
    </TerminalApiClientProvider>
  );
}

export function FdsReviewScreen() {
  const [caseId, setCaseId] = useState("");
  const [reason, setReason] = useState("통합 단말 FDS201 보류 이체 심사");
  const [state, setState] = useState<ApiState<FdsReviewResult>>({ status: "idle" });

  const runAction = async (action: "load" | "assign" | "release" | "block") => {
    setState({ status: "running", message: `FDS201 ${action} 처리 중` });
    try {
      const client = riskClient();
      let selected: FdsCaseDto | undefined;
      let approvalId: string | null | undefined;
      if (action === "assign") {
        selected = await client.assignFdsCase(caseId, {
          actorId: "risk01",
          actorRole: "FDS_REVIEWER",
          owner: "risk01",
          reason
        });
      } else if (action === "release") {
        const response = await client.requestFdsRelease(caseId, {
          actorId: "risk01",
          requestedByRole: "FDS_REVIEWER",
          reason
        });
        selected = response.item;
        approvalId = response.approval.approvalId;
      } else if (action === "block") {
        const response = await client.requestFdsBlock(caseId, {
          actorId: "risk01",
          requestedByRole: "FDS_REVIEWER",
          reason
        });
        selected = response.item;
        approvalId = response.approval.approvalId;
      }
      const cases = await client.fdsCases();
      selected = selected ?? cases.find((item) => item.caseId === caseId) ?? cases[0];
      if (selected) {
        setCaseId(selected.caseId);
      }
      const journey = selected?.journeyId ? await client.staffJourney(selected.journeyId, reason) : undefined;
      setState({ status: "loaded", data: { cases, selected, journey, approvalId, lastAction: action } });
    } catch (error) {
      setState({ status: "failed", error });
    }
  };

  const data = state.status === "loaded" ? state.data : undefined;

  return (
    <TerminalApiClientProvider>
      <ApiForm title="FDS201 보류 이체 심사" icon="manage_search">
        <ApiTextField label="caseId" value={caseId} onChange={setCaseId} />
        <ReasonRequiredPanel reason={reason} onReasonChange={setReason} />
        <div className="api-button-row">
          <ApiButton onClick={() => runAction("load")} loading={state.status === "running"}>목록/상세</ApiButton>
          <ApiButton onClick={() => runAction("assign")} loading={state.status === "running"}>담당 지정</ApiButton>
          <ApiButton onClick={() => runAction("release")} loading={state.status === "running"}>release 승인요청</ApiButton>
          <ApiButton onClick={() => runAction("block")} loading={state.status === "running"}>block 승인요청</ApiButton>
        </div>
      </ApiForm>
      <StructuredErrorPanel error={state.status === "failed" ? state.error : null} />
      <Panel title="FDS 보류 사건" className="api-result-panel">
        <DataTable
          columns={[
            { key: "caseId", label: "caseId", width: "180px" },
            { key: "journeyId", label: "journeyId", width: "180px" },
            { key: "status", label: "사건상태", width: "140px" },
            { key: "transferStatus", label: "이체상태", width: "120px" },
            { key: "riskScore", label: "risk", width: "80px" },
            { key: "owner", label: "담당자", width: "150px" },
            { key: "approvalId", label: "approvalId" }
          ]}
          rows={data?.cases.map((item) => ({
            caseId: item.caseId,
            journeyId: item.journeyId ?? "-",
            status: <span className="state-pill blue">{item.status}</span>,
            transferStatus: item.transferStatus ?? "-",
            riskScore: String(item.riskScore),
            owner: item.owner ?? "-",
            approvalId: item.approvalId ?? "-"
          })) ?? []}
          minRows={5}
        />
      </Panel>
      <KeyValuePanel
        title="선택 사건 통제"
        rows={[
          ["lastAction", data?.lastAction ?? "-"],
          ["caseId", data?.selected?.caseId ?? "-"],
          ["journeyId", data?.selected?.journeyId ?? "-"],
          ["transferReferenceId", data?.selected?.transferReferenceId ?? "-"],
          ["approvalId", data?.approvalId ?? data?.selected?.approvalId ?? "-"],
          ["alerts", data?.selected?.alerts.map((alert) => alert.ruleId).join(", ") ?? "-"],
          ["ledger mutation", data?.selected?.transferStatus === "POSTED" ? "checker 승인 후 balanced posting 1건" : "없음"],
          ["maker/checker", "risk01 / manager01"]
        ]}
      />
      <JourneyPanel journey={data?.journey} />
    </TerminalApiClientProvider>
  );
}

export function ApprovalInboxScreen() {
  const [approvalId, setApprovalId] = useState("");
  const [rejectReason, setRejectReason] = useState("통합 단말 APR101 반려 검토");
  const [state, setState] = useState<ApiState<ApprovalResult>>({ status: "idle" });

  const journeyForApproval = async (approval?: OperatorApproval) => {
    if (!approval || (approval.businessType !== "FDS_RELEASE" && approval.businessType !== "FDS_BLOCK")) {
      return undefined;
    }
    const cases = await riskClient("manager").fdsCases();
    const fdsCase = cases.find((item) => item.caseId === approval.businessReferenceId);
    return fdsCase?.journeyId ? staffClient("manager").staffJourney(fdsCase.journeyId, "APR101 FDS 승인 여정 확인") : undefined;
  };

  const loadApprovals = async () => {
    setState({ status: "running", message: "APR101 승인함 조회 중" });
    try {
      const approvals = await staffClient("manager").staffApprovals();
      const selected = approvalId ? approvals.find((approval) => approval.approvalId === approvalId) : approvals[0];
      setApprovalId(selected?.approvalId ?? approvalId);
      const journey = await journeyForApproval(selected);
      setState({ status: "loaded", data: { approvals, selected, journey } });
    } catch (error) {
      setState({ status: "failed", error });
    }
  };

  const selectApproval = async () => {
    setState({ status: "running", message: "승인 상세 조회 중" });
    try {
      const [approvals, selected] = await Promise.all([staffClient("manager").staffApprovals(), staffClient("manager").staffApproval(approvalId)]);
      const journey = await journeyForApproval(selected);
      setState({ status: "loaded", data: { approvals, selected, journey } });
    } catch (error) {
      setState({ status: "failed", error });
    }
  };

  const actOnApproval = async (action: "approve" | "reject") => {
    const selected = state.status === "loaded" ? state.data.selected : undefined;
    if (!selected) {
      return;
    }
    setState({ status: "running", message: action === "approve" ? "승인 실행 중" : "반려 실행 중" });
    try {
      const client = staffClient("manager");
      const response =
        action === "approve"
          ? await client.approveStaffApproval(selected.approvalId, { approvedBy: "manager01", approvedByRole: "BRANCH_MANAGER", screenId: "APR101" })
          : await client.rejectStaffApproval(selected.approvalId, { rejectedBy: "manager01", rejectedByRole: "BRANCH_MANAGER", rejectReason, screenId: "APR101" });
      const approvals = await client.staffApprovals();
      const fdsCase = response.fdsCase ?? undefined;
      const ledgerTransaction = "ledgerTransaction" in response ? response.ledgerTransaction : undefined;
      const journey = fdsCase?.journeyId
        ? await client.staffJourney(fdsCase.journeyId, "APR101 checker 결정 결과 확인")
        : await journeyForApproval(response.item);
      setState({
        status: "loaded",
        data: {
          approvals,
          selected: response.item,
          journey,
          action: {
            status: response.item.status,
            approvalId: response.item.approvalId,
            businessType: response.item.businessType,
            executed: "executed" in response ? response.executed : undefined,
            rejected: "rejected" in response ? response.rejected : undefined,
            auditEventId: response.item.auditEventId,
            journeyId: fdsCase?.journeyId,
            fdsStatus: fdsCase?.status,
            ledgerTransactionId: ledgerTransaction?.value.id,
            ledgerMutation: ledgerTransaction
              ? `balanced double-entry 1건 / ${ledgerTransaction.replayed ? "idempotent replay" : "new posting"}`
              : "원장 변경 없음"
          }
        }
      });
    } catch (error) {
      setState({ status: "failed", error });
    }
  };

  const selected = state.status === "loaded" ? state.data.selected : undefined;

  return (
    <TerminalApiClientProvider>
      <ApiForm title="APR101 승인함" icon="task_alt">
        <ApiTextField label="approvalId" value={approvalId} onChange={setApprovalId} />
        <ApiTextField label="반려사유" value={rejectReason} onChange={setRejectReason} />
        <ApiButton onClick={loadApprovals} loading={state.status === "running"}>
          목록
        </ApiButton>
        <ApiButton onClick={selectApproval} loading={state.status === "running"}>
          상세
        </ApiButton>
      </ApiForm>
      <StructuredErrorPanel error={state.status === "failed" ? state.error : null} />
      <ApprovalActionPanel selected={selected} onApprove={() => void actOnApproval("approve")} onReject={() => void actOnApproval("reject")} disabled={state.status === "running"} />
      <Panel title="승인함 목록" className="api-result-panel">
        <DataTable columns={approvalColumns} rows={state.status === "loaded" ? state.data.approvals.map(approvalRow) : []} minRows={5} />
      </Panel>
      <KeyValuePanel
        title="선택 승인"
        rows={[
          ["approvalId", selected?.approvalId ?? "-"],
          ["businessType", selected?.businessType ?? "-"],
          ["businessReferenceId", selected?.businessReferenceId ?? "-"],
          ["requestReason", selected?.requestReason ?? "-"],
          ["status", selected?.status ?? "-"],
          ["lastAction", state.status === "loaded" && state.data.action ? `${state.data.action.status} / ${state.data.action.auditEventId ?? "-"}` : "-"],
          ["journeyId", state.status === "loaded" ? state.data.action?.journeyId ?? state.data.journey?.item.journeyId ?? "-" : "-"],
          ["FDS status", state.status === "loaded" ? state.data.action?.fdsStatus ?? "-" : "-"],
          ["ledgerTransactionId", state.status === "loaded" ? state.data.action?.ledgerTransactionId ?? "-" : "-"],
          ["ledger mutation", state.status === "loaded" ? state.data.action?.ledgerMutation ?? "승인 결정 전 없음" : "-"]
        ]}
      />
      <JourneyPanel journey={state.status === "loaded" ? state.data.journey : undefined} />
    </TerminalApiClientProvider>
  );
}

export function OperationalRetryQueueScreen() {
  const [status, setStatus] = useState("");
  const [reason, setReason] = useState("통합 단말 WRK002 운영 예외 점검");
  const [state, setState] = useState<ApiState<{ readonly auditEventId: string; readonly items: readonly OperationalRetryQueueItemDto[] }>>({ status: "idle" });

  const runSearch = async () => {
    setState({ status: "running", message: "WRK002 조회 중" });
    try {
      const response = await staffClient("staff").staffOperationalRetryQueue(reason, status || undefined);
      setState({ status: "loaded", data: { auditEventId: response.auditEventId, items: response.items } });
    } catch (error) {
      setState({ status: "failed", error });
    }
  };

  return (
    <TerminalApiClientProvider>
      <ApiForm title="WRK002 운영 retry queue" icon="sync_alt">
        <ApiTextField label="status" value={status} onChange={setStatus} />
        <ReasonRequiredPanel reason={reason} onReasonChange={setReason} />
        <ApiButton onClick={runSearch} loading={state.status === "running"}>
          조회
        </ApiButton>
      </ApiForm>
      <StructuredErrorPanel error={state.status === "failed" ? state.error : null} />
      <Panel title="outbox retry/dead-letter 상태" className="api-result-panel">
        <DataTable
          columns={[
            { key: "eventId", label: "eventId", width: "170px" },
            { key: "aggregate", label: "aggregate", width: "170px" },
            { key: "eventType", label: "eventType", width: "170px" },
            { key: "status", label: "상태", width: "110px" },
            { key: "retry", label: "retry", width: "80px" },
            { key: "nextRetryAt", label: "nextRetryAt", width: "170px" },
            { key: "eligible", label: "eligible", width: "90px" },
            { key: "error", label: "error" }
          ]}
          rows={state.status === "loaded" ? state.data.items.map(retryRow) : []}
          minRows={6}
        />
      </Panel>
      <KeyValuePanel title="감사" rows={[["auditEventId", state.status === "loaded" ? state.data.auditEventId : "-"]]} />
    </TerminalApiClientProvider>
  );
}

export function WorkflowTimelineScreen() {
  const [businessReferenceId, setBusinessReferenceId] = useState("TX-SYN-CORR-001");
  const [journeyId, setJourneyId] = useState("");
  const [reason, setReason] = useState("통합 단말 WRK003 workflow timeline 확인");
  const [state, setState] = useState<ApiState<{ readonly auditEventId: string; readonly entries: readonly StaffWorkflowTimelineEntryDto[]; readonly journey?: StaffJourneyResponse }>>({ status: "idle" });

  const runSearch = async () => {
    setState({ status: "running", message: "WRK003 조회 중" });
    try {
      const client = staffClient("staff");
      const [response, journey] = await Promise.all([
        client.staffWorkflowTimeline(businessReferenceId, reason),
        journeyId ? client.staffJourney(journeyId, reason) : Promise.resolve(undefined)
      ]);
      setState({ status: "loaded", data: { auditEventId: response.auditEventId, entries: response.items, journey } });
    } catch (error) {
      setState({ status: "failed", error });
    }
  };

  return (
    <TerminalApiClientProvider>
      <ApiForm title="WRK003 workflow timeline" icon="history">
        <ApiTextField label="businessReferenceId" value={businessReferenceId} onChange={setBusinessReferenceId} />
        <ApiTextField label="journeyId" value={journeyId} onChange={setJourneyId} />
        <ReasonRequiredPanel reason={reason} onReasonChange={setReason} />
        <ApiButton onClick={runSearch} loading={state.status === "running"}>
          조회
        </ApiButton>
      </ApiForm>
      <StructuredErrorPanel error={state.status === "failed" ? state.error : null} />
      <TimelinePanel entries={state.status === "loaded" ? state.data.entries : []} />
      <JourneyPanel journey={state.status === "loaded" ? state.data.journey : undefined} />
      <KeyValuePanel title="감사" rows={[["auditEventId", state.status === "loaded" ? state.data.auditEventId : "-"]]} />
    </TerminalApiClientProvider>
  );
}

export function CommandWorkbenchScreen() {
  const [commandType, setCommandType] = useState("hold");
  const [customerId, setCustomerId] = useState("SYN-CUS-001");
  const [accountId, setAccountId] = useState("ACC-RUNTIME-FROM");
  const [transactionId, setTransactionId] = useState("TX-RUNTIME-001");
  const [amountMinor, setAmountMinor] = useState("1000");
  const [reason, setReason] = useState("통합 단말 고위험 command 요청");
  const [idempotencyKey, setIdempotencyKey] = useState(() => `terminal-${Date.now()}`);
  const [state, setState] = useState<ApiState<CommandResult>>({ status: "idle" });

  const executeCommand = async () => {
    setState({ status: "running", message: "고위험 command 실행 중" });
    try {
      const client = staffClient("staff");
      const baseCommand = {
        requestedBy: "branch01",
        requestedByRole: "BRANCH_STAFF",
        reason,
        reasonCode: "CUSTOMER_REQUEST",
        description: "iWorks integrated terminal command workbench",
        idempotencyKey
      };
      const response =
        commandType === "release"
          ? await client.requestAccountHoldRelease(accountId, { ...baseCommand, holdAmountMinor: numericMinor(amountMinor) })
          : commandType === "limit"
            ? await client.requestTransferLimitChange(accountId, { ...baseCommand, dailyTransferLimitMinor: numericMinor(amountMinor), singleTransferLimitMinor: numericMinor(amountMinor) })
            : commandType === "kyc"
              ? await client.requestCustomerKycReview(customerId, { ...baseCommand, reviewTrigger: "STAFF_REVIEW" })
              : commandType === "fee"
                ? await client.requestFeeWaiver(accountId, { ...baseCommand, feeCode: "SYN-FEE", waivedAmountMinor: numericMinor(amountMinor), currency: "KRW", targetTransactionId: transactionId })
                : commandType === "correction"
                  ? await client.requestTransactionCorrection(transactionId, { ...baseCommand, correctionType: "REVERSAL_ADJUSTMENT", targetAccountId: accountId, businessDate: todayIsoDate() })
                  : await client.requestAccountHold(accountId, { ...baseCommand, holdAmountMinor: numericMinor(amountMinor) });
      const item = "item" in response ? response.item : undefined;
      setState({
        status: "loaded",
        data: {
          commandType,
          requestId: item && "requestId" in item ? String(item.requestId) : undefined,
          approvalId: response.approval?.approvalId,
          status: item && "status" in item ? String(item.status) : undefined,
          idempotencyKey,
          makerActor: "branch01",
          checkerActor: "manager01",
          ledgerMutation: commandType === "fee" || commandType === "correction" ? "승인 후 balanced adjustment/reversal" : "request 단계 mutation 없음",
          businessReferenceId: item && "businessReferenceId" in item ? String(item.businessReferenceId) : undefined
        }
      });
      setIdempotencyKey(`terminal-${Date.now()}`);
    } catch (error) {
      setState({ status: "failed", error });
    }
  };

  return (
    <TerminalApiClientProvider>
      <ApiForm title="CMD101 고위험 command workbench" icon="edit_square">
        <label className="api-field">
          <span>command</span>
          <select aria-label="command" value={commandType} onChange={(event) => setCommandType(event.target.value)}>
            <option value="hold">계좌 hold</option>
            <option value="release">계좌 release</option>
            <option value="limit">한도 변경</option>
            <option value="kyc">KYC review</option>
            <option value="fee">fee waiver</option>
            <option value="correction">거래 정정</option>
          </select>
        </label>
        <ApiTextField label="고객번호" value={customerId} onChange={setCustomerId} />
        <ApiTextField label="계좌ID" value={accountId} onChange={setAccountId} />
        <ApiTextField label="거래ID" value={transactionId} onChange={setTransactionId} />
        <ApiTextField label="amountMinor" value={amountMinor} onChange={setAmountMinor} />
        <ApiTextField label="idempotencyKey" value={idempotencyKey} onChange={setIdempotencyKey} />
        <ReasonRequiredPanel reason={reason} onReasonChange={setReason} />
        <ApiButton onClick={executeCommand} loading={state.status === "running"}>
          요청
        </ApiButton>
      </ApiForm>
      <StructuredErrorPanel error={state.status === "failed" ? state.error : null} />
      <KeyValuePanel
        title="command 결과"
        rows={[
          ["command", state.status === "loaded" ? state.data.commandType : commandType],
          ["requestId", state.status === "loaded" ? state.data.requestId ?? "-" : "-"],
          ["approvalId", state.status === "loaded" ? state.data.approvalId ?? "-" : "-"],
          ["businessReferenceId", state.status === "loaded" ? state.data.businessReferenceId ?? "-" : "-"],
          ["idempotencyKey", state.status === "loaded" ? state.data.idempotencyKey : idempotencyKey],
          ["maker/checker", state.status === "loaded" ? `${state.data.makerActor} / ${state.data.checkerActor}` : "branch01 / manager01"],
          ["ledger mutation", state.status === "loaded" ? state.data.ledgerMutation : "승인 전 없음"],
          ["status", state.status === "loaded" ? state.data.status ?? "-" : "-"]
        ]}
      />
    </TerminalApiClientProvider>
  );
}

export function CallCenterWorkspaceScreen() {
  const [query, setQuery] = useState("SYN");
  const [customerId, setCustomerId] = useState("SYN-CUS-001");
  const [interactionId, setInteractionId] = useState("");
  const [reason, setReason] = useState("통합 단말 CALL 업무 상담 응대");
  const [note, setNote] = useState("고객 문의 접수. 주민번호 등 민감정보는 저장하지 않음.");
  const [state, setState] = useState<ApiState<CallCenterResult>>({ status: "idle" });

  const mergeLoaded = (patch: CallCenterResult) => {
    setState((current) => ({ status: "loaded", data: { ...(current.status === "loaded" ? current.data : {}), ...patch } }));
  };

  const runCallAction = async (action: "search" | "start" | "detail" | "note" | "task" | "history" | "escalate" | "close") => {
    try {
      const agent = callCenterClient();
      if (action === "search") {
        const response = await agent.searchCallCenterCustomers(query, reason);
        const first = response.items[0];
        if (first) {
          setCustomerId(first.customerId);
        }
        mergeLoaded({ auditEventId: response.auditEventId, customers: response.items, lastAction: "CALL101" });
        return;
      }
      if (action === "start") {
        const response = await agent.startCallCenterInteraction({
          customerId,
          channel: "BRANCH_TERMINAL",
          contactReasonCode: "GENERAL_INQUIRY",
          requestedBy: "call-agent01",
          requestedByRole: "CALL_CENTER_AGENT",
          assignedTo: "call-agent01",
          reason
        });
        setInteractionId(response.item.interactionId);
        mergeLoaded({ interaction: response.item, auditEventId: response.item.auditEventId, lastAction: "CALL102" });
        return;
      }
      if (action === "detail") {
        const response = await agent.callCenterInteraction(interactionId, reason);
        mergeLoaded({ interaction: response.item, auditEventId: response.item.auditEventId, lastAction: "CALL102 detail" });
        return;
      }
      if (action === "note") {
        const response = await agent.addCallCenterNote(interactionId, { requestedBy: "call-agent01", requestedByRole: "CALL_CENTER_AGENT", reason, noteBody: note });
        mergeLoaded({ interaction: response.item, auditEventId: response.note.auditEventId, redaction: `${response.note.redactionApplied ? "applied" : "none"} / ${response.note.piiPatternCount}`, lastAction: "CALL103" });
        return;
      }
      if (action === "task") {
        const response = await agent.createCallCenterAftercallTask(interactionId, {
          requestedBy: "call-agent01",
          requestedByRole: "CALL_CENTER_AGENT",
          reason,
          taskType: "FOLLOW_UP",
          assignedTo: "call-agent01",
          dueAt: new Date(Date.now() + 86_400_000).toISOString()
        });
        mergeLoaded({ interaction: response.item, auditEventId: response.task.auditEventId, lastAction: "CALL104" });
        return;
      }
      if (action === "history") {
        const response = await agent.callCenterCustomerHistory(customerId, reason);
        mergeLoaded({ auditEventId: response.auditEventId, history: response.items, lastAction: "CALL105" });
        return;
      }
      if (action === "escalate") {
        const response = await agent.escalateCallCenterInteraction(interactionId, {
          requestedBy: "call-agent01",
          requestedByRole: "CALL_CENTER_AGENT",
          reason,
          escalationType: "COMPLAINT",
          complaintCategory: "SERVICE",
          complaintDescription: "통합 단말 상담 escalation",
          metadata: { terminalCode: "CALL106" }
        });
        mergeLoaded({ interaction: response.item, approvalId: response.escalation.approvalId ?? response.approval?.approvalId, auditEventId: response.escalation.auditEventId, lastAction: "CALL106" });
        return;
      }
      const response = await agent.closeCallCenterInteraction(interactionId, { requestedBy: "call-agent01", requestedByRole: "CALL_CENTER_AGENT", reason });
      mergeLoaded({ interaction: response.item, auditEventId: response.item.auditEventId, lastAction: "CALL close" });
    } catch (error) {
      setState({ status: "failed", error });
    }
  };

  const data = state.status === "loaded" ? state.data : undefined;

  return (
    <TerminalApiClientProvider>
      <ApiForm title="CALL101..CALL106 상담센터 compact 업무" icon="support_agent">
        <ApiTextField label="검색어" value={query} onChange={setQuery} />
        <ApiTextField label="고객번호" value={customerId} onChange={setCustomerId} />
        <ApiTextField label="interactionId" value={interactionId} onChange={setInteractionId} />
        <ApiTextField label="메모" value={note} onChange={setNote} />
        <ReasonRequiredPanel reason={reason} onReasonChange={setReason} />
        <div className="api-button-row">
          {(["search", "start", "detail", "note", "task", "history", "escalate", "close"] as const).map((action) => (
            <ApiButton key={action} onClick={() => runCallAction(action)} loading={state.status === "running"}>
              {callActionLabel(action)}
            </ApiButton>
          ))}
        </div>
      </ApiForm>
      <StructuredErrorPanel error={state.status === "failed" ? state.error : null} />
      <Panel title="CALL101 고객 검색" className="api-result-panel">
        <DataTable
          columns={[
            { key: "customerId", label: "고객번호", width: "150px" },
            { key: "maskedName", label: "고객명", width: "120px" },
            { key: "maskedPhone", label: "전화", width: "150px" },
            { key: "grade", label: "등급", width: "100px" },
            { key: "risk", label: "위험", width: "100px" }
          ]}
          rows={data?.customers?.map((customer) => ({ customerId: customer.customerId, maskedName: customer.maskedName, maskedPhone: customer.maskedPhone ?? "-", grade: customer.customerGrade, risk: customer.riskGrade })) ?? []}
          minRows={3}
        />
      </Panel>
      <KeyValuePanel
        title="상담 interaction"
        rows={[
          ["lastAction", data?.lastAction ?? "-"],
          ["auditEventId", data?.auditEventId ?? "-"],
          ["interactionId", data?.interaction?.interactionId ?? "-"],
          ["status", data?.interaction?.status ?? "-"],
          ["notes", data?.interaction ? String(data.interaction.notes.length) : "-"],
          ["aftercallTasks", data?.interaction ? String(data.interaction.aftercallTasks.length) : "-"],
          ["approvalId", data?.approvalId ?? "-"],
          ["redaction", data?.redaction ?? "-"]
        ]}
      />
      <Panel title="CALL105 상담 이력" className="api-result-panel">
        <DataTable
          columns={[
            { key: "interactionId", label: "interactionId", width: "190px" },
            { key: "status", label: "상태", width: "110px" },
            { key: "channel", label: "채널", width: "140px" },
            { key: "startedAt", label: "시작시각" }
          ]}
          rows={data?.history?.map((item) => ({ interactionId: item.interactionId, status: item.status, channel: item.channel, startedAt: formatDateTimeValue(item.startedAt) })) ?? []}
          minRows={4}
        />
      </Panel>
    </TerminalApiClientProvider>
  );
}

function ApiForm({ title, icon, children }: { readonly title: string; readonly icon: IconName; readonly children: ReactNode }) {
  return (
    <Panel title={title} icon={icon} className="api-form-panel">
      <div className="api-form-grid">{children}</div>
    </Panel>
  );
}

function ApiTextField({ label, value, onChange }: { readonly label: string; readonly value: string; readonly onChange: (value: string) => void }) {
  return (
    <label className="api-field">
      <span>{label}</span>
      <input aria-label={label} value={value} onChange={(event) => onChange(event.target.value)} />
    </label>
  );
}

function ApiButton({ children, loading, onClick }: { readonly children: ReactNode; readonly loading: boolean; readonly onClick: () => void | Promise<void> }) {
  return (
    <button className="api-action-button" type="button" onClick={() => void onClick()} disabled={!apiReady || loading}>
      {loading ? "처리중" : children}
    </button>
  );
}

function KeyValuePanel({ title, rows }: { readonly title: string; readonly rows: readonly (readonly [string, ReactNode])[] }) {
  return (
    <Panel title={title} className="api-result-panel">
      <KeyValueGrid rows={rows} />
    </Panel>
  );
}

function KeyValueGrid({ rows }: { readonly rows: readonly (readonly [string, ReactNode])[] }) {
  return (
    <dl className="api-kv-grid">
      {rows.map(([key, value]) => (
        <div key={key}>
          <dt>{key}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  );
}

const accountColumns: readonly TableColumn[] = [
  { key: "customerId", label: "고객번호", width: "140px" },
  { key: "accountId", label: "계좌ID", width: "160px" },
  { key: "maskedAccountNo", label: "마스킹 계좌", width: "160px" },
  { key: "status", label: "상태", width: "90px" },
  { key: "currency", label: "통화", width: "70px" },
  { key: "ledger", label: "원장잔액", width: "120px" },
  { key: "available", label: "가용잔액", width: "120px" },
  { key: "hold", label: "hold", width: "110px" }
];

const approvalColumns: readonly TableColumn[] = [
  { key: "approvalId", label: "approvalId", width: "170px" },
  { key: "businessType", label: "업무", width: "180px" },
  { key: "reference", label: "참조", width: "170px" },
  { key: "maker", label: "maker", width: "130px" },
  { key: "status", label: "상태", width: "110px" },
  { key: "reason", label: "사유" }
];

function customerRow(customer: MaskedCustomerDto): TableRow {
  return {
    customerId: customer.customerId,
    maskedName: customer.maskedName,
    maskedPhone: customer.maskedPhone ?? "-",
    maskedAddress: customer.maskedAddress ?? "-",
    grade: customer.customerGrade,
    risk: customer.riskGrade
  };
}

function accountRow(account: StaffAccountDto): TableRow {
  return {
    customerId: account.customerId,
    accountId: account.accountId,
    maskedAccountNo: account.maskedAccountNo,
    status: <span className="state-pill">{account.status}</span>,
    currency: account.currency,
    ledger: formatMinor(account.ledgerBalanceMinor),
    available: formatMinor(account.availableBalanceMinor),
    hold: formatMinor(account.holdAmountMinor)
  };
}

function transactionRow(transaction: StaffTransactionDto): TableRow {
  return {
    ledgerTransactionId: transaction.ledgerTransactionId,
    type: transaction.transactionType,
    status: transaction.status,
    date: transaction.businessDate,
    account: transaction.accountId,
    amount: `${transaction.direction} ${formatMinor(transaction.amountMinor)} ${transaction.currency}`,
    channel: transaction.requestedChannel,
    reason: transaction.reason ?? "-"
  };
}

function approvalRow(approval: OperatorApproval): TableRow {
  return {
    approvalId: approval.approvalId,
    businessType: approval.businessType,
    reference: approval.businessReferenceId,
    maker: approval.requestedBy,
    status: <span className="state-pill blue">{approval.status}</span>,
    reason: approval.requestReason
  };
}

function retryRow(item: OperationalRetryQueueItemDto): TableRow {
  return {
    eventId: item.outboxEventId,
    aggregate: `${item.aggregateType}/${item.aggregateId}`,
    eventType: item.eventType,
    status: item.status,
    retry: String(item.retryCount),
    nextRetryAt: item.nextRetryAt ? formatDateTimeValue(item.nextRetryAt) : "-",
    eligible: item.retryEligible ? "Y" : "N",
    error: item.errorMessage ?? "-"
  };
}

function structuredError(error: unknown): { readonly title: string; readonly status: string; readonly code: string; readonly message: string; readonly auditEventId?: string | null } {
  if (error instanceof BankingApiError) {
    const parsed = parseErrorBody(error.body);
    const nested = parsed && typeof parsed === "object" && "error" in parsed ? (parsed as { readonly error?: unknown }).error : parsed;
    if (nested && typeof nested === "object") {
      const record = nested as Record<string, unknown>;
      return {
        title: "Spring structured error",
        status: String(error.status),
        code: String(record.code ?? record.errorCode ?? "UNKNOWN"),
        message: String(record.message ?? record.detail ?? error.message),
        auditEventId: typeof record.auditEventId === "string" ? record.auditEventId : undefined
      };
    }
    return {
      title: "Spring structured error",
      status: String(error.status),
      code: "HTTP_ERROR",
      message: error.body || error.message
    };
  }
  return {
    title: "Client error",
    status: "-",
    code: "CLIENT_ERROR",
    message: error instanceof Error ? error.message : "Unknown error"
  };
}

function parseErrorBody(body: string): unknown {
  try {
    return JSON.parse(body) as unknown;
  } catch {
    return null;
  }
}

function formatMinor(value: number): string {
  return value.toLocaleString("ko-KR");
}

function numericMinor(value: string): number {
  const parsed = Number.parseInt(value.replace(/[^\d-]/gu, ""), 10);
  return Number.isFinite(parsed) ? parsed : 0;
}

function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10);
}

function formatDateTimeValue(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString("ko-KR", { hour12: false });
}

function callActionLabel(action: "search" | "start" | "detail" | "note" | "task" | "history" | "escalate" | "close"): string {
  switch (action) {
    case "search":
      return "CALL101";
    case "start":
      return "CALL102 시작";
    case "detail":
      return "CALL102 상세";
    case "note":
      return "CALL103";
    case "task":
      return "CALL104";
    case "history":
      return "CALL105";
    case "escalate":
      return "CALL106";
    default:
      return "종료";
  }
}
