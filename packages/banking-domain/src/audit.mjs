import { createHash } from "node:crypto";

export const AUDIT_EVENT_TYPES = new Set([
  "LOGIN_SUCCESS",
  "LOGIN_FAILURE",
  "CUSTOMER_SEARCH",
  "CUSTOMER_DETAIL_VIEW",
  "PII_UNMASK_REQUESTED",
  "PII_UNMASK_APPROVED",
  "PII_DOWNLOADED",
  "ACCOUNT_VIEW",
  "TRANSACTION_VIEW",
  "COMMAND_REQUESTED",
  "COMMAND_APPROVED",
  "COMMAND_REJECTED",
  "COMMAND_EXECUTED",
  "PARAMETER_CHANGED",
  "ROLE_CHANGED",
  "BATCH_STARTED",
  "BATCH_FAILED",
  "BATCH_RETRIED",
  "BREAK_GLASS_USED"
]);

const REASON_REQUIRED_EVENTS = new Set([
  "CUSTOMER_SEARCH",
  "CUSTOMER_DETAIL_VIEW",
  "PII_UNMASK_REQUESTED",
  "PII_UNMASK_APPROVED",
  "PII_DOWNLOADED",
  "ACCOUNT_VIEW",
  "TRANSACTION_VIEW",
  "COMMAND_REQUESTED",
  "COMMAND_EXECUTED",
  "PARAMETER_CHANGED",
  "ROLE_CHANGED",
  "BREAK_GLASS_USED"
]);

function canonicalJson(value) {
  if (Array.isArray(value)) {
    return `[${value.map(canonicalJson).join(",")}]`;
  }
  if (value && typeof value === "object") {
    return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${canonicalJson(value[key])}`).join(",")}}`;
  }
  return JSON.stringify(value);
}

export function hashAuditEvent(event) {
  const eventForHash = { ...event };
  delete eventForHash.payloadHash;
  return createHash("sha256").update(canonicalJson(eventForHash)).digest("hex");
}

export class AuditLog {
  constructor(seed = []) {
    this.events = [...seed];
  }

  append(input) {
    if (!AUDIT_EVENT_TYPES.has(input.eventType)) {
      throw new Error(`unsupported audit event type: ${input.eventType}`);
    }
    if (REASON_REQUIRED_EVENTS.has(input.eventType) && !input.reason) {
      throw new Error(`${input.eventType} requires a business reason`);
    }
    const previous = this.events[this.events.length - 1];
    const event = {
      auditEventId: input.auditEventId || `AUD-${String(this.events.length + 1).padStart(8, "0")}`,
      eventType: input.eventType,
      actorType: input.actorType || "SYSTEM",
      actorId: input.actorId || "SYSTEM",
      actorRole: input.actorRole || "SYSTEM",
      branchId: input.branchId || "LAB-001",
      screenId: input.screenId || null,
      businessReferenceId: input.businessReferenceId || null,
      customerId: input.customerId || null,
      accountId: input.accountId || null,
      reason: input.reason || null,
      ipAddress: input.ipAddress || "127.0.0.1",
      userAgent: input.userAgent || "banking-lab-runtime",
      deviceId: input.deviceId || "LAB-DEVICE",
      beforeHash: previous?.payloadHash || null,
      previousEventHash: previous?.payloadHash || null,
      createdAt: input.createdAt || new Date().toISOString(),
      payload: input.payload || {}
    };
    event.payloadHash = hashAuditEvent(event);
    this.events.push(Object.freeze(event));
    return event;
  }

  all() {
    return this.events.map((event) => ({ ...event }));
  }

  verifyHashChain() {
    for (let index = 0; index < this.events.length; index += 1) {
      const event = this.events[index];
      if (hashAuditEvent(event) !== event.payloadHash) {
        return false;
      }
      const previous = this.events[index - 1];
      if ((previous?.payloadHash || null) !== event.previousEventHash) {
        return false;
      }
    }
    return true;
  }
}
