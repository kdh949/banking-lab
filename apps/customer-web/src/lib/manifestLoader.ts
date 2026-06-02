import { readdir, readFile } from "node:fs/promises";
import path from "node:path";

export type CustomerWebManifest = {
  screenId: string;
  app: "customer-web";
  type: string;
  domain: string;
  requiredRoles: string[];
  audit: {
    reasonRequired: boolean;
    maskingPolicy: string;
  };
  approval?: {
    required: boolean;
    makerChecker?: boolean;
  };
};

const repoRoot = process.env.BANKING_LAB_ROOT || path.resolve(process.cwd(), "../..");

export async function loadCustomerWebManifests(): Promise<CustomerWebManifest[]> {
  const manifestDir = path.join(repoRoot, "screen-manifests", "customer-web");
  const fileNames = (await readdir(manifestDir))
    .filter((fileName) => fileName.endsWith(".json"))
    .sort();

  const manifests = await Promise.all(
    fileNames.map(async (fileName) => {
      const source = await readFile(path.join(manifestDir, fileName), "utf8");
      return JSON.parse(source) as CustomerWebManifest;
    })
  );

  return manifests.filter((manifest) => manifest.app === "customer-web");
}
