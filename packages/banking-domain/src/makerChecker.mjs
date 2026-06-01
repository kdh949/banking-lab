export const HIGH_RISK_BUSINESS_TYPES = new Set([
  "CUSTOMER_INFO_CHANGE",
  "TRANSFER_LIMIT_INCREASE",
  "ACCOUNT_HOLD",
  "ACCOUNT_HOLD_RELEASE",
  "FEE_WAIVER",
  "PRODUCT_PARAMETER_CHANGE",
  "FDS_RELEASE",
  "FDS_BLOCK",
  "AML_CASE_CLOSE",
  "RECONCILIATION_ADJUSTMENT",
  "COMPLAINT_ANSWER_SEND",
  "ROLE_GRANT",
  "BULK_UNMASK_APPROVAL"
]);

export class ApprovalStore {
  constructor({ auditLog } = {}) {
    this.auditLog = auditLog;
    this.approvals = [];
  }

  submit(input) {
    if (!input.businessType) {
      throw new Error("businessType is required");
    }
    if (HIGH_RISK_BUSINESS_TYPES.has(input.businessType) && !input.requestReason) {
      throw new Error("high-risk approval requires requestReason");
    }
    if (HIGH_RISK_BUSINESS_TYPES.has(input.businessType) && !input.afterSnapshot) {
      throw new Error("high-risk approval requires afterSnapshot");
    }
    const approval = {
      approvalId: input.approvalId || `APR-${String(this.approvals.length + 1).padStart(8, "0")}`,
      businessType: input.businessType,
      businessReferenceId: input.businessReferenceId,
      requestedBy: input.requestedBy,
      requestedAt: input.requestedAt || new Date().toISOString(),
      requestReason: input.requestReason,
      beforeSnapshot: input.beforeSnapshot || null,
      afterSnapshot: input.afterSnapshot || null,
      status: "PENDING",
      approvedBy: null,
      approvedAt: null,
      rejectedBy: null,
      rejectedAt: null,
      rejectReason: null,
      auditEventId: null
    };
    const event = this.auditLog?.append({
      eventType: "COMMAND_REQUESTED",
      actorType: "STAFF",
      actorId: approval.requestedBy,
      actorRole: input.requestedByRole || "BRANCH_STAFF",
      screenId: input.screenId,
      businessReferenceId: approval.businessReferenceId,
      reason: approval.requestReason,
      payload: {
        approvalId: approval.approvalId,
        businessType: approval.businessType
      }
    });
    approval.auditEventId = event?.auditEventId || null;
    this.approvals.push(approval);
    return { ...approval };
  }

  approve(approvalId, input) {
    const approval = this.approvals.find((item) => item.approvalId === approvalId);
    if (!approval) {
      throw new Error("approval not found");
    }
    if (approval.status !== "PENDING") {
      throw new Error("only pending approvals can be approved");
    }
    if (approval.requestedBy === input.approvedBy) {
      throw new Error("maker and checker must be different users");
    }
    approval.status = "APPROVED";
    approval.approvedBy = input.approvedBy;
    approval.approvedAt = input.approvedAt || new Date().toISOString();
    this.auditLog?.append({
      eventType: "COMMAND_APPROVED",
      actorType: "STAFF",
      actorId: input.approvedBy,
      actorRole: input.approvedByRole || "BRANCH_MANAGER",
      screenId: input.screenId,
      businessReferenceId: approval.businessReferenceId,
      payload: {
        approvalId: approval.approvalId,
        businessType: approval.businessType
      }
    });
    return { ...approval };
  }

  reject(approvalId, input) {
    const approval = this.approvals.find((item) => item.approvalId === approvalId);
    if (!approval) {
      throw new Error("approval not found");
    }
    if (approval.status !== "PENDING") {
      throw new Error("only pending approvals can be rejected");
    }
    approval.status = "REJECTED";
    approval.rejectedBy = input.rejectedBy;
    approval.rejectedAt = input.rejectedAt || new Date().toISOString();
    approval.rejectReason = input.rejectReason;
    this.auditLog?.append({
      eventType: "COMMAND_REJECTED",
      actorType: "STAFF",
      actorId: input.rejectedBy,
      actorRole: input.rejectedByRole || "BRANCH_MANAGER",
      screenId: input.screenId,
      businessReferenceId: approval.businessReferenceId,
      payload: {
        approvalId: approval.approvalId,
        businessType: approval.businessType,
        rejectReason: approval.rejectReason
      }
    });
    return { ...approval };
  }

  list() {
    return this.approvals.map((approval) => ({ ...approval }));
  }
}
