# Legacy Node Reference Boundary

This directory contains Node.js modules and static browser shells that are still needed by the executable reference oracle.

They are not target service or channel implementations. Target service code belongs under `services/` and must use the approved target stack, such as Kotlin/Spring Boot for core banking services or Python for analytics/simulators where the plan allows it. Target channel code belongs under `apps/*/src` and must use TypeScript/Next.js.

- `services/` holds legacy Node service-shaped oracle modules.
- `packages/banking-domain/` holds legacy Node domain primitives used by the oracle tests and reference runtime.
- `apps/` holds legacy static HTML shells for the Node reference runtime.
- `ui/public/` holds the legacy static JS/CSS used by those shells.

Keep this directory only while `docs/migration/node-retirement-gate.json` is blocked. Do not add new target behavior here.
