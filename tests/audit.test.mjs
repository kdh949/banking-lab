import assert from "node:assert/strict";
import test from "node:test";
import { AuditLog, maskAccountNo, maskCustomer, maskPhone } from "../packages/banking-domain/src/index.mjs";

test("sensitive staff access requires business reason", () => {
  const auditLog = new AuditLog();

  assert.throws(() => auditLog.append({
    eventType: "CUSTOMER_SEARCH",
    actorType: "STAFF",
    actorId: "branch01",
    actorRole: "BRANCH_STAFF",
    screenId: "CST-001"
  }), /requires a business reason/);
});

test("audit events are append-only hash chained", () => {
  const auditLog = new AuditLog();
  const first = auditLog.append({
    eventType: "LOGIN_SUCCESS",
    actorType: "STAFF",
    actorId: "branch01",
    actorRole: "BRANCH_STAFF"
  });
  const second = auditLog.append({
    eventType: "CUSTOMER_SEARCH",
    actorType: "STAFF",
    actorId: "branch01",
    actorRole: "BRANCH_STAFF",
    screenId: "CST-001",
    reason: "Synthetic customer service request"
  });

  assert.equal(second.previousEventHash, first.payloadHash);
  assert.equal(auditLog.verifyHashChain(), true);
});

test("PII masking hides customer and account details by default", () => {
  const masked = maskCustomer({
    customerId: "SYN-CUS-001",
    name: "Lab Customer Alpha",
    phone: "010-0000-1001",
    address: "Seoul Synthetic District",
    customerGrade: "STANDARD",
    riskGrade: "LOW"
  });

  assert.equal(masked.customerId, "SYN-CUS-001");
  assert.notEqual(masked.maskedName, "Lab Customer Alpha");
  assert.equal(maskPhone("010-0000-1001"), "010-****-1001");
  assert.equal(maskAccountNo("LAB-001-000001"), "LAB-***-0001");
  assert.match(maskAccountNo("LAB-001-000001"), /\*/);
});
