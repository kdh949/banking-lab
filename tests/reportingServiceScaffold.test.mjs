import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

test("reporting-service is registered as a target Spring Boot service with PostgreSQL backing", async () => {
  const settings = await readFile("settings.gradle.kts", "utf8");
  const rootPackage = JSON.parse(await readFile("package.json", "utf8"));
  const build = await readFile("services/reporting-service/build.gradle.kts", "utf8");
  const migration = await readFile("services/reporting-service/src/main/resources/db/migration/V001__reporting_service_foundation.sql", "utf8");

  assert.match(settings, /include\(":services:reporting-service"\)/);
  assert.equal(rootPackage.scripts["test:reporting-service:unit"], "scripts/run-core-banking-tests.sh :services:reporting-service:test");
  assert.equal(rootPackage.scripts["test:reporting-service:integration"], "scripts/run-core-banking-tests.sh :services:reporting-service:integrationTest");
  assert.match(build, /org\.springframework\.boot/);
  assert.match(build, /flyway-database-postgresql/);
  assert.match(build, /org\.testcontainers:postgresql/);
  assert.match(migration, /CREATE TABLE report_definitions/);
  assert.match(migration, /CREATE TABLE report_artifacts/);
  assert.match(migration, /CREATE TABLE reporting_access_audit_events/);
  assert.match(migration, /UNIQUE \(requested_by, idempotency_key\)/);
  assert.match(migration, /'AUDIT_SUMMARY'/);
  assert.match(migration, /'EVIDENCE_COVERAGE'/);
});

test("reporting-service API enforces synthetic reporting controls in source and tests", async () => {
  const service = await readFile("services/reporting-service/src/main/kotlin/lab/banking/reporting/domain/ReportingService.kt", "utf8");
  const models = await readFile("services/reporting-service/src/main/kotlin/lab/banking/reporting/domain/ReportingModels.kt", "utf8");
  const filter = await readFile("services/reporting-service/src/main/kotlin/lab/banking/reporting/security/ReportingAuthorizationFilter.kt", "utf8");
  const errorContract = await readFile("services/reporting-service/src/main/kotlin/lab/banking/reporting/api/ReportingApiError.kt", "utf8");
  const integrationTest = await readFile("services/reporting-service/src/integrationTest/kotlin/lab/banking/reporting/ReportingServiceIntegrationTest.kt", "utf8");

  assert.match(service, /Isolation\.SERIALIZABLE/);
  assert.match(service, /requireReason/);
  assert.match(service, /existingArtifact/);
  assert.match(service, /REPORT_GENERATED/);
  assert.match(models, /REPORTING_POLICY_REASON_REQUIRED/);
  assert.match(models, /maskedByDefault/);
  assert.match(filter, /REPORTING_RBAC_ROUTE_POLICY/);
  assert.match(filter, /AUDITOR/);
  assert.match(filter, /COMPLIANCE_MANAGER/);
  assert.match(errorContract, /supporting\/03-reporting-service\.md/);
  assert.match(errorContract, /syntheticOnly: Boolean = true/);
  assert.match(integrationTest, /isUnauthorized/);
  assert.match(integrationTest, /isForbidden/);
  assert.match(integrationTest, /REPORTING_POLICY_REASON_REQUIRED/);
  assert.match(integrationTest, /RPT-IT-001/);
  assert.match(integrationTest, /reporting_access_audit_events/);
});
