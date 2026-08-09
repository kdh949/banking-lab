import { ChannelShell } from "../../../../packages/channel-ui/src";
import { CustomerSelfServiceHomeSurface } from "../components/CustomerSelfService";

export default function CustomerWebPage() {
  return (
    <ChannelShell appId="customer-web" eyebrow="Customer self-service" title="Customer Dashboard" status="Synthetic only · token-owned">
      <CustomerSelfServiceHomeSurface />
    </ChannelShell>
  );
}
