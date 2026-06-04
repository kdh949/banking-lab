from __future__ import annotations

from dataclasses import dataclass

from .feature_store import FeatureRow


@dataclass(frozen=True)
class AnomalyScore:
    anomaly_score: int
    reasons: tuple[str, ...]


def deterministic_anomaly_score(feature: FeatureRow) -> AnomalyScore:
    reasons: list[str] = []
    score = 0

    if feature.amount_to_median_ratio >= 20:
        reasons.append("AMOUNT_TO_MEDIAN_GE_20X")
        score += 300
    elif feature.amount_to_median_ratio >= 10:
        reasons.append("AMOUNT_TO_MEDIAN_GE_10X")
        score += 200

    if feature.high_amount_anomaly:
        reasons.append("HIGH_AMOUNT_ABSOLUTE_THRESHOLD")
        score += 150
    if feature.velocity_24h >= 5:
        reasons.append("VELOCITY_24H_GE_5")
        score += 100
    if feature.first_time_beneficiary and feature.amount_minor >= 1_000_000:
        reasons.append("NEW_BENEFICIARY_HIGH_AMOUNT")
        score += 100

    return AnomalyScore(anomaly_score=min(score, 500), reasons=tuple(reasons))
