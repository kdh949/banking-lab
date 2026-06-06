# Legacy Node Reference Boundary

This directory contains Node.js modules and static browser shells used by the archived executable reference oracle.

They are not target service or channel implementations. Target service code belongs under `services/` and must use the approved target stack, such as Kotlin/Spring Boot for core banking services or Python for analytics/simulators where the plan allows it. Target channel code belongs under `apps/*/src` and must use TypeScript/Next.js.

- `services/` holds legacy Node service-shaped oracle modules.
- `packages/banking-domain/` holds legacy Node domain primitives used by the oracle tests and reference runtime.
- `packages/screen-engine/` holds legacy Node-compatible manifest helpers used by oracle tests and the reference runtime.
- `packages/form-engine/` holds legacy Node-compatible form validation helpers used by oracle tests.
- `apps/` holds legacy static HTML shells for the Node reference runtime.
- `ui/public/` holds the legacy static JS/CSS used by those shells.

`docs/migration/node-retirement-gate.json` is ready for the current synthetic lab scope, but this directory remains useful for regression comparison and historical oracle behavior. Do not add new target behavior here, and do not delete it without a separate deletion plan that preserves parity evidence.
