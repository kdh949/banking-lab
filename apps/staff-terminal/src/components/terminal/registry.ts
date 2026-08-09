import type { MenuGroup, MenuTarget, ScreenDefinition, ScreenKey, TableColumn, TopModule, UtilityItem } from "./types";

const depositTabs = ["업무포탈", "신규", "입금", "출금", "해지", "정산", "등록/해제", "조회", "통장/증명서/기타", "자기앞수표", "기타별단", "수신거래흐름도", "BPR흐름도"] as const;
const fundTabs = ["펀드흐름도", "펀드(공지)", "펀드상담", "신규", "입금/출금", "제신고/제변경", "판매사이동", "대고객발급/통장", "연금/사모펀드", "계좌정보"] as const;
const queryTabs = ["정산", "등록/해제", "조회", "통장/증명서/기타", "자기앞수표", "기타별단", "수신거래흐름도", "BPR흐름도"] as const;
const staffApiTabs = ["조회", "승인", "운영예외", "상담", "감사"] as const;

export const topModules: readonly TopModule[] = [
  { label: "수신", icon: "account_balance_wallet", screen: "deposit" },
  { label: "여신", icon: "home_work", screen: "portal" },
  { label: "여신종합", icon: "manage_search", implemented: false },
  { label: "외환", icon: "currency_exchange", screen: "fee" },
  { label: "고객", icon: "groups", screen: "inheritance" },
  { label: "CRM", icon: "campaign", screen: "callCenter" },
  { label: "방카", icon: "business_center", implemented: false },
  { label: "펀드", icon: "account_balance", screen: "fund" },
  { label: "신용카드", icon: "credit_card", implemented: false },
  { label: "전자", icon: "payments", implemented: false },
  { label: "대행", icon: "sync_alt", implemented: false },
  { label: "재무", icon: "domain", implemented: false },
  { label: "기타", icon: "query_stats", implemented: false }
];

export const utilityItems: readonly UtilityItem[] = [
  { label: "즐겨찾기", icon: "star", mode: "none" },
  { label: "탑리스트", icon: "task_alt", mode: "none" },
  { label: "워크플로우", icon: "workspaces", mode: "workflow" },
  { label: "업무메뉴", icon: "widgets", mode: "menu" },
  { label: "저널보기", icon: "docs", mode: "none" },
  { label: "고객포털", icon: "language", mode: "none" },
  { label: "계산기", icon: "receipt_long", mode: "none" },
  { label: "날짜계산기", icon: "calendar_month", mode: "none" },
  { label: "일정", icon: "business_center", mode: "none" },
  { label: "오피스", icon: "article", mode: "none" },
  { label: "멀티프레임", icon: "grid_view", mode: "none" },
  { label: "트레이스", icon: "bar_chart", mode: "none" },
  { label: "히든보기", icon: "more_horiz", mode: "none" },
  { label: "스크립트", icon: "edit_square", mode: "none" }
];

