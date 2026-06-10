import { mkdir, readdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { failIfErrors, openApiContractFiles, parseOpenApiOperations, readUtf8 } from "./contract-utils.ts";

type ServiceId = "core-banking" | "payment-service" | "notification-service" | "reporting-service";

type ServiceConfig = {
  readonly serviceId: ServiceId;
  readonly sourceRoot: string;
  readonly contractPath: string;
  readonly generatedPath: string;
};

type RuntimeOperation = {
  readonly method: string;
  readonly path: string;
  readonly handler: string;
  readonly controllerFile: string;
  readonly requestDto: string | null;
  readonly responseDto: string | null;
};

type GeneratedDtoProperty = {
  readonly name: string;
  readonly kotlinType: string;
  readonly required: boolean;
  readonly nullable: boolean;
  readonly ref: string | null;
  readonly itemsRef: string | null;
};

type GeneratedDtoSchema = {
  readonly name: string;
  readonly kind: "object" | "enum";
  readonly required: readonly string[];
  readonly properties: readonly GeneratedDtoProperty[];
  readonly enumValues: readonly string[];
};

type GeneratedOpenApiSnapshot = {
  readonly generatedAt: string;
  readonly generator: "kotlin-controller-source";
  readonly serviceId: ServiceId;
  readonly syntheticOnly: true;
  readonly diffScope: string;
  readonly operations: readonly RuntimeOperation[];
  readonly dtoSchemas: readonly GeneratedDtoSchema[];
};

const generatedAt = "2026-06-10T00:00:00.000Z";
const services: ServiceConfig[] = [
  {
    serviceId: "core-banking",
    sourceRoot: "services/core-banking/src/main/kotlin",
    contractPath: "contracts/openapi/core-banking.yaml",
    generatedPath: "docs/test-evidence/generated/openapi/core-banking.generated.json"
  },
  {
    serviceId: "payment-service",
    sourceRoot: "services/payment-service/src/main/kotlin",
    contractPath: "contracts/openapi/payment-service.yaml",
    generatedPath: "docs/test-evidence/generated/openapi/payment-service.generated.json"
  },
  {
    serviceId: "notification-service",
    sourceRoot: "services/notification-service/src/main/kotlin",
    contractPath: "contracts/openapi/notification-service.yaml",
    generatedPath: "docs/test-evidence/generated/openapi/notification-service.generated.json"
  },
  {
    serviceId: "reporting-service",
    sourceRoot: "services/reporting-service/src/main/kotlin",
    contractPath: "contracts/openapi/reporting-service.yaml",
    generatedPath: "docs/test-evidence/generated/openapi/reporting-service.generated.json"
  }
];
const dtoSchemaServiceIds = new Set<ServiceId>(["core-banking", "payment-service", "notification-service", "reporting-service"]);
const coreBankingDtoSchemaOperationKeys = new Set([
  "GET /api/customer/accounts",
  "GET /api/customer/accounts/{accountId}/detail",
  "GET /api/customer/complaint-types",
  "GET /api/customer/complaints",
  "GET /api/customer/recipients/internal-account-lookup",
  "GET /api/staff/aml-cases",
  "GET /api/staff/aml-cases/{caseId}",
  "GET /api/staff/accounts/search",
  "GET /api/staff/complaints",
  "GET /api/staff/complaints/{caseId}",
  "GET /api/staff/customers/search",
  "GET /api/staff/customers/{customerId}/detail",
  "GET /api/staff/fds-cases",
  "GET /api/staff/fds-cases/{caseId}",
  "GET /api/staff/transactions/search",
  "POST /api/customer/complaints",
  "POST /api/customer/complaints/{caseId}/confirm",
  "POST /api/customer/complaints/{caseId}/materials",
  "POST /api/customer/complaints/{caseId}/reopen-requests",
  "POST /api/staff/aml-cases/{caseId}/assign",
  "POST /api/staff/aml-cases/{caseId}/closure-requests",
  "POST /api/staff/aml-cases/{caseId}/comments",
  "POST /api/staff/complaints/{caseId}/answer-drafts",
  "POST /api/staff/fds-cases/{caseId}/assign",
  "POST /api/staff/fds-cases/{caseId}/block-requests",
  "POST /api/staff/fds-cases/{caseId}/release-requests",
  "POST /api/ledger/deposits",
  "POST /api/ledger/withdrawals",
  "POST /api/ledger/transfers",
  "POST /api/ledger/reversals",
  "POST /api/ledger/adjustments",
  "POST /api/ledger/payment-postings",
  "POST /api/ops/daily-closings"
]);

const errors: string[] = [];
const snapshots: GeneratedOpenApiSnapshot[] = [];

for (const service of services) {
  const contractSource = await readUtf8(service.contractPath);
  const contractOperations = parseOpenApiOperations(service.contractPath, contractSource);
  const contractPaths = new Set(contractOperations.map((operation) => operation.path));
  const runtimeOperations = (await extractRuntimeOperations(service.sourceRoot))
    .filter((operation) => inDiffScope(operation, contractPaths))
    .sort(compareOperation);
  const dtoSchemaMap = dtoSchemaServiceIds.has(service.serviceId) ? await extractDtoSchemas(service.sourceRoot) : new Map<string, GeneratedDtoSchema>();
  const dtoSchemaOperations = dtoSchemaOperationsFor(service.serviceId, runtimeOperations);
  addConcreteGenericResponseSchemas(dtoSchemaMap, dtoSchemaOperations);
  const dtoSchemas = dtoSchemaServiceIds.has(service.serviceId)
    ? referencedDtoSchemas(dtoSchemaOperations, dtoSchemaMap)
    : [];
  const snapshot: GeneratedOpenApiSnapshot = {
    generatedAt,
    generator: "kotlin-controller-source",
    serviceId: service.serviceId,
    syntheticOnly: true,
    diffScope: "Spring @RestController operations under /api/** plus checked-in /health operations.",
    operations: runtimeOperations,
    dtoSchemas
  };
  snapshots.push(snapshot);

  const runtimeByKey = new Map(runtimeOperations.map((operation) => [operationKey(operation), operation]));
  const contractByKey = new Map(contractOperations.map((operation) => [operationKey(operation), operation]));

  for (const operation of runtimeOperations) {
    const key = operationKey(operation);
    const contract = contractByKey.get(key);
    if (!contract) {
      errors.push(`${service.serviceId}: runtime operation is missing from checked-in OpenAPI: ${key} (${operation.controllerFile}#${operation.handler})`);
      continue;
    }

    const contractRequestDto = firstSchemaRef(contract.block, "requestBody");
    if (!operation.requestDto && /requestBody:/u.test(contract.block)) {
      errors.push(`${service.serviceId} ${key}: checked-in OpenAPI declares requestBody but controller has no @RequestBody DTO.`);
    }
    if (operation.requestDto && contractRequestDto) {
      if (contractRequestDto !== "AnyJson" && !sameDto(contractRequestDto, operation.requestDto)) {
        errors.push(`${service.serviceId} ${key}: request DTO mismatch, controller=${operation.requestDto}, OpenAPI=${contractRequestDto}.`);
      }
    }

    const responseDto = operation.responseDto;
    const contractResponseDto = firstSchemaRef(contract.block, "responses");
    if (responseDto && contractResponseDto && contractResponseDto !== "AnyJson" && !sameDto(contractResponseDto, responseDto)) {
      errors.push(`${service.serviceId} ${key}: response DTO mismatch, controller=${responseDto}, OpenAPI=${contractResponseDto}.`);
    }
    if (!/StructuredErrorResponse/u.test(contract.block) && !/structuredErrorDefault:\s*['"]?#\/components\/responses\/StructuredErrorResponse/u.test(contractSource)) {
      errors.push(`${service.serviceId} ${key}: structured error response metadata is missing.`);
    }
  }

  for (const operation of contractOperations) {
    const key = operationKey(operation);
    if (!runtimeByKey.has(key)) {
      errors.push(`${service.serviceId}: checked-in OpenAPI operation has no controller route: ${key} (${operation.operationId ?? "missing operationId"}).`);
    }
  }

  if (dtoSchemaServiceIds.has(service.serviceId)) {
    validateDtoSchemas(service, contractSource, dtoSchemaOperations, dtoSchemas);
  }

  await mkdir(dirname(service.generatedPath), { recursive: true });
  await writeFile(service.generatedPath, `${JSON.stringify(snapshot, null, 2)}\n`);
}

failIfErrors("OpenAPI generated source diff", errors);

console.log("OpenAPI generated source diff passed");
for (const snapshot of snapshots) {
  console.log(`- ${snapshot.serviceId}: ${snapshot.operations.length} controller operations matched ${contractPathFor(snapshot.serviceId)}`);
}

async function extractRuntimeOperations(root: string): Promise<RuntimeOperation[]> {
  const controllerFiles = (await listFiles(root)).filter((file) => file.endsWith("Controller.kt"));
  const operations: RuntimeOperation[] = [];
  for (const controllerFile of controllerFiles) {
    const source = await readFile(controllerFile, "utf8");
    if (!source.includes("@RestController")) {
      continue;
    }
    for (const block of restControllerBlocks(source)) {
      const basePath = extractClassRequestMapping(block);
      for (const operation of extractMappedFunctions(block, basePath, controllerFile)) {
        operations.push(operation);
      }
    }
  }
  return operations;
}

async function extractDtoSchemas(root: string): Promise<Map<string, GeneratedDtoSchema>> {
  const files = await listFiles(root);
  const source = (await Promise.all(files.filter((file) => file.endsWith(".kt")).map((file) => readFile(file, "utf8")))).join("\n");
  const schemas = new Map<string, GeneratedDtoSchema>();

  for (const match of source.matchAll(/enum class\s+([A-Za-z][A-Za-z0-9_]*)\s*\{([\s\S]*?)\}/gmu)) {
    const enumValues = match[2]
      .split(/[,\n]/u)
      .map((value) => value.trim())
      .filter((value) => /^[A-Z][A-Z0-9_]*$/u.test(value));
    schemas.set(match[1], {
      name: match[1],
      kind: "enum",
      required: [],
      properties: [],
      enumValues
    });
  }

  const dataClasses = Array.from(source.matchAll(/data class\s+([A-Za-z][A-Za-z0-9_]*)\s*\(([\s\S]*?)\)\s*(?:\{|$)/gmu))
    .map((match) => ({ name: match[1], parameters: match[2] }));
  for (const dataClass of dataClasses) {
    schemas.set(dataClass.name, {
      name: dataClass.name,
      kind: "object",
      required: [],
      properties: [],
      enumValues: []
    });
  }
  for (const dataClass of dataClasses) {
    const properties = splitKotlinParameters(dataClass.parameters)
      .map((parameter) => dtoProperty(parameter.trim(), schemas))
      .filter((property): property is GeneratedDtoProperty => property !== null);
    schemas.set(dataClass.name, {
      name: dataClass.name,
      kind: "object",
      required: properties.filter((property) => property.required).map((property) => property.name),
      properties,
      enumValues: []
    });
  }
  return schemas;
}

function splitKotlinParameters(parameterBlock: string): string[] {
  const parameters: string[] = [];
  let depth = 0;
  let start = 0;
  for (let index = 0; index < parameterBlock.length; index += 1) {
    const char = parameterBlock[index];
    if (char === "<" || char === "(") depth += 1;
    if (char === ">" || char === ")") depth -= 1;
    if (char === "," && depth === 0) {
      parameters.push(parameterBlock.slice(start, index));
      start = index + 1;
    }
  }
  parameters.push(parameterBlock.slice(start));
  return parameters;
}

function dtoProperty(parameter: string, schemas: ReadonlyMap<string, GeneratedDtoSchema>): GeneratedDtoProperty | null {
  const match = /^val\s+([A-Za-z][A-Za-z0-9_]*)\s*:\s*([^=]+?)(?:\s*=\s*[\s\S]+)?$/u.exec(parameter.replace(/\s+/gu, " ").trim());
  if (!match) {
    return null;
  }
  const kotlinType = match[2].trim();
  const nullable = isTopLevelNullable(kotlinType);
  const required = !nullable && !/\s=\s/u.test(parameter);
  return {
    name: match[1],
    kotlinType,
    required,
    nullable,
    ref: dtoRef(kotlinType, schemas),
    itemsRef: listItemDtoRef(kotlinType, schemas)
  };
}

function isTopLevelNullable(kotlinType: string): boolean {
  let depth = 0;
  for (const char of kotlinType.replace(/\s+/gu, "")) {
    if (char === "<") depth += 1;
    if (char === ">") depth -= 1;
    if (char === "?" && depth === 0) {
      return true;
    }
  }
  return false;
}

function dtoRef(kotlinType: string, schemas: ReadonlyMap<string, GeneratedDtoSchema>): string | null {
  const cleaned = cleanKotlinType(kotlinType);
  if (schemas.has(cleaned)) {
    return cleaned;
  }
  return null;
}

function listItemDtoRef(kotlinType: string, schemas: ReadonlyMap<string, GeneratedDtoSchema>): string | null {
  const match = /^(?:List|Set|Collection)<([A-Za-z][A-Za-z0-9_]*)\??>$/u.exec(cleanKotlinType(kotlinType, false));
  return match && schemas.has(match[1]) ? match[1] : null;
}

function cleanKotlinType(kotlinType: string, stripGeneric = true): string {
  const cleaned = kotlinType.replace(/\s+/gu, "").replace(/\?/gu, "");
  if (!stripGeneric) {
    return cleaned;
  }
  return cleaned.replace(/^(?:List|Set|Collection)</u, "").replace(/>$/u, "");
}

function referencedDtoSchemas(
  operations: readonly RuntimeOperation[],
  schemas: ReadonlyMap<string, GeneratedDtoSchema>
): GeneratedDtoSchema[] {
  const queue = operations
    .flatMap((operation) => [operation.requestDto, operation.responseDto])
    .filter((name): name is string => name !== null);
  const seen = new Set<string>();
  const result: GeneratedDtoSchema[] = [];
  while (queue.length > 0) {
    const name = queue.shift() ?? "";
    if (seen.has(name)) {
      continue;
    }
    const schema = schemas.get(name);
    if (!schema) {
      continue;
    }
    seen.add(name);
    result.push(schema);
    for (const property of schema.properties) {
      if (property.ref) queue.push(property.ref);
      if (property.itemsRef) queue.push(property.itemsRef);
    }
  }
  return result.sort((left, right) => left.name.localeCompare(right.name));
}

function dtoSchemaOperationsFor(serviceId: ServiceId, operations: readonly RuntimeOperation[]): readonly RuntimeOperation[] {
  if (serviceId !== "core-banking") {
    return operations;
  }
  return operations.filter((operation) => coreBankingDtoSchemaOperationKeys.has(operationKey(operation)));
}

function addConcreteGenericResponseSchemas(
  schemas: Map<string, GeneratedDtoSchema>,
  operations: readonly RuntimeOperation[]
): void {
  for (const operation of operations) {
    const parsed = parseConcreteGenericResponseName(operation.responseDto);
    if (!parsed || schemas.has(operation.responseDto ?? "")) {
      continue;
    }
    if (!schemas.has(parsed.itemDto)) {
      errors.push(`${operationKey(operation)}: generated generic response references unknown DTO ${parsed.itemDto}.`);
      continue;
    }
    const itemProperty: GeneratedDtoProperty = {
      name: parsed.kind === "list" ? "items" : "item",
      kotlinType: parsed.kind === "list" ? `List<${parsed.itemDto}>` : parsed.itemDto,
      required: true,
      nullable: false,
      ref: parsed.itemDto,
      itemsRef: parsed.kind === "list" ? parsed.itemDto : null
    };
    schemas.set(parsed.name, {
      name: parsed.name,
      kind: "object",
      required: ["auditEventId", itemProperty.name],
      properties: [
        {
          name: "auditEventId",
          kotlinType: "String",
          required: true,
          nullable: false,
          ref: null,
          itemsRef: null
        },
        itemProperty
      ],
      enumValues: []
    });
  }
}

function parseConcreteGenericResponseName(
  schemaName: string | null
): { readonly name: string; readonly kind: "item" | "list"; readonly itemDto: string } | null {
  const match = /^(StaffAccess(?:Item|List)Response)_([A-Za-z][A-Za-z0-9_]*)$/u.exec(schemaName ?? "");
  if (!match) {
    return null;
  }
  return {
    name: schemaName ?? "",
    kind: match[1] === "StaffAccessListResponse" ? "list" : "item",
    itemDto: match[2]
  };
}

function validateDtoSchemas(
  service: ServiceConfig,
  contractSource: string,
  operations: readonly RuntimeOperation[],
  dtoSchemas: readonly GeneratedDtoSchema[]
): void {
  for (const operation of operations) {
    if (operation.requestDto && !operationHasNonGenericSchema(service.contractPath, contractSource, operation, "requestBody")) {
      errors.push(`${service.serviceId} ${operationKey(operation)}: request DTO ${operation.requestDto} must use a concrete OpenAPI schema.`);
    }
    if (operation.responseDto && !operationHasNonGenericSchema(service.contractPath, contractSource, operation, "responses")) {
      errors.push(`${service.serviceId} ${operationKey(operation)}: response DTO ${operation.responseDto} must use a concrete OpenAPI schema.`);
    }
  }

  for (const schema of dtoSchemas) {
    if (schema.kind === "enum") {
      for (const usage of enumUsages(contractSource, dtoSchemas, schema.name)) {
        for (const value of schema.enumValues) {
          if (!usage.block.includes(value)) {
            errors.push(`${service.serviceId} ${usage.componentName}.${usage.propertyName}: OpenAPI enum for ${schema.name} is missing ${value}.`);
          }
        }
      }
      continue;
    }
    const component = componentSchemaBlockForDto(contractSource, schema.name);
    if (!component) {
      errors.push(`${service.serviceId}: generated DTO schema ${schema.name} is missing from checked-in OpenAPI components.`);
      continue;
    }
    const block = component.block;
    if (schema.properties.length > 0 && !/properties:/u.test(block)) {
      errors.push(`${service.serviceId} ${component.name}: OpenAPI component has no properties block.`);
      continue;
    }
    for (const property of schema.properties) {
      if (!new RegExp(`\\n\\s{8}${property.name}:`, "u").test(block)) {
        errors.push(`${service.serviceId} ${component.name}: OpenAPI component is missing property ${property.name}.`);
      }
    }
  }
}

function operationHasNonGenericSchema(contractPath: string, contractSource: string, operation: RuntimeOperation, section: "requestBody" | "responses"): boolean {
  const operations = parseOpenApiOperations(contractPath, contractSource);
  const block = operations.find((contractOperation) => operationKey(contractOperation) === operationKey(operation))?.block;
  if (!block) {
    return false;
  }
  const schemaRef = firstSchemaRef(block, section);
  return schemaRef !== null && schemaRef !== "AnyJson";
}

function componentSchemaBlockForDto(contractSource: string, schemaName: string): { readonly name: string; readonly block: string } | null {
  for (const candidate of new Set([schemaName, canonicalDto(schemaName)])) {
    const block = componentSchemaBlock(contractSource, candidate);
    if (block) {
      return { name: candidate, block };
    }
  }
  return null;
}

function componentSchemaBlock(contractSource: string, schemaName: string): string | null {
  const match = new RegExp(`^    ${schemaName}:\\n`, "mu").exec(contractSource);
  if (!match || match.index === undefined) {
    return null;
  }
  const rest = contractSource.slice(match.index + match[0].length);
  const next = /^    [A-Za-z][A-Za-z0-9_]*:\n/mu.exec(rest);
  return rest.slice(0, next?.index ?? rest.length);
}

function enumUsages(
  contractSource: string,
  dtoSchemas: readonly GeneratedDtoSchema[],
  enumName: string
): Array<{ readonly componentName: string; readonly propertyName: string; readonly block: string }> {
  const usages: Array<{ readonly componentName: string; readonly propertyName: string; readonly block: string }> = [];
  for (const schema of dtoSchemas) {
    if (schema.kind !== "object") {
      continue;
    }
    const enumProperties = schema.properties.filter((property) => property.ref === enumName);
    if (enumProperties.length === 0) {
      continue;
    }
    const component = componentSchemaBlockForDto(contractSource, schema.name);
    if (!component) {
      continue;
    }
    for (const property of enumProperties) {
      const block = componentPropertyBlock(component.block, property.name);
      if (block) {
        const referencedEnumBlock = block.includes(`#/components/schemas/${enumName}`)
          ? componentSchemaBlock(contractSource, enumName)
          : null;
        usages.push({ componentName: component.name, propertyName: property.name, block: referencedEnumBlock ?? block });
      }
    }
  }
  if (usages.length > 0) {
    return usages;
  }
  return [{ componentName: "components.schemas", propertyName: enumName, block: contractSource }];
}

function componentPropertyBlock(componentBlock: string, propertyName: string): string | null {
  const match = new RegExp(`^        ${escapeRegExp(propertyName)}:\\n`, "mu").exec(componentBlock);
  if (!match || match.index === undefined) {
    return null;
  }
  const rest = componentBlock.slice(match.index + match[0].length);
  const next = /^        [A-Za-z][A-Za-z0-9_]*:\n/mu.exec(rest);
  return rest.slice(0, next?.index ?? rest.length);
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/gu, "\\$&");
}

async function listFiles(root: string): Promise<string[]> {
  const entries = await readdir(root, { withFileTypes: true });
  const files: string[] = [];
  for (const entry of entries) {
    const entryPath = join(root, entry.name).replaceAll("\\", "/");
    if (entry.isDirectory()) {
      files.push(...await listFiles(entryPath));
    } else {
      files.push(entryPath);
    }
  }
  return files.sort();
}

function restControllerBlocks(source: string): string[] {
  const starts = Array.from(source.matchAll(/@RestController/gmu)).map((match) => match.index ?? 0);
  return starts.map((start, index) => source.slice(start, index + 1 < starts.length ? starts[index + 1] : source.length));
}

function extractClassRequestMapping(block: string): string {
  return mappingPath(/@RequestMapping\s*\(([^)]*)\)/u.exec(block)?.[1] ?? "");
}

function extractMappedFunctions(block: string, basePath: string, controllerFile: string): RuntimeOperation[] {
  const operations: RuntimeOperation[] = [];
  const mappingRegex =
    /@(Get|Post|Put|Patch|Delete)Mapping\s*(?:\(([^)]*)\))?[\s\S]{0,900}?fun\s+([A-Za-z][A-Za-z0-9_]*)\s*\(([\s\S]*?)\)\s*:\s*([^\n={]+)/gmu;
  for (const match of block.matchAll(mappingRegex)) {
    const method = match[1].toUpperCase();
    const path = normalizePath(basePath, mappingPath(match[2] ?? ""));
    const handler = match[3];
    const params = match[4];
    const returnType = match[5].trim();
    operations.push({
      method,
      path,
      handler,
      controllerFile,
      requestDto: requestDto(params),
      responseDto: responseDto(returnType)
    });
  }
  return operations;
}

function mappingPath(mappingArgs: string): string {
  if (!mappingArgs.trim()) {
    return "";
  }
  const direct = /^"([^"]*)"/u.exec(mappingArgs.trim())?.[1];
  if (direct !== undefined) {
    return direct;
  }
  return /(?:value|path)\s*=\s*"([^"]*)"/u.exec(mappingArgs)?.[1] ?? "";
}

