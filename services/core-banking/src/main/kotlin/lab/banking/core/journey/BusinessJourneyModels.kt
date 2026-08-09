package lab.banking.core.journey

import java.time.OffsetDateTime

data class JourneyReferenceDto(
    val referenceType: String,
    val referenceId: String,
    val createdAt: OffsetDateTime
)

data class JourneyEventDto(
    val eventId: String,
    val sequence: Long,
    val eventType: String,
    val status: String,
    val sourceReferenceType: String?,
    val sourceReferenceId: String?,
    val actorRole: String?,
    val reason: String?,
    val traceId: String?,
    val requestId: String?,
    val createdAt: OffsetDateTime
)

data class BusinessJourneyDto(
    val journeyId: String,
    val journeyType: String,
    val customerId: String,
    val status: String,
    val primaryReferenceType: String,
    val primaryReferenceId: String,
    val references: List<JourneyReferenceDto>,
    val events: List<JourneyEventDto>,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val syntheticOnly: Boolean = true
)

data class CustomerJourneyDto(
    val journeyId: String,
    val status: String,
    val statusMessage: String,
    val references: List<JourneyReferenceDto>,
    val events: List<JourneyEventDto>,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

data class StaffJourneyResponse(
    val auditEventId: String,
    val item: BusinessJourneyDto
)

data class HeldTransferJourneyCommand(
    val customerId: String,
    val transferResultId: String,
    val transferReferenceId: String,
    val fdsCaseId: String,
    val actorId: String,
    val reason: String?
)
