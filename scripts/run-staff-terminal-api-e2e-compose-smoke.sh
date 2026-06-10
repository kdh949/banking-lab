#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

export COMPOSE_PROJECT_NAME="${BANKING_LAB_STAFF_TERMINAL_API_E2E_COMPOSE_PROJECT:-banking-lab-staff-terminal-api-e2e}"
export BANKING_LAB_POSTGRES_PORT="${BANKING_LAB_POSTGRES_PORT:-15582}"
export BANKING_LAB_CORE_BANKING_PORT="${BANKING_LAB_CORE_BANKING_PORT:-18182}"
export BANKING_LAB_SECURITY_ENABLED=true
export BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true
export BANKING_LAB_DEV_SIMULATOR_TOKEN=true
export BANKING_LAB_SECURITY_ISSUER="${BANKING_LAB_SECURITY_ISSUER:-http://keycloak.local/realms/banking-lab}"
export BANKING_LAB_SECURITY_AUDIENCE="${BANKING_LAB_SECURITY_AUDIENCE:-core-banking-api}"
export BANKING_LAB_SYNTHETIC_SEED_ENABLED=true
export BANKING_LAB_TRACING_ENABLED=false
export BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false
export BANKING_LAB_E2E_API_BASE_URL="http://127.0.0.1:${BANKING_LAB_CORE_BANKING_PORT}"
export NEXT_PUBLIC_BANKING_SIMULATOR_TOKENS_ENABLED=true

cleanup() {
  if [[ "${BANKING_LAB_STAFF_TERMINAL_API_E2E_KEEP_COMPOSE:-false}" == "true" ]]; then
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

cleanup
trap cleanup EXIT

scripts/run-core-banking-tests.sh :services:core-banking:bootJar
docker compose --profile migration up -d --build postgres core-banking

wait_for_url "${BANKING_LAB_E2E_API_BASE_URL}/health" "core-banking health"
curl -fsS \
  "${BANKING_LAB_E2E_API_BASE_URL}/api/staff/customers/SYN-CUS-001/detail?reason=Compose%20staff-terminal%20API%20preflight" \
  -H "Authorization: $(create_simulator_token staff-terminal01 BRANCH_STAFF)" >/dev/null

npm run test:e2e -- \
  apps/staff-terminal/e2e/integrated-terminal.spec.ts \
  --grep "Spring staff API evidence" \
  --project=chromium \
  --workers=1
