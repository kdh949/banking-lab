import { mkdir, readFile, writeFile } from "node:fs/promises";
import { basename, dirname } from "node:path";
import { asyncApiContractFile, eventSchemaFiles, failIfErrors, readUtf8 } from "./contract-utils.ts";

type JsonValue = null | boolean | number | string | JsonValue[] | { [key: string]: JsonValue };
type JsonObject = { [key: string]: JsonValue };

type JsonSchema = {
  readonly title?: string;
  readonly type?: string;
  readonly const?: JsonValue;
  readonly enum?: readonly JsonValue[];
  readonly required?: readonly string[];
  readonly properties?: Record<string, JsonSchema>;
  readonly items?: JsonSchema;
  readonly pattern?: string;
  readonly format?: string;
  readonly minLength?: number;
  readonly maxLength?: number;
  readonly minimum?: number;
  readonly exclusiveMinimum?: number;
  readonly minItems?: number;
  readonly additionalProperties?: boolean | JsonSchema;
};

type RuntimeEnvelopeFixture = {
  readonly eventId: string;
  readonly outboxEventId: string;
  readonly eventType: string;
  readonly aggregateType: string;
  readonly aggregateId: string;
  readonly occurredAt: string;
  readonly sourceService: string;
  readonly syntheticOnly: true;
  readonly schemaVersion: string;
  readonly payload: JsonObject;
  readonly headers: Record<string, string>;
};

type SourceMarkerFinding = {
  readonly id: string;
  readonly status: "pass";
  readonly file: string;
  readonly markers: readonly string[];
};

type RuntimeEnvelopeEvidence = {
  readonly reviewDate: string;
  readonly syntheticOnly: true;
  readonly status: "pass";
  readonly asyncApiContract: string;
  readonly eventSchemaCount: number;
  readonly validatedEnvelopeCount: number;
  readonly envelopeFields: readonly string[];
  readonly eventTypes: readonly string[];
  readonly sourceMarkerFindings: readonly SourceMarkerFinding[];
};

const generatedPath = "docs/test-evidence/generated/event-envelope-runtime-validation.json";
const envelopeFields = [
  "eventId",
  "eventType",
  "aggregateType",
  "aggregateId",
  "occurredAt",
  "sourceService",
  "syntheticOnly",
  "schemaVersion",
  "payload"
] as const;
const errors: string[] = [];

const asyncApi = await readUtf8(asyncApiContractFile);
const schemas = await loadSchemas();
const referencedSchemaFiles = schemaRefs(asyncApi);

for (const schemaPath of schemas.keys()) {
  if (!referencedSchemaFiles.has(`../events/${basename(schemaPath)}`)) {
    errors.push(`${schemaPath}: schema file is not referenced by ${asyncApiContractFile}`);
  }
}

const fixtures: RuntimeEnvelopeFixture[] = [];
for (const [schemaPath, schema] of schemas) {
  const eventType = schema.title;
  if (!eventType) {
    errors.push(`${schemaPath}: missing schema title used as eventType`);
    continue;
  }
  const payload = samplePayload(schema);
  const envelope = runtimeEnvelope(eventType, payload);
  validateRuntimeEnvelope(envelope, schemaPath, schema);
  fixtures.push(envelope);
}

const sourceMarkerFindings = await inspectRuntimeSources();

failIfErrors("Runtime event envelope validation", errors);

const evidence: RuntimeEnvelopeEvidence = {
  reviewDate: "2026-06-10",
  syntheticOnly: true,
  status: "pass",
  asyncApiContract: asyncApiContractFile,
  eventSchemaCount: schemas.size,
  validatedEnvelopeCount: fixtures.length,
  envelopeFields,
  eventTypes: fixtures.map((fixture) => fixture.eventType).sort(),
  sourceMarkerFindings
};

await mkdir(dirname(generatedPath), { recursive: true });
await writeFile(generatedPath, `${JSON.stringify(evidence, null, 2)}\n`);

console.log("Runtime event envelope validation passed");
console.log(`Generated ${generatedPath}`);
console.log(`Validated ${fixtures.length} synthetic envelope fixtures against ${schemas.size} event schemas`);

