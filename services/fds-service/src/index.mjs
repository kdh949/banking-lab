function nowIso() {
  return new Date().toISOString();
}

function dueAt(hours, createdAt = nowIso()) {
  return new Date(new Date(createdAt).getTime() + hours * 60 * 60 * 1000).toISOString();
}

function appendTimeline(caseRecord, entry) {
  return {
    ...caseRecord,
    timeline: [
      ...(caseRecord.timeline || []),
      {
        at: nowIso(),
        ...entry
      }
    ]
  };
}

export function evaluateFdsRules(transfer, context = {}) {
  const alerts = [];
  if (transfer.amountMinor >= 5000000) {
    alerts.push({
      ruleId: "FDS-RULE-UNUSUAL-AMOUNT",
      severity: "HIGH",
      action: "HOLD",
      reason: "Synthetic high amount threshold"
    });
  }
  if (context.newDevice === true && transfer.amountMinor >= 3000000) {
    alerts.push({
      ruleId: "FDS-RULE-NEW-DEVICE-HIGH-AMOUNT",
      severity: "HIGH",
      action: "HOLD",
      reason: "Synthetic new device plus high amount"
    });
  }
  if (context.firstTimeBeneficiary === true && transfer.amountMinor >= 1000000) {
    alerts.push({
      ruleId: "FDS-RULE-FIRST-TIME-BENEFICIARY",
      severity: "MEDIUM",
      action: "HOLD",
      reason: "Synthetic first-time beneficiary"
    });
  }
  if ((context.transferVelocity10m || 0) >= 5) {
    alerts.push({
      ruleId: "FDS-RULE-VELOCITY-10M",
      severity: "HIGH",
      action: "HOLD",
      reason: "Synthetic transfer velocity threshold"
    });
  }
  return alerts;
}

export function createFdsCase(input) {
  const createdAt = input.createdAt || nowIso();
  const alerts = input.alerts || evaluateFdsRules(input.transfer || input, input.context || {});
  return {
    caseId: input.caseId,
    status: "HELD",
    owner: null,
    slaHours: input.slaHours || 4,
    slaDueAt: dueAt(input.slaHours || 4, createdAt),
    customerId: input.customerId,
    fromAccountId: input.fromAccountId,
    toAccountId: input.toAccountId,
    amountMinor: input.amountMinor,
    idempotencyKey: input.idempotencyKey,
    transferResultId: input.transferResultId || null,
    releaseTransactionId: null,
    approvalId: null,
    decision: null,
    deviceFingerprint: input.deviceFingerprint || "LAB-DEVICE",
    firstTimeBeneficiary: Boolean(input.firstTimeBeneficiary),
    transferVelocity10m: input.transferVelocity10m || 0,
    alerts,
    createdAt,
    timeline: [
      {
        from: "REQUESTED",
        to: "HELD",
        type: "FDS_HELD",
        actorId: "FDS_ENGINE",
        at: createdAt
      }
    ]
  };
}

export function assignFdsCase(caseRecord, input = {}) {
  return appendTimeline({
    ...caseRecord,
    status: "INVESTIGATING",
    owner: input.owner || caseRecord.owner || input.actorId || "fds01"
  }, {
    from: caseRecord.status,
    to: "INVESTIGATING",
    type: "ASSIGNED",
    actorId: input.actorId || "fds01",
    owner: input.owner || caseRecord.owner || input.actorId || "fds01"
  });
}

export function requestFdsDecision(caseRecord, input = {}) {
  if (!["RELEASE", "BLOCK"].includes(input.action)) {
    throw new Error("FDS decision action must be RELEASE or BLOCK");
  }
  if (!["HELD", "INVESTIGATING"].includes(caseRecord.status)) {
    throw new Error(`FDS case cannot request decision from ${caseRecord.status}`);
  }
  const nextStatus = input.action === "RELEASE" ? "RELEASE_REQUESTED" : "BLOCK_REQUESTED";
  return appendTimeline({
    ...caseRecord,
    status: nextStatus,
    approvalId: input.approvalId,
    decision: {
      action: input.action,
      requestedBy: input.actorId || "fds01",
      requestedAt: nowIso(),
      reason: input.reason
    }
  }, {
    from: caseRecord.status,
    to: nextStatus,
    type: "DECISION_REQUESTED",
    actorId: input.actorId || "fds01",
    approvalId: input.approvalId
  });
}

export function markFdsReleased(caseRecord, input = {}) {
  return appendTimeline({
    ...caseRecord,
    status: "RELEASED",
    releaseTransactionId: input.transactionId,
    approvalId: input.approvalId || caseRecord.approvalId,
    decision: {
      ...(caseRecord.decision || {}),
      action: "RELEASE",
      executedBy: input.actorId || "SYSTEM",
      executedAt: nowIso()
    }
  }, {
    from: caseRecord.status,
    to: "RELEASED",
    type: "RELEASED",
    actorId: input.actorId || "SYSTEM",
    transactionId: input.transactionId,
    approvalId: input.approvalId || caseRecord.approvalId
  });
}

export function markFdsBlocked(caseRecord, input = {}) {
  return appendTimeline({
    ...caseRecord,
    status: "BLOCKED",
    approvalId: input.approvalId || caseRecord.approvalId,
    decision: {
      ...(caseRecord.decision || {}),
      action: "BLOCK",
      executedBy: input.actorId || "SYSTEM",
      executedAt: nowIso()
    }
  }, {
    from: caseRecord.status,
    to: "BLOCKED",
    type: "BLOCKED",
    actorId: input.actorId || "SYSTEM",
    approvalId: input.approvalId || caseRecord.approvalId
  });
}
