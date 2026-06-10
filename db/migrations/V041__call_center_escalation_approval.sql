-- Call-center escalation is a high-risk synthetic staff operation.
-- The request is persisted first, then executed only after maker-checker approval.

ALTER TABLE call_center_escalations
  ADD COLUMN approval_id TEXT REFERENCES operator_approvals(approval_id);

ALTER TABLE call_center_escalations
  DROP CONSTRAINT call_center_escalations_status_check;

ALTER TABLE call_center_escalations
  ADD CONSTRAINT call_center_escalations_status_check
  CHECK (status IN ('PENDING_APPROVAL', 'CREATED', 'ACCEPTED', 'REJECTED', 'CLOSED'));

CREATE UNIQUE INDEX idx_call_center_escalations_approval_id
  ON call_center_escalations(approval_id)
  WHERE approval_id IS NOT NULL;
