import { useState, type ReactNode } from "react";
import {
  BankingApiError,
  createBankingApiClient,
  type CallCenterCustomerSummaryDto,
  type CallCenterInteractionDto,
  type MaskedCustomerDto,
  type OperationalRetryQueueItemDto,
  type OperatorApproval,
  type StaffAccountDto,
  type StaffCustomerDetailDto,
  type StaffTransactionDto,
  type StaffWorkflowTimelineEntryDto
} from "@banking-lab/api-client";
import { createSimulatorBearerToken } from "@banking-lab/auth-client";
import { depositMenu, depositMenuTargets, detailColumns, feeColumns, fundGroups, inheritanceColumns, newScreenRows, notices } from "./registry";
import { DataTable, Field, MaterialIcon, Panel, RadioGroup, SearchBox } from "./primitives";
import type { IconName, MenuTarget, ScreenControlMetadata, TableColumn, TableRow } from "./types";

export function PortalScreen() {
  return (
    <div className="portal-layout">
      <aside className="operator-profile">
        <div className="portrait-card">
          <div className="synthetic-portrait" aria-hidden="true">
            <span>BL</span>
          </div>
          <div>
            <strong>공승연 (B20100)</strong>
            <span>수신, 여신</span>
            <span>지점: 을지로2가</span>
            <span className="phone-line">
              <MaterialIcon name="call" /> 010-****-2201
            </span>
          </div>
          <button type="button">내정보관리</button>
        </div>
        <MiniWidget title="메시지 알림" icon="description">
          <div className="mail-grid">
            <span>
              받은메일: <b>11</b>
            </span>
            <span>
              결재건수: <b>7</b>
            </span>
            <span>
              안읽은메일: <b>5</b>
            </span>
            <span>
              결재반려건수: <b>0</b>
            </span>
          </div>
        </MiniWidget>
        <MiniWidget title="사이버연수원" icon="support_agent">
          <Progress label="캠페인달성" value={81} />
          <Progress label="상품교육" value={60} />
          <Progress label="업무능력" value={64} />
        </MiniWidget>
        <MiniWidget title="일정" icon="calendar_month">
          <MiniCalendar />
        </MiniWidget>
        <MiniWidget title="금리정보" icon="monitoring">
          <Sparkline />
        </MiniWidget>
        <MiniWidget title="환율정보" icon="currency_exchange">
          <RateList />
        </MiniWidget>
      </aside>
      <section className="portal-grid">
        <Panel title="공지사항" className="portal-panel wide">
          <ListRows rows={["- [이벤트]나의 포인트 기부 행사", "- [FAQ] 이용안내 및 신청", "- [FAQ] 한눈에 보기 신청", "- 사이트맵 이용안내", "- [기타] 경제지표 발표 캘린더"]} dates={["15-06-08", "15-06-07", "15-06-07", "15-06-05", "15-06-02"]} />
        </Panel>
        <Panel title="보도자료" className="portal-panel">
          <ListRows rows={["저축은행 인수계약 체결", "이웃돕기 성금 10억원 기탁", "서울시 안심서비스 실시", "우리 백장대소 정기예금 판매", "스마트뱅킹 1천만 고객 돌파"]} badge />
        </Panel>
        <Panel title="To Do list" tabs={["영업 전", "영업 중", "영업 후"]} className="portal-panel wide short">
          <ListRows rows={["점별 관리대상 발생 조회", "할인이음관계인 관리대상발생 조회", "만기 도래 안내 조회", "연체 계약 목록 조회", "만기 도래 안내 조회"]} />
        </Panel>
        <Panel title="상품정보" tabs={["패키지", "예금/적금", "펀드/보험"]} className="portal-panel short">
          <ListRows rows={["상품패키지 (2012.06.04)", "상품패키지 (2012.05.23)", "상품패키지 (2012.05.15)", "글로벌 자산배분 입니다"]} dates={["15-06-04", "15-05-23", "15-05-15", "15-05-11"]} />
        </Panel>
        <Panel title="여신접수" tabs={["감정결과", "감정의뢰", "여신심사", "기표예정"]} className="portal-panel wide">
          <DataTable
            columns={[
              { key: "no", label: "No", width: "42px" },
              { key: "receipt", label: "접수번호" },
              { key: "name", label: "신청자" },
              { key: "product", label: "대출상품" },
              { key: "amount", label: "신청금액" },
              { key: "date", label: "접수일자" },
              { key: "status", label: "진행상태" }
            ]}
            rows={[
              { no: "5", receipt: "120621-C30-011", name: "한상대", product: "드림론", amount: "22,000,000", date: "2015-06-21", status: <span className="state-pill blue">접수</span> },
              { no: "4", receipt: "120618-C12-505", name: "이화성", product: "전세자금대출", amount: "51,000,000", date: "2015-06-18", status: <span className="state-pill">접수</span> },
              { no: "3", receipt: "120608-D01-220", name: "김학연", product: "전세자금대출", amount: "25,000,000", date: "2015-06-08", status: <span className="state-pill gray">대기</span> }
            ]}
          />
        </Panel>
        <Panel title="투자정보" className="portal-panel">
          <ListRows rows={["[증권사자료] 중국 금리인하 관련 보고서", "[보고서] Monthly Market Report", "[증권사자료] ELS 시장 동향분석", "[기타] 주요증시 휴장일 캘린더"]} dates={["15-06-08", "15-06-07", "15-06-07", "15-06-02"]} />
        </Panel>
        <Panel title="WM Research" tabs={["일간리포트", "정기리포트", "Issue분석", "펀드정보"]} className="portal-panel wide short">
          <ListRows rows={["Daily Market Research (2015.06.18)", "Daily Market Research (2015.06.17)", "Daily Market Research (2015.06.16)", "Daily Market Research (2015.06.15)"]} dates={["2015-06-18", "2015-06-17", "2015-06-16", "2015-06-15"]} />
        </Panel>
        <Panel title="WM Advisory" tabs={["세무정보", "부동산정보", "외환정보", "주요문서모음"]} className="portal-panel short">
          <ListRows rows={["★WM Sales Cafe 통합운영 안내★", "[소득] 오피스텔 임대소득 등록 가능", "[신탁] 금융소득종합과세 대상자인 경우", "토지보상자금 유치를 위한 제안서"]} dates={["2015-06-18", "2015-06-07", "2015-06-16", "2015-06-14"]} />
        </Panel>
      </section>
      <FooterBand />
    </div>
  );
}

