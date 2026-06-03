import { readdir, readFile } from "node:fs/promises";
import { join } from "node:path";
import type { ManifestField, ScreenManifest, ScreenType } from "./types";

const TEMPLATE_TYPES = new Set<string>(["inquiry", "command", "case", "parameter", "dashboard"]);
const SCREEN_TYPES = new Set<string>(["INQUIRY", "COMMAND", "CASE", "PARAMETER", "DASHBOARD"]);
const CONVENTION_VERSION = 1;

type TemplateConvention = {
  template: string;
  purpose: string;
  requiredManifestKeys: readonly string[];
  regions: readonly string[];
  controlMetadata: readonly string[];
  expectedControls: readonly string[];
};

export const TEMPLATE_CONVENTIONS = Object.freeze({
  INQUIRY: Object.freeze({
    template: "inquiry",
    purpose: "Reason-aware search, result table, detail panel, masking, and audit evidence.",
    requiredManifestKeys: Object.freeze(["query", "resultTable"]),
    regions: Object.freeze(["search", "resultTable", "detailPanel", "auditPanel"]),
    controlMetadata: Object.freeze(["requiredRoles", "audit", "masking"]),
    expectedControls: Object.freeze(["role-aware", "audit-logged", "masking-aware", "reason-aware"])
  }),
  COMMAND: Object.freeze({
    template: "command",
    purpose: "Target lookup, before/after snapshot, validation, approval, and command audit.",
    requiredManifestKeys: Object.freeze(["fields", "api"]),
    regions: Object.freeze(["targetLookup", "beforeSnapshot", "commandForm", "approvalPanel", "afterSnapshot", "auditPanel"]),
    controlMetadata: Object.freeze(["requiredRoles", "audit", "approval", "validation"]),
    expectedControls: Object.freeze(["role-aware", "audit-logged", "validation-aware", "approval-aware"])
  }),
  CASE: Object.freeze({
    template: "case",
    purpose: "Case status, owner, SLA, comments, attachments, workflow timeline, and approval history.",
    requiredManifestKeys: Object.freeze(["workflow"]),
    regions: Object.freeze(["caseSummary", "ownerPanel", "slaPanel", "comments", "attachments", "approvalTimeline", "workflowTimeline", "auditPanel"]),
    controlMetadata: Object.freeze(["requiredRoles", "audit", "workflow", "approval", "sla"]),
    expectedControls: Object.freeze(["role-aware", "audit-logged", "workflow-aware", "approval-aware"])
  }),
  PARAMETER: Object.freeze({
    template: "parameter",
    purpose: "Current value, scheduled value, effective date, approval, rollback, and parameter audit.",
    requiredManifestKeys: Object.freeze(["parameter", "fields", "api", "approval"]),
    regions: Object.freeze(["currentValue", "scheduledValue", "effectiveDate", "approvalPanel", "rollbackPanel", "auditPanel"]),
    controlMetadata: Object.freeze(["requiredRoles", "audit", "approval", "rollback"]),
    expectedControls: Object.freeze(["role-aware", "audit-logged", "approval-aware", "rollback-aware"])
  })
}) satisfies Record<Exclude<ScreenType, "DASHBOARD">, TemplateConvention>;

export type ExpandedScreenManifest = ScreenManifest & {
  sourcePath?: string;
  conventionVersion: number;
  templateContract: {
    type: ScreenType;
    template: string;
    purpose: string;
    requiredManifestKeys: string[];
    regions: string[];
    controlMetadata: string[];
    expectedControls: string[];
  };
  controlMetadata: ReturnType<typeof buildControlMetadata>;
  formContract: {
    fields: ManifestField[];
    requiredFieldNames: string[];
    reasonFieldNames: string[];
  };
};

