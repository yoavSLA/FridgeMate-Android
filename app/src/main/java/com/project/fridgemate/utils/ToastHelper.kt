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

    private const val DUPLICATE_COOLDOWN_MS = 4000L 

    fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        val now = System.currentTimeMillis()
        
        if (message == lastMessage && (now - lastTimestamp) < DUPLICATE_COOLDOWN_MS) {
            return
        }

        currentToast?.cancel()
        
        lastMessage = message
        lastTimestamp = now
        
        currentToast = Toast.makeText(context.applicationContext, message, duration).apply {
            show()
        }
    }
}