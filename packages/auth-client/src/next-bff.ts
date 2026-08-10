import { NextRequest, NextResponse } from "next/server";
import { randomBytes } from "node:crypto";
import { createOidcAuthorizationUrl, createSimulatorBearerToken } from "./index";
import {
  authenticatedBffSession,
  beginOidcSession,
  bffSessionCookieName,
  completeOidcSession,
  createAuthenticatedBffSession,
  destroyBffSession,
  pendingOidcVerifier,
  type BffActor
} from "./server";

export type NextBffConfig = {
  readonly appId: string;
  readonly oidcClientId: string;
  readonly defaultReturnTo: string;
  readonly proxyAllowedPrefixes: readonly string[];
  readonly proxyUpstreams?: readonly {
    readonly prefix: string;
    readonly environmentVariable: string;
  }[];
  readonly simulatedActors?: readonly BffActor[];
  readonly customerAuth?: boolean;
};

type CustomerAuthPayload = {
  readonly action?: "login" | "signup";
  readonly command?: Record<string, unknown>;
};

type CustomerAuthResponse = {
  readonly bearerToken?: string;
  readonly tokenType?: string;
  readonly expiresAt?: string;
  readonly customer?: { readonly customerId?: string; readonly username?: string };
  readonly session?: { readonly subject?: string; readonly customerId?: string; readonly roles?: readonly string[]; readonly sessionId?: string };
  readonly [key: string]: unknown;
};

