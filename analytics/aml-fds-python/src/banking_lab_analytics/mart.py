from __future__ import annotations

from pathlib import Path
from typing import Any

import duckdb


def connect_mart() -> Any:
    return duckdb.connect(database=":memory:")


def load_transactions(connection: Any, input_path: str | Path) -> None:
    path = Path(input_path)
    if not path.exists():
        raise FileNotFoundError(f"synthetic transaction input not found: {path}")
    escaped_path = str(path).replace("'", "''")
    connection.execute(
        f"""
        CREATE OR REPLACE TABLE raw_transactions AS
        SELECT
          CAST(transaction_id AS VARCHAR) AS transaction_id,
          CAST(customer_id AS VARCHAR) AS customer_id,
          CAST(account_id AS VARCHAR) AS account_id,
          CAST(beneficiary_account_id AS VARCHAR) AS beneficiary_account_id,
          CAST(amount_minor AS BIGINT) AS amount_minor,
          CAST(currency AS VARCHAR) AS currency,
          CAST(event_time AS TIMESTAMP) AS event_time,
          CAST(channel AS VARCHAR) AS channel,
          CAST(device_id AS VARCHAR) AS device_id,
          CAST(new_device AS BOOLEAN) AS new_device,
          CAST(status AS VARCHAR) AS status,
          CAST(risk_grade AS VARCHAR) AS risk_grade
        FROM read_csv_auto('{escaped_path}', header = true)
        """
    )
    connection.execute(
        """
        CREATE OR REPLACE VIEW posted_transactions AS
        SELECT *
        FROM raw_transactions
        WHERE status = 'POSTED'
        """
    )


def transaction_count(connection: Any) -> int:
    return int(connection.execute("SELECT count(*) FROM raw_transactions").fetchone()[0])
