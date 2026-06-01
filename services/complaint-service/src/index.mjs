import { transitionComplaint } from "../../../packages/banking-domain/src/index.mjs";

export function receiveComplaint(input) {
  return {
    caseId: input.caseId,
    customerId: input.customerId,
    category: input.category,
    description: input.description,
    status: "RECEIVED",
    owner: null,
    slaHours: 72,
    timeline: [
      {
        from: "SUBMITTED",
        to: "RECEIVED",
        actorId: input.customerId,
        at: new Date().toISOString()
      }
    ]
  };
}

export function moveComplaint(caseRecord, nextStatus, actorId) {
  return transitionComplaint(caseRecord, nextStatus, actorId);
}
