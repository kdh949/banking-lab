# Evidence Refresh Review

Date: 2026-06-03

Status: pass

## Scope

This review covers the current evidence-refresh gate for the Kotlin/Spring and Next.js migration. It checks that the evidence docs, generated evidence pack, parity matrix, security/observability evidence, Node reference boundary docs, and retirement gate metadata are consistent with the current target-stack implementation.

This review does not mark Node retirement ready. Node retirement remains blocked by non-synthetic passkey operations and final retirement review.

## Commands

- `npm run passkey:evidence:prepare` passed and generated ignored local TODO templates under `tmp/passkey-evidence-manual/`.
- `npm run passkey:evidence:readiness` passed against the ignored local TODO templates under `tmp/passkey-evidence-manual/`.
- `COMPOSE_PROJECT_NAME=banking-lab-passkey-manual BANKING_LAB_POSTGRES_PORT=15477 BANKING_LAB_CORE_BANKING_PORT=18126 BANKING_LAB_KEYCLOAK_PORT=18127 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://localhost:18127/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking` passed and started the local manual passkey readiness stack.
- `curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://localhost:18127/realms/banking-lab/.well-known/openid-configuration` passed and returned issuer `http://localhost:18127/realms/banking-lab`.
- `curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18126/health` passed and returned `status=ok`, `syntheticOnly=true`, `auditHashChainValid=true`, and `migrationTarget=kotlin-spring-boot`.
- `npm run passkey:evidence:live-readiness` initially failed in the sandbox because Node `fetch` could not reach loopback endpoints; the same command passed under the approved execution path against the live local stack.
- `npm run retirement:final-review:prepare` passed and generated ignored local TODO templates under `tmp/final-retirement-review-manual/`.
- `npm run passkey:evidence:preflight` passed.
- `node --test tests/passkeyLivePlatformReadiness.test.mjs tests/passkeyEvidencePreflight.test.mjs tests/evidenceRefresh.test.mjs` passed 8 tests.
- `node --test tests/passkeyManualReadiness.test.mjs tests/passkeyEvidencePreflight.test.mjs tests/passkeyEvidencePrepare.test.mjs tests/evidenceRefresh.test.mjs` passed 13 tests.
- `node --test tests/passkeyManualReadiness.test.mjs tests/passkeyEvidencePreflight.test.mjs tests/passkeyEvidencePrepare.test.mjs` initially failed because the manual-authenticator summary in the prepared template read like completed evidence; after changing it to a replacement instruction it passed 11 tests.
- `node --test tests/passkeyEvidencePrepare.test.mjs tests/passkeyEvidencePreflight.test.mjs tests/evidenceRefresh.test.mjs` passed 8 tests.
- `node --test tests/finalRetirementReviewPrepare.test.mjs tests/retirementReviewPreflight.test.mjs tests/evidenceRefresh.test.mjs` initially failed because this review did not yet list `npm run retirement:final-review:prepare`; after the document was corrected it passed 7 tests.
- `node --test tests/passkeyEvidenceRecorder.test.mjs tests/passkeyEvidenceVerifier.test.mjs tests/passkeyEvidencePreflight.test.mjs tests/finalRetirementReviewRecorder.test.mjs tests/finalRetirementReviewVerifier.test.mjs tests/nodeRetirementReadySimulation.test.mjs tests/goalCompletionAudit.test.mjs` passed 34 tests.
- `node --test tests/finalRetirementReviewRecorder.test.mjs tests/finalRetirementReviewVerifier.test.mjs tests/passkeyEvidenceVerifier.test.mjs tests/nodeRetirementReadySimulation.test.mjs tests/retirementReviewPreflight.test.mjs tests/evidenceRefresh.test.mjs` passed 26 tests.
- `node --test tests/passkeyEvidenceRecorder.test.mjs tests/passkeyEvidenceVerifier.test.mjs tests/passkeyEvidencePreflight.test.mjs tests/nodeRetirementReadySimulation.test.mjs tests/retirementBoundaryAudit.test.mjs tests/evidenceRefresh.test.mjs` passed 19 tests.
- `npm run scripts:typecheck` passed.
- `npm run retirement:audit` passed with blocked status, 64 approved-reference `.mjs` files, 37 target anchors, 332 evidence paths, and 42/42 mapped scenarios.
- `npm run retirement:stack-audit` passed.
- `npm run retirement:generated-boundary` passed.
- `npm run retirement:ready-simulate` passed.
- `npm run goal:completion-audit` passed with `not complete` status and blocked passkey/final-review items.
- `npm run node:retirement-gate` passed with blocked status.
- `npm test` passed 131 tests.
- `npm run validate:manifests` validated 28 manifests.
- `git diff --check` passed.
- Initial sandboxed `npm run parity` failed because Node reference tests could not bind `127.0.0.1` (`listen EPERM`). The same command passed under the approved execution path with 131 Node reference/structural tests, 28 manifest validations, 6 screen-engine tests, and evidence pack generation.
- `npm run evidence:pack` passed and regenerated the evidence pack summary.
- `npm run evidence:refresh-check` passed.

