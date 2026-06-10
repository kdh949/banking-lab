import { useState, type ReactNode } from "react";
import type { DialogState, IconName, PanelProps, ScreenControlMetadata, TableColumn, TableRow } from "./types";

export function Panel({ title, icon, tabs, actions, className = "", children }: PanelProps) {
  return (
    <article className={`iworks-panel ${className}`}>
      {title || tabs ? (
        <div className="panel-heading">
          <div>
            {icon ? <MaterialIcon name={icon} /> : null}
            {title ? <strong>{title}</strong> : null}
            {tabs ? (
              <div className="panel-tabs">
                {tabs.map((tab, index) => (
                  <button className={index === 0 ? "is-active" : ""} type="button" key={tab}>
                    {tab}
                  </button>
                ))}
              </div>
            ) : null}
          </div>
          {actions ? <div className="panel-actions">{actions}</div> : null}
        </div>
      ) : null}
      <div className="panel-body">{children}</div>
    </article>
  );
}

export function SearchBox({
  children,
  compact = false,
  controls
}: {
  readonly children: ReactNode;
  readonly compact?: boolean;
  readonly controls?: ScreenControlMetadata;
}) {
  return (
    <section className={`search-box ${compact ? "is-compact" : ""}`}>
      {controls?.reasonRequired ? (
        <label className="business-reason-field">
          <span>업무사유</span>
          <input aria-label="업무 사유" placeholder="고객 요청, 사고 조사 등 업무 목적 입력" />
        </label>
      ) : null}
      {children}
    </section>
  );
}

export function Field({
  label,
  type = "text",
  value,
  emphasized = false
}: {
  readonly label: string;
  readonly type?: "text" | "search" | "select" | "date" | "readonly";
  readonly value?: string;
  readonly emphasized?: boolean;
}) {
  const [lookupOpen, setLookupOpen] = useState(false);
  const controlClass = emphasized ? "is-emphasis" : "";
  return (
    <div className="field">
      <span>{label}</span>
      {type === "select" ? (
        <TerminalSelect className={controlClass} label={label} options={selectOptionsForField(label, value)} value={value ?? "-전체"} />
      ) : (
        <div className={`input-wrap ${type === "search" ? "has-search" : ""}`}>
          <input aria-label={label} className={controlClass} defaultValue={value} readOnly={type === "readonly"} type={type === "date" ? "text" : "text"} />
          {type === "search" ? (
            <button className="lookup-icon-button" type="button" aria-label={`${label} 추가 조회`} onClick={() => setLookupOpen(true)}>
              <MaterialIcon name="search" />
            </button>
          ) : null}
        </div>
      )}
      {lookupOpen ? <LookupDialog label={label} onClose={() => setLookupOpen(false)} /> : null}
    </div>
  );
}

