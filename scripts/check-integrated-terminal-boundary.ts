import { access, readdir, readFile } from "node:fs/promises";
import { join } from "node:path";

const ignoredDirs = new Set([".next", "build", "coverage", "dist", "node_modules", "playwright-report", "test-results"]);

const requiredPaths = [
  "apps/staff-terminal/src/app/page.tsx",
  "apps/staff-terminal/src/app/layout.tsx",
  "apps/staff-terminal/src/app/globals.css",
  "apps/staff-terminal/src/app/api/terminal-status/route.ts",
  "apps/staff-terminal/src/app/lab/api-simulator/page.tsx",
  "apps/staff-terminal/src/app/lab/evidence/page.tsx",
  "apps/staff-terminal/src/app/lab/manifests/page.tsx",
  "apps/staff-terminal/src/components/integrated-terminal.tsx",
  "apps/staff-terminal/src/components/integrated-terminal.css",
  "apps/staff-terminal/src/components/terminal/IntegratedTerminalApp.tsx",
  "apps/staff-terminal/src/components/terminal/StaffApiEvidencePanel.tsx",
  "apps/staff-terminal/e2e/integrated-terminal.spec.ts"
];

const removedPaths = [
  "legacy-node-reference",
  "screen-manifests/staff-terminal",
  "design-qa.md",
  "DESIGN.md",
  "apps/staff-terminal/e2e/staff-terminal-parity.spec.ts",
  "apps/staff-terminal/src/app/accounts",
  "apps/staff-terminal/src/app/approvals",
  "apps/staff-terminal/src/app/audit",
  "apps/staff-terminal/src/app/customers",
  "apps/staff-terminal/src/app/tx",
  "apps/staff-terminal/src/app/workflows",
  "apps/staff-terminal/src/app/api/auth",
  "apps/staff-terminal/src/components/ApiBackedStaffPanel.tsx",
  "apps/staff-terminal/src/components/manifest-renderer.tsx",
  "apps/staff-terminal/src/components/terminal-screens.tsx",
  "apps/staff-terminal/src/components/terminal-ui.tsx",
  "apps/staff-terminal/src/components/terminal-ui.css",
  "apps/staff-terminal/src/components/workflow-routes.tsx",
  "apps/staff-terminal/src/components/workflow-route-summaries.ts",
  "apps/staff-terminal/src/lib/manifestLoader.ts"
];

const allowedAppFiles = new Set([
  "apps/staff-terminal/src/app/api/terminal-status/route.ts",
  "apps/staff-terminal/src/app/globals.css",
  "apps/staff-terminal/src/app/lab/api-simulator/page.tsx",
  "apps/staff-terminal/src/app/lab/evidence/page.tsx",
  "apps/staff-terminal/src/app/lab/manifests/page.tsx",
  "apps/staff-terminal/src/app/layout.tsx",
  "apps/staff-terminal/src/app/page.tsx"
]);

const forbiddenStaffSourceNeedles = [
  "ApiBackedStaffPanel",
  "manifest-renderer",
  "terminal-screens",
  "terminal-ui",
  "workflow-routes",
  "workflow-route-summaries",
  "manifestLoader",
  "staff-terminal-parity"
];

async function exists(path: string): Promise<boolean> {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
}

function normalize(path: string): string {
  return path.replaceAll("\\", "/");
}

async function listFiles(root: string): Promise<string[]> {
  if (!await exists(root)) {
    return [];
  }
  const entries = await readdir(root, { withFileTypes: true });
  const files: string[] = [];
  for (const entry of entries) {
    const entryPath = normalize(join(root, entry.name));
    if (entry.isDirectory()) {
      if (!ignoredDirs.has(entry.name)) {
        files.push(...await listFiles(entryPath));
      }
    } else {
      files.push(entryPath);
    }
  }
  return files;
}

const errors: string[] = [];

for (const requiredPath of requiredPaths) {
  if (!await exists(requiredPath)) {
    errors.push(`Required integrated terminal path is missing: ${requiredPath}`);
  }
}

for (const removedPath of removedPaths) {
  if (await exists(removedPath)) {
    errors.push(`Removed staff-terminal legacy path still exists: ${removedPath}`);
  }
}

const appFiles = await listFiles("apps/staff-terminal/src/app");
const unexpectedAppFiles = appFiles.filter((file) => !allowedAppFiles.has(file));
if (unexpectedAppFiles.length > 0) {
  errors.push("apps/staff-terminal official route boundary was expanded unexpectedly:");
  errors.push(...unexpectedAppFiles.map((file) => `  - ${file}`));
}

const staffFiles = (await listFiles("apps/staff-terminal/src"))
  .filter((file) => /\.(ts|tsx|css|json)$/u.test(file));

for (const file of staffFiles) {
  const source = await readFile(file, "utf8");
  for (const needle of forbiddenStaffSourceNeedles) {
    if (source.includes(needle)) {
      errors.push(`${file} still contains removed staff-terminal marker: ${needle}`);
    }
  }
}

const packageJson = JSON.parse(await readFile("apps/staff-terminal/package.json", "utf8")) as {
  dependencies?: Record<string, string>;
  devDependencies?: Record<string, string>;
};
for (const dependency of ["@banking-lab/api-client", "@banking-lab/auth-client"]) {
  if (!packageJson.dependencies?.[dependency]) {
    errors.push(`apps/staff-terminal/package.json must declare the bounded staff API evidence dependency: ${dependency}`);
  }
}

const pageSource = await readFile("apps/staff-terminal/src/app/page.tsx", "utf8");
if (!pageSource.includes("IntegratedTerminalApp")) {
  errors.push("apps/staff-terminal/src/app/page.tsx must render IntegratedTerminalApp.");
}

const cssSource = await readFile("apps/staff-terminal/src/components/integrated-terminal.css", "utf8");
if (!cssSource.includes("Material Symbols")) {
  errors.push("integrated-terminal.css must preserve the Material Symbols font import.");
}

const terminalStatusSource = await readFile("apps/staff-terminal/src/app/api/terminal-status/route.ts", "utf8");
for (const marker of ["clientIp", "serverTimeIso", "NextRequest"]) {
  if (!terminalStatusSource.includes(marker)) {
    errors.push(`terminal-status route must expose ${marker}.`);
  }
}

const apiEvidenceSource = await readFile("apps/staff-terminal/src/components/terminal/StaffApiEvidencePanel.tsx", "utf8");
for (const marker of [
  "@banking-lab/api-client",
  "@banking-lab/auth-client",
  "NEXT_PUBLIC_BANKING_API_BASE_URL",
  "NEXT_PUBLIC_BANKING_SIMULATOR_TOKENS_ENABLED",
  "staffCustomerDetail",
  "staffApprovals",
  "Browser staff-terminal Spring API evidence smoke",
  "core-banking-api",
  "SYN-CUS-001"
]) {
  if (!apiEvidenceSource.includes(marker)) {
    errors.push(`StaffApiEvidencePanel must keep bounded Spring API evidence marker: ${marker}`);
  }
}

if (errors.length > 0) {
  console.error("Integrated terminal boundary: failed");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log("Integrated terminal boundary: pass");
console.log(`Checked ${appFiles.length} official app route files and ${staffFiles.length} staff-terminal source files.`);
