# Codex Parallel Subagent Implementation Plan

This file is written for `kdh949/banking-lab`. It is designed to be pasted into Codex Goal mode when you want several Codex subagents to implement the banking-lab expansion in parallel without stepping on each other.

The goal is not to delete the current Node.js reference runtime. The current `.mjs` runtime remains the executable reference until Kotlin/Spring Boot and TypeScript/Next.js parity are proven.

---

## 1. Repository-specific baseline

Before spawning agents, the coordinator must read these files:

- `README.md`
- `package.json`
- `docs/migration/kotlin-next-playbook.md`
- `docs/migration/parity-scenarios.json`
- `docs/migration/node-retirement-gate.json`
- `docs/migration/structured-api-error-contract.md`

Baseline commands:

```bash
npm install
npm run parity
npm test
npm run validate:manifests
npm run evidence:pack
npm run node:retirement-gate
```

Target migration commands, when toolchain is available:

```bash
./gradlew :services:core-banking:test
./gradlew :services:core-banking:integrationTest
npm run next:customer-web:typecheck
npm run next:customer-web:build
```

Non-negotiable invariants:

- Synthetic data only.
- No real deposits, real transfers, real payment networks, real KYC providers, or real customer PII.
- Do not delete or rewrite the Node reference runtime before `docs/migration/node-retirement-gate.json` is ready.
- Preserve double-entry ledger postings and projected balances.
- Preserve idempotent external commands.
- Preserve append-only audit hash chain.
- Preserve masked PII by default.
- Preserve reason-required staff sensitive access.
- Preserve maker-checker approval for high-risk operations.
- Preserve workflow state validity for complaint, FDS, AML, and reconciliation cases.
- Preserve manifest-declared authorization, audit, masking, workflow, and approval metadata.
- Preserve structured API error contract.

---

## 2. Paste this into Codex Goal mode