async function loadSchemas(): Promise<Map<string, JsonSchema>> {
  const loaded = new Map<string, JsonSchema>();
  for (const schemaPath of await eventSchemaFiles()) {
    loaded.set(schemaPath, JSON.parse(await readFile(schemaPath, "utf8")) as JsonSchema);
  }
  return loaded;
}

function schemaRefs(source: string): Set<string> {
  return new Set(
    Array.from(source.matchAll(/\$ref:\s*(\.\.\/events\/[A-Za-z0-9_.-]+\.schema\.json)/gmu)).map((match) => match[1])
  );
}

function samplePayload(schema: JsonSchema): JsonObject {
  const payload = sampleForSchema(schema, schema.title ?? "payload") as JsonObject;
  return payload;
}

function sampleForSchema(schema: JsonSchema, fieldName: string): JsonValue {
  if (schema.const !== undefined) {
    return schema.const;
  }
  if (schema.enum?.length) {
    return schema.enum[0];
  }
  if (schema.properties || schema.type === "object") {
    const object: JsonObject = {};
    for (const requiredField of schema.required ?? Object.keys(schema.properties ?? {})) {
      const propertySchema = schema.properties?.[requiredField];
      if (!propertySchema) {
        continue;
      }
      object[requiredField] = sampleForSchema(propertySchema, requiredField);
    }
    return object;
  }
  if (schema.type === "array") {
    const length = schema.minItems ?? 1;
    return Array.from({ length }, () => sampleForSchema(schema.items ?? { type: "string" }, singular(fieldName)));
  }
  if (schema.type === "integer") {
    return schema.exclusiveMinimum !== undefined ? schema.exclusiveMinimum + 1 : schema.minimum ?? 1;
  }
  if (schema.type === "number") {
    return schema.exclusiveMinimum !== undefined ? schema.exclusiveMinimum + 1 : schema.minimum ?? 1;
  }
  if (schema.type === "boolean") {
    return true;
  }
  return sampleString(fieldName, schema);
}

function sampleString(fieldName: string, schema: JsonSchema): string {
  if (schema.format === "date") {
    return "2026-06-10";
  }
  if (schema.format === "date-time") {
    return "2026-06-10T00:00:00.000Z";
  }
  if (fieldName === "currency") {
    return "KRW";
  }
  if (fieldName === "contentSha256") {
    return "a".repeat(64);
  }
  const prefix = schema.pattern ? patternPrefix(schema.pattern) : prefixForField(fieldName);
  const value = `${prefix}SYN-001`;
  if (schema.maxLength !== undefined && value.length > schema.maxLength) {
    return value.slice(0, schema.maxLength);
  }
  if (schema.minLength !== undefined && value.length < schema.minLength) {
    return value.padEnd(schema.minLength, "X");
  }
  return value;
}

function patternPrefix(pattern: string): string {
  const match = /^\^([A-Za-z0-9_-]+)/u.exec(pattern);
  return match ? match[1] : "SYN-";
}

function prefixForField(fieldName: string): string {
  if (/ledgerTransactionId|transaction/i.test(fieldName)) return "TX-";
  if (/paymentInstructionId/i.test(fieldName)) return "PAY-";
  if (/paymentAttemptId/i.test(fieldName)) return "PAT-";
  if (/artifactId/i.test(fieldName)) return "RPT-";
  if (/deliveryAttemptId/i.test(fieldName)) return "NAT-";
  if (/deliveryRequestId/i.test(fieldName)) return "NDL-";
  if (/deadLetterId/i.test(fieldName)) return "NDLQ-";
  if (/autopayAgreementId/i.test(fieldName)) return "APAY-";
  if (/autopayExecutionId/i.test(fieldName)) return "APEX-";
  if (/biller/i.test(fieldName)) return "SYN-BILLER-";
  return "SYN-";
}

function singular(fieldName: string): string {
  return fieldName.endsWith("s") ? fieldName.slice(0, -1) : fieldName;
}

