import { NextRequest } from "next/server";

export async function GET(request: NextRequest) {
  return Response.json({
    clientIp: clientIpFromRequest(request),
    serverTimeIso: new Date().toISOString(),
    syntheticOnly: true
  });
}

function clientIpFromRequest(request: NextRequest) {
  const forwardedFor = request.headers.get("x-forwarded-for")?.split(",")[0]?.trim();
  const realIp = request.headers.get("x-real-ip")?.trim();
  const connectingIp = request.headers.get("cf-connecting-ip")?.trim();
  const candidate = forwardedFor || realIp || connectingIp;
  if (candidate) {
    return candidate.replace(/^::ffff:/u, "");
  }

  const host = request.headers.get("host") ?? "";
  if (host.startsWith("localhost") || host.startsWith("127.0.0.1")) {
    return "127.0.0.1";
  }
  return "IP 확인 불가";
}
