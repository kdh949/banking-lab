import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

async function read(path) {
  return readFile(path, "utf8");
}

test("shared channel BFF keeps OIDC PKCE and bearer credentials server-side", async () => {
  const [handlers, store] = await Promise.all([
    read("packages/auth-client/src/next-bff.ts"),
    read("packages/auth-client/src/server.ts")
  ]);

  assert.match(store, /randomBytes\(bytes\)\.toString\("base64url"\)/);
  assert.match(store, /codeVerifier: string/);
  assert.match(store, /createHash\("sha256"\).*digest\("base64url"\)/s);
  assert.match(store, /pendingOidcVerifier/);
  assert.match(store, /store\.pendingOidc\.delete\(key\)/);
  assert.match(store, /readonly bearerToken: string/);
  assert.match(store, /Math\.min\(input\.expiresInSeconds, 3600\)/);

  assert.match(handlers, /code_verifier: verifier/);
  assert.match(handlers, /Authorization: session\.bearerToken/);
  assert.match(handlers, /httpOnly: true/);
  assert.match(handlers, /sameSite: "lax"/);
  assert.match(handlers, /redirect: "manual"/);
  assert.match(handlers, /Cache-Control", "no-store"/);
  assert.match(handlers, /origin === request\.nextUrl\.origin/);
  assert.match(handlers, /const targetPath = `\/api\/\$\{segments\.map\(encodeURIComponent\)\.join\("\/"\)\}`/);
  assert.match(handlers, /const segmentPrefix = prefix\.endsWith\("\/"\) \? prefix : `\$\{prefix\}\/`/);
  assert.match(handlers, /const \{ bearerToken: _bearerToken, appId: _appId, sessionId: _sessionId/);
  assert.match(handlers, /sessionId: _upstreamSessionId/);
  assert.match(handlers, /BANKING_LAB_BFF_SIMULATOR_LOGIN_ENABLED/);
  assert.match(handlers, /BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED/);
  assert.match(handlers, /BANKING_LAB_DEV_SIMULATOR_TOKEN/);
  assert.match(handlers, /payload\.action !== "login" && payload\.action !== "signup"/);
  assert.match(handlers, /process\.env\.BANKING_LAB_API_BASE_URL \?\? ""/);
  assert.match(handlers, /process\.env\.BANKING_LAB_KEYCLOAK_BASE_URL \?\? ""/);
  assert.match(handlers, /safeTraceparent\(request\.headers\.get\("traceparent"\)\) \?\? newTraceparent\(\)/);
  assert.match(handlers, /safeRequestId\(request\.headers\.get\("x-request-id"\)\) \?\? newRequestId\(\)/);
  assert.match(handlers, /normalized\.length <= 512/);
  assert.match(handlers, /responseHeaders\.set\("traceparent"/);
  assert.doesNotMatch(handlers, /request\.headers\.get\("authorization"\)/i);
  assert.doesNotMatch(handlers, /console\.(?:log|info|warn|error)/);
  assert.doesNotMatch(handlers, /process\.env\.NEXT_PUBLIC_BANKING_(?:API|KEYCLOAK)_BASE_URL/);
});

test("product channel code uses same-origin BFF sessions without browser bearer storage", async () => {
  const [customer, callCenter, staffApi, staffSession, customerConfig, callCenterConfig, staffConfig] = await Promise.all([
    read("apps/customer-web/src/components/CustomerSelfService.tsx"),
    read("apps/call-center-console/src/components/CallCenterWorkspace.tsx"),
    read("apps/staff-terminal/src/components/terminal/api-screens.tsx"),
    read("apps/staff-terminal/src/components/terminal/session-boundary.tsx"),
    read("apps/customer-web/src/server/bff.ts"),
    read("apps/call-center-console/src/server/bff.ts"),
    read("apps/staff-terminal/src/server/bff.ts")
  ]);

  for (const [name, source] of [["customer", customer], ["call-center", callCenter], ["staff-api", staffApi], ["staff-session", staffSession]]) {
    assert.doesNotMatch(
      source,
      /bearerToken|authorizationHeader|sessionStorage|createSimulatorBearerToken|NEXT_PUBLIC_BANKING_API_BASE_URL|bankingLabCustomerSyntheticSession/,
      `${name} product code must not expose browser bearer credentials`
    );
  }

  assert.match(customer, /\/api\/session\/customer-auth/);
  assert.match(customer, /window\.location\.origin/);
  assert.match(customer, /opaque HttpOnly BFF session/);
  assert.match(customer, /bankingLabCustomerTransferResults/);
  assert.match(callCenter, /\/api\/session\/login/);
  assert.match(callCenter, /\/api\/session\/simulated/);
  assert.match(staffSession, /\/api\/session\/login/);
  assert.match(staffSession, /\/api\/session\/simulated/);

  assert.match(customerConfig, /customerAuth: true/);
  assert.match(customerConfig, /"\/api\/customer\/"/);
  assert.match(callCenterConfig, /"\/api\/staff\/call-center\/"/);
  assert.match(callCenterConfig, /"\/api\/staff\/journeys\/"/);
  assert.match(staffConfig, /proxyAllowedPrefixes: \["\/api\/staff\/", "\/api\/approvals"\]/);
  assert.match(staffConfig, /subject: "branch01"/);
  assert.match(staffConfig, /subject: "risk01"/);
  assert.match(staffConfig, /subject: "manager01"/);
});

test("legacy browser token exchange is disabled unless lab evidence explicitly opts in", async () => {
  const [customerRoute, callCenterRoute] = await Promise.all([
    read("apps/customer-web/src/app/api/auth/keycloak-token/route.ts"),
    read("apps/call-center-console/src/app/api/auth/keycloak-token/route.ts")
  ]);

  for (const route of [customerRoute, callCenterRoute]) {
    assert.match(route, /BANKING_LAB_LAB_BROWSER_TOKEN_EXCHANGE_ENABLED/);
    assert.match(route, /Browser token exchange is restricted to explicitly opted-in lab evidence/);
    assert.match(route, /\{ status: 403 \}/);
  }
});

test("customer notification routes use an explicit server-side notification upstream", async () => {
  const [customerConfig, handlers] = await Promise.all([
    read("apps/customer-web/src/server/bff.ts"),
    read("packages/auth-client/src/next-bff.ts")
  ]);

  assert.match(customerConfig, /prefix: "\/api\/notifications"/u);
  assert.match(customerConfig, /environmentVariable: "BANKING_LAB_NOTIFICATION_API_BASE_URL"/u);
  assert.match(handlers, /process\.env\[override\.environmentVariable\]/u);
  assert.doesNotMatch(customerConfig, /NEXT_PUBLIC_BANKING_NOTIFICATION_API_BASE_URL/u);
});
