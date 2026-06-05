import assert from "node:assert/strict";
import test from "node:test";
import { access, readFile } from "node:fs/promises";

async function exists(filePath) {
  try {
    await access(filePath);
    return true;
  } catch {
    return false;
  }
}

test("Gradle settings include the Kotlin Spring core-banking service", async () => {
  const settings = await readFile("settings.gradle.kts", "utf8");
  const rootBuild = await readFile("build.gradle.kts", "utf8");
  const serviceBuild = await readFile("services/core-banking/build.gradle.kts", "utf8");
  const wrapper = await readFile("gradle/wrapper/gradle-wrapper.properties", "utf8");

  assert.match(settings, /include\(":services:core-banking"\)/);
  assert.match(rootBuild, /org\.springframework\.boot/);
  assert.match(rootBuild, /3\.5\.14/);
  assert.match(rootBuild, /2\.3\.21/);
  assert.match(serviceBuild, /kotlin\("jvm"\)/);
  assert.match(serviceBuild, /integrationTest/);
  assert.match(serviceBuild, /spring-boot-starter-web/);
  assert.match(serviceBuild, /flyway-database-postgresql/);
  assert.match(serviceBuild, /spring-boot-testcontainers/);
  assert.match(wrapper, /gradle-8\.14\.3-bin\.zip/);
  assert.equal(await exists("gradlew"), true);
  assert.equal(await exists("gradle/wrapper/gradle-wrapper.jar"), true);
});

test("Spring Boot health scaffold preserves synthetic and Node reference signals", async () => {
  const app = await readFile("services/core-banking/src/main/kotlin/lab/banking/core/CoreBankingApplication.kt", "utf8");
  const controller = await readFile("services/core-banking/src/main/kotlin/lab/banking/core/api/HealthController.kt", "utf8");
  const response = await readFile("services/core-banking/src/main/kotlin/lab/banking/core/api/HealthResponse.kt", "utf8");

  assert.match(app, /@SpringBootApplication/);
  assert.match(controller, /@GetMapping\("\/health"\)/);
  assert.match(response, /syntheticOnly: Boolean/);
  assert.match(response, /nodeReferenceRuntimeRetained: Boolean/);
  assert.match(response, /migrationTarget: String/);
  assert.equal(await exists("runtime/labApp.mjs"), true);
  assert.equal(await exists("runtime/server.mjs"), true);
});

test("Spring Boot scaffold declares structured errors and PostgreSQL Flyway migration path", async () => {
  const errorDto = await readFile("services/core-banking/src/main/kotlin/lab/banking/core/api/StructuredApiError.kt", "utf8");
  const errorHandler = await readFile("services/core-banking/src/main/kotlin/lab/banking/core/api/StructuredApiErrorHandler.kt", "utf8");
  const application = await readFile("services/core-banking/src/main/resources/application.yml", "utf8");
  const compose = await readFile("docker-compose.yml", "utf8");
  const ledgerController = await readFile("services/core-banking/src/main/kotlin/lab/banking/core/ledger/api/LedgerController.kt", "utf8");
  const ledgerService = await readFile("services/core-banking/src/main/kotlin/lab/banking/core/ledger/application/LedgerCommandService.kt", "utf8");

  for (const field of ["contractVersion", "code", "message", "statusCode", "domain", "invariant", "policy", "cause", "fix", "requestId", "docs", "syntheticOnly"]) {
    assert.match(errorDto, new RegExp(field));
  }
  assert.match(errorHandler, /x-request-id/);
  assert.match(errorHandler, /BankingLabDomainException/);
  assert.match(application, /jdbc:postgresql:\/\/localhost:5432\/banking_lab/);
  assert.match(application, /filesystem:db\/migrations/);
  assert.match(compose, /postgres:16-alpine/);
  for (const service of ["redis:7-alpine", "redpandadata/redpanda", "quay.io/keycloak/keycloak", "temporalio/auto-setup", "prom/prometheus", "grafana/grafana", "grafana/loki", "grafana/tempo"]) {
    assert.match(compose, new RegExp(service.replaceAll("/", "\\/")));
  }
  assert.match(compose, /profiles:/);
  for (const route of ["/ledger/deposits", "/ledger/withdrawals", "/ledger/transfers", "/ledger/reversals", "/ledger/adjustments", "/ops/daily-closings"]) {
    assert.match(ledgerController, new RegExp(route.replaceAll("/", "\\/")));
  }
  assert.match(ledgerService, /Isolation\.SERIALIZABLE/);
  assert.match(ledgerService, /Isolation\.REPEATABLE_READ|TransactionDefinition\.ISOLATION_REPEATABLE_READ/);
  assert.match(ledgerService, /account_balance_projections/);
  assert.match(ledgerService, /outbox_events/);
});

