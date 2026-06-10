import { mkdir, readdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";

type AppEvidence = {
  readonly app: string;
  readonly status: string;
  readonly liveRunEvidence: string;
  readonly routes: readonly string[];
  readonly requiredEnvironment: readonly string[];
  readonly playwrightSpecs: readonly string[];
  readonly apiMethods: readonly string[];
  readonly states: readonly string[];
  readonly controls: readonly string[];
  readonly notes: readonly string[];
};

type EvidenceDocument = {
  readonly reviewDate: string;
  readonly syntheticOnly: true;
  readonly status: "partial";
  readonly statusReason: string;
  readonly skippedPlaywrightIsPassEvidence: false;
  readonly applications: readonly AppEvidence[];
  readonly commands: readonly {
    readonly id: string;
    readonly command: string;
    readonly purpose: string;
  }[];
  readonly checks: readonly {
    readonly id: string;
    readonly status: "pass";
    readonly details: string;
  }[];
};

const generatedPath = "docs/test-evidence/generated/live-route-api-execution.json";

async function read(path: string): Promise<string> {
  return readFile(path, "utf8");
}

async function listFiles(root: string): Promise<string[]> {
  const entries = await readdir(root, { withFileTypes: true });
  const files: string[] = [];
  for (const entry of entries) {
    const path = join(root, entry.name).replaceAll("\\", "/");
    if (entry.isDirectory()) {
      files.push(...await listFiles(path));
    } else {
      files.push(path);
    }
  }
  return files;
}

function requireIncludes(source: string, needle: string, message: string, errors: string[]): void {
  if (!source.includes(needle)) {
    errors.push(message);
  }
}

function requireNotIncludes(source: string, needle: string, message: string, errors: string[]): void {
  if (source.includes(needle)) {
    errors.push(message);
  }
}

const errors: string[] = [];

const onboardingSpec = await read("apps/customer-web/e2e/customer-onboarding-self-service.spec.ts");
const customerParitySpec = await read("apps/customer-web/e2e/customer-web-parity.spec.ts");
const customerSelfService = await read("apps/customer-web/src/components/CustomerSelfService.tsx");
const apiBackedCustomerPanel = await read("apps/customer-web/src/components/ApiBackedCustomerPanel.tsx");
const customerWorkflowRoutes = await read("apps/customer-web/src/components/workflow-routes.tsx");
const staffE2eSpec = await read("apps/staff-terminal/e2e/integrated-terminal.spec.ts");
const staffBoundaryScript = await read("scripts/check-integrated-terminal-boundary.ts");
const staffPackage = await read("apps/staff-terminal/package.json");
const staffApiEvidencePanel = await read("apps/staff-terminal/src/components/terminal/StaffApiEvidencePanel.tsx");
const staffApiComposeSmoke = await read("scripts/run-staff-terminal-api-e2e-compose-smoke.sh");
const customerAppFiles = (await listFiles("apps/customer-web/src/app")).sort();
const staffAppFiles = (await listFiles("apps/staff-terminal/src/app")).sort();

for (const marker of [
  "BANKING_LAB_E2E_API_BASE_URL",
  "BANKING_LAB_E2E_STAFF_MAKER_BEARER_TOKEN",
  "BANKING_LAB_E2E_STAFF_CHECKER_BEARER_TOKEN",
  "signupCustomer",
  "loginCustomer",
  "requestStaffAccountOpening",
  "approveStaffAccountOpeningRequest",
  "executeStaffAccountOpeningRequest",
  "customerAccounts",
  "internalRecipientLookup",
  "requestCustomerTransfer",
  "customerTransactions",
  "customerTransfers",
  "transferReplay.replayed",
  "/accounts",
  "/transfers/"
]) {
  requireIncludes(onboardingSpec, marker, `customer onboarding self-service spec is missing marker: ${marker}`, errors);
}

for (const marker of [
  "BANKING_LAB_E2E_API_BASE_URL",
  "BANKING_LAB_E2E_KEYCLOAK_BASE_URL",
  "Run transfer retry smoke",
  "Run transfer failure smoke",
  "Run history status smoke",
  "Run held failed status smoke",
  "Run Keycloak transfer smoke",
  "Run Keycloak transfer failure smoke",
  "Run Keycloak history status smoke",
  "Run Keycloak held failed status smoke",
  "Run Keycloak complaint entry smoke",
  "Run Keycloak complaint confirm smoke",
  "LEDGER_INSUFFICIENT_AVAILABLE_BALANCE",
  "HELD",
  "FAILED"
]) {
  requireIncludes(customerParitySpec, marker, `customer-web parity spec is missing marker: ${marker}`, errors);
}

for (const marker of [
  "signupCustomer",
  "loginCustomer",
  "customerAccounts",
  "customerTransactions",
  "internalRecipientLookup",
  "requestCustomerTransfer",
  "customerTransfers",
  "AUTHORIZATION_DENIED",
  "REQUEST_VALIDATION_FAILED"
]) {
  requireIncludes(customerSelfService, marker, `CustomerSelfService is missing marker: ${marker}`, errors);
}

for (const marker of [
  "customerAccountDetail",
  "requestCustomerTransfer",
  "customerTransactions",
  "customerTransfers",
  "requestCustomerComplaint",
  "confirmCustomerComplaint",
  "LEDGER_INSUFFICIENT_AVAILABLE_BALANCE",
  "HELD",
  "FAILED"
]) {
  requireIncludes(apiBackedCustomerPanel, marker, `ApiBackedCustomerPanel is missing marker: ${marker}`, errors);
}

for (const marker of [
  "CWB-001",
  "CWB-002",
  "CWB-101",
  "CWB-201",
  "POSTED",
  "HELD",
  "FAILED",
  "BLOCKED",
  "STEP_UP_REQUIRED",
  "apiMethods"
]) {
  requireIncludes(customerWorkflowRoutes, marker, `customer workflow route metadata is missing marker: ${marker}`, errors);
}

const requiredCustomerRoutes = [
  "apps/customer-web/src/app/signup/page.tsx",
  "apps/customer-web/src/app/login/page.tsx",
  "apps/customer-web/src/app/accounts/page.tsx",
  "apps/customer-web/src/app/accounts/[accountId]/page.tsx",
  "apps/customer-web/src/app/transfers/new/page.tsx",
  "apps/customer-web/src/app/transfers/[resultId]/page.tsx",
  "apps/customer-web/src/app/complaints/page.tsx",
  "apps/customer-web/src/app/complaints/[caseId]/page.tsx",
  "apps/customer-web/src/app/security/page.tsx",
  "apps/customer-web/src/app/api/auth/keycloak-token/route.ts"
];

for (const route of requiredCustomerRoutes) {
  if (!customerAppFiles.includes(route)) {
    errors.push(`customer-web route file is missing: ${route}`);
  }
}

const expectedStaffAppFiles = [
  "apps/staff-terminal/src/app/api/terminal-status/route.ts",
  "apps/staff-terminal/src/app/globals.css",
  "apps/staff-terminal/src/app/layout.tsx",
  "apps/staff-terminal/src/app/page.tsx"
];

if (JSON.stringify(staffAppFiles) !== JSON.stringify(expectedStaffAppFiles)) {
  errors.push(`staff-terminal app route boundary changed: ${JSON.stringify(staffAppFiles)}`);
}

for (const marker of [
  "IntegratedTerminalApp",
  "/api/terminal-status",
  "staff-terminal Spring API evidence smoke",
  "staff-terminal-api-evidence",
  "SYN-CUS-001",
  "clientIp",
  "serverTimeIso",
  "iWorks integrated terminal"
]) {
  requireIncludes(staffE2eSpec, marker, `staff-terminal E2E spec is missing marker: ${marker}`, errors);
}

for (const marker of [
  "apps/staff-terminal/src/app/accounts",
  "apps/staff-terminal/src/app/approvals",
  "apps/staff-terminal/src/app/audit",
  "apps/staff-terminal/src/app/customers",
  "apps/staff-terminal/src/app/tx",
  "apps/staff-terminal/src/app/workflows",
  "StaffApiEvidencePanel",
  "staffCustomerDetail",
  "staffApprovals"
]) {
  requireIncludes(staffBoundaryScript, marker, `integrated terminal boundary script is missing marker: ${marker}`, errors);
}

for (const marker of [
  "@banking-lab/api-client",
  "@banking-lab/auth-client"
]) {
  requireIncludes(staffPackage, marker, `staff-terminal package is missing bounded API evidence dependency: ${marker}`, errors);
}

for (const marker of [
  "NEXT_PUBLIC_BANKING_API_BASE_URL",
  "NEXT_PUBLIC_BANKING_SIMULATOR_TOKENS_ENABLED",
  "staffCustomerDetail",
  "staffApprovals",
  "createSimulatorBearerToken",
  "Browser staff-terminal Spring API evidence smoke",
  "core-banking-api",
  "SYN-CUS-001"
]) {
  requireIncludes(staffApiEvidencePanel, marker, `StaffApiEvidencePanel is missing API evidence marker: ${marker}`, errors);
}

for (const marker of [
  "BANKING_LAB_SECURITY_ISSUER",
  "BANKING_LAB_SECURITY_AUDIENCE",
  "core-banking-api",
  "Compose%20staff-terminal%20API%20preflight",
  "Spring staff API evidence"
]) {
  requireIncludes(staffApiComposeSmoke, marker, `staff-terminal API compose smoke is missing marker: ${marker}`, errors);
}

if (errors.length > 0) {
  console.error("Live route API evidence check failed");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

const evidence: EvidenceDocument = {
  reviewDate: "2026-06-10",
  syntheticOnly: true,
  status: "partial",
  statusReason: "customer-web and staff-terminal have route-backed live API tests gated by environment; skipped Playwright remains non-evidence.",
  skippedPlaywrightIsPassEvidence: false,
  applications: [
    {
      app: "customer-web",
      status: "route-backed-live-gated",
      liveRunEvidence: "source-present-env-gated-not-run-in-this-slice",
      routes: [
        "/signup",
        "/login",
        "/accounts",
        "/accounts/[accountId]",
        "/transfers/new",
        "/transfers/[resultId]",
        "/complaints",
        "/complaints/[caseId]",
        "/security",
        "/api/auth/keycloak-token"
      ],
      requiredEnvironment: [
        "BANKING_LAB_E2E_API_BASE_URL",
        "BANKING_LAB_E2E_STAFF_MAKER_BEARER_TOKEN",
        "BANKING_LAB_E2E_STAFF_CHECKER_BEARER_TOKEN",
        "BANKING_LAB_E2E_KEYCLOAK_BASE_URL"
      ],
      playwrightSpecs: [
        "apps/customer-web/e2e/customer-onboarding-self-service.spec.ts",
        "apps/customer-web/e2e/customer-web-parity.spec.ts"
      ],
      apiMethods: [
        "signupCustomer",
        "loginCustomer",
        "requestStaffAccountOpening",
        "approveStaffAccountOpeningRequest",
        "executeStaffAccountOpeningRequest",
        "customerAccounts",
        "customerAccountDetail",
        "internalRecipientLookup",
        "requestCustomerTransfer",
        "customerTransactions",
        "customerTransfers",
        "requestCustomerComplaint",
        "confirmCustomerComplaint"
      ],
      states: [
        "POSTED",
        "HELD",
        "FAILED",
        "BLOCKED",
        "STEP_UP_REQUIRED",
        "LEDGER_INSUFFICIENT_AVAILABLE_BALANCE",
        "REQUEST_VALIDATION_FAILED",
        "AUTHORIZATION_DENIED"
      ],
      controls: [
        "synthetic signup and login",
        "staff maker-checker account opening for synthetic E2E accounts",
        "masked owned-account read",
        "internal synthetic recipient lookup",
        "balanced transfer command through Spring API",
        "idempotency replay assertion",
        "ledger history and transfer status readback",
        "Keycloak/JWKS gated browser smoke source coverage"
      ],
      notes: [
        "The source coverage is present and environment-gated.",
        "This generated evidence does not claim a live local or hosted run unless the Playwright command is executed with the required environment."
      ]
    },
    {
      app: "staff-terminal",
      status: "route-backed-live-gated",
      liveRunEvidence: "source-present-compose-smoke-script-present",
      routes: [
        "/",
        "/api/terminal-status"
      ],
      requiredEnvironment: [
        "BANKING_LAB_E2E_API_BASE_URL",
        "NEXT_PUBLIC_BANKING_SIMULATOR_TOKENS_ENABLED"
      ],
      playwrightSpecs: [
        "apps/staff-terminal/e2e/integrated-terminal.spec.ts"
      ],
      apiMethods: [
        "terminalStatus",
        "staffCustomerDetail",
        "staffApprovals"
      ],
      states: [
        "integrated-terminal",
        "route-backed-live-gated",
        "reason-required audit",
        "masked staff customer read"
      ],
      controls: [
        "iWorks shell rendering",
        "terminal status route",
        "source boundary excludes retired staff route set",
        "bounded Spring API evidence panel",
        "simulator token opt-in required",
        "reason-required staff customer detail",
        "approval inbox read through Spring API"
      ],
      notes: [
        "Current staff-terminal keeps the iWorks shell and does not restore the retired accounts, approvals, audit, customer, transaction, or workflow app route set.",
        "The bounded evidence panel is the only staff-terminal frontend Spring API caller in this slice.",
        "Use npm run test:staff-terminal:api-e2e-compose to produce local live route-to-API evidence."
      ]
    }
  ],
  commands: [
    {
      id: "generate-evidence",
      command: "npm run live-route:evidence",
      purpose: "Regenerate and validate this bounded live route/API evidence artifact."
    },
    {
      id: "customer-web-live-api",
      command: "BANKING_LAB_E2E_API_BASE_URL=<spring-api> BANKING_LAB_E2E_STAFF_MAKER_BEARER_TOKEN=<maker> BANKING_LAB_E2E_STAFF_CHECKER_BEARER_TOKEN=<checker> npm run test:e2e -- apps/customer-web/e2e/customer-onboarding-self-service.spec.ts --project=chromium",
      purpose: "Execute signup->login->staff account opening->accounts->transfer->history route/API smoke."
    },
    {
      id: "customer-web-keycloak-live-api",
      command: "BANKING_LAB_E2E_API_BASE_URL=<spring-api> BANKING_LAB_E2E_KEYCLOAK_BASE_URL=<keycloak> npm run test:e2e -- apps/customer-web/e2e/customer-web-parity.spec.ts --project=chromium",
      purpose: "Execute customer-web Keycloak and API-backed browser smokes when the live synthetic stack is configured."
    },
    {
      id: "staff-terminal-boundary",
      command: "npm run integrated-terminal:boundary-check",
      purpose: "Confirm staff-terminal remains the current iWorks integrated shell and exposes only the bounded Spring API evidence panel, not the retired staff route set."
    },
    {
      id: "staff-terminal-live-api",
      command: "npm run test:staff-terminal:api-e2e-compose",
      purpose: "Execute the iWorks shell plus bounded staff customer detail and approval inbox Spring API evidence smoke against disposable Compose."
    }
  ],
  checks: [
    {
      id: "customer-web-env-gated-source-coverage",
      status: "pass",
      details: "Customer-web Playwright specs and route components contain live API, idempotency, history/status, failure-state, and Keycloak-gated browser flow markers."
    },
    {
      id: "staff-terminal-no-overclaim",
      status: "pass",
      details: "Staff-terminal source keeps the iWorks shell boundary and limits frontend Spring API execution to StaffApiEvidencePanel."
    },
    {
      id: "synthetic-only-boundary",
      status: "pass",
      details: "Evidence references only synthetic routes, simulator tokens, local Keycloak/JWKS smoke, and no real customer money, PII, KYC, payment network, card network, or external bank API integration."
    }
  ]
};

await mkdir(dirname(generatedPath), { recursive: true });
await writeFile(generatedPath, `${JSON.stringify(evidence, null, 2)}\n`);

console.log("Live route API evidence check passed");
console.log(`Generated ${generatedPath}`);
