export function validateRequiredFields(fields, values) {
  const errors = [];
  for (const field of fields || []) {
    if (field.required && (values[field.name] === undefined || values[field.name] === null || values[field.name] === "")) {
      errors.push({
        field: field.name,
        message: `${field.label || field.name} is required`
      });
    }
  }
  return {
    ok: errors.length === 0,
    errors
  };
}

export function fieldsFromManifest(manifest) {
  if (manifest?.type === "INQUIRY") {
    return manifest.query?.fields || [];
  }
  if (manifest?.type === "COMMAND" || manifest?.type === "PARAMETER") {
    return manifest.fields || [];
  }
  return [];
}

export function validateReason(reason) {
  return {
    ok: typeof reason === "string" && reason.trim().length >= 8,
    errors: typeof reason === "string" && reason.trim().length >= 8 ? [] : [
      {
        field: "reason",
        message: "Business reason must be at least 8 characters"
      }
    ]
  };
}

export function validateManifestSubmission(manifest, values) {
  const fields = fieldsFromManifest(manifest);
  const required = validateRequiredFields(fields, values);
  const reasonField = fields.find((field) => field.name.toLowerCase().includes("reason"));
  const reason = manifest?.audit?.reasonRequired === true
    ? validateReason(values?.[reasonField?.name || "reason"])
    : { ok: true, errors: [] };

  return {
    ok: required.ok && reason.ok,
    errors: [...required.errors, ...reason.errors]
  };
}