```text
You are the implementation coordinator for kdh949/banking-lab.

Goal:
Implement the next bank-grade expansion in parallel using multiple subagents, while avoiding merge conflicts and preserving the existing Node reference runtime as the executable reference. The expansion should move the repo toward a broader bank platform: staff integrated terminal, customer web banking, electronic complaint portal, FDS/AML console, operations/reconciliation console, audit console, Kotlin/Spring Boot backend parity, TypeScript/Next.js frontend parity, manifests, workflow, audit, masking, maker-checker, and evidence.

Repository facts to respect:
- Current repo is a synthetic bank-grade lab, not a real bank and not connected to real money, real PII, or real payment networks.
- Node `.mjs` runtime is still the executable reference.
- Kotlin/Spring Boot and TypeScript/Next.js migration is in progress.
- Current tests and evidence scripts are the proof baseline.
- Do not delete runtime/server.mjs, runtime/labApp.mjs, current packages/*.mjs code, static app shells under legacy-node-reference/apps, legacy static UI assets under legacy-node-reference/ui/public, tests/*.test.mjs, or scripts/generate-*.mjs unless the node retirement gate is explicitly ready and evidence is updated.

Start by reading:
1. README.md
2. package.json
3. docs/migration/kotlin-next-playbook.md
4. docs/migration/parity-scenarios.json
5. docs/migration/node-retirement-gate.json
6. docs/migration/structured-api-error-contract.md

Execution model:
- Use subagents deliberately. Spawn one agent per slice below.
- If Codex Worktrees are available, run each implementation slice in its own worktree/branch:
  - codex/a-core-api-ledger
  - codex/b-workflow-cases
  - codex/c-manifest-screen-factory
  - codex/d-frontend-channels
  - codex/e-codex-skills-devex
  - codex/f-qa-evidence-review
- If only one checkout is available, spawn subagents for exploration and patch planning, but apply write changes serially in the parent thread.
- Never let two agents edit the same shared file in parallel.
- Each subagent must obey its allowed write paths.
- Shared files are coordinator-owned only: package.json, package-lock.json, README.md, settings.gradle.kts, build.gradle.kts, docker-compose.yml, docker-compose.yaml, docs/migration/parity-scenarios.json, docs/migration/node-retirement-gate.json, root tsconfig files, root eslint/prettier configs, and CI workflows.
- If a subagent needs a dependency, script, or shared config change, it must write a request note under docs/codex/coordination-notes/<agent-id>.md instead of editing the shared file directly.
- Existing migrations are immutable. Add new migrations only with assigned numbers. Do not edit V001 through V005.
- Existing Node reference tests under tests/*.test.mjs are read-only. Add target tests in Kotlin/Next-specific locations or add new tests with unique filenames only if the coordinator approves.
- Each subagent must return:
  1. files changed,
  2. tests run,
  3. unrun tests and why,
  4. parity scenarios covered,
  5. remaining risks,
  6. required coordinator-owned changes.

Spawn these subagents:

A. core-api-ledger
Role: Kotlin/Spring backend parity owner for ledger, idempotency, structured errors, and health.
Allowed write paths:
- services/core-banking/src/main/kotlin/lab/banking/core/api/**
- services/core-banking/src/main/kotlin/lab/banking/core/ledger/**
- services/core-banking/src/test/kotlin/lab/banking/core/ledger/**
- services/core-banking/src/integrationTest/kotlin/lab/banking/core/ledger/**
- db/migrations/V006__*.sql only
- docs/architecture/core-api-ledger-*.md
Read-only reference:
- runtime/**
- legacy-node-reference/packages/banking-domain/src/**
- tests/ledger*.test.mjs
- tests/runtime.test.mjs
- docs/migration/**
Objective:
Implement additive Kotlin/Spring parity for ledger command behavior, idempotency, structured API errors, and /health. Do not change frontend apps. Do not change Node reference runtime. Mirror relevant Node scenarios and update architecture evidence.

B. workflow-cases
Role: workflow, maker-checker, complaint, FDS/AML, and reconciliation case owner.
Allowed write paths:
- services/core-banking/src/main/kotlin/lab/banking/core/workflow/**
- services/core-banking/src/main/kotlin/lab/banking/core/approval/**
- services/core-banking/src/main/kotlin/lab/banking/core/complaint/**
- services/core-banking/src/main/kotlin/lab/banking/core/fds/**
- services/core-banking/src/main/kotlin/lab/banking/core/aml/**
- services/core-banking/src/main/kotlin/lab/banking/core/reconciliation/**
- services/core-banking/src/test/kotlin/lab/banking/core/workflow/**
- services/core-banking/src/test/kotlin/lab/banking/core/approval/**
- services/core-banking/src/test/kotlin/lab/banking/core/complaint/**
- services/core-banking/src/test/kotlin/lab/banking/core/fds/**
- services/core-banking/src/test/kotlin/lab/banking/core/aml/**
- services/core-banking/src/test/kotlin/lab/banking/core/reconciliation/**
- db/migrations/V007__*.sql only
- docs/architecture/workflow-cases-*.md
Read-only reference:
- legacy-node-reference/packages/banking-domain/src/**
- tests/complaintWorkflow.test.mjs
- tests/fdsAmlReconciliation.test.mjs
- tests/makerChecker.test.mjs
Objective:
Port workflow state transitions, maker-checker restrictions, complaint lifecycle, FDS hold/release/block, AML case review, and reconciliation case lifecycle in additive Kotlin packages. Preserve structured error codes and audit hooks.

C. manifest-screen-factory
Role: screen manifest, form engine, screen generation, and manifest validation owner.
Allowed write paths:
- packages/screen-engine/**
- packages/form-engine/**
- legacy-node-reference/packages/banking-domain/src/screen*.mjs only if strictly necessary and backwards compatible
- screen-manifests/**
- packages/screen-engine/manifests/**
- docs/screen-manifests/**
- tests/manifestExpansion*.test.mjs
- docs/architecture/manifest-screen-factory-*.md
Read-only reference:
- apps/**
- tests/manifest.test.mjs
- scripts/validate-manifests.ts
Objective:
Add manifest conventions and reusable definitions for many bank screens without hand-coding each screen. Expand screen coverage for staff terminal, customer web, complaint portal, ops console, audit console, and FDS/AML console. Preserve manifest-declared roles, audit, masking, workflow, and approval metadata. Do not edit app UI files directly.

D. frontend-channels
Role: Next.js frontend channels and shared channel UX owner.
Allowed write paths:
- apps/customer-web/src/**
- apps/staff-terminal/src/**
- apps/complaint-portal/src/**
- apps/ops-console/src/**
- apps/audit-console/src/**
- apps/fds-aml-console/src/**
- apps/*/e2e/**
- apps/*/package.json only for app-local dependencies/scripts, not root package.json
- docs/architecture/frontend-channels-*.md
Read-only reference:
- legacy-node-reference/apps/**
- packages/screen-engine/**
- packages/form-engine/**
- docs/migration/**
Objective:
Create or extend Next.js shells that render from manifests and shared contracts. Implement customer web, staff integrated terminal, electronic complaint portal, ops/reconciliation console, audit console, and FDS/AML console as channel shells. Do not hand-code one-off business logic that bypasses manifests. Do not delete static public shells.

E. codex-skills-devex
Role: repo-scoped Codex skills, subagent configuration, and developer experience owner.
Allowed write paths:
- .agents/skills/**
- .codex/agents/**
- .codex/config.toml
- docs/codex/**
- docs/development/**
- docs/architecture/codex-devex-*.md
Read-only reference:
- all source files
Objective:
Add repo-scoped Codex skills and custom agent configs that make future work repeatable: ledger invariant review, manifest screen generator, maker-checker review, structured error review, evidence pack builder, and migration parity reviewer. Do not change implementation code.

F. qa-evidence-review
Role: read-heavy QA, parity, evidence, and integration reviewer.
Allowed write paths:
- docs/test-evidence/**
- docs/failure-drills/**
- docs/demo-scenarios/**
- docs/architecture/qa-evidence-*.md
- docs/codex/qa-review-*.md
- scripts/check-*.mjs only as new files
- tests/*CodexPlan*.test.mjs only as new files
Read-only reference:
- all implementation files
Objective:
Review subagent outputs against invariants, parity scenarios, structured API error contract, and node retirement gate. Add evidence docs and lightweight additive checks only. Do not edit root package.json or existing generated evidence scripts. Do not mark node retirement as ready unless every gate is truly proven.

Coordinator duties:
1. Collect all subagent results.
2. Resolve shared-file requests manually and serially.
3. Run or request these checks:
   - npm run parity
   - npm test
   - npm run validate:manifests
   - npm run evidence:pack
   - npm run node:retirement-gate
   - ./gradlew :services:core-banking:test when Gradle/JDK is available
   - ./gradlew :services:core-banking:integrationTest when Gradle/JDK is available
   - npm run next:customer-web:typecheck when Next dependencies are available
   - npm run next:customer-web:build when Next dependencies are available
4. If a command cannot run, document the exact reason and the closest lower-level check that did run.
5. Return a final merge plan with:
   - recommended merge order,
   - conflicts expected,
   - files that must be reviewed manually,
   - gates still blocking node retirement,
   - demo scenario coverage.

Implementation priorities:
1. Keep the project compiling and tests green after every merged slice.
2. Prefer additive implementation over rewrites.
3. Prefer contract-driven APIs and manifests over screen-by-screen duplication.
4. Prefer deterministic tests and evidence over broad claims.
5. Do not fake passing gates. If something is blocked, keep it blocked and explain why.
```

