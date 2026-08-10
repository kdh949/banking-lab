-- Cross-channel business journeys are correlation projections only.
-- Ledger transactions/postings remain the sole source of financial truth.

CREATE TABLE business_journeys (
  journey_id TEXT PRIMARY KEY,
  journey_type TEXT NOT NULL CHECK (journey_type IN ('HELD_TRANSFER')),
  customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  status TEXT NOT NULL CHECK (
    status IN ('HELD', 'INVESTIGATING', 'PENDING_APPROVAL', 'POSTED', 'BLOCKED', 'REJECTED')
  ),
  primary_reference_type TEXT NOT NULL,
  primary_reference_id TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  synthetic_only BOOLEAN NOT NULL DEFAULT TRUE CHECK (synthetic_only = TRUE),
  CONSTRAINT business_journey_primary_reference_unique
    UNIQUE (primary_reference_type, primary_reference_id)
);

CREATE TABLE business_journey_references (
  journey_id TEXT NOT NULL REFERENCES business_journeys(journey_id),
  reference_type TEXT NOT NULL CHECK (
    reference_type IN (
      'CUSTOMER_TRANSFER_RESULT',
      'TRANSFER_REFERENCE',
      'FDS_CASE',
      'CALL_CENTER_INTERACTION',
      'CALL_CENTER_ESCALATION',
      'APPROVAL',
      'LEDGER_TRANSACTION',
      'NOTIFICATION_DELIVERY'
    )
  ),
  reference_id TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  synthetic_only BOOLEAN NOT NULL DEFAULT TRUE CHECK (synthetic_only = TRUE),
  PRIMARY KEY (journey_id, reference_type, reference_id),
  CONSTRAINT business_journey_reference_global_unique
    UNIQUE (reference_type, reference_id)
);

CREATE TABLE business_journey_events (
  journey_event_id TEXT PRIMARY KEY,
  journey_id TEXT NOT NULL REFERENCES business_journeys(journey_id),
  event_sequence BIGSERIAL NOT NULL UNIQUE,
  event_type TEXT NOT NULL,
  journey_status TEXT NOT NULL,
  source_reference_type TEXT,
  source_reference_id TEXT,
  actor_id TEXT,
  actor_role TEXT,
  reason TEXT,
  trace_id TEXT,
  request_id TEXT,
  payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  synthetic_only BOOLEAN NOT NULL DEFAULT TRUE CHECK (synthetic_only = TRUE)
);

CREATE INDEX idx_business_journeys_customer_updated
  ON business_journeys(customer_id, updated_at DESC, journey_id);

CREATE INDEX idx_business_journey_references_lookup
  ON business_journey_references(reference_type, reference_id);

CREATE INDEX idx_business_journey_events_journey_sequence
  ON business_journey_events(journey_id, event_sequence);

CREATE OR REPLACE FUNCTION prevent_business_journey_event_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  RAISE EXCEPTION 'business journey events are append-only';
END;
$$;

CREATE TRIGGER business_journey_events_no_update_delete
BEFORE UPDATE OR DELETE ON business_journey_events
FOR EACH ROW EXECUTE FUNCTION prevent_business_journey_event_mutation();

ALTER TABLE customer_transfer_results
  ADD COLUMN journey_id TEXT REFERENCES business_journeys(journey_id);

ALTER TABLE fds_cases
  ADD COLUMN journey_id TEXT REFERENCES business_journeys(journey_id);

ALTER TABLE call_center_interactions
  ADD COLUMN journey_id TEXT REFERENCES business_journeys(journey_id);

CREATE INDEX idx_customer_transfer_results_journey
  ON customer_transfer_results(journey_id)
  WHERE journey_id IS NOT NULL;

CREATE INDEX idx_fds_cases_journey
  ON fds_cases(journey_id)
  WHERE journey_id IS NOT NULL;

CREATE INDEX idx_call_center_interactions_journey
  ON call_center_interactions(journey_id)
  WHERE journey_id IS NOT NULL;
