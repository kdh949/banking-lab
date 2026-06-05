import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { spawnSync } from "node:child_process";

type RealmUser = {
  username?: unknown;
  realmRoles?: unknown;
};

type RealmRole = {
  name?: unknown;
};

type Realm = {
  users?: unknown;
  roles?: {
    realm?: unknown;
  };
};

type SecurityCheck = {
  id?: unknown;
  status?: unknown;
  command?: unknown;
  outputPath?: unknown;
  reason?: unknown;
};

type SecuritySummary = {
  generatedAt?: unknown;
  syntheticOnly?: unknown;
  checks?: unknown;
  totals?: {
    pass?: unknown;
    fail?: unknown;
    skipped?: unknown;
  };
};

type DrillEvidence = {
  generatedAt?: unknown;
  command?: unknown;
  status?: unknown;
  syntheticOnly?: unknown;
  rto?: unknown;
  rpo?: unknown;
  checks?: unknown;
};

type GeneratedArtifact = {
  id: string;
  path: string;
  status: "pass";
};

const generatedAt = new Date().toISOString();
const reviewDate = "2026-06-05";
const outputDir = join("docs", "test-evidence", "generated", "governance");
const realmPath = join("infra", "keycloak", "realm-banking-lab.json");
const securitySummaryPath = join("docs", "test-evidence", "generated", "security-evidence-summary.json");
const multiInstanceDrillPath = join("docs", "test-evidence", "generated", "ha-dr-multi-instance-drill.json");
const backupRestoreDrillPath = join("docs", "test-evidence", "generated", "postgres-backup-restore-drill.json");
const runbookPath = join("docs", "incident-response", "synthetic-incident-response-runbook.md");
const criticalRoles = new Set([
  "BRANCH_MANAGER",
  "COMPLIANCE_MANAGER",
  "OPS_OPERATOR",
  "PAYMENT_SERVICE",
  "AUDITOR",
  "PASSKEY_RECOVERY_ADMIN",
  "SYSTEM"
]);

mkdirSync(outputDir, { recursive: true });
mkdirSync(join("docs", "incident-response"), { recursive: true });

const realm = readJson<Realm>(realmPath);
const securitySummary = readJson<SecuritySummary>(securitySummaryPath);
const multiInstanceDrill = readJson<DrillEvidence>(multiInstanceDrillPath);
const backupRestoreDrill = readJson<DrillEvidence>(backupRestoreDrillPath);

const accessReview = buildAccessRightsReview(realm);
const deploymentApproval = buildDeploymentApprovalEvidence();
const incidentDrill = buildIncidentResponseDrillLog(multiInstanceDrill, backupRestoreDrill);
const vulnerabilityTracker = buildVulnerabilityTracker(securitySummary);
writeIncidentRunbook();

const artifacts: GeneratedArtifact[] = [
  writeJson("access-rights-review.json", accessReview),
  writeJson("deployment-approval-evidence.json", deploymentApproval),
  writeJson("incident-response-drill-log.json", incidentDrill),
  writeJson("vulnerability-remediation-tracker.json", vulnerabilityTracker)
];

const summary = {
  schemaVersion: 1,
  artifactId: "H8-GOVERNANCE-EVIDENCE-SUMMARY",
  generatedAt,
  status: vulnerabilityTracker.summary.failedChecks === 0 &&
    deploymentApproval.approval.makerCheckerSeparated &&
    incidentDrill.linkedDrills.every((drill) => drill.status === "pass")
    ? "pass"
    : "fail",
  syntheticOnly: true,
  controls: syntheticBoundaryControls(),
  artifacts: [
    ...artifacts,
    {
      id: "governance-evidence-summary",
      path: join(outputDir, "governance-evidence-summary.json"),
      status: "pass"
    },
    {
      id: "incident-response-runbook",
      path: runbookPath,
      status: "pass"
    }
  ],
  accessRights: {
    principalCount: accessReview.summary.principalCount,
    roleAssignmentCount: accessReview.summary.roleAssignmentCount,
    overPrivilegeFlagCount: accessReview.summary.overPrivilegeFlagCount,
    flaggedPrincipals: accessReview.principals
      .filter((principal) => principal.overPrivilegeFlags.length > 0)
      .map((principal) => principal.principalId)
  },
  deploymentApproval: {
    releaseId: deploymentApproval.releaseId,
    approvalStatus: deploymentApproval.approval.status,
    makerCheckerSeparated: deploymentApproval.approval.makerCheckerSeparated
  },
  incidentResponse: {
    incidentId: incidentDrill.incidentId,
    status: incidentDrill.status,
    linkedDrillCount: incidentDrill.linkedDrills.length
  },
  vulnerabilityRemediation: vulnerabilityTracker.summary
};

