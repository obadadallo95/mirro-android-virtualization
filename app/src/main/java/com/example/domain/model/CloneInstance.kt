package com.example.domain.model

/**
 * Represents an isolated or configured cloned instance of an Android application.
 */
data class CloneInstance(
    val id: String,
    val originalPackageName: String,
    val originalAppLabel: String,
    val customName: String,
    val badgeColorHex: String,
    val badgeSymbol: String,
    val engineType: CloneEngineType,
    val isFrozen: Boolean = false,
    val isLocked: Boolean = false,
    val storageSizeBytes: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLaunchedAt: Long? = null
)

/**
 * Configuration payload used when initiating a new clone.
 */
data class CloneConfig(
    val originalPackageName: String,
    val originalAppLabel: String,
    val customName: String,
    val badgeColorHex: String,
    val badgeSymbol: String,
    val engineType: CloneEngineType,
    val isLocked: Boolean = false
)

/**
 * Storage breakdown for an isolated clone instance.
 */
data class StorageMetrics(
    val apkSizeBytes: Long,
    val dataSizeBytes: Long,
    val cacheSizeBytes: Long,
    val totalBytes: Long
) {
    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            if (bytes < 1024) return "$bytes B"
            val units = arrayOf("KB", "MB", "GB", "TB")
            var value = bytes.toDouble() / 1024.0
            var unitIndex = 0
            while (value >= 1024.0 && unitIndex < units.lastIndex) {
                value /= 1024.0
                unitIndex++
            }
            return String.format(java.util.Locale.US, "%.1f %s", value, units[unitIndex])
        }
    }
}
