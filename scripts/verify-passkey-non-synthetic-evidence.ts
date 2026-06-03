import { readFile } from "node:fs/promises";

type PasskeyEvidenceRecord = {
  schemaVersion?: unknown;
  status?: unknown;
  testDate?: unknown;
  evidenceKind?: unknown;
  authenticatorKind?: unknown;
  usedBrowserVirtualAuthenticator?: unknown;
  usedPlaywrightCdpWebAuthn?: unknown;
  simulatorTokensEnabled?: unknown;
  keycloakRequiredActionCompleted?: unknown;
  springSignedTokenAccepted?: unknown;
  syntheticOnly?: unknown;
  redactionConfirmed?: unknown;
  manualCeremony?: unknown;
  commands?: unknown;
  staffPanelAssertions?: unknown;
};

type CommandEvidence = {
  command?: unknown;
  status?: unknown;
  exitCode?: unknown;
  summary?: unknown;
};

const artifactPath = process.env.BANKING_LAB_PASSKEY_EVIDENCE_ARTIFACT
  ?? "docs/test-evidence/generated/passkey-non-synthetic-evidence.json";

function assertNoReusableSecrets(source: string): string[] {
  const errors: string[] = [];
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
      errors.push("Artifact appears to contain an unredacted token, credential, cookie, password, reusable passkey artifact, or unmasked phone value.");
      break;
    }
  }
  return errors;
}

function commandEvidenceArray(value: unknown): CommandEvidence[] {
  return Array.isArray(value)
    ? value.filter((item): item is CommandEvidence => item && typeof item === "object")
    : [];
}

function objectRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}

function validateLocalhostOrigin(value: unknown, field: string): string[] {
  const errors: string[] = [];
  if (typeof value !== "string") {
    return [`${field} must be a string.`];
  }
  let url: URL;
  try {
    url = new URL(value);
  } catch (error) {
    return [`${field} must be a valid URL origin: ${(error as Error).message}`];
  }
  if (url.protocol !== "http:" || url.hostname !== "localhost" || url.username || url.password || url.pathname !== "/" || url.search || url.hash) {
    errors.push(`${field} must be an http://localhost origin without path, credentials, query, or fragment.`);
  }
  return errors;
}

function validateLocalhostIssuer(value: unknown, field: string): string[] {
  const errors: string[] = [];
  if (typeof value !== "string") {
    return [`${field} must be a string.`];
  }
  let url: URL;
  try {
    url = new URL(value);
  } catch (error) {
    return [`${field} must be a valid Keycloak issuer URL: ${(error as Error).message}`];
  }
  if (url.protocol !== "http:" || url.hostname !== "localhost" || url.username || url.password || url.pathname !== "/realms/banking-lab" || url.search || url.hash) {
    errors.push(`${field} must be an http://localhost Keycloak issuer ending in /realms/banking-lab without credentials, query, or fragment.`);
  }
  return errors;
}

function validateManualCeremony(value: unknown): string[] {
  const errors: string[] = [];
  const ceremony = objectRecord(value);
  if (Object.keys(ceremony).length === 0) {
    return ["manualCeremony must be an object with the local WebAuthn ceremony boundary."];
  }
  errors.push(...validateLocalhostOrigin(ceremony.browserOrigin, "manualCeremony.browserOrigin"));
  errors.push(...validateLocalhostIssuer(ceremony.keycloakIssuer, "manualCeremony.keycloakIssuer"));
  if (ceremony.rpId !== "localhost") errors.push("manualCeremony.rpId must be localhost.");
  if (ceremony.username !== "manager-webauthn01") errors.push("manualCeremony.username must be manager-webauthn01.");
  if (ceremony.authorizationFlow !== "authorization-code-pkce") {
    errors.push("manualCeremony.authorizationFlow must be authorization-code-pkce.");
  }
  if (ceremony.browserAutomation !== "ordinary-browser-no-virtual-authenticator") {
    errors.push("manualCeremony.browserAutomation must be ordinary-browser-no-virtual-authenticator.");
  }
  if (ceremony.operatorConfirmation !== "real-platform-or-hardware-authenticator-used") {
    errors.push("manualCeremony.operatorConfirmation must be real-platform-or-hardware-authenticator-used.");
  }
  return errors;
}

