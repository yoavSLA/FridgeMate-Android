package com.project.fridgemate.data.repository

import org.json.JSONObject

abstract class BaseRepository {

    protected fun parseError(errorBody: String?): String {
        if (errorBody.isNullOrBlank()) return "Something went wrong. Please try again."
        return try {
            val json = JSONObject(errorBody)
            json.optString("message", "Something went wrong. Please try again.")
        } catch (_: Exception) {
            errorBody
        }
    }

    protected fun networkErrorMessage(e: Exception): String {
        return if ((e is java.net.ConnectException) || (e is java.net.UnknownHostException)) {
            "Unable to connect to server. Please check your connection."
        } else {
            e.localizedMessage ?: "An unexpected error occurred."
        }
    }
}
