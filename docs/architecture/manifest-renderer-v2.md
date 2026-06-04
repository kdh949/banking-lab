# Manifest Renderer V2

## Scope

The target screen platform now treats `screen-manifests/**` as the catalog for reusable banking screen rendering instead of a card-only index.

The repository validates:

- at least 60 total manifests;
- unique `screenId`;
- unique `transactionCode`;
- per-channel minimum counts;
- reusable shape rules for `INQUIRY`, `COMMAND`, `CASE`, and `PARAMETER`.

## Renderer Templates

`apps/staff-terminal/src/components/manifest-renderer.tsx` implements the first full workstation renderer:

- `ScreenRenderer`
- `InquiryScreenRenderer`
- `CommandScreenRenderer`
- `CaseScreenRenderer`
- `ParameterScreenRenderer`
- `DashboardScreenRenderer`
- `TransactionCodeLauncher`
- `CustomerContextPanel`
- `SearchPanel`
- `DataTable`
- `DetailPanel`
- `FormRenderer`
- `ActionPanel`
- `ApprovalPanel`
- `AuditTimeline`
- `MaskedValue`
- `StructuredErrorView`

The renderer uses manifest metadata for reason-required lookup, masking state, maker-checker approval, workflow timelines, and structured error surfaces.

## API-backed Boundary

Screens with existing Spring API routes are labeled as API-backed and retain connectivity through the existing `@banking-lab/api-client` smoke panel. Screens without a current target API are shown as `declared-only / not API-backed yet`; the renderer does not show fake success for those screens.

## Synthetic Boundary

All examples use synthetic customer, account, approval, case, and transaction identifiers. No real customer data, real payment network, real KYC provider, or real financial institution API is represented.
