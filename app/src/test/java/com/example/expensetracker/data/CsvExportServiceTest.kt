package com.example.expensetracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.util.Calendar
import java.util.zip.ZipInputStream

class CsvExportServiceTest {

    private val service = CsvExportService()

    @Test
    fun buildReportingZip_filtersAllDatasetsBySelectedPeriod() {
        val jan5 = dateMillis(2026, Calendar.JANUARY, 5)
        val jan10 = dateMillis(2026, Calendar.JANUARY, 10)
        val feb1 = dateMillis(2026, Calendar.FEBRUARY, 1)

        val result = runExport(
            expenses = listOf(
                expense(id = 1, type = "Expense", date = jan5, amount = "10", currency = "USD"),
                expense(id = 2, type = "Expense", date = feb1, amount = "11", currency = "USD")
            ),
            incomes = listOf(
                expense(id = 3, type = "Income", date = jan10, amount = "20", currency = "USD"),
                expense(id = 4, type = "Income", date = feb1, amount = "22", currency = "USD")
            ),
            transfers = listOf(
                transfer(id = 5, date = jan10, source = "Cash", destination = "Bank", amount = "30", currency = "USD"),
                transfer(id = 6, date = feb1, source = "Cash", destination = "Bank", amount = "33", currency = "USD")
            ),
            period = TimeFilter.Month(2026, Calendar.JANUARY)
        )

        val entries = unzip(result.zipBytes)
        val expensesLines = lines(entries.getValue("expenses.csv"))
        val incomesLines = lines(entries.getValue("incomes.csv"))
        val transfersLines = lines(entries.getValue("transfers.csv"))

        assertEquals(2, expensesLines.size)
        assertEquals(2, incomesLines.size)
        assertEquals(2, transfersLines.size)
    }

    @Test
    fun buildReportingZip_mapsDebtPaidAmountAndKeywords() {
        val jan5 = dateMillis(2026, Calendar.JANUARY, 5)
        val jan6 = dateMillis(2026, Calendar.JANUARY, 6)

        val debtParent = expense(id = 10, type = "Expense", date = jan5, amount = "100", currency = "USD")
        val payment = expense(
            id = 11,
            type = "Income",
            date = jan6,
            amount = "20",
            currency = "EUR",
            relatedDebtId = 100
        )

        val result = runExport(
            expenses = listOf(debtParent),
            incomes = listOf(payment),
            debts = listOf(Debt(id = 100, parentExpenseId = 10, status = "OPEN", notes = null)),
            keywords = mapOf(10 to listOf("rent", "fixed")),
            period = TimeFilter.Month(2026, Calendar.JANUARY)
        )

        val entries = unzip(result.zipBytes)
        val expenseRow = lines(entries.getValue("expenses.csv"))[1]
        val cols = csvColumns(expenseRow)

        assertEquals("Yes", cols[9])
        assertEquals("30", cols[10])
        assertEquals("[rent, fixed]", cols[11])
    }

    @Test
    fun buildReportingZip_appliesTransferTargetRulesForSameAndCrossCurrency() {
        val jan5 = dateMillis(2026, Calendar.JANUARY, 5)

        val sameCurrency = transfer(
            id = 21,
            date = jan5,
            source = "Cash",
            destination = "Wallet",
            amount = "50",
            currency = "USD"
        )
        val crossCurrency = transfer(
            id = 22,
            date = jan5,
            source = "Cash",
            destination = "Euro",
            amount = "60",
            currency = "USD",
            destinationAmount = "55",
            destinationCurrency = "EUR"
        )

        val result = runExport(
            transfers = listOf(sameCurrency, crossCurrency),
            accounts = listOf(
                Account(id = 1, name = "Cash", currency = "USD", balance = BigDecimal.ZERO),
                Account(id = 2, name = "Wallet", currency = "USD", balance = BigDecimal.ZERO),
                Account(id = 3, name = "Euro", currency = "EUR", balance = BigDecimal.ZERO)
            ),
            period = TimeFilter.Month(2026, Calendar.JANUARY)
        )

        val lines = lines(unzip(result.zipBytes).getValue("transfers.csv"))
        val sameCols = csvColumns(lines[1])
        val crossCols = csvColumns(lines[2])

        assertEquals("50", sameCols[7])
        assertEquals("USD", sameCols[8])

        assertEquals("55", crossCols[7])
        assertEquals("EUR", crossCols[8])
    }