export function createNextBffHandlers(config: NextBffConfig) {
  const cookieName = bffSessionCookieName(config.appId);

  return {
    session: {
      GET(request: NextRequest) {
        const session = currentSession(request);
        return noStoreJson(session ? publicSession(session) : { authenticated: false }, { status: 200 });
      },
      DELETE(request: NextRequest) {
        if (!sameOriginMutation(request)) {
          return csrfDenied();
        }
        destroyBffSession(config.appId, request.cookies.get(cookieName)?.value);
        const response = noStoreJson({ authenticated: false }, { status: 200 });
        clearSessionCookie(response);
        return response;
      }
    },
    login: {
      GET(request: NextRequest) {
        const keycloakBaseUrl = configuredKeycloakBaseUrl();
        if (!keycloakBaseUrl) {
          return structuredError(503, "AUTHORIZATION_POLICY_VIOLATION", "Keycloak base URL is not configured for the BFF");
        }
        const returnTo = safeReturnTo(request.nextUrl.searchParams.get("returnTo"), config.defaultReturnTo);
        const pending = beginOidcSession(config.appId, returnTo);
        const redirectUri = new URL("/api/session/callback", request.url).toString();
        const authorizationUrl = createOidcAuthorizationUrl({
          issuerBaseUrl: keycloakBaseUrl,
          realm: "banking-lab",
          clientId: config.oidcClientId,
          redirectUri,
          state: pending.state,
          codeChallenge: pending.codeChallenge
        });
        const response = NextResponse.redirect(authorizationUrl);
        setSessionCookie(response, pending.sessionId, request, 5 * 60);
        response.headers.set("Cache-Control", "no-store");
        return response;
      }
    },
    callback: {
      async GET(request: NextRequest) {
        const sessionId = request.cookies.get(cookieName)?.value;
        const code = request.nextUrl.searchParams.get("code") ?? "";
        const state = request.nextUrl.searchParams.get("state") ?? "";
        const verifier = sessionId ? pendingOidcVerifier(config.appId, sessionId, state) : null;
        if (!sessionId || !code || !state || !verifier) {
          return structuredError(401, "AUTHORIZATION_POLICY_VIOLATION", "OIDC state or PKCE verifier is invalid or expired");
        }
        const keycloakBaseUrl = configuredKeycloakBaseUrl();
        if (!keycloakBaseUrl) {
          return structuredError(503, "AUTHORIZATION_POLICY_VIOLATION", "Keycloak base URL is not configured for the BFF");
        }
        const redirectUri = new URL("/api/session/callback", request.url).toString();
        const tokenResponse = await fetch(`${keycloakBaseUrl}/realms/banking-lab/protocol/openid-connect/token`, {
          method: "POST",
          headers: { Accept: "application/json", "Content-Type": "application/x-www-form-urlencoded" },
          body: new URLSearchParams({
            grant_type: "authorization_code",
            client_id: config.oidcClientId,
            code,
            code_verifier: verifier,
            redirect_uri: redirectUri
          }),
          cache: "no-store"
        });
        const tokenBody = await tokenResponse.json().catch(() => ({})) as {
          readonly access_token?: string;
          readonly token_type?: string;
          readonly expires_in?: number;
        };
        if (!tokenResponse.ok || !tokenBody.access_token) {
          return structuredError(401, "AUTHORIZATION_POLICY_VIOLATION", "OIDC authorization code exchange failed");
        }
        const completed = completeOidcSession(
          config.appId,
          sessionId,
          state,
          `${tokenBody.token_type ?? "Bearer"} ${tokenBody.access_token}`,
          tokenBody.expires_in ?? 300
        );
        if (!completed) {
          return structuredError(401, "AUTHORIZATION_POLICY_VIOLATION", "OIDC session completion failed");
        }
        const response = NextResponse.redirect(new URL(completed.returnTo, request.url));
        setSessionCookie(response, completed.session.sessionId, request, 3600);
        response.headers.set("Cache-Control", "no-store");
        return response;
      }
    },
    simulated: {
      async POST(request: NextRequest) {
        if (!sameOriginMutation(request)) {
          return csrfDenied();
        }
        if (!simulatorLoginEnabled()) {
          return structuredError(403, "AUTHORIZATION_POLICY_VIOLATION", "BFF simulator login requires explicit dev/test opt-in");
        }
        const body = await request.json().catch(() => ({})) as { readonly actorKey?: string };
        const actor = config.simulatedActors?.find((candidate) => candidate.actorKey === body.actorKey);
        if (!actor) {
          return structuredError(400, "REQUEST_VALIDATION_FAILED", "Unknown simulated BFF actor");
        }
        const session = createAuthenticatedBffSession({
          appId: config.appId,
          bearerToken: createSimulatorBearerToken({
            subject: actor.subject,
            roles: actor.roles,
            customerId: actor.customerId,
            audience: "core-banking-api",
            authTimeEpochSeconds: actor.simulatedStepUp ? Math.floor(Date.now() / 1000) : undefined,
            issuedAtEpochSeconds: actor.simulatedStepUp ? Math.floor(Date.now() / 1000) : undefined,
            authenticationMethods: actor.simulatedStepUp ? ["pwd", "webauthn"] : undefined,
            assuranceLevel: actor.simulatedStepUp ? "banking-lab-step-up" : undefined
          }),
          subject: actor.subject,
          roles: actor.roles,
          customerId: actor.customerId,
          displayName: actor.displayName,
          mode: "SIMULATED"
        });
        const response = noStoreJson(publicSession(session), { status: 200 });
        setSessionCookie(response, session.sessionId, request, 3600);
        return response;
      }
    },
    customerAuth: {
      async POST(request: NextRequest) {
        if (!config.customerAuth) {
          return structuredError(404, "RESOURCE_NOT_FOUND", "Customer BFF authentication is not enabled for this app");
        }
        if (!sameOriginMutation(request)) {
          return csrfDenied();
        }
        const payload = await request.json().catch(() => ({})) as CustomerAuthPayload;
        if ((payload.action !== "login" && payload.action !== "signup") || !payload.command) {
          return structuredError(400, "REQUEST_VALIDATION_FAILED", "Customer authentication action and command are required");
        }
        const path = payload.action === "signup" ? "/api/auth/customer/signup" : "/api/auth/customer/login";
        const upstream = configuredApiBaseUrl();
        if (!upstream) {
          return structuredError(503, "DEPENDENCY_UNAVAILABLE", "Core banking API is not configured for the BFF");
        }
        const upstreamResponse = await fetch(new URL(path, upstream), {
          method: "POST",
          headers: { Accept: "application/json", "Content-Type": "application/json" },
          body: JSON.stringify(payload.command),
          cache: "no-store"
        });
        const responseText = await upstreamResponse.text();
        if (!upstreamResponse.ok) {
          return new NextResponse(responseText, {
            status: upstreamResponse.status,
            headers: { "Content-Type": upstreamResponse.headers.get("content-type") ?? "application/json", "Cache-Control": "no-store" }
          });
        }
        const auth = JSON.parse(responseText) as CustomerAuthResponse;
        if (!auth.bearerToken || !auth.customer?.customerId || !auth.customer.username) {
          return structuredError(502, "DEPENDENCY_RESPONSE_INVALID", "Customer authentication response is incomplete");
        }
        const expiresInSeconds = auth.expiresAt
          ? Math.max(60, Math.floor((Date.parse(auth.expiresAt) - Date.now()) / 1000))
          : 3600;
        const session = createAuthenticatedBffSession({
          appId: config.appId,
          bearerToken: `${auth.tokenType ?? "Bearer"} ${auth.bearerToken}`,
          subject: auth.session?.subject ?? auth.customer.username,
          roles: auth.session?.roles ?? ["CUSTOMER"],
          customerId: auth.customer.customerId,
          displayName: auth.customer.username,
          expiresInSeconds,
          mode: "SYNTHETIC_CUSTOMER_AUTH"
        });
        const {
          bearerToken: _bearerToken,
          tokenType: _tokenType,
          session: upstreamSession,
          ...safeAuth
        } = auth;
        const { sessionId: _upstreamSessionId, ...safeUpstreamSession } = upstreamSession ?? {};
        const response = noStoreJson({
          ...safeAuth,
          session: safeUpstreamSession,
          bffSession: publicSession(session)
        }, { status: upstreamResponse.status });
        setSessionCookie(response, session.sessionId, request, 3600);
        return response;
      }
    },
    proxy: {
      GET: proxy,
      POST: proxy,
      PUT: proxy,
      PATCH: proxy,
      DELETE: proxy
    }
  };

  async function proxy(request: NextRequest, context: { readonly params: Promise<{ readonly path: readonly string[] }> }) {
    const session = currentSession(request);
    if (!session) {
      return structuredError(401, "AUTHORIZATION_POLICY_VIOLATION", "An authenticated opaque BFF session is required");
    }
    if (!sameOriginMutation(request)) {
      return csrfDenied();
    }
    const segments = (await context.params).path;
    if (!segments.length || segments.some((segment) => segment === "." || segment === ".." || segment.includes("/"))) {
      return structuredError(400, "REQUEST_VALIDATION_FAILED", "Invalid BFF target path");
    }
    const targetPath = `/api/${segments.map(encodeURIComponent).join("/")}`;
    if (!config.proxyAllowedPrefixes.some((prefix) => {
      const segmentPrefix = prefix.endsWith("/") ? prefix : `${prefix}/`;
      return targetPath === prefix || targetPath.startsWith(segmentPrefix);
    })) {
      return structuredError(403, "AUTHORIZATION_POLICY_VIOLATION", "BFF route is outside this channel capability boundary");
    }
    const upstream = configuredApiBaseUrl(targetPath, config.proxyUpstreams);
    if (!upstream) {
      return structuredError(503, "DEPENDENCY_UNAVAILABLE", "Core banking API is not configured for the BFF");
    }
    const targetUrl = new URL(targetPath, upstream);
    targetUrl.search = request.nextUrl.search;
    const requestId = safeRequestId(request.headers.get("x-request-id")) ?? newRequestId();
    const traceparent = safeTraceparent(request.headers.get("traceparent")) ?? newTraceparent();
    const headers = new Headers({
      Accept: request.headers.get("accept") ?? "application/json",
      Authorization: session.bearerToken,
      "x-request-id": requestId,
      traceparent
    });
    const contentType = request.headers.get("content-type");
    if (contentType) {
      headers.set("content-type", contentType);
    }
    const tracestate = safeTracestate(request.headers.get("tracestate"));
    if (tracestate) {
      headers.set("tracestate", tracestate);
    }
    try {
      const upstreamResponse = await fetch(targetUrl, {
        method: request.method,
        headers,
        body: request.method === "GET" || request.method === "HEAD" ? undefined : await request.arrayBuffer(),
        cache: "no-store",
        redirect: "manual"
      });
      const responseHeaders = new Headers({
        "Content-Type": upstreamResponse.headers.get("content-type") ?? "application/json",
        "Cache-Control": "no-store"
      });
      responseHeaders.set("x-request-id", safeRequestId(upstreamResponse.headers.get("x-request-id")) ?? requestId);
      responseHeaders.set("traceparent", safeTraceparent(upstreamResponse.headers.get("traceparent")) ?? traceparent);
      return new NextResponse(await upstreamResponse.arrayBuffer(), { status: upstreamResponse.status, headers: responseHeaders });
    } catch {
      const response = structuredError(502, "DEPENDENCY_UNAVAILABLE", "Core banking API request failed at the BFF boundary");
      response.headers.set("x-request-id", requestId);
      response.headers.set("traceparent", traceparent);
      return response;
    }
  }

  function currentSession(request: NextRequest) {
    return authenticatedBffSession(config.appId, request.cookies.get(cookieName)?.value);
  }

  function setSessionCookie(response: NextResponse, sessionId: string, request: NextRequest, maxAge: number) {
    response.cookies.set(cookieName, sessionId, {
      httpOnly: true,
      sameSite: "lax",
      secure: request.nextUrl.protocol === "https:" || process.env.BANKING_LAB_BFF_SECURE_COOKIE === "true",
      path: "/",
      maxAge
    });
  }

  function clearSessionCookie(response: NextResponse) {
    response.cookies.set(cookieName, "", { httpOnly: true, sameSite: "lax", secure: false, path: "/", maxAge: 0 });
  }
}

