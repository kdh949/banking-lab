export const staffWorkflowRouteSummaries = [
  { href: "/tx/[transactionCode]", title: "Transaction Code", screenIds: ["WRK-001"] },
  { href: "/customers/[customerId]", title: "Customer Lookup", screenIds: ["CST-001", "CST-002", "CST-003", "CST-104"] },
  { href: "/accounts/[accountId]", title: "Account Operations", screenIds: ["ACC-101", "ACC-102", "ACC-103", "ACC-104", "LIM-101", "LIM-102", "FEE-102"] },
  { href: "/approvals", title: "Approval Inbox", screenIds: ["APR-001"] },
  { href: "/audit", title: "Audit Events", screenIds: ["AUD-001"] },
  { href: "/workflows/[businessReferenceId]", title: "Workflow Timeline", screenIds: ["WRK-003"] }
] as const;
