"use client";

import { useState } from "react";
import {
  createCallCenterApiClient,
  type CallCenterCustomerSummaryDto,
  type CallCenterEscalationDto,
  type CallCenterInteractionDto,
  type CallCenterNoteDto,
  type StaffJourneyResponse
} from "@banking-lab/api-client/call-center";
import {
  ChannelBadge,
  ChannelMetric,
  ChannelMetricGrid,
  ChannelPanel,
  ChannelShell,
  ChannelTable
} from "../../../../packages/channel-ui/src/operator-workbench";

type SoftphoneState = "IDLE" | "RINGING" | "CONNECTED" | "WRAP_UP";
type ActionState = "IDLE" | "RUNNING" | "DONE" | "FAILED";

const apiBaseUrl = process.env.NEXT_PUBLIC_BANKING_API_BASE_URL ?? "";

export function CallCenterWorkspace() {
  const [softphone, setSoftphone] = useState<SoftphoneState>("IDLE");
  const [query, setQuery] = useState("");
  const [reason, setReason] = useState("");
  const [journeyId, setJourneyId] = useState("");
  const [customer, setCustomer] = useState<CallCenterCustomerSummaryDto | null>(null);
  const [interaction, setInteraction] = useState<CallCenterInteractionDto | null>(null);
  const [journey, setJourney] = useState<StaffJourneyResponse | null>(null);
  const [noteBody, setNoteBody] = useState("");
  const [note, setNote] = useState<CallCenterNoteDto | null>(null);
  const [escalation, setEscalation] = useState<CallCenterEscalationDto | null>(null);
  const [state, setState] = useState<ActionState>("IDLE");
  const [message, setMessage] = useState("Enter a business reason before customer lookup.");

  const client = () => createCallCenterApiClient({ baseUrl: apiBaseUrl });
  const ready = Boolean(apiBaseUrl);
  const validReason = reason.trim().length >= 3;

  const run = async (action: () => Promise<void>) => {
    setState("RUNNING");
    try {
      await action();
      setState("DONE");
    } catch (error: unknown) {
      setState("FAILED");
      setMessage(error instanceof Error ? error.message : "Call-center workflow failed");
    }
  };

  const search = () => run(async () => {
    const response = await client().searchCallCenterCustomers(query, reason);
    const selected = response.items[0];
    setCustomer(selected ?? null);
    setMessage(selected ? "Masked customer context loaded and audited." : "No synthetic customer matched the query.");
  });

  const loadJourney = async () => {
    if (!journeyId) {
      return;
    }
    const response = await client().staffJourney(journeyId, reason);
    setJourney(response);
  };

  const startInteraction = () => run(async () => {
    if (!customer) {
      throw new Error("Search and select a customer first.");
    }
    const started = await client().startCallCenterInteraction({
      customerId: customer.customerId,
      journeyId: journeyId || null,
      channel: "PHONE",
      contactReasonCode: "HELD_TRANSFER_STATUS",
      requestedBy: "call-agent01",
      requestedByRole: "CALL_CENTER_AGENT",
      assignedTo: "call-agent01",
      reason,
      metadata: { syntheticOnly: true, softphone: "SIMULATED" }
    });
    setInteraction(started.item);
    setSoftphone("CONNECTED");
    if (journeyId) {
      await loadJourney();
    }
    setMessage("Interaction opened with masked customer context.");
  });

  const saveNote = () => run(async () => {
    if (!interaction) {
      throw new Error("Start an interaction before adding a note.");
    }
    const response = await client().addCallCenterNote(interaction.interactionId, {
      requestedBy: "call-agent01",
      requestedByRole: "CALL_CENTER_AGENT",
      reason,
      noteBody
    });
    setInteraction(response.item);
    setNote(response.note);
    setNoteBody("");
    if (journeyId) {
      await loadJourney();
    }
    setMessage("Note redacted before persistence; raw text was not copied to audit or journey events.");
  });

  const handoffToFds = () => run(async () => {
    if (!interaction) {
      throw new Error("Start an interaction before FDS handoff.");
    }
    const response = await client().escalateCallCenterInteraction(interaction.interactionId, {
      requestedBy: "call-agent01",
      requestedByRole: "CALL_CENTER_AGENT",
      reason,
      escalationType: "FDS",
      metadata: { journeyId: journeyId || null, syntheticOnly: true }
    });
    setInteraction(response.item);
    setEscalation(response.escalation);
    if (journeyId) {
      await loadJourney();
    }
    setMessage("FDS handoff requested; the held transfer remains ledger-free.");
  });

  const advanceSoftphone = () => {
    const next: Record<SoftphoneState, SoftphoneState> = {
      IDLE: "RINGING",
      RINGING: "CONNECTED",
      CONNECTED: "WRAP_UP",
      WRAP_UP: "IDLE"
    };
    setSoftphone(next[softphone]);
  };

  return (
    <ChannelShell appId="call-center-console" eyebrow="Call-Center Console" title="Agent Workspace" status="Synthetic masked workflow only">
      <ChannelMetricGrid>
        <ChannelMetric label="Softphone" value={softphone} detail="Simulator · no live telephony" />
        <ChannelMetric label="Customer lookup" value="Reason required" detail="Masked by default" />
        <ChannelMetric label="Case notes" value={note?.redactionApplied ? "Redacted" : "Ready"} detail="Raw note persistence forbidden" />
        <ChannelMetric label="FDS handoff" value={escalation?.status ?? "Ready"} detail="Held transfer remains unposted" />
      </ChannelMetricGrid>

      {!ready ? (
        <ChannelPanel title="API configuration required" eyebrow="product boundary">
          <p>NEXT_PUBLIC_BANKING_API_BASE_URL is not configured. Product controls remain disabled; lab API evidence stays under `/lab/evidence`.</p>
        </ChannelPanel>
      ) : null}

      <section className="call-workspace-grid" aria-label="Call-center held-transfer workspace">
        <ChannelPanel title="Softphone simulator" eyebrow="no live telephony" meta={<ChannelBadge>{softphone}</ChannelBadge>}>
          <p>Advance a synthetic inbound call through ring, connected, and wrap-up states.</p>
          <button type="button" onClick={advanceSoftphone}>{softphone === "IDLE" ? "Simulate inbound call" : "Advance call state"}</button>
        </ChannelPanel>

        <ChannelPanel title="Reason-gated masked customer 360" eyebrow="CALL-101 · CALL-102">
          <div className="call-form-grid">
            <label>Business reason<input value={reason} onChange={(event) => setReason(event.target.value)} required /></label>
            <label>Customer query<input value={query} onChange={(event) => setQuery(event.target.value)} required /></label>
            <label>Journey ID<input value={journeyId} onChange={(event) => setJourneyId(event.target.value)} placeholder="JRN-…" /></label>
          </div>
          <div className="call-actions">
            <button type="button" onClick={search} disabled={!ready || !validReason || !query || state === "RUNNING"}>Search masked customer</button>
            <button type="button" onClick={startInteraction} disabled={!customer || !validReason || state === "RUNNING"}>Start linked interaction</button>
          </div>
          {customer ? (
            <dl className="call-definition-list" data-testid="masked-customer-context">
              <div><dt>Customer</dt><dd>{customer.customerId}</dd></div>
              <div><dt>Name</dt><dd>{customer.maskedName}</dd></div>
              <div><dt>Phone</dt><dd>{customer.maskedPhone ?? "not available"}</dd></div>
              <div><dt>Risk band</dt><dd>{customer.riskGrade}</dd></div>
            </dl>
          ) : null}
        </ChannelPanel>

        <ChannelPanel title="Redacted interaction note" eyebrow="CALL-103">
          <label className="call-note-label">Agent note<textarea value={noteBody} onChange={(event) => setNoteBody(event.target.value)} rows={5} /></label>
          <button type="button" onClick={saveNote} disabled={!interaction || !noteBody || !validReason || state === "RUNNING"}>Save redacted note</button>
          {note ? <p data-testid="redacted-note-proof">Redaction {note.redactionApplied ? "applied" : "not needed"} · {note.piiPatternCount} pattern(s)</p> : null}
        </ChannelPanel>

        <ChannelPanel title="FDS handoff" eyebrow="CALL-106">
          <p>Send the interaction and journey reference to the controlled FDS queue. This action does not post a ledger transaction.</p>
          <button type="button" onClick={handoffToFds} disabled={!interaction || !journeyId || !validReason || state === "RUNNING"}>Request FDS handoff</button>
          {escalation ? <p data-testid="fds-handoff-proof">{escalation.escalationId} · {escalation.status}</p> : null}
        </ChannelPanel>
      </section>

      <ChannelPanel title="Journey timeline" eyebrow="cross-channel correlation">
        {journey ? (
          <div data-testid="call-center-journey">
            <p><strong>{journey.item.journeyId}</strong> · {journey.item.status}</p>
            <ChannelTable>
              <thead><tr><th>Event</th><th>Status</th><th>Reference</th><th>Time</th></tr></thead>
              <tbody>
                {journey.item.events.map((event) => (
                  <tr key={event.eventId}>
                    <td>{event.eventType}</td>
                    <td>{event.status}</td>
                    <td>{event.sourceReferenceId ?? "internal"}</td>
                    <td><time dateTime={event.createdAt}>{event.createdAt}</time></td>
                  </tr>
                ))}
              </tbody>
            </ChannelTable>
          </div>
        ) : <p>Link a held-transfer journey to load the audited staff timeline.</p>}
      </ChannelPanel>

      <p className={`call-workspace-message is-${state.toLowerCase()}`} aria-live="polite">{message}</p>
    </ChannelShell>
  );
}
