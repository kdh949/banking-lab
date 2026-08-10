# ADR 0009 — Channel Client and UI Boundaries

## Status

Accepted on 2026-08-10.

## Context

The shared API client exposed every domain through one 4,668-line entry file. Channel code could accidentally depend on unrelated privileged methods, while the root import was already used broadly and could not be broken safely. The shared channel UI also mixed design tokens, generic primitives, customer surfaces, and operator surfaces in one entry point. Creating three new packages would duplicate the existing primitives and CSS before the product boundaries were stable.

The integrated staff terminal had a 1,484-line `screens.tsx` file combining static portal/product navigation and Spring API workbench screens.

## Decision

1. Preserve `@banking-lab/api-client` as a backward-compatible barrel.
2. Move its implementation to `client.ts` and expose constrained domain entry points:
   - `@banking-lab/api-client/customer`
   - `@banking-lab/api-client/call-center`
   - `@banking-lab/api-client/staff`
   - `@banking-lab/api-client/risk`
   - `@banking-lab/api-client/operations`
3. Domain factories return only their declared method set. Lab evidence may continue using the broad compatibility client because it intentionally exercises multiple domains.
4. Keep one `channel-ui` implementation. Split its CSS design tokens, shared primitives, customer entry point, and operator-workbench entry point instead of adding duplicate packages.
5. Split staff terminal screens into static navigation screens and API workbench screens. New FDS and journey screens must be added as domain modules rather than growing a new monolith.

## Consequences

- Existing root imports continue to compile.
- Product code can express least-capability dependencies in imports and client return types.
- The internal `client.ts` remains large for now; future domains can migrate implementation methods behind the domain factories without another public import change.
- Customer and operator UI can evolve independently while keeping the same tokens and accessible primitives.
- Staff screen changes have a stable domain seam for `FDS201` and journey-aware screens.

## Rejected alternatives

- New `design-tokens`, `customer-ui`, and `operator-workbench` packages were rejected because they would duplicate an existing 187-line primitive layer and create premature versioning overhead.
- Removing the root API client export was rejected because it would break every existing channel and evidence surface in one checkpoint.