export const screenDefinitions: Record<ScreenKey, ScreenDefinition> = {
  portal: {
    key: "portal",
    code: "SY-Starts.scn",
    title: "통합 포털",
    module: "SY-Starts...",
    moduleLabel: "여신",
    tabs: [],
    controls: {
      template: "portal",
      reasonRequired: false,
      piiAccess: true,
      maskingPolicy: "DEFAULT_MASKED",
      approvalRequired: false,
      workflowVisible: true
    }
  },
  deposit: {
    key: "deposit",
    code: "20000",
    title: "수신_네비게이션",
    module: "20000",
    moduleLabel: "수신",
    tabs: depositTabs,
    controls: {
      template: "navigation",
      reasonRequired: false,
      piiAccess: false,
      maskingPolicy: "NONE",
      approvalRequired: false,
      workflowVisible: true
    }
  },
  fee: {
    key: "fee",
    code: "S5801",
    title: "외환이자수수료내역조회",
    module: "S5801",
    moduleLabel: "외환",
    tabs: queryTabs,
    controls: {
      template: "inquiry",
      reasonRequired: true,
      piiAccess: true,
      maskingPolicy: "DEFAULT_MASKED",
      approvalRequired: false,
      workflowVisible: true
    }
  },
  inheritance: {
    key: "inheritance",
    code: "10607",
    title: "상속 및 양도 관리대장 조회/출력",
    module: "10607",
    moduleLabel: "고객",
    tabs: [],
    controls: {
      template: "inquiry",
      reasonRequired: true,
      piiAccess: true,
      maskingPolicy: "DEFAULT_MASKED",
      approvalRequired: false,
      workflowVisible: true
    }
  },
  fund: {
    key: "fund",
    code: "F0000",
    title: "펀드_네비게이션",
    module: "F0000",
    moduleLabel: "펀드",
    tabs: fundTabs,
    controls: {
      template: "navigation",
      reasonRequired: false,
      piiAccess: false,
      maskingPolicy: "NONE",
      approvalRequired: false,
      workflowVisible: true
    }
  },
  staffCustomer: {
    key: "staffCustomer",
    code: "CUS101",
    title: "직원 고객 상세 조회",
    module: "CUS101",
    moduleLabel: "고객",
    tabs: staffApiTabs,
    controls: {
      template: "inquiry",
      reasonRequired: true,
      piiAccess: true,
      maskingPolicy: "DEFAULT_MASKED",
      approvalRequired: false,
      workflowVisible: true
    }
  },
  staffAccount: {
    key: "staffAccount",
    code: "ACC101",
    title: "직원 계좌 조회",
    module: "ACC101",
    moduleLabel: "고객",
    tabs: staffApiTabs,
    controls: {
      template: "inquiry",
      reasonRequired: true,
      piiAccess: true,
      maskingPolicy: "DEFAULT_MASKED",
      approvalRequired: false,
      workflowVisible: true
    }
  },
  staffTransaction: {
    key: "staffTransaction",
    code: "TX101",
    title: "직원 거래 조회",
    module: "TX101",
    moduleLabel: "고객",
    tabs: staffApiTabs,
    controls: {
      template: "inquiry",
      reasonRequired: true,
      piiAccess: false,
      maskingPolicy: "DEFAULT_MASKED",
      approvalRequired: false,
      workflowVisible: true
    }
  },
  fdsReview: {
    key: "fdsReview",
    code: "FDS201",
    title: "FDS 보류 이체 심사",
    module: "FDS201",
    moduleLabel: "리스크",
    tabs: staffApiTabs,
    controls: {
      template: "case",
      reasonRequired: true,
      piiAccess: false,
      maskingPolicy: "DEFAULT_MASKED",
      approvalRequired: true,
      workflowVisible: true
    }
  },
  approvalInbox: {
    key: "approvalInbox",
    code: "APR101",
    title: "승인함 목록/상세",
    module: "APR101",
    moduleLabel: "내부통제",
    tabs: staffApiTabs,
    controls: {
      template: "case",
      reasonRequired: false,
      piiAccess: true,
      maskingPolicy: "DEFAULT_MASKED",
      approvalRequired: true,
      workflowVisible: true
    }
  },
  opsRetry: {
    key: "opsRetry",
    code: "WRK002",
    title: "운영 retry queue",
    module: "WRK002",
    moduleLabel: "운영",
    tabs: staffApiTabs,
    controls: {
      template: "case",
      reasonRequired: true,
      piiAccess: false,
      maskingPolicy: "NONE",
      approvalRequired: false,
      workflowVisible: true
    }
  },
  workflowTimeline: {
    key: "workflowTimeline",
    code: "WRK003",
    title: "workflow timeline",
    module: "WRK003",
    moduleLabel: "운영",
    tabs: staffApiTabs,
    controls: {
      template: "case",
      reasonRequired: true,
      piiAccess: false,
      maskingPolicy: "NONE",
      approvalRequired: false,
      workflowVisible: true
    }
  },
  callCenter: {
    key: "callCenter",
    code: "CALL101",
    title: "상담센터 compact 업무",
    module: "CALL101",
    moduleLabel: "CRM",
    tabs: staffApiTabs,
    controls: {
      template: "case",
      reasonRequired: true,
      piiAccess: true,
      maskingPolicy: "DEFAULT_MASKED",
      approvalRequired: true,
      workflowVisible: true
    }
  },
  commandWorkbench: {
    key: "commandWorkbench",
    code: "CMD101",
    title: "고위험 command workbench",
    module: "CMD101",
    moduleLabel: "내부통제",
    tabs: staffApiTabs,
    controls: {
      template: "command",
      reasonRequired: true,
      piiAccess: true,
      maskingPolicy: "DEFAULT_MASKED",
      approvalRequired: true,
      workflowVisible: true
    }
  }
};

export const screenList: readonly ScreenDefinition[] = [
  screenDefinitions.portal,
  screenDefinitions.deposit,
  screenDefinitions.fee,
  screenDefinitions.inheritance,
  screenDefinitions.fund,
  screenDefinitions.staffCustomer,
  screenDefinitions.staffAccount,
  screenDefinitions.staffTransaction,
  screenDefinitions.fdsReview,
  screenDefinitions.approvalInbox,
  screenDefinitions.opsRetry,
  screenDefinitions.workflowTimeline,
  screenDefinitions.callCenter,
  screenDefinitions.commandWorkbench
];

export function screenDefinitionFor(screen: ScreenKey) {
  return screenDefinitions[screen] ?? screenDefinitions.deposit;
}

