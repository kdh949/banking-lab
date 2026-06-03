import { spawn } from "node:child_process";
import { readdir, readFile } from "node:fs/promises";
import path from "node:path";

type ParitySuite = {
  nodeSuite: string;
  scenarioCount: number;
  controls?: string[];
};

type CommandCheck = {
  controls?: string[];
};

type ParityScenarioMap = {
  nodeReferenceTestCount: number;
  requiredControls: string[];
  suites: ParitySuite[];
  commandChecks?: CommandCheck[];
};

const parityMapPath = "docs/migration/parity-scenarios.json";
const parityMap = JSON.parse(await readFile(parityMapPath, "utf8")) as ParityScenarioMap;

function commandLine(command: string, args: readonly string[]): string {
  return [command, ...args].join(" ");
}

async function runStep(name: string, command: string, args: readonly string[]): Promise<void> {
  console.log(`\n[parity] ${name}`);
  console.log(`[parity] $ ${commandLine(command, args)}`);
  await new Promise<void>((resolve, reject) => {
    const child = spawn(command, [...args], { stdio: "inherit" });
    child.on("error", reject);
    child.on("exit", (code) => {
      if (code === 0) {
        resolve();
      } else {
        reject(new Error(`${name} failed with exit code ${code}`));
      }
    });
  });
}

async function listAllTestFiles(): Promise<string[]> {
  const testFiles = (await readdir("tests"))
    .filter((fileName) => fileName.endsWith(".test.mjs"))
    .sort()
    .map((fileName) => path.join("tests", fileName));
  return testFiles;
}

async function countMappedReferenceTests(): Promise<number> {
  let count = 0;
  for (const suite of parityMap.suites) {
    const source = await readFile(suite.nodeSuite, "utf8");
    count += [...source.matchAll(/^test\(/gm)].length;
  }
  return count;
}

function validateParityMap(actualTestCount: number): void {
  const mappedCount = parityMap.suites.reduce((sum, suite) => sum + suite.scenarioCount, 0);
  if (mappedCount !== parityMap.nodeReferenceTestCount) {
    throw new Error(`parity scenario map count mismatch: map=${mappedCount}, expected=${parityMap.nodeReferenceTestCount}`);
  }
  if (actualTestCount !== parityMap.nodeReferenceTestCount) {
    throw new Error(`Node reference test count changed: actual=${actualTestCount}, parity map=${parityMap.nodeReferenceTestCount}`);
  }

  const coveredControls = new Set<string>();
  for (const suite of parityMap.suites) {
    for (const control of suite.controls || []) {
      coveredControls.add(control);
    }
  }
  for (const check of parityMap.commandChecks || []) {
    for (const control of check.controls || []) {
      coveredControls.add(control);
    }
  }
  const missingControls = parityMap.requiredControls.filter((control) => !coveredControls.has(control));
  if (missingControls.length > 0) {
    throw new Error(`parity scenario map misses required controls: ${missingControls.join(", ")}`);
  }
}

const actualTestCount = await countMappedReferenceTests();
validateParityMap(actualTestCount);
const testFiles = await listAllTestFiles();

console.log(`[parity] Scenario map covers ${actualTestCount} Node reference scenarios.`);
console.log(`[parity] Required controls: ${parityMap.requiredControls.join(", ")}`);

await runStep("Node reference tests", process.execPath, ["--test", ...testFiles]);
await runStep("Screen manifest validation", process.execPath, ["--experimental-strip-types", "scripts/validate-manifests.ts"]);
await runStep("Evidence pack generation", process.execPath, ["scripts/generate-evidence-pack.mjs"]);

console.log("\n[parity] Reference parity checks passed.");
console.log("[parity] Node reference runtime remains required until docs/migration/node-retirement-gate.json is ready.");
