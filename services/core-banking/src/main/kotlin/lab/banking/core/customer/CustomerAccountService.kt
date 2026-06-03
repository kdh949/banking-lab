package lab.banking.core.customer

import java.sql.ResultSet
import lab.banking.core.security.BankingLabAuthContext
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CustomerAccountService(
    private val jdbc: NamedParameterJdbcTemplate
) {
    @Transactional(readOnly = true)
    fun detail(customerId: String, accountId: String): CustomerAccountDetailDto {
        BankingLabAuthContext.requireCustomerOwnership(customerId)
        return jdbc.queryForObject(
            """
            SELECT a.customer_id, a.account_id, a.account_no, a.status, a.currency,
                   COALESCE(p.ledger_balance_minor, 0) AS ledger_balance_minor,
                   COALESCE(p.available_balance_minor, 0) AS available_balance_minor,
                   COALESCE(p.hold_amount_minor, 0) AS hold_amount_minor
            FROM accounts a
            LEFT JOIN account_balance_projections p
              ON p.account_id = a.account_id
             AND p.currency = a.currency
            WHERE a.customer_id = :customerId
              AND a.account_id = :accountId
            """.trimIndent(),
            mapOf("customerId" to customerId, "accountId" to accountId),
            this::mapDetail
        ) ?: throw WorkflowErrors.notFound("customer account not found: $accountId")
    }

    private fun mapDetail(rs: ResultSet, rowNum: Int): CustomerAccountDetailDto =
        CustomerAccountDetailDto(
            customerId = rs.getString("customer_id"),
            accountId = rs.getString("account_id"),
            maskedAccountNo = maskAccountNo(rs.getString("account_no")),
            status = rs.getString("status"),
            currency = rs.getString("currency"),
            ledgerBalanceMinor = rs.getLong("ledger_balance_minor"),
            availableBalanceMinor = rs.getLong("available_balance_minor"),
            holdAmountMinor = rs.getLong("hold_amount_minor")
        )

    private fun maskAccountNo(accountNo: String?): String {
        val value = accountNo.orEmpty()
        val parts = value.split("-")
        if (parts.size >= 3) {
            return "${parts[0]}-${"*".repeat(parts[1].length)}-${parts.last().takeLast(4)}"
        }
        if (value.length <= 6) {
            return "****"
        }
        return "${value.take(4)}-${"*".repeat(maxOf(3, value.length - 10))}-${value.takeLast(4)}"
    }
}
