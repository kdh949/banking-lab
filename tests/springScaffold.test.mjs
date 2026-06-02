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
  assert.match(ledgerService, /Isolation\.REPEATABLE_READ/);
  assert.match(ledgerService, /account_balance_projections/);
  assert.match(ledgerService, /outbox_events/);
});
