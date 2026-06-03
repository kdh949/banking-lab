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

export function deriveAmlRisk(customer, transactionVelocity = 0) {
  if (customer.riskGrade === "HIGH" || transactionVelocity > 10) {
    return "EDD_REQUIRED";
  }
  if (customer.riskGrade === "MEDIUM") {
    return "MONITOR";
  }
  return "STANDARD";
}

export function evaluateAmlRules(customer, candidate = {}, context = {}) {
  const alerts = [];
  if (customer?.riskGrade === "HIGH") {
    alerts.push({
      ruleId: "AML-RULE-HIGH-RISK-CUSTOMER",
      severity: "HIGH",
      reason: "Synthetic high-risk customer requires enhanced review"
    });
  }
  if ((candidate.amountMinor || 0) >= 9000000) {
    alerts.push({
      ruleId: "AML-RULE-LARGE-TRANSFER",
      severity: "MEDIUM",
      reason: "Synthetic large transfer candidate"
    });
  }
  if ((context.transactionVelocity24h || 0) > 10) {
    alerts.push({
      ruleId: "AML-RULE-VELOCITY-24H",
      severity: "HIGH",
      reason: "Synthetic 24h transaction velocity candidate"
    });
  }
  return alerts;
}

export function createAmlCase(input) {
  const createdAt = input.createdAt || nowIso();
  const alerts = input.alerts || [];
  return {
    caseId: input.caseId,
    status: "OPEN",
    owner: null,
    customerId: input.customerId,
    riskDecision: input.riskDecision || "MONITOR",
    source: input.source || "TRANSACTION_MONITORING",
    relatedTransactionId: input.relatedTransactionId || null,
    relatedTransferResultId: input.relatedTransferResultId || null,
    relatedFdsCaseId: input.relatedFdsCaseId || null,
    amountMinor: input.amountMinor || 0,
    strSimulation: {
      required: alerts.some((alert) => alert.severity === "HIGH"),
      reported: false,
      reportReferenceId: null
    },
    approvalId: null,
    comments: [],
    alerts,
    slaHours: input.slaHours || 24,
    slaDueAt: dueAt(input.slaHours || 24, createdAt),
    createdAt,
    closedAt: null,
    timeline: [
      {
        from: "CANDIDATE",
        to: "OPEN",
        type: "AML_CASE_OPENED",
        actorId: "AML_ENGINE",
        at: createdAt
      }
    ]
  };
}

export function assignAmlCase(caseRecord, input = {}) {
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

export function addAmlComment(caseRecord, input = {}) {
  if (!input.body) {
    throw new Error("AML comment body is required");
  }
  return appendTimeline({
    ...caseRecord,
    comments: [
      ...(caseRecord.comments || []),
      {
        commentId: `AML-COM-${String((caseRecord.comments || []).length + 1).padStart(4, "0")}`,
        body: input.body,
        actorId: input.actorId || "fds01",
        createdAt: nowIso()
      }
    ]
  }, {
    type: "COMMENT_ADDED",
    actorId: input.actorId || "fds01"
  });
}

export function requestAmlClosure(caseRecord, input = {}) {
  if (!["OPEN", "INVESTIGATING"].includes(caseRecord.status)) {
    throw new Error(`AML case cannot request closure from ${caseRecord.status}`);
  }
  return appendTimeline({
    ...caseRecord,
    status: "CLOSURE_REQUESTED",
    approvalId: input.approvalId,
    closureRequest: {
      disposition: input.disposition || "FALSE_POSITIVE",
      requestedBy: input.actorId || "fds01",
      requestedAt: nowIso(),
      reason: input.reason
    }
  }, {
    from: caseRecord.status,
    to: "CLOSURE_REQUESTED",
    type: "CLOSURE_REQUESTED",
    actorId: input.actorId || "fds01",
    approvalId: input.approvalId
  });
}

export function closeAmlCase(caseRecord, input = {}) {
  return appendTimeline({
    ...caseRecord,
    status: "CLOSED",
    closedAt: nowIso(),
    strSimulation: {
      ...(caseRecord.strSimulation || {}),
      reported: input.disposition === "STR_SIMULATED",
      reportReferenceId: input.disposition === "STR_SIMULATED"
        ? input.reportReferenceId || `STR-SIM-${caseRecord.caseId}`
        : null
    }
  }, {
    from: caseRecord.status,
    to: "CLOSED",
    type: "CLOSED",
    actorId: input.actorId || "SYSTEM",
    approvalId: input.approvalId || caseRecord.approvalId
  });
}