function runtimeEnvelope(eventType: string, payload: JsonObject): RuntimeEnvelopeFixture {
  const aggregateId = aggregateIdFor(eventType, payload);
  const sourceService = sourceServiceFor(eventType);
  const occurredAt = "2026-06-10T00:00:00.000Z";
  const schemaVersion = schemaVersionFor(payload, sourceService);
  return {
    eventId: `EVT-${eventType}`,
    outboxEventId: `OBX-${eventType}`,
    eventType,
    aggregateType: aggregateTypeFor(eventType),
    aggregateId,
    occurredAt,
    sourceService,
    syntheticOnly: true,
    schemaVersion,
    payload,
    headers: {
      eventType,
      aggregateId,
      occurredAt,
      sourceService,
      schemaVersion,
      syntheticOnly: "true"
    }
  };
}

function sourceServiceFor(eventType: string): string {
  if (eventType === "LedgerTransactionPosted" || eventType === "PaymentLedgerPostingSettled") {
    return "core-banking-service";
  }
  if (eventType.startsWith("Payment")) {
    return "payment-service";
  }
  if (eventType.startsWith("Notification")) {
    return "notification-service";
  }
  if (eventType.startsWith("Report")) {
    return "reporting-service";
  }
  return "core-banking-service";
}

function aggregateTypeFor(eventType: string): string {
  if (eventType === "LedgerTransactionPosted" || eventType === "PaymentLedgerPostingSettled") return "LedgerTransaction";
  if (eventType.startsWith("Payment")) return "payment_instruction";
  if (eventType.startsWith("Notification")) return "notification_delivery";
  if (eventType.startsWith("ReportRetention")) return "REPORT_RETENTION_SWEEP";
  if (eventType.startsWith("Report")) return "REPORT_ARTIFACT";
  return "synthetic_aggregate";
}

function aggregateIdFor(eventType: string, payload: JsonObject): string {
  for (const field of [
    "ledgerTransactionId",
    "paymentInstructionId",
    "deliveryRequestId",
    "artifactId",
    "autopayExecutionId",
    "sweepDate"
  ]) {
    const value = payload[field];
    if (typeof value === "string") {
      return value;
    }
  }
  const nestedValue = payload.value;
  if (isObject(nestedValue) && typeof nestedValue.id === "string") {
    return nestedValue.id;
  }
  return `AGG-${eventType}`;
}

function schemaVersionFor(payload: JsonObject, sourceService: string): string {
  const explicit = payload.contractVersion ?? payload.schemaVersion;
  return typeof explicit === "string" || typeof explicit === "number" ? String(explicit) : `${sourceService}.events.v1`;
}

function validateRuntimeEnvelope(envelope: RuntimeEnvelopeFixture, schemaPath: string, schema: JsonSchema): void {
  for (const field of envelopeFields) {
    if (envelope[field] === undefined || envelope[field] === null) {
      errors.push(`${schemaPath}: runtime envelope is missing ${field}`);
    }
  }
  if (envelope.syntheticOnly !== true) {
    errors.push(`${schemaPath}: runtime envelope syntheticOnly must be true`);
  }
  for (const headerName of ["eventType", "aggregateId", "occurredAt", "sourceService", "schemaVersion"] as const) {
    if (envelope.headers[headerName] !== String(envelope[headerName])) {
      errors.push(`${schemaPath}: Kafka header ${headerName} does not match envelope body`);
    }
  }
  if (envelope.headers.syntheticOnly !== "true") {
    errors.push(`${schemaPath}: Kafka header syntheticOnly must be true`);
  }
  if (envelope.eventType !== schema.title) {
    errors.push(`${schemaPath}: envelope eventType ${envelope.eventType} does not match schema title ${schema.title ?? "missing"}`);
  }
  validateJsonSchema(schema, envelope.payload, `${schemaPath} payload`);
  validateSyntheticControls(schemaPath, envelope.payload);
}