test("notification-service platform profile includes API and Redpanda consumer worker", async () => {
  const settings = await readFile("settings.gradle.kts", "utf8");
  const serviceBuild = await readFile("services/notification-service/build.gradle.kts", "utf8");
  const dockerfile = await readFile("infra/docker-compose/notification-service.Dockerfile", "utf8");
  const compose = await readFile("docker-compose.yml", "utf8");
  const prometheus = await readFile("infra/observability/prometheus/prometheus.yml", "utf8");

  assert.match(settings, /include\(":services:notification-service"\)/);
  assert.match(serviceBuild, /org\.apache\.kafka:kafka-clients/);
  assert.match(serviceBuild, /org\.testcontainers:redpanda/);
  assert.match(dockerfile, /notification-service-\*-migration\.jar/);
  assert.match(compose, /notification-service:/);
  assert.match(compose, /notification-event-consumer:/);
  assert.match(compose, /SPRING_FLYWAY_TABLE: notification_flyway_schema_history/);
  assert.match(
    compose,
    /notification-service:[\s\S]*BANKING_LAB_NOTIFICATION_EVENT_CONSUMER_ENABLED: "false"/
  );
  assert.match(
    compose,
    /notification-event-consumer:[\s\S]*BANKING_LAB_NOTIFICATION_EVENT_CONSUMER_ENABLED: "true"/
  );
  assert.match(compose, /BANKING_LAB_NOTIFICATION_SERVICE_REAL_PROVIDER_ENABLED: "false"/);
  assert.match(compose, /BANKING_LAB_NOTIFICATION_EVENT_CONSUMER_BOOTSTRAP_SERVERS: redpanda:9092/);
  assert.match(prometheus, /job_name: notification-service/);
  assert.match(prometheus, /notification-service:8089/);
  assert.match(prometheus, /notification-event-consumer:8089/);
});

test("payment-service platform profile includes API, outbox worker, and domain event publisher", async () => {
  const settings = await readFile("settings.gradle.kts", "utf8");
  const application = await readFile("services/payment-service/src/main/resources/application.yml", "utf8");
  const dockerfile = await readFile("infra/docker-compose/payment-service.Dockerfile", "utf8");
  const compose = await readFile("docker-compose.yml", "utf8");
  const prometheus = await readFile("infra/observability/prometheus/prometheus.yml", "utf8");

  assert.match(settings, /include\(":services:payment-service"\)/);
  assert.match(application, /real-payment-network-enabled: false/);
  assert.match(application, /BANKING_LAB_PAYMENT_OUTBOX_WORKER_ENABLED:false/);
  assert.match(application, /BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_ENABLED:false/);
  assert.match(dockerfile, /payment-service-\*-migration\.jar/);
  assert.match(compose, /payment-service:/);
  assert.match(compose, /payment-outbox-worker:/);
  assert.match(compose, /payment-domain-event-publisher:/);
  assert.match(compose, /SPRING_FLYWAY_TABLE: payment_flyway_schema_history/);
  assert.match(compose, /BANKING_LAB_CORE_BANKING_BASE_URL: http:\/\/core-banking:8081/);
  assert.match(
    compose,
    /payment-service:[\s\S]*BANKING_LAB_PAYMENT_OUTBOX_WORKER_ENABLED: "false"/
  );
  assert.match(
    compose,
    /payment-service:[\s\S]*BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_ENABLED: "false"/
  );
  assert.match(
    compose,
    /payment-outbox-worker:[\s\S]*BANKING_LAB_PAYMENT_OUTBOX_WORKER_ENABLED: "true"/
  );
  assert.match(
    compose,
    /payment-outbox-worker:[\s\S]*BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_ENABLED: "false"/
  );
  assert.match(
    compose,
    /payment-domain-event-publisher:[\s\S]*BANKING_LAB_PAYMENT_OUTBOX_WORKER_ENABLED: "false"/
  );
  assert.match(
    compose,
    /payment-domain-event-publisher:[\s\S]*BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_ENABLED: "true"/
  );
  assert.match(compose, /BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_BOOTSTRAP_SERVERS: redpanda:9092/);
  assert.match(compose, /BANKING_LAB_PAYMENT_CORE_BANKING_SERVICE_TOKEN/);
  assert.match(prometheus, /job_name: payment-service/);
  assert.match(prometheus, /payment-service:8088/);
  assert.match(prometheus, /payment-outbox-worker:8088/);
  assert.match(prometheus, /payment-domain-event-publisher:8088/);
});

test("payment-service cancellation outbox event has checked-in AsyncAPI contract coverage", async () => {
  const asyncapi = await readFile("contracts/asyncapi/banking-lab-events.yaml", "utf8");
  const schema = JSON.parse(await readFile("contracts/events/payment-instruction-canceled.schema.json", "utf8"));
  const service = await readFile("services/payment-service/src/main/kotlin/lab/banking/payment/domain/PaymentInstructionService.kt", "utf8");
  const authorizationTest = await readFile("services/payment-service/src/integrationTest/kotlin/lab/banking/payment/PaymentAuthorizationIntegrationTest.kt", "utf8");

  assert.match(service, /eventType = "PaymentInstructionCanceled"/);
  assert.match(service, /"cancellationRequestId" to cancellationRequestId/);
  assert.match(service, /"makerCheckerApproved" to true/);
  assert.match(authorizationTest, /PaymentInstructionCanceled/);
  assert.match(asyncapi, /payment\.instruction\.canceled/);
  assert.match(asyncapi, /PaymentInstructionCanceled/);
  assert.match(asyncapi, /payment-instruction-canceled\.schema\.json/);
  assert.equal(schema.title, "PaymentInstructionCanceled");
  assert.equal(schema.properties.syntheticOnly.const, true);
  assert.equal(schema.properties.directLedgerWrite.const, false);
  assert.equal(schema.properties.cancellationRequestId.pattern, "^PCR-");
  assert.equal(schema.properties.realPaymentNetworkUsed.const, false);
});
