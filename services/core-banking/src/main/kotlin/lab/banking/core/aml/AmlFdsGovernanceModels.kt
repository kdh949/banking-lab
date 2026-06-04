package lab.banking.core.aml

import java.time.OffsetDateTime

data class SanctionsScreenCustomerCommand(
    val actorId: String,
    val actorRole: String = "AML_REVIEWER",
    val reason: String
)

data class SanctionsScreenTransferCommand(
    val customerId: String,
    val transferReferenceId: String,
    val counterpartyName: String,
    val actorId: String,
    val actorRole: String = "AML_REVIEWER",
    val reason: String
)

data class SanctionsScreeningResultDto(
    val hits: List<SanctionsScreeningHitDto>,
    val amlCase: AmlCaseDto?,
    val syntheticOnly: Boolean = true
)

data class SanctionsScreeningHitDto(
    val hitId: String,
    val watchlistEntryId: String,
    val listType: String,
    val displayName: String,
    val customerId: String,
    val transferReferenceId: String?,
    val amlCaseId: String?,
    val matchType: String,
    val matchedValue: String,
    val riskScore: Int,
    val status: String,
    val disposition: String?,
    val dispositionReason: String?,
    val dispositionBy: String?,
    val approvedBy: String?,
    val createdAt: OffsetDateTime,
    val dispositionedAt: OffsetDateTime?,
    val syntheticOnly: Boolean = true
)

data class FalsePositiveDispositionCommand(
    val dispositionBy: String,
    val dispositionByRole: String = "AML_REVIEWER",
    val approvedBy: String,
    val approvedByRole: String = "COMPLIANCE_MANAGER",
    val reason: String
)

data class AmlModelCardDto(
    val modelVersionId: String,
    val modelName: String,
    val versionLabel: String,
    val status: String,
    val features: List<String>,
    val scoreDistribution: Map<String, Int>,
    val driftCheck: Map<String, Any?>,
    val explainability: Map<String, Any?>,
    val trainingDataBoundary: String,
    val createdAt: OffsetDateTime,
    val syntheticOnly: Boolean = true
)
