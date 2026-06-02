package lab.banking.core.complaint

import lab.banking.core.approval.ApprovalStore
import lab.banking.core.approval.ApproveApprovalCommand
import lab.banking.core.common.BankingLabDomainException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ComplaintWorkflowParityTest {
    @Test
    fun `complaint answer is sent only after maker-checker approval`() {
        val approvals = ApprovalStore()
        val workflow = ComplaintWorkflow(approvals)
        val created = workflow.create(
            customerId = "SYN-CUS-001",
            category = "ACCOUNT_ACCESS",
            description = "Cannot see account history"
        )
        val classified = workflow.classify(created, "complaint01", "ACCOUNT_ACCESS", "Classified by complaint handler")
        val assigned = workflow.assign(classified, "complaint01", "complaint01")
        val review = workflow.startReview(assigned, "complaint01", "Review started")
        val draft = workflow.draftAnswer(
            review,
            actorId = "complaint01",
            requestedByRole = "COMPLAINT_HANDLER",
            reason = "Prepare customer complaint answer",
            body = "Synthetic answer explains the account history path."
        )

        val beforeApprovalView = draft.item.customerView()
        val selfApproval = assertThrows(BankingLabDomainException::class.java) {
            approvals.approve(
                draft.approval.approvalId,
                ApproveApprovalCommand(approvedBy = "complaint01", approvedByRole = "COMPLAINT_HANDLER")
            )
        }
        val managerApproval = approvals.approve(
            draft.approval.approvalId,
            ApproveApprovalCommand(approvedBy = "manager01", approvedByRole = "BRANCH_MANAGER")
        )
        val answered = workflow.applyApprovedAnswer(draft.item, managerApproval)

        assertEquals(ComplaintStatus.CLASSIFIED, classified.status)
        assertEquals(ComplaintStatus.ASSIGNED, assigned.status)
        assertEquals(ComplaintStatus.IN_REVIEW, review.status)
        assertEquals(ComplaintStatus.WAITING_APPROVAL, draft.item.status)
        assertNull(beforeApprovalView.answer)
        assertFalse(beforeApprovalView.timeline.any { it.javaClass.declaredFields.any { field -> field.name == "actorId" } })
        assertEquals("MAKER_CHECKER_SELF_APPROVAL_REJECTED", selfApproval.code)
        assertEquals("MAKER_CHECKER_SEPARATION_OF_DUTIES", selfApproval.policy)
        assertEquals(ComplaintStatus.ANSWERED, answered.status)
        assertEquals("Synthetic answer explains the account history path.", answered.answer?.body)
        assertTrue(approvals.auditEvents().any { it.eventType == "COMMAND_REQUESTED" && it.payload["businessType"] == "COMPLAINT_ANSWER_SEND" })
        assertTrue(approvals.auditEvents().any { it.eventType == "COMMAND_APPROVED" })
    }

    @Test
    fun `customer can confirm an answered complaint and close the case`() {
        val approvals = ApprovalStore()
        val workflow = ComplaintWorkflow(approvals)
        val created = workflow.create("SYN-CUS-001", "FEE_INQUIRY", "Fee explanation requested")
        val review = workflow.startReview(
            workflow.assign(
                workflow.classify(created, "complaint01", "FEE_INQUIRY"),
                "complaint01",
                "complaint01"
            ),
            "complaint01"
        )
        val draft = workflow.draftAnswer(
            review,
            actorId = "complaint01",
            reason = "Prepare fee inquiry answer",
            body = "Synthetic fee answer"
        )
        val approval = approvals.approve(
            draft.approval.approvalId,
            ApproveApprovalCommand(approvedBy = "manager01", approvedByRole = "BRANCH_MANAGER")
        )
        val answered = workflow.applyApprovedAnswer(draft.item, approval)
        val confirmed = workflow.customerConfirm(answered, "SYN-CUS-001", "Customer accepted answer")

        assertEquals(ComplaintStatus.CLOSED, confirmed.status)
        assertTrue(confirmed.customerConfirmedAt != null)
        assertTrue(confirmed.timeline.any { it.type == "CLOSED" })
    }
}
