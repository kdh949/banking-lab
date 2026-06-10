"use client";

import { useState } from "react";
import { createBankingApiClient, type StaffCustomerDetailDto } from "@banking-lab/api-client";
import { createSimulatorBearerToken } from "@banking-lab/auth-client";
import { MaterialIcon } from "./primitives";

type EvidenceState =
  | { readonly status: "offline"; readonly message: string }
  | { readonly status: "idle"; readonly message: string }
  | { readonly status: "running"; readonly message: string }
  | {
      readonly status: "loaded";
      readonly customer: StaffCustomerDetailDto;
      readonly auditEventId: string;
      readonly approvalCount: number;
    }
  | { readonly status: "failed"; readonly message: string };

const apiBaseUrl = process.env.NEXT_PUBLIC_BANKING_API_BASE_URL ?? "";
const simulatorTokensEnabled = process.env.NEXT_PUBLIC_BANKING_SIMULATOR_TOKENS_ENABLED === "true";
const evidenceReason = "Browser staff-terminal Spring API evidence smoke";

export function StaffApiEvidencePanel() {
  const [state, setState] = useState<EvidenceState>(() => {
    if (!apiBaseUrl) {
      return { status: "offline", message: "Spring API 미설정" };
    }
    if (!simulatorTokensEnabled) {
      return { status: "offline", message: "simulator token opt-in 필요" };
    }
    return { status: "idle", message: "대기" };
  });

  const runEvidence = async () => {
    if (!apiBaseUrl || !simulatorTokensEnabled || state.status === "running") {
      return;
    }
    setState({ status: "running", message: "조회 중" });
    try {
      const client = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "staff-terminal01",
          audience: "core-banking-api",
          roles: ["BRANCH_STAFF"]
        })
      });
      const [customerDetail, approvals] = await Promise.all([
        client.staffCustomerDetail("SYN-CUS-001", evidenceReason),
        client.staffApprovals()
      ]);
      setState({
        status: "loaded",
        customer: customerDetail.item,
        auditEventId: customerDetail.auditEventId,
        approvalCount: approvals.length
      });
    } catch (error: unknown) {
      setState({ status: "failed", message: error instanceof Error ? error.message : "Spring API 조회 실패" });
    }
  };

  return (
    <section className="staff-api-evidence" aria-label="Spring API 연동 증적" data-testid="staff-terminal-api-evidence">
      <div className="staff-api-evidence__header">
        <span>
          <MaterialIcon name="language" /> Spring API
        </span>
        <button type="button" onClick={runEvidence} disabled={state.status === "offline" || state.status === "running"}>
          조회
        </button>
      </div>
      <dl>
        <div>
          <dt>상태</dt>
          <dd>{state.status === "loaded" ? "API-backed" : state.message}</dd>
        </div>
        <div>
          <dt>고객</dt>
          <dd>{state.status === "loaded" ? state.customer.customerId : "미조회"}</dd>
        </div>
        <div>
          <dt>마스킹</dt>
          <dd>{state.status === "loaded" ? state.customer.maskedPhone ?? state.customer.piiExposure : "기본"}</dd>
        </div>
        <div>
          <dt>승인함</dt>
          <dd>{state.status === "loaded" ? `${state.approvalCount}건` : "미조회"}</dd>
        </div>
        <div>
          <dt>감사</dt>
          <dd>{state.status === "loaded" ? state.auditEventId : "reason-required"}</dd>
        </div>
      </dl>
    </section>
  );
}
