"use client";

import { useEffect, useState } from "react";
import {
  createCallCenterApiClient,
  type CallCenterAftercallTaskDto,
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

type SoftphoneState = "IDLE" | "RINGING" | "CONNECTED" | "HOLD" | "AFTER_CALL";
type IdentityVerificationState = "NOT_STARTED" | "PASSED_SIMULATED" | "FAILED_SIMULATED";
type ActionState = "IDLE" | "RUNNING" | "DONE" | "FAILED";

type BffSession = {
  readonly authenticated: true;
  readonly subject: string;
  readonly roles: readonly string[];
  readonly displayName: string;
  readonly expiresAt: string;
  readonly mode: string;
};

export function CallCenterWorkspace() {
  const [session, setSession] = useState<BffSession | null>(null);
  const [softphone, setSoftphone] = useState<SoftphoneState>("IDLE");
  const [identityVerification, setIdentityVerification] = useState<IdentityVerificationState>("NOT_STARTED");
  const [query, setQuery] = useState("");
  const [reason, setReason] = useState("");
  const [journeyId, setJourneyId] = useState("");
  const [customer, setCustomer] = useState<CallCenterCustomerSummaryDto | null>(null);
  const [interaction, setInteraction] = useState<CallCenterInteractionDto | null>(null);
  const [journey, setJourney] = useState<StaffJourneyResponse | null>(null);
  const [noteBody, setNoteBody] = useState("");
  const [note, setNote] = useState<CallCenterNoteDto | null>(null);
  const [escalation, setEscalation] = useState<CallCenterEscalationDto | null>(null);
  const [disposition, setDisposition] = useState("FDS_HANDOFF_COMPLETED");
  const [aftercallTask, setAftercallTask] = useState<CallCenterAftercallTaskDto | null>(null);
  const [state, setState] = useState<ActionState>("IDLE");
  const [message, setMessage] = useState("Enter a business reason before customer lookup.");

  useEffect(() => {
    void refreshSession();
  }, []);

  const client = () => createCallCenterApiClient({ baseUrl: window.location.origin });
  const ready = Boolean(session?.roles.includes("CALL_CENTER_AGENT") || session?.roles.includes("CALL_CENTER_MANAGER"));
  const validReason = reason.trim().length >= 3;
  const hasOpenInteraction = Boolean(interaction && interaction.status !== "CLOSED");

  const refreshSession = async () => {
    try {
      const response = await fetch("/api/session", { cache: "no-store", credentials: "same-origin" });
      const value = await response.json() as BffSession | { readonly authenticated: false };
      setSession(value.authenticated ? value as BffSession : null);
    } catch {
      setSession(null);
      setState("FAILED");
      setMessage("The BFF session endpoint is unavailable.");
    }
  };

  const simulatedLogin = async () => {
    const response = await fetch("/api/session/simulated", {
      method: "POST",
      credentials: "same-origin",
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      body: JSON.stringify({ actorKey: "call-agent" })
    });
    if (!response.ok) {
      setState("FAILED");
      setMessage("Simulated BFF login is disabled; use Keycloak or explicit dev/test opt-in.");
      return;
    }
    await refreshSession();
  };

  const signOut = async () => {
    await fetch("/api/session", { method: "DELETE", credentials: "same-origin" });
    setSession(null);
  };

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
    if (hasOpenInteraction) {
      throw new Error("Close the active interaction before selecting another customer.");
    }
    const response = await client().searchCallCenterCustomers(query, reason);
    const selected = response.items[0];
    setCustomer(selected ?? null);
    setIdentityVerification("NOT_STARTED");
    setInteraction(null);
    setJourney(null);
    setNote(null);
    setNoteBody("");
    setEscalation(null);
    setAftercallTask(null);
    setSoftphone("IDLE");
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
    if (identityVerification !== "PASSED_SIMULATED") {
      throw new Error("Pass the explicit simulated identity check before starting an interaction.");
    }
    if (hasOpenInteraction) {
      throw new Error("Close the active interaction before starting another one.");
    }
    const started = await client().startCallCenterInteraction({
      customerId: customer.customerId,
      journeyId: journeyId || null,
      channel: "PHONE",
      contactReasonCode: "HELD_TRANSFER_STATUS",
      requestedBy: session?.subject,
      requestedByRole: session?.roles.includes("CALL_CENTER_AGENT") ? "CALL_CENTER_AGENT" : "CALL_CENTER_MANAGER",
      assignedTo: session?.subject,
      reason,
      metadata: {
        syntheticOnly: true,
        softphone: "SIMULATED",
        identityVerification
      }
    });
    setInteraction(started.item);
    setNote(null);
    setNoteBody("");
    setEscalation(null);
    setAftercallTask(null);
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
    if (interaction.status === "CLOSED") {
      throw new Error("Closed interactions cannot accept new notes.");
    }
    const response = await client().addCallCenterNote(interaction.interactionId, {
      requestedBy: session?.subject,
      requestedByRole: session?.roles.includes("CALL_CENTER_AGENT") ? "CALL_CENTER_AGENT" : "CALL_CENTER_MANAGER",
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
    if (escalation) {
      throw new Error("This interaction already has an FDS handoff.");
    }
    const response = await client().escalateCallCenterInteraction(interaction.interactionId, {
      requestedBy: session?.subject,
      requestedByRole: session?.roles.includes("CALL_CENTER_AGENT") ? "CALL_CENTER_AGENT" : "CALL_CENTER_MANAGER",
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

  const saveDisposition = () => run(async () => {
    if (!interaction) {
      throw new Error("Start an interaction before after-call disposition.");
    }
    if (!escalation) {
      throw new Error("Complete the FDS handoff before saving the after-call disposition.");
    }
    if (aftercallTask) {
      throw new Error("The after-call disposition is already saved.");
    }
    const response = await client().createCallCenterAftercallTask(interaction.interactionId, {
      requestedBy: session?.subject,
      requestedByRole: session?.roles.includes("CALL_CENTER_AGENT") ? "CALL_CENTER_AGENT" : "CALL_CENTER_MANAGER",
      reason,
      taskType: disposition,
      assignedTo: session?.subject,
      metadata: {
        disposition,
        identityVerification,
        journeyId: journeyId || null,
        syntheticOnly: true
      }
    });
    setInteraction(response.item);
    setAftercallTask(response.task);
    setSoftphone("AFTER_CALL");
    setMessage("After-call disposition persisted with an audited synthetic task.");
  });

  const closeInteraction = () => run(async () => {
    if (!interaction) {
      throw new Error("Start an interaction before closing it.");
    }
    if (!aftercallTask) {
      throw new Error("Save an after-call disposition before closing the interaction.");
    }
    const response = await client().closeCallCenterInteraction(interaction.interactionId, {
      requestedBy: session?.subject,
      requestedByRole: session?.roles.includes("CALL_CENTER_AGENT") ? "CALL_CENTER_AGENT" : "CALL_CENTER_MANAGER",
      reason
    });
    setInteraction(response.item);
    setSoftphone("IDLE");
    setIdentityVerification("NOT_STARTED");
    if (journeyId) {
      await loadJourney();
    }
    setMessage("Interaction closed after the audited after-call disposition.");
  });

  const advanceSoftphone = () => {
    const next: Record<SoftphoneState, SoftphoneState> = {
      IDLE: "RINGING",
      RINGING: "CONNECTED",
      CONNECTED: "HOLD",
      HOLD: "AFTER_CALL",
      AFTER_CALL: "IDLE"
    };
    setSoftphone(next[softphone]);
  };

  return (
    <ChannelShell appId="call-center-console" eyebrow="Call-Center Console" title="Agent Workspace" status="Synthetic masked workflow only">
      <ChannelMetricGrid>
        <ChannelMetric label="Softphone" value={softphone} detail="Simulator · no live telephony" />
        <ChannelMetric label="Identity" value={identityVerification} detail="Explicit simulator · no real identity proofing" />
        <ChannelMetric label="Customer lookup" value="Reason required" detail="Masked by default" />
        <ChannelMetric label="Case notes" value={note?.redactionApplied ? "Redacted" : "Ready"} detail="Raw note persistence forbidden" />
        <ChannelMetric label="FDS handoff" value={escalation?.status ?? "Ready"} detail="Held transfer remains unposted" />
      </ChannelMetricGrid>

      {!session || !ready ? (
        <ChannelPanel title="Authenticated BFF session required" eyebrow="product boundary">
          <p>Product controls call Spring only through a same-origin BFF. Browser JavaScript cannot read the HttpOnly session credential or bearer token.</p>
          <div className="call-actions">
            <a href="/api/session/login?returnTo=/workspace">Sign in with Keycloak</a>
            <button type="button" onClick={() => void simulatedLogin()}>Dev/test simulated agent</button>
          </div>
        </ChannelPanel>
      ) : (
        <ChannelPanel title="Agent session" eyebrow="opaque HttpOnly BFF session">
          <p>{session.displayName} · {session.roles.join(", ")} · {session.mode}</p>
          <button type="button" onClick={() => void signOut()}>Sign out</button>
        </ChannelPanel>
      )}

      <section className="call-workspace-grid" aria-label="Call-center held-transfer workspace">
        <ChannelPanel title="Softphone simulator" eyebrow="no live telephony" meta={<ChannelBadge>{softphone}</ChannelBadge>}>
          <p>Advance a synthetic inbound call through ringing, connected, hold, and after-call states.</p>
          <button type="button" onClick={advanceSoftphone}>{softphone === "IDLE" ? "Simulate inbound call" : "Advance call state"}</button>
        </ChannelPanel>

        <ChannelPanel title="Reason-gated masked customer 360" eyebrow="CALL-101 · CALL-102">
          <div className="call-form-grid">
            <label>Business reason<input value={reason} onChange={(event) => setReason(event.target.value)} required /></label>
            <label>Customer query<input value={query} onChange={(event) => setQuery(event.target.value)} required /></label>
            <label>Journey ID<input value={journeyId} onChange={(event) => setJourneyId(event.target.value)} placeholder="JRN-…" /></label>
          </div>
          <div className="call-actions">
            <button type="button" onClick={search} disabled={!ready || !validReason || !query || hasOpenInteraction || state === "RUNNING"}>Search masked customer</button>
            <button type="button" onClick={() => setIdentityVerification("PASSED_SIMULATED")} disabled={!customer || hasOpenInteraction || state === "RUNNING"}>Pass simulated identity check</button>
            <button type="button" onClick={() => setIdentityVerification("FAILED_SIMULATED")} disabled={!customer || hasOpenInteraction || state === "RUNNING"}>Fail simulated identity check</button>
            <button type="button" onClick={startInteraction} disabled={!customer || identityVerification !== "PASSED_SIMULATED" || !validReason || hasOpenInteraction || state === "RUNNING"}>Start linked interaction</button>
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
          <button type="button" onClick={saveNote} disabled={!interaction || interaction.status === "CLOSED" || !noteBody || !validReason || state === "RUNNING"}>Save redacted note</button>
          {note ? <p data-testid="redacted-note-proof">Redaction {note.redactionApplied ? "applied" : "not needed"} · {note.piiPatternCount} pattern(s)</p> : null}
        </ChannelPanel>

        <ChannelPanel title="FDS handoff" eyebrow="CALL-106">
          <p>Send the interaction and journey reference to the controlled FDS queue. This action does not post a ledger transaction.</p>
          <button type="button" onClick={handoffToFds} disabled={!interaction || interaction.status === "CLOSED" || !journeyId || !validReason || Boolean(escalation) || state === "RUNNING"}>Request FDS handoff</button>
          {escalation ? <p data-testid="fds-handoff-proof">{escalation.escalationId} · {escalation.status}</p> : null}
        </ChannelPanel>

        <ChannelPanel title="After-call disposition" eyebrow="CALL-104 · CALL-102">
          <label className="call-note-label">
            Disposition
            <select value={disposition} onChange={(event) => setDisposition(event.target.value)}>
              <option value="FDS_HANDOFF_COMPLETED">FDS handoff completed</option>
              <option value="CUSTOMER_ADVISED">Customer advised</option>
              <option value="FOLLOW_UP_REQUIRED">Follow-up required</option>
            </select>
          </label>
          <div className="call-actions">
            <button type="button" onClick={saveDisposition} disabled={!interaction || interaction.status === "CLOSED" || !escalation || Boolean(aftercallTask) || !validReason || state === "RUNNING"}>Save after-call disposition</button>
            <button type="button" onClick={closeInteraction} disabled={!interaction || !aftercallTask || interaction.status === "CLOSED" || !validReason || state === "RUNNING"}>Close interaction</button>
          </div>
          {aftercallTask ? <p data-testid="aftercall-disposition-proof">{aftercallTask.taskType} · {aftercallTask.status}</p> : null}
          {interaction?.status === "CLOSED" ? <p data-testid="interaction-closed-proof">{interaction.interactionId} · CLOSED</p> : null}
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
