package com.project.fridgemate.utils

import androidx.annotation.StringRes
import com.project.fridgemate.R

/**
 * What the user can do about a failed scan. A rejected photo needs a new one,
 * while a transient failure can be retried with the same image.
 */
enum class ScanRecovery { RETRY, NEW_PHOTO, NONE }

enum class ScanError(@get:StringRes val messageRes: Int, val recovery: ScanRecovery) {
    NOT_A_FRIDGE(R.string.scan_error_not_a_fridge, ScanRecovery.NEW_PHOTO),
    TOO_BLURRY(R.string.scan_error_too_blurry, ScanRecovery.NEW_PHOTO),
    TOO_DARK(R.string.scan_error_too_dark, ScanRecovery.NEW_PHOTO),
    NO_ITEMS_DETECTED(R.string.scan_error_no_items, ScanRecovery.NEW_PHOTO),
    UNREADABLE_IMAGE(R.string.scan_error_unreadable_image, ScanRecovery.NEW_PHOTO),
    IMAGE_TOO_LARGE(R.string.scan_error_image_too_large, ScanRecovery.NEW_PHOTO),
    TIMEOUT(R.string.scan_error_timeout, ScanRecovery.RETRY),
    RATE_LIMIT(R.string.scan_error_rate_limit, ScanRecovery.RETRY),
    OFFLINE(R.string.scan_error_offline, ScanRecovery.RETRY),
    NO_FRIDGE(R.string.error_no_active_fridge, ScanRecovery.NONE),
    UNKNOWN(R.string.error_scan_failed, ScanRecovery.RETRY)
}

/**
 * The scan endpoint reports why an image was rejected, but the API error handler
 * forwards only the message text, so the reason has to be recovered from it.
 */
object ScanErrorMapper {

    fun fromMessage(rawError: String?): ScanError {
        val message = rawError?.lowercase() ?: return ScanError.UNKNOWN
        return when {
            message.contains("look like a fridge") -> ScanError.NOT_A_FRIDGE
            message.contains("too blurry") -> ScanError.TOO_BLURRY
            message.contains("too dark") -> ScanError.TOO_DARK
            message.contains("rate limit") || message.contains("quota") ||
                message.contains("429") || message.contains("too many") -> ScanError.RATE_LIMIT
            message.contains("file too large") ||
                message.contains("limit_file_size") -> ScanError.IMAGE_TOO_LARGE
            message.contains("images are allowed") ||
                message.contains("file type") -> ScanError.UNREADABLE_IMAGE
            message.contains("no active fridge") -> ScanError.NO_FRIDGE
            message.contains("unable to connect") || message.contains("failed to connect") ||
                message.contains("unable to resolve host") || message.contains("unknownhost") ||
                message.contains("network") || message.contains("offline") -> ScanError.OFFLINE
            message.contains("timeout") || message.contains("timed out") -> ScanError.TIMEOUT
            else -> ScanError.UNKNOWN
        }
    }
}
