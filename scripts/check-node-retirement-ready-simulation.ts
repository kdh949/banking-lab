import { mkdtemp, readFile, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawnSync } from "node:child_process";

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

const sourceGatePath = "docs/migration/node-retirement-gate.json";
const passkeyArtifactName = "passkey-non-synthetic-evidence.json";
const finalReviewArtifactName = "final-node-retirement-review.json";

const requiredFinalReviewCommands = [
  "npm run parity",
  "npm test",
  "npm run validate:manifests",
  "npm run evidence:pack",
  "npm run retirement:audit",
  "npm run retirement:stack-audit",
  "npm run retirement:generated-boundary",
  "npm run passkey:evidence:verify"
];

const controlAttestations = {
  ledgerBalancedPostings: true,
  balancesProjectedFromPostings: true,
  idempotentExternalCommands: true,
  appendOnlyFinalizedTransactions: true,
  reasonRequiredAudit: true,
  piiMaskedByDefault: true,
  makerCheckerSeparation: true,
  workflowDurability: true,
  outboxDurability: true,
  reconciliationAdjustmentsBalanced: true,
  targetAreasNoDisallowedStack: true,
  generatedArtifactsIgnored: true,
  syntheticOnly: true,
  nodeOnlyCriticalDependencyRemoved: true
};

function objectArray<T>(value: unknown): T[] {
  return Array.isArray(value) ? value.filter((item): item is T => typeof item === "object" && item !== null) : [];
}

function readyGate(source: NodeRetirementGate): NodeRetirementGate {
  const requiredGates = objectArray<RequiredGate>(source.requiredGates).map((gate) => ({
    ...gate,
    status: "pass"
  }));
  return {
    ...source,
    status: "ready",
    statusReason: "All required retirement gates have passing evidence in this ready-state simulation.",
    requiredGates
  };
}

function passkeyArtifact() {
  return {
    schemaVersion: 1,
    status: "pass",
    testDate: "2026-06-03",
    evidenceKind: "manual-live-passkey",
    authenticatorKind: "platform",
    usedBrowserVirtualAuthenticator: false,
    usedPlaywrightCdpWebAuthn: false,
    simulatorTokensEnabled: false,
    keycloakRequiredActionCompleted: true,
    springSignedTokenAccepted: true,
    syntheticOnly: true,
    redactionConfirmed: true,
    commands: [
      "env COMPOSE_PROJECT_NAME=banking-lab-passkey-manual BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false docker compose --profile platform up -d --build postgres keycloak core-banking",
      "curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://localhost:18127/realms/banking-lab/.well-known/openid-configuration",
      "curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18126/health",
      "manual browser sign-in completed with a real platform authenticator",
      "npm run passkey:evidence:record"
    ],
    staffPanelAssertions: {
      webAuthnLoaded: true,
      managerSubjectObserved: true,
      bearerTokenTypeObserved: true,
      syntheticCustomerObserved: true,
      maskedPiiObserved: true,
      auditEventObserved: true
    }
  };
}

function finalReviewArtifact() {
  return {
    schemaVersion: 1,
    status: "pass",
    reviewDate: "2026-06-03",
    evidenceKind: "final-node-retirement-review",
    passkeyEvidenceArtifact: "docs/test-evidence/generated/passkey-non-synthetic-evidence.json",
    reviewer: "ready-state-simulation",
    commands: requiredFinalReviewCommands.map((command) => ({
      command,
      status: "pass",
      exitCode: 0,
      runAfterPasskeyEvidence: true,
      summary: `${command} passed after passkey evidence was verified.`
    })),
    controlAttestations,
    remainingBlockers: []
  };
}

async function main(): Promise<void> {
  const tmp = await mkdtemp(join(tmpdir(), "banking-lab-node-retirement-ready-"));
  const gatePath = join(tmp, "node-retirement-gate.ready.json");
  const passkeyPath = join(tmp, passkeyArtifactName);
  const finalReviewPath = join(tmp, finalReviewArtifactName);
  const sourceGate = JSON.parse(await readFile(sourceGatePath, "utf8")) as NodeRetirementGate;

  await writeFile(gatePath, `${JSON.stringify(readyGate(sourceGate), null, 2)}\n`);
  await writeFile(passkeyPath, `${JSON.stringify(passkeyArtifact(), null, 2)}\n`);
  await writeFile(finalReviewPath, `${JSON.stringify(finalReviewArtifact(), null, 2)}\n`);

  const result = spawnSync(process.execPath, ["--experimental-strip-types", "scripts/check-node-retirement-gate.ts"], {
    encoding: "utf8",
    env: {
      ...process.env,
      BANKING_LAB_NODE_RETIREMENT_GATE_PATH: gatePath,
      BANKING_LAB_PASSKEY_EVIDENCE_ARTIFACT: passkeyPath,
      BANKING_LAB_FINAL_REVIEW_ARTIFACT: finalReviewPath
    },
    maxBuffer: 1024 * 1024 * 8
  });
  const output = [result.stdout, result.stderr].filter(Boolean).join("\n");
  if (result.status !== 0 || !output.includes("Node reference retirement gate: ready")) {
    console.error("Node retirement ready-state simulation: failed");
    if (output.trim().length > 0) {
      console.error(output.trim());
    }
    process.exit(1);
  }

  console.log("Node retirement ready-state simulation: pass");
  console.log("Fixture gate and redacted synthetic evidence artifacts can satisfy the ready gate path.");
}

main().catch((error: unknown) => {
  console.error(`Node retirement ready-state simulation failed: ${(error as Error).message}`);
  process.exit(1);
});
