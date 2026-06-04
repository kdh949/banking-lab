from pathlib import Path

from banking_lab_analytics import AmlFdsInput, score_transaction
from banking_lab_analytics.scoring import score_file


SAMPLE_DATA = Path(__file__).resolve().parents[1] / "sample-data" / "transactions.csv"


def test_high_risk_transfer_is_explainable() -> None:
    result = score_transaction(
        AmlFdsInput(
            customer_id="SYN-CUS-001",
            amount_minor=7_000_000,
            new_device=True,
            first_time_beneficiary=True,
            velocity_24h=6,
            risk_grade="HIGH",
        )
    )

    assert result.synthetic_only is True
    assert result.score == 1000
    assert "FDS_LARGE_TRANSFER" in result.alerts
    assert "AML_HIGH_RISK_CUSTOMER" in result.alerts


def test_low_risk_transfer_has_no_alerts() -> None:
    result = score_transaction(AmlFdsInput(customer_id="SYN-CUS-002", amount_minor=50_000))

    assert result.score == 0
    assert result.alerts == ()


def test_scoring_cli_artifact_is_deterministic_for_sample_data(tmp_path: Path) -> None:
    output = tmp_path / "fds-aml-analytics.json"
    csv_output = tmp_path / "fds-aml-analytics.csv"

    artifact = score_file(SAMPLE_DATA, output, csv_output)

    assert output.exists()
    assert csv_output.exists()
    assert artifact["syntheticOnly"] is True
    assert artifact["sourceRecordCount"] == 9
    assert artifact["scoredRecordCount"] == 9
    assert artifact["controls"]["duckdbMartGenerated"] is True
    assert artifact["riskDistribution"]["HIGH"] >= 1
    assert artifact["alertCounts"]["FDS_LARGE_TRANSFER"] == 1
    assert artifact["alertCounts"]["AML_HIGH_VELOCITY"] == 1
    assert artifact["topRisks"][0]["transactionId"] == "TX-AML-003"
    assert artifact["topRisks"][0]["totalScore"] == 1000
