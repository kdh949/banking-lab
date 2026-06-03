package lab.banking.core.approval

import lab.banking.core.common.BankingLabDomainException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MakerCheckerParityTest {
    @Test
    fun `high-risk operation requires maker-checker approval data`() {
        val approvals = ApprovalStore()

        val error = assertThrows(BankingLabDomainException::class.java) {
            approvals.submit(
                SubmitApprovalCommand(
                    businessType = ApprovalBusinessTypes.ACCOUNT_HOLD,
                    businessReferenceId = "ACC-SYN-001-001",
                    requestedBy = "branch01",
                    requestReason = null
                )
            )
        }

        assertEquals("POLICY_REASON_REQUIRED", error.code)
        assertEquals("REASON_REQUIRED", error.policy)
    }

    @Test
    fun `maker cannot approve own high-risk request`() {
        val approvals = ApprovalStore()
        val approval = approvals.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.ACCOUNT_HOLD,
                businessReferenceId = "ACC-SYN-001-001",
                requestedBy = "branch01",
                requestReason = "Synthetic hold verification",
                beforeSnapshot = mapOf("status" to "ACTIVE"),
                afterSnapshot = mapOf("status" to "HOLD_REQUESTED"),
                screenId = "ACC-103"
            )
        )

        val selfApproval = assertThrows(BankingLabDomainException::class.java) {
            approvals.approve(
                approval.approvalId,
                ApproveApprovalCommand(approvedBy = "branch01", approvedByRole = "BRANCH_STAFF")
            )
        }
        assertEquals("MAKER_CHECKER_SELF_APPROVAL_REJECTED", selfApproval.code)
        assertEquals("MAKER_CHECKER_SEPARATION_OF_DUTIES", selfApproval.policy)
        assertEquals(ApprovalStatus.PENDING, approvals.approval(approval.approvalId).status)
        assertEquals(1, approvals.auditEvents().size)
        assertEquals("COMMAND_REQUESTED", approvals.auditEvents().single().eventType)

        val approved = approvals.approve(
            approval.approvalId,
            ApproveApprovalCommand(approvedBy = "manager01", approvedByRole = "BRANCH_MANAGER")
        )

        assertEquals(ApprovalStatus.APPROVED, approved.status)
        assertEquals(2, approvals.auditEvents().size)
        assertTrue(approvals.auditEvents().any { it.eventType == "COMMAND_REQUESTED" })
        assertTrue(approvals.auditEvents().any { it.eventType == "COMMAND_APPROVED" })
    }
}
