package app.mirro.android.data.repository

import android.content.Context
import app.mirro.android.domain.model.CloneInstance
import app.mirro.android.domain.model.StorageMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Storage metrics inspection repository.
 *
 * Measures actual base application APK payload size from the device file system.
 * For unmeasured isolated sandbox data and cache where real runtime isolation has not yet run,
 * values are explicitly returned as null (unavailable) or marked as estimates.
 * Never displays fabricated fake numbers as measured reality.
 */
class StorageRepository(
    private val context: Context
) {
    suspend fun getStorageMetrics(instance: CloneInstance): StorageMetrics = withContext(Dispatchers.IO) {
        var apkSize: Long? = null
        var isApkEstimated = false

        try {
            val appInfo = context.packageManager.getApplicationInfo(instance.originalPackageName, 0)
            val apkFile = File(appInfo.sourceDir)
            if (apkFile.exists()) {
                apkSize = apkFile.length()
            }
        } catch (_: Exception) {
            // If appInfo could not be inspected, do not invent 42MB. Keep as null.
            apkSize = null
            isApkEstimated = false
        }

        // Real measured isolated data: if recorded in database as > 0 from a real sync, use it.
        // Otherwise, mark as null (unavailable until isolation engine executes and measures)
        val dataSize: Long? = if (instance.storageSizeBytes > 0) {
            instance.storageSizeBytes
        } else {
            null // Unavailable until real isolation is active
        }

        // Cache size: unavailable until runtime sandbox reports cache directory usage
        val cacheSize: Long? = null

        StorageMetrics(
            apkSizeBytes = apkSize,
            dataSizeBytes = dataSize,
            cacheSizeBytes = cacheSize,
            isApkEstimated = isApkEstimated,
            isDataEstimated = false,
            isCacheEstimated = false
        )
    }
}
