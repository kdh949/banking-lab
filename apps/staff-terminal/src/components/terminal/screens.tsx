import { useState, type ReactNode } from "react";
import { depositMenu, depositMenuTargets, detailColumns, feeColumns, fundGroups, inheritanceColumns, newScreenRows, notices } from "./registry";
import { DataTable, Field, MaterialIcon, Panel, RadioGroup, SearchBox } from "./primitives";
import type { MenuTarget, ScreenControlMetadata } from "./types";

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
export {
  ApprovalInboxScreen,
  CallCenterWorkspaceScreen,
  CommandWorkbenchScreen,
  OperationalRetryQueueScreen,
  StaffAccountInquiryScreen,
  StaffCustomerInquiryScreen,
  StaffTransactionInquiryScreen,
  WorkflowTimelineScreen
} from "./api-screens";
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