export function TerminalSelect({ className, label, options, value }: { readonly className: string; readonly label: string; readonly options: readonly string[]; readonly value: string }) {
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState(value);

  return (
    <div className="terminal-select">
      <button className={`terminal-select-button ${className}`} type="button" aria-expanded={open} aria-label={label} onClick={() => setOpen((current) => !current)}>
        <span>{selected}</span>
        <MaterialIcon name="arrow_drop_down" />
      </button>
      {open ? (
        <div className="terminal-select-menu" role="listbox" aria-label={`${label} 선택`}>
          {options.map((option) => (
            <button
              className={option === selected ? "is-selected" : ""}
              type="button"
              role="option"
              aria-selected={option === selected}
              key={option}
              onClick={() => {
                setSelected(option);
                setOpen(false);
              }}
            >
              {option}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}

function selectOptionsForField(label: string, value?: string) {
  if (label === "조회구분") {
    return ["1-계좌번호", "2-변경전실명번호", "3-전행고객번호", "전체"];
  }
  if (label === "이자수수료종류") {
    return ["%", "전체", "이자", "수수료"];
  }
  if (label === "거래상태") {
    return ["%-전체", "정상", "취소", "미정리"];
  }
  if (label === "입출금구분") {
    return ["-전체", "입금", "출금"];
  }
  return Array.from(new Set([value ?? "-전체", "전체"]));
}

function LookupDialog({ label, onClose }: { readonly label: string; readonly onClose: () => void }) {
  const [keyword, setKeyword] = useState("");
  const rows = lookupRowsForField(label).filter((row) => `${row.code} ${row.name} ${row.detail}`.toLowerCase().includes(keyword.toLowerCase()));

  return (
    <div className="dialog-backdrop" role="presentation">
      <section className="terminal-dialog lookup-dialog" role="dialog" aria-modal="true" aria-labelledby="lookup-dialog-title">
        <header>
          <strong id="lookup-dialog-title">{label} 추가 조회</strong>
          <button type="button" aria-label="조회창 닫기" onClick={onClose}>
            <MaterialIcon name="close" />
          </button>
        </header>
        <div className="lookup-search-row">
          <span>검색어</span>
          <input autoFocus value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="번호 또는 이름 입력" />
          <button type="button">조회</button>
        </div>
        <div className="lookup-result-table">
          <div className="lookup-head">
            <span>구분</span>
            <span>명칭</span>
            <span>상세</span>
          </div>
          {rows.map((row) => (
            <button type="button" key={row.code} onClick={onClose}>
              <span>{row.code}</span>
              <span>{row.name}</span>
              <span>{row.detail}</span>
            </button>
          ))}
        </div>
        <footer>
          <button type="button" onClick={onClose}>
            선택
          </button>
          <button type="button" onClick={onClose}>
            닫기
          </button>
        </footer>
      </section>
    </div>
  );
}

function lookupRowsForField(label: string) {
  if (label.includes("고객")) {
    return [
      { code: "SYN-CUS-001", name: "김우리", detail: "개인 / 생년월일 마스킹" },
      { code: "SYN-CUS-002", name: "한상대", detail: "개인 / 실명확인 완료" },
      { code: "SYN-BIZ-001", name: "우리상사", detail: "법인 / 사업자번호 마스킹" }
    ];
  }
  if (label.includes("계좌") || label.includes("계약")) {
    return [
      { code: "1002-***-4421", name: "요구불 계좌", detail: "정상 / 본인확인 필요" },
      { code: "2001-***-0194", name: "펀드 계좌", detail: "정상 / 거래가능" },
      { code: "3004-***-7750", name: "외환 계약", detail: "미정리 수수료 있음" }
    ];
  }
  return [
    { code: "001", name: `${label} 기본조회`, detail: "현재 화면 조건으로 조회" },
    { code: "002", name: `${label} 상세조회`, detail: "추가 조건 입력 가능" }
  ];
}

export function RadioGroup({ label, options }: { readonly label: string; readonly options: readonly string[] }) {
  return (
    <fieldset className="radio-group">
      <legend>{label}</legend>
      {options.map((option, index) => (
        <label key={option}>
          <input defaultChecked={index === 0} name={label} type="radio" /> {option}
        </label>
      ))}
    </fieldset>
  );
}

export function DataTable({ columns, rows, minRows = 0 }: { readonly columns: readonly TableColumn[]; readonly rows: readonly TableRow[]; readonly minRows?: number }) {
  const filler = Array.from({ length: Math.max(0, minRows - rows.length) });
  return (
    <div className="data-table-scroll">
      <table className="data-table">
        <colgroup>
          {columns.map((column) => (
            <col style={column.width ? { width: column.width } : undefined} key={column.key} />
          ))}
        </colgroup>
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key}>{column.label}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, rowIndex) => (
            <tr key={`row-${rowIndex}`}>
              {columns.map((column) => (
                <td key={column.key}>{row[column.key]}</td>
              ))}
            </tr>
          ))}
          {filler.map((_, index) => (
            <tr className="empty-row" key={`empty-${index}`}>
              {columns.map((column) => (
                <td key={column.key}>&nbsp;</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function AppDialog({ dialog, onClose }: { readonly dialog: DialogState; readonly onClose: () => void }) {
  return (
    <div className="dialog-backdrop" role="presentation">
      <section className="terminal-dialog unavailable-dialog" role="dialog" aria-modal="true" aria-labelledby="unavailable-dialog-title">
        <button className="dialog-close" type="button" aria-label="닫기" onClick={onClose}>
          <MaterialIcon name="close" />
        </button>
        <div className="dialog-x-mark">
          <MaterialIcon name="close" />
        </div>
        <strong id="unavailable-dialog-title">{dialog.title}</strong>
        <p>{dialog.message}</p>
        <button className="dialog-confirm" type="button" onClick={onClose}>
          확인
        </button>
      </section>
    </div>
  );
}

export function MaterialIcon({ name }: { readonly name: IconName }) {
  return (
    <span className="material-symbols-outlined iworks-icon" aria-hidden="true">
      {name}
    </span>
  );
}
