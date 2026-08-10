#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

export COMPOSE_PROJECT_NAME="${BANKING_LAB_CALL_CENTER_KEYCLOAK_E2E_COMPOSE_PROJECT:-banking-lab-call-center-keycloak-e2e}"
export BANKING_LAB_POSTGRES_PORT="${BANKING_LAB_POSTGRES_PORT:-15572}"
export BANKING_LAB_CORE_BANKING_PORT="${BANKING_LAB_CORE_BANKING_PORT:-18172}"
export BANKING_LAB_KEYCLOAK_PORT="${BANKING_LAB_KEYCLOAK_PORT:-18173}"
export BANKING_LAB_SECURITY_ENABLED=true
export BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false
export BANKING_LAB_DEV_SIMULATOR_TOKEN=false
export BANKING_LAB_SECURITY_STEP_UP_ENFORCEMENT_ENABLED=false
export BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs
export BANKING_LAB_SECURITY_ISSUER="http://127.0.0.1:${BANKING_LAB_KEYCLOAK_PORT}/realms/banking-lab"
export BANKING_LAB_SECURITY_AUDIENCE=core-banking-api
export BANKING_LAB_SYNTHETIC_SEED_ENABLED=true
export BANKING_LAB_TRACING_ENABLED=false
export BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false
export BANKING_LAB_CORS_ALLOWED_ORIGINS="http://localhost:3008,http://127.0.0.1:3008"
export BANKING_LAB_E2E_API_BASE_URL="http://127.0.0.1:${BANKING_LAB_CORE_BANKING_PORT}"
export BANKING_LAB_E2E_KEYCLOAK_BASE_URL="http://127.0.0.1:${BANKING_LAB_KEYCLOAK_PORT}"

cleanup() {
  if [[ "${BANKING_LAB_CALL_CENTER_KEYCLOAK_E2E_KEEP_COMPOSE:-false}" == "true" ]]; then
    return
  fi
  docker compose --profile platform down -v --remove-orphans >/dev/null 2>&1 || true
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

cleanup
trap cleanup EXIT

scripts/run-core-banking-tests.sh :services:core-banking:bootJar
docker compose --profile platform up -d --build postgres keycloak core-banking

wait_for_url "${BANKING_LAB_E2E_KEYCLOAK_BASE_URL}/realms/banking-lab/.well-known/openid-configuration" "Keycloak OIDC discovery"
wait_for_url "${BANKING_LAB_E2E_API_BASE_URL}/health" "core-banking health"

keycloak_access_token() {
  local username="$1"
  local password="$2"
  local token_response
  token_response="$(
    curl -fsS -X POST "${BANKING_LAB_E2E_KEYCLOAK_BASE_URL}/realms/banking-lab/protocol/openid-connect/token" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      --data-urlencode "grant_type=password" \
      --data-urlencode "client_id=call-center-console" \
      --data-urlencode "username=${username}" \
      --data-urlencode "password=${password}"
  )"
  TOKEN_RESPONSE="${token_response}" node -e '
    const response = JSON.parse(process.env.TOKEN_RESPONSE || "{}");
    if (!response.access_token) {
      console.error("Keycloak token response did not contain access_token");
      process.exit(1);
    }
    process.stdout.write(response.access_token);
  '
}

AGENT_ACCESS_TOKEN="$(keycloak_access_token call-agent01 call-agent01-pass)"
MANAGER_ACCESS_TOKEN="$(keycloak_access_token call-manager01 call-manager01-pass)"
curl -fsS "${BANKING_LAB_E2E_API_BASE_URL}/api/staff/call-center/customers/search?query=SYN-CUS&reason=Compose%20call-center%20Keycloak%20preflight" \
  -H "Authorization: Bearer ${AGENT_ACCESS_TOKEN}" >/dev/null
curl -fsS "${BANKING_LAB_E2E_API_BASE_URL}/api/staff/call-center/customers/search?query=SYN-CUS&reason=Compose%20call-center%20manager%20Keycloak%20preflight" \
  -H "Authorization: Bearer ${MANAGER_ACCESS_TOKEN}" >/dev/null

npx playwright test apps/call-center-console/e2e/call-center-console-parity.spec.ts \
  --grep "call-center console completes live Keycloak PKCE login through the opaque product BFF" \
  --project=chromium \
  --workers=1
