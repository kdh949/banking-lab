import { spawnSync } from "node:child_process";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

type SmokeStatus = "pass" | "failed" | "skipped_missing_tool" | "skipped_docker_unavailable";

type CommandLog = {
  command: string;
  status: number | null;
  durationMs: number;
  stdout: string;
  stderr: string;
};

const rootDir = process.cwd();
const evidencePath = path.join(rootDir, "docs", "test-evidence", "generated", "platform-live-runtime-smoke.json");
const chartDir = path.join("infra", "helm", "banking-lab");
const releaseName = process.env.BANKING_LAB_PLATFORM_LIVE_RELEASE ?? "banking-lab";
const namespace = process.env.BANKING_LAB_PLATFORM_LIVE_NAMESPACE ?? "banking-lab";
const clusterName = process.env.BANKING_LAB_PLATFORM_LIVE_CLUSTER ?? `banking-lab-platform-${process.pid}`;
const coreBankingImage = process.env.BANKING_LAB_PLATFORM_LIVE_CORE_IMAGE ?? "banking-lab/core-banking-service:local";
const helmTimeout = process.env.BANKING_LAB_PLATFORM_LIVE_HELM_TIMEOUT ?? "10m";
const rolloutTimeout = process.env.BANKING_LAB_PLATFORM_LIVE_ROLLOUT_TIMEOUT ?? "300s";
const keepCluster = process.env.BANKING_LAB_PLATFORM_LIVE_KEEP_CLUSTER === "true";
const commands: CommandLog[] = [];
const errors: string[] = [];
const skippedDependencyImages: string[] = [];
const failedDependencyImageLoads: string[] = [];
const runtimeProbes: Record<string, unknown> = {};
const startedAt = new Date();
let clusterCreated = false;
let finalStatus: SmokeStatus = "failed";
let finalNote = "The live platform smoke did not complete.";

const dependencyImages = [
  "postgres:16-alpine",
  "quay.io/keycloak/keycloak:26.0",
  "redpandadata/redpanda:v24.3.7",
  "temporalio/auto-setup:1.26"
];

await main();

