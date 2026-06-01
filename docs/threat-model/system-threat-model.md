# System Threat Model

## Scope

This threat model covers the local synthetic banking lab: customer web, staff terminal, complaint portal, FDS/AML console, ops console, audit console, runtime APIs, domain services, and evidence scripts.

## Trust Boundaries

- Customer self-service APIs
- Staff and operations APIs
- Approval execution path
- Ledger command service
- Synthetic external simulators
- Generated evidence artifacts

## STRIDE Summary

| Threat | Example | Control |
| --- | --- | --- |
| Spoofing | using a staff-only action as customer | role-shaped mock sessions and manifest roles |
| Tampering | changing balance directly | no balance mutation API; balances projected from postings |
| Repudiation | staff denies sensitive lookup | reason-required audit hash chain |
| Information disclosure | customer sees internal complaint draft | public complaint DTO removes draft and approval fields |
| Denial of service | duplicate transfer retry | idempotency keys prevent duplicate posting |
| Elevation of privilege | maker approves own request | maker-checker rejects same maker/checker |

## High-Impact Abuse Cases

| Abuse case | Evidence |
| --- | --- |
| Duplicate withdrawal overdraws account | concurrent withdrawal test |
| FDS hold posts before review | Phase 6 FDS no-posting test |
| Complaint answer visible before approval | Phase 5 public DTO test |
| Closed-day transaction mutates ledger | Phase 6 closed-day test |
| Reconciliation patch bypasses ledger | Phase 6 adjustment transaction test |

## Residual Risk

- Runtime state is in-memory.
- Approval execution is not crash-recovered.
- Mock auth is not production identity.
- No centralized observability stack yet.
- No formal TLA+/Alloy model yet.
