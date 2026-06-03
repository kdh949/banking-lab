import { readFile } from "node:fs/promises";
import path from "node:path";

type CommandEvidenceTemplate = {
  command?: unknown;
};

type JsonObject = Record<string, unknown>;

const templateDir = process.env.BANKING_LAB_PASSKEY_TEMPLATE_DIR ?? "tmp/passkey-evidence-manual";
const commandsPath = path.join(templateDir, "redacted-commands.template.json");
const recordCommandPath = path.join(templateDir, "record-command.template.sh");
const keycloakBaseUrl = normalizeBaseUrl(
  process.env.BANKING_LAB_PASSKEY_LIVE_KEYCLOAK_BASE_URL ?? "http://localhost:18127",
  "BANKING_LAB_PASSKEY_LIVE_KEYCLOAK_BASE_URL",
  ["localhost", "127.0.0.1"]
);
const coreBaseUrl = normalizeBaseUrl(
  process.env.BANKING_LAB_PASSKEY_LIVE_CORE_BASE_URL ?? "http://127.0.0.1:18126",
  "BANKING_LAB_PASSKEY_LIVE_CORE_BASE_URL",
  ["localhost", "127.0.0.1"]
);
const expectedIssuer = `${keycloakBaseUrl}/realms/banking-lab`;
const discoveryUrl = `${expectedIssuer}/.well-known/openid-configuration`;
const healthUrl = `${coreBaseUrl}/health`;
const timeoutMillis = Number(process.env.BANKING_LAB_PASSKEY_LIVE_READINESS_TIMEOUT_MS ?? "10000");
const errors: string[] = [];

function normalizeBaseUrl(value: string, name: string, allowedHosts: string[]): string {
  let url: URL;
  try {
    url = new URL(value);
  } catch (error) {
    throw new Error(`${name} must be a valid URL: ${(error as Error).message}`);
  }
  if (url.protocol !== "http:" || !allowedHosts.includes(url.hostname) || url.username || url.password || url.pathname !== "/" || url.search || url.hash) {
    throw new Error(`${name} must be an http URL origin on ${allowedHosts.join(" or ")} without path, credentials, query, or fragment.`);
  }
  return url.origin;
}

function assertNoReusableSecrets(label: string, source: string): void {
  const forbidden = [
    /\baccess_token\b/i,
    /\brefresh_token\b/i,
    /\bid_token\b/i,
    /\bcookie\b/i,
    /\bset-cookie\b/i,
    /\bauthorization:\s*bearer\s+[A-Za-z0-9._-]+/i,
    /\bpassword=/i,
    /manager-webauthn01-pass/i,
    /credentialId/i,
    /attestationObject/i,
    /clientDataJSON/i,
    /\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b/,
    /010-0000-1001/
  ];
  for (const pattern of forbidden) {
    if (pattern.test(source)) {
      errors.push(`${label} contains an unredacted token, credential, cookie, password, reusable passkey artifact, or unmasked phone value.`);
      return;
    }
  }
}

async function readRequired(filePath: string): Promise<string> {
  try {
    return await readFile(filePath, "utf8");
  } catch (error) {
    errors.push(`Missing or unreadable live passkey readiness path: ${filePath}: ${(error as Error).message}`);
    return "";
  }
}

function commandTemplateText(source: string): string {
  assertNoReusableSecrets("passkey command template", source);
  let parsed: unknown;
  try {
    parsed = JSON.parse(source);
  } catch (error) {
    errors.push(`Passkey command template must be JSON: ${(error as Error).message}`);
    return "";
  }
  const commands = Array.isArray(parsed)
    ? parsed.filter((item): item is CommandEvidenceTemplate => item && typeof item === "object")
    : [];
  if (!Array.isArray(parsed) || commands.length !== parsed.length) {
    errors.push("Passkey command template must be an array of command evidence objects.");
  }
  return commands.map((item) => typeof item.command === "string" ? item.command : "").join("\n");
}

function requireIncludes(source: string, needle: string, message: string): void {
  if (!source.includes(needle)) {
    errors.push(message);
  }
}

function validatePreparedTemplates(commandTemplate: string, recordCommand: string): void {
  assertNoReusableSecrets("passkey recorder command template", recordCommand);
  const commandText = commandTemplateText(commandTemplate);
  for (const marker of [
    "COMPOSE_PROJECT_NAME=banking-lab-passkey-manual",
    "BANKING_LAB_SECURITY_ENABLED=true",
    "BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false",
    `BANKING_LAB_SECURITY_ISSUER=${expectedIssuer}`,
    "BANKING_LAB_SECURITY_AUDIENCE=core-banking-api",
    "BANKING_LAB_SYNTHETIC_SEED_ENABLED=true",
    "docker compose --profile platform up -d --build postgres keycloak core-banking",
    discoveryUrl,
    healthUrl,
    "manual browser sign-in completed with a real",
    "npm run passkey:evidence:record"
  ]) {
    requireIncludes(commandText, marker, `Prepared passkey command template must include ${marker}.`);
  }
  for (const marker of [
    `BANKING_LAB_PASSKEY_KEYCLOAK_ISSUER=${expectedIssuer}`,
    "BANKING_LAB_PASSKEY_BROWSER_ORIGIN=http://localhost:3002",
    "BANKING_LAB_PASSKEY_SIMULATOR_TOKENS_ENABLED=false",
    `BANKING_LAB_PASSKEY_COMMANDS_FILE=${commandsPath}`,
    "npm run passkey:evidence:record"
  ]) {
    requireIncludes(recordCommand, marker, `Prepared passkey recorder command must include ${marker}.`);
  }
}

