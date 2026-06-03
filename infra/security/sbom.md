# SBOM Profile

Generate security evidence for target-stack artifacts with:

```bash
npm run security:evidence
```

When Trivy is installed or Docker is available for the Trivy scanner image, this writes CycloneDX SBOM evidence to:

```text
docs/test-evidence/generated/sbom.cdx.json
```

The direct Trivy command remains:

```bash
trivy fs --format cyclonedx --output docs/test-evidence/generated/sbom.cdx.json .
```

The Docker fallback used by `npm run security:evidence` is:

```bash
docker run --rm -v <repo>:/workspace -w /workspace --entrypoint trivy aquasec/trivy:latest fs --format cyclonedx /workspace
```

The Node reference remains excluded from retirement until `docs/migration/node-retirement-gate.json` is ready.