async function walkJsonFiles(dir: string): Promise<string[]> {
  const entries = await readdir(dir, { withFileTypes: true });
  const files: string[] = [];
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

function requireField(manifest: ScreenManifest | Record<string, unknown>, field: string): void {
  const value = (manifest as Record<string, unknown>)[field];
  if (value === undefined || value === null || value === "") {
    throw new Error(`${manifest.screenId || "unknown manifest"} missing ${field}`);
  }
}

function collectFields(manifest: ScreenManifest): ManifestField[] {
  if (manifest.type === "INQUIRY") {
    return manifest.query?.fields || [];
  }
  if (manifest.type === "COMMAND" || manifest.type === "PARAMETER") {
    return manifest.fields || [];
  }
  return [];
}

function collectMaskingFields(manifest: ScreenManifest): Array<{ name: string; mask: string }> {
  const fields = collectFields(manifest);
  return fields.filter((field): field is ManifestField & { mask: string } => Boolean(field.mask)).map((field) => ({
    name: field.name,
    mask: field.mask
  }));
}

function buildControlMetadata(manifest: ScreenManifest) {
  const maskingPolicy = manifest.audit.maskingPolicy || "NONE";
  return {
    roles: {
      required: [...manifest.requiredRoles],
      roleAwareMenu: manifest.app !== "customer-web"
    },
    audit: {
      enabled: manifest.audit.enabled === true,
      reasonRequired: manifest.audit.reasonRequired === true,
      eventTypes: manifest.audit.eventTypes || [],
      piiAccess: manifest.audit.piiAccess === true,
      selfService: manifest.audit.selfService === true,
      maskingPolicy
    },
    masking: {
      policy: maskingPolicy,
      defaultMasked: manifest.audit.piiAccess === true || maskingPolicy !== "NONE",
      fields: collectMaskingFields(manifest)
    },
    approval: {
      required: manifest.approval?.required === true,
      makerChecker: manifest.approval?.makerChecker === true,
      approverRole: manifest.approval?.approverRole || null,
      businessTypes: manifest.approval?.businessTypes || []
    },
    workflow: {
      required: manifest.type === "CASE",
      name: manifest.workflow?.name || null,
      states: manifest.workflow?.states || [],
      timelineRequired: manifest.type === "CASE"
    },
    highRisk: manifest.highRisk === true,
    syntheticOnly: true
  };
}

export function getTemplateConvention(typeOrManifest: ScreenType | Pick<ScreenManifest, "type"> | null | undefined): TemplateConvention | null {
  const type = typeof typeOrManifest === "string" ? typeOrManifest : typeOrManifest?.type;
  if (!type || type === "DASHBOARD") {
    return null;
  }
  return TEMPLATE_CONVENTIONS[type] || null;
}

export function expandManifest(manifest: ScreenManifest): ExpandedScreenManifest {
  validateManifest(manifest);
  const convention = getTemplateConvention(manifest);
  const fields = collectFields(manifest);
  return {
    ...manifest,
    conventionVersion: CONVENTION_VERSION,
    templateContract: convention ? {
      type: manifest.type,
      template: convention.template,
      purpose: convention.purpose,
      requiredManifestKeys: [...convention.requiredManifestKeys],
      regions: [...convention.regions],
      controlMetadata: [...convention.controlMetadata],
      expectedControls: [...convention.expectedControls]
    } : {
      type: manifest.type,
      template: manifest.layout.template,
      purpose: "Dashboard composition manifest.",
      requiredManifestKeys: ["widgets"],
      regions: manifest.widgets || [],
      controlMetadata: ["requiredRoles", "audit"],
      expectedControls: ["role-aware", "audit-logged"]
    },
    controlMetadata: buildControlMetadata(manifest),
    formContract: {
      fields,
      requiredFieldNames: fields.filter((field) => field.required === true).map((field) => field.name),
      reasonFieldNames: fields.filter((field) => field.name.toLowerCase().includes("reason")).map((field) => field.name)
    }
  };
}

export function expandManifests(manifests: ScreenManifest[]): ExpandedScreenManifest[] {
  return manifests.map((manifest) => expandManifest(manifest));
}

export function validateManifest(manifest: ScreenManifest): true {
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
  if (
    manifest.app === "staff-terminal" &&
    manifest.type === "INQUIRY" &&
    manifest.audit.piiAccess === true &&
    (!Array.isArray(manifest.audit.eventTypes) || manifest.audit.eventTypes.length === 0)
  ) {
    throw new Error(`${manifest.screenId} staff PII inquiry must declare audit.eventTypes`);
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
  if (manifest.type === "PARAMETER") {
    for (const field of ["parameter", "fields", "api"]) {
      requireField(manifest, field);
    }
    if (manifest.approval?.makerChecker !== true) {
      throw new Error(`${manifest.screenId} parameter screen must use maker-checker approval`);
    }
  }
  return true;
}

export async function loadManifests(rootDir = "screen-manifests"): Promise<Array<ScreenManifest & { sourcePath: string }>> {
  const files = await walkJsonFiles(rootDir);
  const manifests: Array<ScreenManifest & { sourcePath: string }> = [];
  for (const file of files) {
    const manifest = JSON.parse(await readFile(file, "utf8")) as ScreenManifest;
    validateManifest(manifest);
    manifests.push({ ...manifest, sourcePath: file });
  }
  return manifests.sort((a, b) => a.screenId.localeCompare(b.screenId));
}

export async function loadExpandedManifests(rootDir = "screen-manifests"): Promise<ExpandedScreenManifest[]> {
  return expandManifests(await loadManifests(rootDir));
}

export function filterManifestsByApp<T extends ScreenManifest>(manifests: T[], app: string): T[] {
  return manifests.filter((manifest) => manifest.app === app);
}
