#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

export COMPOSE_PROJECT_NAME="${BANKING_LAB_NOTIFICATION_PROVIDER_DEAD_LETTER_COMPOSE_PROJECT:-banking-lab-notification-provider-dead-letter-smoke}"
export BANKING_LAB_POSTGRES_PORT="${BANKING_LAB_POSTGRES_PORT:-15532}"
export BANKING_LAB_REDPANDA_PORT="${BANKING_LAB_REDPANDA_PORT:-19132}"
export BANKING_LAB_REDPANDA_ADMIN_PORT="${BANKING_LAB_REDPANDA_ADMIN_PORT:-19632}"
export BANKING_LAB_KEYCLOAK_PORT="${BANKING_LAB_KEYCLOAK_PORT:-18158}"
export BANKING_LAB_NOTIFICATION_SERVICE_PORT="${BANKING_LAB_NOTIFICATION_SERVICE_PORT:-18159}"
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
SMOKE_SUFFIX="$(date +%s)-$$"
SOURCE_EVENT_ID="OBX-NOTIF-PROVIDER-DL-${SMOKE_SUFFIX}"
RECIPIENT_ID="CUS-NOTIF-PROVIDER-DL-${SMOKE_SUFFIX}"
RAW_PHONE="010-5555-9090"

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

assert_json() {
  local json="$1"
  local script="$2"
  JSON_INPUT="${json}" node -e "${script}"
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
  console.log(`Keycloak notification provider retry token ok: sub=${payload.sub}`);
'

CONSUME_RESPONSE="$(
  curl -fsS -X POST "${NOTIFICATION_BASE_URL}/api/notifications/events" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    --data "{
      \"sourceEventId\":\"${SOURCE_EVENT_ID}\",
      \"eventType\":\"PaymentLedgerPostingRequested\",
      \"recipientId\":\"${RECIPIENT_ID}\",
      \"channel\":\"SMS\",
      \"payload\":{
        \"paymentInstructionId\":\"PAY-NOTIF-PROVIDER-DL-${SMOKE_SUFFIX}\",
        \"amountMinor\":33000,
        \"currency\":\"KRW\",
        \"accountNo\":\"LAB-PROVIDER-DL-9090\",
        \"phone\":\"${RAW_PHONE}\"
      },
      \"requestedBy\":\"notification-provider-dead-letter-smoke\"
    }"
)"
DELIVERY_REQUEST_ID="$(
  CONSUME_RESPONSE="${CONSUME_RESPONSE}" RAW_PHONE="${RAW_PHONE}" node -e '
    const response = JSON.parse(process.env.CONSUME_RESPONSE || "{}");
    const item = Array.isArray(response.items) ? response.items[0] : null;
    const failures = [];
    if (!item) failures.push("response did not contain a delivery item");
    if (item && item.status !== "PENDING") failures.push(`unexpected delivery status ${item.status}`);
    if (item && item.syntheticOnly !== true) failures.push("delivery item is not syntheticOnly");
    if (item && String(item.maskedMessage || "").includes(process.env.RAW_PHONE || "")) failures.push("masked message exposed raw phone number");
    if (item && !String(item.deliveryRequestId || "").startsWith("NDL-")) failures.push(`unexpected delivery id ${item.deliveryRequestId}`);
    if (failures.length > 0) {
      console.error(`${failures.join("; ")}: ${JSON.stringify(response)}`);
      process.exit(1);
    }
    process.stdout.write(item.deliveryRequestId);
  '
)"
echo "Notification delivery created for provider retry drill: ${DELIVERY_REQUEST_ID}"

FIRST_FAILURE_RESPONSE="$(
  curl -fsS -X POST "${NOTIFICATION_BASE_URL}/api/notifications/deliveries/${DELIVERY_REQUEST_ID}/failures" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    --data '{
      "errorMessage":"Synthetic provider transient failure for retry",
      "requestedBy":"notification-provider-dead-letter-smoke",
      "reason":"Synthetic provider retry drill",
      "deadLetterThreshold":2
    }'
)"
assert_json "${FIRST_FAILURE_RESPONSE}" '
  const item = JSON.parse(process.env.JSON_INPUT || "{}");
  if (item.status !== "FAILED" || item.syntheticOnly !== true) {
    console.error(`expected first failure to leave FAILED synthetic delivery: ${JSON.stringify(item)}`);
    process.exit(1);
  }
'

