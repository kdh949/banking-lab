import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  reactStrictMode: true,
  transpilePackages: ["@banking-lab/api-client", "@banking-lab/auth-client"]
};

export default nextConfig;
