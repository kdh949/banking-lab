import { readAllOpenApiOperations, readUtf8, failIfErrors } from "./contract-utils.ts";

const clientSource = await readUtf8("packages/api-client/src/index.ts");
const clientMethods = new Set(
  Array.from(clientSource.matchAll(/^    ([A-Za-z][A-Za-z0-9_]*)\([^)]*\)\s*\{/gmu)).map((match) => match[1])
);
const errors: string[] = [];
const operationIds = new Set<string>();

for (const operation of await readAllOpenApiOperations()) {
  if (!operation.operationId) continue;

  if (operationIds.has(operation.operationId)) {
    errors.push(`Duplicate OpenAPI operationId across contracts: ${operation.operationId}`);
  }
  operationIds.add(operation.operationId);

  if (operation.clientRequired && !clientMethods.has(operation.operationId)) {
    errors.push(
      `${operation.filePath} ${operation.method} ${operation.path}: operationId ${operation.operationId} is missing from packages/api-client/src/index.ts; add the client method or mark x-api-client-required: false.`
    );
  }
}

for (const method of clientMethods) {
  if (!operationIds.has(method)) {
    errors.push(`packages/api-client/src/index.ts: method ${method} has no OpenAPI operationId coverage.`);
  }
}

failIfErrors("API client contract check", errors);
console.log(`API client contract check passed: ${operationIds.size} operationIds match ${clientMethods.size} shared client methods/exemptions.`);

