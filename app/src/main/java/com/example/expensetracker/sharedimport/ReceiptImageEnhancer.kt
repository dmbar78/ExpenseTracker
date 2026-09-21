package com.example.expensetracker.sharedimport

import android.graphics.Bitmap
import android.graphics.Color

internal fun enhanceReceiptBitmap(source: Bitmap): Bitmap {
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)

    val luminance = IntArray(pixels.size)
    val histogram = IntArray(256)
    pixels.forEachIndexed { index, color ->
        val value = (Color.red(color) * 77 + Color.green(color) * 150 + Color.blue(color) * 29) shr 8
        luminance[index] = value
        histogram[value]++
    }
    val (low, high) = calculateContrastBounds(histogram, pixels.size)
    val stretched = IntArray(pixels.size) { index ->
        ((luminance[index] - low) * 255 / (high - low)).coerceIn(0, 255)
    }
    val output = IntArray(pixels.size)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val index = y * width + x
            val value = if (x == 0 || y == 0 || x == width - 1 || y == height - 1) {
                stretched[index]
            } else {
                val neighbors = stretched[index - 1] + stretched[index + 1] +
                    stretched[index - width] + stretched[index + width]
                (stretched[index] * 2 - neighbors / 4).coerceIn(0, 255)
            }
            output[index] = Color.rgb(value, value, value)
        }
    }

    return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
}

internal fun calculateContrastBounds(histogram: IntArray, pixelCount: Int): Pair<Int, Int> {
    require(histogram.size == 256 && pixelCount > 0)
    val clippedPixels = maxOf(1, (pixelCount * 0.01f).toInt())
    var cumulative = 0
    var low = 0
    while (low < 255 && cumulative + histogram[low] <= clippedPixels) {
        cumulative += histogram[low++]
    }
    cumulative = 0
    var high = 255
    while (high > 0 && cumulative + histogram[high] <= clippedPixels) {
        cumulative += histogram[high--]
    }
    if (high - low < 32) {
        val midpoint = (low + high) / 2
        low = (midpoint - 16).coerceAtLeast(0)
        high = (midpoint + 16).coerceAtMost(255)
    }
    return low to maxOf(low + 1, high)
}