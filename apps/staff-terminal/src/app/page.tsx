import { StaffIntegratedWorkspace } from "../components/manifest-renderer";
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

  return <StaffIntegratedWorkspace manifests={manifests} initialScreen={screen} />;
}