function normalizePath(basePath: string, methodPath: string): string {
  const joined = `${basePath}${methodPath}`.replaceAll("\\", "/").replace(/\/{2,}/gu, "/");
  if (!joined) {
    return "/";
  }
  return joined.startsWith("/") ? joined : `/${joined}`;
}

function requestDto(params: string): string | null {
  const match = /@RequestBody\s+(?:val\s+)?[A-Za-z][A-Za-z0-9_]*\s*:\s*([A-Za-z][A-Za-z0-9_<>?]*)/u.exec(params);
  return match ? normalizeDto(match[1]) : null;
}

function responseDto(returnType: string): string | null {
  return normalizeDto(returnType);
}

function normalizeDto(typeName: string): string | null {
  const compact = typeName.replace(/\s+/gu, "").replace(/\?/gu, "");
  const responseEntity = unwrapGeneric(compact, "ResponseEntity");
  if (responseEntity) {
    return normalizeDto(responseEntity);
  }
  for (const wrapper of ["List", "Set", "Collection"]) {
    const itemType = unwrapGeneric(compact, wrapper);
    if (itemType) {
      return normalizeDto(itemType);
    }
  }
  const mapValue = /^Map<[^,>]+,(.+)>$/u.exec(compact)?.[1];
  if (mapValue) {
    return normalizeDto(mapValue);
  }
  const concreteGeneric = concreteGenericResponseSchemaName(compact);
  const cleaned = concreteGeneric ?? compact.replace(/>+$/u, "");
  if (!cleaned || ["String", "Int", "Long", "Boolean", "Unit", "HttpServletRequest"].includes(cleaned)) {
    return null;
  }
  return cleaned;
}

