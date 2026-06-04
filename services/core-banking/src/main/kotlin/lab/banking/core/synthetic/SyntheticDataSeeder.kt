package lab.banking.core.synthetic

import java.security.MessageDigest
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "banking-lab.synthetic-seed", name = ["enabled"], havingValue = "true")
class SyntheticDataSeeder(
    private val jdbc: NamedParameterJdbcTemplate
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        seedCustomers()
        seedKycProfiles()
        seedAccounts()
        seedAccountLimits()
        seedBalanceProjections()
        seedWorkflowCases()
        seedAuditEvent()
    }

    private fun seedCustomers() {
        jdbc.update(
            """
            INSERT INTO customers (
              customer_id, customer_name, customer_phone, customer_address, customer_grade, risk_grade
            )
            VALUES
              ('SYN-CUS-001', 'Lab Customer Alpha', '010-0000-1001', 'Seoul Synthetic District', 'STANDARD', 'LOW'),
              ('SYN-CUS-002', 'Lab Customer Beta', '010-0000-1002', 'Busan Synthetic District', 'STANDARD', 'LOW'),
              ('SYN-CUS-CMD-001', 'Lab Customer Command', '010-0000-1301', 'Incheon Synthetic District', 'STANDARD', 'LOW'),
              ('SYN-CUS-HOLD-001', 'Lab Customer Hold', '010-0000-1701', 'Daejeon Synthetic District', 'STANDARD', 'LOW'),
              ('SYN-CUS-LIMIT-001', 'Lab Customer Limit', '010-0000-1801', 'Gwangju Synthetic District', 'STANDARD', 'LOW'),
              ('SYN-CUS-KYC-001', 'Lab Customer KYC', '010-0000-1901', 'Seoul Synthetic KYC District', 'STANDARD', 'MEDIUM'),
              ('SYN-CUS-FEE-001', 'Lab Customer Fee', '010-0000-2001', 'Seoul Synthetic Fee District', 'STANDARD', 'LOW')
            ON CONFLICT (customer_id) DO UPDATE
            SET customer_name = EXCLUDED.customer_name,
                customer_phone = EXCLUDED.customer_phone,
                customer_address = EXCLUDED.customer_address,
                customer_grade = EXCLUDED.customer_grade,
                risk_grade = EXCLUDED.risk_grade
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

    private fun seedKycProfiles() {
        jdbc.update(
            """
            INSERT INTO customer_kyc_profiles (
              customer_id, kyc_status, source_of_funds_code, transaction_purpose_code, simulated_provider_reference
            )
            VALUES
              ('SYN-CUS-001', 'VERIFIED', 'SALARY', 'DAILY_BANKING', 'SIM-KYC-001'),
              ('SYN-CUS-002', 'VERIFIED', 'BUSINESS', 'DAILY_BANKING', 'SIM-KYC-002'),
              ('SYN-CUS-CMD-001', 'VERIFIED', 'SALARY', 'DAILY_BANKING', 'SIM-KYC-CMD-001'),
              ('SYN-CUS-HOLD-001', 'VERIFIED', 'SALARY', 'DAILY_BANKING', 'SIM-KYC-HOLD-001'),
              ('SYN-CUS-LIMIT-001', 'VERIFIED', 'SALARY', 'DAILY_BANKING', 'SIM-KYC-LIMIT-001'),
              ('SYN-CUS-KYC-001', 'VERIFIED', 'SALARY', 'DAILY_BANKING', 'SIM-KYC-REVIEW-001'),
              ('SYN-CUS-FEE-001', 'VERIFIED', 'SALARY', 'DAILY_BANKING', 'SIM-KYC-FEE-001')
            ON CONFLICT (customer_id) DO UPDATE
            SET kyc_status = EXCLUDED.kyc_status,
                source_of_funds_code = EXCLUDED.source_of_funds_code,
                transaction_purpose_code = EXCLUDED.transaction_purpose_code,
                simulated_provider_reference = EXCLUDED.simulated_provider_reference,
                updated_at = now()
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

    private fun seedAccounts() {
        jdbc.update(
            """
            INSERT INTO accounts (account_id, customer_id, account_no, currency, status)
            VALUES
              ('ACC-SYN-001-001', 'SYN-CUS-001', 'LAB-001-000001', 'KRW', 'ACTIVE'),
              ('ACC-SYN-002-001', 'SYN-CUS-002', 'LAB-002-000001', 'KRW', 'ACTIVE'),
              ('ACC-SYN-CMD-FROM', 'SYN-CUS-002', 'LAB-002-000901', 'KRW', 'ACTIVE'),
              ('ACC-SYN-CMD-TO', 'SYN-CUS-001', 'LAB-001-000902', 'KRW', 'ACTIVE'),
              ('ACC-SYN-HOLD-001', 'SYN-CUS-HOLD-001', 'LAB-017-000001', 'KRW', 'ACTIVE'),
              ('ACC-SYN-LIMIT-001', 'SYN-CUS-LIMIT-001', 'LAB-018-000001', 'KRW', 'ACTIVE'),
              ('ACC-SYN-FEE-001', 'SYN-CUS-FEE-001', 'LAB-020-000001', 'KRW', 'ACTIVE')
            ON CONFLICT (account_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

    private fun seedAccountLimits() {
        jdbc.update(
            """
            INSERT INTO account_limits (account_id, daily_transfer_limit_minor, single_transfer_limit_minor)
            VALUES
              ('ACC-SYN-001-001', 100000000, 50000000),
              ('ACC-SYN-002-001', 200000000, 100000000),
              ('ACC-SYN-CMD-FROM', 100000000, 50000000),
              ('ACC-SYN-CMD-TO', 100000000, 50000000),
              ('ACC-SYN-HOLD-001', 100000000, 50000000),
              ('ACC-SYN-LIMIT-001', 100000000, 50000000),
              ('ACC-SYN-FEE-001', 100000000, 50000000)
            ON CONFLICT (account_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

    private fun seedBalanceProjections() {
        jdbc.update(
            """
            INSERT INTO account_balance_projections (
              account_id, currency, ledger_balance_minor, available_balance_minor, hold_amount_minor
            )
            VALUES
              ('ACC-SYN-001-001', 'KRW', 100000000, 100000000, 0),
              ('ACC-SYN-002-001', 'KRW', 200000000, 200000000, 0),
              ('ACC-SYN-CMD-FROM', 'KRW', 50000000, 50000000, 0),
              ('ACC-SYN-CMD-TO', 'KRW', 0, 0, 0),
              ('ACC-SYN-HOLD-001', 'KRW', 1000000, 1000000, 0),
              ('ACC-SYN-LIMIT-001', 'KRW', 3000000, 3000000, 0),
              ('ACC-SYN-FEE-001', 'KRW', 2500000, 2500000, 0)
            ON CONFLICT (account_id, currency) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

    private fun seedWorkflowCases() {
        jdbc.update(
            """
            INSERT INTO complaint_cases (
              complaint_case_id, customer_id, category, description, status,
              sla_due_at, classification, owner_id
            )
            VALUES (
              'CMP-SYN-001', 'SYN-CUS-001', 'TRANSFER_DELAY',
              'Synthetic complaint for API-backed channel smoke',
              'IN_REVIEW', now() + INTERVAL '72 hours', 'SERVICE', 'complaint01'
            )
            ON CONFLICT (complaint_case_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO complaint_cases (
              complaint_case_id, customer_id, category, description, status,
              sla_due_at, classification, owner_id
            )
            VALUES (
              'CMP-SYN-CMD-001', 'SYN-CUS-001', 'ACCOUNT_ACCESS',
              'Synthetic complaint for browser maker-checker command smoke',
              'IN_REVIEW', now() + INTERVAL '72 hours', 'SERVICE', 'complaint01'
            )
            ON CONFLICT (complaint_case_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO complaint_cases (
              complaint_case_id, customer_id, category, description, status,
              sla_due_at, classification, owner_id, answer_json
            )
            VALUES (
              'CMP-SYN-FAIL-001', 'SYN-CUS-001', 'ACCOUNT_ACCESS',
              'Synthetic answered complaint for browser workflow failure smoke',
              'ANSWERED', now() + INTERVAL '72 hours', 'SERVICE', 'complaint01',
              '{"body":"Synthetic answer already sent.","answeredBy":"manager01","answeredAt":"2026-06-03T00:00:00Z"}'::jsonb
            )
            ON CONFLICT (complaint_case_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO complaint_cases (
              complaint_case_id, customer_id, category, description, status,
              sla_due_at, classification, owner_id, answer_json
            )
            VALUES (
              'CMP-SYN-CONFIRM-001', 'SYN-CUS-001', 'FEE_INQUIRY',
              'Synthetic answered complaint for browser confirmation smoke',
              'ANSWERED', now() + INTERVAL '72 hours', 'FEE_INQUIRY', 'complaint01',
              '{"body":"Synthetic fee answer ready for customer confirmation.","answeredBy":"manager01","answeredAt":"2026-06-03T00:00:00Z"}'::jsonb
            )
            ON CONFLICT (complaint_case_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO complaint_case_timeline (
              complaint_timeline_id, complaint_case_id, event_type, from_status,
              to_status, actor_id, note, payload_json
            )
            VALUES (
              'CMT-SYN-001', 'CMP-SYN-001', 'ASSIGNED', 'RECEIVED',
              'IN_REVIEW', 'complaint01', 'Synthetic complaint assigned', '{}'::jsonb
            )
            ON CONFLICT (complaint_timeline_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO complaint_case_timeline (
              complaint_timeline_id, complaint_case_id, event_type, from_status,
              to_status, actor_id, note, payload_json
            )
            VALUES (
              'CMT-SYN-CONFIRM-001', 'CMP-SYN-CONFIRM-001', 'ANSWERED', 'WAITING_APPROVAL',
              'ANSWERED', 'manager01', 'Synthetic answered complaint for confirmation smoke', '{}'::jsonb
            )
            ON CONFLICT (complaint_timeline_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO complaint_case_timeline (
              complaint_timeline_id, complaint_case_id, event_type, from_status,
              to_status, actor_id, note, payload_json
            )
            VALUES (
              'CMT-SYN-FAIL-001', 'CMP-SYN-FAIL-001', 'ANSWERED', 'WAITING_APPROVAL',
              'ANSWERED', 'manager01', 'Synthetic answered complaint for workflow failure smoke', '{}'::jsonb
            )
            ON CONFLICT (complaint_timeline_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO complaint_case_timeline (
              complaint_timeline_id, complaint_case_id, event_type, from_status,
              to_status, actor_id, note, payload_json
            )
            VALUES (
              'CMT-SYN-CMD-001', 'CMP-SYN-CMD-001', 'ASSIGNED', 'RECEIVED',
              'IN_REVIEW', 'complaint01', 'Synthetic complaint command case assigned', '{}'::jsonb
            )
            ON CONFLICT (complaint_timeline_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO fds_cases (
              fds_case_id, transfer_reference_id, customer_id, status, risk_score,
              alerts_json, owner_id, from_account_id, to_account_id, amount_minor,
              transfer_idempotency_key, requested_by, business_date, transfer_status
            )
            VALUES (
              'FDS-SYN-001', 'TR-SYN-FDS-001', 'SYN-CUS-001', 'INVESTIGATING', 820,
              '[{"ruleId":"FDS-RULE-HIGH-AMOUNT","message":"Synthetic held transfer"}]'::jsonb,
              'fds01', 'ACC-SYN-001-001', 'ACC-SYN-002-001', 15000000,
              'IDEMP-FDS-SYN-001', 'customer01', CURRENT_DATE, 'HELD'
            )
            ON CONFLICT (fds_case_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO fds_cases (
              fds_case_id, transfer_reference_id, customer_id, status, risk_score,
              alerts_json, owner_id, from_account_id, to_account_id, amount_minor,
              transfer_idempotency_key, requested_by, business_date, transfer_status
            )
            VALUES (
              'FDS-SYN-OIDC-REL-001', 'TR-SYN-FDS-OIDC-REL-001', 'SYN-CUS-002', 'INVESTIGATING', 920,
              '[{"ruleId":"FDS-RULE-OIDC-RELEASE-SMOKE","message":"Synthetic held transfer for Keycloak release command"}]'::jsonb,
              'risk01', 'ACC-SYN-CMD-FROM', 'ACC-SYN-CMD-TO', 31000,
              'IDEMP-FDS-SYN-OIDC-REL-001', 'customer02', CURRENT_DATE, 'HELD'
            )
            ON CONFLICT (fds_case_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO fds_cases (
              fds_case_id, transfer_reference_id, customer_id, status, risk_score,
              alerts_json, owner_id, from_account_id, to_account_id, amount_minor,
              transfer_idempotency_key, requested_by, business_date, transfer_status
            )
            VALUES (
              'FDS-SYN-OIDC-BLOCK-001', 'TR-SYN-FDS-OIDC-BLOCK-001', 'SYN-CUS-002', 'INVESTIGATING', 925,
              '[{"ruleId":"FDS-RULE-OIDC-BLOCK-SMOKE","message":"Synthetic held transfer for Keycloak block command"}]'::jsonb,
              'risk01', 'ACC-SYN-CMD-FROM', 'ACC-SYN-CMD-TO', 41000,
              'IDEMP-FDS-SYN-OIDC-BLOCK-001', 'customer02', CURRENT_DATE, 'HELD'
            )
            ON CONFLICT (fds_case_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO fds_cases (
              fds_case_id, transfer_reference_id, customer_id, status, risk_score,
              alerts_json, owner_id, from_account_id, to_account_id, amount_minor,
              transfer_idempotency_key, requested_by, business_date, transfer_status
            )
            VALUES (
              'FDS-SYN-CMD-001', 'TR-SYN-FDS-CMD-001', 'SYN-CUS-002', 'INVESTIGATING', 910,
              '[{"ruleId":"FDS-RULE-COMMAND-SMOKE","message":"Synthetic held transfer for browser release command"}]'::jsonb,
              'fds01', 'ACC-SYN-CMD-FROM', 'ACC-SYN-CMD-TO', 30000,
              'IDEMP-FDS-SYN-CMD-001', 'customer02', CURRENT_DATE, 'HELD'
            )
            ON CONFLICT (fds_case_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO fds_cases (
              fds_case_id, transfer_reference_id, customer_id, status, risk_score,
              alerts_json, owner_id, from_account_id, to_account_id, amount_minor,
              transfer_idempotency_key, requested_by, business_date, transfer_status
            )
            VALUES (
              'FDS-SYN-BLOCK-CMD-001', 'TR-SYN-FDS-BLOCK-CMD-001', 'SYN-CUS-002', 'INVESTIGATING', 915,
              '[{"ruleId":"FDS-RULE-BLOCK-COMMAND-SMOKE","message":"Synthetic held transfer for browser block command"}]'::jsonb,
              'fds01', 'ACC-SYN-CMD-FROM', 'ACC-SYN-CMD-TO', 40000,
              'IDEMP-FDS-SYN-BLOCK-CMD-001', 'customer02', CURRENT_DATE, 'HELD'
            )
            ON CONFLICT (fds_case_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO fds_cases (
              fds_case_id, transfer_reference_id, customer_id, status, risk_score,
              alerts_json, owner_id, from_account_id, to_account_id, amount_minor,
              transfer_idempotency_key, requested_by, business_date, transfer_status
            )
            VALUES (
              'FDS-SYN-FAIL-001', 'TR-SYN-FDS-FAIL-001', 'SYN-CUS-002', 'RELEASED', 905,
              '[{"ruleId":"FDS-RULE-FAILURE-SMOKE","message":"Synthetic released transfer for browser failure-state smoke"}]'::jsonb,
              'fds01', 'ACC-SYN-CMD-FROM', 'ACC-SYN-CMD-TO', 50000,
              'IDEMP-FDS-SYN-FAIL-001', 'customer02', CURRENT_DATE, 'POSTED'
            )
            ON CONFLICT (fds_case_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO fds_case_timeline (
              fds_timeline_id, fds_case_id, event_type, from_status,
              to_status, actor_id, note, payload_json
            )
            VALUES (
              'FDT-SYN-001', 'FDS-SYN-001', 'INVESTIGATING', 'HELD',
              'INVESTIGATING', 'fds01', 'Synthetic FDS investigation opened', '{}'::jsonb
            )
            ON CONFLICT (fds_timeline_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO fds_case_timeline (
              fds_timeline_id, fds_case_id, event_type, from_status,
              to_status, actor_id, note, payload_json
            )
            VALUES (
              'FDT-SYN-FAIL-001', 'FDS-SYN-FAIL-001', 'RELEASED', 'RELEASE_REQUESTED',
              'RELEASED', 'manager01', 'Synthetic released FDS case for workflow failure smoke', '{}'::jsonb
            )
            ON CONFLICT (fds_timeline_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO fds_case_timeline (
              fds_timeline_id, fds_case_id, event_type, from_status,
              to_status, actor_id, note, payload_json
            )
            VALUES (
              'FDT-SYN-CMD-001', 'FDS-SYN-CMD-001', 'INVESTIGATING', 'HELD',
              'INVESTIGATING', 'fds01', 'Synthetic FDS command investigation opened', '{}'::jsonb
            )
            ON CONFLICT (fds_timeline_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO fds_case_timeline (
              fds_timeline_id, fds_case_id, event_type, from_status,
              to_status, actor_id, note, payload_json
            )
            VALUES (
              'FDT-SYN-BLOCK-CMD-001', 'FDS-SYN-BLOCK-CMD-001', 'INVESTIGATING', 'HELD',
              'INVESTIGATING', 'fds01', 'Synthetic FDS block command investigation opened', '{}'::jsonb
            )
            ON CONFLICT (fds_timeline_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO fds_case_timeline (
              fds_timeline_id, fds_case_id, event_type, from_status,
              to_status, actor_id, note, payload_json
            )
            VALUES (
              'FDT-SYN-OIDC-REL-001', 'FDS-SYN-OIDC-REL-001', 'INVESTIGATING', 'HELD',
              'INVESTIGATING', 'risk01', 'Synthetic FDS Keycloak release investigation opened', '{}'::jsonb
            )
            ON CONFLICT (fds_timeline_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO fds_case_timeline (
              fds_timeline_id, fds_case_id, event_type, from_status,
              to_status, actor_id, note, payload_json
            )
            VALUES (
              'FDT-SYN-OIDC-BLOCK-001', 'FDS-SYN-OIDC-BLOCK-001', 'INVESTIGATING', 'HELD',
              'INVESTIGATING', 'risk01', 'Synthetic FDS Keycloak block investigation opened', '{}'::jsonb
            )
            ON CONFLICT (fds_timeline_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO aml_cases (
              aml_case_id, customer_id, status, risk_score, alerts_json,
              owner_id, str_simulation_json
            )
            VALUES (
              'AML-SYN-001', 'SYN-CUS-001', 'INVESTIGATING', 850,
              '[{"ruleId":"AML-RULE-HIGH-RISK-CUSTOMER","message":"Synthetic high-risk customer"}]'::jsonb,
              'aml01', '{"reported":false,"disposition":null,"reportReferenceId":null}'::jsonb
            )
            ON CONFLICT (aml_case_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO aml_cases (
              aml_case_id, customer_id, status, risk_score, alerts_json,
              owner_id, str_simulation_json
            )
            VALUES (
              'AML-SYN-CMD-001', 'SYN-CUS-002', 'INVESTIGATING', 905,
              '[{"ruleId":"AML-RULE-COMMAND-SMOKE","message":"Synthetic AML case for browser closure command"}]'::jsonb,
              'aml01', '{"reported":false,"disposition":null,"reportReferenceId":null}'::jsonb
            )
            ON CONFLICT (aml_case_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO aml_cases (
              aml_case_id, customer_id, status, risk_score, alerts_json,
              owner_id, str_simulation_json
            )
            VALUES (
              'AML-SYN-OIDC-CLOSE-001', 'SYN-CUS-002', 'INVESTIGATING', 925,
              '[{"ruleId":"AML-RULE-OIDC-CLOSE-SMOKE","message":"Synthetic AML case for Keycloak closure command"}]'::jsonb,
              'risk01', '{"reported":false,"disposition":null,"reportReferenceId":null}'::jsonb
            )
            ON CONFLICT (aml_case_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO aml_cases (
              aml_case_id, customer_id, status, risk_score, alerts_json,
              owner_id, str_simulation_json
            )
            VALUES (
              'AML-SYN-FAIL-001', 'SYN-CUS-002', 'CLOSED', 915,
              '[{"ruleId":"AML-RULE-FAILURE-SMOKE","message":"Synthetic closed AML case for browser failure-state smoke"}]'::jsonb,
              'aml01', '{"reported":true,"disposition":"STR_SIMULATED","reportReferenceId":"STR-SYN-FAIL-001"}'::jsonb
            )
            ON CONFLICT (aml_case_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO aml_case_comments (aml_comment_id, aml_case_id, actor_id, body)
            VALUES (
              'AMC-SYN-001', 'AML-SYN-001', 'aml01',
              'Synthetic AML investigation note for API-backed channel smoke'
            )
            ON CONFLICT (aml_comment_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO aml_case_comments (aml_comment_id, aml_case_id, actor_id, body)
            VALUES (
              'AMC-SYN-CMD-001', 'AML-SYN-CMD-001', 'aml01',
              'Synthetic AML command investigation note for browser closure smoke'
            )
            ON CONFLICT (aml_comment_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO aml_case_comments (aml_comment_id, aml_case_id, actor_id, body)
            VALUES (
              'AMC-SYN-OIDC-CLOSE-001', 'AML-SYN-OIDC-CLOSE-001', 'risk01',
              'Synthetic AML Keycloak command investigation note for browser closure smoke'
            )
            ON CONFLICT (aml_comment_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO aml_case_comments (aml_comment_id, aml_case_id, actor_id, body)
            VALUES (
              'AMC-SYN-FAIL-001', 'AML-SYN-FAIL-001', 'compliance01',
              'Synthetic AML case already closed for browser workflow failure smoke'
            )
            ON CONFLICT (aml_comment_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO reconciliation_items (
              reconciliation_item_id, business_date, source_system,
              internal_reference_id, external_reference_id, amount_minor,
              currency, status, owner_id
            )
            VALUES (
              'REC-SYN-001', CURRENT_DATE, 'OPENBANKING-SIM',
              'TX-SYN-REC-001', 'EXT-SYN-REC-001', 12000,
              'KRW', 'OPEN', 'ops01'
            )
            ON CONFLICT (reconciliation_item_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO reconciliation_items (
              reconciliation_item_id, business_date, source_system,
              internal_reference_id, external_reference_id, amount_minor,
              currency, status, owner_id
            )
            VALUES (
              'REC-SYN-CMD-001', CURRENT_DATE, 'OPENBANKING-SIM',
              'TX-SYN-REC-CMD-001', 'EXT-SYN-REC-CMD-001', 15000,
              'KRW', 'OPEN', 'ops01'
            )
            ON CONFLICT (reconciliation_item_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO reconciliation_items (
              reconciliation_item_id, business_date, source_system,
              internal_reference_id, external_reference_id, amount_minor,
              currency, status, owner_id
            )
            VALUES (
              'REC-SYN-FAIL-001', CURRENT_DATE, 'OPENBANKING-SIM',
              'TX-SYN-REC-FAIL-001', 'EXT-SYN-REC-FAIL-001', 15000,
              'KRW', 'ADJUSTED', 'ops01'
            )
            ON CONFLICT (reconciliation_item_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

    private fun seedAuditEvent() {
        val payloadJson = """{"syntheticOnly":true,"seed":"api-backed-channel"}"""
        jdbc.update(
            """
            INSERT INTO audit_events (
              audit_event_id, event_type, actor_type, actor_id, actor_role,
              screen_id, business_reference_id, reason, payload_hash,
              previous_event_hash, payload_json
            )
            VALUES (
              'AUD-SYN-SEED-001', 'SYNTHETIC_SEED', 'SYSTEM', 'synthetic-seeder', 'SYSTEM',
              'AUD-201', 'api-backed-channel-smoke', 'Synthetic seed for audit console smoke',
              :payloadHash, :previousEventHash, CAST(:payloadJson AS jsonb)
            )
            ON CONFLICT (audit_event_id) DO NOTHING
            """.trimIndent(),
            mapOf(
                "payloadHash" to sha256(payloadJson),
                "previousEventHash" to latestAuditHash(),
                "payloadJson" to payloadJson
            )
        )
    }

    private fun latestAuditHash(): String? =
        jdbc.query(
            """
            SELECT payload_hash
            FROM audit_events
            ORDER BY created_at DESC, audit_event_id DESC
            LIMIT 1
            """.trimIndent(),
            emptyMap<String, Any?>()
        ) { rs, _ -> rs.getString("payload_hash") }.firstOrNull()

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
