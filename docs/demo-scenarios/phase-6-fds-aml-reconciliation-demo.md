# Phase 6 FDS AML Reconciliation Demo

## Scenario 1: FDS Hold and Release

1. Open `/customer-web`.
2. Submit a transfer with amount `5000000`.
3. Open `/fds-aml-console`.
4. Confirm the FDS case is `HELD`.
5. Release the case and approve the generated approval.
6. Confirm the customer transfer result becomes `POSTED`.

## Scenario 2: AML STR Simulation

1. Use customer `SYN-CUS-003` and account `ACC-SYN-003-001`.
2. Submit a high amount transfer.
3. Open `/fds-aml-console`.
4. Confirm an AML case exists for the high-risk customer.
5. Add a reviewer comment.
6. Request closure with `STR_SIMULATED`.
7. Approve the closure.

## Scenario 3: EOD Reconciliation

1. Open `/ops-console`.
2. Seed an internal transfer.
3. Run EOD.
4. Confirm an unmatched reconciliation item is created.
5. Request an adjustment.
6. Approve the adjustment.
7. Confirm the adjustment transaction exists and the closed business date was not mutated.
