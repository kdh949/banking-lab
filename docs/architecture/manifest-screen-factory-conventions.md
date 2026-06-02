# Manifest Screen Factory Conventions

## Scope

The manifest screen factory defines reusable screen contracts for the target Next.js/React screen renderer. The current Node runtime remains a legacy oracle only; these contracts are intended to be portable to the target channel apps.

## Template Contracts

| Type | Layout template | Required manifest keys | Required control metadata |
|---|---|---|---|
| `INQUIRY` | `inquiry` | `query`, `resultTable` | roles, audit, masking, reason |
| `COMMAND` | `command` | `fields`, `api` | roles, audit, validation, approval |
| `CASE` | `case` | `workflow` | roles, audit, workflow, approval, SLA |
| `PARAMETER` | `parameter` | `parameter`, `fields`, `api`, `approval` | roles, audit, approval, rollback |

`DASHBOARD` remains supported for existing operational status screens, but it is not one of the reusable banking transaction templates.

## Control Preservation

Every expanded manifest exposes derived `controlMetadata` with:

- required roles for RBAC or future ABAC decisions;
- audit enablement, reason requirement, PII access, and masking policy;
- masking defaults and field-level masks;
- maker-checker metadata and approval business types;
- workflow name, states, and timeline requirement for case screens;
- `syntheticOnly: true` to keep the simulator boundary visible.

## Channel Coverage

Current manifests cover:

- staff terminal: inquiry, command, case;
- customer web: inquiry, command, case;
- complaint portal: case;
- operations console: case, dashboard, parameter;
- audit console: inquiry, parameter;
- FDS/AML console: case, parameter.

## Parity Link

The expansion tests bind this convention layer to the manifest parity controls in `docs/migration/parity-scenarios.json`: manifest validation, audit, masking, maker-checker, workflow, complaint, FDS/AML, and reconciliation.
