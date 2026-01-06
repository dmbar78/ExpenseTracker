## Issue description:
Now trying to create expense record:
Partial: income to ABN 25 category default 22nd of January
parseTrailingSpokenDate: input='default 22nd of January' trimmed='default 22nd of January' lower='default 22nd of january' defaultMillis=1767634302663 (05.01.2026)
--- Month loop: month='january' index=0
Pattern1 <month> <day>: regex='\s+january\s+(\d{1,2})(?:st|nd|rd|th)?\s*$' match='null'
Pattern1b <month> <ordinal>: no match
Pattern2 <day> of <month>: regex='\s+(\d{1,2})(?:st|nd|rd|th)?\s+of\s+january\s*$' match=' 22nd of january'
Pattern2 parse: rawDay='22' parsedDay=22
Pattern2 MATCH: month='january' day=22 -> millis=1769076000000 (22.01.2026) strippedText='default'
opens EditExpenseScreen with "Category Not Found" message, though category "Default" exists.
But other try:
Partial: income to ABN 50 category income 21st of January
onResults received: null
parseTrailingSpokenDate: input='income 21st of January' trimmed='income 21st of January' lower='income 21st of january' defaultMillis=1767634476517 (05.01.2026)
--- Month loop: month='january' index=0
Pattern1 <month> <day>: regex='\s+january\s+(\d{1,2})(?:st|nd|rd|th)?\s*$' match='null'
Pattern1b <month> <ordinal>: no match
Pattern2 <day> of <month>: regex='\s+(\d{1,2})(?:st|nd|rd|th)?\s+of\s+january\s*$' match=' 21st of january'
Pattern2 parse: rawDay='21' parsedDay=21
Pattern2 MATCH: month='january' day=21 -> millis=1768989600000 (21.01.2026) strippedText='income'
works fine and creates expense record.
What could be the reason?

## Reason:
This is a race with the initial empty StateFlow value.

allCategories is a StateFlow(..., initialValue = emptyList()).
In processParsedExpense you do val categories = allCategories.first().
first() returns the first emission, which is often that initial emptyList(), before your init pre-population coroutine inserts "Default".
Result: categories.find { ... } returns null, so you navigate to EditExpenseScreen with Category Not Found.
Later, when categories have actually loaded/been inserted, the same flow works (your "income" category attempt happened after data was available).
## Fix: wait until categories are non-empty (since you always insert "Default"), instead of taking the initial empty emission.

// ...existing code...
private suspend fun processParsedExpense(parsedExpense: ParsedExpense) {
    val accounts = allAccounts.first() // keep as-is if you want to proceed even if empty
    val account = accounts.find { it.name.equals(parsedExpense.accountName, ignoreCase = true) }

    // Wait for categories to be loaded (Default is pre-populated)
    val categories = allCategories.first { it.isNotEmpty() }
    val category = categories.find { it.name.equals(parsedExpense.categoryName, ignoreCase = true) }

    if (account == null || category == null) {
        // ...existing code...
    }

    // ...existing code...
}
// ...existing code...