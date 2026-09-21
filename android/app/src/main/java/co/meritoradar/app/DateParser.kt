package co.meritoradar.app

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*

/**
 * Strict Spanish calendar dates; no inferred year and no invented time of day.
 * Ported from backend/app/dates.py
 */
object DateParser {
    private val MONTHS = mapOf(
        "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4,
        "mayo" to 5, "junio" to 6, "julio" to 7, "agosto" to 8,
        "septiembre" to 9, "setiembre" to 9, "octubre" to 10,
        "noviembre" to 11, "diciembre" to 12
    )

    private val MONTH_PATTERN = MONTHS.keys.joinToString("|")
    private val NORMALIZER = Regex("\\s+")

    data class DateEvidence(
        val rawText: String,
        val start: LocalDate?,
        val end: LocalDate?,
        val confidence: String,
        val timezone: String = "America/Bogota"
    )

    /**
     * Parse Spanish date text and return DateEvidence
     */
    fun parseDates(rawText: String): DateEvidence {
        val text = normalizeText(rawText)
        val unknown = DateEvidence(rawText, null, null, "UNCONFIRMED")

        // Pattern: "del X al Y de MONTH de YEAR" or "entre el X y el Y de MONTH de YEAR"
        val rangePattern = Regex("(?:del|entre el) (\\d{1,2}) (?:al|y el) (\\d{1,2}) de ($MONTH_PATTERN) de (\\d{4})")
        val rangeMatch = rangePattern.matchEntire(text)

        if (rangeMatch != null) {
            val (first, last, month, year) = rangeMatch.destructured
            return try {
                val startDate = LocalDate.of(year.toInt(), MONTHS[month]!!, first.toInt())
                val endDate = LocalDate.of(year.toInt(), MONTHS[month]!!, last.toInt())
                if (startDate <= endDate) {
                    DateEvidence(rawText, startDate, endDate, "CONFIRMED")
                } else {
                    unknown
                }
            } catch (e: Exception) {
                unknown
            }
        }

        // Pattern: "a partir del X de MONTH de YEAR" or "X de MONTH de YEAR"
        val singlePattern = Regex("(?:a partir del )?(\\d{1,2}) de ($MONTH_PATTERN) de (\\d{4})")
        val singleMatch = singlePattern.matchEntire(text)

        if (singleMatch != null) {
            val (day, month, year) = singleMatch.destructured
            return try {
                val date = LocalDate.of(year.toInt(), MONTHS[month]!!, day.toInt())
                DateEvidence(rawText, date, null, "CONFIRMED")
            } catch (e: Exception) {
                unknown
            }
        }

        // Pattern: "DD/MM/YYYY" or "DD-MM-YYYY"
        val numericPattern = Regex("(\\d{1,2})([/\\-])(\\d{1,2})\\2(\\d{4})")
        val numericMatch = numericPattern.matchEntire(text)

        if (numericMatch != null) {
            val (day, _, month, year) = numericMatch.destructured
            return try {
                val date = LocalDate.of(year.toInt(), month.toInt(), day.toInt())
                DateEvidence(rawText, date, null, "CONFIRMED")
            } catch (e: Exception) {
                unknown
            }
        }

        return unknown
    }

    /**
     * Normalize text: remove extra spaces, normalize unicode
     */
    private fun normalizeText(text: String): String {
        val normalized = java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFKC)
        return NORMALIZER.replace(normalized, " ").trim()
    }

    /**
     * Check if date text looks ambiguous or invalid
     */
    fun isAmbiguousOrInvalid(text: String): Boolean {
        val normalized = normalizeText(text)
        val result = parseDates(normalized)
        return result.confidence == "UNCONFIRMED"
    }
}
