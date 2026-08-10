import { createNextBffHandlers } from "@banking-lab/auth-client/next-bff";

export const staffBff = createNextBffHandlers({
  appId: "staff-terminal",
  oidcClientId: "staff-terminal",
  defaultReturnTo: "/",
  proxyAllowedPrefixes: ["/api/staff/", "/api/approvals"],
  simulatedActors: [
    {
      actorKey: "branch-staff",
      subject: "branch01",
      roles: ["BRANCH_STAFF"],
      displayName: "Synthetic branch staff"
    },
    {
      actorKey: "fds-reviewer",
      subject: "risk01",
      roles: ["FDS_REVIEWER"],
      displayName: "Synthetic FDS reviewer"
    },
    {
      actorKey: "branch-manager",
      subject: "manager01",
      roles: ["BRANCH_MANAGER"],
      displayName: "Synthetic branch manager",
      simulatedStepUp: true
    }
  ]
});
