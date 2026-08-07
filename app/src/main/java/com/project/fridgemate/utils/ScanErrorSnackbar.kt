package com.project.fridgemate.utils

import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.project.fridgemate.R

/**
 * Scan failures get a Snackbar instead of a toast: a scan is a slow, deliberate
 * action, so the result has to stay on screen until the user acts on it.
 */
object ScanErrorSnackbar {

    private const val MAX_LINES = 3

    fun show(root: View, error: ScanError, onRetry: () -> Unit, onNewPhoto: () -> Unit) {
        val snackbar = Snackbar.make(root, error.messageRes, Snackbar.LENGTH_INDEFINITE)
            .setTextMaxLines(MAX_LINES)
            .setActionTextColor(ContextCompat.getColor(root.context, R.color.accent_green))

        when (error.recovery) {
            ScanRecovery.RETRY -> snackbar.setAction(R.string.scan_action_retry) { onRetry() }
            ScanRecovery.NEW_PHOTO -> snackbar.setAction(R.string.scan_action_new_photo) { onNewPhoto() }
            ScanRecovery.NONE -> snackbar.duration = Snackbar.LENGTH_LONG
        }

        snackbar.show()
    }
}
