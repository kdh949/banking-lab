import { spawnSync } from "node:child_process";
import { access, readdir } from "node:fs/promises";
import { join } from "node:path";

const targetRoots = [
  "apps",
  "services",
  "packages",
  "analytics",
  "infra",
  "contracts",
  "db"
];

const generatedDirNames = new Set([
  ".gradle",
  ".next",
  ".turbo",
  "__pycache__",
  "build",
  "coverage",
  "dist",
  "node_modules",
  "playwright-report",
  "test-results"
]);

const disallowedSourceExtensions = [
  ".mjs",
  ".cjs",
  ".js",
  ".jsx",
  ".html"
];

function normalizePath(path: string): string {
  return path.replaceAll("\\", "/");
}

async function exists(path: string): Promise<boolean> {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
}

async function listFiles(root: string): Promise<string[]> {
  if (!await exists(root)) {
    return [];
  }
  const entries = await readdir(root, { withFileTypes: true }).catch(() => []);
  if (entries.length === 0) {
    return [root];
  }

  const files: string[] = [];
  for (const entry of entries) {
    const entryPath = normalizePath(join(root, entry.name));
    if (entry.isDirectory()) {
      if (generatedDirNames.has(entry.name)) {
        generatedDirs.add(entryPath);
        continue;
      }
      files.push(...await listFiles(entryPath));
    } else {
      files.push(entryPath);
    }
  }
  return files;
}

function hasDisallowedExtension(path: string): boolean {
  return disallowedSourceExtensions.some((extension) => path.endsWith(extension));
}

function isUnderGeneratedDir(path: string): boolean {
  return path
    .split("/")
    .some((segment) => generatedDirNames.has(segment));
}

function gitOutput(args: string[]): string[] {
  const result = spawnSync("git", args, {
    encoding: "utf8",
    maxBuffer: 1024 * 1024 * 8
  });
  if (result.status !== 0 && result.stdout.trim().length === 0) {
    return [];
  }
  return result.stdout
    .split(/\r?\n/u)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
}

function isIgnored(path: string): boolean {
  const result = spawnSync("git", ["check-ignore", "--quiet", path], {
    encoding: "utf8"
  });
  return result.status === 0;
}

const generatedDirs = new Set<string>();
const allFiles = (await Promise.all(targetRoots.map((root) => listFiles(root)))).flat();
const disallowedFiles = allFiles.filter(hasDisallowedExtension);
const trackedFiles = gitOutput(["ls-files", ...targetRoots]);
const trackedDisallowed = trackedFiles.filter(hasDisallowedExtension);
const untrackedSourceLike = disallowedFiles.filter((file) => !isUnderGeneratedDir(file) && !isIgnored(file));
const generatedNotIgnored = [...generatedDirs].filter((dir) => !isIgnored(dir));
const errors: string[] = [];

if (trackedDisallowed.length > 0) {
  errors.push("Tracked target files use legacy/static generated extensions:");
  errors.push(...trackedDisallowed.map((file) => `  - ${file}`));
}

if (untrackedSourceLike.length > 0) {
  errors.push("Untracked target source-like files use legacy/static generated extensions outside generated directories:");
  errors.push(...untrackedSourceLike.map((file) => `  - ${file}`));
}

if (generatedNotIgnored.length > 0) {
  errors.push("Generated target directories are not ignored by git:");
  errors.push(...generatedNotIgnored.slice(0, 50).map((file) => `  - ${file}`));
  if (generatedNotIgnored.length > 50) {
    errors.push(`  - ... ${generatedNotIgnored.length - 50} more`);
  }
}

if (errors.length > 0) {
  console.error("Generated artifact boundary audit: failed");
  for (const error of errors) {
    console.error(error);
  }
  process.exit(1);
}

console.log("Generated artifact boundary audit: pass");
console.log(`Tracked target legacy/static extension files: ${trackedDisallowed.length}`);
console.log(`Untracked source-like legacy/static extension files: ${untrackedSourceLike.length}`);
console.log(`Ignored generated directories: ${generatedDirs.size}`);