---

## 3. Merge order

Use this merge order to reduce conflicts:

1. `codex/e-codex-skills-devex`  
   Adds instructions and repo-scoped skills. Lowest implementation risk.

2. `codex/a-core-api-ledger`  
   Establishes backend API/ledger parity surfaces.

3. `codex/b-workflow-cases`  
   Builds on backend domain conventions.

4. `codex/c-manifest-screen-factory`  
   Adds manifest vocabulary and screen definitions.

5. `codex/d-frontend-channels`  
   Renders manifests and channel shells after manifest conventions stabilize.

6. `codex/f-qa-evidence-review`  
   Updates evidence and review docs after implementation branches settle.

7. Coordinator integration branch  
   Applies root `package.json`, CI, parity map, node retirement gate, and README updates if truly needed.

---

## 4. Conflict-avoidance rules

### 4.1 Shared files are locked

Only the coordinator may edit:

```text
README.md
package.json
package-lock.json
settings.gradle.kts
build.gradle.kts
docker-compose.yml
docker-compose.yaml
docs/migration/parity-scenarios.json
docs/migration/node-retirement-gate.json
.github/workflows/**
root tsconfig/eslint/prettier configs
```

Subagents needing changes to those files must write:

```text
docs/codex/coordination-notes/<agent-id>.md
```

Use this note format:

```md
# Coordination request: <agent-id>

## Required shared-file change
- File:
- Exact change:
- Reason:
- Risk if not applied:

## Dependency or script requested
- Package/script:
- Used by:
- Alternative without this change:
```

### 4.2 Migrations are append-only

- Do not edit `V001__foundation.sql` through `V005__fds_aml_reconciliation.sql`.
- Assigned migration numbers:
  - Agent A: `V006__*.sql`
  - Agent B: `V007__*.sql`
  - Coordinator-only follow-up: `V008__*.sql` and later
- If an agent discovers that a previous migration is wrong, create a new corrective migration instead of editing history.

### 4.3 Node reference is read-only

These are read-only until the retirement gate is ready:

```text
runtime/server.mjs
runtime/labApp.mjs
legacy-node-reference/packages/banking-domain/src/**/*.mjs
legacy-node-reference/packages/screen-engine/src/**/*.mjs
legacy-node-reference/packages/form-engine/src/**/*.mjs
legacy-node-reference/apps/**
tests/*.test.mjs
scripts/generate-*.mjs
```

Exception: Agent C may make backwards-compatible manifest-engine additions if unavoidable, but must not break current `npm run parity`.

### 4.4 Tests must be additive

- Existing Node tests are the baseline. Do not weaken them.
- Kotlin tests should mirror Node parity scenarios.
- Frontend tests should be added under app-level `e2e` folders.
- New Node tests must have unique names and must not replace existing suites.

---

## 5. Subagent child prompts

Use these if Codex needs direct child prompts instead of the single Goal-mode prompt.

### Agent A prompt: core-api-ledger

```text
You are Agent A: core-api-ledger.

Implement additive Kotlin/Spring backend parity for ledger, idempotency, structured API errors, and /health in kdh949/banking-lab.

Read first:
- README.md
- docs/migration/kotlin-next-playbook.md
- docs/migration/parity-scenarios.json
- docs/migration/node-retirement-gate.json
- docs/migration/structured-api-error-contract.md
- tests/ledger.test.mjs
- tests/ledgerCore.test.mjs
- tests/runtime.test.mjs
- runtime/**
- legacy-node-reference/packages/banking-domain/src/**

Allowed write paths:
- services/core-banking/src/main/kotlin/lab/banking/core/api/**
- services/core-banking/src/main/kotlin/lab/banking/core/ledger/**
- services/core-banking/src/test/kotlin/lab/banking/core/ledger/**
- services/core-banking/src/integrationTest/kotlin/lab/banking/core/ledger/**
- db/migrations/V006__*.sql only
- docs/architecture/core-api-ledger-*.md
- docs/codex/coordination-notes/a-core-api-ledger.md if shared changes are needed

Do not edit root package.json, README.md, docs/migration/*.json, runtime/**, existing tests/*.test.mjs, or existing migrations.

Deliver:
- Kotlin domain/application/API additions.
- Structured error handling aligned to the contract.
- Tests mirroring relevant Node ledger/runtime scenarios.
- Evidence doc listing covered parity scenarios.
- Coordination note for any shared config/dependency changes.

Run what you can:
- npm run parity
- npm test
- ./gradlew :services:core-banking:test
- ./gradlew :services:core-banking:integrationTest

If a command cannot run, record the exact reason.
```

### Agent B prompt: workflow-cases

```text
You are Agent B: workflow-cases.

Implement additive Kotlin workflow, maker-checker, complaint, FDS/AML, and reconciliation case behavior.

Read first:
- docs/migration/kotlin-next-playbook.md
- docs/migration/parity-scenarios.json
- docs/migration/structured-api-error-contract.md
- tests/makerChecker.test.mjs
- tests/complaintWorkflow.test.mjs
- tests/fdsAmlReconciliation.test.mjs
- legacy-node-reference/packages/banking-domain/src/**

Allowed write paths:
- services/core-banking/src/main/kotlin/lab/banking/core/workflow/**
- services/core-banking/src/main/kotlin/lab/banking/core/approval/**
- services/core-banking/src/main/kotlin/lab/banking/core/complaint/**
- services/core-banking/src/main/kotlin/lab/banking/core/fds/**
- services/core-banking/src/main/kotlin/lab/banking/core/aml/**
- services/core-banking/src/main/kotlin/lab/banking/core/reconciliation/**
- matching test folders under services/core-banking/src/test/kotlin/lab/banking/core/**
- db/migrations/V007__*.sql only
- docs/architecture/workflow-cases-*.md
- docs/codex/coordination-notes/b-workflow-cases.md if shared changes are needed

Do not edit Agent A ledger files unless you only add interfaces in your own package. Do not edit root shared files.

Deliver:
- State machines and policy checks.
- Maker-checker self-approval rejection.
- Complaint answer approval lifecycle.
- FDS hold/release/block lifecycle.
- AML case review lifecycle.
- Reconciliation mismatch and adjustment request lifecycle.
- Tests and evidence doc.
```

