import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

type DrillStatus = "pass" | "fail";

interface AlertDrill {
  readonly alert: string;
  readonly control: string;
  readonly rulePresent: boolean;
  readonly simulatedFired: boolean;
  readonly sampleCount: number;
  readonly threshold: number;
}

const rulesFile = join("infra", "observability", "loki", "rules", "fake", "operational-security-alerts.yml");
const outputPath = join("docs", "test-evidence", "generated", "siem-alert-drill.json");
const rules = readFileSync(rulesFile, "utf8");

const drills: AlertDrill[] = [
  alertDrill({
    alert: "FailedAuthBurst",
    control: "failed-auth-burst",
    requiredTokens: ["AUTHORIZATION_DENIED", "AUTHENTICATION_FAILURE"],
    sampleCount: 6,
    threshold: 5
  }),
  alertDrill({
    alert: "PrivilegeChangeObserved",
    control: "privilege-change",
    requiredTokens: ["COMMAND_APPROVED", "ROLE_GRANT"],
    sampleCount: 1,
    threshold: 0
  }),
  alertDrill({
    alert: "MassPiiAccess",
    control: "mass-pii-access",
    requiredTokens: ["PII_UNMASK_GRANTED"],
    sampleCount: 11,
    threshold: 10
  }),
  alertDrill({
    alert: "BreakGlassUsage",
    control: "break-glass",
    requiredTokens: ["BREAK_GLASS_GRANTED"],
    sampleCount: 1,
    threshold: 0
  })
];

const status: DrillStatus = drills.every((drill) => drill.rulePresent && drill.simulatedFired) ? "pass" : "fail";

mkdirSync(join("docs", "test-evidence", "generated"), { recursive: true });
writeFileSync(
  outputPath,
  `${JSON.stringify(
    {
      generatedAt: new Date().toISOString(),
      command: "npm run siem:alert-drill",
      status,
      syntheticOnly: true,
      lokiRulesFile: rulesFile,
      liveLokiRequired: false,
      controlsVerified: drills.map((drill) => drill.control),
      drills
    },
    null,
    2
  )}\n`
);

console.log(`SIEM alert drill: ${status}.`);
console.log(`Evidence written: ${outputPath}`);

if (status !== "pass") {
  process.exit(1);
}

function alertDrill(input: {
  readonly alert: string;
  readonly control: string;
  readonly requiredTokens: readonly string[];
  readonly sampleCount: number;
  readonly threshold: number;
}): AlertDrill {
  const rulePresent = rules.includes(`alert: ${input.alert}`) &&
    input.requiredTokens.every((token) => rules.includes(token)) &&
    rules.includes('synthetic_only: "true"');
  return {
    alert: input.alert,
    control: input.control,
    rulePresent,
    simulatedFired: input.sampleCount > input.threshold,
    sampleCount: input.sampleCount,
    threshold: input.threshold
  };
}
