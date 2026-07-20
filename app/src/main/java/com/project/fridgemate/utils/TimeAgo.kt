package com.project.fridgemate.utils

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

    fun format(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        val date = runCatching { isoParser.parse(iso) }.getOrNull() ?: return ""
        return format(date.time)
    }

    private fun format(timestampMillis: Long): String {
        val diff = System.currentTimeMillis() - timestampMillis
        if (diff < TimeUnit.MINUTES.toMillis(1)) return "now"

        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        if (minutes < 60) return "${minutes}m"

        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        if (hours < 24) return "${hours}h"

        val days = TimeUnit.MILLISECONDS.toDays(diff)
        if (days < 7) return "${days}d"

        if (days < 30) return "${days / 7}w"
        if (days < 365) return "${days / 30}mo"
        return "${days / 365}y"
    }

    fun formatWithFallback(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        val date = runCatching { isoParser.parse(iso) }.getOrNull() ?: return ""
        val diff = System.currentTimeMillis() - date.time
        return if (TimeUnit.MILLISECONDS.toDays(diff) >= 365) {
            fallbackDateFormat.format(Date(date.time))
        } else {
            format(date.time)
        }
    }
}
