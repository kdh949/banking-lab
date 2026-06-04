import { existsSync } from "node:fs";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";

export type KubernetesDocument = {
  file: string;
  apiVersion: string;
  kind: string;
  name: string;
  namespace?: string;
  content: string;
};

export function splitYamlDocuments(source: string): string[] {
  return source
    .split(/^---\s*$/m)
    .map((document) => document.trim())
    .filter(Boolean);
}

export function parseKubernetesDocuments(source: string, file: string): KubernetesDocument[] {
  return splitYamlDocuments(source).map((content, index) => {
    const apiVersion = matchScalar(content, /^apiVersion:\s*(.+)$/m);
    const kind = matchScalar(content, /^kind:\s*(.+)$/m);
    const metadata = metadataBlock(content);
    const name = matchScalar(metadata, /^  name:\s*(.+)$/m);
    const namespace = matchScalar(metadata, /^  namespace:\s*(.+)$/m) || undefined;
    if (!apiVersion || !kind || !name) {
      throw new Error(`${file} document ${index + 1} is missing apiVersion, kind, or metadata.name.`);
    }
    return {
      file,
      apiVersion: unquote(apiVersion),
      kind: unquote(kind),
      name: unquote(name),
      namespace: namespace ? unquote(namespace) : undefined,
      content
    };
  });
}

export function requireDocument(
  documents: KubernetesDocument[],
  kind: string,
  name: string,
  errors: string[]
): KubernetesDocument | undefined {
  const found = documents.find((document) => document.kind === kind && document.name === name);
  if (!found) {
    errors.push(`Missing Kubernetes document ${kind}/${name}.`);
  }
  return found;
}

export function hasText(document: KubernetesDocument | undefined, needle: string): boolean {
  return Boolean(document?.content.includes(needle));
}

export function scalarFromSection(source: string, section: string, keyPath: string[]): string {
  const target = [section, ...keyPath];
  const stack: Array<{ indent: number; key: string }> = [];
  for (const line of source.split(/\r?\n/)) {
    const match = /^(\s*)([A-Za-z0-9_-]+):(?:\s*(.*))?$/.exec(line);
    if (!match) {
      continue;
    }
    const indent = match[1].length;
    const key = match[2];
    const value = match[3]?.trim() || "";
    while (stack.length > 0 && stack[stack.length - 1].indent >= indent) {
      stack.pop();
    }
    stack.push({ indent, key });
    const currentPath = stack.map((item) => item.key);
    if (currentPath.length === target.length && currentPath.every((item, index) => item === target[index])) {
      if (!value) {
        throw new Error(`Missing scalar value for ${target.join(".")}`);
      }
      return unquote(value);
    }
  }
  throw new Error(`Missing values key: ${target.join(".")}`);
}

export async function writeJsonPreservingTimestamp(filePath: string, payload: Record<string, unknown>): Promise<void> {
  await mkdir(path.dirname(filePath), { recursive: true });
  const existing = existsSync(filePath) ? JSON.parse(await readFile(filePath, "utf8")) as Record<string, unknown> : undefined;
  const generatedAt = sameExceptGeneratedAt(existing, payload)
    ? existing?.generatedAt
    : new Date().toISOString();
  const next = `${JSON.stringify({ generatedAt, ...payload }, null, 2)}\n`;
  if (!existing || next !== `${JSON.stringify(existing, null, 2)}\n`) {
    await writeFile(filePath, next, "utf8");
  }
}

function metadataBlock(content: string): string {
  const metadataMatch = /^metadata:\s*$/m.exec(content);
  if (!metadataMatch) {
    return "";
  }
  const rest = content.slice(metadataMatch.index + metadataMatch[0].length);
  const nextTopLevel = /^\S.*:.*$/m.exec(rest);
  return nextTopLevel ? rest.slice(0, nextTopLevel.index) : rest;
}

function matchScalar(source: string, pattern: RegExp): string {
  return pattern.exec(source)?.[1]?.trim() || "";
}

function unquote(value: string): string {
  return value.replace(/^["']|["']$/g, "");
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function sameExceptGeneratedAt(existing: Record<string, unknown> | undefined, payload: Record<string, unknown>): boolean {
  if (!existing) {
    return false;
  }
  const { generatedAt: _generatedAt, ...withoutGeneratedAt } = existing;
  return JSON.stringify(withoutGeneratedAt) === JSON.stringify(payload);
}
