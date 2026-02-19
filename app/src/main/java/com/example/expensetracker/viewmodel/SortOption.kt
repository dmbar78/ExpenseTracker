package com.example.expensetracker.viewmodel

import androidx.annotation.StringRes
import com.example.expensetracker.R

enum class SortOption(@StringRes val displayNameRes: Int) {
    DATE(R.string.sort_by_date),
    AMOUNT(R.string.sort_by_amount)
}
