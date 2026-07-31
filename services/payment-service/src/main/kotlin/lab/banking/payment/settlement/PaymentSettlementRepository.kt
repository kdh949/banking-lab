package lab.banking.payment.settlement

import java.sql.ResultSet
import java.util.UUID
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class PaymentSettlementRepository(
    private val jdbc: NamedParameterJdbcTemplate
) {
    fun lockKeys(keys: Collection<String>) {
        keys.distinct().sorted().forEach { lockKey ->
            jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))",
                mapOf("lockKey" to lockKey)
            ) { _, _ -> Unit }
        }
    }

    fun findImportByIdempotencyKey(idempotencyKey: String): ExternalSettlementImportRecord? =
        findImportBy(
            "WHERE idempotency_key = :idempotencyKey",
            mapOf("idempotencyKey" to idempotencyKey)
        )

    fun findImportByFileSha256(fileSha256: String): ExternalSettlementImportRecord? =
        findImportBy(
            "WHERE file_sha256 = :fileSha256",
            mapOf("fileSha256" to fileSha256)
        )

    fun findImport(importId: String): ExternalSettlementImportRecord? =
        findImportBy(
            "WHERE external_settlement_import_id = :importId",
            mapOf("importId" to importId)
        )

    fun insertImport(record: ExternalSettlementImportRecord) {
        jdbc.update(
            """
            INSERT INTO payment_external_settlement_imports (
              external_settlement_import_id,
              original_file_name,
              institution_code,
              file_sha256,
              file_byte_size,
              business_date,
              status,
              line_count,
              accepted_line_count,
              rejected_line_count,
              requested_by,
              reason,
              idempotency_key,
              request_hash,
              synthetic_only,
              received_at
            )
            VALUES (
              :importId,
              :originalFileName,
              :institutionCode,
              :fileSha256,
              :fileByteSize,
              :businessDate,
              :status,
              :lineCount,
              :acceptedLineCount,
              :rejectedLineCount,
              :requestedBy,
              :reason,
              :idempotencyKey,
              :requestHash,
              true,
              :receivedAt
            )
            """.trimIndent(),
            mapOf(
                "importId" to record.externalSettlementImportId,
                "originalFileName" to record.originalFileName,
                "institutionCode" to record.institutionCode,
                "fileSha256" to record.fileSha256,
                "fileByteSize" to record.fileByteSize,
                "businessDate" to record.businessDate,
                "status" to record.status.name,
                "lineCount" to record.lineCount,
                "acceptedLineCount" to record.acceptedLineCount,
                "rejectedLineCount" to record.rejectedLineCount,
                "requestedBy" to record.requestedBy,
                "reason" to record.reason,
                "idempotencyKey" to record.idempotencyKey,
                "requestHash" to record.requestHash,
                "receivedAt" to record.receivedAt
            )
        )
    }

    fun insertLine(record: ExternalSettlementLineRecord) {
        jdbc.update(
            """
            INSERT INTO payment_external_settlement_lines (
              external_settlement_line_id,
              external_settlement_import_id,
              line_number,
              external_reference,
              payment_instruction_id,
              biller_id,
              amount_minor,
              currency,
              business_date,
              value_date,
              status,
              raw_line,
              synthetic_only,
              created_at
            )
            VALUES (
              :lineId,
              :importId,
              :lineNumber,
              :externalReference,
              :paymentInstructionId,
              :billerId,
              :amountMinor,
              :currency,
              :businessDate,
              :valueDate,
              :status,
              :rawLine,
              true,
              :createdAt
            )
            """.trimIndent(),
            mapOf(
                "lineId" to record.externalSettlementLineId,
                "importId" to record.externalSettlementImportId,
                "lineNumber" to record.lineNumber,
                "externalReference" to record.externalReference,
                "paymentInstructionId" to record.paymentInstructionId,
                "billerId" to record.billerId,
                "amountMinor" to record.amountMinor,
                "currency" to record.currency,
                "businessDate" to record.businessDate,
                "valueDate" to record.valueDate,
                "status" to record.status.name,
                "rawLine" to record.rawLine,
                "createdAt" to record.createdAt
            )
        )
    }

    fun linesForImport(importId: String): List<ExternalSettlementLineRecord> =
        jdbc.query(
            """
            SELECT external_settlement_line_id,
                   external_settlement_import_id,
                   line_number,
                   external_reference,
                   payment_instruction_id,
                   biller_id,
                   amount_minor,
                   currency,
                   business_date,
                   value_date,
                   status,
                   raw_line,
                   synthetic_only,
                   created_at
            FROM payment_external_settlement_lines
            WHERE external_settlement_import_id = :importId
            ORDER BY line_number
            """.trimIndent(),
            mapOf("importId" to importId),
            this::mapLine
        )

    fun acceptedLinesForImport(importId: String): List<ExternalSettlementLineRecord> =
        jdbc.query(
            """
            SELECT external_settlement_line_id,
                   external_settlement_import_id,
                   line_number,
                   external_reference,
                   payment_instruction_id,
                   biller_id,
                   amount_minor,
                   currency,
                   business_date,
                   value_date,
                   status,
                   raw_line,
                   synthetic_only,
                   created_at
            FROM payment_external_settlement_lines
            WHERE external_settlement_import_id = :importId
              AND status = 'ACCEPTED'
            ORDER BY biller_id, currency, value_date, line_number
            """.trimIndent(),
            mapOf("importId" to importId),
            this::mapLine
        )

    fun markImportBatched(importId: String) {
        jdbc.update(
            """
            UPDATE payment_external_settlement_imports
            SET status = 'BATCHED'
            WHERE external_settlement_import_id = :importId
            """.trimIndent(),
            mapOf("importId" to importId)
        )
    }

    fun findBatchRunByIdempotencyKey(idempotencyKey: String): SettlementBatchRunRecord? =
        findBatchRunBy(
            "WHERE idempotency_key = :idempotencyKey",
            mapOf("idempotencyKey" to idempotencyKey)
        )

    fun findBatchRun(batchRunId: String): SettlementBatchRunRecord? =
        findBatchRunBy(
            "WHERE settlement_batch_run_id = :batchRunId",
            mapOf("batchRunId" to batchRunId)
        )

    fun findBatchRunByImportId(importId: String): SettlementBatchRunRecord? =
        findBatchRunBy(
            "WHERE external_settlement_import_id = :importId",
            mapOf("importId" to importId)
        )

    fun insertBatchRun(record: SettlementBatchRunRecord) {
        jdbc.update(
            """
            INSERT INTO payment_settlement_batch_runs (
              settlement_batch_run_id,
              external_settlement_import_id,
              fee_rate_bps,
              vat_rate_bps,
              cutoff_at,
              idempotency_key,
              request_hash,
              requested_by,
              reason,
              synthetic_only,
              created_at
            )
            VALUES (
              :batchRunId,
              :importId,
              :feeRateBps,
              :vatRateBps,
              :cutoffAt,
              :idempotencyKey,
              :requestHash,
              :requestedBy,
              :reason,
              true,
              :createdAt
            )
            """.trimIndent(),
            mapOf(
                "batchRunId" to record.settlementBatchRunId,
                "importId" to record.externalSettlementImportId,
                "feeRateBps" to record.feeRateBps,
                "vatRateBps" to record.vatRateBps,
                "cutoffAt" to record.cutoffAt,
                "idempotencyKey" to record.idempotencyKey,
                "requestHash" to record.requestHash,
                "requestedBy" to record.requestedBy,
                "reason" to record.reason,
                "createdAt" to record.createdAt
            )
        )
    }

    fun insertBatch(record: SettlementBatchRecord) {
        jdbc.update(
            """
            INSERT INTO payment_settlement_batches (
              settlement_batch_id,
              settlement_batch_run_id,
              external_settlement_import_id,
              biller_id,
              currency,
              business_date,
              value_date,
              cutoff_at,
              gross_amount_minor,
              fee_rate_bps,
              fee_amount_minor,
              vat_rate_bps,
              vat_amount_minor,
              adjustment_amount_minor,
              net_amount_minor,
              status,
              external_payout_reference,
              synthetic_only,
              created_at
            )
            VALUES (
              :batchId,
              :batchRunId,
              :importId,
              :billerId,
              :currency,
              :businessDate,
              :valueDate,
              :cutoffAt,
              :grossAmountMinor,
              :feeRateBps,
              :feeAmountMinor,
              :vatRateBps,
              :vatAmountMinor,
              :adjustmentAmountMinor,
              :netAmountMinor,
              :status,
              :externalPayoutReference,
              true,
              :createdAt
            )
            """.trimIndent(),
            mapOf(
                "batchId" to record.settlementBatchId,
                "batchRunId" to record.settlementBatchRunId,
                "importId" to record.externalSettlementImportId,
                "billerId" to record.billerId,
                "currency" to record.currency,
                "businessDate" to record.businessDate,
                "valueDate" to record.valueDate,
                "cutoffAt" to record.cutoffAt,
                "grossAmountMinor" to record.grossAmountMinor,
                "feeRateBps" to record.feeRateBps,
                "feeAmountMinor" to record.feeAmountMinor,
                "vatRateBps" to record.vatRateBps,
                "vatAmountMinor" to record.vatAmountMinor,
                "adjustmentAmountMinor" to record.adjustmentAmountMinor,
                "netAmountMinor" to record.netAmountMinor,
                "status" to record.status.name,
                "externalPayoutReference" to record.externalPayoutReference,
                "createdAt" to record.createdAt
            )
        )
    }

    fun insertBatchItem(
        batchId: String,
        line: ExternalSettlementLineRecord
    ) {
        jdbc.update(
            """
            INSERT INTO payment_settlement_batch_items (
              settlement_batch_item_id,
              settlement_batch_id,
              external_settlement_line_id,
              payment_instruction_id,
              amount_minor,
              synthetic_only
            )
            VALUES (
              :itemId,
              :batchId,
              :lineId,
              :paymentInstructionId,
              :amountMinor,
              true
            )
            """.trimIndent(),
            mapOf(
                "itemId" to "SBI-${UUID.randomUUID().toString().uppercase()}",
                "batchId" to batchId,
                "lineId" to line.externalSettlementLineId,
                "paymentInstructionId" to line.paymentInstructionId,
                "amountMinor" to line.amountMinor
            )
        )
    }

    fun batchesForRun(batchRunId: String): List<Pair<SettlementBatchRecord, Int>> =
        jdbc.query(
            """
            SELECT b.settlement_batch_id,
                   b.settlement_batch_run_id,
                   b.external_settlement_import_id,
                   b.biller_id,
                   b.currency,
                   b.business_date,
                   b.value_date,
                   b.cutoff_at,
                   b.gross_amount_minor,
                   b.fee_rate_bps,
                   b.fee_amount_minor,
                   b.vat_rate_bps,
                   b.vat_amount_minor,
                   b.adjustment_amount_minor,
                   b.net_amount_minor,
                   b.status,
                   b.external_payout_reference,
                   b.synthetic_only,
                   b.created_at,
                   (
                     SELECT count(*)
                     FROM payment_settlement_batch_items i
                     WHERE i.settlement_batch_id = b.settlement_batch_id
                   ) AS item_count
            FROM payment_settlement_batches b
            WHERE b.settlement_batch_run_id = :batchRunId
            ORDER BY b.biller_id, b.currency, b.value_date, b.settlement_batch_id
            """.trimIndent(),
            mapOf("batchRunId" to batchRunId)
        ) { rs, _ -> mapBatch(rs) to rs.getInt("item_count") }

    private fun findImportBy(
        whereClause: String,
        params: Map<String, Any?>
    ): ExternalSettlementImportRecord? =
        jdbc.query(
            """
            SELECT external_settlement_import_id,
                   original_file_name,
                   institution_code,
                   file_sha256,
                   file_byte_size,
                   business_date,
                   status,
                   line_count,
                   accepted_line_count,
                   rejected_line_count,
                   requested_by,
                   reason,
                   idempotency_key,
                   request_hash,
                   synthetic_only,
                   received_at
            FROM payment_external_settlement_imports
            $whereClause
            """.trimIndent(),
            params,
            this::mapImport
        ).firstOrNull()

    private fun findBatchRunBy(
        whereClause: String,
        params: Map<String, Any?>
    ): SettlementBatchRunRecord? =
        jdbc.query(
            """
            SELECT settlement_batch_run_id,
                   external_settlement_import_id,
                   fee_rate_bps,
                   vat_rate_bps,
                   cutoff_at,
                   idempotency_key,
                   request_hash,
                   requested_by,
                   reason,
                   synthetic_only,
                   created_at
            FROM payment_settlement_batch_runs
            $whereClause
            """.trimIndent(),
            params,
            this::mapBatchRun
        ).firstOrNull()

    private fun mapImport(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): ExternalSettlementImportRecord =
        ExternalSettlementImportRecord(
            externalSettlementImportId = rs.getString("external_settlement_import_id"),
            originalFileName = rs.getString("original_file_name"),
            institutionCode = rs.getString("institution_code"),
            fileSha256 = rs.getString("file_sha256"),
            fileByteSize = rs.getLong("file_byte_size"),
            businessDate = rs.getObject("business_date", java.time.LocalDate::class.java),
            status = ExternalSettlementImportStatus.valueOf(rs.getString("status")),
            lineCount = rs.getInt("line_count"),
            acceptedLineCount = rs.getInt("accepted_line_count"),
            rejectedLineCount = rs.getInt("rejected_line_count"),
            requestedBy = rs.getString("requested_by"),
            reason = rs.getString("reason"),
            idempotencyKey = rs.getString("idempotency_key"),
            requestHash = rs.getString("request_hash"),
            syntheticOnly = rs.getBoolean("synthetic_only"),
            receivedAt = rs.getObject("received_at", java.time.OffsetDateTime::class.java)
        )

    private fun mapLine(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): ExternalSettlementLineRecord =
        ExternalSettlementLineRecord(
            externalSettlementLineId = rs.getString("external_settlement_line_id"),
            externalSettlementImportId = rs.getString("external_settlement_import_id"),
            lineNumber = rs.getInt("line_number"),
            externalReference = rs.getString("external_reference"),
            paymentInstructionId = rs.getString("payment_instruction_id"),
            billerId = rs.getString("biller_id"),
            amountMinor = rs.getLong("amount_minor"),
            currency = rs.getString("currency"),
            businessDate = rs.getObject("business_date", java.time.LocalDate::class.java),
            valueDate = rs.getObject("value_date", java.time.LocalDate::class.java),
            status = ExternalSettlementLineStatus.valueOf(rs.getString("status")),
            rawLine = rs.getString("raw_line"),
            syntheticOnly = rs.getBoolean("synthetic_only"),
            createdAt = rs.getObject("created_at", java.time.OffsetDateTime::class.java)
        )

    private fun mapBatchRun(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): SettlementBatchRunRecord =
        SettlementBatchRunRecord(
            settlementBatchRunId = rs.getString("settlement_batch_run_id"),
            externalSettlementImportId = rs.getString("external_settlement_import_id"),
            feeRateBps = rs.getInt("fee_rate_bps"),
            vatRateBps = rs.getInt("vat_rate_bps"),
            cutoffAt = rs.getObject("cutoff_at", java.time.OffsetDateTime::class.java),
            idempotencyKey = rs.getString("idempotency_key"),
            requestHash = rs.getString("request_hash"),
            requestedBy = rs.getString("requested_by"),
            reason = rs.getString("reason"),
            syntheticOnly = rs.getBoolean("synthetic_only"),
            createdAt = rs.getObject("created_at", java.time.OffsetDateTime::class.java)
        )

    private fun mapBatch(rs: ResultSet): SettlementBatchRecord =
        SettlementBatchRecord(
            settlementBatchId = rs.getString("settlement_batch_id"),
            settlementBatchRunId = rs.getString("settlement_batch_run_id"),
            externalSettlementImportId = rs.getString("external_settlement_import_id"),
            billerId = rs.getString("biller_id"),
            currency = rs.getString("currency"),
            businessDate = rs.getObject("business_date", java.time.LocalDate::class.java),
            valueDate = rs.getObject("value_date", java.time.LocalDate::class.java),
            cutoffAt = rs.getObject("cutoff_at", java.time.OffsetDateTime::class.java),
            grossAmountMinor = rs.getLong("gross_amount_minor"),
            feeRateBps = rs.getInt("fee_rate_bps"),
            feeAmountMinor = rs.getLong("fee_amount_minor"),
            vatRateBps = rs.getInt("vat_rate_bps"),
            vatAmountMinor = rs.getLong("vat_amount_minor"),
            adjustmentAmountMinor = rs.getLong("adjustment_amount_minor"),
            netAmountMinor = rs.getLong("net_amount_minor"),
            status = SettlementBatchStatus.valueOf(rs.getString("status")),
            externalPayoutReference = rs.getString("external_payout_reference"),
            syntheticOnly = rs.getBoolean("synthetic_only"),
            createdAt = rs.getObject("created_at", java.time.OffsetDateTime::class.java)
        )
}
