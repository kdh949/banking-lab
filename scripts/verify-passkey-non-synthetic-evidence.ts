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
  commands?: unknown;
  staffPanelAssertions?: unknown;
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

function validateEvidence(evidence: PasskeyEvidenceRecord, source: string): string[] {
  const errors: string[] = [...assertNoReusableSecrets(source)];
  const authenticatorKind = typeof evidence.authenticatorKind === "string" ? evidence.authenticatorKind : "";
  const commands = Array.isArray(evidence.commands) ? evidence.commands : [];
  const commandText = commands.filter((item): item is string => typeof item === "string").join("\n");
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
  if (commands.length === 0 || !commands.every((item) => typeof item === "string" && item.trim().length > 0)) {
    errors.push("commands must include at least one non-empty command.");
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
