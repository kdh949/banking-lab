import { spawnSync } from "node:child_process";
import { access, readFile } from "node:fs/promises";

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
  statusReason?: unknown;
  requiredGates?: unknown;
};

type KeycloakRequiredAction = {
  alias?: unknown;
  providerId?: unknown;
  enabled?: unknown;
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
  webAuthnPolicyAcceptableAaguids?: unknown;
  requiredActions?: unknown;
  users?: unknown;
};

const packageJsonPath = "package.json";
const gatePath = "docs/migration/node-retirement-gate.json";
const evidenceDocPath = "docs/test-evidence/passkey-non-synthetic-operations.md";
const preparePath = "scripts/prepare-passkey-non-synthetic-evidence.ts";
const readinessPath = "scripts/check-passkey-manual-readiness.ts";
const liveReadinessPath = "scripts/check-passkey-live-platform-readiness.ts";
const recorderPath = "scripts/record-passkey-non-synthetic-evidence.ts";
const verifierPath = "scripts/verify-passkey-non-synthetic-evidence.ts";
const preflightPath = "scripts/check-passkey-non-synthetic-preflight.ts";
const passkeyArtifactPath = "docs/test-evidence/generated/passkey-non-synthetic-evidence.json";
const realmPath = "infra/keycloak/realm-banking-lab.json";
const staffPanelPath = "apps/staff-terminal/src/components/terminal/IntegratedTerminalApp.tsx";
const staffWebAuthnSpecPath = "apps/staff-terminal/e2e/integrated-terminal.spec.ts";
const recorderTestPath = "tests/passkeyEvidenceRecorder.test.mjs";
const verifierTestPath = "tests/passkeyEvidenceVerifier.test.mjs";
const prepareTestPath = "tests/passkeyEvidencePrepare.test.mjs";
const readinessTestPath = "tests/passkeyManualReadiness.test.mjs";
const liveReadinessTestPath = "tests/passkeyLivePlatformReadiness.test.mjs";
const preflightTestPath = "tests/passkeyEvidencePreflight.test.mjs";
const passkeyGateId = "non-synthetic-passkey-operations";
const evidenceRefreshGateId = "evidence-refresh";

async function exists(path: string): Promise<boolean> {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
}

async function readJson<T>(path: string): Promise<T | undefined> {
  try {
    return JSON.parse(await readFile(path, "utf8")) as T;
  } catch (error) {
    errors.push(`Could not parse ${path}: ${(error as Error).message}`);
    return undefined;
  }
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}

function objectArray<T>(value: unknown): T[] {
  return Array.isArray(value) ? value.filter((item): item is T => typeof item === "object" && item !== null) : [];
}

function includes(source: string, needle: string, message: string): void {
  if (!source.includes(needle)) {
    errors.push(message);
  }
}

function matches(source: string, pattern: RegExp, message: string): void {
  if (!pattern.test(source)) {
    errors.push(message);
  }
}

function evidenceIncludes(gate: RequiredGate | undefined, path: string): void {
  if (!stringArray(gate?.evidence).includes(path)) {
    errors.push(`${String(gate?.id ?? "unknown gate")} evidence must include ${path}.`);
  }
}

