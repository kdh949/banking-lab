import { access, readFile } from "node:fs/promises";
import path from "node:path";

type CommandEvidenceTemplate = {
  command?: unknown;
  status?: unknown;
  exitCode?: unknown;
  summary?: unknown;
};

type PackageJson = {
  scripts?: Record<string, string>;
};

type RequiredGate = {
  id?: unknown;
  status?: unknown;
  evidence?: unknown;
};

type NodeRetirementGate = {
  status?: unknown;
  requiredGates?: unknown;
};

type KeycloakUser = {
  username?: unknown;
  enabled?: unknown;
  requiredActions?: unknown;
  realmRoles?: unknown;
};

type KeycloakRealm = {
  webAuthnPolicyRpId?: unknown;
  webAuthnPolicyUserVerificationRequirement?: unknown;
  users?: unknown;
};

const templateDir = process.env.BANKING_LAB_PASSKEY_TEMPLATE_DIR ?? "tmp/passkey-evidence-manual";
const commandsPath = path.join(templateDir, "redacted-commands.template.json");
const panelSnapshotPath = path.join(templateDir, "redacted-staff-panel.template.txt");
const recordCommandPath = path.join(templateDir, "record-command.template.sh");
const packageJsonPath = "package.json";
const gatePath = "docs/migration/node-retirement-gate.json";
const dockerComposePath = "docker-compose.yml";
const realmPath = "infra/keycloak/realm-banking-lab.json";
const readinessPath = "scripts/check-passkey-manual-readiness.ts";
const readinessTestPath = "tests/passkeyManualReadiness.test.mjs";
const errors: string[] = [];

async function exists(filePath: string): Promise<boolean> {
  try {
    await access(filePath);
    return true;
  } catch {
    return false;
  }
}

async function readRequired(filePath: string): Promise<string> {
  try {
    return await readFile(filePath, "utf8");
  } catch (error) {
    errors.push(`Missing or unreadable passkey manual readiness path: ${filePath}: ${(error as Error).message}`);
    return "";
  }
}

async function readJson<T>(filePath: string): Promise<T | undefined> {
  const source = await readRequired(filePath);
  if (!source) return undefined;
  try {
    return JSON.parse(source) as T;
  } catch (error) {
    errors.push(`Could not parse ${filePath}: ${(error as Error).message}`);
    return undefined;
  }
}

function objectArray<T>(value: unknown): T[] {
  return Array.isArray(value) ? value.filter((item): item is T => typeof item === "object" && item !== null) : [];
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}

function requireIncludes(source: string, needle: string, message: string): void {
  if (!source.includes(needle)) {
    errors.push(message);
  }
}

