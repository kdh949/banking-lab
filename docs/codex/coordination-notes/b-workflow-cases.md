# Agent B Coordination Notes: Workflow Cases

Date: 2026-06-02

## Completed In Scope

- Added Kotlin maker-checker approval primitives under `lab.banking.core.approval`.
- Added complaint, FDS, AML, and reconciliation workflow state machines under their assigned packages.
- Added V007 additive workflow case lifecycle migration.
- Added focused Kotlin unit tests for Agent B parity scenarios.
- Added architecture evidence in `docs/architecture/workflow-cases-kotlin-port.md`.

## Coordinator-Owned Requests

- Provide or configure JDK 21 for Gradle test execution in this workspace. `./gradlew :services:core-banking:test` currently exits before Gradle starts with: `Unable to locate a Java Runtime`.
- Decide the Spring API ownership for workflow routes. Agent B did not edit shared API/controller packages because they were outside the allowed write paths.
- Connect approved FDS release and reconciliation adjustment handoff commands to the ledger service in a coordinator-owned application service or a future integration slice.
- Confirm whether V007 should remain the complaint persistence owner, since V005 already owns FDS/AML/reconciliation base tables.

## Worktree Coordination

Unrelated existing modifications were present outside Agent B scope, including shared frontend/package files and `LedgerCommandService.kt`. Agent B did not modify or revert them.
