import { ChannelShell } from "../../../../../../packages/channel-ui/src/operator-workbench";
import { ApiBackedCallCenterPanel } from "../../../components/ApiBackedCallCenterPanel";

export default function CallCenterEvidencePage() {
  return (
    <ChannelShell appId="call-center-console" eyebrow="Lab · API evidence" title="Call-Center API Evidence" status="LAB_ONLY · synthetic smoke controls">
      <ApiBackedCallCenterPanel />
    </ChannelShell>
  );
}
