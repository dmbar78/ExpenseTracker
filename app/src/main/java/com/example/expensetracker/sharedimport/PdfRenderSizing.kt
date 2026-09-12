package com.example.expensetracker.sharedimport

data class PdfRenderSize(val width: Int, val height: Int)

internal fun calculatePdfRenderSize(
    pageWidth: Int,
    pageHeight: Int,
    targetLongEdge: Int = 2048
): PdfRenderSize {
    require(pageWidth > 0 && pageHeight > 0 && targetLongEdge > 0)
    val scale = targetLongEdge.toFloat() / maxOf(pageWidth, pageHeight)
    return PdfRenderSize(
        width = maxOf(1, (pageWidth * scale).toInt()),
        height = maxOf(1, (pageHeight * scale).toInt())
    )
}