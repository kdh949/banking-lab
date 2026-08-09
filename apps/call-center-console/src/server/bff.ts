import { createNextBffHandlers } from "@banking-lab/auth-client/next-bff";

export const callCenterBff = createNextBffHandlers({
  appId: "call-center-console",
  oidcClientId: "call-center-console",
  defaultReturnTo: "/workspace",
  proxyAllowedPrefixes: ["/api/staff/call-center/", "/api/staff/journeys/"],
  simulatedActors: [
    {
      actorKey: "call-agent",
      subject: "call-agent01",
      roles: ["CALL_CENTER_AGENT"],
      displayName: "Synthetic call agent"
    }
  ]
});
