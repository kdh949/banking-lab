from pathlib import Path

from banking_lab_analytics.feature_store import generate_feature_rows
from banking_lab_analytics.mart import connect_mart, load_transactions, transaction_count


SAMPLE_DATA = Path(__file__).resolve().parents[1] / "sample-data" / "transactions.csv"


def test_duckdb_mart_generates_velocity_and_beneficiary_features() -> None:
    connection = connect_mart()
    load_transactions(connection, SAMPLE_DATA)

    rows = generate_feature_rows(connection)
    by_id = {row.transaction_id: row for row in rows}

    assert transaction_count(connection) == 9
    assert by_id["TX-AML-001"].first_time_beneficiary is True
    assert by_id["TX-AML-002"].first_time_beneficiary is False
    assert by_id["TX-AML-008"].velocity_24h == 5
    assert by_id["TX-AML-003"].high_amount_anomaly is True
    assert by_id["TX-AML-003"].risk_grade == "HIGH"
    assert by_id["TX-AML-001"].synthetic_only is True
