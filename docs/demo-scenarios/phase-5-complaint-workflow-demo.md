# Phase 5 Complaint Workflow Demo Scenario

## Customer Intake

1. Open `http://127.0.0.1:8080/complaint-portal`.
2. Submit a synthetic complaint.
3. Confirm the case appears with `RECEIVED`, SLA, and timeline.

## Staff Processing

1. Open `http://127.0.0.1:8080/staff-terminal`.
2. Run transaction code `CMP-201`.
3. Confirm the case advances through classification, assignment, review, draft, approval, and `ANSWERED`.

## Customer Confirmation

1. Return to the complaint portal.
2. Confirm the answered case.
3. Verify the case is `CLOSED`.
