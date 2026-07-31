import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  allowedDevOrigins: ["127.0.0.1"],
  output: "standalone",
  reactStrictMode: true,
  transpilePackages: ["@banking-lab/api-client", "@banking-lab/auth-client"]
};

export default nextConfig;
