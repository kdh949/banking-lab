# Phase 4 Customer Web Demo Scenario

## Account and Transaction History

1. Open `http://127.0.0.1:8080/customer-web`.
2. Confirm customer login shows `customer01`.
3. Confirm account detail and transaction history load for `ACC-SYN-001-001`.

## Transfer Result States

1. Submit a small transfer and confirm `POSTED`.
2. Submit a transfer of `5000000` and confirm `HELD`.
3. Submit an invalid negative amount and confirm `FAILED`.
4. Confirm the transfer result table shows all statuses.

## Complaint Entry

1. Click complaint entry.
2. Confirm the complaint portal shell opens.
