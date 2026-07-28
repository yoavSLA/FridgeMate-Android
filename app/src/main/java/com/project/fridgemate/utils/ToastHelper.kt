package com.project.fridgemate.utils

import android.content.Context
import android.widget.Toast

/**
 * Utility to ensure only one Toast is visible at a time and prevent 
 * redundant stacking of duplicate messages.
 */
object ToastHelper {
    private var currentToast: Toast? = null
    private var lastMessage: String? = null
    private var lastTimestamp: Long = 0

    // Cooldown for identical messages to prevent them from popping up 
    // immediately after one another.
    private const val DUPLICATE_COOLDOWN_MS = 4000L 

    fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        val now = System.currentTimeMillis()
        
        // If it's the exact same message, don't show it again if the previous 
        // one is either still showing or was just shown.
        if (message == lastMessage && (now - lastTimestamp) < DUPLICATE_COOLDOWN_MS) {
            return
        }

        // If it's a DIFFERENT message, we cancel the current one immediately 
        // to show the new one.
        currentToast?.cancel()
        
        lastMessage = message
        lastTimestamp = now
        
        // Use applicationContext to avoid leaking activity contexts
        currentToast = Toast.makeText(context.applicationContext, message, duration).apply {
            show()
        }
    }
}