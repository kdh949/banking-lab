from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

from .mart import connect_mart

AS_OF = "2026-06-05T00:00:00Z"


def run_data_platform(
    source_dir: str | Path,
    output_dir: str | Path,
    *,
    inject_dirty_data: bool = False,
) -> dict[str, Any]:
    """Build deterministic synthetic data-platform marts and DQ evidence."""
    source_root = Path(source_dir)
    output_root = Path(output_dir)
    output_root.mkdir(parents=True, exist_ok=True)

    postings_path = source_root / "ledger_postings.csv"
    projections_path = source_root / "account_balance_projections.csv"
    for path in (postings_path, projections_path):
        if not path.exists():
            raise FileNotFoundError(f"synthetic data-platform source not found: {path}")

    connection = connect_mart()
    load_sources(connection, postings_path, projections_path)
    if inject_dirty_data:
        inject_dirty_fixture(connection)

    create_marts(connection)
    write_parquet_marts(connection, output_root)
    dq_checks = run_dq_checks(connection)
    risk_report = build_risk_report(connection)
    lineage = build_lineage(postings_path, projections_path, output_root)
    evidence = {
        "generatedAt": AS_OF,
        "engine": "banking_lab_analytics.duckdb_data_platform",
        "syntheticOnly": True,
        "dqStatus": "pass" if all(check["status"] == "pass" for check in dq_checks) else "fail",
        "controls": {
            "realMoneyUsed": False,
            "realPiiUsed": False,
            "realBankNetworkUsed": False,
            "realKycUsed": False,
            "realExternalProviderUsed": False,
            "duckdbMartGenerated": True,
            "parquetMartsWritten": True,
            "deterministicReporting": True,
        },
        "sourceHashes": {
            "ledgerPostingsSha256": sha256_file(postings_path),
            "accountBalanceProjectionsSha256": sha256_file(projections_path),
        },
        "martRowCounts": {
            "financeBalanceMart": scalar(connection, "SELECT count(*) FROM finance_balance_mart"),
            "riskExposureMart": scalar(connection, "SELECT count(*) FROM risk_exposure_mart"),
        },
        "dqChecks": dq_checks,
        "lineage": lineage,
        "riskReport": risk_report,
    }
    write_json(output_root / "data-platform-evidence.json", evidence)
    write_json(output_root / "data-lineage.json", lineage)
    write_json(output_root / "synthetic-risk-report.json", risk_report)
    return evidence


def load_sources(connection: Any, postings_path: Path, projections_path: Path) -> None:
    connection.execute(
        f"""
        CREATE OR REPLACE TABLE ledger_postings AS
        SELECT
          CAST(posting_id AS VARCHAR) AS posting_id,
          CAST(ledger_transaction_id AS VARCHAR) AS ledger_transaction_id,
          CAST(account_id AS VARCHAR) AS account_id,
          CAST(currency AS VARCHAR) AS currency,
          CAST(direction AS VARCHAR) AS direction,
          CAST(amount_minor AS BIGINT) AS amount_minor,
          CAST(business_date AS DATE) AS business_date,
          CAST(status AS VARCHAR) AS status,
          CAST(posting_type AS VARCHAR) AS posting_type
        FROM read_csv_auto('{escaped(postings_path)}', header = true)
        """
    )
    connection.execute(
        f"""
        CREATE OR REPLACE TABLE account_balance_projections AS
        SELECT
          CAST(account_id AS VARCHAR) AS account_id,
          CAST(customer_id AS VARCHAR) AS customer_id,
          CAST(currency AS VARCHAR) AS currency,
          CAST(ledger_balance_minor AS BIGINT) AS ledger_balance_minor,
          CAST(available_balance_minor AS BIGINT) AS available_balance_minor,
          CAST(hold_amount_minor AS BIGINT) AS hold_amount_minor,
          CAST(risk_grade AS VARCHAR) AS risk_grade,
          CAST(account_status AS VARCHAR) AS account_status
        FROM read_csv_auto('{escaped(projections_path)}', header = true)
        """
    )


def inject_dirty_fixture(connection: Any) -> None:
    connection.execute(
        """
        UPDATE account_balance_projections
        SET ledger_balance_minor = ledger_balance_minor + 1
        WHERE account_id = 'ACC-DATA-001'
        """
    )
    connection.execute(
        """
        INSERT INTO account_balance_projections
        SELECT *
        FROM account_balance_projections
        WHERE account_id = 'ACC-DATA-002'
        """
    )