export function DepositNavigationScreen({ onMenuSelect }: { readonly onMenuSelect: (target: MenuTarget) => void }) {
  const [selectedNumber, setSelectedNumber] = useState("");

  return (
    <div className="deposit-screen">
      <section className="deposit-top-grid">
        <Panel title="수신업무" className="deposit-menu-panel">
          <div className="deposit-menu-box">
            <h2>
              <MaterialIcon name="keyboard_double_arrow_right" /> 수신업무 중간화면
            </h2>
            {depositMenu.map((item, index) => (
              <button className={item.startsWith("23.") ? "is-highlight" : ""} type="button" key={item} onClick={() => onMenuSelect(depositMenuTargets[index])}>
                {item}
              </button>
            ))}
          </div>
          <label className="number-choice">
            번호선택
            <input
              aria-label="번호선택"
              inputMode="numeric"
              pattern="[0-9]*"
              value={selectedNumber}
              onChange={(event) => {
                setSelectedNumber(event.target.value.replace(/\D/gu, "").slice(0, 4));
              }}
            />
          </label>
        </Panel>
        <Panel title="수신_중요공지" className="notice-panel">
          <NoticeTable />
        </Panel>
      </section>
      <section className="deposit-bottom-grid">
        <Panel title="자주묻는 질문" className="empty-board">
          <BoardHeader />
        </Panel>
        <Panel title="알면 편한 단말 메뉴얼" className="manual-board">
          <BoardHeader />
          <button type="button">[POST차세대 변경업무 메뉴얼]수신업무</button>
          <button type="button">[POST차세대 시스템 화면구성] 이렇게 좋아져요~!(수신)</button>
        </Panel>
        <Panel title="신규화면 공지" className="new-screen-board">
          <table>
            <tbody>
              {newScreenRows.map(([code, label]) => (
                <tr key={code}>
                  <td>
                    <button type="button" onClick={() => onMenuSelect({ code: code.replace(/\[|\]/gu, ""), label, screen: "deposit", moduleLabel: "수신" })}>
                      {code}
                    </button>
                  </td>
                  <td>
                    <button type="button" onClick={() => onMenuSelect({ code: code.replace(/\[|\]/gu, ""), label, screen: "deposit", moduleLabel: "수신" })}>
                      {label}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Panel>
      </section>
    </div>
  );
}

export function FeeInquiryScreen({ controls }: { readonly controls: ScreenControlMetadata }) {
  return (
    <div className="query-screen">
      <SearchBox controls={controls}>
        <Field label="고객계좌번호" type="search" />
        <Field label="내부계약번호" />
        <Field label="이자수수료종류" type="select" value="%" />
        <Field label="거래상태" type="select" value="%-전체" emphasized />
        <Field label="입출금구분" type="select" value="-전체" />
        <RadioGroup label="출력구분" options={["화면", "단말", "레이저"]} />
        <Field label="조회기간" value="2005-01-01" type="date" emphasized />
      </SearchBox>
      <Panel title="외환이자수수료 발생내역" className="query-panel">
        <DataTable columns={feeColumns} rows={[]} minRows={9} />
      </Panel>
      <Panel title="외환이자수수료 상세내역" className="query-panel short">
        <DataTable columns={detailColumns} rows={[]} minRows={3} />
      </Panel>
      <Panel title="외환수수료이자 금리환율상세" className="query-panel short">
        <DataTable
          columns={[
            { key: "serial", label: "일련번호", width: "110px" },
            { key: "type", label: "금리환율구분", width: "150px" },
            { key: "rateId", label: "금리환율상세일련번호", width: "210px" },
            { key: "baseDate", label: "금리적용기준년월일", width: "180px" },
            { key: "shown", label: "표면적용율", width: "130px" },
            { key: "actual", label: "실적적용율", width: "130px" },
            { key: "condition", label: "상품조건코드", width: "150px" },
            { key: "contract", label: "내부계약번호", width: "170px" }
          ]}
          rows={[]}
          minRows={4}
        />
      </Panel>
    </div>
  );
}

export function InheritanceScreen({ controls }: { readonly controls: ScreenControlMetadata }) {
  return (
    <div className="inheritance-screen">
      <div className="notice-strip">
        <p className="danger">※ 사망으로 인한 해지건은 계좌번호 혹은 변경전실명번호로만 조회 가능합니다.</p>
        <p>※ 제자번호는 양도 후 변경된 계좌번호를 입력하시기 바랍니다.</p>
        <p>※ 양도거래후 첨부순서&nbsp;&nbsp;① 고객정보등록표인자 ==&gt; ② 양도상속관리대장 출력</p>
      </div>
      <SearchBox compact controls={controls}>
        <Field label="조회구분" type="select" value="1-계좌번호" />
        <Field label="계좌번호" type="search" emphasized />
        <Field label="회차" type="search" />
        <Field label="양도인고객번호" value="고객 가져오기" type="readonly" />
        <Field label="고객명" type="readonly" />
        <Field label="전행고객번호" type="readonly" />
        <Field label="사업자번호/생일" type="readonly" />
      </SearchBox>
      <div className="inheritance-actions">
        <button type="button">고객정보등록표 인자</button>
        <button type="button">상속및양도관리대장 출력</button>
      </div>
      <DataTable columns={inheritanceColumns} rows={[]} minRows={15} />
    </div>
  );
}

export function FundNavigationScreen({ onMenuSelect }: { readonly onMenuSelect: (target: MenuTarget) => void }) {
  return (
    <div className="fund-screen">
      <div className="fund-list">
        <SectionLabel title="입금/출금/해지신청" />
        {fundGroups.map((group) => (
          <div className="fund-group" key={group.title}>
            <div className="fund-group-title">{group.title}</div>
            <div className="fund-group-rows">
              {group.rows.map(([code, note]) => (
                <button className="fund-row" type="button" key={code} onClick={() => onMenuSelect(fundTargetFromRow(code, note))}>
                  <span>{code}</span>
                  <span>{note}</span>
                </button>
              ))}
            </div>
          </div>
        ))}
      </div>
      <div className="flow-row">
        <FlowPanel title="입금/거래취소">
          <FlowChart
            lanes={[
              [
                { label: "입금", type: "process", target: fundTarget("F2201", "(MMF)펀드입금") },
                { label: "확인증 교부 및 안내", type: "process", muted: true }
              ],
              [
                { label: "거래취소", type: "process", target: fundTarget("F2401", "펀드거래취소") },
                { label: "취소가능여부", type: "decision" },
                { label: "기준시간 전", type: "decision" },
                { label: "거래취소", type: "process", target: fundTarget("F2401", "펀드거래취소") }
              ]
            ]}
            onMenuSelect={onMenuSelect}
          />
        </FlowPanel>
        <FlowPanel title="출금">
          <FlowChart
            lanes={[
              [
                { label: "인출가능금액조회", type: "process", target: fundTarget("F2303", "펀드인출가능금액") },
                { label: "출금", type: "process", target: fundTarget("F2302", "펀드출금/해지") },
                { label: "확인증 교부 및 안내", type: "process", muted: true },
                { label: "계좌거래내역조회", type: "process", target: fundTarget("F2501", "펀드계좌거래내역조회") }
              ]
            ]}
            onMenuSelect={onMenuSelect}
          />
        </FlowPanel>
        <FlowPanel title="펀드예탁금이자정리">
          <FlowChart
            lanes={[
              [
                { label: "별단미정리내역", type: "process", target: fundTarget("F2304", "펀드예탁금이자정리") },
                { label: "원리금지급계좌 등록유무", type: "decision" },
                { label: "예탁금이자정리", type: "decision" },
                { label: "예탁금이자정리", type: "process", target: fundTarget("F2304", "펀드예탁금이자정리") }
              ],
              [
                { label: "계좌개설", type: "process", target: fundTarget("F2101", "펀드계좌개설") },
                { label: "예탁금이자환급", type: "process", target: fundTarget("F2305", "펀드예탁금이자환급") }
              ]
            ]}
            onMenuSelect={onMenuSelect}
          />
        </FlowPanel>
      </div>
    </div>
  );
}

const apiBaseUrl = process.env.NEXT_PUBLIC_BANKING_API_BASE_URL ?? "";
const simulatorTokensEnabled = process.env.NEXT_PUBLIC_BANKING_SIMULATOR_TOKENS_ENABLED === "true";
const apiReady = Boolean(apiBaseUrl) && simulatorTokensEnabled;

type ApiState<T> =
  | { readonly status: "idle"; readonly message?: string }
  | { readonly status: "running"; readonly message: string }
  | { readonly status: "loaded"; readonly data: T; readonly message?: string }
  | { readonly status: "failed"; readonly error: unknown };

type TerminalActor = "staff" | "manager" | "callAgent" | "callManager";

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
};

type ApprovalResult = {
  readonly approvals: readonly OperatorApproval[];
  readonly selected?: OperatorApproval;
  readonly action?: {
    readonly status: string;
    readonly approvalId: string;
    readonly businessType: string;
    readonly executed?: boolean;
    readonly rejected?: boolean;
    readonly auditEventId?: string | null;
  };
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
          <MaterialIcon name="language" /> Spring API
        </span>
        <span>{apiBaseUrl || "NEXT_PUBLIC_BANKING_API_BASE_URL 미설정"}</span>
        <span>{simulatorTokensEnabled ? "simulator-token opt-in" : "simulator-token 필요"}</span>
      </div>
      {children}
    </div>
  );
}

function terminalClient(actor: TerminalActor) {
  if (!apiReady) {
    throw new Error("Spring API base URL 또는 simulator token opt-in이 필요합니다.");
  }
  const actorConfig = actorConfigFor(actor);
  return createBankingApiClient({
    baseUrl: apiBaseUrl,
    bearerToken: createSimulatorBearerToken({
      subject: actorConfig.subject,
      audience: "core-banking-api",
      roles: actorConfig.roles
    })
  });
}

function actorConfigFor(actor: TerminalActor) {
  switch (actor) {
    case "manager":
      return { subject: "branch-manager01", roles: ["BRANCH_MANAGER"] as const };
    case "callAgent":
      return { subject: "call-agent01", roles: ["CALL_CENTER_AGENT"] as const };
    case "callManager":
      return { subject: "call-manager01", roles: ["CALL_CENTER_MANAGER"] as const };
    default:
      return { subject: "branch-staff01", roles: ["BRANCH_STAFF"] as const };
  }
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
          <dd>branch-manager01</dd>
        </div>
        <div>
          <dt>approvalId</dt>
          <dd>{selected?.approvalId ?? "-"}</dd>
        </div>
        <div>
          <dt>self approval</dt>
          <dd>{selected?.requestedBy === "branch-manager01" ? "차단 대상" : "분리"}</dd>
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

export function StaffCustomerInquiryScreen() {
  const [query, setQuery] = useState("SYN");
  const [customerId, setCustomerId] = useState("SYN-CUS-001");
  const [reason, setReason] = useState("통합 단말 CUS101 고객 요청 응대");
  const [state, setState] = useState<ApiState<CustomerResult>>({ status: "idle" });

  const runSearch = async () => {
    setState({ status: "running", message: "CUS101 조회 중" });
    try {
      const [search, detail] = await Promise.all([
        terminalClient("staff").staffCustomerSearch(query, reason),
        terminalClient("staff").staffCustomerDetail(customerId, reason)
      ]);
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
      const response = await terminalClient("staff").staffAccountSearch({ customerId, accountId, reason });
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
  const [reason, setReason] = useState("통합 단말 TX101 거래 확인");
  const [state, setState] = useState<ApiState<TransactionResult>>({ status: "idle" });

  const runSearch = async () => {
    setState({ status: "running", message: "TX101 조회 중" });
    try {
      const response = await terminalClient("staff").staffTransactionSearch({ accountId, reason });
      setState({ status: "loaded", data: { auditEventId: response.auditEventId, transactions: response.items } });
    } catch (error) {
      setState({ status: "failed", error });
    }
  };

  return (
    <TerminalApiClientProvider>
      <ApiForm title="TX101 거래 조회" icon="receipt_long">
        <ApiTextField label="계좌ID" value={accountId} onChange={setAccountId} />
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
      <KeyValuePanel title="감사" rows={[["auditEventId", state.status === "loaded" ? state.data.auditEventId : "-"]]} />
    </TerminalApiClientProvider>
  );
}

export function ApprovalInboxScreen() {
  const [approvalId, setApprovalId] = useState("");
  const [rejectReason, setRejectReason] = useState("통합 단말 APR101 반려 검토");
  const [state, setState] = useState<ApiState<ApprovalResult>>({ status: "idle" });

  const loadApprovals = async () => {
    setState({ status: "running", message: "APR101 승인함 조회 중" });
    try {
      const approvals = await terminalClient("manager").staffApprovals();
      const selected = approvalId ? approvals.find((approval) => approval.approvalId === approvalId) : approvals[0];
      setApprovalId(selected?.approvalId ?? approvalId);
      setState({ status: "loaded", data: { approvals, selected } });
    } catch (error) {
      setState({ status: "failed", error });
    }
  };

  const selectApproval = async () => {
    setState({ status: "running", message: "승인 상세 조회 중" });
    try {
      const [approvals, selected] = await Promise.all([terminalClient("manager").staffApprovals(), terminalClient("manager").staffApproval(approvalId)]);
      setState({ status: "loaded", data: { approvals, selected } });
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
      const client = terminalClient("manager");
      const response =
        action === "approve"
          ? await client.approveStaffApproval(selected.approvalId, { approvedBy: "branch-manager01", approvedByRole: "BRANCH_MANAGER", screenId: "APR101" })
          : await client.rejectStaffApproval(selected.approvalId, { rejectedBy: "branch-manager01", rejectedByRole: "BRANCH_MANAGER", rejectReason, screenId: "APR101" });
      const approvals = await client.staffApprovals();
      setState({
        status: "loaded",
        data: {
          approvals,
          selected: response.item,
          action: {
            status: response.item.status,
            approvalId: response.item.approvalId,
            businessType: response.item.businessType,
            executed: "executed" in response ? response.executed : undefined,
            rejected: "rejected" in response ? response.rejected : undefined,
            auditEventId: response.item.auditEventId
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
          ["lastAction", state.status === "loaded" && state.data.action ? `${state.data.action.status} / ${state.data.action.auditEventId ?? "-"}` : "-"]
        ]}
      />
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
      const response = await terminalClient("staff").staffOperationalRetryQueue(reason, status || undefined);
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
  const [reason, setReason] = useState("통합 단말 WRK003 workflow timeline 확인");
  const [state, setState] = useState<ApiState<{ readonly auditEventId: string; readonly entries: readonly StaffWorkflowTimelineEntryDto[] }>>({ status: "idle" });

  const runSearch = async () => {
    setState({ status: "running", message: "WRK003 조회 중" });
    try {
      const response = await terminalClient("staff").staffWorkflowTimeline(businessReferenceId, reason);
      setState({ status: "loaded", data: { auditEventId: response.auditEventId, entries: response.items } });
    } catch (error) {
      setState({ status: "failed", error });
    }
  };

  return (
    <TerminalApiClientProvider>
      <ApiForm title="WRK003 workflow timeline" icon="history">
        <ApiTextField label="businessReferenceId" value={businessReferenceId} onChange={setBusinessReferenceId} />
        <ReasonRequiredPanel reason={reason} onReasonChange={setReason} />
        <ApiButton onClick={runSearch} loading={state.status === "running"}>
          조회
        </ApiButton>
      </ApiForm>
      <StructuredErrorPanel error={state.status === "failed" ? state.error : null} />
      <TimelinePanel entries={state.status === "loaded" ? state.data.entries : []} />
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
      const client = terminalClient("staff");
      const baseCommand = {
        requestedBy: "branch-staff01",
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
          makerActor: "branch-staff01",
          checkerActor: "branch-manager01",
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
          ["maker/checker", state.status === "loaded" ? `${state.data.makerActor} / ${state.data.checkerActor}` : "branch-staff01 / branch-manager01"],
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
      const agent = terminalClient("callAgent");
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

function NoticeTable() {
  return (
    <div className="notice-table">
      <div className="notice-caption">
        <MaterialIcon name="keyboard_double_arrow_right" /> 수신업무 공지사항
      </div>
      <DataTable
        columns={[
          { key: "no", label: "순번", width: "70px" },
          { key: "title", label: "제목" },
          { key: "dept", label: "등록부서", width: "120px" },
          { key: "date", label: "등록일시", width: "120px" }
        ]}
        rows={notices.map(([title, dept, date, important], index) => ({
          no: String(index + 1),
          title: <span className={important ? "important-notice" : ""}>{title}</span>,
          dept,
          date
        }))}
      />
    </div>
  );
}

function BoardHeader() {
  return (
    <div className="board-header">
      <strong>제&nbsp;&nbsp;&nbsp;&nbsp;목</strong>
    </div>
  );
}

function MiniWidget({ title, icon, children }: { readonly title: string; readonly icon: Parameters<typeof MaterialIcon>[0]["name"]; readonly children: ReactNode }) {
  return (
    <section className="mini-widget">
      <h3>
        <MaterialIcon name={icon} />
        {title}
      </h3>
      <div>{children}</div>
    </section>
  );
}

function Progress({ label, value }: { readonly label: string; readonly value: number }) {
  return (
    <div className="progress-row">
      <span>{label}</span>
      <div>
        <i style={{ width: `${value}%` }} />
      </div>
      <em>{value}%</em>
    </div>
  );
}

function MiniCalendar() {
  const days = Array.from({ length: 31 }, (_, index) => String(index + 1));
  return (
    <div className="mini-calendar">
      <div>12월</div>
      <div className="calendar-grid">
        {days.map((day) => (
          <span className={day === "28" ? "is-today" : ""} key={day}>
            {day}
          </span>
        ))}
      </div>
    </div>
  );
}

function Sparkline() {
  return (
    <div className="sparkline" aria-label="금리 추이">
      {[24, 25, 28, 30, 34, 35, 36, 38, 39, 40].map((height, index) => (
        <span style={{ height }} key={index} />
      ))}
    </div>
  );
}

function RateList() {
  return (
    <table className="rate-list">
      <tbody>
        {[
          ["USD", "1194.54", "1153.46"],
          ["GBP", "1845.37", "1773.37"],
          ["JPY100", "1508.26", "1456.38"],
          ["CNY", "197.29", "175.18"]
        ].map(([unit, buy, sell]) => (
          <tr key={unit}>
            <td>{unit}</td>
            <td>{buy}</td>
            <td>{sell}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function ListRows({ rows, dates, badge = false }: { readonly rows: readonly string[]; readonly dates?: readonly string[]; readonly badge?: boolean }) {
  return (
    <div className="list-rows">
      {rows.map((row, index) => (
        <div className="list-row" key={`${row}-${index}`}>
          <span>
            {badge ? <b>N</b> : null}
            {row}
          </span>
          {dates?.[index] ? <time>{dates[index]}</time> : null}
        </div>
      ))}
    </div>
  );
}

function FooterBand() {
  return (
    <footer className="portal-footer">
      <p>은행상담 1588-5000 1599-5000 (해외 82-2-2006-5000) | 고객의 말씀 080-365-5000</p>
      <p>은행소개 | 영업점안내 | 고객광장 | 개인정보처리방침 | 사고신고 | 전자민원접수 | 보안센터</p>
      <p>COPYRIGHTS WOORI BANK. ALL RIGHTS RESERVED. synthetic lab</p>
    </footer>
  );
}

function SectionLabel({ title }: { readonly title: string }) {
  return (
    <div className="section-label">
      <MaterialIcon name="keyboard_double_arrow_right" />
      {title}
    </div>
  );
}

function FlowPanel({ title, children }: { readonly title: string; readonly children: ReactNode }) {
  return (
    <section className="flow-panel">
      <SectionLabel title={title} />
      {children}
    </section>
  );
}

type FlowNode = {
  readonly label: string;
  readonly type: "process" | "decision";
  readonly muted?: boolean;
  readonly target?: MenuTarget;
};

function FlowChart({ lanes, onMenuSelect }: { readonly lanes: readonly (readonly FlowNode[])[]; readonly onMenuSelect: (target: MenuTarget) => void }) {
  return (
    <div className={`flow-chart lanes-${lanes.length}`}>
      {lanes.map((lane, laneIndex) => (
        <div className="flow-lane" key={laneIndex}>
          {lane.map((node, index) => {
            const className = `flow-node ${node.type} ${node.muted ? "is-muted" : ""}`;
            return (
              <div className="flow-node-wrap" key={`${node.label}-${index}`}>
                {node.target ? (
                  <button className={className} type="button" onClick={() => onMenuSelect(node.target!)}>
                    {node.label}
                  </button>
                ) : (
                  <div className={className}>{node.label}</div>
                )}
                {index < lane.length - 1 ? <div className="flow-arrow" /> : null}
              </div>
            );
          })}
        </div>
      ))}
    </div>
  );
}

function fundTarget(code: string, label: string): MenuTarget {
  return { code, label, screen: "fund", moduleLabel: "펀드" };
}

function fundTargetFromRow(codeText: string, note: string): MenuTarget {
  const code = codeText.match(/\[([^\]]+)\]/u)?.[1] ?? codeText;
  const label = codeText.replace(/\[[^\]]+\]\s*/u, "").trim() || note.replace(/^※\s*/u, "");
  return fundTarget(code, label);
}
