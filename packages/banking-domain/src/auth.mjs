export const MOCK_USERS = [
  {
    userId: "customer01",
    displayName: "Synthetic Customer 01",
    actorType: "CUSTOMER",
    roles: ["CUSTOMER"],
    customerId: "SYN-CUS-001"
  },
  {
    userId: "branch01",
    displayName: "Branch Operator 01",
    actorType: "STAFF",
    roles: ["BRANCH_STAFF", "CALL_CENTER"],
    branchId: "LAB-001"
  },
  {
    userId: "manager01",
    displayName: "Branch Manager 01",
    actorType: "STAFF",
    roles: ["BRANCH_MANAGER"],
    branchId: "LAB-001"
  },
  {
    userId: "complaint01",
    displayName: "Complaint Handler 01",
    actorType: "STAFF",
    roles: ["COMPLAINT_HANDLER"],
    branchId: "LAB-OPS"
  },
  {
    userId: "auditor01",
    displayName: "Internal Auditor 01",
    actorType: "STAFF",
    roles: ["AUDITOR"],
    branchId: "LAB-AUDIT"
  },
  {
    userId: "fds01",
    displayName: "FDS AML Reviewer 01",
    actorType: "STAFF",
    roles: ["FDS_REVIEWER", "AML_REVIEWER"],
    branchId: "LAB-RISK"
  },
  {
    userId: "ops01",
    displayName: "Operations Operator 01",
    actorType: "STAFF",
    roles: ["OPS_OPERATOR"],
    branchId: "LAB-OPS"
  },
  {
    userId: "compliance01",
    displayName: "Compliance Manager 01",
    actorType: "STAFF",
    roles: ["COMPLIANCE_MANAGER", "OPS_MANAGER"],
    branchId: "LAB-COMPLIANCE"
  }
];

export function findMockUser(userId) {
  return MOCK_USERS.find((user) => user.userId === userId);
}

export function loginMockUser({ userId, auditLog }) {
  const user = findMockUser(userId);
  if (!user) {
    auditLog?.append({
      eventType: "LOGIN_FAILURE",
      actorType: "UNKNOWN",
      actorId: userId || "UNKNOWN",
      actorRole: "UNKNOWN",
      payload: { reason: "unknown mock user" }
    });
    throw new Error("unknown mock user");
  }
  const session = {
    sessionId: `SES-${Date.now()}-${user.userId}`,
    userId: user.userId,
    actorType: user.actorType,
    roles: user.roles,
    customerId: user.customerId || null,
    branchId: user.branchId || null,
    createdAt: new Date().toISOString()
  };
  auditLog?.append({
    eventType: "LOGIN_SUCCESS",
    actorType: user.actorType,
    actorId: user.userId,
    actorRole: user.roles[0],
    branchId: user.branchId,
    payload: { sessionId: session.sessionId }
  });
  return session;
}