function validateJsonSchema(schema: JsonSchema, value: JsonValue, path: string): void {
  if (schema.const !== undefined && value !== schema.const) {
    errors.push(`${path}: expected const ${String(schema.const)}, got ${String(value)}`);
  }
  if (schema.enum && !schema.enum.includes(value)) {
    errors.push(`${path}: value ${String(value)} is not in enum ${schema.enum.map(String).join(", ")}`);
  }
  if (schema.type) {
    validateType(schema, value, path);
  }
  if (schema.properties || schema.required) {
    if (!isObject(value)) {
      errors.push(`${path}: expected object`);
      return;
    }
    for (const requiredField of schema.required ?? []) {
      if (!(requiredField in value)) {
        errors.push(`${path}: missing required property ${requiredField}`);
      }
    }
    for (const [propertyName, propertyValue] of Object.entries(value)) {
      const propertySchema = schema.properties?.[propertyName];
      if (!propertySchema) {
        if (schema.additionalProperties === false) {
          errors.push(`${path}: additional property is not allowed: ${propertyName}`);
        }
        continue;
      }
      validateJsonSchema(propertySchema, propertyValue, `${path}.${propertyName}`);
    }
  }
  if (schema.type === "array") {
    if (!Array.isArray(value)) {
      errors.push(`${path}: expected array`);
      return;
    }
    if (schema.minItems !== undefined && value.length < schema.minItems) {
      errors.push(`${path}: expected at least ${schema.minItems} items`);
    }
    for (const [index, item] of value.entries()) {
      validateJsonSchema(schema.items ?? {}, item, `${path}[${index}]`);
    }
  }
  if (typeof value === "string") {
    if (schema.pattern && !new RegExp(schema.pattern, "u").test(value)) {
      errors.push(`${path}: value ${value} does not match ${schema.pattern}`);
    }
    if (schema.minLength !== undefined && value.length < schema.minLength) {
      errors.push(`${path}: value is shorter than ${schema.minLength}`);
    }
    if (schema.maxLength !== undefined && value.length > schema.maxLength) {
      errors.push(`${path}: value is longer than ${schema.maxLength}`);
    }
    if (schema.format === "date" && !/^\d{4}-\d{2}-\d{2}$/u.test(value)) {
      errors.push(`${path}: value is not an ISO date`);
    }
    if (schema.format === "date-time" && Number.isNaN(Date.parse(value))) {
      errors.push(`${path}: value is not an ISO date-time`);
    }
  }
  if (typeof value === "number") {
    if (schema.minimum !== undefined && value < schema.minimum) {
      errors.push(`${path}: value is below minimum ${schema.minimum}`);
    }
    if (schema.exclusiveMinimum !== undefined && value <= schema.exclusiveMinimum) {
      errors.push(`${path}: value is not greater than ${schema.exclusiveMinimum}`);
    }
  }
}

function validateType(schema: JsonSchema, value: JsonValue, path: string): void {
  if (schema.type === "object" && !isObject(value)) errors.push(`${path}: expected object`);
  if (schema.type === "array" && !Array.isArray(value)) errors.push(`${path}: expected array`);
  if (schema.type === "string" && typeof value !== "string") errors.push(`${path}: expected string`);
  if (schema.type === "integer" && !Number.isInteger(value)) errors.push(`${path}: expected integer`);
  if (schema.type === "number" && typeof value !== "number") errors.push(`${path}: expected number`);
  if (schema.type === "boolean" && typeof value !== "boolean") errors.push(`${path}: expected boolean`);
}

function validateSyntheticControls(schemaPath: string, payload: JsonObject): void {
  for (const [key, value] of Object.entries(flatten(payload))) {
    if (key.endsWith("syntheticOnly") && value !== true) {
      errors.push(`${schemaPath}: ${key} must remain true`);
    }
    if (/real.*(Used|Exposed)$/u.test(key) && value !== false) {
      errors.push(`${schemaPath}: ${key} must remain false`);
    }
    if ((key.endsWith("directLedgerWrite") || key.endsWith("ledgerRowsMutated")) && value !== false) {
      errors.push(`${schemaPath}: ${key} must remain false`);
    }
  }
}

function flatten(value: JsonValue, prefix = ""): Record<string, JsonValue> {
  if (!isObject(value)) {
    return prefix ? { [prefix]: value } : {};
  }
  const result: Record<string, JsonValue> = {};
  for (const [key, child] of Object.entries(value)) {
    Object.assign(result, flatten(child, prefix ? `${prefix}.${key}` : key));
  }
  return result;
}