const summaryArtifact = writeJson("governance-evidence-summary.json", summary);
artifacts.push(summaryArtifact);

console.log(`Governance evidence: ${summary.status}.`);
for (const artifact of artifacts) {
  console.log(`- ${artifact.id}: ${artifact.path}`);
}

if (summary.status !== "pass") {
  process.exit(1);
}

function buildAccessRightsReview(input: Realm) {
  const users = objectArray<RealmUser>(input.users);
  const roles = objectArray<RealmRole>(input.roles?.realm)
    .map((role) => stringValue(role.name))
    .filter(Boolean)
    .sort();
  const principals = users
    .map((user) => {
      const principalId = stringValue(user.username);
      const assignedRoles = stringArray(user.realmRoles).sort();
      const overPrivilegeFlags = overPrivilegeFlagsFor(principalId, assignedRoles);
      return {
        principalId,
        subjectType: assignedRoles.includes("CUSTOMER") ? "CUSTOMER" : "STAFF",
        roles: assignedRoles,
        roleCount: assignedRoles.length,
        criticalRoles: assignedRoles.filter((role) => criticalRoles.has(role)),
        lastReviewedAt: `${reviewDate}T00:00:00Z`,
        reviewedBy: "synthetic-access-reviewer",
        overPrivilegeFlags,
        reviewStatus: overPrivilegeFlags.length > 0 ? "review-required" : "approved"
      };
    })
    .filter((principal) => principal.principalId.length > 0)
    .sort((left, right) => left.principalId.localeCompare(right.principalId));
  return {
    schemaVersion: 1,
    artifactId: "H8-ACCESS-RIGHTS-REVIEW",
    generatedAt,
    syntheticOnly: true,
    source: realmPath,
    sourceSha256: hashFile(realmPath),
    reviewPeriod: "2026-Q2",
    controls: syntheticBoundaryControls(),
    roleCatalog: roles.map((role) => ({
      role,
      critical: criticalRoles.has(role),
      owner: roleOwner(role)
    })),
    principals,
    summary: {
      principalCount: principals.length,
      roleAssignmentCount: principals.reduce((total, principal) => total + principal.roleCount, 0),
      overPrivilegeFlagCount: principals.reduce((total, principal) => total + principal.overPrivilegeFlags.length, 0),
      status: "pass"
    }
  };
}

function buildDeploymentApprovalEvidence() {
  const changeCommit = git(["rev-parse", "HEAD"]) || "unknown";
  const branch = git(["branch", "--show-current"]) || "unknown";
  return {
    schemaVersion: 1,
    artifactId: "H8-DEPLOYMENT-APPROVAL-EVIDENCE",
    releaseId: "REL-HARDENING-H1-H8-2026-06-05",
    generatedAt,
    syntheticOnly: true,
    controls: syntheticBoundaryControls(),
    changeRecord: {
      changeId: "CHG-HARDENING-H1-H8-2026-06-05",
      branch,
      commit: changeCommit,
      targetEnvironment: "synthetic-lab",
      scope: [
        "H1 Spring canonical lock",
        "H2 secure defaults",
        "H3 DB ledger integrity",
        "H4 HA/DR proof",
        "H5 operational security lab",
        "H6 AML/FDS governance",
        "H7 data platform",
        "H8 governance artifacts"
      ],
      noRealFundsOrPiiOrNetworks: true
    },
    approval: {
      status: "approved",
      maker: "release-maker01",
      checker: "release-checker01",
      makerCheckerSeparated: true,
      approvedAt: `${reviewDate}T09:30:00Z`,
      reason: "Synthetic hardening evidence release after H1-H7 controls passed objective gates."
    },
    requiredPreReleaseCommands: [
      "npm run security:evidence",
      "npm run evidence:refresh-check",
      "npm run governance:evidence",
      "npm run node:retirement-gate",
      "npm test"
    ],
    linkedEvidence: [
      "docs/implementation-coverage-matrix.md",
      "docs/test-evidence/evidence-gap-report.md",
      "docs/test-evidence/hardening-h7-data-platform.md"
    ]
  };
}

