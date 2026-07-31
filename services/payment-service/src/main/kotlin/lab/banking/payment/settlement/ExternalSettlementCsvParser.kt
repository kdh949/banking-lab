package lab.banking.payment.settlement

import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.springframework.stereotype.Component

@Component
class ExternalSettlementCsvParser {
    fun parse(csvContent: String, expectedBusinessDate: LocalDate): List<ParsedExternalSettlementLine> {
        if (csvContent.isBlank()) {
            throw ExternalSettlementCsvException(
                lineNumber = null,
                field = "csvContent",
                message = "external settlement CSV content is required"
            )
        }

        val normalizedLines = csvContent
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .dropLastWhile(String::isBlank)

        if (normalizedLines.size < 2) {
            throw ExternalSettlementCsvException(
                lineNumber = 1,
                field = "csvContent",
                message = "external settlement CSV must contain a header and at least one data row"
            )
        }

        val actualHeader = parseCsvLine(normalizedLines.first().removePrefix("\uFEFF"), 1)
            .map(String::trim)
        if (actualHeader != expectedHeader) {
            throw ExternalSettlementCsvException(
                lineNumber = 1,
                field = "header",
                message = "external settlement CSV header must be ${expectedHeader.joinToString(",")}"
            )
        }

        return normalizedLines.drop(1).mapIndexed { index, rawLine ->
            val lineNumber = index + 2
            if (rawLine.isBlank()) {
                throw ExternalSettlementCsvException(
                    lineNumber = lineNumber,
                    field = "row",
                    message = "blank external settlement CSV rows are not allowed"
                )
            }
            val values = parseCsvLine(rawLine, lineNumber)
            if (values.size != expectedHeader.size) {
                throw ExternalSettlementCsvException(
                    lineNumber = lineNumber,
                    field = "row",
                    message = "external settlement CSV row must contain ${expectedHeader.size} columns"
                )
            }

            val row = expectedHeader.zip(values.map(String::trim)).toMap()
            val businessDate = parseDate(row.required("business_date", lineNumber), "business_date", lineNumber)
            if (businessDate != expectedBusinessDate) {
                throw ExternalSettlementCsvException(
                    lineNumber = lineNumber,
                    field = "business_date",
                    message = "external settlement row business_date must match request businessDate $expectedBusinessDate"
                )
            }
            val valueDate = parseDate(row.required("value_date", lineNumber), "value_date", lineNumber)
            if (valueDate.isBefore(businessDate)) {
                throw ExternalSettlementCsvException(
                    lineNumber = lineNumber,
                    field = "value_date",
                    message = "external settlement value_date cannot be before business_date"
                )
            }

            val paymentInstructionId = row.required("payment_instruction_id", lineNumber)
            if (!paymentInstructionId.startsWith("PAY-")) {
                throw ExternalSettlementCsvException(
                    lineNumber = lineNumber,
                    field = "payment_instruction_id",
                    message = "payment_instruction_id must start with PAY-"
                )
            }
            val billerId = row.required("biller_id", lineNumber)
            if (!billerId.startsWith("SYN-BILLER-")) {
                throw ExternalSettlementCsvException(
                    lineNumber = lineNumber,
                    field = "biller_id",
                    message = "biller_id must reference a synthetic biller"
                )
            }
            val currency = row.required("currency", lineNumber).uppercase()
            if (!currency.matches(Regex("^[A-Z]{3}$"))) {
                throw ExternalSettlementCsvException(
                    lineNumber = lineNumber,
                    field = "currency",
                    message = "currency must be a three-letter uppercase code"
                )
            }
            val amountMinor = row.required("amount_minor", lineNumber).toLongOrNull()
            if (amountMinor == null || amountMinor <= 0) {
                throw ExternalSettlementCsvException(
                    lineNumber = lineNumber,
                    field = "amount_minor",
                    message = "amount_minor must be a positive integer"
                )
            }
            val status = try {
                ExternalSettlementLineStatus.valueOf(row.required("status", lineNumber).uppercase())
            } catch (_: IllegalArgumentException) {
                throw ExternalSettlementCsvException(
                    lineNumber = lineNumber,
                    field = "status",
                    message = "status must be ACCEPTED, REJECTED, or RETURNED"
                )
            }

            ParsedExternalSettlementLine(
                lineNumber = lineNumber,
                externalReference = row.required("external_reference", lineNumber),
                paymentInstructionId = paymentInstructionId,
                billerId = billerId,
                amountMinor = amountMinor,
                currency = currency,
                businessDate = businessDate,
                valueDate = valueDate,
                status = status,
                rawLine = rawLine
            )
        }
    }

    private fun Map<String, String>.required(field: String, lineNumber: Int): String =
        get(field)?.takeIf(String::isNotBlank)
            ?: throw ExternalSettlementCsvException(
                lineNumber = lineNumber,
                field = field,
                message = "$field is required"
            )

    private fun parseDate(value: String, field: String, lineNumber: Int): LocalDate =
        try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            throw ExternalSettlementCsvException(
                lineNumber = lineNumber,
                field = field,
                message = "$field must be an ISO-8601 date"
            )
        }

    private fun parseCsvLine(rawLine: String, lineNumber: Int): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0

        while (index < rawLine.length) {
            val character = rawLine[index]
            when {
                quoted && character == '"' -> {
                    if (index + 1 < rawLine.length && rawLine[index + 1] == '"') {
                        current.append('"')
                        index += 1
                    } else {
                        quoted = false
                    }
                }
                !quoted && character == '"' -> {
                    if (current.isNotEmpty()) {
                        throw ExternalSettlementCsvException(
                            lineNumber = lineNumber,
                            field = "row",
                            message = "quote must begin at the start of a CSV field"
                        )
                    }
                    quoted = true
                }
                !quoted && character == ',' -> {
                    values += current.toString()
                    current.setLength(0)
                }
                else -> current.append(character)
            }
            index += 1
        }

        if (quoted) {
            throw ExternalSettlementCsvException(
                lineNumber = lineNumber,
                field = "row",
                message = "unterminated quoted CSV field"
            )
        }

        values += current.toString()
        return values
    }

    companion object {
        val expectedHeader = listOf(
            "external_reference",
            "payment_instruction_id",
            "biller_id",
            "amount_minor",
            "currency",
            "business_date",
            "value_date",
            "status"
        )
    }
}
