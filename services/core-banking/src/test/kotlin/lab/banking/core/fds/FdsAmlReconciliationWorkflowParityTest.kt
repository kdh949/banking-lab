package lab.banking.core.fds

import java.time.LocalDate
import lab.banking.core.aml.AmlCaseStatus
import lab.banking.core.aml.AmlWorkflow
import lab.banking.core.approval.ApprovalStore
import lab.banking.core.approval.ApproveApprovalCommand
import lab.banking.core.common.BankingLabDomainException
import lab.banking.core.ledger.domain.PostingDirection
import lab.banking.core.reconciliation.ReconciliationItemStatus
import lab.banking.core.reconciliation.ReconciliationWorkflow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FdsAmlReconciliationWorkflowParityTest {
    @Test
    fun `high-risk transfer creates FDS case and release posts only after checker approval`() {
        val approvals = ApprovalStore()
        val workflow = FdsWorkflow(approvals)
        val held = workflow.holdTransfer(
            fromAccountId = "ACC-SYN-001-001",
            toAccountId = "ACC-SYN-002-001",
            amountMinor = 5_000_000,
            idempotencyKey = "FDS-RELEASE-FLOW-001",
            requestedBy = "SYN-CUS-001",
            customerId = "SYN-CUS-001",
            newDevice = true
        )
        val assigned = workflow.assign(held.fdsCase, "fds01", "fds01")
        val releaseRequest = workflow.requestRelease(
            assigned,
            actorId = "fds01",
            requestedByRole = "FDS_REVIEWER",
            reason = "Synthetic FDS release after reviewer investigation"
        )

        val selfApproval = assertThrows(BankingLabDomainException::class.java) {
            approvals.approve(
                releaseRequest.approval.approvalId,
                ApproveApprovalCommand(approvedBy = "fds01", approvedByRole = "FDS_REVIEWER")
            )
        }
        val managerApproval = approvals.approve(
            releaseRequest.approval.approvalId,
            ApproveApprovalCommand(approvedBy = "manager01", approvedByRole = "BRANCH_MANAGER")
        )
        val released = workflow.applyApprovedRelease(releaseRequest.item, managerApproval)

        assertEquals(HeldTransferStatus.HELD, held.item.status)
        assertTrue(held.fdsCase.alerts.any { it.ruleId == "FDS-RULE-UNUSUAL-AMOUNT" })
        assertEquals(FdsCaseStatus.INVESTIGATING, assigned.status)
        assertEquals(FdsCaseStatus.RELEASE_REQUESTED, releaseRequest.item.status)
        assertEquals("MAKER_CHECKER_SELF_APPROVAL_REJECTED", selfApproval.code)
        assertEquals(FdsCaseStatus.RELEASED, released.fdsCase.status)
        assertEquals(HeldTransferStatus.POSTED, released.transfer.status)
        assertEquals("FDS-RELEASE-${held.fdsCase.caseId}", released.ledgerCommand.idempotencyKey)
        assertEquals(held.fdsCase.caseId, released.ledgerCommand.businessReferenceId)
        assertEquals("CUSTOMER_WEB", released.ledgerCommand.requestedChannel)
    }

    @Test
    fun `FDS block closes held transfer without ledger posting command`() {
        val approvals = ApprovalStore()
        val workflow = FdsWorkflow(approvals)
        val held = workflow.holdTransfer(
            fromAccountId = "ACC-SYN-001-001",
            toAccountId = "ACC-SYN-002-001",
            amountMinor = 6_000_000,
            idempotencyKey = "FDS-BLOCK-FLOW-001",
            requestedBy = "SYN-CUS-001",
            customerId = "SYN-CUS-001",
            firstTimeBeneficiary = true
        )
        val assigned = workflow.assign(held.fdsCase, "fds01", "fds01")
        val blockRequest = workflow.requestBlock(assigned, actorId = "fds01", reason = "Synthetic suspicious beneficiary block")
        val approval = approvals.approve(
            blockRequest.approval.approvalId,
            ApproveApprovalCommand(approvedBy = "manager01", approvedByRole = "BRANCH_MANAGER")
        )
        val blocked = workflow.applyApprovedBlock(blockRequest.item, approval)

        assertEquals(FdsCaseStatus.BLOCK_REQUESTED, blockRequest.item.status)
        assertEquals(FdsCaseStatus.BLOCKED, blocked.fdsCase.status)
        assertEquals(HeldTransferStatus.BLOCKED, blocked.transfer.status)
    }

    @Test
    fun `AML case is generated for high-risk customer and closes only after approval`() {
        val approvals = ApprovalStore()
        val workflow = AmlWorkflow(approvals)
        val opened = workflow.openHighRiskCustomerCase("SYN-CUS-003", "FDS-AML-HIGH-RISK-001")
        val assigned = workflow.assign(opened, "fds01", "fds01")
        val commented = workflow.addComment(assigned, "fds01", "Synthetic enhanced due diligence reviewed.")
        val closure = workflow.requestClosure(
            commented,
            actorId = "fds01",
            reason = "Synthetic STR simulation disposition reviewed",
            disposition = "STR_SIMULATED",
            reportReferenceId = "STR-SIM-TEST-001"
        )
        val approval = approvals.approve(
            closure.approval.approvalId,
            ApproveApprovalCommand(approvedBy = "manager01", approvedByRole = "BRANCH_MANAGER")
        )
        val closed = workflow.applyApprovedClosure(closure.item, approval)

        assertTrue(opened.alerts.any { it.ruleId == "AML-RULE-HIGH-RISK-CUSTOMER" })
        assertEquals(AmlCaseStatus.INVESTIGATING, assigned.status)
        assertEquals(1, commented.comments.size)
        assertEquals(AmlCaseStatus.CLOSURE_REQUESTED, closure.item.status)
        assertEquals(AmlCaseStatus.CLOSED, closed.status)
        assertEquals(true, closed.strSimulation.reported)
        assertTrue(approvals.auditEvents().any { it.screenId == "AML-201" && it.eventType == "COMMAND_REQUESTED" })
    }

    @Test
    fun `reconciliation adjustment lifecycle emits approved adjustment command on open day`() {
        val approvals = ApprovalStore()
        val workflow = ReconciliationWorkflow(approvals)
        val businessDate = LocalDate.parse("2026-06-02")
        val nextDate = businessDate.plusDays(1)
        val item = workflow.createMismatch(
            businessDate = businessDate,
            owner = "ops01",
            amountMinor = 1_000,
            internalReferenceId = "TX-TRF-RECON-001"
        )
        val request = workflow.requestAdjustment(
            item = item,
            requestedBy = "ops01",
            reason = "Synthetic reconciliation adjustment",
            accountId = "ACC-SYN-001-001",
            direction = PostingDirection.CREDIT,
            amountMinor = 1_000,
            businessDate = nextDate,
            idempotencyKey = "RECON-ADJ-001"
        )
        val approval = approvals.approve(
            request.approval.approvalId,
            ApproveApprovalCommand(approvedBy = "manager01", approvedByRole = "BRANCH_MANAGER")
        )
        val adjusted = workflow.applyApprovedAdjustment(request.item, approval)

        assertEquals(ReconciliationItemStatus.OPEN, item.status)
        assertEquals("ops01", item.owner)
        assertEquals(ReconciliationItemStatus.ADJUSTMENT_REQUESTED, request.item.status)
        assertEquals(ReconciliationItemStatus.ADJUSTED, adjusted.reconciliationItem.status)
        assertEquals(nextDate, adjusted.ledgerCommand.businessDate)
        assertEquals("RECON-ADJ-001", adjusted.ledgerCommand.idempotencyKey)
        assertEquals(item.itemId, adjusted.ledgerCommand.businessReferenceId)
        assertEquals("OPS_RECONCILIATION", adjusted.ledgerCommand.requestedChannel)
    }
}
