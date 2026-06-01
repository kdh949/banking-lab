export function evaluateFdsRules(transfer) {
  const alerts = [];
  if (transfer.amountMinor >= 5000000) {
    alerts.push({
      ruleId: "FDS-RULE-UNUSUAL-AMOUNT",
      severity: "HIGH",
      action: "HOLD",
      reason: "Synthetic high amount threshold"
    });
  }
  return alerts;
}