function isObject(value: JsonValue | undefined): value is JsonObject {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

async function inspectRuntimeSources(): Promise<SourceMarkerFinding[]> {
  const findings: SourceMarkerFinding[] = [];
  await inspectProducerSource({
    id: "core-banking-kafka-outbox-runtime-envelope",
    file: "services/core-banking/src/main/kotlin/lab/banking/core/eventing/KafkaOutboxPublisher.kt",
    sourceService: "core-banking-service",
    publishedMarker: "outboxService.markPublished",
    failureMarker: "outboxService.recordPublishFailure"
  }, findings);
  await inspectProducerSource({
    id: "payment-service-kafka-outbox-runtime-envelope",
    file: "services/payment-service/src/main/kotlin/lab/banking/payment/eventing/PaymentKafkaOutboxPublisher.kt",
    sourceService: "payment-service",
    publishedMarker: "repository.markOutboxPublished",
    failureMarker: "repository.markOutboxFailed"
  }, findings);
  await inspectProducerSource({
    id: "reporting-service-kafka-outbox-runtime-envelope",
    file: "services/reporting-service/src/main/kotlin/lab/banking/reporting/eventing/ReportingKafkaOutboxPublisher.kt",
    sourceService: "reporting-service",
    publishedMarker: "repository.markOutboxPublished",
    failureMarker: "repository.markOutboxFailed"
  }, findings);

  const notificationFile = "services/notification-service/src/main/kotlin/lab/banking/notification/eventing/NotificationKafkaConsumer.kt";
  const notificationSource = await readUtf8(notificationFile);
  const notificationMarkers = [
    "requireEnvelopeMetadata(envelope)",
    "requireSyntheticOnly(envelope)",
    "firstString(envelope.sourceService, envelope.headers[\"sourceService\"])",
    "matching eventType envelope metadata",
    "matching aggregateId envelope metadata",
    "occurredAt envelope metadata"
  ];
  requireMarkers("notification-service-runtime-envelope-consumer", notificationFile, notificationSource, notificationMarkers);
  findings.push({
    id: "notification-service-runtime-envelope-consumer",
    status: "pass",
    file: notificationFile,
    markers: notificationMarkers
  });
  return findings;
}

async function inspectProducerSource(input: {
  readonly id: string;
  readonly file: string;
  readonly sourceService: string;
  readonly publishedMarker: string;
  readonly failureMarker: string;
}, findings: SourceMarkerFinding[]): Promise<void> {
  const source = await readUtf8(input.file);
  const markers = [
    "ProducerConfig.ACKS_CONFIG",
    "\"all\"",
    "ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG",
    "record.headers().add(header(\"eventType\", event.eventType))",
    "record.headers().add(header(\"aggregateId\", event.aggregateId))",
    "record.headers().add(header(\"occurredAt\", event.createdAt.toString()))",
    `record.headers().add(header("sourceService", "${input.sourceService}"))`,
    "record.headers().add(header(\"schemaVersion\", schemaVersion(event.payload)))",
    "record.headers().add(header(\"syntheticOnly\", \"true\"))",
    ".send(record)",
    ".get(config.publishTimeoutMillis",
    input.publishedMarker,
    input.failureMarker,
    "DEAD_LETTER"
  ];
  requireMarkers(input.id, input.file, source, markers);
  const ackIndex = source.indexOf(".get(config.publishTimeoutMillis");
  const publishedIndex = source.indexOf(input.publishedMarker);
  if (ackIndex < 0 || publishedIndex < 0 || ackIndex > publishedIndex) {
    errors.push(`${input.id}: published marker must appear after broker ack get()`);
  }
  findings.push({
    id: input.id,
    status: "pass",
    file: input.file,
    markers
  });
}

function requireMarkers(id: string, file: string, source: string, markers: readonly string[]): void {
  for (const marker of markers) {
    if (!source.includes(marker)) {
      errors.push(`${id}: ${basename(file)} is missing marker: ${marker}`);
    }
  }
}
