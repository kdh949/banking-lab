from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class RuleScore:
    rule_score: int
    alerts: tuple[str, ...]
    risk_band: str


def evaluate_rule_inputs(
    *,
    amount_minor: int,
    new_device: bool,
    first_time_beneficiary: bool,
    velocity_24h: int,
    risk_grade: str,
) -> RuleScore:
    alerts: list[str] = []
    score = 0

    if amount_minor >= 5_000_000:
        alerts.append("FDS_LARGE_TRANSFER")
        score += 300
    if new_device:
        alerts.append("FDS_NEW_DEVICE")
        score += 250
    if first_time_beneficiary:
        alerts.append("FDS_FIRST_TIME_BENEFICIARY")
        score += 200
    if velocity_24h >= 5:
        alerts.append("AML_HIGH_VELOCITY")
        score += 200
    if risk_grade.upper() in {"HIGH", "REVIEW_REQUIRED"}:
        alerts.append("AML_HIGH_RISK_CUSTOMER")
        score += 250

    capped_score = min(score, 1000)
    return RuleScore(
        rule_score=capped_score,
        alerts=tuple(alerts),
        risk_band=risk_band(capped_score),
    )


def risk_band(score: int) -> str:
    if score >= 700:
        return "HIGH"
    if score >= 300:
        return "REVIEW"
    return "LOW"
