from __future__ import annotations

from dataclasses import dataclass


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


def score_transaction(candidate: AmlFdsInput) -> AmlFdsScore:
    """Rule-based synthetic scoring; no real PII, money, or network data."""
    alerts: list[str] = []
    score = 0

    if candidate.amount_minor >= 5_000_000:
        alerts.append("FDS_LARGE_TRANSFER")
        score += 300
    if candidate.new_device:
        alerts.append("FDS_NEW_DEVICE")
        score += 250
    if candidate.first_time_beneficiary:
        alerts.append("FDS_FIRST_TIME_BENEFICIARY")
        score += 200
    if candidate.velocity_24h >= 5:
        alerts.append("AML_HIGH_VELOCITY")
        score += 200
    if candidate.risk_grade.upper() in {"HIGH", "REVIEW_REQUIRED"}:
        alerts.append("AML_HIGH_RISK_CUSTOMER")
        score += 250

    return AmlFdsScore(score=min(score, 1000), alerts=tuple(alerts))
