package lab.banking.core.testsupport

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

object ParameterSeedSupport {
    fun reseedFdsRuleParameters(jdbc: NamedParameterJdbcTemplate) {
        jdbc.update(
            """
            INSERT INTO fds_rule_parameters (parameter_key, value_type, current_version_id, description)
            VALUES
              ('highAmountMinor', 'NUMBER', 'FPV-SEED-HIGH-AMOUNT', 'Synthetic FDS high amount hold threshold'),
              ('newDeviceHoldHours', 'NUMBER', 'FPV-SEED-NEW-DEVICE-HOURS', 'Synthetic new device hold duration'),
              ('velocityWindowMinutes', 'NUMBER', 'FPV-SEED-VELOCITY-WINDOW', 'Synthetic transfer velocity window')
            ON CONFLICT (parameter_key) DO UPDATE
            SET value_type = EXCLUDED.value_type,
                current_version_id = EXCLUDED.current_version_id,
                description = EXCLUDED.description,
                updated_at = now()
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO fds_rule_parameter_versions (
              parameter_version_id, parameter_key, parameter_value, value_type,
              effective_from, created_by, approved_at, metadata_json
            )
            VALUES
              ('FPV-SEED-HIGH-AMOUNT', 'highAmountMinor', '5000000', 'NUMBER', DATE '2026-01-01', 'SEED', TIMESTAMPTZ '2026-01-01T00:00:00Z', '{"syntheticOnly":true}'::jsonb),
              ('FPV-SEED-NEW-DEVICE-HOURS', 'newDeviceHoldHours', '24', 'NUMBER', DATE '2026-01-01', 'SEED', TIMESTAMPTZ '2026-01-01T00:00:00Z', '{"syntheticOnly":true}'::jsonb),
              ('FPV-SEED-VELOCITY-WINDOW', 'velocityWindowMinutes', '60', 'NUMBER', DATE '2026-01-01', 'SEED', TIMESTAMPTZ '2026-01-01T00:00:00Z', '{"syntheticOnly":true}'::jsonb)
            ON CONFLICT (parameter_version_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }
}
