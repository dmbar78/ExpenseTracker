package com.example.expensetracker.util

import com.example.expensetracker.viewmodel.ParsedExpense
import com.example.expensetracker.viewmodel.ParsedTransfer
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar
import java.util.Locale

/**
 * Parser for voice commands. Extracted from ExpenseViewModel for testability.
 * 
 * Supports:
 * - Expense commands: "Expense from <Account> <Amount> category <Category> [<Date>]"
 * - Income commands: "Income to <Account> <Amount> category <Category> [<Date>]"
 * - Transfer commands: "Transfer from <Source> to <Destination> <Amount> [<Date>]"
 * - Spoken dates: "January 1st", "1st of January", "January first", etc.
 * - Money amounts: "1234.56", "1,234.56", "1234,56" (EU format)
 */
object VoiceCommandParser {

    /**
     * Parses a transfer command from spoken text.
     * Format: "Transfer from <Source> to <Destination> <Amount> [<Date>]"
     * 
     * @param input The spoken text
     * @param defaultDateMillis Default date if no date is spoken (defaults to current time)
     * @return ParsedTransfer if successful, null otherwise
     */
    fun parseTransfer(input: String, defaultDateMillis: Long = System.currentTimeMillis()): ParsedTransfer? {
        val lowerInput = input.lowercase(Locale.ROOT)
        val transferIndex = lowerInput.indexOf("transfer from ")
        val toIndex = lowerInput.indexOf(" to ")

        if (transferIndex == -1 || toIndex == -1 || !(transferIndex < toIndex)) {
            return null
        }

        val sourceAccountStr = input.substring(transferIndex + 14, toIndex).trim()
        val restAfterTo = input.substring(toIndex + 4).trim()

        // Parse (and remove) trailing date first so day numbers don't get treated as amount
        val (restWithoutDate, transferDate) = parseTrailingSpokenDate(restAfterTo, defaultDateMillis)

        val amountRegex = Regex("([\\d,]+\\.?\\d*|[\\d.]+,?\\d*)")
        val amountMatch = amountRegex.findAll(restWithoutDate).lastOrNull() ?: return null
        val amount = parseMoneyAmount(amountMatch.value) ?: return null

        val destAccountStr = restWithoutDate.substring(0, amountMatch.range.first).trim()

        return ParsedTransfer(
            sourceAccountName = sourceAccountStr,
            destAccountName = destAccountStr,
            amount = amount,
            transferDate = transferDate,
            comment = null
        )
    }

    /**
     * Parses an expense/income command from spoken text.
     * Format: "Expense from <Account> <Amount> category <Category> [<Date>]"
     *         "Income to <Account> <Amount> category <Category> [<Date>]"
     * 
     * @param input The spoken text
     * @param defaultDateMillis Default date if no date is spoken (defaults to current time)
     * @return ParsedExpense if successful, null otherwise
     */
    fun parseExpense(input: String, defaultDateMillis: Long = System.currentTimeMillis()): ParsedExpense? {
        val lowerInput = input.lowercase(Locale.ROOT)

        val expenseKeyword = "expense from "
        val incomeKeyword = "income to "

        val expenseIndex = lowerInput.indexOf(expenseKeyword)
        val incomeIndex = lowerInput.indexOf(incomeKeyword)

        val type: String
        val typeIndex: Int

        if (expenseIndex != -1) {
            type = "Expense"
            typeIndex = expenseIndex + expenseKeyword.length
        } else if (incomeIndex != -1) {
            type = "Income"
            typeIndex = incomeIndex + incomeKeyword.length
        } else {
            return null
        }

        val categoryIndex = lowerInput.indexOf(" category ", startIndex = typeIndex)
        if (categoryIndex == -1) {
            return null
        }

        val accountAndAmountBlock = input.substring(typeIndex, categoryIndex).trim()
        val categoryStr = input.substring(categoryIndex + 10).trim()

        val amountRegex = Regex("([\\d,]+\\.?\\d*|[\\d.]+,?\\d*)")
        val amountMatch = amountRegex.findAll(accountAndAmountBlock).lastOrNull() ?: return null

        val accountStr = accountAndAmountBlock.substring(0, amountMatch.range.first).trim()
        val amountStr = amountMatch.value

        val amount = parseMoneyAmount(amountStr) ?: return null

        // Parse optional trailing date from category string
        val (finalCategoryStr, parsedDate) = parseTrailingSpokenDate(categoryStr, defaultDateMillis)

        return ParsedExpense(
            accountName = accountStr,
            amount = amount,
            categoryName = finalCategoryStr,
            type = type,
            expenseDate = parsedDate
        )
    }

