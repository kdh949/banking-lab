export function deriveAmlRisk(customer, transactionVelocity = 0) {
  if (customer.riskGrade === "HIGH" || transactionVelocity > 10) {
    return "EDD_REQUIRED";
  }
  if (customer.riskGrade === "MEDIUM") {
    return "MONITOR";
  }
  return "STANDARD";
}