### Agent C prompt: manifest-screen-factory

```text
You are Agent C: manifest-screen-factory.

Expand the manifest-driven screen system so the repo can scale to many bank screens without hand-coding each one.

Read first:
- README.md
- tests/manifest.test.mjs
- scripts/validate-manifests.ts
- packages/screen-engine/**
- packages/form-engine/**
- docs/migration/parity-scenarios.json

Allowed write paths:
- packages/screen-engine/**
- packages/form-engine/**
- screen-manifests/**
- packages/screen-engine/manifests/**
- docs/screen-manifests/**
- tests/manifestExpansion*.test.mjs
- docs/architecture/manifest-screen-factory-*.md
- docs/codex/coordination-notes/c-manifest-screen-factory.md if shared changes are needed

Do not edit app UI files directly. Do not change current manifest behavior incompatibly.

Deliver:
- Manifest schema conventions for INQUIRY, COMMAND, CASE, PARAMETER screens.
- Expanded screen manifests for staff terminal, customer web, complaint portal, ops console, audit console, and FDS/AML console.
- Validation for roles, audit, masking, workflow, approval, and synthetic-only boundaries.
- Tests proving manifests validate.
```

### Agent D prompt: frontend-channels

```text
You are Agent D: frontend-channels.

Create or extend Next.js channel shells that render banking-lab screens from manifests and shared contracts.

Read first:
- README.md
- docs/migration/kotlin-next-playbook.md
- docs/architecture/next-customer-web-foundation.md if present
- apps/customer-web/src/**
- legacy-node-reference/apps/**
- packages/screen-engine/**
- packages/form-engine/**

Allowed write paths:
- apps/customer-web/src/**
- apps/staff-terminal/src/**
- apps/complaint-portal/src/**
- apps/ops-console/src/**
- apps/audit-console/src/**
- apps/fds-aml-console/src/**
- apps/*/e2e/**
- apps/*/package.json only for app-local dependencies/scripts
- docs/architecture/frontend-channels-*.md
- docs/codex/coordination-notes/d-frontend-channels.md if shared changes are needed

Do not edit packages/screen-engine or packages/form-engine. Do not delete legacy-node-reference/apps static shells. Do not edit root package.json.

Deliver:
- Manifest-rendered shells for customer web, staff terminal, complaint portal, ops console, audit console, and FDS/AML console.
- Staff terminal layout with transaction code input, tabbed screen area, customer context, approval inbox, audit panel.
- Customer web layout with accounts, transfers, complaint navigation, security/access history.
- Complaint portal intake/status layout.
- Ops/audit/FDS layouts based on manifest metadata.
- App-level tests or type checks where possible.
```

### Agent E prompt: codex-skills-devex

```text
You are Agent E: codex-skills-devex.

Add repo-scoped Codex skills and custom agent config to make future banking-lab work repeatable.

Read first:
- docs/codex/parallel-subagent-implementation-plan.md if present
- README.md
- docs/migration/**
- docs/architecture/**
- package.json

Allowed write paths:
- .agents/skills/**
- .codex/agents/**
- .codex/config.toml
- docs/codex/**
- docs/development/**
- docs/architecture/codex-devex-*.md

Do not edit source implementation files.

Deliver:
- Skills:
  - ledger-invariant-review
  - manifest-screen-generator
  - maker-checker-review
  - structured-error-contract-review
  - evidence-pack-builder
  - migration-parity-review
- Project-scoped custom agent TOML examples:
  - banking_explorer
  - banking_worker
  - banking_reviewer
  - banking_security_reviewer
- A short developer guide for invoking these skills.
```

### Agent F prompt: qa-evidence-review