async function fetchJson(url: string): Promise<JsonObject | undefined> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMillis);
  try {
    const response = await fetch(url, {
      headers: { accept: "application/json" },
      signal: controller.signal
    });
    const body = await response.text();
    if (!response.ok) {
      errors.push(`${url} returned HTTP ${response.status}.`);
      return undefined;
    }
    try {
      const parsed = JSON.parse(body) as unknown;
      if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
        return parsed as JsonObject;
      }
      errors.push(`${url} must return a JSON object.`);
      return undefined;
    } catch (error) {
      errors.push(`${url} did not return valid JSON: ${(error as Error).message}`);
      return undefined;
    }
  } catch (error) {
    errors.push(`${url} is not reachable: ${(error as Error).message}`);
    return undefined;
  } finally {
    clearTimeout(timer);
  }
}

async function readFixtureJson(filePath: string, label: string): Promise<JsonObject | undefined> {
  let source: string;
  try {
    source = await readFile(filePath, "utf8");
  } catch (error) {
    errors.push(`${label} fixture is not readable at ${filePath}: ${(error as Error).message}`);
    return undefined;
  }
  try {
    const parsed = JSON.parse(source) as unknown;
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      return parsed as JsonObject;
    }
    errors.push(`${label} fixture must be a JSON object.`);
    return undefined;
  } catch (error) {
    errors.push(`${label} fixture must contain valid JSON: ${(error as Error).message}`);
    return undefined;
  }
}

async function endpointJson(url: string, fixtureEnvName: string, label: string): Promise<JsonObject | undefined> {
  const fixturePath = process.env[fixtureEnvName];
  if (fixturePath && fixturePath.trim().length > 0) {
    return readFixtureJson(fixturePath.trim(), label);
  }
  return fetchJson(url);
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}

function validateDiscovery(discovery: JsonObject | undefined): string | undefined {
  if (!discovery) return undefined;
  const issuer = discovery.issuer;
  const jwksUri = discovery.jwks_uri;
  if (issuer !== expectedIssuer) {
    errors.push(`Keycloak discovery issuer must be ${expectedIssuer}.`);
  }
  if (jwksUri !== `${expectedIssuer}/protocol/openid-connect/certs`) {
    errors.push(`Keycloak discovery jwks_uri must be ${expectedIssuer}/protocol/openid-connect/certs.`);
  }
  if (typeof discovery.authorization_endpoint !== "string" || !discovery.authorization_endpoint.startsWith(`${expectedIssuer}/protocol/openid-connect/auth`)) {
    errors.push("Keycloak discovery authorization_endpoint must point to the banking-lab realm.");
  }
  if (typeof discovery.token_endpoint !== "string" || !discovery.token_endpoint.startsWith(`${expectedIssuer}/protocol/openid-connect/token`)) {
    errors.push("Keycloak discovery token_endpoint must point to the banking-lab realm.");
  }
  if (!stringArray(discovery.response_types_supported).includes("code")) {
    errors.push("Keycloak discovery must support authorization code response type.");
  }
  return typeof jwksUri === "string" ? jwksUri : undefined;
}

function validateJwks(jwks: JsonObject | undefined): void {
  if (!jwks) return;
  const keys = Array.isArray(jwks.keys) ? jwks.keys : [];
  if (keys.length === 0) {
    errors.push("Keycloak JWKS endpoint must return at least one signing key.");
  }
}

function validateHealth(health: JsonObject | undefined): void {
  if (!health) return;
  if (health.status !== "ok") {
    errors.push("Spring /health status must be ok.");
  }
  if (health.syntheticOnly !== true) {
    errors.push("Spring /health syntheticOnly must be true.");
  }
  if (health.auditHashChainValid !== true) {
    errors.push("Spring /health auditHashChainValid must be true.");
  }
  if (health.migrationTarget !== "kotlin-spring-boot") {
    errors.push("Spring /health migrationTarget must be kotlin-spring-boot.");
  }
}

const commandTemplate = await readRequired(commandsPath);
const recordCommand = await readRequired(recordCommandPath);
validatePreparedTemplates(commandTemplate, recordCommand);

const discovery = await endpointJson(discoveryUrl, "BANKING_LAB_PASSKEY_LIVE_DISCOVERY_FILE", "Keycloak discovery");
const jwksUri = validateDiscovery(discovery);
if (jwksUri) {
  validateJwks(await endpointJson(jwksUri, "BANKING_LAB_PASSKEY_LIVE_JWKS_FILE", "Keycloak JWKS"));
}
validateHealth(await endpointJson(healthUrl, "BANKING_LAB_PASSKEY_LIVE_HEALTH_FILE", "Spring health"));

if (errors.length > 0) {
  console.error("Passkey live platform readiness check: failed");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log("Passkey live platform readiness check: pass");
console.log(`Keycloak discovery: ${discoveryUrl}`);
console.log(`Spring health: ${healthUrl}`);
console.log("This proves the local platform is ready for the manual browser ceremony, but does not prove non-synthetic passkey operations.");
