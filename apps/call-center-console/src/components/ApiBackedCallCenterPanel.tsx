"use client";

import { useEffect, useState } from "react";
import {
  createBankingApiClient,
  type CallCenterCustomerSummaryDto,
  type CallCenterInteractionDto
} from "@banking-lab/api-client";
import { createSimulatorBearerToken } from "@banking-lab/auth-client";

type SearchState =
  | { readonly status: "offline" }
  | { readonly status: "loading" }
  | { readonly status: "loaded"; readonly auditEventId: string; readonly customer: CallCenterCustomerSummaryDto }
  | { readonly status: "empty"; readonly auditEventId: string }
  | { readonly status: "failed"; readonly message: string };

type WorkflowState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | {
      readonly status: "completed";
      readonly interaction: CallCenterInteractionDto;
      readonly noteRedactionApplied: boolean;
      readonly notePiiPatternCount: number;
      readonly aftercallTaskStatus: string;
      readonly escalationType: string;
      readonly complaintCaseId: string;
      readonly historyCount: number;
    }
  | { readonly status: "failed"; readonly message: string };

const apiBaseUrl = process.env.NEXT_PUBLIC_BANKING_API_BASE_URL ?? "";
const searchReason = "Browser CALL-101 synthetic customer search smoke";
const workflowReason = "Browser CALL-102 through CALL-106 synthetic workflow smoke";

