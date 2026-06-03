package lab.banking.core.staff

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDate
import java.time.OffsetDateTime
import lab.banking.core.aml.AmlCaseDto
import lab.banking.core.approval.OperatorApproval
import lab.banking.core.complaint.ComplaintCaseDto
import lab.banking.core.fds.FdsCaseDto
import lab.banking.core.ledger.domain.LedgerCommandResult
import lab.banking.core.reconciliation.ReconciliationItemDto

data class StaffAccessItemResponse<T>(
    val auditEventId: String,
    val item: T
)

data class StaffAccessListResponse<T>(
    val auditEventId: String,
    val items: List<T>
)

data class StaffUnmaskResponse(
    val auditEventId: String,
    val expiresInSeconds: Int,
    val item: StaffCustomerDetailDto
)

data class PiiUnmaskCommand(
    val customerId: String,
    val requestedBy: String? = null,
    val actorRole: String? = null,
    val reason: String? = null,
    val screenId: String? = null
)

data class CustomerInfoChangeCommand(
    val requestedBy: String? = null,
    val requestedByRole: String? = null,
    val reason: String? = null,
    val afterSnapshot: Map<String, Any?>? = null
)

data class CustomerInfoChangeResponse(
    val item: OperatorApproval,
    val customer: StaffCustomerDetailDto
)

data class StaffApprovalExecutionResponse(
    val item: OperatorApproval,
    val executed: Boolean,
    val customer: StaffCustomerDetailDto?,
    val complaint: ComplaintCaseDto?,
    val fdsCase: FdsCaseDto?,
    val amlCase: AmlCaseDto?,
    val reconciliationItem: ReconciliationItemDto?,
    val ledgerTransaction: LedgerCommandResult?
)

data class StaffCustomerRecord(
    val customerId: String,
    val name: String,
    val phone: String?,
    val address: String?,
    val customerGrade: String,
    val riskGrade: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class MaskedCustomerDto(
    val customerId: String,
    val maskedName: String,
    val maskedPhone: String?,
    val maskedAddress: String?,
    val customerGrade: String,
    val riskGrade: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class StaffCustomerDetailDto(
    val customerId: String,
    val piiExposure: String,
    val customerGrade: String,
    val riskGrade: String,
    val maskedName: String? = null,
    val maskedPhone: String? = null,
    val maskedAddress: String? = null,
    val name: String? = null,
    val phone: String? = null,
    val address: String? = null
)

data class StaffAccountDto(
    val customerId: String,
    val accountId: String,
    val maskedAccountNo: String,
    val status: String,
    val currency: String,
    val ledgerBalanceMinor: Long,
    val availableBalanceMinor: Long,
    val holdAmountMinor: Long
)

data class StaffTransactionDto(
    val ledgerTransactionId: String,
    val transactionType: String,
    val status: String,
    val businessDate: LocalDate,
    val postedAt: OffsetDateTime?,
    val accountId: String,
    val direction: String,
    val amountMinor: Long,
    val currency: String,
    val requestedChannel: String,
    val reason: String?
)
