# Notification Service

## Goal

Send synthetic SMS, app push, email, and chat-style notifications for login,
transaction, abnormal activity, maturity, case, approval, and operations events
through durable event consumption and simulated providers.

## Current Code To Inspect

- `contracts/events/**`
- `contracts/asyncapi/**`
- `services/core-banking/src/main/kotlin/lab/banking/core/eventing/**`
- `infra/docker-compose/**`
- `docs/implementation-coverage-matrix.md`
- `docs/test-evidence/outbox-worker-failure-drill.md`

## Target Folder Placement

Create `services/notification-service` for templates, preferences, event
consumers, delivery logs, retries, dead-letter handling, and synthetic provider
sinks. Core-banking should emit events, not send provider calls directly.

## Backend Implementation Plan

- Consume durable Outbox/Kafka events.
- Apply customer/staff notification preferences.
- Render templates with masked data.
- Write delivery attempts and final delivery status.
- Retry transient failures and dead-letter permanent failures.
- Use synthetic SMS/push/email/chat sinks only.

## Database / Migration Plan

Use notification templates, recipient preferences, delivery requests, delivery
attempts, provider sink records, retry state, and dead-letter records.

## API / Event / Workflow Contracts

Define events for notification requested, delivery attempted, delivered, failed,
and dead-lettered. Expose admin/template and delivery history APIs.

## Frontend / Screen Manifest Plan

Customer web may show notification preferences and delivery history. Admin
Console manages templates and channels. Audit Console can inspect delivery
events.

## Security, Audit, Maker-Checker Controls

Templates that expose sensitive data require approval. Delivery logs must mask
PII. Staff access requires reason. Provider config is synthetic-only.

## Tests And Evidence

Test template rendering, preference filtering, event consumption idempotency,
retry, dead-letter, masking, and synthetic provider boundary. Add Kafka/Redpanda
or contract tests.

## Acceptance Criteria

- Notification Service is durable and event-driven.
- Delivery attempts are auditable.
- Retry/dead-letter behavior is tested.
- No real SMS, push, email, or chat provider is used.

## Explicit Non-Goals

No production messaging provider, no real phone/email delivery, and no marketing
campaign system.

