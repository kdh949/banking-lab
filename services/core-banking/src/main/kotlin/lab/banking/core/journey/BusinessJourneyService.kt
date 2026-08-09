package lab.banking.core.journey

import io.micrometer.tracing.Tracer
import lab.banking.core.audit.AuditEventAppender
import lab.banking.core.security.BankingLabAuthContext
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class BusinessJourneyService(
    private val repository: BusinessJourneyRepository,
    private val auditEvents: AuditEventAppender,
    private val tracerProvider: ObjectProvider<Tracer>
) {
    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun createHeldTransfer(command: HeldTransferJourneyCommand): String {
        val journeyId = repository.insertHeldTransfer(command)
        listOf(
            "CUSTOMER_TRANSFER_RESULT" to command.transferResultId,
            "TRANSFER_REFERENCE" to command.transferReferenceId,
            "FDS_CASE" to command.fdsCaseId
        ).forEach { (type, id) -> repository.addReference(journeyId, type, id) }
        repository.appendEvent(
            journeyId = journeyId,
            eventType = "TRANSFER_HELD",
            status = "HELD",
            sourceReferenceType = "FDS_CASE",
            sourceReferenceId = command.fdsCaseId,
            actorId = command.actorId,
            actorRole = "CUSTOMER",
            reason = command.reason,
            traceId = currentTraceId(),
            requestId = null,
            payload = mapOf("ledgerTransactionCount" to 0)
        )
        return journeyId
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun correlate(
        journeyId: String,
        referenceType: String,
        referenceId: String,
        eventType: String,
        status: String,
        actorId: String?,
        actorRole: String?,
        reason: String?,
        requestId: String? = null,
        payload: Map<String, Any?> = emptyMap()
    ) {
        val journey = repository.find(journeyId)
        repository.addReference(journeyId, referenceType, referenceId)
        if (journey.status != status) {
            repository.updateStatus(journeyId, status)
        }
        repository.appendEvent(
            journeyId = journeyId,
            eventType = eventType,
            status = status,
            sourceReferenceType = referenceType,
            sourceReferenceId = referenceId,
            actorId = actorId,
            actorRole = actorRole,
            reason = reason,
            traceId = currentTraceId(),
            requestId = requestId,
            payload = payload
        )
    }

    fun journeyIdForReference(referenceType: String, referenceId: String): String? =
        repository.journeyIdForReference(referenceType, referenceId)

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun customerJourney(journeyId: String): CustomerJourneyDto {
        val journey = repository.find(journeyId)
        BankingLabAuthContext.requireCustomerOwnership(journey.customerId)
        val principal = BankingLabAuthContext.get()
        auditEvents.append(
            eventType = "CUSTOMER_JOURNEY_VIEWED",
            actorType = "CUSTOMER",
            actorId = principal?.customerId ?: journey.customerId,
            actorRole = "CUSTOMER",
            screenId = "CWB-202",
            businessReferenceId = journeyId,
            customerId = journey.customerId,
            reason = "Customer-owned journey status inquiry",
            payload = mapOf("journeyId" to journeyId, "status" to journey.status, "syntheticOnly" to true)
        )
        val safeReferenceTypes = setOf("CUSTOMER_TRANSFER_RESULT", "TRANSFER_REFERENCE", "LEDGER_TRANSACTION")
        return CustomerJourneyDto(
            journeyId = journey.journeyId,
            status = journey.status,
            statusMessage = customerStatusMessage(journey.status),
            references = journey.references.filter { it.referenceType in safeReferenceTypes },
            events = journey.events.map { event ->
                val exposesReference = event.sourceReferenceType in safeReferenceTypes
                event.copy(
                    sourceReferenceType = event.sourceReferenceType.takeIf { exposesReference },
                    sourceReferenceId = event.sourceReferenceId.takeIf { exposesReference },
                    actorRole = null,
                    reason = null,
                    requestId = null
                )
            },
            createdAt = journey.createdAt,
            updatedAt = journey.updatedAt
        )
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun staffJourney(journeyId: String, reason: String?): StaffJourneyResponse {
        if (reason.isNullOrBlank()) {
            throw WorkflowErrors.reasonRequired("staff journey view requires a business reason")
        }
        val journey = repository.find(journeyId)
        val principal = BankingLabAuthContext.get()
        val auditEventId = auditEvents.append(
            eventType = "STAFF_JOURNEY_VIEWED",
            actorType = "STAFF",
            actorId = principal?.subject ?: "staff01",
            actorRole = principal?.roles?.sorted()?.joinToString(",") ?: "BRANCH_STAFF",
            screenId = "WRK-003",
            businessReferenceId = journeyId,
            customerId = journey.customerId,
            reason = reason,
            payload = mapOf("journeyId" to journeyId, "status" to journey.status, "syntheticOnly" to true)
        )
        return StaffJourneyResponse(auditEventId = auditEventId, item = journey)
    }

    fun requireCustomer(journeyId: String, customerId: String) {
        if (repository.find(journeyId).customerId != customerId) {
            throw WorkflowErrors.authorizationViolation("journey does not belong to the interaction customer")
        }
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun correlateKeepingStatus(
        journeyId: String,
        referenceType: String,
        referenceId: String,
        eventType: String,
        actorId: String?,
        actorRole: String?,
        reason: String?,
        requestId: String? = null,
        payload: Map<String, Any?> = emptyMap()
    ) {
        val status = repository.find(journeyId).status
        correlate(
            journeyId = journeyId,
            referenceType = referenceType,
            referenceId = referenceId,
            eventType = eventType,
            status = status,
            actorId = actorId,
            actorRole = actorRole,
            reason = reason,
            requestId = requestId,
            payload = payload
        )
    }

    private fun currentTraceId(): String? =
        tracerProvider.ifAvailable?.currentSpan()?.context()?.traceId()?.takeIf { it.isNotBlank() }

    private fun customerStatusMessage(status: String): String = when (status) {
        "HELD", "INVESTIGATING", "PENDING_APPROVAL" -> "Security review in progress"
        "POSTED" -> "Transfer completed"
        "BLOCKED" -> "Transfer was not completed"
        else -> "Transfer review updated"
    }
}