## Result

- `docs/test-evidence/generated/evidence-pack-summary.json` reports all generated evidence checks passing with `syntheticOnly=true`.
- `docs/test-evidence/parity-coverage-matrix.md` now reflects pass status for current mapped parity instead of stale partial status.
- `docs/test-evidence/evidence-gap-report.md` keeps Node retirement blocked for passkey and Node-independence review without listing evidence-refresh as a remaining blocker.
- `docs/architecture/qa-evidence-node-retirement-recommendation.md` marks evidence-refresh passed while keeping Node retirement blocked.
- `docs/test-evidence/stack-retirement-area-audit.md` proves target implementation areas are free of legacy Node MVP stack source while preserving the approved Node oracle/support paths.
- `docs/test-evidence/generated-artifact-boundary.md` proves generated `.next`/`build` output is ignored and not tracked as target source.
- `npm run retirement:ready-simulate` proves the future ready path with fixture passkey/final-review artifacts without changing the real blocked gate.
- `npm run passkey:evidence:prepare` creates ignored local command, staff-panel, and recorder-command templates with TODO values so the future manual run starts from the required redaction format without accidentally producing pass evidence.
- `npm run passkey:evidence:readiness` proves the prepared local templates align with the documented Compose ports, Keycloak issuer, simulator-token-disabled startup, Spring health URL, recorder command, and masked staff-panel markers without marking passkey evidence complete.
- `passkey:evidence:live-readiness` now has a fixture-tested verifier and a live local stack run for the future live stack step: it checks prepared templates against Keycloak discovery/JWKS and Spring `/health`, while still refusing to prove non-synthetic passkey operations.
- `npm run retirement:final-review:prepare` creates ignored local command and recorder-command templates with TODO values so the future post-passkey final review starts from the required command/control format without accidentally producing pass evidence.
- Passkey recorder and verifier evidence now require structured passing command evidence with `command`, `status: "pass"`, `exitCode: 0`, a non-empty `summary`, no duplicate commands, live Compose startup, simulator-token-disabled Spring configuration, Keycloak discovery readiness, Spring `/health` readiness, a real platform/hardware authenticator attestation, structured manual ceremony evidence for `http://localhost` staff-terminal origin, `http://localhost/.../realms/banking-lab` issuer, RP ID `localhost`, synthetic `manager-webauthn01`, Authorization Code + PKCE, no browser virtual authenticator automation, and the recorder command before accepting future non-synthetic passkey evidence.
- Final retirement review recorder and verifier now require the referenced passkey evidence artifact to pass the strict passkey verifier, then require post-passkey `retirement:ready-simulate`, `passkey:evidence:preflight`, and `retirement:review-preflight` command evidence in addition to parity, manifest, evidence pack, boundary, and passkey verifier commands.
- Final retirement review recorder and verifier now also reject duplicate command entries, non-object command evidence entries, silent non-object extras, and any extra command evidence item that is not passing.
- `docs/test-evidence/final-retirement-review-verifier.md` defines the future final-review artifact recorder/verifier schema and keeps the retirement review fail-closed until post-passkey commands and control attestations are recorded.
- `docs/test-evidence/goal-completion-audit.md` keeps the active objective blocked until passkey evidence, final review, and the ready retirement gate are all proven.
- `docs/migration/node-retirement-gate.json` marks `evidence-refresh` passed and leaves `non-synthetic-passkey-operations` plus `retirement-review` incomplete.

## Remaining Blockers

- Non-synthetic passkey operations require a real platform authenticator or hardware security key, simulator tokens disabled, Spring JWKS validation, masked staff detail, and the generated redacted evidence artifact.
- Final retirement review must still confirm no ledger, idempotency, audit, masking, maker-checker, workflow, reconciliation, or evidence behavior depends on Node-only code.
