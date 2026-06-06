# Authorization Denial Spike Runbook

Synthetic lab only. Do not use real identities or real customer data.

## Symptoms

- `AuthorizationDeniedSpike` fires.
- Browser panels show authorization failures.
- `AUTHORIZATION_DENIED` audit events increase.

## Detection

```sql
SELECT actor_id, actor_role, screen_id, business_reference_id, count(*)
FROM audit_events
WHERE event_type = 'AUTHORIZATION_DENIED'
  AND created_at > now() - interval '15 minutes'
GROUP BY actor_id, actor_role, screen_id, business_reference_id
ORDER BY count(*) DESC;
```

## Immediate Containment

- Do not weaken route policies.
- Confirm simulator tokens remain disabled outside local dev/test.
- Pause browser smoke loops if they are causing the spike.

## Diagnosis Queries

```sql
SELECT payload_json->>'code' AS code, payload_json->>'policy' AS policy, count(*)
FROM audit_events
WHERE event_type = 'AUTHORIZATION_DENIED'
GROUP BY code, policy
ORDER BY count(*) DESC;
```

## Recovery Steps

1. Identify the route family and policy code.
2. Check token role, `sub`, `amr`, `acr`, `sid`, and device fingerprint.
3. Fix the synthetic realm, token generation, or client route usage.
4. Re-run the targeted security integration or browser smoke.

## Evidence To Capture

- Request id.
- Structured error code and policy.
- Audit event id.
- Token source, without copying token material.

## Rollback

Rollback a bad UI/client route change or realm fixture. Do not disable MFA/step-up globally to clear the alert.

## Escalation

Escalate to security owner if denials affect high-risk checker, audit export, PII, or projection rebuild routes.

## Post-incident Review

Add a route authorization regression test for the denied path.
