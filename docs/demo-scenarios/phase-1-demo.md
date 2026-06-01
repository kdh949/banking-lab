# Phase 1 Demo Scenario

## Staff Reason-Required Lookup

1. Open `http://127.0.0.1:8080/staff-terminal`.
2. Confirm transaction code `CST-001`.
3. Enter a business reason.
4. Run the lookup.
5. Confirm customer PII is masked and the audit panel shows a `CUSTOMER_SEARCH` event.

## Customer Transfer Idempotency

1. Open `http://127.0.0.1:8080/customer-web`.
2. Submit a small synthetic transfer with idempotency key `WEB-DEMO-001`.
3. Submit again with the same key.
4. Confirm balances do not double-apply the second request.

## Complaint Intake Shell

1. Open `http://127.0.0.1:8080/complaint-portal`.
2. Submit a synthetic complaint.
3. Confirm the case appears with `RECEIVED` status and a 72-hour SLA.
