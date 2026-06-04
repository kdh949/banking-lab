from __future__ import annotations

import argparse
import csv
import json
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from .feature_store import FeatureRow, generate_feature_rows
from .mart import connect_mart, load_transactions, transaction_count
from .model import deterministic_anomaly_score
from .rules import evaluate_rule_inputs, risk_band


@dataclass(frozen=True)
class AmlFdsInput:
    customer_id: str
    amount_minor: int
    new_device: bool = False
    first_time_beneficiary: bool = False
    velocity_24h: int = 0
    risk_grade: str = "LOW"


@dataclass(frozen=True)
class AmlFdsScore:
    score: int
    alerts: tuple[str, ...]
    synthetic_only: bool = True


@dataclass(frozen=True)
class ScoredTransaction:
    transaction_id: str
    customer_id: str
    amount_minor: int
    currency: str
    event_time: str
    rule_score: int
    anomaly_score: int
    total_score: int
    risk_band: str
    alerts: tuple[str, ...]
    anomaly_reasons: tuple[str, ...]
    features: dict[str, Any]
    synthetic_only: bool = True

    def to_json(self) -> dict[str, Any]:
        return {
            "transactionId": self.transaction_id,
            "customerId": self.customer_id,
            "amountMinor": self.amount_minor,
            "currency": self.currency,
            "eventTime": self.event_time,
            "ruleScore": self.rule_score,
            "anomalyScore": self.anomaly_score,
            "totalScore": self.total_score,
            "riskBand": self.risk_band,
            "alerts": list(self.alerts),
            "anomalyReasons": list(self.anomaly_reasons),
            "features": self.features,
            "syntheticOnly": self.synthetic_only,
        }


def score_transaction(candidate: AmlFdsInput) -> AmlFdsScore:
    """Rule-based synthetic scoring; no real PII, money, or network data."""
    rule_score = evaluate_rule_inputs(
        amount_minor=candidate.amount_minor,
        new_device=candidate.new_device,
        first_time_beneficiary=candidate.first_time_beneficiary,
        velocity_24h=candidate.velocity_24h,
        risk_grade=candidate.risk_grade,
    )
    return AmlFdsScore(score=rule_score.rule_score, alerts=rule_score.alerts)


def score_feature_row(feature: FeatureRow) -> ScoredTransaction:
    rule_score = evaluate_rule_inputs(
        amount_minor=feature.amount_minor,
        new_device=feature.new_device,
        first_time_beneficiary=feature.first_time_beneficiary,
        velocity_24h=feature.velocity_24h,
        risk_grade=feature.risk_grade,
    )
    anomaly = deterministic_anomaly_score(feature)
    total_score = min(rule_score.rule_score + anomaly.anomaly_score, 1000)
    return ScoredTransaction(
        transaction_id=feature.transaction_id,
        customer_id=feature.customer_id,
        amount_minor=feature.amount_minor,
        currency=feature.currency,
        event_time=feature.event_time.isoformat(),
        rule_score=rule_score.rule_score,
        anomaly_score=anomaly.anomaly_score,
        total_score=total_score,
        risk_band=risk_band(total_score),
        alerts=rule_score.alerts,
        anomaly_reasons=anomaly.reasons,
        features=feature.to_json(),
    )


def score_file(input_path: str | Path, output_path: str | Path, csv_output_path: str | Path | None = None) -> dict[str, Any]:
    connection = connect_mart()
    load_transactions(connection, input_path)
    features = generate_feature_rows(connection)
    scored = [score_feature_row(feature) for feature in features]
    artifact = build_artifact(input_path, transaction_count(connection), scored)
    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(f"{json.dumps(artifact, indent=2, sort_keys=True)}\n", encoding="utf8")
    if csv_output_path is not None:
        write_csv(scored, csv_output_path)
    return artifact


def build_artifact(input_path: str | Path, source_record_count: int, scored: list[ScoredTransaction]) -> dict[str, Any]:
    alert_counts: dict[str, int] = {}
    risk_distribution: dict[str, int] = {}
    for item in scored:
        risk_distribution[item.risk_band] = risk_distribution.get(item.risk_band, 0) + 1
        for alert in item.alerts:
            alert_counts[alert] = alert_counts.get(alert, 0) + 1

    top_risks = sorted(scored, key=lambda item: item.total_score, reverse=True)[:5]
    return {
        "generatedAt": datetime.now(UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "source": str(input_path),
        "engine": "banking_lab_analytics.duckdb_batch",
        "syntheticOnly": True,
        "sourceRecordCount": source_record_count,
        "scoredRecordCount": len(scored),
        "riskDistribution": risk_distribution,
        "alertCounts": alert_counts,
        "controls": {
            "realMoneyUsed": False,
            "realPiiUsed": False,
            "realBankNetworkUsed": False,
            "duckdbMartGenerated": True,
            "deterministicScoring": True,
        },
        "topRisks": [item.to_json() for item in top_risks],
        "results": [item.to_json() for item in scored],
    }


def write_csv(scored: list[ScoredTransaction], csv_output_path: str | Path) -> None:
    output = Path(csv_output_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            lineterminator="\n",
            fieldnames=[
                "transactionId",
                "customerId",
                "amountMinor",
                "currency",
                "eventTime",
                "ruleScore",
                "anomalyScore",
                "totalScore",
                "riskBand",
                "alerts",
                "anomalyReasons",
                "syntheticOnly",
            ],
        )
        writer.writeheader()
        for item in scored:
            writer.writerow(
                {
                    "transactionId": item.transaction_id,
                    "customerId": item.customer_id,
                    "amountMinor": item.amount_minor,
                    "currency": item.currency,
                    "eventTime": item.event_time,
                    "ruleScore": item.rule_score,
                    "anomalyScore": item.anomaly_score,
                    "totalScore": item.total_score,
                    "riskBand": item.risk_band,
                    "alerts": "|".join(item.alerts),
                    "anomalyReasons": "|".join(item.anomaly_reasons),
                    "syntheticOnly": item.synthetic_only,
                }
            )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Generate synthetic Banking Lab AML/FDS scoring evidence.")
    parser.add_argument("--input", required=True, help="Synthetic transaction CSV input.")
    parser.add_argument("--output", required=True, help="Generated JSON artifact path.")
    parser.add_argument("--csv-output", help="Optional scored transaction CSV output.")
    args = parser.parse_args(argv)
    artifact = score_file(args.input, args.output, args.csv_output)
    print(
        "AML/FDS analytics artifact generated: "
        f"{artifact['scoredRecordCount']} scored records, "
        f"{len(artifact['alertCounts'])} alert families"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
