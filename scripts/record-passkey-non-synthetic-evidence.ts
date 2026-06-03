import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";

type AuthenticatorKind = "platform" | "hardware-security-key";

type StaffPanelAssertions = {
  webAuthnLoaded: boolean;
  managerSubjectObserved: boolean;
  bearerTokenTypeObserved: boolean;
  syntheticCustomerObserved: boolean;
  maskedPiiObserved: boolean;
  auditEventObserved: boolean;
};

type ManualCeremonyEvidence = {
  browserOrigin: string;
  keycloakIssuer: string;
  rpId: "localhost";
  username: "manager-webauthn01";
  authorizationFlow: "authorization-code-pkce";
  browserAutomation: "ordinary-browser-no-virtual-authenticator";
  operatorConfirmation: "real-platform-or-hardware-authenticator-used";
};

type CommandEvidence = {
  command?: unknown;
  status?: unknown;
  exitCode?: unknown;
  summary?: unknown;
};

type PasskeyEvidenceRecord = {
  schemaVersion: 1;
  status: "pass";
  testDate: string;
  evidenceKind: "manual-live-passkey";
  authenticatorKind: AuthenticatorKind;
  usedBrowserVirtualAuthenticator: false;
  usedPlaywrightCdpWebAuthn: false;
  simulatorTokensEnabled: false;
  keycloakRequiredActionCompleted: true;
  springSignedTokenAccepted: true;
  syntheticOnly: true;
  redactionConfirmed: true;
  manualCeremony: ManualCeremonyEvidence;
  commands: Array<Required<CommandEvidence>>;
  staffPanelAssertions: StaffPanelAssertions;
};

const outputPath = process.env.BANKING_LAB_PASSKEY_EVIDENCE_OUTPUT ?? "docs/test-evidence/generated/passkey-non-synthetic-evidence.json";
const commandsFile = process.env.BANKING_LAB_PASSKEY_COMMANDS_FILE;
const panelSnapshotFile = process.env.BANKING_LAB_PASSKEY_PANEL_SNAPSHOT_FILE;

function envBoolean(name: string): boolean | undefined {
  const value = process.env[name];
  if (value === undefined) return undefined;
  if (value === "true") return true;
  if (value === "false") return false;
  throw new Error(`${name} must be true or false.`);
}

function requireBoolean(name: string, expected: boolean): void {
  const actual = envBoolean(name);
  if (actual !== expected) {
    throw new Error(`${name} must be ${expected}.`);
  }
}

function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value || value.trim().length === 0) {
    throw new Error(`${name} is required.`);
  }
  return value.trim();
}

function parseAuthenticatorKind(): AuthenticatorKind {
  const value = requireEnv("BANKING_LAB_PASSKEY_AUTHENTICATOR_KIND");
  if (value === "platform" || value === "hardware-security-key") {
    return value;
  }
  throw new Error("BANKING_LAB_PASSKEY_AUTHENTICATOR_KIND must be platform or hardware-security-key.");
}

function parseTestDate(): string {
  const value = process.env.BANKING_LAB_PASSKEY_TEST_DATE ?? new Date().toISOString().slice(0, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    throw new Error("BANKING_LAB_PASSKEY_TEST_DATE must be YYYY-MM-DD.");
  }
  return value;
}

function parseLocalhostOrigin(name: string): string {
  const value = requireEnv(name);
  let url: URL;
  try {
    url = new URL(value);
  } catch (error) {
    throw new Error(`${name} must be a valid URL origin: ${(error as Error).message}`);
  }
  if (url.protocol !== "http:" || url.hostname !== "localhost" || url.username || url.password || url.pathname !== "/" || url.search || url.hash) {
    throw new Error(`${name} must be an http://localhost origin without path, credentials, query, or fragment.`);
  }
  return url.origin;
}

function parseLocalhostIssuer(name: string): string {
  const value = requireEnv(name);
  let url: URL;
  try {
    url = new URL(value);
  } catch (error) {
    throw new Error(`${name} must be a valid Keycloak issuer URL: ${(error as Error).message}`);
  }
  if (url.protocol !== "http:" || url.hostname !== "localhost" || url.username || url.password || url.pathname !== "/realms/banking-lab" || url.search || url.hash) {
    throw new Error(`${name} must be an http://localhost Keycloak issuer ending in /realms/banking-lab without credentials, query, or fragment.`);
  }
  return url.toString().replace(/\/$/u, "");
}

