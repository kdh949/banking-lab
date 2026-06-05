#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

export COMPOSE_PROJECT_NAME="${BANKING_LAB_PAYMENT_KEYCLOAK_COMPOSE_PROJECT:-banking-lab-payment-keycloak-service-token-smoke}"
export BANKING_LAB_POSTGRES_PORT="${BANKING_LAB_POSTGRES_PORT:-15512}"
export BANKING_LAB_CORE_BANKING_PORT="${BANKING_LAB_CORE_BANKING_PORT:-18141}"
export BANKING_LAB_KEYCLOAK_PORT="${BANKING_LAB_KEYCLOAK_PORT:-18142}"
export BANKING_LAB_PAYMENT_SERVICE_PORT="${BANKING_LAB_PAYMENT_SERVICE_PORT:-18143}"
export BANKING_LAB_SECURITY_ENABLED=true
export BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false
export BANKING_LAB_DEV_SIMULATOR_TOKEN=false
export BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs
export BANKING_LAB_SECURITY_ISSUER="http://127.0.0.1:${BANKING_LAB_KEYCLOAK_PORT}/realms/banking-lab"
export BANKING_LAB_SECURITY_AUDIENCE=core-banking-api
export BANKING_LAB_PAYMENT_SECURITY_AUDIENCE=payment-service-api
export BANKING_LAB_TRACING_ENABLED=false
export BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false

KEYCLOAK_BASE_URL="http://127.0.0.1:${BANKING_LAB_KEYCLOAK_PORT}"
PAYMENT_BASE_URL="http://127.0.0.1:${BANKING_LAB_PAYMENT_SERVICE_PORT}"

cleanup() {
  docker compose --profile platform down -v --remove-orphans >/dev/null 2>&1 || true
}

wait_for_url() {
  local url="$1"
  local label="$2"
  local attempts="${3:-60}"
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

scripts/run-core-banking-tests.sh :services:core-banking:bootJar :services:payment-service:bootJar
docker compose --profile platform up -d --build postgres keycloak core-banking payment-service

wait_for_url "${KEYCLOAK_BASE_URL}/realms/banking-lab/.well-known/openid-configuration" "Keycloak OIDC discovery"
wait_for_url "${PAYMENT_BASE_URL}/actuator/health" "payment-service health"

TOKEN_RESPONSE="$(
  curl -fsS -X POST "${KEYCLOAK_BASE_URL}/realms/banking-lab/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=client_credentials" \
    --data-urlencode "client_id=payment-service-api" \
    --data-urlencode "client_secret=payment-service-api-secret"
)"
ACCESS_TOKEN="$(
  TOKEN_RESPONSE="${TOKEN_RESPONSE}" node -e '
    const response = JSON.parse(process.env.TOKEN_RESPONSE || "{}");
    if (!response.access_token) {
      console.error("Keycloak token response did not contain access_token");
      process.exit(1);
    }
    process.stdout.write(response.access_token);
  '
)"

ACCESS_TOKEN="${ACCESS_TOKEN}" node -e '
  const token = process.env.ACCESS_TOKEN || "";
  const parts = token.split(".");
  if (parts.length < 2) {
    console.error("expected a JWT access token");
    process.exit(1);
  }
  const payload = JSON.parse(Buffer.from(parts[1], "base64url").toString("utf8"));
  const realmRoles = new Set((((payload.realm_access || {}).roles) || []).map(String));
  const audience = new Set(Array.isArray(payload.aud) ? payload.aud.map(String) : [String(payload.aud || "")]);
  const failures = [];
  if (!realmRoles.has("PAYMENT_SERVICE")) failures.push("missing PAYMENT_SERVICE realm role");
  if (!audience.has("payment-service-api")) failures.push("missing payment-service-api audience");
  if (!audience.has("core-banking-api")) failures.push("missing core-banking-api audience");
  if (payload.iss !== process.env.BANKING_LAB_SECURITY_ISSUER) failures.push(`unexpected issuer ${payload.iss}`);
  if (failures.length > 0) {
    console.error(failures.join("; "));
    process.exit(1);
  }
  console.log(`Keycloak service token ok: sub=${payload.sub}, roles=${[...realmRoles].sort().join(",")}`);
'

DISPATCH_RESPONSE="$(
  curl -fsS -X POST "${PAYMENT_BASE_URL}/api/payments/outbox/ledger-postings/dispatch-next" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    --data '{"requestedBy":"payment-keycloak-service-smoke","reason":"Synthetic live Keycloak PAYMENT_SERVICE service-token smoke","deadLetterThreshold":3}'
)"
DISPATCH_RESPONSE="${DISPATCH_RESPONSE}" node -e '
  const response = JSON.parse(process.env.DISPATCH_RESPONSE || "{}");
  if (response.status !== "NO_PENDING_EVENT" || response.syntheticOnly !== true) {
    console.error(`unexpected payment dispatch response: ${JSON.stringify(response)}`);
    process.exit(1);
  }
  console.log(`Payment dispatch route accepted Keycloak PAYMENT_SERVICE token: ${response.status}`);
'
