import { spawnSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";

type CheckStatus = "pass" | "fail" | "skipped";

type SecurityCheck = {
  id: string;
  status: CheckStatus;
  command: string;
  outputPath?: string;
  reason?: string;
  exitCode?: number | null;
};

type SecuritySummary = {
  generatedAt: string;
  syntheticOnly: boolean;
  checks: SecurityCheck[];
  totals: {
    pass: number;
    fail: number;
    skipped: number;
  };
};

type DockerFallback = {
  image: string;
  args: string[];
  entrypoint?: string;
};

const rootDir = process.cwd();
const generatedDir = join(rootDir, "docs/test-evidence/generated");

mkdirSync(generatedDir, { recursive: true });

const checks: SecurityCheck[] = [];

runOptionalToolCheck({
  id: "npm-audit-high",
  tool: "npm",
  args: ["audit", "--audit-level=high"],
  outputFile: "npm-audit.txt"
});

runOptionalToolCheck({
  id: "semgrep-sast",
  tool: "semgrep",
  args: ["--config", "infra/security/semgrep.yml", "--json", "--error", "."],
  outputFile: "semgrep.json",
  docker: {
    image: "semgrep/semgrep:latest",
    entrypoint: "semgrep",
    args: ["--config", "infra/security/semgrep.yml", "--json", "--error", "."]
  }
});

runOptionalToolCheck({
  id: "trivy-fs",
  tool: "trivy",
  args: ["fs", "--config", "infra/security/trivy.yaml", "--format", "json", "--exit-code", "1", "."],
  outputFile: "trivy-fs.json",
  docker: {
    image: "aquasec/trivy:latest",
    entrypoint: "trivy",
    args: ["fs", "--config", "infra/security/trivy.yaml", "--format", "json", "--exit-code", "1", "/workspace"]
  }
});

if (commandExists("trivy")) {
  runTool({
    id: "sbom-cyclonedx",
    tool: "trivy",
    args: ["fs", "--format", "cyclonedx", "."],
    outputFile: "sbom.cdx.json"
  });
} else if (commandExists("docker")) {
  runDockerTool({
    id: "sbom-cyclonedx",
    command: "docker run --rm -v <repo>:/workspace -w /workspace --entrypoint trivy aquasec/trivy:latest fs --format cyclonedx /workspace",
    docker: {
      image: "aquasec/trivy:latest",
      entrypoint: "trivy",
      args: ["fs", "--format", "cyclonedx", "/workspace"]
    },
    outputFile: "sbom.cdx.json"
  });
} else if (commandExists("syft")) {
  runTool({
    id: "sbom-cyclonedx",
    tool: "syft",
    args: ["dir:.", "-o", "cyclonedx-json"],
    outputFile: "sbom.cdx.json"
  });
} else {
  checks.push({
    id: "sbom-cyclonedx",
    status: "skipped",
    command: "trivy fs --format cyclonedx . OR syft dir:. -o cyclonedx-json",
    reason: "Neither trivy nor syft is installed in this environment."
  });
}

if (process.env.BANKING_LAB_DAST_URL && commandExists("zap-baseline.py")) {
  runTool({
    id: "dast-zap-baseline",
    tool: "zap-baseline.py",
    args: [
      "-t",
      process.env.BANKING_LAB_DAST_URL,
      "-J",
      join(generatedDir, "zap-baseline.json"),
      "-c",
      "infra/security/zap-baseline.conf"
    ],
    outputFile: "zap-baseline.log"
  });
} else if (process.env.BANKING_LAB_DAST_URL && commandExists("docker")) {
  runZapDockerTool(process.env.BANKING_LAB_DAST_URL);
} else {
  checks.push({
    id: "dast-zap-baseline",
    status: "skipped",
    command: "BANKING_LAB_DAST_URL=<url> zap-baseline.py -t <url>",
    reason: process.env.BANKING_LAB_DAST_URL
      ? "zap-baseline.py is not installed in this environment."
      : "BANKING_LAB_DAST_URL is not set; no live target was supplied for DAST."
  });
}

const summary: SecuritySummary = {
  generatedAt: new Date().toISOString(),
  syntheticOnly: true,
  checks,
  totals: {
    pass: checks.filter((check) => check.status === "pass").length,
    fail: checks.filter((check) => check.status === "fail").length,
    skipped: checks.filter((check) => check.status === "skipped").length
  }
};

writeFileSync(
  join(generatedDir, "security-evidence-summary.json"),
  `${JSON.stringify(summary, null, 2)}\n`
);
writeFileSync(join(generatedDir, "security-evidence-summary.md"), markdownSummary(summary));

console.log(
  `Security evidence: ${summary.totals.pass} passed, ${summary.totals.fail} failed, ${summary.totals.skipped} skipped.`
);
for (const check of checks) {
  console.log(`- ${check.id}: ${check.status}${check.reason ? ` (${check.reason})` : ""}`);
}

function runOptionalToolCheck(options: {
  id: string;
  tool: string;
  args: string[];
  outputFile: string;
  docker?: DockerFallback;
}) {
  if (!commandExists(options.tool)) {
    if (options.docker && commandExists("docker")) {
      runDockerTool({
        id: options.id,
        command: dockerCommand(options.docker),
        docker: options.docker,
        outputFile: options.outputFile
      });
    } else {
      checks.push({
        id: options.id,
        status: "skipped",
        command: [options.tool, ...options.args].join(" "),
        reason: options.docker
          ? `${options.tool} is not installed and Docker is not available in this environment.`
          : `${options.tool} is not installed in this environment.`
      });
    }
    return;
  }
  runTool(options);
}

function runTool(options: {
  id: string;
  tool: string;
  args: string[];
  outputFile: string;
}) {
  const command = [options.tool, ...options.args].join(" ");
  const result = spawnSync(options.tool, options.args, {
    cwd: rootDir,
    encoding: "utf8",
    maxBuffer: 50 * 1024 * 1024
  });
  const outputPath = join("docs/test-evidence/generated", options.outputFile);
  writeFileSync(
    join(rootDir, outputPath),
    result.stdout || result.stderr || ""
  );
  checks.push({
    id: options.id,
    status: result.status === 0 ? "pass" : "fail",
    command,
    outputPath,
    exitCode: result.status,
    reason: result.status === 0 ? undefined : firstLine(result.stderr || result.stdout)
  });
}

function runDockerTool(options: {
  id: string;
  command: string;
  docker: DockerFallback;
  outputFile: string;
}) {
  const dockerArgs = [
    "run",
    "--rm",
    "-v",
    `${rootDir}:/workspace`,
    "-w",
    "/workspace"
  ];
  if (options.docker.entrypoint) {
    dockerArgs.push("--entrypoint", options.docker.entrypoint);
  }
  dockerArgs.push(options.docker.image, ...options.docker.args);
  const result = spawnSync("docker", dockerArgs, {
    cwd: rootDir,
    encoding: "utf8",
    maxBuffer: 100 * 1024 * 1024
  });
  const outputPath = join("docs/test-evidence/generated", options.outputFile);
  writeFileSync(join(rootDir, outputPath), result.stdout || result.stderr || "");
  checks.push({
    id: options.id,
    status: result.status === 0 ? "pass" : "fail",
    command: options.command,
    outputPath,
    exitCode: result.status,
    reason: result.status === 0 ? undefined : firstLine(result.stderr || result.stdout)
  });
}

function runZapDockerTool(targetUrl: string) {
  const dockerArgs = [
    "run",
    "--rm",
    "-v",
    `${generatedDir}:/zap/wrk`,
    "-v",
    `${rootDir}:/workspace:ro`,
    "ghcr.io/zaproxy/zaproxy:stable",
    "zap-baseline.py",
    "-t",
    targetUrl,
    "-J",
    "zap-baseline.json",
    "-c",
    "/workspace/infra/security/zap-baseline.conf"
  ];
  const result = spawnSync("docker", dockerArgs, {
    cwd: rootDir,
    encoding: "utf8",
    maxBuffer: 100 * 1024 * 1024
  });
  const outputPath = join("docs/test-evidence/generated", "zap-baseline.log");
  writeFileSync(join(rootDir, outputPath), result.stdout || result.stderr || "");
  checks.push({
    id: "dast-zap-baseline",
    status: result.status === 0 ? "pass" : "fail",
    command: "docker run --rm -v <generated>:/zap/wrk -v <repo>:/workspace:ro ghcr.io/zaproxy/zaproxy:stable zap-baseline.py -t <url> -J zap-baseline.json -c /workspace/infra/security/zap-baseline.conf",
    outputPath,
    exitCode: result.status,
    reason: result.status === 0 ? undefined : firstLine(result.stderr || result.stdout)
  });
}

function commandExists(tool: string): boolean {
  return spawnSync("which", [tool], { encoding: "utf8" }).status === 0;
}

function dockerCommand(fallback: DockerFallback): string {
  const entrypoint = fallback.entrypoint ? ` --entrypoint ${fallback.entrypoint}` : "";
  return `docker run --rm -v <repo>:/workspace -w /workspace${entrypoint} ${fallback.image} ${fallback.args.join(" ")}`;
}

function firstLine(value: string): string | undefined {
  return value.split("\n").map((line) => line.trim()).find(Boolean);
}

function markdownSummary(summary: SecuritySummary): string {
  const lines = [
    "# Security Evidence Summary",
    "",
    `Generated at: ${summary.generatedAt}`,
    "",
    "| Check | Status | Evidence | Reason |",
    "| --- | --- | --- | --- |"
  ];
  for (const check of summary.checks) {
    lines.push(
      `| ${check.id} | ${check.status} | ${check.outputPath ?? ""} | ${check.reason ?? ""} |`
    );
  }
  lines.push("");
  lines.push(
    `Totals: ${summary.totals.pass} passed, ${summary.totals.fail} failed, ${summary.totals.skipped} skipped.`
  );
  lines.push("");
  return `${lines.join("\n")}\n`;
}
