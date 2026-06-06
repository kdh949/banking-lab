import { openApiContractFiles, parseOpenApiOperations, readUtf8, failIfErrors, asyncApiContractFile } from "./contract-utils.ts";

const errors: string[] = [];

for (const filePath of openApiContractFiles) {
  const source = await readUtf8(filePath);
  lintOpenApiFile(filePath, source);
}

lintAsyncApiFile(asyncApiContractFile, await readUtf8(asyncApiContractFile));

failIfErrors("Contract lint", errors);
console.log(`Contract lint passed: ${openApiContractFiles.length} OpenAPI files and 1 AsyncAPI file validated.`);

function lintOpenApiFile(filePath: string, source: string): void {
  requireSource(filePath, source, /^openapi:\s*3\.1\.0/mu, "OpenAPI 3.1 header");
  requireSource(filePath, source, /^info:\n(?:[\s\S]*?)^paths:/mu, "info section before paths");
  requireSource(filePath, source, /^paths:\n/mu, "paths section");
  requireSource(filePath, source, /x-banking-lab-contract:\n[\s\S]*?syntheticOnly:\s*true/u, "synthetic-only contract marker");
  requireSource(filePath, source, /structuredErrorDefault:\s*['"]?#\/components\/responses\/StructuredErrorResponse/u, "structured error default marker");
  requireSource(filePath, source, /StructuredErrorResponse:/u, "StructuredErrorResponse component");
  requireSource(filePath, source, /StructuredError:/u, "StructuredError schema");

  if (/\t/u.test(source)) {
    errors.push(`${filePath}: YAML must not use tab indentation.`);
  }

  const operations = parseOpenApiOperations(filePath, source);
  if (operations.length === 0) {
    errors.push(`${filePath}: no path operations found.`);
  }

  const seenOperationIds = new Set<string>();
  for (const operation of operations) {
    const label = `${filePath} ${operation.method} ${operation.path}`;
    if (!operation.operationId) {
      errors.push(`${label}: missing operationId.`);
      continue;
    }
    if (seenOperationIds.has(operation.operationId)) {
      errors.push(`${label}: duplicate operationId ${operation.operationId}.`);
    }
    seenOperationIds.add(operation.operationId);

    if (!/responses:/u.test(operation.block)) {
      errors.push(`${label}: missing responses block.`);
    }

    if (/x-idempotent-command:\s*true/u.test(operation.block) && !/(idempotencyKey|Idempotency-Key|x-idempotency-policy)/u.test(operation.block)) {
      errors.push(`${label}: idempotent command must document idempotencyKey or Idempotency-Key policy.`);
    }

    if (/x-reason-required:\s*true/u.test(operation.block) && !/(^|\s)reason:|name:\s*reason|x-reason-policy/u.test(operation.block)) {
      errors.push(`${label}: reason-required operation must document a reason field, parameter, or policy.`);
    }
  }
}

function lintAsyncApiFile(filePath: string, source: string): void {
  requireSource(filePath, source, /^asyncapi:\s*3\.0\.0/mu, "AsyncAPI 3.0 header");
  requireSource(filePath, source, /^channels:\n/mu, "channels section");
  requireSource(filePath, source, /x-banking-lab-envelope:\n[\s\S]*?required:/u, "banking event envelope metadata");

  for (const field of ["syntheticOnly", "sourceService", "eventType", "aggregateId", "occurredAt"]) {
    requireSource(filePath, source, new RegExp(`- ${field}\\b`, "u"), `envelope field ${field}`);
  }
}

function requireSource(filePath: string, source: string, pattern: RegExp, description: string): void {
  if (!pattern.test(source)) {
    errors.push(`${filePath}: missing ${description}.`);
  }
}

