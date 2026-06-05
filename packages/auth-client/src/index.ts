export interface SimulatorTokenClaims {
  readonly subject: string;
  readonly roles: readonly string[];
  readonly customerId?: string;
  readonly issuer?: string;
  readonly active?: boolean;
  readonly sessionId?: string;
  readonly authTimeEpochSeconds?: number;
  readonly issuedAtEpochSeconds?: number;
  readonly authenticationMethods?: readonly string[];
  readonly assuranceLevel?: string;
  readonly deviceFingerprint?: string;
}

export function createSimulatorBearerToken(claims: SimulatorTokenClaims): string {
  const payload = {
    iss: claims.issuer ?? "http://keycloak.local/realms/banking-lab",
    sub: claims.subject,
    roles: claims.roles,
    customerId: claims.customerId,
    active: claims.active ?? true,
    sid: claims.sessionId,
    auth_time: claims.authTimeEpochSeconds,
    iat: claims.issuedAtEpochSeconds,
    amr: claims.authenticationMethods,
    acr: claims.assuranceLevel,
    deviceFingerprint: claims.deviceFingerprint
  };
  const encoded = base64UrlEncode(JSON.stringify(payload));
  return `Bearer lab.${encoded}.sig`;
}

export interface PkcePair {
  readonly codeVerifier: string;
  readonly codeChallenge: string;
}

export interface OidcAuthorizationUrlOptions {
  readonly issuerBaseUrl: string;
  readonly realm: string;
  readonly clientId: string;
  readonly redirectUri: string;
  readonly state: string;
  readonly codeChallenge: string;
  readonly scope?: string;
  readonly prompt?: string;
  readonly loginHint?: string;
}

export async function createPkcePair(): Promise<PkcePair> {
  const randomBytes = new Uint8Array(32);
  globalThis.crypto.getRandomValues(randomBytes);
  const codeVerifier = base64UrlEncodeBytes(randomBytes);
  const challengeBytes = new Uint8Array(
    await globalThis.crypto.subtle.digest("SHA-256", new TextEncoder().encode(codeVerifier))
  );
  return {
    codeVerifier,
    codeChallenge: base64UrlEncodeBytes(challengeBytes)
  };
}

export function createOidcAuthorizationUrl(options: OidcAuthorizationUrlOptions): string {
  const url = new URL(
    `/realms/${encodeURIComponent(options.realm)}/protocol/openid-connect/auth`,
    normalizeIssuerBaseUrl(options.issuerBaseUrl)
  );
  url.searchParams.set("client_id", options.clientId);
  url.searchParams.set("redirect_uri", options.redirectUri);
  url.searchParams.set("response_type", "code");
  url.searchParams.set("scope", options.scope ?? "openid profile");
  url.searchParams.set("state", options.state);
  url.searchParams.set("code_challenge", options.codeChallenge);
  url.searchParams.set("code_challenge_method", "S256");
  if (options.prompt) {
    url.searchParams.set("prompt", options.prompt);
  }
  if (options.loginHint) {
    url.searchParams.set("login_hint", options.loginHint);
  }
  return url.toString();
}

function base64UrlEncode(value: string): string {
  return btoa(value).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/u, "");
}

function base64UrlEncodeBytes(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/u, "");
}

function normalizeIssuerBaseUrl(value: string): string {
  const trimmed = value.trim();
  if (!trimmed) {
    throw new Error("OIDC issuer base URL is required.");
  }
  return trimmed.endsWith("/") ? trimmed : `${trimmed}/`;
}
