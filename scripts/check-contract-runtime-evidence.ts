import { mkdir, readFile, writeFile } from "node:fs/promises";
import { basename, dirname } from "node:path";
import {
  asyncApiContractFile,
  eventSchemaFiles,
  failIfErrors,
  openApiContractFiles,
  readAllOpenApiOperations,
  readUtf8
} from "./contract-utils.ts";

type EvidenceStatus = "pass" | "partial" | "not-present-plan-required";

type ContractRuntimeEvidence = {
  readonly reviewDate: string;
  readonly syntheticOnly: true;
  readonly status: "partial";
  readonly statusReason: string;
  readonly openApi: {
    readonly checkedInFiles: readonly string[];
    readonly operationCount: number;
    readonly clientMethodCount: number;
    readonly structuralGateScripts: readonly string[];
    readonly generatedDtoDiffGate: {
      readonly status: EvidenceStatus;
      readonly expectedScript: string;
      readonly note: string;
    };
  };
  readonly asyncApi: {
    readonly contractFile: string;
    readonly eventSchemaCount: number;
    readonly envelopeFields: readonly string[];
    readonly structuralGateScripts: readonly string[];
    readonly runtimeEventValidationGate: {
      readonly status: EvidenceStatus;
      readonly expectedScript: string;
      readonly note: string;
    };
  };
  readonly sourceInspection: readonly {
    readonly id: string;
    readonly status: EvidenceStatus;
    readonly files: readonly string[];
    readonly markers: readonly string[];
    readonly note: string;
  }[];
  readonly overclaimGuards: readonly {
    readonly id: string;
    readonly status: "pass";
    readonly details: string;
  }[];
};

const generatedPath = "docs/test-evidence/generated/contract-runtime-evidence.json";
const packageJson = JSON.parse(await readUtf8("package.json")) as { readonly scripts?: Record<string, string> };
const scripts = packageJson.scripts ?? {};
const operations = await readAllOpenApiOperations();
const clientSource = await readUtf8("packages/api-client/src/index.ts");
const clientMethods = new Set(
  Array.from(clientSource.matchAll(/^    ([A-Za-z][A-Za-z0-9_]*)\([^)]*\)\s*\{/gmu)).map((match) => match[1])
);
const eventSchemas = await eventSchemaFiles();
const asyncApi = await readUtf8(asyncApiContractFile);
const readme = await readUtf8("README.md");
const contractDoc = await readUtf8("docs/test-evidence/contract-validation-hardening.md");

const errors: string[] = [];

for (const requiredScript of ["contracts:lint", "contracts:check-client", "contracts:check-events"]) {
  if (!scripts[requiredScript]) {
    errors.push(`package.json is missing structural contract script: ${requiredScript}`);
  }
}

if (!scripts["contracts:diff-openapi"]) {
  errors.push("package.json is missing PLAN Phase 4 OpenAPI source diff script: contracts:diff-openapi");
}

for (const requiredDocMarker of [
  "add springdoc/Jackson DTO schema generation and broader live broker-backed event-envelope coverage beyond the current contract gates",
  "Kotlin controller source-to-OpenAPI path/method diffing is wired through `contracts:diff-openapi`",
  "springdoc/Jackson DTO schema generation remains a future improvement",
  "`contracts:validate-runtime-events` now validates deterministic runtime envelope fixtures"
]) {
  if (!readme.includes(requiredDocMarker) && !contractDoc.includes(requiredDocMarker)) {
    errors.push(`contract evidence docs are missing no-overclaim marker: ${requiredDocMarker}`);
  }
}

const envelopeFields = ["syntheticOnly", "sourceService", "eventType", "aggregateId", "occurredAt"] as const;
for (const field of envelopeFields) {
  if (!new RegExp(`- ${field}\\b`, "u").test(asyncApi)) {
    errors.push(`${asyncApiContractFile} is missing envelope field: ${field}`);
  }
}

const sourceInspection = [
  inspectSource({
    id: "payment-service-kafka-producer-envelope-source",
    files: ["services/payment-service/src/main/kotlin/lab/banking/payment/eventing/PaymentKafkaOutboxPublisher.kt"],
    requiredMarkers: [
      "toKafkaEnvelope",
      "sourceService\" to \"payment-service",
      "syntheticOnly\" to true",
      "eventType\" to eventType",
      "aggregateId\" to aggregateId",
      "occurredAt\" to createdAt.toString()",
      "record.headers().add(header(\"syntheticOnly\", \"true\"))"
    ],
    note: "Payment publisher source builds envelope headers and payload metadata before Kafka send; this is source inspection, not a generated runtime schema gate."
  }),
  inspectSource({
    id: "reporting-service-kafka-producer-envelope-source",
    files: ["services/reporting-service/src/main/kotlin/lab/banking/reporting/eventing/ReportingKafkaOutboxPublisher.kt"],
    requiredMarkers: [
      "toKafkaEnvelope",
      "sourceService\" to \"reporting-service",
      "syntheticOnly\" to true",
      "eventType\" to eventType",
      "aggregateId\" to aggregateId",
      "occurredAt\" to createdAt.toString()",
      "record.headers().add(header(\"syntheticOnly\", \"true\"))"
    ],
    note: "Reporting publisher source builds envelope headers and payload metadata before Kafka send; this is source inspection, not a generated runtime schema gate."
  }),
  inspectSource({
    id: "notification-service-consumer-envelope-source",
    files: [
      "services/notification-service/src/main/kotlin/lab/banking/notification/eventing/NotificationKafkaConsumer.kt",
      "services/notification-service/src/main/kotlin/lab/banking/notification/eventing/NotificationKafkaModels.kt"
    ],
    requiredMarkers: [
      "requireEnvelopeMetadata(envelope)",
      "requireSyntheticOnly(envelope)",
      "sourceService envelope metadata",
      "matching eventType envelope metadata",
      "matching aggregateId envelope metadata",
      "occurredAt envelope metadata",
      "NotificationOutboxKafkaEnvelope"
    ],
    note: "Notification consumer source rejects missing envelope metadata and non-synthetic events; this is source inspection, not a cross-service runtime contract validation gate."
  }),
  inspectSource({
    id: "core-banking-outbox-source",
    files: ["services/core-banking/src/main/kotlin/lab/banking/core/ledger/application/LedgerCommandService.kt"],
    requiredMarkers: [
      "INSERT INTO outbox_events",
      "\"syntheticOnly\" to true",
      "aggregateId",
      "eventType"
    ],
    note: "Core banking persists durable synthetic outbox records; full Kafka envelope runtime validation is still a PLAN-required gap."
  })
];

