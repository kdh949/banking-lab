from banking_lab_analytics import AmlFdsInput, score_transaction


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