```text
You are Agent F: qa-evidence-review.

Review all changes against parity, evidence, structured errors, and node retirement gates. Prefer read-heavy review. Only add evidence docs and lightweight additive checks.

Read first:
- README.md
- package.json
- docs/migration/kotlin-next-playbook.md
- docs/migration/parity-scenarios.json
- docs/migration/node-retirement-gate.json
- docs/migration/structured-api-error-contract.md
- docs/test-evidence/**
- docs/failure-drills/**
- tests/*.test.mjs

Allowed write paths:
- docs/test-evidence/**
- docs/failure-drills/**
- docs/demo-scenarios/**
- docs/architecture/qa-evidence-*.md
- docs/codex/qa-review-*.md
- scripts/check-*.mjs only as new files
- tests/*CodexPlan*.test.mjs only as new files
- docs/codex/coordination-notes/f-qa-evidence-review.md if shared changes are needed

Do not edit implementation files. Do not mark node retirement ready unless all gates are truly proven.

Deliver:
- Parity coverage matrix.
- Evidence gaps.
- Failure drill additions.
- Structured error contract gap report.
- Node retirement gate status recommendation.
- Commands run and command failures.
```

---

## 6. Optional Codex config snippets

Codex subagents can be made more predictable with project-scoped custom agents. Add these only if you want Codex to reuse them across future sessions.

### `.codex/config.toml`

```toml
[agents]
max_threads = 6
max_depth = 1
job_max_runtime_seconds = 7200
```

### `.codex/agents/banking-explorer.toml`

```toml
name = "banking_explorer"
description = "Read-only banking-lab explorer that maps execution paths, tests, and controls before implementation."
model_reasoning_effort = "medium"
sandbox_mode = "read-only"
developer_instructions = """
Stay read-only. Trace actual files, symbols, tests, and docs. Do not propose broad rewrites. Return concise evidence with file paths and risk notes.
"""
```

### `.codex/agents/banking-worker.toml`

```toml
name = "banking_worker"
description = "Implementation worker for one explicitly assigned banking-lab slice."
model_reasoning_effort = "high"
developer_instructions = """
Implement only the assigned slice and allowed paths. Prefer additive changes. Preserve Node reference runtime. Do not edit coordinator-owned files. Add tests and evidence for your slice.
"""
```

### `.codex/agents/banking-reviewer.toml`

```toml
name = "banking_reviewer"
description = "Reviewer for correctness, parity, merge conflict risk, and missing tests."
model_reasoning_effort = "high"
sandbox_mode = "read-only"
developer_instructions = """
Review like a bank-grade platform owner. Prioritize ledger correctness, idempotency, auditability, workflow state safety, masking, maker-checker, and evidence. Do not implement changes.
"""
```

---

## 7. Pull request checklist

Each subagent PR should include this checklist:

```md
## Scope
- Agent:
- Branch:
- Allowed paths followed: yes/no

## Controls
- [ ] Synthetic-only boundary preserved
- [ ] Node reference runtime untouched
- [ ] Double-entry ledger invariant preserved if ledger touched
- [ ] Idempotency preserved if command path touched
- [ ] Audit/masking preserved if staff/customer data touched
- [ ] Maker-checker preserved if high-risk operation touched
- [ ] Workflow state validity preserved if case lifecycle touched
- [ ] Structured API error contract followed if API touched
- [ ] Manifest authorization/audit/masking metadata preserved if screen touched

## Tests
- [ ] npm run parity
- [ ] npm test
- [ ] npm run validate:manifests
- [ ] npm run evidence:pack
- [ ] ./gradlew :services:core-banking:test
- [ ] ./gradlew :services:core-banking:integrationTest
- [ ] npm run next:customer-web:typecheck
- [ ] npm run next:customer-web:build

## Evidence
- Files added/updated:
- Parity scenarios covered:
- Known gaps:
- Coordinator-owned changes requested:
```

---

## 8. Practical guidance

For the current repo, the safest parallelization strategy is:

1. Use subagents for exploration and isolated implementation, not for shared config edits.
2. Keep the Node reference runtime as the oracle.
3. Let backend agents build target-stack parity.
4. Let manifest/frontend agents expand UI breadth through manifests, not one-off screen code.
5. Let QA/evidence run last.
6. Apply root-level dependency/script/CI changes only after child branches are reviewed.

This avoids the common failure mode where every agent edits `package.json`, `README.md`, migration files, or the same test suites and creates unnecessary conflicts.
