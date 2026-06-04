from banking_lab_analytics.rules import evaluate_rule_inputs


def test_rule_engine_flags_high_amount_new_beneficiary_velocity_and_risk_grade() -> None:
    score = evaluate_rule_inputs(
        amount_minor=7_500_000,
        new_device=True,
        first_time_beneficiary=True,
        velocity_24h=5,
        risk_grade="HIGH",
    )

    assert score.rule_score == 1000
    assert score.risk_band == "HIGH"
    assert "FDS_LARGE_TRANSFER" in score.alerts
    assert "FDS_FIRST_TIME_BENEFICIARY" in score.alerts
    assert "AML_HIGH_VELOCITY" in score.alerts
    assert "AML_HIGH_RISK_CUSTOMER" in score.alerts


def test_rule_engine_keeps_normal_case_low() -> None:
    score = evaluate_rule_inputs(
        amount_minor=50_000,
        new_device=False,
        first_time_beneficiary=False,
        velocity_24h=1,
        risk_grade="LOW",
    )

    assert score.rule_score == 0
    assert score.risk_band == "LOW"
    assert score.alerts == ()
