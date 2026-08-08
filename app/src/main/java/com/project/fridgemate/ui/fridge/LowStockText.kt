package com.project.fridgemate.ui.fridge

import android.content.Context
import com.project.fridgemate.R
import kotlin.math.roundToInt

/** Urgency label shared by the per-item popup and the full low-stock list. */
fun lowStockDaysText(context: Context, daysOfSupply: Double?): String = when {
    daysOfSupply == null -> context.getString(R.string.low_stock_no_suggestion)
    daysOfSupply < 1 -> context.getString(R.string.low_stock_under_a_day)
    else -> {
        val days = daysOfSupply.roundToInt()
        context.resources.getQuantityString(R.plurals.low_stock_days_left, days, days)
    }
}
