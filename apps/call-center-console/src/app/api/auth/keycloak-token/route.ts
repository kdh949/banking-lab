import { NextRequest } from "next/server";

interface TokenRequest {
  readonly code?: string;
  readonly codeVerifier?: string;
  readonly redirectUri?: string;
}

interface KeycloakTokenResponse {
  readonly access_token?: string;
  readonly token_type?: string;
  readonly expires_in?: number;
}

export async function POST(request: NextRequest) {
  if (process.env.BANKING_LAB_LAB_BROWSER_TOKEN_EXCHANGE_ENABLED !== "true") {
    return Response.json(
      {
        error: {
          code: "AUTHORIZATION_POLICY_VIOLATION",
          message: "Browser token exchange is restricted to explicitly opted-in lab evidence",
          syntheticOnly: true
        }
      },
      { status: 403 }
    );
  }
  const body = (await request.json()) as TokenRequest;
  if (!body.code || !body.codeVerifier || !body.redirectUri) {
    return Response.json(
      {
        error: {
          code: "REQUEST_VALIDATION_FAILED",
          message: "authorization code, verifier, and redirect URI are required",
          syntheticOnly: true
        }
      },
      { status: 400 }
    );
  }

  const keycloakBaseUrl = keycloakBaseUrlFromEnv();
  if (!keycloakBaseUrl) {
    return Response.json(
      {
        error: {
          code: "AUTHORIZATION_POLICY_VIOLATION",
          message: "Keycloak base URL is not configured for call-center-console",
          syntheticOnly: true
        }
      },
      { status: 503 }
    );
  }

  const response = await fetch(`${keycloakBaseUrl}/realms/banking-lab/protocol/openid-connect/token`, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/x-www-form-urlencoded"
    },
    body: new URLSearchParams({
      grant_type: "authorization_code",
      client_id: "call-center-console",
      code: body.code,
      code_verifier: body.codeVerifier,
      redirect_uri: body.redirectUri
    })
  });
  const responseBody = await response.text();
  if (!response.ok) {
    return Response.json(
      {
        error: {
          code: "AUTHORIZATION_POLICY_VIOLATION",
          message: "Keycloak authorization code exchange failed",
          statusCode: response.status,
          syntheticOnly: true
        }
      },
      { status: 401 }
    );
  }

  const parsed = JSON.parse(responseBody) as KeycloakTokenResponse;
  if (!parsed.access_token) {
    return Response.json(
      {
        error: {
          code: "AUTHORIZATION_POLICY_VIOLATION",
          message: "Keycloak token response did not contain an access token",
          syntheticOnly: true
        }
      },
      { status: 401 }
    );
  }

  return Response.json({
    accessToken: parsed.access_token,
    tokenType: parsed.token_type ?? "Bearer",
    expiresIn: parsed.expires_in ?? null,
    syntheticOnly: true
  });
}

function keycloakBaseUrlFromEnv(): string {
  return (
    process.env.BANKING_LAB_KEYCLOAK_BASE_URL ??
    process.env.NEXT_PUBLIC_BANKING_KEYCLOAK_BASE_URL ??
    ""
  )
    .trim()
    .replace(/\/+$/u, "");
}
