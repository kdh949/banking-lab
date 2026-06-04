# Security Evidence Summary

Generated at: 2026-06-04T14:13:45.766Z

Forced Docker scanner fallbacks: false

| Check | Status | Evidence | Reason |
| --- | --- | --- | --- |
| npm-audit-high | pass | docs/test-evidence/generated/npm-audit.txt |  |
| semgrep-sast | pass | docs/test-evidence/generated/semgrep.json |  |
| trivy-fs | pass | docs/test-evidence/generated/trivy-fs.json |  |
| sbom-cyclonedx | pass | docs/test-evidence/generated/sbom.cdx.json |  |
| dast-zap-baseline | skipped |  | BANKING_LAB_DAST_URL is not set; no live target was supplied for DAST. |

Totals: 4 passed, 0 failed, 1 skipped.

