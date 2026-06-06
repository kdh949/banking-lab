import { basename } from "node:path";
import { asyncApiContractFile, eventSchemaFiles, failIfErrors, readUtf8 } from "./contract-utils.ts";

const asyncApi = await readUtf8(asyncApiContractFile);
const errors: string[] = [];
const referencedSchemas = new Set(
  Array.from(asyncApi.matchAll(/\$ref:\s+\.\.\/events\/([A-Za-z0-9-]+\.schema\.json)/gmu)).map((match) => match[1])
);

for (const field of ["syntheticOnly", "sourceService", "eventType", "aggregateId", "occurredAt"]) {
  if (!new RegExp(`- ${field}\\b`, "u").test(asyncApi)) {
    errors.push(`${asyncApiContractFile}: missing event envelope field ${field}.`);
  }
}

for (const schemaPath of await eventSchemaFiles()) {
  const schemaName = basename(schemaPath);
  if (!referencedSchemas.has(schemaName)) {
    errors.push(`${schemaPath}: event schema is not referenced by ${asyncApiContractFile}.`);
  }

  const schema = JSON.parse(await readUtf8(schemaPath)) as {
    readonly title?: string;
    readonly type?: string;
    readonly required?: readonly string[];
    readonly properties?: Record<string, unknown>;
  };

  if (!schema.title) {
    errors.push(`${schemaPath}: missing JSON Schema title.`);
  }
  if (schema.type !== "object") {
    errors.push(`${schemaPath}: event payload schema must be an object.`);
  }
  if (!Array.isArray(schema.required)) {
    errors.push(`${schemaPath}: event payload schema must define required fields.`);
  }
  if (!schema.required?.includes("syntheticOnly")) {
    errors.push(`${schemaPath}: event payload must require syntheticOnly.`);
  }
  const syntheticOnly = schema.properties?.syntheticOnly as { readonly const?: unknown } | undefined;
  if (syntheticOnly?.const !== true) {
    errors.push(`${schemaPath}: syntheticOnly must be const true.`);
  }
}

failIfErrors("Event contract check", errors);
console.log(`Event contract check passed: ${referencedSchemas.size} AsyncAPI schema references validated.`);

