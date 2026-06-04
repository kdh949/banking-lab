package lab.banking.core.analytics

data class FdsAnalyticsEvidenceDto(
    val engine: String,
    val generatedAt: String,
    val controls: Map<String, Boolean>,
    val scoredTransactions: Int,
    val highRiskResults: Int,
    val alertCounts: Map<String, Int>,
    val highestRisk: FdsAnalyticsResultDto?,
    val auditEventId: String,
    val syntheticOnly: Boolean = true
)

data class FdsAnalyticsResultDto(
    val transactionId: String,
    val customerId: String,
    val riskBand: String,
    val totalScore: Int,
    val alerts: List<String>,
    val syntheticOnly: Boolean = true
)