function requirePattern(source: string, pattern: RegExp, message: string): void {
  if (!pattern.test(source)) {
    errors.push(message);
  }
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

function validateGateAndScripts(packageJson: PackageJson | undefined, gate: NodeRetirementGate | undefined): void {
  if (packageJson?.scripts?.["passkey:evidence:readiness"] !== `node --experimental-strip-types ${readinessPath}`) {
    errors.push("package.json must expose passkey:evidence:readiness for manual passkey template readiness.");
  }
  if (gate?.status !== "blocked") {
    errors.push("Node retirement gate must remain blocked while manual passkey readiness is only a pre-evidence check.");
  }
  const requiredGates = objectArray<RequiredGate>(gate?.requiredGates);
  for (const gateId of ["non-synthetic-passkey-operations", "evidence-refresh"]) {
    const requiredGate = requiredGates.find((item) => item.id === gateId);
    if (!requiredGate) {
      errors.push(`Missing required gate ${gateId}.`);
      continue;
    }
    if (gateId === "non-synthetic-passkey-operations" && requiredGate.status !== "pending") {
      errors.push("non-synthetic-passkey-operations must remain pending after readiness checks.");
    }
    for (const evidencePath of [readinessPath, readinessTestPath]) {
      if (!stringArray(requiredGate.evidence).includes(evidencePath)) {
        errors.push(`${gateId} evidence must include ${evidencePath}.`);
      }
    }
  }
}

function validatePreparedCommands(source: string): void {
  assertNoReusableSecrets("passkey command template", source);
  let parsed: unknown;
  try {
    parsed = JSON.parse(source);
  } catch (error) {
    errors.push(`Passkey command template must be JSON: ${(error as Error).message}`);
    return;
  }
  if (!Array.isArray(parsed)) {
    errors.push("Passkey command template must be a JSON array.");
    return;
  }
  const commands = parsed.filter((item): item is CommandEvidenceTemplate => item && typeof item === "object");
  if (commands.length !== parsed.length) {
    errors.push("Every passkey command template item must be an object.");
  }
  if (commands.length !== 5) {
    errors.push("Passkey command template must include exactly five manual-run command evidence items.");
  }

  const commandText = commands
    .map((item) => typeof item.command === "string" ? item.command : "")
    .join("\n");
  for (const [pattern, description] of [
    [/COMPOSE_PROJECT_NAME=banking-lab-passkey-manual/u, "manual Compose project name"],
    [/BANKING_LAB_POSTGRES_PORT=15477/u, "manual PostgreSQL port"],
    [/BANKING_LAB_CORE_BANKING_PORT=18126/u, "manual Spring port"],
    [/BANKING_LAB_KEYCLOAK_PORT=18127/u, "manual Keycloak port"],
    [/BANKING_LAB_SECURITY_ENABLED=true/u, "Spring security enabled"],
    [/BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false/u, "simulator tokens disabled"],
    [/BANKING_LAB_SECURITY_JWKS_URI=http:\/\/keycloak:8080\/realms\/banking-lab\/protocol\/openid-connect\/certs/u, "Keycloak container JWKS URI"],
    [/BANKING_LAB_SECURITY_ISSUER=http:\/\/localhost:18127\/realms\/banking-lab/u, "local Keycloak issuer"],
    [/BANKING_LAB_SECURITY_AUDIENCE=core-banking-api/u, "Spring JWT audience"],
    [/BANKING_LAB_SYNTHETIC_SEED_ENABLED=true/u, "synthetic seed enabled"],
    [/docker compose --profile platform up -d --build postgres keycloak core-banking/u, "minimal live platform startup"],
    [/http:\/\/localhost:18127\/realms\/banking-lab\/\.well-known\/openid-configuration/u, "Keycloak discovery readiness"],
    [/http:\/\/127\.0\.0\.1:18126\/health/u, "Spring health readiness"],
    [/manual browser sign-in completed with a real (platform authenticator|hardware security key)/iu, "real authenticator operator step"],
    [/npm run passkey:evidence:record/u, "passkey evidence recorder command"]
  ] as Array<[RegExp, string]>) {
    requirePattern(commandText, pattern, `Passkey command template must include ${description}.`);
  }
  for (const pattern of [
    /WebAuthn\.enable/u,
    /WebAuthn\.addVirtualAuthenticator/u,
    /addVirtualAuthenticator/u,
    /virtual authenticator/iu,
    /CDP WebAuthn/iu
  ]) {
    if (pattern.test(commandText)) {
      errors.push("Passkey command template must not include CDP or browser virtual-authenticator operations.");
      break;
    }
  }

  for (const command of commands) {
    if (typeof command.command !== "string" || command.command.trim().length === 0) {
      errors.push("Every passkey command template item must include a non-empty command.");
    }
    if (command.status !== "TODO_REPLACE_WITH_pass") {
      errors.push(`${String(command.command)} status must remain TODO_REPLACE_WITH_pass before the real run.`);
    }
    if (command.exitCode !== "TODO_REPLACE_WITH_0") {
      errors.push(`${String(command.command)} exitCode must remain TODO_REPLACE_WITH_0 before the real run.`);
    }
    if (typeof command.summary !== "string" || !/Replace this summary/.test(command.summary)) {
      errors.push(`${String(command.command)} summary must be a replacement instruction before the real run.`);
    }
  }
}

function validatePanelTemplate(source: string): void {
  assertNoReusableSecrets("passkey staff panel template", source);
  for (const marker of [
    "Keycloak WebAuthn manager loaded",
    "manager-webauthn01",
    "Bearer",
    "SYN-CUS-001",
    "010-****-1001",
    "AUD-"
  ]) {
    requireIncludes(source, marker, `Passkey staff panel template must include ${marker}.`);
  }
}

function validateRecordCommand(source: string): void {
  assertNoReusableSecrets("passkey recorder command template", source);
  for (const marker of [
    "BANKING_LAB_PASSKEY_EVIDENCE_CONFIRMED=true",
    "BANKING_LAB_PASSKEY_AUTHENTICATOR_KIND=",
    "BANKING_LAB_PASSKEY_BROWSER_ORIGIN=http://localhost:3002",
    "BANKING_LAB_PASSKEY_KEYCLOAK_ISSUER=http://localhost:18127/realms/banking-lab",
    "BANKING_LAB_PASSKEY_RP_ID=localhost",
    "BANKING_LAB_PASSKEY_USERNAME=manager-webauthn01",
    "BANKING_LAB_PASSKEY_AUTHORIZATION_FLOW=authorization-code-pkce",
    "BANKING_LAB_PASSKEY_BROWSER_AUTOMATION=ordinary-browser-no-virtual-authenticator",
    "BANKING_LAB_PASSKEY_OPERATOR_CONFIRMATION=real-platform-or-hardware-authenticator-used",
    "BANKING_LAB_PASSKEY_USED_BROWSER_VIRTUAL_AUTHENTICATOR=false",
    "BANKING_LAB_PASSKEY_USED_PLAYWRIGHT_CDP_WEBAUTHN=false",
    "BANKING_LAB_PASSKEY_SIMULATOR_TOKENS_ENABLED=false",
    "BANKING_LAB_PASSKEY_KEYCLOAK_REQUIRED_ACTION_COMPLETED=true",
    "BANKING_LAB_PASSKEY_SPRING_SIGNED_TOKEN_ACCEPTED=true",
    "BANKING_LAB_PASSKEY_SYNTHETIC_ONLY=true",
    "BANKING_LAB_PASSKEY_REDACTION_CONFIRMED=true",
    `BANKING_LAB_PASSKEY_COMMANDS_FILE=${commandsPath}`,
    `BANKING_LAB_PASSKEY_PANEL_SNAPSHOT_FILE=${panelSnapshotPath}`,
    "npm run passkey:evidence:record"
  ]) {
    requireIncludes(source, marker, `Passkey recorder command template must include ${marker}.`);
  }
}

function validateStaticPlatformConfig(dockerCompose: string, realm: KeycloakRealm | undefined): void {
  for (const marker of [
    "core-banking:",
    "keycloak:",
    "profiles:",
    "platform",
    "${BANKING_LAB_CORE_BANKING_PORT:-8081}:8081",
    "${BANKING_LAB_KEYCLOAK_PORT:-8085}:8080",
    "./infra/keycloak/realm-banking-lab.json:/opt/keycloak/data/import/realm-banking-lab.json:ro"
  ]) {
    requireIncludes(dockerCompose, marker, `Docker Compose platform config must include ${marker}.`);
  }

  if (realm?.webAuthnPolicyRpId !== "localhost") {
    errors.push("Keycloak realm WebAuthn RP ID must remain localhost for the manual passkey run.");
  }
  if (realm?.webAuthnPolicyUserVerificationRequirement !== "required") {
    errors.push("Keycloak realm WebAuthn policy must require user verification.");
  }
  const users = objectArray<KeycloakUser>(realm?.users);
  const manager = users.find((item) => item.username === "manager-webauthn01");
  if (!manager) {
    errors.push("Keycloak realm must include manager-webauthn01.");
  } else {
    if (manager.enabled !== true) errors.push("manager-webauthn01 must be enabled.");
    if (!stringArray(manager.requiredActions).includes("webauthn-register")) {
      errors.push("manager-webauthn01 must require webauthn-register.");
    }
    if (!stringArray(manager.realmRoles).includes("BRANCH_MANAGER")) {
      errors.push("manager-webauthn01 must keep BRANCH_MANAGER.");
    }
  }
}

for (const filePath of [
  packageJsonPath,
  gatePath,
  dockerComposePath,
  realmPath,
  commandsPath,
  panelSnapshotPath,
  recordCommandPath
]) {
  if (!await exists(filePath)) {
    errors.push(`Required passkey manual readiness path is missing: ${filePath}. Run npm run passkey:evidence:prepare before readiness.`);
  }
}

const packageJson = await readJson<PackageJson>(packageJsonPath);
const gate = await readJson<NodeRetirementGate>(gatePath);
const realm = await readJson<KeycloakRealm>(realmPath);
const dockerCompose = await readRequired(dockerComposePath);
const commandTemplate = await readRequired(commandsPath);
const panelTemplate = await readRequired(panelSnapshotPath);
const recordCommand = await readRequired(recordCommandPath);

validateGateAndScripts(packageJson, gate);
validateStaticPlatformConfig(dockerCompose, realm);
validatePreparedCommands(commandTemplate);
validatePanelTemplate(panelTemplate);
validateRecordCommand(recordCommand);

if (errors.length > 0) {
  console.error("Passkey manual readiness check: failed");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log("Passkey manual readiness check: pass");
console.log(`Prepared command template: ${commandsPath}`);
console.log(`Prepared staff panel template: ${panelSnapshotPath}`);
console.log(`Prepared recorder command template: ${recordCommandPath}`);
console.log("This confirms manual-run inputs are aligned, but does not prove non-synthetic passkey operations.");
