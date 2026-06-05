#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

export COMPOSE_PROJECT_NAME="${BANKING_LAB_NOTIFICATION_KEYCLOAK_COMPOSE_PROJECT:-banking-lab-notification-keycloak-service-token-smoke}"
export BANKING_LAB_POSTGRES_PORT="${BANKING_LAB_POSTGRES_PORT:-15514}"
export BANKING_LAB_REDPANDA_PORT="${BANKING_LAB_REDPANDA_PORT:-19114}"
export BANKING_LAB_REDPANDA_ADMIN_PORT="${BANKING_LAB_REDPANDA_ADMIN_PORT:-19614}"
export BANKING_LAB_KEYCLOAK_PORT="${BANKING_LAB_KEYCLOAK_PORT:-18154}"
export BANKING_LAB_NOTIFICATION_SERVICE_PORT="${BANKING_LAB_NOTIFICATION_SERVICE_PORT:-18155}"
export BANKING_LAB_SECURITY_ENABLED=true
export BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false
export BANKING_LAB_DEV_SIMULATOR_TOKEN=false
export BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs
export BANKING_LAB_SECURITY_ISSUER="http://127.0.0.1:${BANKING_LAB_KEYCLOAK_PORT}/realms/banking-lab"
export BANKING_LAB_NOTIFICATION_SECURITY_AUDIENCE=notification-service-api
export BANKING_LAB_TRACING_ENABLED=false
export BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false

KEYCLOAK_BASE_URL="http://127.0.0.1:${BANKING_LAB_KEYCLOAK_PORT}"
NOTIFICATION_BASE_URL="http://127.0.0.1:${BANKING_LAB_NOTIFICATION_SERVICE_PORT}"

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

scripts/run-core-banking-tests.sh :services:notification-service:bootJar
docker compose --profile platform up -d --build postgres redpanda keycloak notification-service

wait_for_url "${KEYCLOAK_BASE_URL}/realms/banking-lab/.well-known/openid-configuration" "Keycloak OIDC discovery"
wait_for_url "${NOTIFICATION_BASE_URL}/actuator/health" "notification-service health"

TOKEN_RESPONSE="$(
  curl -fsS -X POST "${KEYCLOAK_BASE_URL}/realms/banking-lab/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=client_credentials" \
    --data-urlencode "client_id=notification-service-api" \
    --data-urlencode "client_secret=notification-service-api-secret"
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
  if (!realmRoles.has("NOTIFICATION_SERVICE")) failures.push("missing NOTIFICATION_SERVICE realm role");
  if (!audience.has("notification-service-api")) failures.push("missing notification-service-api audience");
  if (payload.iss !== process.env.BANKING_LAB_SECURITY_ISSUER) failures.push(`unexpected issuer ${payload.iss}`);
  if (failures.length > 0) {
    console.error(failures.join("; "));
    process.exit(1);
  }
  console.log(`Keycloak notification service token ok: sub=${payload.sub}, roles=${[...realmRoles].sort().join(",")}`);
'

CONSUME_RESPONSE="$(
  curl -fsS -X POST "${NOTIFICATION_BASE_URL}/api/notifications/events" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    --data '{
      "sourceEventId":"OBX-NOTIF-KEYCLOAK-SERVICE-001",
      "eventType":"PaymentLedgerPostingRequested",
      "recipientId":"CUS-NOTIF-KEYCLOAK-001",
      "channel":"SMS",
      "payload":{
        "paymentInstructionId":"PAY-NOTIF-KEYCLOAK-001",
        "amountMinor":25000,
        "currency":"KRW",
        "accountNo":"LAB-KEYCLOAK-0001",
        "phone":"010-1234-5678"
      },
      "requestedBy":"notification-keycloak-service-smoke"
    }'
)"
CONSUME_RESPONSE="${CONSUME_RESPONSE}" node -e '
  const response = JSON.parse(process.env.CONSUME_RESPONSE || "{}");
  const item = Array.isArray(response.items) ? response.items[0] : null;
  const failures = [];
  if (!item) failures.push("response did not contain a delivery item");
  if (item && item.status !== "PENDING") failures.push(`unexpected delivery status ${item.status}`);
  if (item && item.syntheticOnly !== true) failures.push("delivery item is not syntheticOnly");
  if (item && String(item.maskedMessage || "").includes("010-1234-5678")) failures.push("masked message exposed raw phone number");
  if (item && !String(item.deliveryRequestId || "").startsWith("NDL-")) failures.push(`unexpected delivery id ${item.deliveryRequestId}`);
  if (failures.length > 0) {
    console.error(`${failures.join("; ")}: ${JSON.stringify(response)}`);
    process.exit(1);
  }
  console.log(`Notification event route accepted Keycloak NOTIFICATION_SERVICE token: ${item.deliveryRequestId}`);
'