    @Test
    fun buildReportingZip_containsExactlyThreeCsvEntriesWithHeaders() {
        val jan5 = dateMillis(2026, Calendar.JANUARY, 5)

        val result = runExport(
            expenses = listOf(expense(id = 1, type = "Expense", date = jan5, amount = "10", currency = "USD")),
            incomes = listOf(expense(id = 2, type = "Income", date = jan5, amount = "20", currency = "USD")),
            transfers = listOf(transfer(id = 3, date = jan5, source = "Cash", destination = "Bank", amount = "30", currency = "USD")),
            period = TimeFilter.Month(2026, Calendar.JANUARY)
        )

        val entries = unzip(result.zipBytes)
        assertEquals(setOf("expenses.csv", "incomes.csv", "transfers.csv"), entries.keys)

        assertTrue(lines(entries.getValue("expenses.csv")).first().startsWith("Date,Type,Category"))
        assertTrue(lines(entries.getValue("incomes.csv")).first().startsWith("Date,Type,Category"))
        assertTrue(lines(entries.getValue("transfers.csv")).first().startsWith("Date,SourceAccount,DestinationAccount"))
    }

    private fun runExport(
        expenses: List<Expense> = emptyList(),
        incomes: List<Expense> = emptyList(),
        transfers: List<TransferHistory> = emptyList(),
        accounts: List<Account> = listOf(
            Account(id = 1, name = "Cash", currency = "USD", balance = BigDecimal.ZERO),
            Account(id = 2, name = "Bank", currency = "USD", balance = BigDecimal.ZERO)
        ),
        debts: List<Debt> = emptyList(),
        keywords: Map<Int, List<String>> = emptyMap(),
        period: TimeFilter
    ): CsvExportResult {
        val input = CsvExportInput(
            expenses = expenses,
            incomes = incomes,
            transfers = transfers,
            accounts = accounts,
            debts = debts,
            expenseKeywords = keywords,
            paymentsByDebtId = (expenses + incomes)
                .filter { it.relatedDebtId != null }
                .groupBy { it.relatedDebtId!! }
        )

        return kotlinx.coroutines.runBlocking {
            service.buildReportingZip(
                period = period,
                input = input,
                defaultCurrencyAtExport = "USD",
                convertToDefaultAtExport = { amount, currency, _, _, _ ->
                    when (currency) {
                        "USD" -> amount
                        "EUR" -> amount.multiply(BigDecimal("2"))
                        else -> null
                    }
                },
                convertAmountToTargetCurrency = { amount, sourceCurrency, targetCurrency, _ ->
                    if (sourceCurrency == targetCurrency) {
                        amount
                    } else if (sourceCurrency == "EUR" && targetCurrency == "USD") {
                        amount.multiply(BigDecimal("1.5"))
                    } else {
                        null
                    }
                }
            )
        }
    }

    private fun expense(
        id: Int,
        type: String,
        date: Long,
        amount: String,
        currency: String,
        relatedDebtId: Int? = null
    ): Expense {
        return Expense(
            id = id,
            account = "Cash",
            amount = BigDecimal(amount),
            currency = currency,
            category = "General",
            expenseDate = date,
            type = type,
            comment = "note",
            relatedDebtId = relatedDebtId
        )
    }

    private fun transfer(
        id: Int,
        date: Long,
        source: String,
        destination: String,
        amount: String,
        currency: String,
        destinationAmount: String? = null,
        destinationCurrency: String? = null
    ): TransferHistory {
        return TransferHistory(
            id = id,
            date = date,
            sourceAccount = source,
            destinationAccount = destination,
            amount = BigDecimal(amount),
            currency = currency,
            destinationAmount = destinationAmount?.let { BigDecimal(it) },
            destinationCurrency = destinationCurrency,
            comment = "memo"
        )
    }

    private fun dateMillis(year: Int, month: Int, day: Int): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.YEAR, year)
        c.set(Calendar.MONTH, month)
        c.set(Calendar.DAY_OF_MONTH, day)
        c.set(Calendar.HOUR_OF_DAY, 12)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun unzip(zipBytes: ByteArray): Map<String, String> {
        val result = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val text = zis.readBytes().toString(Charsets.UTF_8)
                result[entry.name] = text
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return result
    }

    private fun lines(csv: String): List<String> {
        return csv.split("\n").filter { it.isNotBlank() }
    }

    private fun csvColumns(row: String): List<String> {
        val columns = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < row.length) {
            val ch = row[i]
            if (ch == '"') {
                if (inQuotes && i + 1 < row.length && row[i + 1] == '"') {
                    current.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (ch == ',' && !inQuotes) {
                columns.add(current.toString())
                current.setLength(0)
            } else {
                current.append(ch)
            }
            i++
        }
        columns.add(current.toString())
        return columns
    }
}
