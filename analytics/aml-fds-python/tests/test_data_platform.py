from pathlib import Path

from banking_lab_analytics.data_platform import resolve_lineage, run_data_platform


SAMPLE_DIR = Path(__file__).resolve().parents[1] / "sample-data" / "data-platform"


def test_data_platform_builds_parquet_marts_and_reconciles_balances(tmp_path: Path) -> None:
    artifact = run_data_platform(SAMPLE_DIR, tmp_path)

    assert artifact["syntheticOnly"] is True
    assert artifact["dqStatus"] == "pass"
    assert artifact["controls"]["parquetMartsWritten"] is True
    assert artifact["martRowCounts"]["financeBalanceMart"] == 3
    assert artifact["martRowCounts"]["riskExposureMart"] == 3
    assert all(check["status"] == "pass" for check in artifact["dqChecks"])
    assert (tmp_path / "finance_balance_mart.parquet").exists()
    assert (tmp_path / "risk_exposure_mart.parquet").exists()
    assert artifact["riskReport"]["totalLedgerBalanceMinor"] == 6_630_000
    assert artifact["riskReport"]["totalAvailableBalanceMinor"] == 6_080_000
    assert artifact["riskReport"]["highRiskExposureMinor"] == 3_500_000


def test_data_quality_gate_fails_on_injected_dirty_data(tmp_path: Path) -> None:
    artifact = run_data_platform(SAMPLE_DIR, tmp_path, inject_dirty_data=True)
    checks = {check["checkId"]: check for check in artifact["dqChecks"]}

    assert artifact["dqStatus"] == "fail"
    assert checks["projection_unique_account_currency"]["status"] == "fail"
    assert checks["ledger_projection_reconciliation"]["status"] == "fail"
    assert checks["ledger_projection_reconciliation"]["failedRows"] >= 1


def test_lineage_resolves_report_field_to_source_and_mart(tmp_path: Path) -> None:
    artifact = run_data_platform(SAMPLE_DIR, tmp_path)

    lineage_path = resolve_lineage(artifact, "riskReport.totalAvailableBalanceMinor")

    assert lineage_path == [
        "source.synthetic_oltp.account_balance_projections.available_balance_minor",
        "mart.risk_exposure_mart.available_balance_minor",
        "report.synthetic_liquidity_exposure.totalAvailableBalanceMinor",
    ]


def test_risk_report_is_reproducible_for_fixed_inputs(tmp_path: Path) -> None:
    first = run_data_platform(SAMPLE_DIR, tmp_path / "first")
    second = run_data_platform(SAMPLE_DIR, tmp_path / "second")

    assert first["riskReport"] == second["riskReport"]
    assert first["riskReport"]["reportHash"] == second["riskReport"]["reportHash"]