function unwrapGeneric(typeName: string, wrapper: string): string | null {
  const prefix = `${wrapper}<`;
  return typeName.startsWith(prefix) && typeName.endsWith(">")
    ? typeName.slice(prefix.length, -1)
    : null;
}

function concreteGenericResponseSchemaName(typeName: string): string | null {
  const match = /^(StaffAccess(?:Item|List)Response)<([A-Za-z][A-Za-z0-9_]*)>$/u.exec(typeName);
  return match ? `${match[1]}_${match[2]}` : null;
}

function sameDto(contractDto: string, runtimeDto: string): boolean {
  return canonicalDto(contractDto) === canonicalDto(runtimeDto);
}

function canonicalDto(typeName: string): string {
  return typeName.replace(/Dto$/u, "");
}

function firstSchemaRef(block: string, section: "requestBody" | "responses"): string | null {
  const sectionStart = block.indexOf(`${section}:`);
  if (sectionStart < 0) {
    return null;
  }
  const sectionBlock = block.slice(sectionStart);
  const schemaRef = /\$ref:\s*['"]?#\/components\/schemas\/([A-Za-z][A-Za-z0-9_]*)/u.exec(sectionBlock)?.[1];
  if (schemaRef) {
    return schemaRef;
  }
  const responseRef = /\$ref:\s*['"]?#\/components\/responses\/AnyJsonResponse/u.test(sectionBlock);
  return responseRef ? "AnyJson" : null;
}

function inDiffScope(operation: RuntimeOperation, contractPaths: ReadonlySet<string>): boolean {
  return operation.path.startsWith("/api/") || (operation.path === "/health" && contractPaths.has("/health"));
}

function operationKey(operation: { readonly method: string; readonly path: string }): string {
  return `${operation.method} ${operation.path}`;
}

function compareOperation(left: RuntimeOperation, right: RuntimeOperation): number {
  return operationKey(left).localeCompare(operationKey(right));
}

function contractPathFor(serviceId: ServiceId): string {
  return openApiContractFiles.find((file) => file.includes(serviceId === "core-banking" ? "core-banking" : serviceId)) ?? serviceId;
}
