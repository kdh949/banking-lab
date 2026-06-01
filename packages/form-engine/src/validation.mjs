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
