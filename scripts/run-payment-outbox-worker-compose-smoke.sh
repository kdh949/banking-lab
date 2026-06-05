#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

default_payment_service_token() {
  node -e 'const payload={iss:"http://keycloak.local/realms/banking-lab",sub:"payment-compose-outbox-worker",roles:["PAYMENT_SERVICE"],active:true,iat:Math.floor(Date.now()/1000),auth_time:Math.floor(Date.now()/1000)}; process.stdout.write(`lab.${Buffer.from(JSON.stringify(payload)).toString("base64url")}.sig`);'
}

export COMPOSE_PROJECT_NAME="${BANKING_LAB_LIVE_PAYMENT_OUTBOX_WORKER_COMPOSE_PROJECT:-banking-lab-payment-outbox-worker-smoke}"
export BANKING_LAB_LIVE_PAYMENT_OUTBOX_WORKER_COMPOSE_PROJECT="${COMPOSE_PROJECT_NAME}"
export BANKING_LAB_POSTGRES_PORT="${BANKING_LAB_POSTGRES_PORT:-15511}"
export BANKING_LAB_CORE_BANKING_PORT="${BANKING_LAB_CORE_BANKING_PORT:-18111}"
export BANKING_LAB_PAYMENT_SERVICE_PORT="${BANKING_LAB_PAYMENT_SERVICE_PORT:-18118}"
export BANKING_LAB_LIVE_PAYMENT_OUTBOX_WORKER_DATABASE_URL="${BANKING_LAB_LIVE_PAYMENT_OUTBOX_WORKER_DATABASE_URL:-jdbc:postgresql://127.0.0.1:${BANKING_LAB_POSTGRES_PORT}/banking_lab}"
export BANKING_LAB_LIVE_PAYMENT_OUTBOX_WORKER_CORE_URL="${BANKING_LAB_LIVE_PAYMENT_OUTBOX_WORKER_CORE_URL:-http://127.0.0.1:${BANKING_LAB_CORE_BANKING_PORT}}"
export BANKING_LAB_LIVE_PAYMENT_OUTBOX_WORKER_PAYMENT_URL="${BANKING_LAB_LIVE_PAYMENT_OUTBOX_WORKER_PAYMENT_URL:-http://127.0.0.1:${BANKING_LAB_PAYMENT_SERVICE_PORT}}"
export BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED="${BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED:-true}"
export BANKING_LAB_DEV_SIMULATOR_TOKEN="${BANKING_LAB_DEV_SIMULATOR_TOKEN:-true}"
export BANKING_LAB_PAYMENT_CORE_BANKING_SERVICE_TOKEN="${BANKING_LAB_PAYMENT_CORE_BANKING_SERVICE_TOKEN:-$(default_payment_service_token)}"
export BANKING_LAB_TRACING_ENABLED="${BANKING_LAB_TRACING_ENABLED:-false}"
export BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED="${BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED:-false}"

cleanup() {
  docker compose --profile platform down -v --remove-orphans >/dev/null 2>&1 || true
}

cleanup
trap cleanup EXIT

scripts/run-core-banking-tests.sh :services:core-banking:bootJar :services:payment-service:bootJar
docker compose --profile platform up -d --build postgres core-banking payment-service payment-outbox-worker
scripts/run-core-banking-tests.sh \
  :services:payment-service:integrationTest \
  --tests 'lab.banking.payment.LivePaymentOutboxWorkerComposeSmokeIntegrationTest.live payment outbox worker posts ledger settlement through Compose core banking' \
  --rerun-tasks
