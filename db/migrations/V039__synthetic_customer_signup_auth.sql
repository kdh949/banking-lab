-- Synthetic customer self-signup support.
-- Lab-only credential issuance: no real PII, KYC provider, Keycloak Admin API, or external identity system is called.

CREATE SEQUENCE synthetic_customer_signup_customer_seq START 1;

ALTER TABLE customer_auth_identities
  ADD COLUMN signup_idempotency_key TEXT UNIQUE,
  ADD COLUMN signup_command_hash TEXT;
