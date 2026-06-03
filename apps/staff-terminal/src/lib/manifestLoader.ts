import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import type { ScreenManifest } from "../../../../packages/screen-engine/src/types";

const repoRoot = process.env.BANKING_LAB_ROOT || path.resolve(process.cwd(), "../..");
const appId = "staff-terminal";

export async function loadChannelManifests(): Promise<ScreenManifest[]> {
  const manifestDir = path.join(repoRoot, "screen-manifests", appId);
  const fileNames = (await readdir(manifestDir))
    .filter((fileName) => fileName.endsWith(".json"))
    .sort();

  const manifests = await Promise.all(
    fileNames.map(async (fileName) => {
      const source = await readFile(path.join(manifestDir, fileName), "utf8");
      return JSON.parse(source) as ScreenManifest;
    })
  );

  return manifests.filter((manifest) => manifest.app === appId);
}
