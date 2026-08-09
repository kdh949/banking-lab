import { createNextBffHandlers } from "@banking-lab/auth-client/next-bff";

export const customerBff = createNextBffHandlers({
  appId: "customer-web",
  oidcClientId: "customer-web",
  defaultReturnTo: "/",
  customerAuth: true,
  proxyAllowedPrefixes: [
    "/api/customer/",
    "/api/customers/",
    "/api/accounts/",
    "/api/transactions/",
    "/api/cards",
    "/api/loans",
    "/api/payments",
    "/api/notifications"
  ]
});
