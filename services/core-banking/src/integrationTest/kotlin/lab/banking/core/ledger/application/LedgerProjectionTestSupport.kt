package lab.banking.core.ledger.application

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

fun truncateCoreTables(jdbc: NamedParameterJdbcTemplate) {
    jdbc.jdbcTemplate.execute(
        """
        TRUNCATE TABLE
          ledger_projection_rebuild_items,
          ledger_projection_rebuild_runs,
          ledger_projection_rebuild_requests,
          ledger_projection_drift_items,
          ledger_projection_drift_runs,
          aml_cases,
          fds_cases,
          reconciliation_items,
          inbox_events,
          outbox_events,
          workflow_events,
          workflow_instances,
          masking_access_logs,
          screen_access_logs,
          operator_approvals,
          audit_events,
          daily_closings,
          idempotency_keys,
          ledger_postings,
          ledger_transactions,
          account_balance_projections,
          limit_usage_counters,
          account_holds,
          account_limits,
          accounts,
          customer_kyc_profiles,
          customers
        RESTART IDENTITY CASCADE
        """.trimIndent()
    )
}
