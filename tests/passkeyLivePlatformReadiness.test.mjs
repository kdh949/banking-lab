import assert from "node:assert/strict";
import test from "node:test";
import { mkdtemp, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { spawnSync } from "node:child_process";
import { tmpdir } from "node:os";

const script = "scripts/check-passkey-live-platform-readiness.ts";
const keycloakBase = "http://localhost:18127";
const coreBase = "http://127.0.0.1:18126";
const issuer = `${keycloakBase}/realms/banking-lab`;

async function writeTemplates(dir, overrides = {}) {
  const templateIssuer = overrides.templateIssuer ?? issuer;
  const commands = [
    {
      command: [
        "env",
        "COMPOSE_PROJECT_NAME=banking-lab-passkey-manual",
        "BANKING_LAB_SECURITY_ENABLED=true",
        overrides.simulatorMarker ?? "BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false",
        `BANKING_LAB_SECURITY_ISSUER=${templateIssuer}`,
        "BANKING_LAB_SECURITY_AUDIENCE=core-banking-api",
        "BANKING_LAB_SYNTHETIC_SEED_ENABLED=true",
        "docker",
        "compose",
        "--profile",
        "platform",
        "up",
        "-d",
        "--build",
        "postgres",
        "keycloak",
        "core-banking"
      ].join(" ")
    },
    {
      command: `${templateIssuer}/.well-known/openid-configuration`
    },
    {
      command: `${coreBase}/health`
    },
    {
      command: "manual browser sign-in completed with a real platform authenticator"
    },
    {
      command: "npm run passkey:evidence:record"
    }
  ];
  const recordCommand = [
    "env BANKING_LAB_PASSKEY_EVIDENCE_CONFIRMED=true \\",
    "  BANKING_LAB_PASSKEY_AUTHENTICATOR_KIND=platform \\",
    "  BANKING_LAB_PASSKEY_BROWSER_ORIGIN=http://localhost:3002 \\",
    `  BANKING_LAB_PASSKEY_KEYCLOAK_ISSUER=${templateIssuer} \\`,
    "  BANKING_LAB_PASSKEY_SIMULATOR_TOKENS_ENABLED=false \\",
    `  BANKING_LAB_PASSKEY_COMMANDS_FILE=${join(dir, "redacted-commands.template.json")} \\`,
    "  npm run passkey:evidence:record"
  ].join("\n");

  await writeFile(join(dir, "redacted-commands.template.json"), `${JSON.stringify(commands, null, 2)}\n`);
  await writeFile(join(dir, "record-command.template.sh"), recordCommand);
}

async function writeFixtures(dir, overrides = {}) {
  const fixtureIssuer = overrides.issuer ?? issuer;
  const discoveryPath = join(dir, "discovery.json");
  const jwksPath = join(dir, "jwks.json");
  const healthPath = join(dir, "health.json");
  await writeFile(discoveryPath, `${JSON.stringify({
    issuer: fixtureIssuer,
    jwks_uri: `${fixtureIssuer}/protocol/openid-connect/certs`,
    authorization_endpoint: `${fixtureIssuer}/protocol/openid-connect/auth`,
    token_endpoint: `${fixtureIssuer}/protocol/openid-connect/token`,
    response_types_supported: ["code"]
  }, null, 2)}\n`);
  await writeFile(jwksPath, `${JSON.stringify({
    keys: overrides.emptyKeys ? [] : [{ kid: "synthetic-key", kty: "RSA" }]
  }, null, 2)}\n`);
  await writeFile(healthPath, `${JSON.stringify({
    status: "ok",
    syntheticOnly: overrides.syntheticOnly ?? true,
    auditHashChainValid: true,
    migrationTarget: "kotlin-spring-boot"
  }, null, 2)}\n`);
  return { discoveryPath, jwksPath, healthPath };
}

function runReadiness(dir, fixtures) {
  return spawnSync(process.execPath, ["--experimental-strip-types", script], {
    cwd: process.cwd(),
    env: {
      ...process.env,
      BANKING_LAB_PASSKEY_TEMPLATE_DIR: dir,
      BANKING_LAB_PASSKEY_LIVE_KEYCLOAK_BASE_URL: keycloakBase,
      BANKING_LAB_PASSKEY_LIVE_CORE_BASE_URL: coreBase,
      BANKING_LAB_PASSKEY_LIVE_DISCOVERY_FILE: fixtures.discoveryPath,
      BANKING_LAB_PASSKEY_LIVE_JWKS_FILE: fixtures.jwksPath,
      BANKING_LAB_PASSKEY_LIVE_HEALTH_FILE: fixtures.healthPath,
      BANKING_LAB_PASSKEY_LIVE_READINESS_TIMEOUT_MS: "3000"
    },
    encoding: "utf8"
  });
}

test("passkey live platform readiness accepts matching Keycloak and Spring fixtures", async () => {
  const dir = await mkdtemp(join(tmpdir(), "banking-lab-passkey-live-"));
  await writeTemplates(dir);
  const fixtures = await writeFixtures(dir);

  const result = runReadiness(dir, fixtures);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Passkey live platform readiness check: pass/);
  assert.match(result.stdout, /does not prove non-synthetic passkey operations/);
});

test("passkey live platform readiness rejects Keycloak issuer mismatch", async () => {
  const dir = await mkdtemp(join(tmpdir(), "banking-lab-passkey-live-"));
  await writeTemplates(dir);
  const fixtures = await writeFixtures(dir, {
    issuer: "http://localhost:9999/realms/banking-lab"
  });

  const result = runReadiness(dir, fixtures);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /discovery issuer must be/);
});

test("passkey live platform readiness rejects unhealthy synthetic boundary", async () => {
  const dir = await mkdtemp(join(tmpdir(), "banking-lab-passkey-live-"));
  await writeTemplates(dir);
  const fixtures = await writeFixtures(dir, {
    syntheticOnly: false
  });

  const result = runReadiness(dir, fixtures);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /syntheticOnly must be true/);
});

test("passkey live platform readiness rejects template simulator-token drift", async () => {
  const dir = await mkdtemp(join(tmpdir(), "banking-lab-passkey-live-"));
  await writeTemplates(dir, {
    simulatorMarker: "BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true"
  });
  const fixtures = await writeFixtures(dir);

  const result = runReadiness(dir, fixtures);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /SECURITY_SIMULATOR_TOKENS_ENABLED=false/);
});
