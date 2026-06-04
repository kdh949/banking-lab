import { spawnSync } from "node:child_process";

const dockerCheck = spawnSync("docker", ["version"], {
  encoding: "utf8",
  maxBuffer: 10 * 1024 * 1024
});

if (dockerCheck.status !== 0) {
  const reason = firstLine(dockerCheck.stderr || dockerCheck.stdout) ?? "Docker is not available.";
  console.error(`Cannot run Docker-backed security evidence: ${reason}`);
  process.exit(dockerCheck.status ?? 1);
}

const result = spawnSync(process.execPath, ["--experimental-strip-types", "scripts/run-security-evidence.ts"], {
  stdio: "inherit",
  env: {
    ...process.env,
    BANKING_LAB_SECURITY_FORCE_DOCKER: "true"
  }
});

process.exit(result.status ?? 1);

function firstLine(value: string): string | undefined {
  return value.split("\n").map((line) => line.trim()).find(Boolean);
}
