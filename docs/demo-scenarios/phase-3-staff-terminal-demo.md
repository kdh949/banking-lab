# Phase 3 Staff Terminal Demo Scenario

## Customer Inquiry and Audit

1. Open `http://127.0.0.1:8080/staff-terminal`.
2. Run `CST-001` with customer `SYN-CUS-001` and a reason.
3. Confirm masked customer data appears and the audit panel records the lookup.

## Account and Transaction Inquiry

1. Run `ACC-101` with account `ACC-SYN-001-001`.
2. Run `LED-101` for the same account.
3. Confirm account and transaction tables are populated and audit events are created.

## Customer Information Change

1. Run `CST-103` with a new phone or address.
2. Confirm the approval inbox contains a pending `CUSTOMER_INFO_CHANGE`.
3. Run `APR-001`.
4. Confirm the customer detail reflects the approved change and audit log records execution.
