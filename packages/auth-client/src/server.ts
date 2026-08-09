import { createHash, randomBytes } from "node:crypto";

export type BffSessionMode = "OIDC" | "SIMULATED" | "SYNTHETIC_CUSTOMER_AUTH";

export type BffActor = {
  readonly actorKey: string;
  readonly subject: string;
  readonly roles: readonly string[];
  readonly customerId?: string;
  readonly displayName: string;
};

export type BffSessionView = {
  readonly authenticated: true;
  readonly sessionId: string;
  readonly subject: string;
  readonly roles: readonly string[];
  readonly customerId?: string;
  readonly displayName: string;
  readonly expiresAt: string;
  readonly mode: BffSessionMode;
};

type BffSessionRecord = BffSessionView & {
  readonly appId: string;
  readonly bearerToken: string;
};

type PendingOidcRecord = {
  readonly appId: string;
  readonly sessionId: string;
  readonly state: string;
  readonly codeVerifier: string;
  readonly returnTo: string;
  readonly expiresAtEpochMs: number;
};

type BffSessionStore = {
  readonly authenticated: Map<string, BffSessionRecord>;
  readonly pendingOidc: Map<string, PendingOidcRecord>;
};

const globalStore = globalThis as typeof globalThis & {
  __bankingLabBffSessionStore?: BffSessionStore;
};

const store = globalStore.__bankingLabBffSessionStore ?? {
  authenticated: new Map<string, BffSessionRecord>(),
  pendingOidc: new Map<string, PendingOidcRecord>()
};
globalStore.__bankingLabBffSessionStore = store;

export function beginOidcSession(appId: string, returnTo: string) {
  purgeExpiredSessions();
  const sessionId = randomOpaqueValue(32);
  const state = randomOpaqueValue(24);
  const codeVerifier = randomOpaqueValue(48);
  const codeChallenge = createHash("sha256").update(codeVerifier).digest("base64url");
  store.pendingOidc.set(sessionKey(appId, sessionId), {
    appId,
    sessionId,
    state,
    codeVerifier,
    returnTo,
    expiresAtEpochMs: Date.now() + 5 * 60_000
  });
  return { sessionId, state, codeChallenge };
}

export function completeOidcSession(
  appId: string,
  sessionId: string,
  state: string,
  bearerToken: string,
  expiresInSeconds: number
): { readonly session: BffSessionView; readonly returnTo: string } | null {
  purgeExpiredSessions();
  const key = sessionKey(appId, sessionId);
  const pending = store.pendingOidc.get(key);
  if (!pending || pending.state !== state) {
    return null;
  }
  const claims = jwtClaims(bearerToken);
  const subject = stringClaim(claims, "preferred_username") ?? stringClaim(claims, "sub") ?? "oidc-user";
  const roles = rolesFromClaims(claims);
  const customerId = stringClaim(claims, "customerId")
    ?? stringClaim(claims, "customer_id")
    ?? stringClaim(claims, "banking_lab_customer_id");
  const session = saveAuthenticatedSession({
    appId,
    sessionId,
    bearerToken,
    subject,
    roles,
    customerId,
    displayName: subject,
    expiresInSeconds,
    mode: "OIDC"
  });
  store.pendingOidc.delete(key);
  return { session, returnTo: pending.returnTo };
}

export function createAuthenticatedBffSession(input: {
  readonly appId: string;
  readonly bearerToken: string;
  readonly subject: string;
  readonly roles: readonly string[];
  readonly customerId?: string;
  readonly displayName: string;
  readonly expiresInSeconds?: number;
  readonly mode: BffSessionMode;
}): BffSessionView {
  purgeExpiredSessions();
  return saveAuthenticatedSession({
    ...input,
    sessionId: randomOpaqueValue(32),
    expiresInSeconds: input.expiresInSeconds ?? 3600
  });
}