function parseManualCeremony(): ManualCeremonyEvidence {
  const rpId = requireEnv("BANKING_LAB_PASSKEY_RP_ID");
  if (rpId !== "localhost") {
    throw new Error("BANKING_LAB_PASSKEY_RP_ID must be localhost for the local non-synthetic passkey run.");
  }
  const username = requireEnv("BANKING_LAB_PASSKEY_USERNAME");
  if (username !== "manager-webauthn01") {
    throw new Error("BANKING_LAB_PASSKEY_USERNAME must be manager-webauthn01.");
  }
  const authorizationFlow = requireEnv("BANKING_LAB_PASSKEY_AUTHORIZATION_FLOW");
  if (authorizationFlow !== "authorization-code-pkce") {
    throw new Error("BANKING_LAB_PASSKEY_AUTHORIZATION_FLOW must be authorization-code-pkce.");
  }
  const browserAutomation = requireEnv("BANKING_LAB_PASSKEY_BROWSER_AUTOMATION");
  if (browserAutomation !== "ordinary-browser-no-virtual-authenticator") {
    throw new Error("BANKING_LAB_PASSKEY_BROWSER_AUTOMATION must be ordinary-browser-no-virtual-authenticator.");
  }
  const operatorConfirmation = requireEnv("BANKING_LAB_PASSKEY_OPERATOR_CONFIRMATION");
  if (operatorConfirmation !== "real-platform-or-hardware-authenticator-used") {
    throw new Error("BANKING_LAB_PASSKEY_OPERATOR_CONFIRMATION must be real-platform-or-hardware-authenticator-used.");
  }

  return {
    browserOrigin: parseLocalhostOrigin("BANKING_LAB_PASSKEY_BROWSER_ORIGIN"),
    keycloakIssuer: parseLocalhostIssuer("BANKING_LAB_PASSKEY_KEYCLOAK_ISSUER"),
    rpId,
    username,
    authorizationFlow,
    browserAutomation,
    operatorConfirmation
  };
}

function parseCommands(source: string): Array<Required<CommandEvidence>> {
  let parsed: unknown;
  try {
    parsed = JSON.parse(source);
  } catch (error) {
    throw new Error(`Passkey evidence commands file must be a JSON array: ${(error as Error).message}`);
  }
  const commands = Array.isArray(parsed)
    ? parsed.filter((item): item is CommandEvidence => item && typeof item === "object")
    : [];
  if (commands.length === 0) {
    throw new Error("Passkey evidence commands file must contain at least one command evidence item.");
  }
  if (commands.length !== (Array.isArray(parsed) ? parsed.length : 0)) {
    throw new Error("Every passkey command evidence item must be an object.");
  }
  const seenCommands = new Set<string>();
  for (const evidence of commands) {
    if (typeof evidence.command !== "string" || evidence.command.trim().length === 0) {
      throw new Error("Every passkey command evidence item must include a non-empty command.");
    }
    if (seenCommands.has(evidence.command)) {
      throw new Error(`Passkey command evidence must not include duplicate command ${evidence.command}.`);
    }
    seenCommands.add(evidence.command);
    if (evidence.status !== "pass") {
      throw new Error(`${evidence.command} status must be pass.`);
    }
    if (evidence.exitCode !== 0) {
      throw new Error(`${evidence.command} exitCode must be 0.`);
    }
    if (typeof evidence.summary !== "string" || evidence.summary.trim().length === 0) {
      throw new Error(`${evidence.command} summary must be a non-empty string.`);
    }
  }
  return commands as Array<Required<CommandEvidence>>;
}

function assertRequiredPasskeyCommandEvidence(commands: Array<Required<CommandEvidence>>): void {
  const source = commands.map((item) => item.command).join("\n");
  const requiredMarkers: Array<[RegExp, string]> = [
    [/docker compose --profile platform up/u, "live Docker Compose platform startup"],
    [/BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false/u, "simulator-token-disabled Spring setting"],
    [/\.well-known\/openid-configuration/u, "live Keycloak discovery readiness check"],
    [/\/health/u, "live Spring health readiness check"],
    [/real (platform authenticator|hardware security key)/iu, "real platform authenticator or hardware security key attestation"],
    [/npm run passkey:evidence:record/u, "passkey evidence recorder command"]
  ];
  for (const [pattern, description] of requiredMarkers) {
    if (!pattern.test(source)) {
      throw new Error(`Passkey evidence commands must include ${description}.`);
    }
  }

  const forbiddenMarkers = [
    /WebAuthn\.enable/u,
    /WebAuthn\.addVirtualAuthenticator/u,
    /addVirtualAuthenticator/u,
    /virtual authenticator/iu,
    /CDP WebAuthn/iu
  ];
  for (const pattern of forbiddenMarkers) {
    if (pattern.test(source)) {
      throw new Error("Passkey evidence commands must not include CDP or browser virtual-authenticator operations.");
    }
  }
}