export function defaultModuleForScreen(screen: ScreenKey) {
  return screenDefinitionFor(screen).moduleLabel;
}

export function defaultTargetForScreen(screen: ScreenKey, moduleLabel = defaultModuleForScreen(screen)): MenuTarget {
  const screenInfo = screenDefinitionFor(screen);
  return {
    code: screenInfo.code,
    label: screenInfo.title,
    moduleLabel,
    screen
  };
}

export const menuTree = [
  {
    label: "정산",
    open: true,
    items: [
      { code: "S5801", label: "외환이자수수료내역조회", screen: "fee", moduleLabel: "외환" },
      { code: "50841", label: "외환거래내역조회", screen: "fee", moduleLabel: "외환" },
      { code: "50710", label: "미정수내역관리", screen: "fee", moduleLabel: "외환" }
    ]
  },
  {
    label: "계약",
    open: true,
    items: [
      { code: "10601", label: "정보변경 전입/명의변경", screen: "inheritance", moduleLabel: "고객" },
      { code: "10602", label: "세금우대 일반 상호전환", screen: "inheritance", moduleLabel: "고객" },
      { code: "10607", label: "상속 및 양도 관리대장", screen: "inheritance", moduleLabel: "고객" }
    ]
  },
  {
    label: "수신",
    open: false,
    items: [
      { code: "20000", label: "수신 네비게이션", screen: "deposit", moduleLabel: "수신" },
      { code: "23601", label: "수표어음교부", screen: "deposit", moduleLabel: "수신" }
    ]
  },
  {
    label: "펀드",
    open: false,
    items: [{ code: "F0000", label: "펀드 네비게이션", screen: "fund", moduleLabel: "펀드" }]
  },
  {
    label: "직원 API 업무",
    open: true,
    items: [
      { code: "CUS101", label: "고객 상세 조회", screen: "staffCustomer", moduleLabel: "고객" },
      { code: "ACC101", label: "계좌 조회", screen: "staffAccount", moduleLabel: "고객" },
      { code: "TX101", label: "거래 조회", screen: "staffTransaction", moduleLabel: "고객" },
      { code: "FDS201", label: "FDS 보류 이체 심사", screen: "fdsReview", moduleLabel: "리스크" },
      { code: "APR101", label: "승인함 목록/상세", screen: "approvalInbox", moduleLabel: "내부통제" },
      { code: "CMD101", label: "고위험 command workbench", screen: "commandWorkbench", moduleLabel: "내부통제" },
      { code: "WRK002", label: "운영 retry queue", screen: "opsRetry", moduleLabel: "운영" },
      { code: "WRK003", label: "workflow timeline", screen: "workflowTimeline", moduleLabel: "운영" },
      { code: "CALL101", label: "상담 고객 검색", screen: "callCenter", moduleLabel: "CRM" },
      { code: "CALL102", label: "상담 interaction 시작/상세", screen: "callCenter", moduleLabel: "CRM" },
      { code: "CALL103", label: "redacted note", screen: "callCenter", moduleLabel: "CRM" },
      { code: "CALL104", label: "aftercall task", screen: "callCenter", moduleLabel: "CRM" },
      { code: "CALL105", label: "상담 이력", screen: "callCenter", moduleLabel: "CRM" },
      { code: "CALL106", label: "상담 escalation", screen: "callCenter", moduleLabel: "CRM" }
    ]
  }
] satisfies readonly MenuGroup[];

export const depositMenu = [
  "11. 수신기본(신규,해지,조회,기타)",
  "15. 계약공통",
  "21. 수신기본(입출금,조회,기타)거래",
  "23. 수표/어음",
  "24. 자기앞수표",
  "25. 기타별단",
  "S2. 수신정산"
] as const;

export const depositMenuTargets: readonly MenuTarget[] = [
  { code: "20000", label: "수신기본", screen: "deposit", moduleLabel: "수신" },
  { code: "15000", label: "계약공통", screen: "deposit", moduleLabel: "수신" },
  { code: "21000", label: "수신기본 거래", screen: "deposit", moduleLabel: "수신" },
  { code: "23601", label: "수표/어음", screen: "deposit", moduleLabel: "수신" },
  { code: "24000", label: "자기앞수표", screen: "deposit", moduleLabel: "수신" },
  { code: "25000", label: "기타별단", screen: "deposit", moduleLabel: "수신" },
  { code: "S200", label: "수신정산", screen: "deposit", moduleLabel: "수신" }
];

