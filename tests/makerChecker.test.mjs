import assert from "node:assert/strict";
import test from "node:test";
import { ApprovalStore, AuditLog } from "../runtime/synthetic-reference/packages/banking-domain/src/index.mjs";

test("high-risk operation requires maker-checker approval data", () => {
  const approvals = new ApprovalStore({ auditLog: new AuditLog() });

  assert.throws(() => approvals.submit({
    businessType: "ACCOUNT_HOLD",
    businessReferenceId: "ACC-SYN-001-001",
    requestedBy: "branch01"
  }), /requestReason/);
});

test("maker cannot approve own high-risk request", () => {
  const auditLog = new AuditLog();
  const approvals = new ApprovalStore({ auditLog });
  const approval = approvals.submit({
    businessType: "ACCOUNT_HOLD",
    businessReferenceId: "ACC-SYN-001-001",
    requestedBy: "branch01",
    requestReason: "Synthetic hold verification",
    beforeSnapshot: { status: "ACTIVE" },
    afterSnapshot: { status: "HOLD_REQUESTED" },
    screenId: "ACC-103"
  });

  assert.throws(() => approvals.approve(approval.approvalId, {
    approvedBy: "branch01",
    approvedByRole: "BRANCH_STAFF"
  }), /maker and checker/);

  const approved = approvals.approve(approval.approvalId, {
    approvedBy: "manager01",
    approvedByRole: "BRANCH_MANAGER"
  });

  assert.equal(approved.status, "APPROVED");
  assert.equal(auditLog.verifyHashChain(), true);
});
