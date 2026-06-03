# Legacy Node Reference Boundary

This directory contains Node.js modules that are still needed by the executable reference oracle.

They are not target service implementations. Target service code belongs under `services/` and must use the approved target stack, such as Kotlin/Spring Boot for core banking services or Python for analytics/simulators where the plan allows it.

Keep this directory only while `docs/migration/node-retirement-gate.json` is blocked. Do not add new target behavior here.