export const notices = [
  [">>> [POST차세대] POST 차세대시행관련 문서 <<<", "IT금융개발부", "2014-09-25", "important"],
  ["일부 급여이체 기업의 타행이체 고객 적극 유치", "개인고객부", "2014-09-30", ""],
  ["「주택청약(종합)저축」 금리변경 안내<시행일 '14.10.1>", "개인고객부", "2014-09-30", ""],
  ["국민주택기금 대출고객에 대한 해피콜 실시 요청", "개인고객부", "2014-09-30", ""],
  ["「POST차세대시스템」전환 시 개인고객 응대 유의사항", "개인고객부", "2014-09-30", ""],
  ["제1종 국민주택채권 발행금리 인하 안내", "개인고객부", "2014-09-30", ""],
  ["주택청약저축 업무취급지침 개정<시행일 '14.10.1>", "개인고객부", "2014-09-30", ""]
] as const;

export const newScreenRows = [
  ["[23601]", "수표어음교부"],
  ["[23602]", "수표어음사고등록"],
  ["[23608]", "당좌/가당 부도등록"],
  ["[23805]", "어음발행정보조회"],
  ["[23808]", "수표어음 교부계좌 조회"],
  ["[23809]", "수표어음 적정교부량조회"],
  ["[23810]", "당좌 교환결제현황"],
  ["[21680]", "계좌사고신고"],
  ["[21711]", "조건변경 등록/해제"],
  ["[23815]", "당좌가당 부도내역 조회"]
] as const;

export const feeColumns: readonly TableColumn[] = [
  { key: "date", label: "거래년월일", width: "120px" },
  { key: "name", label: "거래명", width: "180px" },
  { key: "bl", label: "B/L", width: "48px" },
  { key: "lg", label: "L/G", width: "48px" },
  { key: "fee", label: "이자수수료명", width: "230px" },
  { key: "amount", label: "계산금액", width: "140px" },
  { key: "paid", label: "실거래금액", width: "140px" },
  { key: "discount", label: "감면금액", width: "120px" },
  { key: "remain", label: "미정리잔액", width: "120px" }
];

export const inheritanceColumns: readonly TableColumn[] = [
  { key: "select", label: "선택", width: "50px" },
  { key: "date", label: "거래일", width: "110px" },
  { key: "time", label: "거래시각", width: "96px" },
  { key: "from", label: "양도인", width: "120px" },
  { key: "fromId", label: "양도인사업...", width: "140px" },
  { key: "fromCustomer", label: "양도인고객번호", width: "150px" },
  { key: "to", label: "양수인", width: "120px" },
  { key: "toCustomer", label: "양수인고객번호", width: "150px" }
];

export const detailColumns: readonly TableColumn[] = [
  { key: "serial", label: "일련번호", width: "110px" },
  { key: "base", label: "부리대상금액", width: "140px" },
  { key: "calcStart", label: "계산시작일", width: "120px" },
  { key: "calcEnd", label: "계산종료일", width: "120px" },
  { key: "days", label: "이자계산일수", width: "130px" },
  { key: "rate", label: "적용이율", width: "110px" },
  { key: "fee", label: "수수료계산일/일수", width: "170px" },
  { key: "appliedFee", label: "적용수수료율", width: "140px" }
];

export const fundGroups = [
  {
    title: "입금",
    rows: [
      ["[F2201] (MMF)펀드입금", "※ MMF계좌 입금 거래"],
      ["[F2202] (일반)펀드입금", "※ 일반펀드 계좌 입금 거래"],
      ["[F2203] 펀드대계좌입금", "※ 다계좌 입금 거래"]
    ]
  },
  {
    title: "출금\n해지신청",
    rows: [
      ["[F2301] 펀드판매/해지신청", "※ 펀드 판매/해지 신청"],
      ["[F2302] 펀드출금/해지(당일/별단출금)", "※ 개인MMF 당일출금 / 일반펀드 별단출금"],
      ["[F2303] 펀드인출가능금액(해지예정조회)", "※ 인출가능(해지예정) 금액 조회"],
      ["[F2304] 펀드예탁금이자정리(일괄지급)", "※ 원리금지급계좌 미등록 사유로 미지급된 예탁금이자 정리"],
      ["[F2305] 펀드예탁금이자환급", "※ 예탁금 이자 환급처리"],
      ["[F2306] 펀드판매 본부승인신청(구속성 관련)", "※ 기안결재 후, 본부 승인 신청 등록"],
      ["[F2307] 승인계좌 판매신청(구속성 관련)", "※ 구속성 관련 본부 승인 받은 계좌에 한해 17시 이후 판매신청"]
    ]
  },
  {
    title: "거래취소",
    rows: [
      ["[F2401] 펀드거래취소", "※ 취소대상 거래 조회 및 취소처리"],
      ["[F2402] 펀드본부승인신청등록(거래취소)", "※ 거래취소 본부 승인 신청 등록/조회"],
      ["[F2403] 본부승인신청등록(사모펀드해지)", "※ 해지하고자 하는 사모펀드가 해당 상품의 마지막 계좌일 경우 사용"]
    ]
  }
] as const;
