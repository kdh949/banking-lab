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
  commands: string[];
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

function parseCommands(source: string): string[] {
  const commands = source
    .split(/\r?\n/u)
    .map((line) => line.trim())
    .filter((line) => line.length > 0 && !line.startsWith("#"));
  if (commands.length === 0) {
    throw new Error("Passkey evidence commands file must contain at least one command.");
  }
  return commands;
}

function assertRequiredPasskeyCommandEvidence(commands: string[]): void {
  const source = commands.join("\n");
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
  const commandsSource = await readFile(commandsFile, "utf8");
  const snapshot = await readFile(panelSnapshotFile, "utf8");
  const commands = parseCommands(commandsSource);

  assertNoReusableSecrets("commands file", commandsSource);
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