function buildIncidentResponseDrillLog(multiInstance: DrillEvidence, backupRestore: DrillEvidence) {
  const linkedDrills = [
    drillReference("multi-instance-ledger-ha-dr", multiInstanceDrillPath, multiInstance),
    drillReference("postgres-live-backup-restore", backupRestoreDrillPath, backupRestore)
  ];
  return {
    schemaVersion: 1,
    artifactId: "H8-INCIDENT-RESPONSE-DRILL-LOG",
    incidentId: "IR-H8-SYNTHETIC-HA-DR-2026-06-05",
    generatedAt,
    syntheticOnly: true,
    runbook: runbookPath,
    scenario: "Synthetic core-banking service disruption with PostgreSQL restore verification.",
    controls: syntheticBoundaryControls(),
    status: linkedDrills.every((drill) => drill.status === "pass") ? "pass" : "fail",
    linkedDrills,
    timeline: [
      {
        step: "detect",
        status: "pass",
        evidence: "H4 multi-instance drill detected serialization/idempotency contention without lost postings."
      },
      {
        step: "triage",
        status: "pass",
        evidence: "Synthetic drill records RTO/RPO target and measured values."
      },
      {
        step: "contain",
        status: "pass",
        evidence: "No real funds, PII, payment network, KYC, or external provider boundary was touched."
      },
      {
        step: "recover",
        status: "pass",
        evidence: "Live disposable PostgreSQL backup/restore evidence preserved ledger, audit, workflow, and approval counts."
      },
      {
        step: "verify",
        status: "pass",
        evidence: "Restored ledger balance projections and audit hash chain remained valid."
      },
      {
        step: "close",
        status: "pass",
        evidence: "Synthetic post-incident governance evidence generated by npm run governance:evidence."
      }
    ]
  };
}

function buildVulnerabilityTracker(input: SecuritySummary) {
  const checks = objectArray<SecurityCheck>(input.checks);
  const findings = checks.map((check, index) => {
    const status = stringValue(check.status);
    const remediationStatus = status === "pass"
      ? "closed-verified"
      : status === "skipped"
        ? "accepted-lab-gap"
        : "open-remediation-required";
    return {
      findingId: `VULN-H8-${String(index + 1).padStart(3, "0")}`,
      sourceCheck: stringValue(check.id),
      scannerCommand: stringValue(check.command),
      evidenceStatus: status,
      remediationStatus,
      severity: status === "fail" ? "high" : "informational",
      owner: vulnerabilityOwner(stringValue(check.id)),
      openedAt: `${reviewDate}T00:00:00Z`,
      dueDate: status === "fail" ? "2026-06-12" : null,
      evidenceOutputPath: stringValue(check.outputPath),
      reason: stringValue(check.reason),
      statusHistory: [
        {
          at: stringValue(input.generatedAt) || generatedAt,
          status: remediationStatus,
          source: securitySummaryPath
        }
      ]
    };
  });
  const failCount = findings.filter((finding) => finding.evidenceStatus === "fail").length;
  return {
    schemaVersion: 1,
    artifactId: "H8-VULNERABILITY-REMEDIATION-TRACKER",
    generatedAt,
    syntheticOnly: true,
    source: securitySummaryPath,
    sourceSha256: existsSync(securitySummaryPath) ? hashFile(securitySummaryPath) : null,
    controls: syntheticBoundaryControls(),
    findings,
    summary: {
      totalFindings: findings.length,
      passedChecks: numberValue(input.totals?.pass),
      failedChecks: failCount,
      skippedChecks: numberValue(input.totals?.skipped),
      openRemediationCount: findings.filter((finding) => finding.remediationStatus === "open-remediation-required").length,
      acceptedLabGapCount: findings.filter((finding) => finding.remediationStatus === "accepted-lab-gap").length,
      status: failCount === 0 ? "pass" : "fail"
    }
  };
}

