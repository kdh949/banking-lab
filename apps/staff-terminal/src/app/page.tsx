import { TerminalNavigationWorkbench } from "../components/terminal-screens";
import { loadChannelManifests } from "../lib/manifestLoader";

export default async function StaffTerminalPage() {
  const manifests = await loadChannelManifests();

  return (
    <TerminalNavigationWorkbench
      evidence={{
        transactionCode: manifests[0]?.transactionCode || "",
        screenCount: manifests.length,
        reasonRequired: manifests.filter((manifest) => manifest.audit.reasonRequired).length,
        makerChecker: manifests.filter((manifest) => manifest.approval?.required).length
      }}
    />
  );
}
