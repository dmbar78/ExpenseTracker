package com.example.expensetracker.sharedimport

import com.example.expensetracker.util.VoiceCommandParser
import java.math.BigDecimal
import java.text.DateFormat
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

data class ParsedReceipt(
    val amount: BigDecimal?,
    val dateMillis: Long
)

object ReceiptTextParser {
    private val amountRegex = Regex("""(?<![\d.,])(?:\d{1,3}(?:[.,]\d{3})+|\d+)(?:[.,]\d{1,2})?(?![\d.,])""")
    private val isoDateRegex = Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\b""")
    private val numericDateRegex = Regex("""\b(\d{1,2})[./-](\d{1,2})[./-](\d{2,4})\b""")
    private val yearlessDateRegex = Regex("""\b(\d{1,2})[./](\d{1,2})(?![./]\d)\b""")
    private val amountLabels = listOf(
        "grand total", "gesamtpreis", "gesamtbetrag", "total including vat", "amount paid",
        "already paid", "balance due", "total", "paid", "amount", "flexpreis", "billett",
        "reservierung", "reservation"
    )
    private val negativeLabels = listOf(
        "subtotal", "sub total", "total excluding", "excl.", "tax", "vat", "change", "cash", "tender"
    )
    private val currencyRegex = Regex("""(?:€|£|\$|\b(?:CHF|EUR|USD|GBP)\b)""", RegexOption.IGNORE_CASE)

    fun parse(
        text: String,
        locale: Locale = Locale.getDefault(),
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ParsedReceipt {
        return ParsedReceipt(
            amount = parseAmount(text),
            dateMillis = parseDate(
                text,
                locale,
                zoneId,
                Instant.ofEpochMilli(nowMillis).atZone(zoneId).year
            ) ?: startOfDay(nowMillis, zoneId)
        )
    }

    internal fun parseAmount(text: String): BigDecimal? {
        data class Candidate(val amount: BigDecimal, val score: Int)

        val lines = text.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        val candidates = lines.mapIndexedNotNull { index, line ->
            val lower = line.lowercase(Locale.ROOT)
            val previous = lines.getOrNull(index - 1)?.lowercase(Locale.ROOT).orEmpty()
            val twoLinesBack = lines.getOrNull(index - 2)?.lowercase(Locale.ROOT).orEmpty()
            val header = lines.firstOrNull()?.lowercase(Locale.ROOT).orEmpty()
            val score = when {
                negativeLabels.any { lower.contains(it) } -> -10
                lower.contains("grand total") || lower.contains("gesamtpreis") ||
                    lower.contains("gesamtbetrag") || lower.contains("total including vat") ||
                    lower.contains("amount paid") || lower.contains("already paid") -> 7
                lower.contains("flexpreis") || lower.contains("balance due") -> 6
                lower.contains("total") || lower.contains("amount") || lower.contains("billett") -> 5
                lower.contains("reservierung") || lower.contains("reservation") -> 4
                amountLabels.any { previous == it || previous.endsWith(": $it") } -> 6
                amountLabels.any { twoLinesBack == it } && previous.none(Char::isDigit) -> 5
                header.contains("amount") && index == 1 -> 5
                currencyRegex.containsMatchIn(line) -> 4
                else -> 0
            }
            amountRegex.findAll(line).mapNotNull { match ->
                VoiceCommandParser.parseMoneyAmount(match.value)?.let { Candidate(it, score) }
            }.lastOrNull()
        }.filter { it.score >= 4 }.toList()

        val bestScore = candidates.maxOfOrNull { it.score } ?: return null
        val best = candidates.filter { it.score == bestScore }.map { it.amount }.distinct()
        return best.singleOrNull()
    }

    internal fun parseDate(
        text: String,
        locale: Locale,
        zoneId: ZoneId,
        defaultYear: Int = java.time.ZonedDateTime.now(zoneId).year
    ): Long? {
        val lines = text.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        val orderedLines = lines.mapIndexed { index, line ->
            val lower = line.lowercase(Locale.ROOT)
            val previous = lines.getOrNull(index - 1)?.lowercase(Locale.ROOT).orEmpty()
            val score = when {
                lower.contains("download date") || lower.contains("geburtsdatum") ||
                    previous.contains("geburtsdatum") || previous == "reisende" || previous == "passagierliste:" -> -10
                lower.contains("transaction date") || previous.contains("transaction date") -> 10
                lower.contains("invoice date") || previous.contains("invoice date") -> 10
                lower.startsWith("execution") || previous == "execution" -> 9
                lower.contains("verbindung") || previous.contains("verbindung") -> 9
                lower.contains("gültig") || lower.contains("gueltig") || lower.contains("gültigkeit") ||
                    previous.contains("reservierung") -> 9
                lower.startsWith("date") || previous == "date" -> 8
                index == 1 && lines.first().lowercase(Locale.ROOT).startsWith("date") -> 7
                else -> 0
            }
            line to score
        }.sortedByDescending { it.second }.map { it.first }

        orderedLines.forEach { line ->
            isoDateRegex.find(line)?.let { match ->
                dateMillis(match.groupValues[1], match.groupValues[2], match.groupValues[3], zoneId)?.let { return it }
            }
            parseTextualDate(line, locale, zoneId)?.let { return it }
            numericDateRegex.find(line)?.let { match ->
                val first = match.groupValues[1].toInt()
                val second = match.groupValues[2].toInt()
                val year = normalizeYear(match.groupValues[3].toInt())
                val monthFirst = when {
                    first > 12 -> false
                    second > 12 -> true
                    else -> isMonthFirst(locale)
                }
                val month = if (monthFirst) first else second
                val day = if (monthFirst) second else first
                localDateMillis(year, month, day, zoneId)?.let { return it }
            }
            yearlessDateRegex.find(line)?.let { match ->
                val first = match.groupValues[1].toInt()
                val second = match.groupValues[2].toInt()
                val monthFirst = when {
                    first > 12 -> false
                    second > 12 -> true
                    else -> isMonthFirst(locale)
                }
                val month = if (monthFirst) first else second
                val day = if (monthFirst) second else first
                localDateMillis(defaultYear, month, day, zoneId)?.let { return it }
            }
        }
        return null
    }

    private fun parseTextualDate(line: String, locale: Locale, zoneId: ZoneId): Long? {
        val monthNames = (
            DateFormatSymbols(locale).months.filter(String::isNotBlank) +
                DateFormatSymbols(locale).shortMonths.filter(String::isNotBlank) +
                DateFormatSymbols(Locale.ENGLISH).months.filter(String::isNotBlank) +
                DateFormatSymbols(Locale.ENGLISH).shortMonths.filter(String::isNotBlank)
            ).distinct()
        val monthPattern = monthNames.joinToString("|") { Regex.escape(it.trimEnd('.')) }
        val dateTextRegex = Regex(
            """\b(?:\d{1,2}\s+(?:$monthPattern)|(?:$monthPattern)\s+\d{1,2})(?:,)?\s+\d{4}\b""",
            RegexOption.IGNORE_CASE
        )
        val cleaned = dateTextRegex.find(line)?.value?.replace(",", "") ?: return null
        val patterns = listOf("d MMMM uuuu", "MMMM d uuuu", "d MMM uuuu", "MMM d uuuu")
        for (pattern in patterns) {
            try {
                val formatter = DateTimeFormatter.ofPattern(pattern, locale).withResolverStyle(ResolverStyle.STRICT)
                return LocalDate.parse(cleaned, formatter).atStartOfDay(zoneId).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                try {
                    val formatter = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)
                    return LocalDate.parse(cleaned, formatter).atStartOfDay(zoneId).toInstant().toEpochMilli()
                } catch (_: DateTimeParseException) {
                    // Try the next supported receipt date shape.
                }
            }
        }
        return null
    }

    private fun isMonthFirst(locale: Locale): Boolean {
        val pattern = (DateFormat.getDateInstance(DateFormat.SHORT, locale) as? SimpleDateFormat)?.toPattern().orEmpty()
        return pattern.indexOf('M').let { monthIndex -> monthIndex >= 0 && monthIndex < pattern.indexOf('d') }
    }

    private fun dateMillis(year: String, month: String, day: String, zoneId: ZoneId): Long? =
        localDateMillis(year.toInt(), month.toInt(), day.toInt(), zoneId)

    private fun localDateMillis(year: Int, month: Int, day: Int, zoneId: ZoneId): Long? =
        runCatching { LocalDate.of(year, month, day).atStartOfDay(zoneId).toInstant().toEpochMilli() }.getOrNull()

    private fun normalizeYear(year: Int): Int = if (year < 100) 2000 + year else year

    private fun startOfDay(nowMillis: Long, zoneId: ZoneId): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
}