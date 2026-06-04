# Formal Ledger Verification

This directory contains the executable formal models used by `npm run formal:ledger`.

- `Ledger.tla` / `Ledger.cfg` models balanced postings, balance projection, reversals, closed dates, held/failed commands, idempotency keys, and approved adjustments.
- `Idempotency.tla` / `Idempotency.cfg` focuses on retry behavior and duplicate side-effect prevention.

The checker first validates the TLA+ artifacts, then attempts TLC through `BANKING_LAB_TLC_CMD`, a local `tlc` command, `BANKING_LAB_TLC_JAR`, `tools/tla2tools.jar`, `formal/tla2tools.jar`, or `~/Downloads/tla2tools.jar`. For TLC jar execution it resolves Java through `BANKING_LAB_JAVA_CMD`, `JAVA_HOME`, or a local OpenJDK fallback before falling back to `java`. The CI pass condition does not rely on a static artifact scan: when TLC is unavailable, the script runs a deterministic bounded state-search model checker over the same finite model scope and fails on any invariant violation.

Static-only mode is reserved for local development diagnostics:

```bash
BANKING_LAB_ALLOW_FORMAL_STATIC_ONLY=true BANKING_LAB_FORMAL_ENGINE=static npm run formal:ledger
```

CI must not set that override. The generated evidence is written to `docs/test-evidence/generated/formal-ledger-tlc-result.json`.
