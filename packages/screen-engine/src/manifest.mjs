import { readdir, readFile } from "node:fs/promises";
import { join } from "node:path";

const TEMPLATE_TYPES = new Set(["inquiry", "command", "case", "parameter", "dashboard"]);
const SCREEN_TYPES = new Set(["INQUIRY", "COMMAND", "CASE", "PARAMETER", "DASHBOARD"]);

async function walkJsonFiles(dir) {
  const entries = await readdir(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const fullPath = join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...await walkJsonFiles(fullPath));
    } else if (entry.name.endsWith(".json")) {
      files.push(fullPath);
    }
  }
  return files;
}

function requireField(manifest, field) {
  if (manifest[field] === undefined || manifest[field] === null || manifest[field] === "") {
    throw new Error(`${manifest.screenId || "unknown manifest"} missing ${field}`);
  }
}

export function validateManifest(manifest) {
  for (const field of ["screenId", "title", "app", "type", "domain", "requiredRoles", "layout", "audit"]) {
    requireField(manifest, field);
  }
  if (!SCREEN_TYPES.has(manifest.type)) {
    throw new Error(`${manifest.screenId} has unsupported type ${manifest.type}`);
  }
  if (!Array.isArray(manifest.requiredRoles) || manifest.requiredRoles.length === 0) {
    throw new Error(`${manifest.screenId} must declare at least one required role`);
  }
  if (!TEMPLATE_TYPES.has(manifest.layout.template)) {
    throw new Error(`${manifest.screenId} uses unsupported template ${manifest.layout.template}`);
  }
  if (manifest.audit.enabled !== true) {
    throw new Error(`${manifest.screenId} must enable audit logging`);
  }
  if (typeof manifest.audit.reasonRequired !== "boolean") {
    throw new Error(`${manifest.screenId} must declare audit.reasonRequired`);
  }
  if (manifest.audit.piiAccess === true && manifest.audit.reasonRequired !== true && manifest.audit.selfService !== true) {
    throw new Error(`${manifest.screenId} PII access must require a reason`);
  }
  if (manifest.type === "INQUIRY") {
    requireField(manifest, "query");
    requireField(manifest, "resultTable");
  }
  if (manifest.type === "COMMAND") {
    requireField(manifest, "fields");
    requireField(manifest, "api");
    if (manifest.highRisk === true && manifest.approval?.required !== true) {
      throw new Error(`${manifest.screenId} high-risk command must require maker-checker approval`);
    }
  }
  if (manifest.type === "CASE") {
    requireField(manifest, "workflow");
  }
  if (manifest.type === "PARAMETER" && manifest.approval?.required !== true) {
    throw new Error(`${manifest.screenId} parameter screen must require approval`);
  }
  return true;
}

export async function loadManifests(rootDir = "screen-manifests") {
  const files = await walkJsonFiles(rootDir);
  const manifests = [];
  for (const file of files) {
    const manifest = JSON.parse(await readFile(file, "utf8"));
    validateManifest(manifest);
    manifests.push({ ...manifest, sourcePath: file });
  }
  return manifests.sort((a, b) => a.screenId.localeCompare(b.screenId));
}

export function filterManifestsByApp(manifests, app) {
  return manifests.filter((manifest) => manifest.app === app);
}
