# Manifest Renderer V2

## Scope

The target screen platform treats `screen-manifests/**` as the catalog for reusable banking screen rendering instead of a card-only index. `staff-terminal` is intentionally excluded from this catalog because its official UI is the iWorks integrated terminal.

The repository validates:

- at least 60 total manifests;
- unique `screenId`;
- unique `transactionCode`;
- per-channel minimum counts;
- reusable shape rules for `INQUIRY`, `COMMAND`, `CASE`, and `PARAMETER`.

## Renderer Templates

The reusable renderer capability applies to manifest-backed channel apps such as customer web, complaint portal, operations, audit, FDS/AML, and admin. Staff-terminal screens must not reintroduce the removed staff manifest renderer.

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

Manifest-backed channel renderers use manifest metadata for reason-required lookup, masking state, maker-checker approval, workflow timelines, and structured error surfaces.

## API-backed Boundary

Screens with existing Spring API routes are labeled as API-backed and retain connectivity through the existing `@banking-lab/api-client` smoke panels in the manifest-backed channels. Screens without a current target API are shown as `declared-only / not API-backed yet`; renderers do not show fake success for those screens.

## Synthetic Boundary

All examples use synthetic customer, account, approval, case, and transaction identifiers. No real customer data, real payment network, real KYC provider, or real financial institution API is represented.
