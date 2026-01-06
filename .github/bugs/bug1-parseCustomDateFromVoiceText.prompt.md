## Issue description:
//For transfer transaction spoken input text is: "transfer from ABN to Dima 50 January 1st", but parsed results for date logged are:
//parseTrailingSpokenDate: input='st' trimmed='st' lower='st' defaultMillis=1767631147631 (05.01.2026) 
//and eventually destAccount is parsed as "Dima 50 January". What could be the reason?

## Reason:
//in "transfer from ABN to Dima 50 January 1st" your amountRegex finds two numeric matches in restAfterTo:
//
//50 (the transfer amount)
//1 (from 1st in the date)
//Because parseTransfer uses findAll(...).lastOrNull(), it picks 1 as the amount. That shifts the split point so destAccountStr becomes "Dima 50 January", and the leftover after the “amount” becomes "st", which is exactly what your log shows.
//
//Fix: strip the trailing spoken date before extracting the amount, then take the last numeric match from the stripped text.

// ...existing code...
private fun parseTransfer(input: String): ParsedTransfer? {
    val lowerInput = input.lowercase(Locale.ROOT)
    val transferIndex = lowerInput.indexOf("transfer from ")
    val toIndex = lowerInput.indexOf(" to ")

    if (transferIndex == -1 || toIndex == -1 || !(transferIndex < toIndex)) {
        return null
    }

    val sourceAccountStr = input.substring(transferIndex + 14, toIndex).trim()
    val restAfterTo = input.substring(toIndex + 4).trim()

    // 1) Parse (and remove) trailing date first so day numbers (e.g., "1st") don't get treated as amount
    val (restWithoutDate, transferDate) = parseTrailingSpokenDate(restAfterTo)

    // Regex that handles both dot and comma decimal separators, and thousand separators
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
// ...existing code...