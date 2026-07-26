package com.project.fridgemate.utils

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.project.fridgemate.R

object AvatarHelper {

    private val AVATAR_COLOR_RES_IDS = intArrayOf(
        R.color.avatar_1, R.color.avatar_2, R.color.avatar_3, R.color.avatar_4,
        R.color.avatar_5, R.color.avatar_6, R.color.avatar_7, R.color.avatar_8,
        R.color.avatar_9, R.color.avatar_10, R.color.avatar_11, R.color.avatar_12,
        R.color.avatar_13, R.color.avatar_14, R.color.avatar_15, R.color.avatar_16,
        R.color.avatar_17
    )

    fun getInitials(name: String?): String {
        if (name.isNullOrBlank()) return "?"
        val parts = name.trim().split(Regex("\\s+"))
        return when {
            parts.size >= 2 -> {
                val first = parts[0].take(1).uppercase()
                val second = parts[1].take(1).uppercase()
                "$first$second"
            }
            parts.isNotEmpty() -> {
                parts[0].take(1).uppercase()
            }
            else -> "?"
        }
    }

    private fun getColorForName(context: Context, name: String?): Int {
        if (name.isNullOrBlank()) return ContextCompat.getColor(context, AVATAR_COLOR_RES_IDS[0])
        val hash = name.hashCode()
        val index = Math.abs(hash) % AVATAR_COLOR_RES_IDS.size
        return ContextCompat.getColor(context, AVATAR_COLOR_RES_IDS[index])
    }

    fun createPlaceholder(context: Context, name: String?, sizePx: Int = 200): Drawable {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Draw background circle
        paint.color = getColorForName(context, name)
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

        // Draw text
        paint.color = Color.WHITE
        paint.textSize = sizePx / 2.5f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        val initials = getInitials(name)
        val xPos = canvas.width / 2f
        val yPos = (canvas.height / 2f - (paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(initials, xPos, yPos, paint)

        return BitmapDrawable(context.resources, bitmap)
    }
}
