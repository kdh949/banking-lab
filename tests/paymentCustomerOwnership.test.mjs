import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("customer payment bodies are rebound to the authenticated customer", async () => {
  const advice = await readFile(
    "services/payment-service/src/main/kotlin/lab/banking/payment/security/PaymentCustomerRequestBindingAdvice.kt",
    "utf8"
  );
  const authorization = await readFile(
    "services/payment-service/src/main/kotlin/lab/banking/payment/security/PaymentCustomerAuthorization.kt",
    "utf8"
  );
  const application = await readFile("services/payment-service/src/main/resources/application.yml", "utf8");

  assert.match(authorization, /fun PaymentPrincipal\?\.requirePaymentCustomer\(\)/u);
  assert.match(authorization, /PAYMENT_CUSTOMER_CLAIM_REQUIRED/u);
  assert.match(advice, /customerId = actor\.customerId/u);
  assert.match(advice, /requestedBy = actor\.subject/u);
  assert.match(advice, /requestedChannel = "CUSTOMER_WEB"/u);
  assert.match(advice, /accountOwnershipVerifier\.requireOwned/u);
  assert.match(application, /BANKING_LAB_PAYMENT_CUSTOMER_ACCOUNT_OWNERSHIP_ENABLED:true/u);
});

test("customer payment resources are concealed from other customers", async () => {
  const interceptor = await readFile(
    "services/payment-service/src/main/kotlin/lab/banking/payment/security/PaymentCustomerOwnershipInterceptor.kt",
    "utf8"
  );

  assert.match(interceptor, /PAYMENT_INSTRUCTION_NOT_FOUND/u);
  assert.match(interceptor, /PAYMENT_AUTOPAY_NOT_FOUND/u);
  assert.match(interceptor, /customer_id = :customerId/u);
  assert.match(interceptor, /customer_identity_bound = true/u);
  assert.match(interceptor, /addPathPatterns\("\/api\/payments\/\*\*"\)/u);
});

test("legacy unbound payment work is quarantined during migration", async () => {
  const migration = await readFile(
    "services/payment-service/src/main/resources/db/migration/V005__payment_customer_identity_binding.sql",
    "utf8"
  );
  const documentation = await readFile("docs/security/payment-customer-ownership.md", "utf8");

  assert.match(migration, /customer_identity_bound BOOLEAN NOT NULL DEFAULT false/u);
  assert.match(migration, /ALTER COLUMN customer_identity_bound SET DEFAULT true/u);
  assert.match(migration, /payment_autopay_active_identity_bound/u);
  assert.match(migration, /Legacy autopay paused because customer identity was not server-bound/u);
  assert.match(migration, /legacy payment posting quarantined/u);
  assert.match(migration, /status = 'DEAD_LETTER'/u);
  assert.match(documentation, /server treats them as untrusted and overwrites them/u);
});
