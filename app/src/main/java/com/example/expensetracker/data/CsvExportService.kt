package com.example.expensetracker.data

import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class CsvExportInput(
    val expenses: List<Expense>,
    val incomes: List<Expense>,
    val transfers: List<TransferHistory>,
    val accounts: List<Account>,
    val debts: List<Debt>,
    val expenseKeywords: Map<Int, List<String>>,
    val paymentsByDebtId: Map<Int, List<Expense>>
)

data class CsvExportResult(
    val zipBytes: ByteArray,
    val warningCount: Int
)

class CsvExportService {

    suspend fun buildReportingZip(
        period: TimeFilter,
        input: CsvExportInput,
        defaultCurrencyAtExport: String,
        convertToDefaultAtExport: suspend (
            amount: BigDecimal,
            currency: String,
            date: Long,
            originalDefaultCurrency: String?,
            amountInOriginalDefault: BigDecimal?
        ) -> BigDecimal?,
        convertAmountToTargetCurrency: suspend (
            amount: BigDecimal,
            sourceCurrency: String,
            targetCurrency: String,
            date: Long
        ) -> BigDecimal?
    ): CsvExportResult {
        require(period is TimeFilter.Day || period is TimeFilter.Week || period is TimeFilter.Month || period is TimeFilter.Year || period is TimeFilter.Period) {
            "Reporting CSV export requires an explicit period"
        }

        val range = period.toDateRange()
            ?: throw IllegalArgumentException("Selected reporting period cannot be resolved")

        val accountByName = input.accounts.associateBy { it.name.lowercase(Locale.ROOT) }
        val debtByParentExpenseId = input.debts.associateBy { it.parentExpenseId }
        val warnings = mutableListOf<String>()

        val filteredExpenses = input.expenses.filter { it.expenseDate in range.first..range.second }
        val filteredIncomes = input.incomes.filter { it.expenseDate in range.first..range.second }
        val filteredTransfers = input.transfers.filter { it.date in range.first..range.second }

        val expensesCsv = buildExpenseIncomeCsv(
            rows = filteredExpenses,
            debtByParentExpenseId = debtByParentExpenseId,
            paymentsByDebtId = input.paymentsByDebtId,
            keywordMap = input.expenseKeywords,
            defaultCurrencyAtExport = defaultCurrencyAtExport,
            warnings = warnings,
            convertToDefaultAtExport = convertToDefaultAtExport,
            convertAmountToTargetCurrency = convertAmountToTargetCurrency
        )

        val incomesCsv = buildExpenseIncomeCsv(
            rows = filteredIncomes,
            debtByParentExpenseId = debtByParentExpenseId,
            paymentsByDebtId = input.paymentsByDebtId,
            keywordMap = input.expenseKeywords,
            defaultCurrencyAtExport = defaultCurrencyAtExport,
            warnings = warnings,
            convertToDefaultAtExport = convertToDefaultAtExport,
            convertAmountToTargetCurrency = convertAmountToTargetCurrency
        )

        val transfersCsv = buildTransfersCsv(
            rows = filteredTransfers,
            accountByName = accountByName,
            defaultCurrencyAtExport = defaultCurrencyAtExport,
            warnings = warnings,
            convertToDefaultAtExport = convertToDefaultAtExport
        )

        val zipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zip ->
                writeEntry(zip, "expenses.csv", expensesCsv)
                writeEntry(zip, "incomes.csv", incomesCsv)
                writeEntry(zip, "transfers.csv", transfersCsv)
            }
            baos.toByteArray()
        }

        return CsvExportResult(zipBytes = zipBytes, warningCount = warnings.size)
    }

    private suspend fun buildExpenseIncomeCsv(
        rows: List<Expense>,
        debtByParentExpenseId: Map<Int, Debt>,
        paymentsByDebtId: Map<Int, List<Expense>>,
        keywordMap: Map<Int, List<String>>,
        defaultCurrencyAtExport: String,
        warnings: MutableList<String>,
        convertToDefaultAtExport: suspend (
            amount: BigDecimal,
            currency: String,
            date: Long,
            originalDefaultCurrency: String?,
            amountInOriginalDefault: BigDecimal?
        ) -> BigDecimal?,
        convertAmountToTargetCurrency: suspend (
            amount: BigDecimal,
            sourceCurrency: String,
            targetCurrency: String,
            date: Long
        ) -> BigDecimal?
    ): String {
        val header = listOf(
            "Date",
            "Type",
            "Category",
            "Account",
            "Comment/Note",
            "AmountOriginal",
            "CurrencyOriginal",
            "AmountDefaultAtExport",
            "CurrencyDefaultAtExport",
            "Debt",
            "Paid Amount",
            "Keywords"
        )

        val lines = mutableListOf(toCsvLine(header))

        for (expense in rows) {
            val amountInDefaultAtExport = convertToDefaultAtExport(
                expense.amount,
                expense.currency,
                expense.expenseDate,
                expense.originalDefaultCurrencyCode,
                expense.amountInOriginalDefault
            )

            if (amountInDefaultAtExport == null && expense.currency != defaultCurrencyAtExport) {
                warnings.add("Missing conversion rate for expense ${expense.id}")
            }

            val debt = debtByParentExpenseId[expense.id]
            val isDebt = debt != null
            val paidAmount = if (isDebt) {
                val debtCurrency = expense.currency
                var paid = BigDecimal.ZERO
                val payments = paymentsByDebtId[debt.id].orEmpty()
                for (payment in payments) {
                    val converted = convertAmountToTargetCurrency(
                        payment.amount,
                        payment.currency,
                        debtCurrency,
                        payment.expenseDate
                    )
                    if (converted == null) {
                        warnings.add("Missing conversion rate for debt payment ${payment.id}")
                        continue
                    }
                    paid = paid.add(converted)
                }
                paid
            } else {
                BigDecimal.ZERO
            }

            val keywords = keywordMap[expense.id].orEmpty()
            val keywordLiteral = "[${keywords.joinToString(", ")}]"

            val row = listOf(
                formatDate(expense.expenseDate),
                expense.type,
                expense.category,
                expense.account,
                expense.comment.orEmpty(),
                formatDecimal(expense.amount),
                expense.currency,
                formatDecimal(amountInDefaultAtExport),
                defaultCurrencyAtExport,
                if (isDebt) "Yes" else "No",
                formatDecimal(paidAmount),
                keywordLiteral
            )

            lines.add(toCsvLine(row))
        }

        return lines.joinToString("\n")
    }

    private suspend fun buildTransfersCsv(
        rows: List<TransferHistory>,
        accountByName: Map<String, Account>,
        defaultCurrencyAtExport: String,
        warnings: MutableList<String>,
        convertToDefaultAtExport: suspend (
            amount: BigDecimal,
            currency: String,
            date: Long,
            originalDefaultCurrency: String?,
            amountInOriginalDefault: BigDecimal?
        ) -> BigDecimal?
    ): String {
        val header = listOf(
            "Date",
            "SourceAccount",
            "DestinationAccount",
            "SourceAmountOriginal",
            "SourceCurrencyOriginal",
            "SourceAmountDefaultAtExport",
            "SourceCurrencyDefaultAtExport",
            "TargetAmount",
            "TargetCurrency",
            "Comment"
        )

        val lines = mutableListOf(toCsvLine(header))

        for (transfer in rows) {
            val sourceAmountDefault = convertToDefaultAtExport(
                transfer.amount,
                transfer.currency,
                transfer.date,
                transfer.originalDefaultCurrencyCode,
                transfer.amountInOriginalDefault
            )

            if (sourceAmountDefault == null && transfer.currency != defaultCurrencyAtExport) {
                warnings.add("Missing conversion rate for transfer ${transfer.id}")
            }

            val sourceAccount = accountByName[transfer.sourceAccount.lowercase(Locale.ROOT)]
            val destinationAccount = accountByName[transfer.destinationAccount.lowercase(Locale.ROOT)]

            val sameCurrencyTransfer = sourceAccount != null && destinationAccount != null &&
                sourceAccount.currency.equals(destinationAccount.currency, ignoreCase = true)

            val targetAmount = if (sameCurrencyTransfer) {
                transfer.amount
            } else {
                transfer.destinationAmount ?: transfer.amount
            }

            val targetCurrency = if (sameCurrencyTransfer) {
                transfer.currency
            } else {
                destinationAccount?.currency ?: transfer.destinationCurrency ?: transfer.currency
            }

            val row = listOf(
                formatDate(transfer.date),
                transfer.sourceAccount,
                transfer.destinationAccount,
                formatDecimal(transfer.amount),
                transfer.currency,
                formatDecimal(sourceAmountDefault),
                defaultCurrencyAtExport,
                formatDecimal(targetAmount),
                targetCurrency,
                transfer.comment.orEmpty()
            )

            lines.add(toCsvLine(row))
        }

        return lines.joinToString("\n")
    }

    private fun writeEntry(zip: ZipOutputStream, entryName: String, content: String) {
        val entry = ZipEntry(entryName)
        zip.putNextEntry(entry)
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun formatDate(millis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
    }

    private fun formatDecimal(value: BigDecimal?): String {
        if (value == null) return ""
        return value.stripTrailingZeros().toPlainString()
    }

    private fun toCsvLine(columns: List<String>): String {
        return columns.joinToString(",") { escapeCsv(it) }
    }

    private fun escapeCsv(value: String): String {
        val normalized = value.replace("\r\n", "\n").replace("\r", "\n")
        val shouldQuote = normalized.contains(',') || normalized.contains('"') || normalized.contains('\n')
        if (!shouldQuote) {
            return normalized
        }
        return "\"${normalized.replace("\"", "\"\"")}\""
    }
}
