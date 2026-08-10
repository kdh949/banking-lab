DO $$
DECLARE
  release_case TEXT;
  release_journey TEXT;
  release_transaction TEXT;
  block_case TEXT;
  block_journey TEXT;
  row_count INTEGER;
  signed_total BIGINT;
BEGIN
  SELECT fds_case_id, journey_id, ledger_transaction_id
  INTO release_case, release_journey, release_transaction
  FROM customer_transfer_results
  WHERE idempotency_key = 'CHANNEL-DEMO-RELEASE-001';

  IF release_case IS NULL OR release_journey IS NULL OR release_transaction IS NULL THEN
    RAISE EXCEPTION 'release journey correlation is incomplete';
  END IF;

  SELECT count(*) INTO row_count
  FROM ledger_transactions
  WHERE business_reference_id = release_case
    AND transaction_type = 'INTERNAL_TRANSFER'
    AND idempotency_key = 'FDS-RELEASE-' || release_case
    AND status = 'POSTED';
  IF row_count <> 1 THEN
    RAISE EXCEPTION 'release must create exactly one posted internal transfer, got %', row_count;
  END IF;

  SELECT count(*), COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount_minor ELSE -amount_minor END), 0)
  INTO row_count, signed_total
  FROM ledger_postings
  WHERE ledger_transaction_id = release_transaction;
  IF row_count <> 2 OR signed_total <> 0 THEN
    RAISE EXCEPTION 'release ledger transaction must have two balanced postings, rows %, signed total %', row_count, signed_total;
  END IF;

  SELECT fds_case_id, journey_id
  INTO block_case, block_journey
  FROM customer_transfer_results
  WHERE idempotency_key = 'CHANNEL-DEMO-BLOCK-001'
    AND status = 'BLOCKED';
  IF block_case IS NULL OR block_journey IS NULL THEN
    RAISE EXCEPTION 'blocked journey correlation is incomplete';
  END IF;

  SELECT count(*) INTO row_count
  FROM ledger_transactions
  WHERE business_reference_id = block_case;
  IF row_count <> 0 THEN
    RAISE EXCEPTION 'blocked transfer must have zero ledger transactions, got %', row_count;
  END IF;

  SELECT count(*) INTO row_count
  FROM operator_approvals
  WHERE business_reference_id IN (release_case, block_case)
    AND business_type IN ('FDS_RELEASE', 'FDS_BLOCK')
    AND status = 'APPROVED'
    AND requested_by = 'risk01'
    AND approved_by = 'manager01'
    AND requested_by <> approved_by;
  IF row_count <> 2 THEN
    RAISE EXCEPTION 'release and block must each preserve maker-checker separation, got %', row_count;
  END IF;

  SELECT count(*) INTO row_count
  FROM outbox_events
  WHERE aggregate_id IN (release_case, block_case)
    AND event_type = 'CustomerTransferStatusChanged';
  IF row_count <> 2 THEN
    RAISE EXCEPTION 'each final decision must create one customer status outbox event, got %', row_count;
  END IF;

  SELECT count(*) INTO row_count
  FROM notification_delivery_requests delivery
  JOIN business_journey_references reference
    ON reference.reference_type = 'NOTIFICATION_DELIVERY'
   AND reference.reference_id = delivery.delivery_request_id
  WHERE reference.journey_id IN (release_journey, block_journey)
    AND delivery.event_type = 'CustomerTransferStatusChanged'
    AND delivery.synthetic_only = true
    AND delivery.masked_message NOT LIKE '%010-%';
  IF row_count <> 2 THEN
    RAISE EXCEPTION 'final journeys must each link one masked customer notification, got %', row_count;
  END IF;

  SELECT count(*) INTO row_count
  FROM call_center_interactions interaction
  WHERE interaction.journey_id = release_journey
    AND interaction.status = 'CLOSED'
    AND interaction.metadata_json ->> 'identityVerification' = 'PASSED_SIMULATED';
  IF row_count <> 1 THEN
    RAISE EXCEPTION 'call-center interaction must close after simulated identity verification';
  END IF;

  SELECT count(*) INTO row_count
  FROM call_center_notes note
  JOIN call_center_interactions interaction USING (interaction_id)
  WHERE interaction.journey_id = release_journey
    AND note.redaction_applied = true
    AND note.note_body_redacted NOT LIKE '%010-1234-5678%';
  IF row_count <> 1 THEN
    RAISE EXCEPTION 'call-center note must be redacted before persistence';
  END IF;

  SELECT count(*) INTO row_count
  FROM call_center_aftercall_tasks task
  JOIN call_center_interactions interaction USING (interaction_id)
  WHERE interaction.journey_id = release_journey
    AND task.task_type = 'FDS_HANDOFF_COMPLETED';
  IF row_count <> 1 THEN
    RAISE EXCEPTION 'call-center after-call disposition is missing';
  END IF;

  SELECT count(*) INTO row_count
  FROM call_center_escalations escalation
  JOIN call_center_interactions interaction USING (interaction_id)
  WHERE interaction.journey_id = release_journey
    AND escalation.escalation_type = 'FDS';
  IF row_count <> 1 THEN
    RAISE EXCEPTION 'call-center FDS handoff is missing';
  END IF;

  SELECT count(*) INTO row_count
  FROM business_journey_events
  WHERE journey_id IN (release_journey, block_journey)
    AND (request_id IS NULL OR trace_id IS NULL);
  IF row_count <> 0 THEN
    RAISE EXCEPTION 'journey audit events must retain request and trace correlation, missing %', row_count;
  END IF;

  SELECT count(*) INTO row_count
  FROM business_journey_events
  WHERE journey_id = release_journey
    AND actor_id = 'risk01'
    AND reason IS NOT NULL
    AND request_id IS NOT NULL
    AND trace_id IS NOT NULL;
  IF row_count = 0 THEN
    RAISE EXCEPTION 'FDS maker audit correlation is missing';
  END IF;

  SELECT count(*) INTO row_count
  FROM business_journey_events
  WHERE journey_id = release_journey
    AND actor_id = 'manager01'
    AND reason IS NOT NULL
    AND request_id IS NOT NULL
    AND trace_id IS NOT NULL;
  IF row_count = 0 THEN
    RAISE EXCEPTION 'checker audit correlation is missing';
  END IF;
END $$;

SELECT 'cross-channel held-transfer database invariants passed' AS result;
