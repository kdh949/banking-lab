import { loadManifests } from "../packages/screen-engine/src/manifest.ts";

const manifests = await loadManifests("screen-manifests");
const apps = new Map<string, number>();
for (const manifest of manifests) {
  apps.set(manifest.app, (apps.get(manifest.app) || 0) + 1);
}

console.log(`Validated ${manifests.length} screen manifests.`);
for (const [app, count] of [...apps.entries()].sort()) {
  console.log(`- ${app}: ${count}`);
}