for (const schemaPath of eventSchemas) {
  const schema = JSON.parse(await readFile(schemaPath, "utf8")) as {
    readonly required?: readonly string[];
    readonly properties?: Record<string, { readonly const?: unknown }>;
  };
  if (!schema.required?.includes("syntheticOnly")) {
    errors.push(`${basename(schemaPath)} must require syntheticOnly`);
  }
  if (schema.properties?.syntheticOnly?.const !== true) {
    errors.push(`${basename(schemaPath)} must constrain syntheticOnly to true`);
  }
}

for (const finding of sourceInspection) {
  for (const marker of finding.markers) {
    const presentInAnyFile = (await Promise.all(finding.files.map((file) => sourceIncludes(file, marker)))).some(Boolean);
    if (!presentInAnyFile) {
      errors.push(`${finding.id} is missing source marker: ${marker}`);
    }
  }
}

if (sourceInspection.some((finding) => finding.status !== "pass" && finding.id !== "core-banking-outbox-source")) {
  errors.push("producer/consumer source inspection failed for a non-core bounded context");
}

failIfErrors("Contract runtime evidence boundary", errors);

const evidence: ContractRuntimeEvidence = {
  reviewDate: "2026-06-10",
  syntheticOnly: true,
  status: "partial",
  statusReason:
    "Current gates prove checked-in OpenAPI/AsyncAPI structure, API-client operationId parity, Kotlin controller source-to-OpenAPI path/method parity, event schema references, runtime envelope fixture/schema validation, and selected source envelope markers. Springdoc/Jackson DTO schema generation remains a PLAN-required gap.",
  openApi: {
    checkedInFiles: [...openApiContractFiles],
    operationCount: operations.length,
    clientMethodCount: clientMethods.size,
    structuralGateScripts: ["contracts:lint", "contracts:check-client", "contracts:diff-openapi"],
    generatedDtoDiffGate: {
      status: "partial",
      expectedScript: "contracts:diff-openapi",
      note:
        "Kotlin controller source-to-OpenAPI path/method diffing is wired and writes generated evidence snapshots; this does not certify full springdoc/Jackson DTO schema parity."
    }
  },
  asyncApi: {
    contractFile: asyncApiContractFile,
    eventSchemaCount: eventSchemas.length,
    envelopeFields,
    structuralGateScripts: ["contracts:check-events"],
    runtimeEventValidationGate: {
      status: scripts["contracts:validate-runtime-events"] ? "partial" : "not-present-plan-required",
      expectedScript: "contracts:validate-runtime-events",
      note: scripts["contracts:validate-runtime-events"]
        ? "Script is wired and validates synthetic runtime envelope fixtures, JSON Schemas, Kafka header/body consistency, and producer ack/source markers. Full live broker producer/consumer coverage still depends on the service integration tests."
        : "No runtime producer/consumer event-envelope validation gate is wired yet; do not treat source inspection as a live runtime pass."
    }
  },
  sourceInspection,
  overclaimGuards: [
    {
      id: "generated-openapi-diff-not-overclaimed",
      status: "pass",
      details: "README and contract evidence distinguish the wired Kotlin controller source diff from remaining springdoc/Jackson DTO schema generation."
    },
    {
      id: "runtime-event-validation-not-overclaimed",
      status: "pass",
      details: "Evidence distinguishes the wired fixture/schema/source-marker runtime event gate from full live broker producer/consumer integration coverage."
    },
    {
      id: "synthetic-only-boundary",
      status: "pass",
      details: "Contract evidence remains synthetic-only and excludes real money, real PII, real KYC/AML providers, payment/card networks, and external financial institution APIs."
    }
  ]
};

await mkdir(dirname(generatedPath), { recursive: true });
await writeFile(generatedPath, `${JSON.stringify(evidence, null, 2)}\n`);

console.log("Contract runtime evidence boundary passed");
console.log(`Generated ${generatedPath}`);

function inspectSource(input: {
  readonly id: string;
  readonly files: readonly string[];
  readonly requiredMarkers: readonly string[];
  readonly note: string;
}): ContractRuntimeEvidence["sourceInspection"][number] {
  return {
    id: input.id,
    status: "pass",
    files: input.files,
    markers: input.requiredMarkers,
    note: input.note
  };
}

async function sourceIncludes(filePath: string, marker: string): Promise<boolean> {
  return (await readUtf8(filePath)).includes(marker);
}
