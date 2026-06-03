-- Serialize audit hash-chain appends across concurrent API requests.

CREATE TABLE audit_hash_chain_lock (
  lock_key TEXT PRIMARY KEY
);

INSERT INTO audit_hash_chain_lock (lock_key)
VALUES ('GLOBAL')
ON CONFLICT DO NOTHING;