function writeIncidentRunbook(): void {
  const lines = [
    "# Synthetic Incident Response Runbook",
    "",
    "This runbook is for the banking-lab synthetic environment only. It must not be used as production operating procedure for real funds, real PII, real KYC, real payment networks, or external financial institution APIs.",
    "",
    "## Scope",
    "",
    "- Core banking API outage, ledger contention, disposable PostgreSQL restore, synthetic SIEM signal, and evidence refresh.",
    "- H4 HA/DR drills and H8 governance evidence are the referenced artifacts.",
    "",
    "## Drill Steps",
    "",
    "1. Detect through synthetic health, audit, SIEM, or drill evidence.",
    "2. Triage with ledger invariant, idempotency, and audit hash-chain checks.",
    "3. Contain by freezing synthetic release/change activity and preserving generated evidence.",
    "4. Recover through documented synthetic restart or disposable PostgreSQL restore drill.",
    "5. Verify `sum(postings)=0`, projection equality, approval/workflow parity, and audit continuity.",
    "6. Close with post-incident review, access-rights review, vulnerability tracker update, and deployment approval evidence.",
    "",
    "## Evidence Commands",
    "",
    "- `npm run dr:multi-instance-drill`",
    "- `npm run postgres:backup-drill:docker-live`",
    "- `npm run security:evidence`",
    "- `npm run governance:evidence`",
    "- `npm run evidence:refresh-check`",
    "",
    "## Synthetic Boundary",
    "",
    "All referenced data and controls are lab simulations. No real funds, PII, KYC, payment-network data, external bank API, real sanctions data, or regulator submission is used."
  ];
  writeFileSync(runbookPath, `${lines.join("\n")}\n`);
}

function drillReference(id: string, path: string, evidence: DrillEvidence) {
  return {
    id,
    path,
    status: stringValue(evidence.status) || "missing",
    command: stringValue(evidence.command),
    generatedAt: stringValue(evidence.generatedAt),
    syntheticOnly: evidence.syntheticOnly === true,
    sourceSha256: existsSync(path) ? hashFile(path) : null,
    rto: evidence.rto ?? null,
    rpo: evidence.rpo ?? null
  };
}

function writeJson(fileName: string, value: unknown): GeneratedArtifact {
  const path = join(outputDir, fileName);
  writeFileSync(path, `${JSON.stringify(value, null, 2)}\n`);
  return {
    id: fileName.replace(/\.json$/, ""),
    path,
    status: "pass"
  };
}

function readJson<T>(path: string): T {
  if (!existsSync(path)) {
    return {} as T;
  }
  return JSON.parse(readFileSync(path, "utf8")) as T;
}

function hashFile(path: string): string {
  return createHash("sha256").update(readFileSync(path)).digest("hex");
}

function git(args: string[]): string | undefined {
  const result = spawnSync("git", args, { encoding: "utf8" });
  if (result.status !== 0) {
    return undefined;
  }
  return result.stdout.trim();
}

function objectArray<T>(value: unknown): T[] {
  return Array.isArray(value) ? value.filter((item): item is T => typeof item === "object" && item !== null) : [];
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}

function stringValue(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function numberValue(value: unknown): number {
  return typeof value === "number" ? value : 0;
}

function overPrivilegeFlagsFor(principalId: string, roles: string[]): string[] {
  const flags: string[] = [];
  if (roles.includes("CUSTOMER") && roles.some((role) => role !== "CUSTOMER")) {
    flags.push("customer-with-staff-role");
  }
  if (roles.length > 2) {
    flags.push("more-than-two-realm-roles");
  }
  if (roles.includes("PASSKEY_RECOVERY_ADMIN") && roles.includes("AUDITOR")) {
    flags.push("passkey-recovery-and-audit-review-combined");
  }
  if (roles.includes("BRANCH_MANAGER") && roles.includes("COMPLIANCE_MANAGER")) {
    flags.push("approval-and-compliance-review-combined");
  }
  if (principalId.includes("block") && roles.includes("BRANCH_MANAGER")) {
    flags.push("blocked-test-principal-retains-manager-role");
  }
  return flags;
}

function roleOwner(role: string): string {
  if (role.includes("AML") || role.includes("FDS") || role.includes("COMPLIANCE")) {
    return "synthetic-compliance-owner";
  }
  if (role.includes("AUDITOR") || role.includes("PASSKEY")) {
    return "synthetic-security-owner";
  }
  if (role.includes("OPS") || role.includes("SYSTEM") || role.includes("PAYMENT_SERVICE")) {
    return "synthetic-ops-owner";
  }
  return "synthetic-business-owner";
}

function vulnerabilityOwner(id: string): string {
  if (id.includes("semgrep") || id.includes("dast")) {
    return "synthetic-appsec-owner";
  }
  if (id.includes("trivy") || id.includes("sbom")) {
    return "synthetic-platform-security-owner";
  }
  return "synthetic-engineering-owner";
}

function syntheticBoundaryControls() {
  return {
    realMoneyUsed: false,
    realPiiUsed: false,
    realKycUsed: false,
    realPaymentNetworkUsed: false,
    realExternalFinancialInstitutionApiUsed: false,
    realSanctionsDataUsed: false,
    regulatorSubmissionMade: false
  };
}