function stringValue(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function runStrictPasskeyVerifier(): void {
  const result = spawnSync(process.execPath, ["--experimental-strip-types", verifierPath], {
    encoding: "utf8",
    maxBuffer: 1024 * 1024
  });
  const output = [result.stdout, result.stderr].filter(Boolean).join("\n");
  if (result.status !== 0 || !output.includes("Passkey non-synthetic evidence verification: pass")) {
    errors.push("Strict passkey evidence verifier must pass once non-synthetic-passkey-operations is marked pass.");
    if (output.trim()) {
      errors.push(output.trim());
    }
  }
}

async function validatePath(path: string): Promise<void> {
  if (!await exists(path)) {
    errors.push(`Required passkey preflight path is missing: ${path}.`);
  }
}

const errors: string[] = [];

for (const path of [
  packageJsonPath,
  gatePath,
  evidenceDocPath,
  preparePath,
  readinessPath,
  liveReadinessPath,
  recorderPath,
  verifierPath,
  preflightPath,
  realmPath,
  staffPanelPath,
  staffWebAuthnSpecPath,
  prepareTestPath,
  readinessTestPath,
  liveReadinessTestPath,
  recorderTestPath,
  verifierTestPath,
  preflightTestPath
]) {
  await validatePath(path);
}

const packageJson = await readJson<PackageJson>(packageJsonPath);
const gate = await readJson<NodeRetirementGate>(gatePath);
const realm = await readJson<KeycloakRealm>(realmPath);

const requiredGates = objectArray<RequiredGate>(gate?.requiredGates);
const passkeyGate = requiredGates.find((item) => item.id === passkeyGateId);
const evidenceRefreshGate = requiredGates.find((item) => item.id === evidenceRefreshGateId);
const passkeyGateStatus = stringValue(passkeyGate?.status);
const gateStatus = stringValue(gate?.status);

if (packageJson?.scripts?.["passkey:evidence:record"] !== `node --experimental-strip-types ${recorderPath}`) {
  errors.push("package.json must expose passkey:evidence:record for the manual evidence recorder.");
}
if (packageJson?.scripts?.["passkey:evidence:prepare"] !== `node --experimental-strip-types ${preparePath}`) {
  errors.push("package.json must expose passkey:evidence:prepare for manual evidence input templates.");
}
if (packageJson?.scripts?.["passkey:evidence:readiness"] !== `node --experimental-strip-types ${readinessPath}`) {
  errors.push("package.json must expose passkey:evidence:readiness for prepared manual-run template readiness.");
}
if (packageJson?.scripts?.["passkey:evidence:live-readiness"] !== `node --experimental-strip-types ${liveReadinessPath}`) {
  errors.push("package.json must expose passkey:evidence:live-readiness for local platform readiness.");
}
if (packageJson?.scripts?.["passkey:evidence:verify"] !== `node --experimental-strip-types ${verifierPath}`) {
  errors.push("package.json must expose passkey:evidence:verify for strict artifact verification.");
}
if (packageJson?.scripts?.["passkey:evidence:preflight"] !== `node --experimental-strip-types ${preflightPath}`) {
  errors.push("package.json must expose passkey:evidence:preflight for static manual-run prerequisites.");
}

if (!passkeyGate) {
  errors.push(`Missing required gate ${passkeyGateId}.`);
} else {
  if (passkeyGateStatus !== "pending" && passkeyGateStatus !== "pass") {
    errors.push(`${passkeyGateId} must be pending before manual evidence or pass after strict manual-live-passkey evidence verification.`);
  }
  evidenceIncludes(passkeyGate, evidenceDocPath);
  evidenceIncludes(passkeyGate, preparePath);
  evidenceIncludes(passkeyGate, readinessPath);
  evidenceIncludes(passkeyGate, liveReadinessPath);
  evidenceIncludes(passkeyGate, recorderPath);
  evidenceIncludes(passkeyGate, verifierPath);
  evidenceIncludes(passkeyGate, preflightPath);
  evidenceIncludes(passkeyGate, staffPanelPath);
  evidenceIncludes(passkeyGate, staffWebAuthnSpecPath);
  evidenceIncludes(passkeyGate, prepareTestPath);
  evidenceIncludes(passkeyGate, readinessTestPath);
  evidenceIncludes(passkeyGate, liveReadinessTestPath);
  evidenceIncludes(passkeyGate, recorderTestPath);
  evidenceIncludes(passkeyGate, verifierTestPath);
  evidenceIncludes(passkeyGate, preflightTestPath);
  if (passkeyGateStatus === "pass") {
    evidenceIncludes(passkeyGate, passkeyArtifactPath);
    await validatePath(passkeyArtifactPath);
    runStrictPasskeyVerifier();
  }
}

if (!evidenceRefreshGate) {
  errors.push(`Missing required gate ${evidenceRefreshGateId}.`);
} else {
  evidenceIncludes(evidenceRefreshGate, evidenceDocPath);
  evidenceIncludes(evidenceRefreshGate, preparePath);
  evidenceIncludes(evidenceRefreshGate, readinessPath);
  evidenceIncludes(evidenceRefreshGate, liveReadinessPath);
  evidenceIncludes(evidenceRefreshGate, recorderPath);
  evidenceIncludes(evidenceRefreshGate, verifierPath);
  evidenceIncludes(evidenceRefreshGate, preflightPath);
  evidenceIncludes(evidenceRefreshGate, staffPanelPath);
  evidenceIncludes(evidenceRefreshGate, staffWebAuthnSpecPath);
  evidenceIncludes(evidenceRefreshGate, prepareTestPath);
  evidenceIncludes(evidenceRefreshGate, readinessTestPath);
  evidenceIncludes(evidenceRefreshGate, liveReadinessTestPath);
  evidenceIncludes(evidenceRefreshGate, recorderTestPath);
  evidenceIncludes(evidenceRefreshGate, verifierTestPath);
  evidenceIncludes(evidenceRefreshGate, preflightTestPath);
  if (passkeyGateStatus === "pass") {
    evidenceIncludes(evidenceRefreshGate, passkeyArtifactPath);
  }
}

if (passkeyGateStatus === "pending") {
  if (gateStatus !== "blocked") {
    errors.push("Node retirement gate must remain blocked while non-synthetic passkey evidence is pending.");
  }
  if (typeof gate?.statusReason !== "string" || !/non-synthetic passkey operations/i.test(gate.statusReason)) {
    errors.push("Node retirement gate statusReason must name non-synthetic passkey operations while passkey evidence is pending.");
  }
} else if (passkeyGateStatus === "pass") {
  if (gateStatus !== "blocked" && gateStatus !== "ready") {
    errors.push("Node retirement gate must be blocked by final review or ready after non-synthetic passkey evidence passes.");
  }
  if (gateStatus === "blocked" && (typeof gate?.statusReason !== "string" || !/final retirement review/i.test(gate.statusReason))) {
    errors.push("Node retirement gate statusReason must name final retirement review after passkey evidence passes.");
  }
}

const evidenceDoc = await readFile(evidenceDocPath, "utf8").catch(() => "");
if (passkeyGateStatus === "pass") {
  matches(evidenceDoc, /Status:\s+pass/i, "Passkey evidence doc must be Status: pass after strict manual-live-passkey evidence verification.");
  includes(evidenceDoc, passkeyArtifactPath, "Passkey evidence doc must name the generated manual-live-passkey artifact.");
  includes(evidenceDoc, "Passkey non-synthetic evidence verification: pass", "Passkey evidence doc must include the strict verifier pass result.");
} else {
  matches(evidenceDoc, /Status:\s+blocked/i, "Passkey evidence doc must keep Status: blocked before manual-live-passkey evidence is recorded.");
}
matches(evidenceDoc, /not non-synthetic passkey evidence/i, "Passkey evidence doc must explicitly reject virtual-authenticator smoke as non-synthetic evidence.");
includes(evidenceDoc, "manual-live-passkey", "Passkey evidence doc must name manual-live-passkey as the future evidence kind.");
includes(evidenceDoc, "npm run passkey:evidence:prepare", "Passkey evidence doc must describe the template preparation command.");
includes(evidenceDoc, "npm run passkey:evidence:readiness", "Passkey evidence doc must describe the manual readiness command.");
includes(evidenceDoc, "npm run passkey:evidence:live-readiness", "Passkey evidence doc must describe the live platform readiness command.");
includes(evidenceDoc, "npm run passkey:evidence:record", "Passkey evidence doc must keep the recorder command in the manual runbook.");
includes(evidenceDoc, "npm run passkey:evidence:preflight", "Passkey evidence doc must describe the preflight command.");
includes(evidenceDoc, "NEXT_PUBLIC_BANKING_SIMULATOR_TOKENS_ENABLED=false", "Passkey evidence doc must disable staff-terminal simulator-token smoke calls for the manual ceremony.");
includes(evidenceDoc, "simulator token smoke disabled", "Passkey evidence doc must require the staff terminal simulator-token disabled status.");
for (const envName of [
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
  "BANKING_LAB_PASSKEY_REDACTION_CONFIRMED=true"
]) {
  includes(evidenceDoc, envName, `Passkey evidence doc must require ${envName}.`);
}
for (const panelMarker of [
  "Keycloak WebAuthn manager loaded",
  "manager-webauthn01",
  "SYN-CUS-001",
  "masked phone output",
  "AUD-"
]) {
  includes(evidenceDoc, panelMarker, `Passkey evidence doc must require staff panel marker ${panelMarker}.`);
}
for (const commandMarker of [
  "docker compose --profile platform up",
  "BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false",
  ".well-known/openid-configuration",
  "/health",
  "real platform authenticator",
  "npm run passkey:evidence:record",
  "\"status\": \"pass\"",
  "\"exitCode\": 0",
  "duplicate commands and failed extra commands are rejected"
]) {
  includes(evidenceDoc, commandMarker, `Passkey evidence doc must require command evidence marker ${commandMarker}.`);
}

const recorder = await readFile(recorderPath, "utf8").catch(() => "");
const prepare = await readFile(preparePath, "utf8").catch(() => "");
const readiness = await readFile(readinessPath, "utf8").catch(() => "");
const liveReadiness = await readFile(liveReadinessPath, "utf8").catch(() => "");
for (const prepareMarker of [
  "redacted-commands.template.json",
  "redacted-staff-panel.template.txt",
  "record-command.template.sh",
  "TODO_REPLACE_WITH_pass",
  "TODO_REPLACE_WITH_0",
  "BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false",
  "BANKING_LAB_PASSKEY_BROWSER_AUTOMATION=ordinary-browser-no-virtual-authenticator",
  "BANKING_LAB_PASSKEY_OPERATOR_CONFIRMATION=real-platform-or-hardware-authenticator-used",
  "Keycloak WebAuthn manager loaded",
  "manager-webauthn01",
  "010-****-1001",
  "AUD-"
]) {
  includes(prepare, prepareMarker, `Passkey prepare script must keep template marker ${prepareMarker}.`);
}

for (const readinessMarker of [
  "redacted-commands.template.json",
  "redacted-staff-panel.template.txt",
  "record-command.template.sh",
  "COMPOSE_PROJECT_NAME=banking-lab-passkey-manual",
  "BANKING_LAB_CORE_BANKING_PORT=18126",
  "BANKING_LAB_KEYCLOAK_PORT=18127",
  "BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false",
  "BANKING_LAB_PASSKEY_BROWSER_ORIGIN=http://localhost:3002",
  "BANKING_LAB_PASSKEY_KEYCLOAK_ISSUER=http://localhost:18127/realms/banking-lab",
  "TODO_REPLACE_WITH_pass",
  "TODO_REPLACE_WITH_0",
  "Run npm run passkey:evidence:prepare before readiness",
  "does not prove non-synthetic passkey operations"
]) {
  includes(readiness, readinessMarker, `Passkey readiness script must keep validation marker ${readinessMarker}.`);
}

for (const liveReadinessMarker of [
  "BANKING_LAB_PASSKEY_LIVE_KEYCLOAK_BASE_URL",
  "BANKING_LAB_PASSKEY_LIVE_CORE_BASE_URL",
  "http://localhost:18127",
  "http://127.0.0.1:18126",
  ".well-known/openid-configuration",
  "protocol/openid-connect/certs",
  "BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false",
  "syntheticOnly must be true",
  "auditHashChainValid must be true",
  "does not prove non-synthetic passkey operations"
]) {
  includes(liveReadiness, liveReadinessMarker, `Passkey live readiness script must keep validation marker ${liveReadinessMarker}.`);
}

for (const recorderMarker of [
  "BANKING_LAB_PASSKEY_USED_BROWSER_VIRTUAL_AUTHENTICATOR",
  "BANKING_LAB_PASSKEY_USED_PLAYWRIGHT_CDP_WEBAUTHN",
  "BANKING_LAB_PASSKEY_SIMULATOR_TOKENS_ENABLED",
  "BANKING_LAB_PASSKEY_REDACTION_CONFIRMED",
  "parseManualCeremony",
  "BANKING_LAB_PASSKEY_BROWSER_ORIGIN",
  "BANKING_LAB_PASSKEY_KEYCLOAK_ISSUER",
  "BANKING_LAB_PASSKEY_RP_ID",
  "BANKING_LAB_PASSKEY_USERNAME",
  "BANKING_LAB_PASSKEY_AUTHORIZATION_FLOW",
  "BANKING_LAB_PASSKEY_BROWSER_AUTOMATION",
  "BANKING_LAB_PASSKEY_OPERATOR_CONFIRMATION",
  "ordinary-browser-no-virtual-authenticator",
  "real-platform-or-hardware-authenticator-used",
  "manual-live-passkey",
  "manager-webauthn01",
  "maskedPiiObserved",
  "AUD-",
  "assertNoReusableSecrets",
  "assertRequiredPasskeyCommandEvidence",
  "status must be pass",
  "exitCode must be 0",
  "duplicate command",
  "docker compose --profile platform up",
  "openid-configuration",
  "/health"
]) {
  includes(recorder, recorderMarker, `Passkey recorder must keep validation marker ${recorderMarker}.`);
}
matches(recorder, /maskedPiiObserved:\s*\/010-\\\*\{4\}-1001\//, "Passkey recorder must validate the masked synthetic phone value.");
matches(recorder, /010-0000-1001/, "Passkey recorder must reject the unmasked synthetic phone value.");

const verifier = await readFile(verifierPath, "utf8").catch(() => "");
for (const verifierMarker of [
  "BANKING_LAB_PASSKEY_EVIDENCE_ARTIFACT",
  "manual-live-passkey",
  "hardware-security-key",
  "manualCeremony",
  "browserOrigin",
  "keycloakIssuer",
  "authorization-code-pkce",
  "ordinary-browser-no-virtual-authenticator",
  "real-platform-or-hardware-authenticator-used",
  "usedBrowserVirtualAuthenticator",
  "usedPlaywrightCdpWebAuthn",
  "simulatorTokensEnabled",
  "maskedPiiObserved",
  "auditEventObserved",
  "assertNoReusableSecrets",
  "status must be pass",
  "exitCode must be 0",
  "duplicate command",
  "010-0000-1001",
  "docker compose --profile platform up",
  "openid-configuration",
  "/health"
]) {
  includes(verifier, verifierMarker, `Passkey verifier must keep validation marker ${verifierMarker}.`);
}

const requiredActions = objectArray<KeycloakRequiredAction>(realm?.requiredActions);
const webAuthnAction = requiredActions.find((item) => item.alias === "webauthn-register");
if (!webAuthnAction) {
  errors.push("Keycloak realm must define the webauthn-register required action.");
} else {
  if (webAuthnAction.providerId !== "webauthn-register") {
    errors.push("Keycloak webauthn-register action must use providerId webauthn-register.");
  }
  if (webAuthnAction.enabled !== true) {
    errors.push("Keycloak webauthn-register action must be enabled.");
  }
}
if (realm?.webAuthnPolicyRpId !== "localhost") {
  errors.push("Keycloak WebAuthn RP ID must remain localhost for the documented local manual run.");
}
if (realm?.webAuthnPolicyUserVerificationRequirement !== "required") {
  errors.push("Keycloak WebAuthn policy must require user verification.");
}
if (!Array.isArray(realm?.webAuthnPolicyAcceptableAaguids) || realm.webAuthnPolicyAcceptableAaguids.length !== 0) {
  errors.push("Keycloak WebAuthn acceptable AAGUIDs must stay empty for local platform/hardware-key compatibility.");
}

const users = objectArray<KeycloakUser>(realm?.users);
function findUser(username: string): KeycloakUser | undefined {
  return users.find((item) => item.username === username);
}

const managerWebAuthn = findUser("manager-webauthn01");
if (!managerWebAuthn) {
  errors.push("Keycloak realm must include manager-webauthn01.");
} else {
  if (managerWebAuthn.enabled !== true) errors.push("manager-webauthn01 must be enabled.");
  if (!stringArray(managerWebAuthn.requiredActions).includes("webauthn-register")) {
    errors.push("manager-webauthn01 must require webauthn-register.");
  }
  if (!stringArray(managerWebAuthn.realmRoles).includes("BRANCH_MANAGER")) {
    errors.push("manager-webauthn01 must have BRANCH_MANAGER for staff lookup parity.");
  }
}

const blockedWebAuthn = findUser("manager-webauthn-block01");
if (!blockedWebAuthn || !stringArray(blockedWebAuthn.requiredActions).includes("webauthn-register")) {
  errors.push("manager-webauthn-block01 must keep webauthn-register for direct-grant blocking evidence.");
}

const securityAdmin = findUser("security-admin01");
const securityAdminRoles = stringArray(securityAdmin?.realmRoles);
if (!securityAdmin) {
  errors.push("Keycloak realm must include security-admin01 for passkey recovery segregation.");
} else {
  for (const requiredRole of ["COMPLIANCE_MANAGER", "AUDITOR", "PASSKEY_RECOVERY_ADMIN"]) {
    if (!securityAdminRoles.includes(requiredRole)) {
      errors.push(`security-admin01 must keep ${requiredRole}.`);
    }
  }
  for (const forbiddenRole of ["CUSTOMER", "BRANCH_STAFF", "BRANCH_MANAGER"]) {
    if (securityAdminRoles.includes(forbiddenRole)) {
      errors.push(`security-admin01 must not have ${forbiddenRole}.`);
    }
  }
}

const staffPanel = await readFile(staffPanelPath, "utf8").catch(() => "");
for (const staffMarker of [
  "IntegratedTerminalApp",
  "TerminalHeader",
  "StatusBar",
  "SideDrawer",
  "RightRail",
  "DepositNavigationScreen",
  "FeeInquiryScreen",
  "InheritanceScreen",
  "onMenuSelect",
  "navigateToMenu"
]) {
  includes(staffPanel, staffMarker, `Staff terminal must keep integrated-terminal evidence marker ${staffMarker}.`);
}

const staffWebAuthnSpec = await readFile(staffWebAuthnSpecPath, "utf8").catch(() => "");
for (const integratedSpecMarker of [
  "iWorks integrated terminal",
  "terminal-status",
  "통합단말 프로토타입",
  "업무 모듈",
  "IT 기기장애",
  "즐겨찾기",
  "번호선택"
]) {
  includes(staffWebAuthnSpec, integratedSpecMarker, `Staff integrated terminal Playwright smoke must keep marker ${integratedSpecMarker}.`);
}

if (errors.length > 0) {
  console.error("Passkey non-synthetic evidence preflight: failed");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log("Passkey non-synthetic evidence preflight: pass");
console.log("Static runbook, recorder, Keycloak realm, and staff terminal prerequisites are present.");
if (passkeyGateStatus === "pass") {
  console.log("Manual-live-passkey evidence artifact is recorded and strict verification passed.");
} else {
  console.log("This does not prove non-synthetic passkey operations; keep the gate pending until manual-live-passkey evidence is recorded.");
}