    /**
     * Parses a trailing date phrase from spoken text.
     * Supports formats like:
     * - "January 1", "January 1st", "January first"
     * - "1st of January", "first of January"
     * - "1 January", "1st January"
     * 
     * @param input The text potentially containing a trailing date
     * @param defaultMillis Default date if no date found
     * @param year The year to use (defaults to current year)
     * @return Pair of (text with date removed, epoch millis)
     */
    fun parseTrailingSpokenDate(
        input: String,
        defaultMillis: Long = System.currentTimeMillis(),
        year: Int = Calendar.getInstance().get(Calendar.YEAR)
    ): Pair<String, Long> {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return Pair(trimmed, defaultMillis)
        }

        val months = listOf(
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december"
        )

        val ordinalWords = mapOf(
            "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5,
            "sixth" to 6, "seventh" to 7, "eighth" to 8, "ninth" to 9, "tenth" to 10,
            "eleventh" to 11, "twelfth" to 12, "thirteenth" to 13, "fourteenth" to 14,
            "fifteenth" to 15, "sixteenth" to 16, "seventeenth" to 17, "eighteenth" to 18,
            "nineteenth" to 19, "twentieth" to 20, "twenty first" to 21, "twenty-first" to 21,
            "twenty second" to 22, "twenty-second" to 22, "twenty third" to 23, "twenty-third" to 23,
            "twenty fourth" to 24, "twenty-fourth" to 24, "twenty fifth" to 25, "twenty-fifth" to 25,
            "twenty sixth" to 26, "twenty-sixth" to 26, "twenty seventh" to 27, "twenty-seventh" to 27,
            "twenty eighth" to 28, "twenty-eighth" to 28, "twenty ninth" to 29, "twenty-ninth" to 29,
            "thirtieth" to 30, "thirty first" to 31, "thirty-first" to 31,
            "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
            "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10
        )

        val lowerInput = trimmed.lowercase(Locale.ROOT)

