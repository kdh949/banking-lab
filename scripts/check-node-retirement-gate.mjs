import { access, readFile } from "node:fs/promises";

const gatePath = "docs/migration/node-retirement-gate.json";
const gate = JSON.parse(await readFile(gatePath, "utf8"));

async function exists(path) {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
}

const missingReferencePaths = [];
for (const referencePath of gate.nodeReferenceRuntime.paths) {
  if (!await exists(referencePath)) {
    missingReferencePaths.push(referencePath);
  }
}

const incompleteGates = gate.requiredGates.filter((item) => item.status !== "pass");
const ready = gate.status === "ready";

if (missingReferencePaths.length > 0 && !ready) {
  console.error("Node reference retirement gate: failed");
  console.error("Reference runtime paths are missing before parity is complete:");
  for (const referencePath of missingReferencePaths) {
    console.error(`- ${referencePath}`);
  }
  process.exit(1);
}

if (ready && incompleteGates.length > 0) {
  console.error("Node reference retirement gate: failed");
  console.error("Gate status is ready but required gates are incomplete:");
  for (const item of incompleteGates) {
    console.error(`- ${item.id}: ${item.status}`);
  }
  process.exit(1);
}

if (!ready) {
  console.log("Node reference retirement gate: blocked");
  console.log(gate.statusReason);
  console.log("Incomplete gates:");
  for (const item of incompleteGates) {
    console.log(`- ${item.id}: ${item.status}`);
  }
  process.exit(0);
}

console.log("Node reference retirement gate: ready");
console.log("All required retirement gates have passing evidence.");