async function main(): Promise<void> {
  const missingTools = requiredTools().filter((tool) => !hasTool(tool));
  if (missingTools.length > 0) {
    finalStatus = "skipped_missing_tool";
    finalNote = "Required local runtime tools are not available; no production claim is made.";
    runtimeProbes.missingTools = missingTools;
    await writeEvidence();
    console.log(`Platform live runtime smoke skipped; missing tools: ${missingTools.join(", ")}`);
    return;
  }

  const dockerCheck = run("docker", ["ps"]);
  if (dockerCheck.status !== 0) {
    finalStatus = "skipped_docker_unavailable";
    finalNote = "Docker is installed but not available to this process; no live cluster was created.";
    errors.push("docker ps failed; Docker must be running for the disposable kind smoke.");
    await writeEvidence();
    console.log("Platform live runtime smoke skipped; Docker is unavailable.");
    return;
  }

  try {
    mustRun("scripts/run-core-banking-tests.sh", [":services:core-banking:bootJar"]);
    mustRun("docker", ["build", "-f", "infra/docker-compose/core-banking.Dockerfile", "-t", coreBankingImage, "."]);

    mustRun("kind", ["create", "cluster", "--name", clusterName, "--wait", "120s"]);
    clusterCreated = true;
    mustRun("kind", ["load", "docker-image", coreBankingImage, "--name", clusterName]);
    loadDependencyImagesIfPresent();

    mustRun("helm", [
      "install",
      releaseName,
      chartDir,
      "--namespace",
      namespace,
      "--create-namespace",
      "--wait",
      "--timeout",
      helmTimeout,
      "--set",
      `coreBanking.image=${coreBankingImage}`,
      "--set",
      "coreBanking.replicas=1",
      "--set",
      "paymentService.replicas=0",
      "--set",
      "paymentService.outboxWorkerReplicas=0",
      "--set",
      "paymentService.domainEventPublisherReplicas=0",
      "--set",
      "reportingService.replicas=0",
      "--set",
      "reportingService.domainEventPublisherReplicas=0",
      "--set",
      "notificationService.replicas=0",
      "--set",
      "notificationService.eventConsumerReplicas=0",
      "--set",
      "temporalWorker.replicas=0",
      "--set",
      "keycloak.replicas=1",
      "--set",
      "redpanda.replicas=1",
      "--set",
      "temporalServer.replicas=1"
    ]);

    for (const target of [
      `statefulset/${releaseName}-postgres`,
      `deployment/${releaseName}-keycloak`,
      `deployment/${releaseName}-redpanda`,
      `deployment/${releaseName}-temporal`,
      `deployment/${releaseName}-core-banking`
    ]) {
      mustRun("kubectl", ["rollout", "status", target, "-n", namespace, `--timeout=${rolloutTimeout}`]);
    }

    runtimeProbes.coreHealth = JSON.parse(
      mustRun("kubectl", [
        "get",
        "--raw",
        `/api/v1/namespaces/${namespace}/services/http:${releaseName}-core-banking:8081/proxy/health`
      ]).stdout
    );
    runtimeProbes.keycloakManagementReady = parseMaybeJson(mustRun("kubectl", [
      "get",
      "--raw",
      `/api/v1/namespaces/${namespace}/services/http:${releaseName}-keycloak:9000/proxy/health/ready`
    ]).stdout);
    runtimeProbes.redpandaReady = parseMaybeJson(mustRun("kubectl", [
      "get",
      "--raw",
      `/api/v1/namespaces/${namespace}/services/http:${releaseName}-redpanda:9644/proxy/v1/status/ready`
    ]).stdout);
    runtimeProbes.temporalEndpoints = JSON.parse(
      mustRun("kubectl", ["get", "endpoints", `${releaseName}-temporal`, "-n", namespace, "-o", "json"]).stdout
    );
    runtimeProbes.ingress = JSON.parse(
      mustRun("kubectl", ["get", "ingress", `${releaseName}-ingress`, "-n", namespace, "-o", "json"]).stdout
    );
    runtimeProbes.tlsSecretType = mustRun("kubectl", [
      "get",
      "secret",
      `${releaseName}-ingress-tls`,
      "-n",
      namespace,
      "-o",
      "jsonpath={.type}"
    ]).stdout;

    const argoResources = run("kubectl", ["api-resources", "--api-group", "argoproj.io", "-o", "name"]);
    runtimeProbes.argocdSync = argoResources.status === 0 && argoResources.stdout.includes("applications")
      ? "argocd_application_crd_present_not_synced_by_smoke"
      : "skipped_no_argocd_controller_or_application_crd";

    finalStatus = "pass";
    finalNote = "Disposable kind Helm install proved live PostgreSQL, Keycloak, Redpanda, Temporal, core-banking, TLS Ingress object, and placeholder TLS secret readiness for the scoped synthetic runtime slice.";
  } catch (error) {
    finalStatus = "failed";
    finalNote = "The disposable kind Helm live runtime smoke failed.";
    errors.push(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  } finally {
    if (clusterCreated && !keepCluster) {
      run("helm", ["uninstall", releaseName, "-n", namespace, "--wait", "--ignore-not-found"]);
      run("kind", ["delete", "cluster", "--name", clusterName]);
    } else if (clusterCreated) {
      runtimeProbes.clusterRetained = true;
    }
    await writeEvidence();
  }

  if (finalStatus === "pass") {
    console.log(`Platform live runtime smoke passed on disposable kind cluster ${clusterName}.`);
  }
}

function requiredTools(): string[] {
  return ["docker", "kind", "kubectl", "helm"];
}

function hasTool(tool: string): boolean {
  return run("sh", ["-lc", `command -v ${shellQuote(tool)}`]).status === 0;
}

function loadDependencyImagesIfPresent(): void {
  for (const image of dependencyImages) {
    const inspect = run("docker", ["image", "inspect", image]);
    if (inspect.status === 0) {
      const load = run("kind", ["load", "docker-image", image, "--name", clusterName]);
      if (load.status !== 0) {
        failedDependencyImageLoads.push(image);
      }
    } else {
      skippedDependencyImages.push(image);
    }
  }
  runtimeProbes.skippedDependencyImageLoads = skippedDependencyImages;
  runtimeProbes.failedDependencyImageLoads = failedDependencyImageLoads;
}

function mustRun(command: string, args: string[]): CommandLog {
  const result = run(command, args);
  if (result.status !== 0) {
    throw new Error(`Command failed: ${result.command}\n${result.stderr || result.stdout}`);
  }
  return result;
}

function run(command: string, args: string[]): CommandLog {
  const started = Date.now();
  const result = spawnSync(command, args, {
    cwd: rootDir,
    encoding: "utf8",
    env: process.env,
    maxBuffer: 1024 * 1024 * 8
  });
  const log: CommandLog = {
    command: [command, ...args.map(shellQuote)].join(" "),
    status: typeof result.status === "number" ? result.status : null,
    durationMs: Date.now() - started,
    stdout: trim(result.stdout),
    stderr: trim(result.stderr || (result.error ? result.error.message : ""))
  };
  commands.push(log);
  return log;
}

async function writeEvidence(): Promise<void> {
  await mkdir(path.dirname(evidencePath), { recursive: true });
  const payload = {
    command: "npm run platform:live-runtime-smoke",
    status: finalStatus,
    note: finalNote,
    startedAt: startedAt.toISOString(),
    finishedAt: new Date().toISOString(),
    durationMs: Date.now() - startedAt.getTime(),
    syntheticOnly: true,
    productionClaim: false,
    clusterName,
    helmRelease: releaseName,
    namespace,
    keepCluster,
    scope: [
      "PostgreSQL StatefulSet rollout",
      "Keycloak Deployment management readiness",
      "Redpanda Deployment admin readiness",
      "Temporal Deployment TCP readiness",
      "core-banking Spring health through Kubernetes service proxy",
      "TLS Ingress object and synthetic placeholder TLS Secret presence"
    ],
    explicitNonClaims: [
      "No real customer data, real money, real KYC, real payment network, or real financial institution API is used.",
      "No production secret manager, real certificate, ingress controller traffic path, canary promotion, multi-node storage, or Argo CD controller sync is proven by this smoke."
    ],
    probes: runtimeProbes,
    commands,
    errors
  };
  await writeFile(evidencePath, `${JSON.stringify(payload, null, 2)}\n`, "utf8");
}

function parseMaybeJson(value: string): unknown {
  try {
    return JSON.parse(value);
  } catch {
    return value;
  }
}

function shellQuote(value: string): string {
  if (/^[A-Za-z0-9_./:=@+-]+$/.test(value)) {
    return value;
  }
  return `'${value.replace(/'/g, "'\\''")}'`;
}

function trim(value: string | undefined): string {
  const output = value ?? "";
  return output.length > 6000 ? `${output.slice(0, 6000)}\n...<truncated>` : output;
}
