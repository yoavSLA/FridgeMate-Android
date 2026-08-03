package com.project.fridgemate.utils

import com.project.fridgemate.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object TimeAgo {

    private val isoParser: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private val fallbackDateFormat: SimpleDateFormat by lazy {
        SimpleDateFormat("MMM d", Locale.getDefault())
    }

    fun format(context: android.content.Context, iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        val date = runCatching { isoParser.parse(iso) }.getOrNull() ?: return ""
        return format(context, date.time)
    }

    private fun format(context: android.content.Context, timestampMillis: Long): String {
        val diff = System.currentTimeMillis() - timestampMillis
        if (diff < TimeUnit.MINUTES.toMillis(1)) return context.getString(R.string.time_now)

        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        if (minutes < 60) return context.getString(R.string.time_min, minutes)

        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        if (hours < 24) return context.getString(R.string.time_hour, hours)

        val days = TimeUnit.MILLISECONDS.toDays(diff)
        if (days < 7) return context.getString(R.string.time_day, days)

        if (days < 30) return context.getString(R.string.time_week, days / 7)
        if (days < 365) return context.getString(R.string.time_month, days / 30)
        return context.getString(R.string.time_year, days / 365)
    }

    fun formatWithFallback(context: android.content.Context, iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        val date = runCatching { isoParser.parse(iso) }.getOrNull() ?: return ""
        val diff = System.currentTimeMillis() - date.time
        return if (TimeUnit.MILLISECONDS.toDays(diff) >= 365) {
            fallbackDateFormat.format(Date(date.time))
        } else {
            format(context, date.time)
        }
    }
}
