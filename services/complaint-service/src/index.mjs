import { transitionComplaint } from "../../../packages/banking-domain/src/index.mjs";

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

export function receiveComplaint(input) {
  const createdAt = input.createdAt || nowIso();
  const slaHours = input.slaHours || 72;
  return {
    caseId: input.caseId,
    customerId: input.customerId,
    category: input.category,
    description: input.description,
    status: "RECEIVED",
    owner: null,
    slaHours,
    slaDueAt: dueAt(slaHours, createdAt),
    createdAt,
    answerDraft: null,
    answer: null,
    approvalId: null,
    customerConfirmedAt: null,
    comments: [],
    attachments: input.attachments || [],
    timeline: [
      {
        from: "SUBMITTED",
        to: "RECEIVED",
        actorId: input.customerId,
        at: createdAt
      }
    ]
  };
}

export function moveComplaint(caseRecord, nextStatus, actorId) {
  return transitionComplaint(caseRecord, nextStatus, actorId);
}

export function classifyComplaint(caseRecord, input = {}) {
  const moved = moveComplaint(caseRecord, "CLASSIFIED", input.actorId || "SYSTEM");
  return appendTimeline({
    ...moved,
    category: input.category || caseRecord.category,
    classification: input.classification || caseRecord.category
  }, {
    type: "CLASSIFIED",
    actorId: input.actorId || "SYSTEM",
    note: input.note || "Complaint classified"
  });
}

export function assignComplaint(caseRecord, input = {}) {
  const moved = moveComplaint(caseRecord, "ASSIGNED", input.actorId || "SYSTEM");
  return appendTimeline({
    ...moved,
    owner: input.owner || caseRecord.owner
  }, {
    type: "ASSIGNED",
    actorId: input.actorId || "SYSTEM",
    owner: input.owner || caseRecord.owner
  });
}

export function startComplaintReview(caseRecord, input = {}) {
  const moved = moveComplaint(caseRecord, "IN_REVIEW", input.actorId || caseRecord.owner || "SYSTEM");
  return appendTimeline(moved, {
    type: "IN_REVIEW",
    actorId: input.actorId || caseRecord.owner || "SYSTEM",
    note: input.note || "Review started"
  });
}

export function draftComplaintAnswer(caseRecord, input = {}) {
  const moved = moveComplaint(caseRecord, "WAITING_APPROVAL", input.actorId || caseRecord.owner || "SYSTEM");
  return appendTimeline({
    ...moved,
    answerDraft: {
      body: input.body,
      draftedBy: input.actorId || caseRecord.owner || "SYSTEM",
      draftedAt: nowIso()
    }
  }, {
    type: "ANSWER_DRAFTED",
    actorId: input.actorId || caseRecord.owner || "SYSTEM"
  });
}

export function markComplaintAnswered(caseRecord, input = {}) {
  const moved = moveComplaint(caseRecord, "ANSWERED", input.actorId || "SYSTEM");
  return appendTimeline({
    ...moved,
    answer: {
      body: input.body || caseRecord.answerDraft?.body,
      sentBy: input.actorId || "SYSTEM",
      sentAt: nowIso()
    },
    approvalId: input.approvalId || caseRecord.approvalId
  }, {
    type: "ANSWERED",
    actorId: input.actorId || "SYSTEM",
    approvalId: input.approvalId || caseRecord.approvalId
  });
}

export function closeComplaint(caseRecord, input = {}) {
  const moved = moveComplaint(caseRecord, "CLOSED", input.actorId || caseRecord.customerId);
  return appendTimeline({
    ...moved,
    customerConfirmedAt: nowIso()
  }, {
    type: "CLOSED",
    actorId: input.actorId || caseRecord.customerId,
    note: input.note || "Customer confirmed answer"
  });
}