function validateEvidence(evidence: PasskeyEvidenceRecord, source: string): string[] {
  const errors: string[] = [...assertNoReusableSecrets(source)];
  const authenticatorKind = typeof evidence.authenticatorKind === "string" ? evidence.authenticatorKind : "";
  const commands = commandEvidenceArray(evidence.commands);
  const commandText = commands
    .filter((item): item is CommandEvidence & { command: string } => typeof item.command === "string")
    .map((item) => item.command)
    .join("\n");
  const staffPanelAssertions = evidence.staffPanelAssertions && typeof evidence.staffPanelAssertions === "object"
    ? evidence.staffPanelAssertions as Record<string, unknown>
    : {};

  if (evidence.schemaVersion !== 1) errors.push("schemaVersion must be 1.");
  if (evidence.status !== "pass") errors.push("status must be pass.");
  if (!/^\d{4}-\d{2}-\d{2}$/.test(String(evidence.testDate ?? ""))) errors.push("testDate must be YYYY-MM-DD.");
  if (evidence.evidenceKind !== "manual-live-passkey") errors.push("evidenceKind must be manual-live-passkey.");
  if (!["platform", "hardware-security-key"].includes(authenticatorKind)) {
    errors.push("authenticatorKind must be platform or hardware-security-key.");
  }
  if (/virtual|simulated|cdp/i.test(authenticatorKind)) {
    errors.push("authenticatorKind must not be virtual, simulated, or CDP-backed.");
  }
  if (evidence.usedBrowserVirtualAuthenticator !== false) errors.push("usedBrowserVirtualAuthenticator must be false.");
  if (evidence.usedPlaywrightCdpWebAuthn !== false) errors.push("usedPlaywrightCdpWebAuthn must be false.");
  if (evidence.simulatorTokensEnabled !== false) errors.push("simulatorTokensEnabled must be false.");
  if (evidence.keycloakRequiredActionCompleted !== true) errors.push("keycloakRequiredActionCompleted must be true.");
  if (evidence.springSignedTokenAccepted !== true) errors.push("springSignedTokenAccepted must be true.");
  if (evidence.syntheticOnly !== true) errors.push("syntheticOnly must be true.");
  if (evidence.redactionConfirmed !== true) errors.push("redactionConfirmed must be true.");
  errors.push(...validateManualCeremony(evidence.manualCeremony));
  if (!Array.isArray(evidence.commands) || commands.length !== evidence.commands.length) {
    errors.push("commands must be an array of command evidence objects.");
  }
  if (commands.length === 0) {
    errors.push("commands must include at least one command evidence item.");
  }
  const seenCommands = new Set<string>();
  for (const commandEvidence of commands) {
    if (typeof commandEvidence.command !== "string" || commandEvidence.command.trim().length === 0) {
      errors.push("Every command evidence item must include a non-empty command.");
      continue;
    }
    if (seenCommands.has(commandEvidence.command)) {
      errors.push(`commands must not include duplicate command ${commandEvidence.command}.`);
    }
    seenCommands.add(commandEvidence.command);
    if (commandEvidence.status !== "pass") errors.push(`${commandEvidence.command} status must be pass.`);
    if (commandEvidence.exitCode !== 0) errors.push(`${commandEvidence.command} exitCode must be 0.`);
    if (typeof commandEvidence.summary !== "string" || commandEvidence.summary.trim().length === 0) {
      errors.push(`${commandEvidence.command} summary must be a non-empty string.`);
    }
  }
  for (const [pattern, description] of [
    [/docker compose --profile platform up/u, "live Docker Compose platform startup"],
    [/BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false/u, "simulator-token-disabled Spring setting"],
    [/\.well-known\/openid-configuration/u, "live Keycloak discovery readiness check"],
    [/\/health/u, "live Spring health readiness check"],
    [/real (platform authenticator|hardware security key)/iu, "real platform authenticator or hardware security key attestation"],
    [/npm run passkey:evidence:record/u, "passkey evidence recorder command"]
  ] as Array<[RegExp, string]>) {
    if (!pattern.test(commandText)) {
      errors.push(`commands must include ${description}.`);
    }
  }
  for (const pattern of [
    /WebAuthn\.enable/u,
    /WebAuthn\.addVirtualAuthenticator/u,
    /addVirtualAuthenticator/u,
    /virtual authenticator/iu,
    /CDP WebAuthn/iu
  ]) {
    if (pattern.test(commandText)) {
      errors.push("commands must not include CDP or browser virtual-authenticator operations.");
      break;
    }
  }

  for (const key of [
    "webAuthnLoaded",
    "managerSubjectObserved",
    "bearerTokenTypeObserved",
    "syntheticCustomerObserved",
    "maskedPiiObserved",
    "auditEventObserved"
  ]) {
    if (staffPanelAssertions[key] !== true) {
      errors.push(`staffPanelAssertions.${key} must be true.`);
    }
  }

  return errors;
}

async function main(): Promise<void> {
  let source: string;
  try {
    source = await readFile(artifactPath, "utf8");
  } catch (error) {
    throw new Error(`Could not read passkey evidence artifact at ${artifactPath}: ${(error as Error).message}`);
  }

  let evidence: PasskeyEvidenceRecord;
  try {
    evidence = JSON.parse(source) as PasskeyEvidenceRecord;
  } catch (error) {
    throw new Error(`Could not parse passkey evidence artifact at ${artifactPath}: ${(error as Error).message}`);
  }

  const errors = validateEvidence(evidence, source);
  if (errors.length > 0) {
    console.error("Passkey non-synthetic evidence verification: failed");
    for (const error of errors) {
      console.error(`- ${error}`);
    }
    process.exit(1);
  }

  console.log("Passkey non-synthetic evidence verification: pass");
  console.log(`Verified manual-live-passkey artifact: ${artifactPath}`);
}

main().catch((error: unknown) => {
  console.error(`Passkey non-synthetic evidence verification failed: ${(error as Error).message}`);
  process.exit(1);
});