function configuredApiBaseUrl(
  targetPath?: string,
  proxyUpstreams: NextBffConfig["proxyUpstreams"] = []
): string {
  const override = targetPath
    ? [...proxyUpstreams]
        .sort((left, right) => right.prefix.length - left.prefix.length)
        .find(({ prefix }) => targetPath === prefix || targetPath.startsWith(prefix.endsWith("/") ? prefix : `${prefix}/`))
    : undefined;
  return normalizeBaseUrl((override ? process.env[override.environmentVariable] : undefined) ?? process.env.BANKING_LAB_API_BASE_URL ?? "");
}

function configuredKeycloakBaseUrl(): string {
  return normalizeBaseUrl(process.env.BANKING_LAB_KEYCLOAK_BASE_URL ?? "");
}

function normalizeBaseUrl(value: string): string {
  const trimmed = value.trim();
  return trimmed ? (trimmed.endsWith("/") ? trimmed : `${trimmed}/`) : "";
}

function simulatorLoginEnabled(): boolean {
  return process.env.BANKING_LAB_BFF_SIMULATOR_LOGIN_ENABLED === "true"
    && process.env.BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED === "true"
    && process.env.BANKING_LAB_DEV_SIMULATOR_TOKEN === "true";
}

function sameOriginMutation(request: NextRequest): boolean {
  if (request.method === "GET" || request.method === "HEAD") {
    return true;
  }
  const origin = request.headers.get("origin");
  return Boolean(origin) && origin === request.nextUrl.origin;
}

