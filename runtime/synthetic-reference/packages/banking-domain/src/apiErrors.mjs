export const API_ERROR_CONTRACT_VERSION = "2026-06-02";
export const API_ERROR_DOCS = "docs/migration/structured-api-error-contract.md";

const ERROR_RULES = [
  {
    match: /requires a business reason/i,
    code: "POLICY_REASON_REQUIRED",
    statusCode: 400,
    domain: "audit",
    policy: "REASON_REQUIRED",
    cause: "A sensitive or high-risk banking operation was requested without an explicit business reason.",
    fix: "Retry with a non-empty reason tied to the customer, account, case, approval, or reconciliation context."
  },
  {
    match: /cannot unmask pii|mock user is not a customer|does not belong to customer/i,
    code: "AUTHORIZATION_POLICY_VIOLATION",
    statusCode: 403,
    domain: "auth",
    policy: "ROLE_OR_CONTEXT_REQUIRED",
    cause: "The actor is not authorized for the requested synthetic banking context.",
    fix: "Use an actor role and customer/account/case context allowed by the screen manifest and masking policy."
  },
  {
    match: /maker and checker must be different/i,
    code: "MAKER_CHECKER_SELF_APPROVAL_REJECTED",
    statusCode: 409,
    domain: "maker-checker",
    policy: "MAKER_CHECKER_SEPARATION_OF_DUTIES",
    cause: "The requester attempted to approve the same high-risk operation.",
    fix: "Submit approval with a different checker actor who has the required approval role."
  },
  {
    match: /insufficient available balance/i,
    code: "LEDGER_INSUFFICIENT_AVAILABLE_BALANCE",
    statusCode: 409,
    domain: "ledger",
    invariant: "available_balance <= ledger_balance and debit commands cannot overdraw available balance",
    cause: "The debit amount exceeds the projected available balance derived from postings.",
    fix: "Retry with a lower amount, release a hold through the modeled workflow, or use an approved adjustment/reversal flow."
  },
  {
    match: /business day is closed/i,
    code: "LEDGER_CLOSED_DAY_IMMUTABLE",
    statusCode: 409,
    domain: "ledger",
    invariant: "closed day cannot be mutated directly",
    cause: "A command attempted to post directly on a closed business date.",
    fix: "Use an explicitly modeled reversal or adjustment transaction on an open business date."
  },
  {
    match: /already reversed|reversal transactions cannot be reversed directly/i,
    code: "LEDGER_REVERSAL_POLICY_VIOLATION",
    statusCode: 409,
    domain: "ledger",
    invariant: "reversal references original transaction and duplicate reversals are rejected",
    cause: "The reversal command violates the append-only correction policy.",
    fix: "Use the original reversal idempotency key for replay, or create an approved adjustment if a new correction is required."
  },
  {
    match: /positive integer|is required|must differ|must include|body is required|invalid json/i,
    code: "REQUEST_VALIDATION_FAILED",
    statusCode: 400,
    domain: "validation",
    cause: "The request is missing a required field or contains an invalid command value.",
    fix: "Correct the request payload or query parameters according to the API contract and screen manifest."
  },
  {
    match: /not found/i,
    code: "RESOURCE_NOT_FOUND",
    statusCode: 404,
    domain: "resource",
    cause: "The requested synthetic resource does not exist in the current lab state.",
    fix: "Use a seeded synthetic identifier or create the resource through the modeled workflow first."
  },
  {
    match: /not waiting|only pending|unsupported .* action|invalid .* transition/i,
    code: "WORKFLOW_STATE_VIOLATION",
    statusCode: 409,
    domain: "workflow",
    policy: "VALID_WORKFLOW_TRANSITION_REQUIRED",
    cause: "The requested workflow transition is not valid from the current state.",
    fix: "Inspect the case or approval timeline and retry with an allowed state transition."
  }
];

function messageOf(error) {
  if (typeof error === "string") {
    return error;
  }
  if (error instanceof SyntaxError) {
    return "invalid JSON request body";
  }
  return error?.message || "unexpected banking lab runtime error";
}

function fallbackStatusCode(statusCode) {
  return Number.isInteger(statusCode) && statusCode >= 400 ? statusCode : 500;
}

export function isApiError(value) {
  return Boolean(value && typeof value === "object" && typeof value.code === "string" && typeof value.message === "string");
}

export function createApiError(input, context = {}) {
  const statusCode = fallbackStatusCode(input.statusCode || context.statusCode);
  const requestId = input.requestId || context.requestId || "REQ-UNSPECIFIED";
  return {
    contractVersion: API_ERROR_CONTRACT_VERSION,
    code: input.code,
    message: input.message,
    statusCode,
    domain: input.domain,
    invariant: input.invariant || null,
    policy: input.policy || null,
    cause: input.cause,
    fix: input.fix,
    requestId,
    correlationId: input.correlationId || context.correlationId || requestId,
    route: context.route || input.route || null,
    docs: input.docs || API_ERROR_DOCS,
    syntheticOnly: true
  };
}

export function inferApiError(error, context = {}) {
  const message = messageOf(error);
  const rule = ERROR_RULES.find((candidate) => candidate.match.test(message));
  if (rule) {
    return createApiError({
      ...rule,
      statusCode: context.statusCode || rule.statusCode,
      message
    }, context);
  }

  return createApiError({
    code: "INTERNAL_RUNTIME_ERROR",
    statusCode: context.statusCode || 500,
    domain: "runtime",
    message,
    cause: "The reference runtime raised an unmapped error.",
    fix: "Inspect the failing route, add a domain-specific error mapping if this becomes part of the migration parity surface."
  }, context);
}