def create_marts(connection: Any) -> None:
    connection.execute(
        """
        CREATE OR REPLACE TABLE finance_balance_mart AS
        SELECT
          p.account_id,
          p.currency,
          SUM(
            CASE
              WHEN p.direction = 'CREDIT' THEN p.amount_minor
              WHEN p.direction = 'DEBIT' THEN -p.amount_minor
              ELSE 0
            END
          )::BIGINT AS posting_balance_minor,
          count(*)::BIGINT AS posting_count
        FROM ledger_postings p
        JOIN account_balance_projections b
          ON b.account_id = p.account_id
         AND b.currency = p.currency
        WHERE p.status = 'POSTED'
        GROUP BY p.account_id, p.currency
        ORDER BY p.account_id, p.currency
        """
    )
    connection.execute(
        """
        CREATE OR REPLACE TABLE risk_exposure_mart AS
        SELECT
          b.account_id,
          b.customer_id,
          b.currency,
          b.risk_grade,
          b.account_status,
          b.ledger_balance_minor,
          b.available_balance_minor,
          b.hold_amount_minor,
          f.posting_balance_minor,
          CASE
            WHEN b.risk_grade = 'HIGH' THEN 'HIGH_EXPOSURE'
            WHEN b.hold_amount_minor > 0 THEN 'LIQUIDITY_WATCH'
            ELSE 'STANDARD'
          END AS exposure_bucket
        FROM account_balance_projections b
        LEFT JOIN finance_balance_mart f
          ON f.account_id = b.account_id
         AND f.currency = b.currency
        ORDER BY b.account_id, b.currency
        """
    )


def write_parquet_marts(connection: Any, output_root: Path) -> None:
    finance_path = output_root / "finance_balance_mart.parquet"
    risk_path = output_root / "risk_exposure_mart.parquet"
    connection.execute(f"COPY finance_balance_mart TO '{escaped(finance_path)}' (FORMAT parquet)")
    connection.execute(f"COPY risk_exposure_mart TO '{escaped(risk_path)}' (FORMAT parquet)")


def run_dq_checks(connection: Any) -> list[dict[str, Any]]:
    checks = [
        dq_check(
            "source_null_account_ids",
            "null account_id values are not allowed in source extracts",
            scalar(
                connection,
                """
                SELECT
                  (SELECT count(*) FROM ledger_postings WHERE account_id IS NULL)
                  + (SELECT count(*) FROM account_balance_projections WHERE account_id IS NULL)
                """,
            ),
        ),
        dq_check(
            "projection_unique_account_currency",
            "account balance projections must be unique by account and currency",
            scalar(
                connection,
                """
                SELECT count(*)
                FROM (
                  SELECT account_id, currency
                  FROM account_balance_projections
                  GROUP BY account_id, currency
                  HAVING count(*) > 1
                )
                """,
            ),
        ),
        dq_check(
            "posting_amount_positive",
            "ledger posting amounts must be positive",
            scalar(connection, "SELECT count(*) FROM ledger_postings WHERE amount_minor <= 0"),
        ),
        dq_check(
            "projection_balance_range",
            "projection balances must be non-negative and available balance cannot exceed ledger balance",
            scalar(
                connection,
                """
                SELECT count(*)
                FROM account_balance_projections
                WHERE ledger_balance_minor < 0
                   OR available_balance_minor < 0
                   OR hold_amount_minor < 0
                   OR available_balance_minor > ledger_balance_minor
                """,
            ),
        ),
        dq_check(
            "ledger_projection_reconciliation",
            "finance balance mart must equal account balance projections",
            scalar(
                connection,
                """
                SELECT count(*)
                FROM risk_exposure_mart
                WHERE coalesce(posting_balance_minor, 0) <> ledger_balance_minor
                """,
            ),
        ),
    ]
    return checks


def dq_check(check_id: str, description: str, failed_rows: int) -> dict[str, Any]:
    return {
        "checkId": check_id,
        "description": description,
        "status": "pass" if failed_rows == 0 else "fail",
        "failedRows": failed_rows,
    }


