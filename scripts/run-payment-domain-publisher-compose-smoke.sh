#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

export COMPOSE_PROJECT_NAME="${BANKING_LAB_LIVE_PAYMENT_DOMAIN_PUBLISHER_COMPOSE_PROJECT:-banking-lab-payment-domain-publisher-smoke}"
export BANKING_LAB_LIVE_PAYMENT_DOMAIN_PUBLISHER_COMPOSE_PROJECT="${COMPOSE_PROJECT_NAME}"
export BANKING_LAB_POSTGRES_PORT="${BANKING_LAB_POSTGRES_PORT:-15510}"
export BANKING_LAB_REDPANDA_PORT="${BANKING_LAB_REDPANDA_PORT:-19110}"
export BANKING_LAB_REDPANDA_ADMIN_PORT="${BANKING_LAB_REDPANDA_ADMIN_PORT:-19610}"
export BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_TOPIC="${BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_TOPIC:-banking.lab.payment-domain-publisher-smoke}"
export BANKING_LAB_LIVE_PAYMENT_DOMAIN_PUBLISHER_DATABASE_URL="${BANKING_LAB_LIVE_PAYMENT_DOMAIN_PUBLISHER_DATABASE_URL:-jdbc:postgresql://127.0.0.1:${BANKING_LAB_POSTGRES_PORT}/banking_lab}"
export BANKING_LAB_LIVE_PAYMENT_DOMAIN_PUBLISHER_BOOTSTRAP_SERVERS="${BANKING_LAB_LIVE_PAYMENT_DOMAIN_PUBLISHER_BOOTSTRAP_SERVERS:-127.0.0.1:${BANKING_LAB_REDPANDA_PORT}}"
export BANKING_LAB_TRACING_ENABLED="${BANKING_LAB_TRACING_ENABLED:-false}"
export BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED="${BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED:-false}"

cleanup() {
  docker compose --profile platform down -v --remove-orphans >/dev/null 2>&1 || true
}

cleanup
trap cleanup EXIT

scripts/run-core-banking-tests.sh :services:payment-service:bootJar
docker compose --profile platform up -d --build postgres redpanda payment-domain-event-publisher
scripts/run-core-banking-tests.sh \
  :services:payment-service:integrationTest \
  --tests 'lab.banking.payment.LivePaymentDomainEventPublisherComposeSmokeIntegrationTest.live payment domain event publisher emits pending domain event from Compose worker' \
  --rerun-tasks