export function ApiBackedCallCenterPanel() {
  const [searchState, setSearchState] = useState<SearchState>(() => (apiBaseUrl ? { status: "loading" } : { status: "offline" }));
  const [workflowState, setWorkflowState] = useState<WorkflowState>({ status: "idle" });

  useEffect(() => {
    if (!apiBaseUrl) {
      return;
    }
    let cancelled = false;

    callCenterAgentClient()
      .searchCallCenterCustomers("SYN-CUS", searchReason)
      .then((response) => {
        if (cancelled) {
          return;
        }
        const customer = response.items[0];
        setSearchState(customer ? { status: "loaded", auditEventId: response.auditEventId, customer } : { status: "empty", auditEventId: response.auditEventId });
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setSearchState({ status: "failed", message: error instanceof Error ? error.message : "Unknown call-center API failure" });
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const runWorkflowSmoke = async () => {
    if (!apiBaseUrl || searchState.status !== "loaded") {
      return;
    }
    setWorkflowState({ status: "running" });
    try {
      const agentClient = callCenterAgentClient();
      const managerClient = callCenterManagerClient();
      const customerId = searchState.customer.customerId;
      const started = await agentClient.startCallCenterInteraction({
        customerId,
        channel: "PHONE",
        contactReasonCode: "BALANCE_INQUIRY",
        requestedBy: "call-agent01",
        requestedByRole: "CALL_CENTER_AGENT",
        assignedTo: "call-agent01",
        reason: workflowReason,
        metadata: {
          syntheticOnly: true,
          screenPath: "CALL-102"
        }
      });
      const note = await agentClient.addCallCenterNote(started.item.interactionId, {
        requestedBy: "call-agent01",
        requestedByRole: "CALL_CENTER_AGENT",
        reason: "Browser CALL-103 redacted synthetic note smoke",
        noteBody: "Synthetic lab note mentions 010-1111-2222 and lab-only account-like 123456789012."
      });
      const aftercall = await agentClient.createCallCenterAftercallTask(started.item.interactionId, {
        requestedBy: "call-agent01",
        requestedByRole: "CALL_CENTER_AGENT",
        reason: "Browser CALL-104 synthetic aftercall task smoke",
        taskType: "FOLLOW_UP",
        assignedTo: "call-agent02",
        metadata: {
          syntheticOnly: true
        }
      });
      const escalation = await managerClient.escalateCallCenterInteraction(started.item.interactionId, {
        requestedBy: "call-manager01",
        requestedByRole: "CALL_CENTER_MANAGER",
        reason: "Browser CALL-106 synthetic complaint escalation smoke",
        escalationType: "COMPLAINT",
        complaintCategory: "ACCOUNT_ACCESS",
        complaintDescription: "Synthetic complaint converted from call-center browser smoke with 010-2222-3333 redacted.",
        metadata: {
          syntheticOnly: true
        }
      });
      await agentClient.closeCallCenterInteraction(started.item.interactionId, {
        requestedBy: "call-agent01",
        requestedByRole: "CALL_CENTER_AGENT",
        reason: "Browser CALL-102 synthetic close smoke"
      });
      const history = await agentClient.callCenterCustomerHistory(customerId, "Browser CALL-105 synthetic history smoke");
      const finalInteraction = await agentClient.callCenterInteraction(started.item.interactionId, "Browser CALL-102 synthetic detail smoke");

      setWorkflowState({
        status: "completed",
        interaction: finalInteraction.item,
        noteRedactionApplied: note.note.redactionApplied,
        notePiiPatternCount: note.note.piiPatternCount,
        aftercallTaskStatus: aftercall.task.status,
        escalationType: escalation.escalation.escalationType,
        complaintCaseId: escalation.escalation.complaintCaseId ?? "none",
        historyCount: history.items.length
      });
    } catch (error: unknown) {
      setWorkflowState({ status: "failed", message: error instanceof Error ? error.message : "Unknown call-center workflow failure" });
    }
  };

  return (
    <section className="api-panel" aria-label="API-backed call-center workflow">
      <h2>Spring API call-center smoke</h2>
      <dl data-testid="api-backed-call-center-search">
        <div>
          <dt>Search state</dt>
          <dd>{searchStatusText(searchState)}</dd>
        </div>
        <div>
          <dt>Customer</dt>
          <dd>{searchState.status === "loaded" ? searchState.customer.customerId : "none"}</dd>
        </div>
        <div>
          <dt>PII policy</dt>
          <dd>masked by default</dd>
        </div>
      </dl>

      <div className="api-actions" data-testid="api-backed-call-center-workflow">
        <button type="button" onClick={runWorkflowSmoke} disabled={searchState.status !== "loaded" || workflowState.status === "running"}>
          Run call-center workflow smoke
        </button>
        <dl>
          <div>
            <dt>Workflow state</dt>
            <dd>{workflowStatusText(workflowState)}</dd>
          </div>
          <div>
            <dt>Interaction</dt>
            <dd>{workflowState.status === "completed" ? workflowState.interaction.interactionId : "not run"}</dd>
          </div>
          <div>
            <dt>Final status</dt>
            <dd>{workflowState.status === "completed" ? workflowState.interaction.status : "not run"}</dd>
          </div>
          <div>
            <dt>Redaction</dt>
            <dd>
              {workflowState.status === "completed"
                ? `${workflowState.noteRedactionApplied ? "applied" : "not applied"} / ${workflowState.notePiiPatternCount} patterns`
                : "not run"}
            </dd>
          </div>
          <div>
            <dt>Escalation</dt>
            <dd>{workflowState.status === "completed" ? `${workflowState.escalationType} ${workflowState.complaintCaseId}` : "not run"}</dd>
          </div>
          <div>
            <dt>History count</dt>
            <dd>{workflowState.status === "completed" ? workflowState.historyCount : "not run"}</dd>
          </div>
        </dl>
      </div>
    </section>
  );
}

function callCenterAgentClient() {
  return createBankingApiClient({
    baseUrl: apiBaseUrl,
    bearerToken: createSimulatorBearerToken({
      subject: "call-agent01",
      roles: ["CALL_CENTER_AGENT"]
    })
  });
}

function callCenterManagerClient() {
  return createBankingApiClient({
    baseUrl: apiBaseUrl,
    bearerToken: createSimulatorBearerToken({
      subject: "call-manager01",
      roles: ["CALL_CENTER_MANAGER"]
    })
  });
}

function searchStatusText(state: SearchState): string {
  switch (state.status) {
    case "offline":
      return "offline - configure NEXT_PUBLIC_BANKING_API_BASE_URL";
    case "loading":
      return "loading";
    case "loaded":
      return "customers loaded";
    case "empty":
      return "no synthetic customers returned";
    case "failed":
      return state.message;
  }
}

function workflowStatusText(state: WorkflowState): string {
  switch (state.status) {
    case "idle":
      return "not run";
    case "running":
      return "running";
    case "completed":
      return "call-center workflow completed";
    case "failed":
      return state.message;
  }
}
