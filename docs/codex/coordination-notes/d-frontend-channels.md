# Agent D Frontend Channels Coordination Notes

## Completed In Scope

- Added manifest-rendered Next App Router shells for `staff-terminal`, `complaint-portal`, `ops-console`, `audit-console`, and `fds-aml-console`.
- Kept `apps/*/public/index.html` static Node reference shells unchanged.
- Did not edit `packages/screen-engine`, `packages/form-engine`, root shared files, `runtime/**`, or root workspace scripts.

## Coordinator-Owned Changes Requested

- Add root package scripts for the new channel apps, for example `next:staff-terminal:typecheck`, `next:staff-terminal:build`, and equivalent scripts for the other four apps.
- Update `package-lock.json` for the new app workspaces after coordinator approval, since Agent D scope allowed app-local `package.json` only.
- Add app-local `tsconfig.json`, `next.config.mjs`, and generated `next-env.d.ts` files for the new channels if the project wants strict TypeScript parity with `customer-web`.
- Add Playwright configuration and route wiring once the coordinator decides whether these shells are served independently by Next or through a shared BFF gateway.

## Validation Run By Agent D

- `npm --workspace @banking-lab/staff-terminal run typecheck`
- `npm --workspace @banking-lab/complaint-portal run typecheck`
- `npm --workspace @banking-lab/ops-console run typecheck`
- `npm --workspace @banking-lab/audit-console run typecheck`
- `npm --workspace @banking-lab/fds-aml-console run typecheck`
- `npm run validate:manifests`
- `node --test tests/nextScaffold.test.mjs`
- `node --test tests/manifest.test.mjs`

## Not Run

- Full `next build` for the five new channel apps, because it writes `.next` output and may generate config/type files outside Agent D's allowed write paths.
- Playwright E2E, because no app-level Playwright config or served target is in Agent D scope yet.
