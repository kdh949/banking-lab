#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

export COMPOSE_PROJECT_NAME="${BANKING_LAB_REPORTING_KEYCLOAK_COMPOSE_PROJECT:-banking-lab-reporting-keycloak-service-token-smoke}"
export BANKING_LAB_POSTGRES_PORT="${BANKING_LAB_POSTGRES_PORT:-15515}"
export BANKING_LAB_KEYCLOAK_PORT="${BANKING_LAB_KEYCLOAK_PORT:-18156}"
export BANKING_LAB_REPORTING_SERVICE_PORT="${BANKING_LAB_REPORTING_SERVICE_PORT:-18157}"
export BANKING_LAB_SECURITY_ENABLED=true
export BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false
export BANKING_LAB_DEV_SIMULATOR_TOKEN=false
export BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs
export BANKING_LAB_SECURITY_ISSUER="http://127.0.0.1:${BANKING_LAB_KEYCLOAK_PORT}/realms/banking-lab"
export BANKING_LAB_REPORTING_SECURITY_AUDIENCE=reporting-service-api
export BANKING_LAB_TRACING_ENABLED=false
export BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false

KEYCLOAK_BASE_URL="http://127.0.0.1:${BANKING_LAB_KEYCLOAK_PORT}"
REPORTING_BASE_URL="http://127.0.0.1:${BANKING_LAB_REPORTING_SERVICE_PORT}"

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

scripts/run-core-banking-tests.sh :services:reporting-service:bootJar
docker compose --profile platform up -d --build postgres keycloak reporting-service

wait_for_url "${KEYCLOAK_BASE_URL}/realms/banking-lab/.well-known/openid-configuration" "Keycloak OIDC discovery"
wait_for_url "${REPORTING_BASE_URL}/health" "reporting-service health"

TOKEN_RESPONSE="$(
  curl -fsS -X POST "${KEYCLOAK_BASE_URL}/realms/banking-lab/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=client_credentials" \
    --data-urlencode "client_id=reporting-service-api" \
    --data-urlencode "client_secret=reporting-service-api-secret"
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
  if (!realmRoles.has("REPORTING_ANALYST")) failures.push("missing REPORTING_ANALYST realm role");
  if (!audience.has("reporting-service-api")) failures.push("missing reporting-service-api audience");
  if (payload.iss !== process.env.BANKING_LAB_SECURITY_ISSUER) failures.push(`unexpected issuer ${payload.iss}`);
  if (failures.length > 0) {
    console.error(failures.join("; "));
    process.exit(1);
  }
  console.log(`Keycloak reporting service token ok: sub=${payload.sub}, roles=${[...realmRoles].sort().join(",")}`);
'

CATALOG_RESPONSE="$(
  curl -fsS "${REPORTING_BASE_URL}/api/reports/catalog?reason=reporting-keycloak-service-smoke" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
CATALOG_RESPONSE="${CATALOG_RESPONSE}" node -e '
  const response = JSON.parse(process.env.CATALOG_RESPONSE || "{}");
  const reportTypes = new Set((response.items || []).map((item) => item.reportType));
  const failures = [];
  if (response.syntheticOnly !== true) failures.push("catalog response is not syntheticOnly");
  if (!String(response.auditEventId || "").startsWith("RPA-")) failures.push(`unexpected catalog audit id ${response.auditEventId}`);
  for (const type of ["AUDIT_SUMMARY", "OPERATIONS_DAILY", "EVIDENCE_COVERAGE"]) {
    if (!reportTypes.has(type)) failures.push(`missing seeded report type ${type}`);
  }
  if (failures.length > 0) {
    console.error(`${failures.join("; ")}: ${JSON.stringify(response)}`);
    process.exit(1);
  }
  console.log(`Reporting catalog accepted Keycloak REPORTING_ANALYST token: ${[...reportTypes].sort().join(",")}`);
'

GENERATE_RESPONSE="$(
  curl -fsS -X POST "${REPORTING_BASE_URL}/api/reports/artifacts" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    --data '{
      "reportType":"AUDIT_SUMMARY",
      "requestedBy":"service-account-reporting-service-api",
      "requestedByRole":"REPORTING_ANALYST",
      "reason":"reporting-keycloak-service-smoke",
      "idempotencyKey":"RPT-KEYCLOAK-SERVICE-001"
    }'
)"
ARTIFACT_ID="$(
  GENERATE_RESPONSE="${GENERATE_RESPONSE}" node -e '
    const response = JSON.parse(process.env.GENERATE_RESPONSE || "{}");
    const item = response.item || {};
    const failures = [];
    if (response.replayed !== false) failures.push("initial report generation was replayed");
    if (!String(item.artifactId || "").startsWith("RPT-")) failures.push(`unexpected artifact id ${item.artifactId}`);
    if (item.reportType !== "AUDIT_SUMMARY") failures.push(`unexpected reportType ${item.reportType}`);
    if (item.requestedBy !== "service-account-reporting-service-api") failures.push(`unexpected requestedBy ${item.requestedBy}`);
    if (item.requestedRole !== "REPORTING_ANALYST") failures.push(`unexpected requestedRole ${item.requestedRole}`);
    if (item.maskedByDefault !== true) failures.push("artifact is not maskedByDefault");
    if (item.syntheticOnly !== true) failures.push("artifact is not syntheticOnly");
    if (!String(item.artifactPath || "").includes("/audit_summary-")) failures.push(`unexpected artifactPath ${item.artifactPath}`);
    if (failures.length > 0) {
      console.error(`${failures.join("; ")}: ${JSON.stringify(response)}`);
      process.exit(1);
    }
    console.log(`Reporting artifact generated with Keycloak REPORTING_ANALYST token: ${item.artifactId}`);
    process.stdout.write(item.artifactId);
  ' | tail -n 1
)"

ARTIFACTS_RESPONSE="$(
  curl -fsS "${REPORTING_BASE_URL}/api/reports/artifacts?reason=reporting-keycloak-service-smoke&reportType=AUDIT_SUMMARY" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
ARTIFACTS_RESPONSE="${ARTIFACTS_RESPONSE}" ARTIFACT_ID="${ARTIFACT_ID}" node -e '
  const response = JSON.parse(process.env.ARTIFACTS_RESPONSE || "{}");
  const item = (response.items || []).find((candidate) => candidate.artifactId === process.env.ARTIFACT_ID);
  const failures = [];
  if (response.syntheticOnly !== true) failures.push("artifact list response is not syntheticOnly");
  if (!String(response.auditEventId || "").startsWith("RPA-")) failures.push(`unexpected artifact list audit id ${response.auditEventId}`);
  if (!item) failures.push(`generated artifact ${process.env.ARTIFACT_ID} was not listed`);
  if (item && item.maskedByDefault !== true) failures.push("listed artifact is not maskedByDefault");
  if (failures.length > 0) {
    console.error(`${failures.join("; ")}: ${JSON.stringify(response)}`);
    process.exit(1);
  }
  console.log(`Reporting artifact list accepted Keycloak REPORTING_ANALYST token: ${process.env.ARTIFACT_ID}`);
'
