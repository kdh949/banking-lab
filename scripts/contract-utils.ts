import { readdir, readFile } from "node:fs/promises";
import { join } from "node:path";

export const openApiContractFiles = [
  "contracts/openapi/core-banking.yaml",
  "contracts/openapi/payment-service.yaml",
  "contracts/openapi/notification-service.yaml",
  "contracts/openapi/reporting-service.yaml"
] as const;

export const asyncApiContractFile = "contracts/asyncapi/banking-lab-events.yaml";

export interface OpenApiOperation {
  readonly filePath: string;
  readonly path: string;
  readonly method: string;
  readonly operationId: string | null;
  readonly block: string;
  readonly clientRequired: boolean;
}

export async function readUtf8(path: string): Promise<string> {
  return readFile(path, "utf8");
}

export async function eventSchemaFiles(): Promise<string[]> {
  const entries = await readdir("contracts/events", { withFileTypes: true });
  return entries
    .filter((entry) => entry.isFile() && entry.name.endsWith(".schema.json"))
    .map((entry) => join("contracts/events", entry.name))
    .sort();
}

export function parseOpenApiOperations(filePath: string, source: string): OpenApiOperation[] {
  const operations: OpenApiOperation[] = [];
  const pathMatches = Array.from(source.matchAll(/^  (\/[^:\n]+):\n/gmu));

  for (let pathIndex = 0; pathIndex < pathMatches.length; pathIndex += 1) {
    const pathMatch = pathMatches[pathIndex];
    const path = pathMatch[1];
    const pathStart = pathMatch.index ?? 0;
    const pathEnd = pathIndex + 1 < pathMatches.length ? pathMatches[pathIndex + 1].index ?? source.length : source.length;
    const pathBlock = source.slice(pathStart, pathEnd);
    const methodMatches = Array.from(pathBlock.matchAll(/^    (get|post|put|patch|delete):\n/gmu));

    for (let methodIndex = 0; methodIndex < methodMatches.length; methodIndex += 1) {
      const methodMatch = methodMatches[methodIndex];
      const method = methodMatch[1].toUpperCase();
      const methodStart = methodMatch.index ?? 0;
      const methodEnd =
        methodIndex + 1 < methodMatches.length ? methodMatches[methodIndex + 1].index ?? pathBlock.length : pathBlock.length;
      const block = pathBlock.slice(methodStart, methodEnd);
      const operationId = /operationId:\s*([A-Za-z][A-Za-z0-9_]*)/u.exec(block)?.[1] ?? null;
      operations.push({
        filePath,
        path,
        method,
        operationId,
        block,
        clientRequired: !/x-api-client-required:\s*false/u.test(block)
      });
    }
  }

  return operations;
}

export async function readAllOpenApiOperations(): Promise<OpenApiOperation[]> {
  const operations: OpenApiOperation[] = [];
  for (const filePath of openApiContractFiles) {
    operations.push(...parseOpenApiOperations(filePath, await readUtf8(filePath)));
  }
  return operations;
}

export function failIfErrors(title: string, errors: readonly string[]): void {
  if (errors.length > 0) {
    throw new Error(`${title} failed:\n${errors.join("\n")}`);
  }
}

