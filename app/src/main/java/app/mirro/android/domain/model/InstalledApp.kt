package app.mirro.android.domain.model

import android.graphics.drawable.Drawable

/**
 * Represents an installed Android application discovered on the physical/virtual device.
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val targetSdkVersion: Int,
    val installTimeMillis: Long,
    val compatibilityStatus: CompatibilityStatus = CompatibilityStatus.UNKNOWN,
    val iconDrawable: Drawable? = null
)
