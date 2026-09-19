package app.mirro.android.data.repository

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import app.mirro.android.MainActivity
import app.mirro.android.domain.model.CloneInstance

/**
 * Pinned launcher shortcut manager for clone instances.
 */
class ShortcutRepository(
    private val context: Context
) {
    fun isRequestPinShortcutSupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            shortcutManager?.isRequestPinShortcutSupported == true
        } else {
            false
        }
    }

    fun requestPinShortcut(instance: CloneInstance): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return false
            if (!shortcutManager.isRequestPinShortcutSupported) return false

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("EXTRA_CLONE_ID", instance.id)
                putExtra("EXTRA_PACKAGE_NAME", instance.originalPackageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            // Create badged icon
            val baseDrawable = try {
                context.packageManager.getApplicationIcon(instance.originalPackageName)
            } catch (_: Exception) {
                null
            }

            val iconBitmap = createBadgedBitmap(baseDrawable, instance.badgeColorHex, instance.badgeSymbol)
            val shortcutIcon = Icon.createWithBitmap(iconBitmap)

            val shortcut = ShortcutInfo.Builder(context, "clone_${instance.id}")
                .setShortLabel(instance.customName)
                .setLongLabel("${instance.customName} (${instance.originalAppLabel})")
                .setIcon(shortcutIcon)
                .setIntent(launchIntent)
                .build()

            val successCallback = PendingIntent.getActivity(
                context,
                instance.id.hashCode(),
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            return shortcutManager.requestPinShortcut(shortcut, successCallback.intentSender)
        }
        return false
    }

    private fun createBadgedBitmap(baseDrawable: Drawable?, colorHex: String, symbol: String): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (baseDrawable != null) {
            baseDrawable.setBounds(0, 0, size, size)
            baseDrawable.draw(canvas)
        } else {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.parseColor("#3B52C4")
            }
            canvas.drawRoundRect(RectF(8f, 8f, size - 8f, size - 8f), 32f, 32f, bgPaint)
        }

        // Draw badge circle at bottom right
        val badgeRadius = 36f
        val badgeCenterX = size - badgeRadius - 4f
        val badgeCenterY = size - badgeRadius - 4f

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = try {
                android.graphics.Color.parseColor(colorHex)
            } catch (_: Exception) {
                android.graphics.Color.parseColor("#10B981")
            }
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }

        canvas.drawCircle(badgeCenterX, badgeCenterY, badgeRadius, badgePaint)
        canvas.drawCircle(badgeCenterX, badgeCenterY, badgeRadius, borderPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 28f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val textY = badgeCenterY - ((textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText(symbol.take(2), badgeCenterX, textY, textPaint)

        return bitmap
    }
}
