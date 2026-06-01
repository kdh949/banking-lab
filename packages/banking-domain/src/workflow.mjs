export const COMPLAINT_STATES = [
  "DRAFT",
  "SUBMITTED",
  "RECEIVED",
  "CLASSIFIED",
  "ASSIGNED",
  "IN_REVIEW",
  "WAITING_CUSTOMER",
  "WAITING_APPROVAL",
  "ANSWERED",
  "CLOSED",
  "REOPENED",
  "TRANSFERRED_TO_AUTHORITY_SIM"
];

export const COMPLAINT_TRANSITIONS = new Map([
  ["DRAFT", ["SUBMITTED"]],
  ["SUBMITTED", ["RECEIVED"]],
  ["RECEIVED", ["CLASSIFIED"]],
  ["CLASSIFIED", ["ASSIGNED"]],
  ["ASSIGNED", ["IN_REVIEW"]],
  ["IN_REVIEW", ["WAITING_CUSTOMER", "WAITING_APPROVAL"]],
  ["WAITING_CUSTOMER", ["IN_REVIEW"]],
  ["WAITING_APPROVAL", ["ANSWERED", "IN_REVIEW"]],
  ["ANSWERED", ["CLOSED"]],
  ["CLOSED", ["REOPENED"]],
  ["REOPENED", ["IN_REVIEW", "TRANSFERRED_TO_AUTHORITY_SIM"]]
]);

export function transitionComplaint(caseRecord, nextStatus, actorId) {
  const allowed = COMPLAINT_TRANSITIONS.get(caseRecord.status) || [];
  if (!allowed.includes(nextStatus)) {
    throw new Error(`invalid complaint transition ${caseRecord.status} -> ${nextStatus}`);
  }
  return {
    ...caseRecord,
    status: nextStatus,
    timeline: [
      ...(caseRecord.timeline || []),
      {
        from: caseRecord.status,
        to: nextStatus,
        actorId,
        at: new Date().toISOString()
      }
    ]
  };
}