function assertNoReusableSecrets(label: string, value: string): void {
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
    /\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b/
  ];
  for (const pattern of forbidden) {
    if (pattern.test(value)) {
      throw new Error(`${label} appears to contain an unredacted token, credential, cookie, password, or reusable passkey artifact.`);
    }
  }
}

function staffPanelAssertions(snapshot: string): StaffPanelAssertions {
  if (/010-0000-1001/.test(snapshot)) {
    throw new Error("Staff panel snapshot contains unmasked phone output; non-synthetic passkey evidence must keep default PII masked.");
  }
  return {
    webAuthnLoaded: /Keycloak WebAuthn manager loaded/.test(snapshot),
    managerSubjectObserved: /manager-webauthn01/.test(snapshot),
    bearerTokenTypeObserved: /\bBearer\b/.test(snapshot),
    syntheticCustomerObserved: /SYN-CUS-001/.test(snapshot),
    maskedPiiObserved: /010-\*{4}-1001/.test(snapshot),
    auditEventObserved: /AUD-/.test(snapshot)
  };
}

function assertAllStaffPanelAssertions(assertions: StaffPanelAssertions): void {
  const failed = Object.entries(assertions)
    .filter(([, passed]) => !passed)
    .map(([name]) => name);
  if (failed.length > 0) {
    throw new Error(`Staff panel snapshot is missing required evidence: ${failed.join(", ")}.`);
  }
}

async function main(): Promise<void> {
  if (!commandsFile) {
    throw new Error("BANKING_LAB_PASSKEY_COMMANDS_FILE is required.");
  }
  if (!panelSnapshotFile) {
    throw new Error("BANKING_LAB_PASSKEY_PANEL_SNAPSHOT_FILE is required.");
  }

  requireBoolean("BANKING_LAB_PASSKEY_EVIDENCE_CONFIRMED", true);
  requireBoolean("BANKING_LAB_PASSKEY_USED_BROWSER_VIRTUAL_AUTHENTICATOR", false);
  requireBoolean("BANKING_LAB_PASSKEY_USED_PLAYWRIGHT_CDP_WEBAUTHN", false);
  requireBoolean("BANKING_LAB_PASSKEY_SIMULATOR_TOKENS_ENABLED", false);
  requireBoolean("BANKING_LAB_PASSKEY_KEYCLOAK_REQUIRED_ACTION_COMPLETED", true);
  requireBoolean("BANKING_LAB_PASSKEY_SPRING_SIGNED_TOKEN_ACCEPTED", true);
  requireBoolean("BANKING_LAB_PASSKEY_SYNTHETIC_ONLY", true);
  requireBoolean("BANKING_LAB_PASSKEY_REDACTION_CONFIRMED", true);

  const authenticatorKind = parseAuthenticatorKind();
  const testDate = parseTestDate();
  const manualCeremony = parseManualCeremony();
  const commandsSource = await readFile(commandsFile, "utf8");
  const snapshot = await readFile(panelSnapshotFile, "utf8");
  const commands = parseCommands(commandsSource);

  assertNoReusableSecrets("commands file", commandsSource);
  assertNoReusableSecrets("manual ceremony", JSON.stringify(manualCeremony));
  assertRequiredPasskeyCommandEvidence(commands);
  assertNoReusableSecrets("staff panel snapshot", snapshot);
  const assertions = staffPanelAssertions(snapshot);
  assertAllStaffPanelAssertions(assertions);

  const evidence: PasskeyEvidenceRecord = {
    schemaVersion: 1,
    status: "pass",
    testDate,
    evidenceKind: "manual-live-passkey",
    authenticatorKind,
    usedBrowserVirtualAuthenticator: false,
    usedPlaywrightCdpWebAuthn: false,
    simulatorTokensEnabled: false,
    keycloakRequiredActionCompleted: true,
    springSignedTokenAccepted: true,
    syntheticOnly: true,
    redactionConfirmed: true,
    manualCeremony,
    commands,
    staffPanelAssertions: assertions
  };

  await mkdir(path.dirname(outputPath), { recursive: true });
  await writeFile(outputPath, `${JSON.stringify(evidence, null, 2)}\n`);
  console.log(`Wrote ${outputPath}`);
}

main().catch((error: unknown) => {
  console.error(`Passkey non-synthetic evidence recording failed: ${(error as Error).message}`);
  process.exit(1);
});
