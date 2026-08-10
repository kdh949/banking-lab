import { ChannelShell } from "../../../../../../packages/channel-ui/src/customer-ui";
import { ApiBackedCustomerPanel } from "../../../components/ApiBackedCustomerPanel";

export default function CustomerEvidencePage() {
  return (
    <ChannelShell appId="customer-web" eyebrow="Lab · API evidence" title="Customer API Evidence" status="LAB_ONLY · synthetic smoke controls">
      <ApiBackedCustomerPanel />
    </ChannelShell>
  );
}