function safeReturnTo(value: string | null, fallback: string): string {
  return value?.startsWith("/") && !value.startsWith("//") ? value : fallback;
}

function publicSession(session: object) {
  const { bearerToken: _bearerToken, appId: _appId, sessionId: _sessionId, ...safe } = session as Record<string, unknown>;
  return safe;
}

function noStoreJson(body: unknown, init: ResponseInit): NextResponse {
  const response = NextResponse.json(body, init);
  response.headers.set("Cache-Control", "no-store");
  return response;
}

function structuredError(status: number, code: string, message: string): NextResponse {
  return noStoreJson({
    error: {
      code,
      message,
      statusCode: status,
      domain: "auth",
      policy: "OPAQUE_BFF_SESSION_REQUIRED",
      syntheticOnly: true
    }
  }, { status });
}

function csrfDenied(): NextResponse {
  return structuredError(403, "AUTHORIZATION_POLICY_VIOLATION", "Same-origin BFF request is required");
}

const requestIdPattern = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/u;
const traceparentPattern = /^00-([0-9a-f]{32})-([0-9a-f]{16})-(00|01)$/u;

function safeRequestId(value: string | null): string | null {
  const normalized = value?.trim() ?? "";
  return requestIdPattern.test(normalized) ? normalized : null;
}

function safeTraceparent(value: string | null): string | null {
  const normalized = value?.trim().toLowerCase() ?? "";
  const match = traceparentPattern.exec(normalized);
  if (!match || /^0{32}$/u.test(match[1]) || /^0{16}$/u.test(match[2])) {
    return null;
  }
  return normalized;
}

function safeTracestate(value: string | null): string | null {
  const normalized = value?.trim() ?? "";
  return normalized.length > 0 && normalized.length <= 512 && /^[\x20-\x7e]+$/u.test(normalized) ? normalized : null;
}

function newRequestId(): string {
  return `REQ-${randomBytes(12).toString("hex").toUpperCase()}`;
}

function newTraceparent(): string {
  return `00-${randomBytes(16).toString("hex")}-${randomBytes(8).toString("hex")}-01`;
}
