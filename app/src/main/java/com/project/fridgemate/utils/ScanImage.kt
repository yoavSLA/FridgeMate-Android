package com.project.fridgemate.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

/**
 * Normalises a scan photo before upload. The gallery picker hands back any
 * image type at full camera resolution, while the scan endpoint accepts only
 * JPEG, PNG and WebP up to 10 MB.
 */
object ScanImage {

    const val MIME_TYPE = "image/jpeg"

    private const val MAX_DIMENSION = 1600
    private const val JPEG_QUALITY = 85

    fun fromBitmap(bitmap: Bitmap): ByteArray = compress(downscale(bitmap))

    /** Returns null when the image cannot be decoded on this device. */
    fun fromUri(context: Context, uri: Uri): ByteArray? {
        val bitmap = decode(context, uri) ?: return null
        return compress(downscale(bitmap))
    }

    private fun decode(context: Context, uri: Uri): Bitmap? {
        return try {
            // Reads outWidth/outHeight only; decodeStream itself returns null here.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Keeps the full-size bitmap out of memory while decoding. */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sampleSize = 1
        while (width / (sampleSize * 2) >= MAX_DIMENSION || height / (sampleSize * 2) >= MAX_DIMENSION) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        val longestSide = maxOf(bitmap.width, bitmap.height)
        if (longestSide <= MAX_DIMENSION) return bitmap

        val scale = MAX_DIMENSION.toFloat() / longestSide
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true
        )
    }

    private fun compress(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        return stream.toByteArray()
    }
}
