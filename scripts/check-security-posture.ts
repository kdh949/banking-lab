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
      "BANKING_LAB_SECURITY_AUDIENCE: \"${BANKING_LAB_NOTIFICATION_SECURITY_AUDIENCE:-notification-service-api}\"",
      "BANKING_LAB_SECURITY_AUDIENCE: \"${BANKING_LAB_PAYMENT_SECURITY_AUDIENCE:-payment-service-api}\"",
      "SPRING_FLYWAY_TABLE: reporting_flyway_schema_history",
      "reporting-domain-event-publisher:",
      "BANKING_LAB_REPORTING_DOMAIN_EVENT_PUBLISHER_ENABLED: \"true\"",
      "BANKING_LAB_REPORTING_DOMAIN_EVENT_PUBLISHER_BOOTSTRAP_SERVERS: redpanda:9092",
      "SPRING_FLYWAY_TABLE: notification_flyway_schema_history",
      "SPRING_FLYWAY_TABLE: payment_flyway_schema_history"
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
    file: "services/payment-service/src/main/resources/application.yml",
    description: "Payment service defaults to synthetic-only payment network and secure auth.",
    mustContain: [
      "real-payment-network-enabled: false",
      "enabled: ${BANKING_LAB_SECURITY_ENABLED:true}",
      "simulator-tokens-enabled: ${BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED:false}",
      "dev-simulator-token-enabled: ${BANKING_LAB_DEV_SIMULATOR_TOKEN:false}",
      "audience: ${BANKING_LAB_SECURITY_AUDIENCE:}"
    ],
    mustNotContain: [
      "real-payment-network-enabled: true",
      "enabled: ${BANKING_LAB_SECURITY_ENABLED:false}",
      "simulator-tokens-enabled: ${BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED:true}"
    ]
  },
  {
    file: "infra/k8s/reporting-service-deployment.yaml",
    description: "Reporting Kubernetes deployment uses secure route audience and separate Flyway state.",
    mustContain: [
      "value: \"reporting-service-api\"",
      "value: \"reporting_flyway_schema_history\"",
      "BANKING_LAB_REPORTING_DATABASE_URL",
      "BANKING_LAB_REPORTING_DOMAIN_EVENT_PUBLISHER_ENABLED",
      "value: \"false\""
    ],
    mustNotContain: [
      "BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED: \"true\""
    ]
  },
  {
    file: "infra/k8s/reporting-domain-event-publisher-deployment.yaml",
    description: "Reporting Kubernetes domain event publisher emits synthetic reporting events to Redpanda only.",
    mustContain: [
      "value: \"reporting-service-api\"",
      "value: \"reporting_flyway_schema_history\"",
      "BANKING_LAB_REPORTING_DOMAIN_EVENT_PUBLISHER_ENABLED",
      "value: \"true\"",
      "BANKING_LAB_REPORTING_DOMAIN_EVENT_PUBLISHER_BOOTSTRAP_SERVERS",
      "value: \"redpanda:9092\"",
      "ReportRetentionSweepCompleted"
    ],
    mustNotContain: [
      "BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED: \"true\""
    ]
  },
  {
    file: "infra/helm/banking-lab/templates/reporting-service-deployment.yaml",
    description: "Reporting Helm deployment renders secure route audience and separate Flyway state.",
    mustContain: [
      "value: {{ .Values.reportingService.securityAudience | quote }}",
      "value: \"reporting_flyway_schema_history\"",
      "BANKING_LAB_REPORTING_DATABASE_URL",
      "BANKING_LAB_REPORTING_DOMAIN_EVENT_PUBLISHER_ENABLED",
      "value: \"false\""
    ]
  },
  {
    file: "infra/helm/banking-lab/templates/reporting-domain-event-publisher-deployment.yaml",
    description: "Reporting Helm domain event publisher renders synthetic Redpanda publisher controls.",
    mustContain: [
      "value: {{ .Values.reportingService.securityAudience | quote }}",
      "value: \"reporting_flyway_schema_history\"",
      "BANKING_LAB_REPORTING_DOMAIN_EVENT_PUBLISHER_ENABLED",
      "value: \"true\"",
      "value: {{ .Values.reportingService.domainEventPublisherBootstrapServers | quote }}",
      "BANKING_LAB_REPORTING_DOMAIN_EVENT_PUBLISHER_EVENT_TYPES",
      "value: {{ .Values.reportingService.domainEventPublisherEventTypes | quote }}"
    ]
  },
  {
    file: "infra/k8s/payment-service-deployment.yaml",
    description: "Payment Kubernetes API deployment keeps service audience, disabled worker mode, and separate Flyway state.",
    mustContain: [
      "value: \"payment-service-api\"",
      "value: \"payment_flyway_schema_history\"",
      "BANKING_LAB_PAYMENT_OUTBOX_WORKER_ENABLED",
      "value: \"false\"",
      "BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_ENABLED",
      "http://core-banking-service:8081",
      "BANKING_LAB_PAYMENT_CORE_BANKING_TOKEN_URL",
      "BANKING_LAB_PAYMENT_CORE_BANKING_CLIENT_ID",
      "PAYMENT_CORE_BANKING_CLIENT_SECRET"
    ],
    mustNotContain: [
      "BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED: \"true\""
    ]
  },
  {
    file: "infra/k8s/payment-outbox-worker-deployment.yaml",
    description: "Payment Kubernetes worker deployment enables only the synthetic outbox dispatch path.",
    mustContain: [
      "value: \"payment-service-api\"",
      "value: \"payment_flyway_schema_history\"",
      "BANKING_LAB_PAYMENT_OUTBOX_WORKER_ENABLED",
      "value: \"true\"",
      "BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_ENABLED",
      "value: \"false\"",
      "payment-k8s-outbox-worker",
      "BANKING_LAB_PAYMENT_CORE_BANKING_TOKEN_URL",
      "BANKING_LAB_PAYMENT_CORE_BANKING_CLIENT_ID",
      "PAYMENT_CORE_BANKING_CLIENT_SECRET"
    ],
    mustNotContain: [
      "BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED: \"true\""
    ]
  },
  {
    file: "infra/k8s/payment-domain-event-publisher-deployment.yaml",
    description: "Payment Kubernetes domain event publisher emits synthetic payment events to Redpanda only.",
    mustContain: [
      "value: \"payment-service-api\"",
      "value: \"payment_flyway_schema_history\"",
      "BANKING_LAB_PAYMENT_OUTBOX_WORKER_ENABLED",
      "value: \"false\"",
      "BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_ENABLED",
      "value: \"true\"",
      "BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_BOOTSTRAP_SERVERS",
      "value: \"redpanda:9092\"",
      "PaymentInstructionCanceled"
    ],
    mustNotContain: [
      "BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED: \"true\""
    ]
  },
  {
    file: "infra/helm/banking-lab/templates/payment-service-deployment.yaml",
    description: "Payment Helm API deployment renders service audience, disabled worker mode, and separate Flyway state.",
    mustContain: [
      "value: {{ .Values.paymentService.securityAudience | quote }}",
      "value: \"payment_flyway_schema_history\"",
      "BANKING_LAB_PAYMENT_OUTBOX_WORKER_ENABLED",
      "value: \"false\"",
      "BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_ENABLED",
      "BANKING_LAB_PAYMENT_CORE_BANKING_TOKEN_URL",
      "BANKING_LAB_PAYMENT_CORE_BANKING_CLIENT_ID",
      "PAYMENT_CORE_BANKING_CLIENT_SECRET"
    ]
  },
  {
    file: "infra/helm/banking-lab/templates/payment-outbox-worker-deployment.yaml",
    description: "Payment Helm worker deployment renders synthetic outbox dispatch controls.",
    mustContain: [
      "value: {{ .Values.paymentService.securityAudience | quote }}",
      "value: \"payment_flyway_schema_history\"",
      "BANKING_LAB_PAYMENT_OUTBOX_WORKER_ENABLED",
      "value: \"true\"",
      "BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_ENABLED",
      "value: \"false\"",
      "payment-helm-outbox-worker",
      "BANKING_LAB_PAYMENT_CORE_BANKING_TOKEN_URL",
      "BANKING_LAB_PAYMENT_CORE_BANKING_CLIENT_ID",
      "PAYMENT_CORE_BANKING_CLIENT_SECRET"
    ]
  },
  {
    file: "infra/helm/banking-lab/templates/payment-domain-event-publisher-deployment.yaml",
    description: "Payment Helm domain event publisher renders synthetic Redpanda publisher controls.",
    mustContain: [
      "value: {{ .Values.paymentService.securityAudience | quote }}",
      "value: \"payment_flyway_schema_history\"",
      "BANKING_LAB_PAYMENT_OUTBOX_WORKER_ENABLED",
      "value: \"false\"",
      "BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_ENABLED",
      "value: \"true\"",
      "value: {{ .Values.paymentService.domainEventPublisherBootstrapServers | quote }}",
      "BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_EVENT_TYPES",
      "value: {{ .Values.paymentService.domainEventPublisherEventTypes | quote }}"
    ]
  },
  {
    file: "infra/k8s/notification-service-deployment.yaml",
    description: "Notification Kubernetes API deployment keeps synthetic provider and service audience controls.",
    mustContain: [
      "value: \"notification-service-api\"",
      "value: \"notification_flyway_schema_history\"",
      "BANKING_LAB_NOTIFICATION_SERVICE_REAL_PROVIDER_ENABLED",
      "value: \"false\"",
      "BANKING_LAB_NOTIFICATION_DATABASE_URL"
    ],
    mustNotContain: [
      "BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED: \"true\"",
      "BANKING_LAB_NOTIFICATION_SERVICE_REAL_PROVIDER_ENABLED: \"true\""
    ]
  },
  {
    file: "infra/k8s/notification-event-consumer-deployment.yaml",
    description: "Notification Kubernetes worker deployment consumes synthetic Redpanda events only.",
    mustContain: [
      "value: \"notification-service-api\"",
      "value: \"notification_flyway_schema_history\"",
      "BANKING_LAB_NOTIFICATION_EVENT_CONSUMER_ENABLED",
      "value: \"true\"",
      "value: \"redpanda:9092\"",
      "BANKING_LAB_NOTIFICATION_SERVICE_REAL_PROVIDER_ENABLED"
    ],
    mustNotContain: [
      "BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED: \"true\"",
      "BANKING_LAB_NOTIFICATION_SERVICE_REAL_PROVIDER_ENABLED: \"true\""
    ]
  },
  {
    file: "infra/helm/banking-lab/templates/notification-service-deployment.yaml",
    description: "Notification Helm API deployment renders synthetic provider and service audience controls.",
    mustContain: [
      "value: {{ .Values.notificationService.securityAudience | quote }}",
      "value: \"notification_flyway_schema_history\"",
      "BANKING_LAB_NOTIFICATION_SERVICE_REAL_PROVIDER_ENABLED",
      "value: \"false\"",
      "BANKING_LAB_NOTIFICATION_DATABASE_URL"
    ]
  },
  {
    file: "infra/helm/banking-lab/templates/notification-event-consumer-deployment.yaml",
    description: "Notification Helm worker deployment renders synthetic Redpanda consumer controls.",
    mustContain: [
      "value: {{ .Values.notificationService.securityAudience | quote }}",
      "value: \"notification_flyway_schema_history\"",
      "BANKING_LAB_NOTIFICATION_EVENT_CONSUMER_ENABLED",
      "value: \"true\"",
      "value: {{ .Values.notificationService.eventConsumerBootstrapServers | quote }}",
      "BANKING_LAB_NOTIFICATION_SERVICE_REAL_PROVIDER_ENABLED"
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
      "audience: core-banking-api",
      "domainEventPublisherClientId: reporting-helm-domain-event-publisher"
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