        for ((monthIndex, monthName) in months.withIndex()) {
            // Pattern 1: "<month> <day>" e.g., "January 1", "January 1st"
            val monthDayRegex = Regex("\\s+$monthName\\s+(\\d{1,2})(?:st|nd|rd|th)?\\s*$", RegexOption.IGNORE_CASE)
            val monthDayMatch = monthDayRegex.find(lowerInput)
            if (monthDayMatch != null) {
                val day = monthDayMatch.groupValues[1].toIntOrNull()
                if (day != null && day in 1..31) {
                    val strippedText = trimmed.substring(0, monthDayMatch.range.first).trim()
                    val millis = buildDateMillis(monthIndex, day, year)
                    return Pair(strippedText, millis)
                }
            }

            // Pattern 1b: "<month> <ordinal word>" e.g., "January first"
            for ((ordinalWord, day) in ordinalWords) {
                val pattern = Regex("\\s+$monthName\\s+$ordinalWord\\s*$", RegexOption.IGNORE_CASE)
                val match = pattern.find(lowerInput)
                if (match != null) {
                    val strippedText = trimmed.substring(0, match.range.first).trim()
                    val millis = buildDateMillis(monthIndex, day, year)
                    return Pair(strippedText, millis)
                }
            }

            // Pattern 2: "<day> of <month>" e.g., "1st of January"
            val dayOfMonthRegex = Regex("\\s+(\\d{1,2})(?:st|nd|rd|th)?\\s+of\\s+$monthName\\s*$", RegexOption.IGNORE_CASE)
            val dayOfMonthMatch = dayOfMonthRegex.find(lowerInput)
            if (dayOfMonthMatch != null) {
                val day = dayOfMonthMatch.groupValues[1].toIntOrNull()
                if (day != null && day in 1..31) {
                    val strippedText = trimmed.substring(0, dayOfMonthMatch.range.first).trim()
                    val millis = buildDateMillis(monthIndex, day, year)
                    return Pair(strippedText, millis)
                }
            }

            // Pattern 2b: "<ordinal word> of <month>" e.g., "first of January"
            for ((ordinalWord, day) in ordinalWords) {
                val pattern = Regex("\\s+$ordinalWord\\s+of\\s+$monthName\\s*$", RegexOption.IGNORE_CASE)
                val match = pattern.find(lowerInput)
                if (match != null) {
                    val strippedText = trimmed.substring(0, match.range.first).trim()
                    val millis = buildDateMillis(monthIndex, day, year)
                    return Pair(strippedText, millis)
                }
            }

            // Pattern 3: "<day> <month>" e.g., "1 January", "1st January"
            val dayMonthRegex = Regex("\\s+(\\d{1,2})(?:st|nd|rd|th)?\\s+$monthName\\s*$", RegexOption.IGNORE_CASE)
            val dayMonthMatch = dayMonthRegex.find(lowerInput)
            if (dayMonthMatch != null) {
                val day = dayMonthMatch.groupValues[1].toIntOrNull()
                if (day != null && day in 1..31) {
                    val strippedText = trimmed.substring(0, dayMonthMatch.range.first).trim()
                    val millis = buildDateMillis(monthIndex, day, year)
                    return Pair(strippedText, millis)
                }
            }

            // Pattern 3b: "<ordinal word> <month>" e.g., "first January"
            for ((ordinalWord, day) in ordinalWords) {
                val pattern = Regex("\\s+$ordinalWord\\s+$monthName\\s*$", RegexOption.IGNORE_CASE)
                val match = pattern.find(lowerInput)
                if (match != null) {
                    val strippedText = trimmed.substring(0, match.range.first).trim()
                    val millis = buildDateMillis(monthIndex, day, year)
                    return Pair(strippedText, millis)
                }
            }
        }

        // No date pattern found
        return Pair(trimmed, defaultMillis)
    }

    /**
     * Parses a money amount string handling both dot and comma decimal separators.
     * Supports formats like: "1234.56", "1,234.56", "1234,56", "1.234,56"
     * 
     * @return BigDecimal with scale 2, or null if parsing fails
     */
    fun parseMoneyAmount(input: String): BigDecimal? {
        if (input.isBlank()) return null

        val cleaned = input.trim()

        val lastDotIndex = cleaned.lastIndexOf('.')
        val lastCommaIndex = cleaned.lastIndexOf(',')

        val normalizedString = when {
            lastDotIndex == -1 && lastCommaIndex == -1 -> cleaned
            lastCommaIndex == -1 -> {
                val afterDot = cleaned.length - lastDotIndex - 1
                if (cleaned.count { it == '.' } == 1 && afterDot <= 2) {
                    cleaned
                } else {
                    cleaned.replace(".", "")
                }
            }
            lastDotIndex == -1 -> {
                val afterComma = cleaned.length - lastCommaIndex - 1
                if (cleaned.count { it == ',' } == 1 && afterComma <= 2) {
                    cleaned.replace(',', '.')
                } else {
                    cleaned.replace(",", "")
                }
            }
            lastDotIndex > lastCommaIndex -> {
                cleaned.replace(",", "")
            }
            else -> {
                cleaned.replace(".", "").replace(',', '.')
            }
        }

        return try {
            BigDecimal(normalizedString).setScale(2, RoundingMode.HALF_UP)
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * Builds epoch millis for a given month (0-based) and day.
     */
    fun buildDateMillis(month: Int, day: Int, year: Int = Calendar.getInstance().get(Calendar.YEAR)): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month)
        calendar.set(Calendar.DAY_OF_MONTH, day)
        calendar.set(Calendar.HOUR_OF_DAY, 12) // Noon to avoid timezone edge cases
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
