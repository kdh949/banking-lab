# DAST Profile

Run DAST only against local synthetic lab targets.

```bash
BANKING_LAB_DAST_URL=http://127.0.0.1:8081 npm run security:evidence
```

The wrapper records ZAP baseline output when `zap-baseline.py` is installed. If no live target is supplied, the DAST check is recorded as skipped in `docs/test-evidence/generated/security-evidence-summary.json`.

When Docker is available but `zap-baseline.py` is not installed on the host, the wrapper uses:

```bash
docker run --rm -v <generated>:/zap/wrk -v <repo>:/workspace:ro ghcr.io/zaproxy/zaproxy:stable zap-baseline.py -t <url> -J zap-baseline.json -c /workspace/infra/security/zap-baseline.conf
```

`infra/security/zap-baseline.conf` ignores ZAP rule `10049` because this banking API intentionally uses `Cache-Control: no-store` for synthetic financial responses.

## 2026-06-04 Live Synthetic Run

The current generated DAST evidence was produced against a disposable local
synthetic core-banking target:

```bash
env BANKING_LAB_DAST_URL=http://host.docker.internal:18132/health npm run security:evidence:docker
```

Result: pass, with `docs/test-evidence/generated/security-evidence-summary.json`
recording 5 passed checks and 0 skipped checks.
