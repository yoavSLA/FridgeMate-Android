package com.project.fridgemate.utils

import android.content.Context
import com.project.fridgemate.R

/**
 * Utility to map raw error messages or codes to user-friendly strings.
 */
object ErrorMapper {

    fun mapToUserFriendly(context: Context, rawError: String?): String {
        if (rawError == null) return context.getString(R.string.error_generic)

        val errorLower = rawError.lowercase()
        return when {
            errorLower.contains("connect") || errorLower.contains("unknownhost") || 
            errorLower.contains("offline") || errorLower.contains("network") ||
            errorLower.contains("unable to reach") -> {
                context.getString(R.string.error_no_connection)
            }
            errorLower.contains("timeout") -> {
                context.getString(R.string.error_timeout)
            }
            errorLower.contains("server") || errorLower.contains("500") || 
            errorLower.contains("502") || errorLower.contains("503") ||
            errorLower.contains("kitchen") -> { // "kitchen" is in R.string.error_server
                context.getString(R.string.error_server)
            }
            errorLower.contains("unauthorized") || errorLower.contains("401") || 
            errorLower.contains("expired") || errorLower.contains("session") -> {
                context.getString(R.string.error_auth_expired)
            }
            errorLower.contains("too many") || errorLower.contains("429") || errorLower.contains("slow down") -> {
                context.getString(R.string.error_rate_limit)
            }
            errorLower.contains("illegalstate") || errorLower.contains("nullpointer") -> {
                context.getString(R.string.error_generic)
            }
            else -> rawError // Fallback to server message if it's already localized or specific
        }
    }

    /**
     * Checks if the mapped error message is the generic system error message.
     */
    fun isGeneric(context: Context, message: String): Boolean {
        return message == context.getString(R.string.error_generic)
    }
}