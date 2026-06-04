# Synthetic Secret Rotation Runbook

This runbook is for the banking-lab synthetic environment only. Do not place real Keycloak client secrets, database passwords, customer data, payment credentials, KYC data, or financial-network credentials in this repository.

## Scope

Rotatable secret classes:

- Keycloak client secrets supplied through environment variables or Kubernetes Secrets;
- PostgreSQL credentials supplied through environment variables or Kubernetes Secrets;
- synthetic KMS/HSM key versions managed by `synthetic_kms_keys` and `/api/ops/security/kms/rotate`.

## Procedure

1. Create the replacement value outside the repository.
2. Update the local `.env` file or Kubernetes Secret object; never commit the value.
3. Restart the affected synthetic service or roll the Kubernetes deployment.
4. Run health and authorization smoke checks.
5. For synthetic KMS keys, call `/api/ops/security/kms/rotate` with a fresh step-up token and a business reason.
6. Verify old WORM audit segments still validate through `/api/ops/security/audit-exports/verification`.
7. Record the command output in the evidence pack.

## Verification Commands

```bash
npm run siem:alert-drill
npm run test:core-banking:integration -- --tests lab.banking.core.opsec.OperationalSecurityIntegrationTest --rerun-tasks
```

## Non-Claims

- This is not a real HSM, cloud KMS, or PAM integration.
- This does not rotate live production secrets.
- This does not authorize any real privileged infrastructure access.
