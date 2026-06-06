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
| Spoofing | using a staff-only action as customer | Keycloak/OIDC roles, Spring route policy, and manifest roles |
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

- The archived Node reference runtime remains in-memory by design and must not be used as the target implementation.
- Target services have PostgreSQL/Flyway, Keycloak/OIDC, Redpanda/Kafka, Temporal, observability, security, and formal evidence for the current synthetic scope, but this is still a local lab, not production deployment proof.
- Live platform hardening still needs ingress-controller traffic, real TLS termination evidence, Argo CD controller sync health, canary promotion, and multi-node storage behavior.
- Broader future command paths should keep adding explicit retry or operator-visible failure policies for serialization conflicts.
