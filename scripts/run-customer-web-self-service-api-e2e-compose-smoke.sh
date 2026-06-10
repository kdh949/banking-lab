#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

export COMPOSE_PROJECT_NAME="${BANKING_LAB_CUSTOMER_WEB_API_E2E_COMPOSE_PROJECT:-banking-lab-customer-web-api-e2e}"
export BANKING_LAB_POSTGRES_PORT="${BANKING_LAB_POSTGRES_PORT:-15583}"
export BANKING_LAB_CORE_BANKING_PORT="${BANKING_LAB_CORE_BANKING_PORT:-18183}"
export BANKING_LAB_SECURITY_ENABLED=true
export BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true
export BANKING_LAB_DEV_SIMULATOR_TOKEN=true
export BANKING_LAB_CUSTOMER_AUTH_SYNTHETIC_TOKEN_ISSUER_ENABLED=true
export BANKING_LAB_SECURITY_ISSUER="${BANKING_LAB_SECURITY_ISSUER:-http://keycloak.local/realms/banking-lab,banking-lab-synthetic-customer-auth}"
export BANKING_LAB_SECURITY_AUDIENCE="${BANKING_LAB_SECURITY_AUDIENCE:-core-banking-api}"
export BANKING_LAB_SYNTHETIC_SEED_ENABLED=true
export BANKING_LAB_TRACING_ENABLED=false
export BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false
export BANKING_LAB_E2E_API_BASE_URL="http://127.0.0.1:${BANKING_LAB_CORE_BANKING_PORT}"

cleanup() {
  if [[ "${BANKING_LAB_CUSTOMER_WEB_API_E2E_KEEP_COMPOSE:-false}" == "true" ]]; then
    return
  fi
  docker compose --profile migration down -v --remove-orphans >/dev/null 2>&1 || true
}

wait_for_url() {
  local url="$1"
  local label="$2"
  local attempts="${3:-90}"
  local delay_seconds="${4:-2}"
  local attempt
  for attempt in $(seq 1 "${attempts}"); do
    if curl -fsS "${url}" >/dev/null 2>&1; then
      return 0
    fi
    sleep "${delay_seconds}"
  done
  echo "${label} was not ready after ${attempts} attempts: ${url}" >&2
  return 1
}

create_simulator_token() {
  node -e '
    const [subject, ...roles] = process.argv.slice(1);
    const payload = {
      iss: "http://keycloak.local/realms/banking-lab",
      sub: subject,
      aud: process.env.BANKING_LAB_SECURITY_AUDIENCE || "core-banking-api",
      roles,
      active: true
    };
    process.stdout.write(`Bearer lab.${Buffer.from(JSON.stringify(payload)).toString("base64url")}.sig`);
  ' "$@"
}

record_smoke_pass() {
  node -e '
    const { mkdirSync, writeFileSync } = require("node:fs");
    const { dirname } = require("node:path");
    const outputPath = "docs/test-evidence/generated/customer-web-self-service-api-e2e-compose-smoke.json";
    const evidence = {
      reviewDate: "2026-06-11",
      executedAt: new Date().toISOString(),
      issue: "https://github.com/kdh949/banking-lab/issues/90",
      status: "pass",
      syntheticOnly: true,
      localOnly: true,
      hostedCiGreenClaim: false,
      app: "customer-web",
      command: "npm run test:customer-web:self-service-api-e2e-compose",
      scenario: "signup -> login -> staff maker-checker account opening -> accounts -> transfer -> history/status routes",
      controls: [
        "synthetic signup and login",
        "explicit dev/test synthetic customer auth issuer opt-in",
        "staff maker-checker account opening",
        "masked owned-account route rendering",
        "internal recipient lookup",
        "idempotent transfer replay",
        "ledger transaction history readback"
      ],
      notes: [
        "Executed against disposable PostgreSQL and Spring Boot core-banking Compose services.",
        "Simulator tokens are enabled only by explicit dev/test environment variables in this wrapper.",
        "This evidence does not use real customer money, real PII, real KYC/AML providers, real card networks, real payment networks, or external financial institution APIs."
      ]
    };
    mkdirSync(dirname(outputPath), { recursive: true });
    writeFileSync(outputPath, `${JSON.stringify(evidence, null, 2)}\n`);
  '
}

cleanup
trap cleanup EXIT

scripts/run-core-banking-tests.sh :services:core-banking:bootJar
docker compose --profile migration up -d --build postgres core-banking

wait_for_url "${BANKING_LAB_E2E_API_BASE_URL}/health" "core-banking health"

export BANKING_LAB_E2E_STAFF_MAKER_BEARER_TOKEN="$(create_simulator_token e2e-maker BRANCH_STAFF)"
export BANKING_LAB_E2E_STAFF_CHECKER_BEARER_TOKEN="$(create_simulator_token e2e-checker BRANCH_MANAGER)"

npm run test:e2e -- \
  apps/customer-web/e2e/customer-onboarding-self-service.spec.ts \
  --project=chromium \
  --workers=1

record_smoke_pass
