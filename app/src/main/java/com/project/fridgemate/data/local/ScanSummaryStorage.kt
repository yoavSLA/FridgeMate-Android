package com.project.fridgemate.data.local

import android.content.Context
import com.google.gson.Gson
import com.project.fridgemate.data.remote.dto.ScanChangesDto

class ScanSummaryStorage(context: Context) {
    private val prefs = context.getSharedPreferences("scan_summary_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveLastScanSummary(summary: ScanChangesDto, createdAt: String) {
        prefs.edit().apply {
            putString(KEY_SUMMARY, gson.toJson(summary))
            putString(KEY_CREATED_AT, createdAt)
            apply()
        }
    }

    fun getLastScanSummary(): ScanChangesDto? {
        val json = prefs.getString(KEY_SUMMARY, null) ?: return null
        return try {
            gson.fromJson(json, ScanChangesDto::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getLastScanCreatedAt(): String? {
        return prefs.getString(KEY_CREATED_AT, null)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_SUMMARY = "last_scan_summary"
        private const val KEY_CREATED_AT = "last_scan_created_at"
    }
}
