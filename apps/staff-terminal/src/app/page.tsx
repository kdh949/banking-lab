import { StaffManifestScreenRenderer } from "../components/manifest-renderer";
import { TerminalNavigationWorkbench } from "../components/terminal-screens";
import { loadChannelManifests } from "../lib/manifestLoader";

type StaffTerminalPageProps = {
  readonly searchParams?: Promise<{
    readonly screen?: string | string[];
  }>;
};

function firstParam(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}

export default async function StaffTerminalPage({ searchParams }: StaffTerminalPageProps) {
  const manifests = await loadChannelManifests();
  const resolvedSearchParams = await searchParams;
  const screen = firstParam(resolvedSearchParams?.screen);
  const selectedManifest = screen ? manifests.find((manifest) => manifest.screenId === screen || manifest.transactionCode === screen) : null;

  if (selectedManifest) {
    return <StaffManifestScreenRenderer manifest={selectedManifest} />;
  }

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
