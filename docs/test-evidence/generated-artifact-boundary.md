# Generated Artifact Boundary

Date: 2026-06-03

Status: pass

## Scope

This evidence distinguishes target source files from generated build/test output. The target source tree must not contain tracked or untracked source-like `.mjs`, `.cjs`, `.js`, `.jsx`, or `.html` files outside generated directories. Generated output under `.next`, `build`, `dist`, `node_modules`, coverage, and test-report directories must be ignored by git.

This document does not mark Node retirement ready.

## Command

```bash
npm run retirement:generated-boundary
```

## Current Result

Passed on 2026-06-03.

The audit verifies:

- tracked target source under `apps/`, `services/`, `packages/`, `analytics/`, `infra/`, `contracts/`, and `db/` has no legacy/static generated source extensions;
- untracked target files with those extensions are not source-like files outside generated directories;
- generated files with those extensions are ignored by git.

## Retirement Impact

This audit prevents generated Next.js/Gradle output from being confused with allowed target source. Node retirement remains blocked by non-synthetic passkey operations and final retirement review.
