import { mkdir, writeFile } from "node:fs/promises";
import { generateSyntheticDataset } from "../legacy-node-reference/packages/banking-domain/src/index.mjs";

const outputDir = "var/generated";
const outputFile = `${outputDir}/synthetic-seed.json`;

await mkdir(outputDir, { recursive: true });
await writeFile(outputFile, `${JSON.stringify(generateSyntheticDataset(), null, 2)}\n`);

console.log(`Wrote ${outputFile}`);
