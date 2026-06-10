# iWorks Integrated Staff Terminal Architecture

## Official Boundary

`apps/staff-terminal` now has one official UI: the iWorks integrated terminal.

Official runtime files:

- `apps/staff-terminal/src/app/page.tsx`
- `apps/staff-terminal/src/app/layout.tsx`
- `apps/staff-terminal/src/app/api/terminal-status/route.ts`
- `apps/staff-terminal/src/components/terminal/IntegratedTerminalApp.tsx`
- `apps/staff-terminal/src/components/terminal/**`
- `apps/staff-terminal/src/components/integrated-terminal.css`
- `apps/staff-terminal/e2e/integrated-terminal.spec.ts`

The app exposes `/` and `/api/terminal-status` only. Staff-terminal manifests,
old route pages, old API-backed panels, and old manifest renderer components are
removed.

## Runtime Flow

```text
Browser
  -> Next.js staff-terminal /
  -> IntegratedTerminalApp
  -> reusable terminal shell/components
  -> /api/terminal-status for client IP and connected server time
```

Operator actions inside the terminal either navigate to implemented synthetic
screens or show an unavailable-work modal with an X mark. Flowchart process
nodes and menu rows use the same navigation registry so future screens can be
added through shared terminal primitives instead of page-route copy-paste.

## Control Rules

- All data is synthetic.
- The UI must not expose real PII, real money, real bank APIs, real KYC, or real payment networks.
- Implemented module selection and hover state are visually distinct.
- Only one module can be selected at a time.
- Number-entry fields restrict input to digits.
- Lookup icons open modal lookup surfaces rather than inert decoration.
- The bottom status bar reads current client IP and server time from `/api/terminal-status`.

## Verification

- `npm run next:staff-terminal:typecheck`
- `npm run next:staff-terminal:build`
- `npm run integrated-terminal:boundary-check`
- `npx playwright test apps/staff-terminal/e2e/integrated-terminal.spec.ts`
