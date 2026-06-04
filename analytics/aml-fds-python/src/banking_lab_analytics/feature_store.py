from __future__ import annotations

from dataclasses import dataclass, asdict
from datetime import datetime
from typing import Any


@dataclass(frozen=True)
class FeatureRow:
    transaction_id: str
    customer_id: str
    account_id: str
    beneficiary_account_id: str
    amount_minor: int
    currency: str
    event_time: datetime
    channel: str
    device_id: str
    new_device: bool
    status: str
    risk_grade: str
    velocity_24h: int
    first_time_beneficiary: bool
    high_amount_anomaly: bool
    portfolio_median_amount_minor: int
    amount_to_median_ratio: float
    synthetic_only: bool = True

    def to_json(self) -> dict[str, Any]:
        payload = asdict(self)
        payload["event_time"] = self.event_time.isoformat()
        payload["amount_to_median_ratio"] = round(self.amount_to_median_ratio, 4)
        return payload


def generate_feature_rows(connection: Any) -> list[FeatureRow]:
    rows = connection.execute(
        """
        WITH baseline AS (
          SELECT CAST(quantile_cont(amount_minor, 0.5) AS BIGINT) AS median_amount_minor
          FROM raw_transactions
        )
        SELECT
          t.transaction_id,
          t.customer_id,
          t.account_id,
          t.beneficiary_account_id,
          t.amount_minor,
          t.currency,
          t.event_time,
          t.channel,
          t.device_id,
          t.new_device,
          t.status,
          t.risk_grade,
          (
            SELECT count(*)
            FROM raw_transactions prior
            WHERE prior.customer_id = t.customer_id
              AND prior.event_time BETWEEN t.event_time - INTERVAL 24 HOURS AND t.event_time
          ) AS velocity_24h,
          NOT EXISTS (
            SELECT 1
            FROM raw_transactions prior_beneficiary
            WHERE prior_beneficiary.customer_id = t.customer_id
              AND prior_beneficiary.beneficiary_account_id = t.beneficiary_account_id
              AND (
                prior_beneficiary.event_time < t.event_time
                OR (
                  prior_beneficiary.event_time = t.event_time
                  AND prior_beneficiary.transaction_id < t.transaction_id
                )
              )
          ) AS first_time_beneficiary,
          t.amount_minor >= 5000000 AS high_amount_anomaly,
          baseline.median_amount_minor,
          CASE
            WHEN baseline.median_amount_minor <= 0 THEN 0
            ELSE CAST(t.amount_minor AS DOUBLE) / CAST(baseline.median_amount_minor AS DOUBLE)
          END AS amount_to_median_ratio
        FROM raw_transactions t
        CROSS JOIN baseline
        ORDER BY t.event_time, t.transaction_id
        """
    ).fetchall()
    return [
        FeatureRow(
            transaction_id=str(row[0]),
            customer_id=str(row[1]),
            account_id=str(row[2]),
            beneficiary_account_id=str(row[3]),
            amount_minor=int(row[4]),
            currency=str(row[5]),
            event_time=row[6],
            channel=str(row[7]),
            device_id=str(row[8]),
            new_device=bool(row[9]),
            status=str(row[10]),
            risk_grade=str(row[11]),
            velocity_24h=int(row[12]),
            first_time_beneficiary=bool(row[13]),
            high_amount_anomaly=bool(row[14]),
            portfolio_median_amount_minor=int(row[15]),
            amount_to_median_ratio=float(row[16]),
        )
        for row in rows
    ]