def build_risk_report(connection: Any) -> dict[str, Any]:
    totals = connection.execute(
        """
        SELECT
          currency,
          sum(ledger_balance_minor)::BIGINT AS total_ledger_balance_minor,
          sum(available_balance_minor)::BIGINT AS total_available_balance_minor,
          sum(hold_amount_minor)::BIGINT AS total_hold_amount_minor,
          sum(CASE WHEN risk_grade = 'HIGH' THEN ledger_balance_minor ELSE 0 END)::BIGINT AS high_risk_exposure_minor,
          count(*)::BIGINT AS account_count
        FROM risk_exposure_mart
        GROUP BY currency
        ORDER BY currency
        """
    ).fetchone()
    buckets = connection.execute(
        """
        SELECT
          risk_grade,
          sum(ledger_balance_minor)::BIGINT AS exposure_minor,
          count(*)::BIGINT AS account_count
        FROM risk_exposure_mart
        GROUP BY risk_grade
        ORDER BY risk_grade
        """
    ).fetchall()
    total_ledger = int(totals[1])
    total_available = int(totals[2])
    high_risk = int(totals[4])
    report = {
        "reportId": "RISK-SYN-H7-LIQUIDITY-EXPOSURE",
        "asOf": AS_OF,
        "currency": str(totals[0]),
        "syntheticOnly": True,
        "totalLedgerBalanceMinor": total_ledger,
        "totalAvailableBalanceMinor": total_available,
        "totalHoldAmountMinor": int(totals[3]),
        "highRiskExposureMinor": high_risk,
        "accountCount": int(totals[5]),
        "availableToLedgerRatio": round(total_available / total_ledger, 6) if total_ledger else 0,
        "highRiskExposureRatio": round(high_risk / total_ledger, 6) if total_ledger else 0,
        "riskBuckets": [
            {
                "riskGrade": str(row[0]),
                "exposureMinor": int(row[1]),
                "accountCount": int(row[2]),
            }
            for row in buckets
        ],
    }
    report["reportHash"] = sha256_json(report)
    return report


def build_lineage(postings_path: Path, projections_path: Path, output_root: Path) -> dict[str, Any]:
    return {
        "generatedAt": AS_OF,
        "syntheticOnly": True,
        "nodes": [
            {
                "id": "source.synthetic_oltp.ledger_postings",
                "kind": "source",
                "path": str(postings_path),
            },
            {
                "id": "source.synthetic_oltp.account_balance_projections",
                "kind": "source",
                "path": str(projections_path),
            },
            {
                "id": "mart.finance_balance_mart",
                "kind": "duckdb_parquet_mart",
                "path": str(output_root / "finance_balance_mart.parquet"),
            },
            {
                "id": "mart.risk_exposure_mart",
                "kind": "duckdb_parquet_mart",
                "path": str(output_root / "risk_exposure_mart.parquet"),
            },
            {
                "id": "report.synthetic_liquidity_exposure",
                "kind": "risk_report",
                "path": str(output_root / "synthetic-risk-report.json"),
            },
        ],
        "fieldLineage": {
            "riskReport.totalAvailableBalanceMinor": [
                "source.synthetic_oltp.account_balance_projections.available_balance_minor",
                "mart.risk_exposure_mart.available_balance_minor",
                "report.synthetic_liquidity_exposure.totalAvailableBalanceMinor",
            ],
            "riskReport.totalLedgerBalanceMinor": [
                "source.synthetic_oltp.ledger_postings.direction",
                "source.synthetic_oltp.ledger_postings.amount_minor",
                "mart.finance_balance_mart.posting_balance_minor",
                "mart.risk_exposure_mart.ledger_balance_minor",
                "report.synthetic_liquidity_exposure.totalLedgerBalanceMinor",
            ],
            "riskReport.highRiskExposureMinor": [
                "source.synthetic_oltp.account_balance_projections.risk_grade",
                "source.synthetic_oltp.account_balance_projections.ledger_balance_minor",
                "mart.risk_exposure_mart.risk_grade",
                "report.synthetic_liquidity_exposure.highRiskExposureMinor",
            ],
        },
    }


def resolve_lineage(evidence: dict[str, Any], report_field: str) -> list[str]:
    lineage = evidence.get("lineage", {}).get("fieldLineage", {})
    value = lineage.get(report_field, [])
    return list(value) if isinstance(value, list) else []


def scalar(connection: Any, sql: str) -> int:
    row = connection.execute(sql).fetchone()
    return int(row[0]) if row is not None else 0


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.write_text(f"{json.dumps(payload, indent=2, sort_keys=True)}\n", encoding="utf8")


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def sha256_json(payload: dict[str, Any]) -> str:
    return hashlib.sha256(json.dumps(payload, sort_keys=True).encode("utf8")).hexdigest()


def escaped(path: Path) -> str:
    return str(path).replace("'", "''")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Build synthetic data-platform marts and DQ evidence.")
    parser.add_argument("--source-dir", required=True, help="Directory with synthetic OLTP extract CSV files.")
    parser.add_argument("--output-dir", required=True, help="Directory for generated evidence and parquet marts.")
    parser.add_argument("--inject-dirty-data", action="store_true", help="Inject dirty data and expect DQ failure.")
    args = parser.parse_args(argv)
    evidence = run_data_platform(args.source_dir, args.output_dir, inject_dirty_data=args.inject_dirty_data)
    print(
        "Data-platform DQ gate: "
        f"{evidence['dqStatus']} "
        f"({len(evidence['dqChecks'])} checks, "
        f"{evidence['martRowCounts']['riskExposureMart']} risk rows)"
    )
    return 0 if evidence["dqStatus"] == "pass" else 1


if __name__ == "__main__":
    raise SystemExit(main())
