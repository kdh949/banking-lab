import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const allowedStatuses = [
  "api-backed-command",
  "api-backed-read",
  "backend-control-covered",
  "browser-e2e-backed",
  "complete",
  "integrated-terminal",
  "live-keycloak-backed",
  "manifest-only",
  "missing",
  "not-applicable",
  "partial",
  "route-backed-live-gated"
].sort();

function tableRows(markdown) {
  return markdown
    .split("\n")
    .filter((line) => line.startsWith("|") && !line.includes("---") && !line.startsWith("| Area"))
    .map((line) => {
      const columns = line.split("|").map((column) => column.trim());
      return {
        line,
        feature: columns[2],
        evidence: columns[columns.length - 3],
        status: columns[columns.length - 2]
      };
    });
}

test("implementation coverage matrix declares every status it uses", async () => {
  const matrix = await readFile("docs/implementation-coverage-matrix.md", "utf8");
  const statusLine = matrix
    .split("\n")
    .find((line) => line.startsWith("Status values are limited to"));
  assert.ok(statusLine, "coverage matrix must define the allowed status vocabulary");

  const declaredStatuses = [...statusLine.matchAll(/`([^`]+)`/g)].map((match) => match[1]).sort();
  assert.deepEqual(declaredStatuses, allowedStatuses);

  const rows = tableRows(matrix);
  assert.ok(rows.length > 0, "coverage matrix must have implementation rows");
  for (const row of rows) {
    assert.ok(allowedStatuses.includes(row.status), `Unexpected status '${row.status}' in row: ${row.line}`);
  }
  assert.deepEqual([...new Set(rows.map((row) => row.status))].sort(), [
    "api-backed-command",
    "api-backed-read",
    "backend-control-covered",
    "complete",
    "integrated-terminal",
    "live-keycloak-backed",
    "partial",
    "route-backed-live-gated"
  ]);
});

test("partial or missing matrix rows are not presented as complete", async () => {
  const matrix = await readFile("docs/implementation-coverage-matrix.md", "utf8");
  const limitedRows = tableRows(matrix).filter((row) => row.status === "partial" || row.status === "missing");

  assert.ok(limitedRows.length > 0, "matrix should keep at least one explicit non-complete row while gaps remain");
  for (const row of limitedRows) {
    assert.doesNotMatch(row.evidence, /\bcomplete\b/i, `Non-complete row evidence should not claim complete: ${row.feature}`);
  }
});

test("README avoids outdated or production-readiness claims", async () => {
  const readme = await readFile("README.md", "utf8");

  assert.doesNotMatch(readme, /production-ready|real banking ready|actual payment network ready/i);
  assert.doesNotMatch(readme, /runtime persistence is in-memory/i);
  assert.doesNotMatch(readme, /100\+ catalog breadth/i);
  assert.doesNotMatch(readme, /add a synthetic-only call-center workflow/i);
  assert.match(readme, /Node runtime remains an in-memory archived reference\/oracle path only/);
  assert.match(readme, /current manifest catalog contains 73 synthetic screens/);
  assert.match(readme, /rerun the call-center live synthetic API\/Keycloak browser wrapper/);
  assert.doesNotMatch(readme, /decide whether call-center escalation should become maker-checker/);
  assert.match(readme, /A skipped env-gated Playwright smoke is not\s+counted as a live API pass/);
  assert.match(readme, /no real customer PII/);
});
