import { readFile } from "node:fs/promises";
import path from "node:path";
import { writeJsonPreservingTimestamp } from "./k8s-yaml-utils.ts";

type Check = {
  file: string;
  description: string;
  mustContain: string[];
  mustNotContain?: string[];
};

const checks: Check[] = [
  {
    file: "services/core-banking/src/main/resources/application.yml",
    description: "Spring Boot defaults security on and simulator fallback off.",
    mustContain: [
      "enabled: ${BANKING_LAB_SECURITY_ENABLED:true}",
      "simulator-tokens-enabled: ${BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED:false}",
      "dev-simulator-token-enabled: ${BANKING_LAB_DEV_SIMULATOR_TOKEN:false}",
      "trusted-device-enforcement-enabled: ${BANKING_LAB_SECURITY_TRUSTED_DEVICE_ENFORCEMENT_ENABLED:true}",
      "enforcement-enabled: ${BANKING_LAB_SECURITY_STEP_UP_ENFORCEMENT_ENABLED:true}",
      "enforcement-enabled: ${BANKING_LAB_SECURITY_SESSION_ENFORCEMENT_ENABLED:true}"
    ],
    mustNotContain: [
      "enabled: ${BANKING_LAB_SECURITY_ENABLED:false}",
      "simulator-tokens-enabled: ${BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED:true}"
    ]
  },
  {
    file: "docker-compose.yml",
    description: "Docker Compose platform uses secure auth defaults.",
    mustContain: [
      "BANKING_LAB_SECURITY_ENABLED: \"${BANKING_LAB_SECURITY_ENABLED:-true}\"",
      "BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED: \"${BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED:-false}\"",
      "BANKING_LAB_DEV_SIMULATOR_TOKEN: \"${BANKING_LAB_DEV_SIMULATOR_TOKEN:-false}\"",
      "BANKING_LAB_SECURITY_JWKS_URI: \"${BANKING_LAB_SECURITY_JWKS_URI:-http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs}\"",
      "BANKING_LAB_SECURITY_AUDIENCE: \"${BANKING_LAB_SECURITY_AUDIENCE:-core-banking-api}\"",
      "BANKING_LAB_SECURITY_AUDIENCE: \"${BANKING_LAB_REPORTING_SECURITY_AUDIENCE:-reporting-service-api}\"",
      "SPRING_FLYWAY_TABLE: reporting_flyway_schema_history"
    ],
    mustNotContain: [
      "BANKING_LAB_SECURITY_ENABLED: \"${BANKING_LAB_SECURITY_ENABLED:-false}\"",
      "BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED: \"${BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED:-true}\""
    ]
  },
  {
    file: "services/reporting-service/src/main/resources/application.yml",
    description: "Reporting service defaults security on and simulator fallback off.",
    mustContain: [
      "enabled: ${BANKING_LAB_SECURITY_ENABLED:true}",
      "simulator-tokens-enabled: ${BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED:false}",
      "dev-simulator-token-enabled: ${BANKING_LAB_DEV_SIMULATOR_TOKEN:false}",
      "audience: ${BANKING_LAB_SECURITY_AUDIENCE:}"
    ],
    mustNotContain: [
      "enabled: ${BANKING_LAB_SECURITY_ENABLED:false}",
      "simulator-tokens-enabled: ${BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED:true}"
    ]
  },
  {
    file: "infra/k8s/configmap.yaml",
    description: "Kubernetes configmap keeps secure defaults and Keycloak JWKS settings.",
    mustContain: [
      "BANKING_LAB_SECURITY_ENABLED: \"true\"",
      "BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED: \"false\"",
      "BANKING_LAB_DEV_SIMULATOR_TOKEN: \"false\"",
      "BANKING_LAB_SECURITY_JWKS_URI: \"http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs\"",
      "BANKING_LAB_SECURITY_AUDIENCE: \"core-banking-api\""
    ],
    mustNotContain: [
      "BANKING_LAB_SECURITY_ENABLED: \"false\"",
      "BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED: \"true\""
    ]
  },
  {
    file: "infra/helm/banking-lab/templates/configmap.yaml",
    description: "Helm configmap renders secure auth values from chart values.",
    mustContain: [
      "BANKING_LAB_SECURITY_ENABLED: {{ .Values.security.enabled | quote }}",
      "BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED: {{ .Values.security.simulatorTokensEnabled | quote }}",
      "BANKING_LAB_DEV_SIMULATOR_TOKEN: {{ .Values.security.devSimulatorToken | quote }}",
      "BANKING_LAB_SECURITY_JWKS_URI: {{ .Values.security.jwksUri | quote }}",
      "BANKING_LAB_SECURITY_AUDIENCE: {{ .Values.security.audience | quote }}"
    ]
  },
  {
    file: "infra/helm/banking-lab/values.yaml",
    description: "Helm chart values default to secure auth posture.",
    mustContain: [
      "enabled: \"true\"",
      "simulatorTokensEnabled: \"false\"",
      "devSimulatorToken: \"false\"",
      "jwksUri: http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs",
      "audience: core-banking-api"
    ]
  },
  {
    file: "db/migrations/V026__security_auth_hardening_controls.sql",
    description: "Flyway migration adds trusted device and revoked session registries.",
    mustContain: [
      "CREATE TABLE trusted_devices",
      "CREATE TABLE revoked_sessions",
      "CHECK (actor_type IN ('CUSTOMER', 'STAFF'))",
      "CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED'))"
    ]
  }
];

const errors: string[] = [];
const results = [];

for (const check of checks) {
  const source = await readFile(check.file, "utf8");
  const missing = check.mustContain.filter((needle) => !source.includes(needle));
  const forbidden = (check.mustNotContain ?? []).filter((needle) => source.includes(needle));
  if (missing.length > 0) {
    errors.push(`${check.file} missing: ${missing.join(" | ")}`);
  }
  if (forbidden.length > 0) {
    errors.push(`${check.file} contains forbidden insecure default: ${forbidden.join(" | ")}`);
  }
  results.push({
    file: check.file,
    description: check.description,
    status: missing.length === 0 && forbidden.length === 0 ? "pass" : "failed",
    missing,
    forbidden
  });
}

const evidencePath = path.join("docs", "test-evidence", "generated", "security-posture-check.json");
await writeJsonPreservingTimestamp(evidencePath, {
  command: "npm run security:posture-check",
  status: errors.length === 0 ? "pass" : "failed",
  syntheticOnly: true,
  productionClaim: false,
  checks: results,
  errors
});

if (errors.length > 0) {
  throw new Error(`Security posture check failed:\n${errors.join("\n")}`);
}

console.log(`Security posture check passed: ${results.length} controls verified.`);