export function pendingOidcVerifier(appId: string, sessionId: string, state: string): string | null {
  purgeExpiredSessions();
  const pending = store.pendingOidc.get(sessionKey(appId, sessionId));
  return pending?.state === state ? pending.codeVerifier : null;
}

export function authenticatedBffSession(appId: string, sessionId: string | undefined): BffSessionRecord | null {
  if (!sessionId) {
    return null;
  }
  purgeExpiredSessions();
  return store.authenticated.get(sessionKey(appId, sessionId)) ?? null;
}

export function destroyBffSession(appId: string, sessionId: string | undefined) {
  if (!sessionId) {
    return;
  }
  const key = sessionKey(appId, sessionId);
  store.authenticated.delete(key);
  store.pendingOidc.delete(key);
}

export function bffSessionCookieName(appId: string): string {
  return `banking-lab-${appId}-sid`;
}

function saveAuthenticatedSession(input: {
  readonly appId: string;
  readonly sessionId: string;
  readonly bearerToken: string;
  readonly subject: string;
  readonly roles: readonly string[];
  readonly customerId?: string;
  readonly displayName: string;
  readonly expiresInSeconds: number;
  readonly mode: BffSessionMode;
}): BffSessionView {
  const expiresAt = new Date(Date.now() + Math.max(60, Math.min(input.expiresInSeconds, 3600)) * 1000).toISOString();
  const session: BffSessionRecord = {
    authenticated: true,
    appId: input.appId,
    sessionId: input.sessionId,
    bearerToken: input.bearerToken,
    subject: input.subject,
    roles: [...new Set(input.roles)].sort(),
    customerId: input.customerId,
    displayName: input.displayName,
    expiresAt,
    mode: input.mode
  };
  store.authenticated.set(sessionKey(input.appId, input.sessionId), session);
  return sessionView(session);
}

function sessionView(record: BffSessionRecord): BffSessionView {
  const { appId: _appId, bearerToken: _bearerToken, ...view } = record;
  return view;
}

function purgeExpiredSessions() {
  const now = Date.now();
  for (const [key, value] of store.pendingOidc) {
    if (value.expiresAtEpochMs <= now) {
      store.pendingOidc.delete(key);
    }
  }
  for (const [key, value] of store.authenticated) {
    if (Date.parse(value.expiresAt) <= now) {
      store.authenticated.delete(key);
    }
  }
}

function sessionKey(appId: string, sessionId: string) {
  return `${appId}:${sessionId}`;
}

function randomOpaqueValue(bytes: number) {
  return randomBytes(bytes).toString("base64url");
}

function jwtClaims(bearerToken: string): Record<string, unknown> {
  const token = bearerToken.replace(/^Bearer\s+/u, "");
  const payload = token.split(".")[1];
  if (!payload) {
    return {};
  }
  try {
    return JSON.parse(Buffer.from(payload, "base64url").toString("utf8")) as Record<string, unknown>;
  } catch {
    return {};
  }
}

function rolesFromClaims(claims: Record<string, unknown>): readonly string[] {
  const roles = new Set<string>();
  addRoles(roles, claims.roles);
  const realmAccess = objectClaim(claims.realm_access);
  addRoles(roles, realmAccess?.roles);
  const resourceAccess = objectClaim(claims.resource_access);
  for (const clientAccess of Object.values(resourceAccess ?? {})) {
    addRoles(roles, objectClaim(clientAccess)?.roles);
  }
  return [...roles].sort();
}

function addRoles(target: Set<string>, value: unknown) {
  if (Array.isArray(value)) {
    value.forEach((role) => typeof role === "string" && role && target.add(role));
  } else if (typeof value === "string") {
    value.split(/[ ,]+/u).filter(Boolean).forEach((role) => target.add(role));
  }
}

function objectClaim(value: unknown): Record<string, unknown> | undefined {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : undefined;
}

function stringClaim(claims: Record<string, unknown>, name: string): string | undefined {
  const value = claims[name];
  return typeof value === "string" && value ? value : undefined;
}
