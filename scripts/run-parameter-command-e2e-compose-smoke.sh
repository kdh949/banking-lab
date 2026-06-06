#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

export COMPOSE_PROJECT_NAME="${BANKING_LAB_PARAMETER_COMMAND_E2E_COMPOSE_PROJECT:-banking-lab-parameter-command-e2e}"
export BANKING_LAB_POSTGRES_PORT="${BANKING_LAB_POSTGRES_PORT:-15541}"
export BANKING_LAB_CORE_BANKING_PORT="${BANKING_LAB_CORE_BANKING_PORT:-18141}"
export BANKING_LAB_SECURITY_ENABLED=true
export BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true
export BANKING_LAB_DEV_SIMULATOR_TOKEN=true
export BANKING_LAB_SECURITY_TRUSTED_DEVICE_ENFORCEMENT_ENABLED=true
export BANKING_LAB_SECURITY_STEP_UP_ENFORCEMENT_ENABLED=true
export BANKING_LAB_SECURITY_SESSION_ENFORCEMENT_ENABLED=true
export BANKING_LAB_SYNTHETIC_SEED_ENABLED=true
export BANKING_LAB_TRACING_ENABLED="${BANKING_LAB_TRACING_ENABLED:-false}"
export BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED="${BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED:-false}"
export BANKING_LAB_E2E_API_BASE_URL="http://127.0.0.1:${BANKING_LAB_CORE_BANKING_PORT}"
export NEXT_PUBLIC_BANKING_SIMULATOR_TOKENS_ENABLED=true

cleanup() {
  if [[ "${BANKING_LAB_PARAMETER_COMMAND_E2E_KEEP_COMPOSE:-false}" == "true" ]]; then
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

wait_for_authenticated_url() {
  local url="$1"
  local label="$2"
  local bearer_token="$3"
  local attempts="${4:-60}"
  local delay_seconds="${5:-2}"
  local attempt
  for attempt in $(seq 1 "${attempts}"); do
    if curl -fsS -H "Authorization: ${bearer_token}" "${url}" >/dev/null 2>&1; then
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
docker compose --profile migration up -d --build postgres core-banking
wait_for_url "${BANKING_LAB_E2E_API_BASE_URL}/health" "core-banking health"

create_simulator_token() {
  node -e '
    const [subject, ...roles] = process.argv.slice(1);
    const payload = {
      iss: "http://keycloak.local/realms/banking-lab",
      sub: subject,
      roles,
      active: true
    };
    process.stdout.write(`Bearer lab.${Buffer.from(JSON.stringify(payload)).toString("base64url")}.sig`);
  ' "$@"
}

wait_for_authenticated_url "${BANKING_LAB_E2E_API_BASE_URL}/api/staff/fds-parameters?reason=Compose%20parameter%20E2E%20preflight" "FDS parameter API" "$(create_simulator_token fds01 FDS_REVIEWER)"
wait_for_authenticated_url "${BANKING_LAB_E2E_API_BASE_URL}/api/ops/parameters/reconciliation?reason=Compose%20parameter%20E2E%20preflight" "OPS parameter API" "$(create_simulator_token ops01 OPS_MANAGER)"
wait_for_authenticated_url "${BANKING_LAB_E2E_API_BASE_URL}/api/staff/audit-parameters?reason=Compose%20parameter%20E2E%20preflight" "AUD parameter API" "$(create_simulator_token auditor01 AUDITOR)"
wait_for_authenticated_url "${BANKING_LAB_E2E_API_BASE_URL}/api/admin/platform/security-parameters?reason=Compose%20parameter%20E2E%20preflight" "ADM-201 parameter API" "$(create_simulator_token security-admin01 COMPLIANCE_MANAGER)"
wait_for_authenticated_url "${BANKING_LAB_E2E_API_BASE_URL}/api/admin/platform/authorization-parameters?reason=Compose%20parameter%20E2E%20preflight" "ADM-301 parameter API" "$(create_simulator_token security-admin01 COMPLIANCE_MANAGER PASSKEY_RECOVERY_ADMIN)"

npm run test:e2e -- \
  apps/fds-aml-console/e2e/fds-aml-console-parity.spec.ts \
  apps/ops-console/e2e/ops-console-parity.spec.ts \
  apps/audit-console/e2e/audit-console-parity.spec.ts \
  apps/admin-console/e2e/admin-console-parity.spec.ts \
  --grep "parameter change" \
  --workers=1