SECOND_FAILURE_RESPONSE="$(
  curl -fsS -X POST "${NOTIFICATION_BASE_URL}/api/notifications/deliveries/${DELIVERY_REQUEST_ID}/failures" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    --data '{
      "errorMessage":"Synthetic provider permanent failure reaches dead letter",
      "requestedBy":"notification-provider-dead-letter-smoke",
      "reason":"Synthetic provider dead-letter drill",
      "deadLetterThreshold":2
    }'
)"
assert_json "${SECOND_FAILURE_RESPONSE}" '
  const item = JSON.parse(process.env.JSON_INPUT || "{}");
  const failures = [];
  if (item.status !== "DEAD_LETTER") failures.push(`unexpected final status ${item.status}`);
  if (item.syntheticOnly !== true) failures.push("dead-letter delivery is not syntheticOnly");
  if (String(item.maskedMessage || "").includes("010-5555-9090")) failures.push("dead-letter response exposed raw phone number");
  if (failures.length > 0) {
    console.error(`${failures.join("; ")}: ${JSON.stringify(item)}`);
    process.exit(1);
  }
'

DEAD_LETTER_ROWS="$(
  docker compose exec -T postgres psql -U banking_lab -d banking_lab -tA \
    -c "SELECT count(*) FROM notification_dead_letters WHERE delivery_request_id = '${DELIVERY_REQUEST_ID}';" |
    tr -d '[:space:]'
)"
ATTEMPT_ROWS="$(
  docker compose exec -T postgres psql -U banking_lab -d banking_lab -tA \
    -c "SELECT count(*) FROM notification_delivery_attempts WHERE delivery_request_id = '${DELIVERY_REQUEST_ID}' AND status IN ('FAILED', 'DEAD_LETTER');" |
    tr -d '[:space:]'
)"
if [[ "${DEAD_LETTER_ROWS}" != "1" || "${ATTEMPT_ROWS}" != "2" ]]; then
  echo "expected one dead-letter row and two failure/dead-letter attempts, got deadLetters=${DEAD_LETTER_ROWS}, attempts=${ATTEMPT_ROWS}" >&2
  exit 1
fi

DELIVERED_REJECT_FILE="$(mktemp)"
DELIVERED_STATUS="$(
  curl -sS -o "${DELIVERED_REJECT_FILE}" -w "%{http_code}" \
    -X POST "${NOTIFICATION_BASE_URL}/api/notifications/deliveries/${DELIVERY_REQUEST_ID}/delivered" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    --data '{
      "requestedBy":"notification-provider-dead-letter-smoke",
      "reason":"Synthetic delivered command should be rejected after dead letter"
    }'
)"
DELIVERED_REJECT_RESPONSE="$(cat "${DELIVERED_REJECT_FILE}")"
rm -f "${DELIVERED_REJECT_FILE}"
if [[ "${DELIVERED_STATUS}" != "409" ]]; then
  echo "expected delivered command on dead-letter delivery to return 409, got ${DELIVERED_STATUS}: ${DELIVERED_REJECT_RESPONSE}" >&2
  exit 1
fi
assert_json "${DELIVERED_REJECT_RESPONSE}" '
  const envelope = JSON.parse(process.env.JSON_INPUT || "{}");
  const error = envelope.error || {};
  if (error.code !== "NOTIFICATION_STATE_TRANSITION_REJECTED" || error.syntheticOnly !== true) {
    console.error(`expected structured dead-letter transition rejection: ${JSON.stringify(envelope)}`);
    process.exit(1);
  }
'

HISTORY_RESPONSE="$(
  curl -fsS -G "${NOTIFICATION_BASE_URL}/api/notifications/deliveries" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    --data-urlencode "recipientId=${RECIPIENT_ID}" \
    --data-urlencode "status=DEAD_LETTER" \
    --data-urlencode "requestedBy=notification-provider-dead-letter-smoke" \
    --data-urlencode "reason=Synthetic provider dead-letter history verification"
)"
assert_json "${HISTORY_RESPONSE}" '
  const items = JSON.parse(process.env.JSON_INPUT || "[]");
  const item = Array.isArray(items) ? items[0] : null;
  const failures = [];
  if (!item) failures.push("dead-letter history did not return the delivery");
  if (item && item.status !== "DEAD_LETTER") failures.push(`history status was ${item.status}`);
  if (item && item.syntheticOnly !== true) failures.push("history item is not syntheticOnly");
  if (item && String(item.maskedMessage || "").includes("010-5555-9090")) failures.push("history exposed raw phone number");
  if (failures.length > 0) {
    console.error(`${failures.join("; ")}: ${JSON.stringify(items)}`);
    process.exit(1);
  }
'

echo "Notification provider retry/dead-letter Compose smoke passed: delivery=${DELIVERY_REQUEST_ID}, deadLetters=${DEAD_LETTER_ROWS}, failureAttempts=${ATTEMPT_ROWS}"
