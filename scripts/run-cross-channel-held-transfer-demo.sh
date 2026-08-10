#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

MODE="${1:-test}"
export COMPOSE_PROJECT_NAME="${BANKING_LAB_CHANNEL_DEMO_COMPOSE_PROJECT:-banking-lab-channel-demo}"
export BANKING_LAB_POSTGRES_PORT="${BANKING_LAB_POSTGRES_PORT:-15584}"
export BANKING_LAB_CORE_BANKING_PORT="${BANKING_LAB_CORE_BANKING_PORT:-18184}"
export BANKING_LAB_NOTIFICATION_SERVICE_PORT="${BANKING_LAB_NOTIFICATION_SERVICE_PORT:-18089}"
export BANKING_LAB_REDPANDA_PORT="${BANKING_LAB_REDPANDA_PORT:-19092}"
export BANKING_LAB_REDPANDA_ADMIN_PORT="${BANKING_LAB_REDPANDA_ADMIN_PORT:-19644}"
export BANKING_LAB_SECURITY_ENABLED=true
export BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true
export BANKING_LAB_DEV_SIMULATOR_TOKEN=true
export BANKING_LAB_BFF_SIMULATOR_LOGIN_ENABLED=true
export BANKING_LAB_CUSTOMER_AUTH_SYNTHETIC_TOKEN_ISSUER_ENABLED=true
export BANKING_LAB_SECURITY_ISSUER="${BANKING_LAB_SECURITY_ISSUER:-http://keycloak.local/realms/banking-lab,banking-lab-synthetic-customer-auth}"
export BANKING_LAB_SECURITY_AUDIENCE="${BANKING_LAB_SECURITY_AUDIENCE:-core-banking-api}"
export BANKING_LAB_NOTIFICATION_SECURITY_AUDIENCE="${BANKING_LAB_NOTIFICATION_SECURITY_AUDIENCE:-core-banking-api}"
export BANKING_LAB_SYNTHETIC_SEED_ENABLED=true
export BANKING_LAB_TRACING_ENABLED=false
export BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false
export BANKING_LAB_E2E_API_BASE_URL="http://127.0.0.1:${BANKING_LAB_CORE_BANKING_PORT}"
export BANKING_LAB_E2E_NOTIFICATION_API_BASE_URL="http://127.0.0.1:${BANKING_LAB_NOTIFICATION_SERVICE_PORT}"
export BANKING_LAB_CHANNEL_DEMO_STATE_PATH="${BANKING_LAB_CHANNEL_DEMO_STATE_PATH:-/tmp/banking-lab-channel-demo-state.json}"

cleanup() {
  docker compose --profile platform down -v --remove-orphans >/dev/null 2>&1 || true
}

wait_for_url() {
  local url="$1"
  local label="$2"
  local attempts="${3:-90}"
  local attempt
  for attempt in $(seq 1 "${attempts}"); do
    if curl -fsS "${url}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "${label} was not ready: ${url}" >&2
  return 1
}

create_simulator_token() {
  node -e '
    const [subject, ...roles] = process.argv.slice(1);
    const stepUp = roles.includes("BRANCH_MANAGER");
    const now = Math.floor(Date.now() / 1000);
    const payload = {
      iss: "http://keycloak.local/realms/banking-lab",
      sub: subject,
      aud: process.env.BANKING_LAB_SECURITY_AUDIENCE || "core-banking-api",
      roles,
      active: true,
      auth_time: stepUp ? now : undefined,
      iat: stepUp ? now : undefined,
      amr: stepUp ? ["pwd", "webauthn"] : undefined,
      acr: stepUp ? "banking-lab-step-up" : undefined
    };
    process.stdout.write(`Bearer lab.${Buffer.from(JSON.stringify(payload)).toString("base64url")}.sig`);
  ' "$@"
}

build_services() {
  scripts/run-core-banking-tests.sh \
    :services:core-banking:bootJar \
    :services:notification-service:bootJar
}

up_stack() {
  build_services
  docker compose --profile platform up -d --build \
    postgres \
    redpanda \
    core-banking
  wait_for_url "${BANKING_LAB_E2E_API_BASE_URL}/health" "core-banking"
  docker compose --profile platform up -d --build \
    core-banking-outbox-worker \
    notification-service \
    notification-event-consumer
  wait_for_url "${BANKING_LAB_E2E_NOTIFICATION_API_BASE_URL}/actuator/health" "notification-service"
}

seed_check() {
  local seed_token
  seed_token="$(create_simulator_token channel-seed-check BRANCH_STAFF)"
  curl -fsS \
    "${BANKING_LAB_E2E_API_BASE_URL}/api/staff/customers/SYN-CUS-001/detail?reason=Cross-channel%20demo%20seed%20check" \
    -H "Authorization: ${seed_token}" >/dev/null
}

run_test() {
  export BANKING_LAB_E2E_STAFF_MAKER_BEARER_TOKEN
  export BANKING_LAB_E2E_STAFF_CHECKER_BEARER_TOKEN
  export BANKING_LAB_E2E_FDS_MAKER_BEARER_TOKEN
  BANKING_LAB_E2E_STAFF_MAKER_BEARER_TOKEN="$(create_simulator_token channel-account-maker BRANCH_STAFF)"
  BANKING_LAB_E2E_STAFF_CHECKER_BEARER_TOKEN="$(create_simulator_token manager01 BRANCH_MANAGER)"
  BANKING_LAB_E2E_FDS_MAKER_BEARER_TOKEN="$(create_simulator_token risk01 FDS_REVIEWER)"
  npx playwright test --config=playwright.channels.config.ts --project=chromium --workers=1
  docker compose --profile platform exec -T postgres \
    psql -v ON_ERROR_STOP=1 -U banking_lab -d banking_lab \
    < scripts/verify-cross-channel-held-transfer-demo.sql
  node scripts/record-cross-channel-held-transfer-demo.mjs
}

case "${MODE}" in
  reset)
    cleanup
    ;;
  up)
    up_stack
    ;;
  seed)
    up_stack
    seed_check
    ;;
  test)
    cleanup
    if [[ "${BANKING_LAB_CHANNEL_DEMO_KEEP_COMPOSE:-false}" != "true" ]]; then
      trap cleanup EXIT
    fi
    up_stack
    seed_check
    run_test
    ;;
  *)
    echo "usage: $0 {reset|up|seed|test}" >&2
    exit 2
    ;;
esac
