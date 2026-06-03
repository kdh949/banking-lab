import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

type AuthenticatorKind = "platform" | "hardware-security-key";

type CommandEvidenceTemplate = {
  command: string;
  status: "TODO_REPLACE_WITH_pass";
  exitCode: "TODO_REPLACE_WITH_0";
  summary: string;
};

const outputDir = process.env.BANKING_LAB_PASSKEY_TEMPLATE_DIR ?? "tmp/passkey-evidence-manual";
const testDate = process.env.BANKING_LAB_PASSKEY_TEST_DATE ?? new Date().toISOString().slice(0, 10);
const authenticatorKind = parseAuthenticatorKind(process.env.BANKING_LAB_PASSKEY_AUTHENTICATOR_KIND ?? "platform");
const browserOrigin = "http://localhost:3002";
const keycloakIssuer = "http://localhost:18127/realms/banking-lab";
const commandsPath = path.join(outputDir, "redacted-commands.template.json");
const panelSnapshotPath = path.join(outputDir, "redacted-staff-panel.template.txt");
const recordCommandPath = path.join(outputDir, "record-command.template.sh");

function parseAuthenticatorKind(value: string): AuthenticatorKind {
  if (value === "platform" || value === "hardware-security-key") {
    return value;
  }
  throw new Error("BANKING_LAB_PASSKEY_AUTHENTICATOR_KIND must be platform or hardware-security-key.");
}

function commandTemplate(command: string, summary: string): CommandEvidenceTemplate {
  return {
    command,
    status: "TODO_REPLACE_WITH_pass",
    exitCode: "TODO_REPLACE_WITH_0",
    summary
  };
}

function authenticatorSummary(kind: AuthenticatorKind): string {
  return kind === "hardware-security-key"
    ? "Operator completed Keycloak WebAuthn required action using a real hardware security key."
    : "Operator completed Keycloak WebAuthn required action using a real platform authenticator.";
}

const composeCommand = [
  "env",
  "COMPOSE_PROJECT_NAME=banking-lab-passkey-manual",
  "BANKING_LAB_POSTGRES_PORT=15477",
  "BANKING_LAB_CORE_BANKING_PORT=18126",
  "BANKING_LAB_KEYCLOAK_PORT=18127",
  "BANKING_LAB_SECURITY_ENABLED=true",
  "BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false",
  "BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs",
  `BANKING_LAB_SECURITY_ISSUER=${keycloakIssuer}`,
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
].join(" ");

const commands: CommandEvidenceTemplate[] = [
  commandTemplate(composeCommand, "Replace this summary after the Compose platform stack starts with simulator tokens disabled."),
  commandTemplate(
    "curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://localhost:18127/realms/banking-lab/.well-known/openid-configuration",
    "Replace this summary after Keycloak OIDC discovery returns successfully."
  ),
  commandTemplate(
    "curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18126/health",
    "Replace this summary after Spring health returns successfully."
  ),
  commandTemplate(
    `manual browser sign-in completed with a real ${authenticatorKind === "hardware-security-key" ? "hardware security key" : "platform authenticator"}`,
    authenticatorSummary(authenticatorKind)
  ),
  commandTemplate(
    "npm run passkey:evidence:record",
    "Replace this summary after the passkey evidence recorder writes the redacted artifact."
  )
];

const panelSnapshot = [
  "Keycloak WebAuthn manager loaded",
  "manager-webauthn01",
  "Bearer",
  "SYN-CUS-001",
  "010-****-1001",
  "[replace with the redacted AUD-... audit event id from the staff panel]"
].join("\n");

const recordCommand = `env BANKING_LAB_PASSKEY_EVIDENCE_CONFIRMED=true \\
  BANKING_LAB_PASSKEY_AUTHENTICATOR_KIND=${authenticatorKind} \\
  BANKING_LAB_PASSKEY_TEST_DATE=${testDate} \\
  BANKING_LAB_PASSKEY_BROWSER_ORIGIN=${browserOrigin} \\
  BANKING_LAB_PASSKEY_KEYCLOAK_ISSUER=${keycloakIssuer} \\
  BANKING_LAB_PASSKEY_RP_ID=localhost \\
  BANKING_LAB_PASSKEY_USERNAME=manager-webauthn01 \\
  BANKING_LAB_PASSKEY_AUTHORIZATION_FLOW=authorization-code-pkce \\
  BANKING_LAB_PASSKEY_BROWSER_AUTOMATION=ordinary-browser-no-virtual-authenticator \\
  BANKING_LAB_PASSKEY_OPERATOR_CONFIRMATION=real-platform-or-hardware-authenticator-used \\
  BANKING_LAB_PASSKEY_USED_BROWSER_VIRTUAL_AUTHENTICATOR=false \\
  BANKING_LAB_PASSKEY_USED_PLAYWRIGHT_CDP_WEBAUTHN=false \\
  BANKING_LAB_PASSKEY_SIMULATOR_TOKENS_ENABLED=false \\
  BANKING_LAB_PASSKEY_KEYCLOAK_REQUIRED_ACTION_COMPLETED=true \\
  BANKING_LAB_PASSKEY_SPRING_SIGNED_TOKEN_ACCEPTED=true \\
  BANKING_LAB_PASSKEY_SYNTHETIC_ONLY=true \\
  BANKING_LAB_PASSKEY_REDACTION_CONFIRMED=true \\
  BANKING_LAB_PASSKEY_COMMANDS_FILE=${commandsPath} \\
  BANKING_LAB_PASSKEY_PANEL_SNAPSHOT_FILE=${panelSnapshotPath} \\
  npm run passkey:evidence:record
`;

async function main(): Promise<void> {
  await mkdir(outputDir, { recursive: true });
  await writeFile(commandsPath, `${JSON.stringify(commands, null, 2)}\n`);
  await writeFile(panelSnapshotPath, `${panelSnapshot}\n`);
  await writeFile(recordCommandPath, recordCommand);

  console.log("Passkey non-synthetic evidence templates prepared.");
  console.log(`Commands template: ${commandsPath}`);
  console.log(`Staff panel template: ${panelSnapshotPath}`);
  console.log(`Recorder command template: ${recordCommandPath}`);
  console.log("Templates contain TODO values and do not prove non-synthetic passkey operations.");
}

main().catch((error: unknown) => {
  console.error(`Passkey non-synthetic evidence template preparation failed: ${(error as Error).message}`);
  process.exit(1);
});
