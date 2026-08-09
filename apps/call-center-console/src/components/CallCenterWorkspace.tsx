import {
  ChannelBadge,
  ChannelCard,
  ChannelCardGrid,
  ChannelMetric,
  ChannelMetricGrid,
  ChannelPanel,
  ChannelShell
} from "../../../../packages/channel-ui/src";

export function CallCenterWorkspace() {
  return (
    <ChannelShell appId="call-center-console" eyebrow="Call-Center Console" title="Agent Workspace" status="Synthetic masked workflow only">
      <ChannelMetricGrid>
        <ChannelMetric label="Softphone" value="Ready" detail="No live telephony" />
        <ChannelMetric label="Customer lookup" value="Reason required" detail="Masked by default" />
        <ChannelMetric label="Case notes" value="Redacted" detail="Raw note persistence forbidden" />
        <ChannelMetric label="Escalation" value="Controlled" detail="Role and approval gated" />
      </ChannelMetricGrid>

      <ChannelPanel title="Start an interaction" eyebrow="Agent workflow" meta={<ChannelBadge>Product route</ChannelBadge>}>
        <p>Search for a synthetic customer with a business reason, then start a masked interaction workspace.</p>
        <a href="/workspace">Open agent workspace</a>
      </ChannelPanel>

      <ChannelCardGrid density="wide">
        <ChannelCard screenId="LOOKUP" title="Reason-gated customer search" meta="masked 360">
          <p>Customer PII remains masked until a permitted workflow explicitly requires more detail.</p>
        </ChannelCard>
        <ChannelCard screenId="NOTES" title="Interaction notes" meta="redaction">
          <p>Notes are redacted before persistence and appear on the interaction timeline.</p>
        </ChannelCard>
        <ChannelCard screenId="FDS_HANDOFF" title="FDS escalation" meta="controlled handoff">
          <p>Held transfers can be handed to the fraud review queue without posting to the ledger.</p>
        </ChannelCard>
      </ChannelCardGrid>
    </ChannelShell>
  );
}
