package app.mirro.android.domain.model

/**
 * Storage breakdown for an application instance.
 *
 * Real measurements from device storage when available;
 * null or marked unavailable when runtime isolation has not yet measured them.
 * Never displays fabricated numbers as measured facts.
 */
data class StorageMetrics(
    val apkSizeBytes: Long?,
    val dataSizeBytes: Long?,
    val cacheSizeBytes: Long?,
    val isApkEstimated: Boolean = false,
    val isDataEstimated: Boolean = false,
    val isCacheEstimated: Boolean = false
) {
    val totalBytes: Long?
        get() {
            val apk = apkSizeBytes ?: 0L
            val data = dataSizeBytes ?: 0L
            val cache = cacheSizeBytes ?: 0L
            return if (apkSizeBytes == null && dataSizeBytes == null && cacheSizeBytes == null) {
                null
            } else {
                apk + data + cache
            }
        }

    val isTotalEstimated: Boolean
        get() = isApkEstimated || isDataEstimated || isCacheEstimated

    companion object {
        fun formatBytes(bytes: Long?, isEstimated: Boolean = false): String {
            if (bytes == null || bytes < 0) return "Unavailable"
            if (bytes == 0L) return if (isEstimated) "~0 B" else "0 B"
            if (bytes < 1024) return if (isEstimated) "~$bytes B" else "$bytes B"
            val units = arrayOf("KB", "MB", "GB", "TB")
            var value = bytes.toDouble() / 1024.0
            var unitIndex = 0
            while (value >= 1024.0 && unitIndex < units.lastIndex) {
                value /= 1024.0
                unitIndex++
            }
            val formatted = String.format(java.util.Locale.US, "%.1f %s", value, units[unitIndex])
            return if (isEstimated) "~$formatted" else formatted
        }
    }
}
