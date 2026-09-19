package com.example.data.repository

import android.content.Context
import com.example.domain.model.CloneInstance
import com.example.domain.model.StorageMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Storage metrics inspection repository.
 *
 * Computes base application APK payload size from device file system
 * and projects isolated instance sandbox data footprint.
 */
class StorageRepository(
    private val context: Context
) {
    suspend fun getStorageMetrics(instance: CloneInstance): StorageMetrics = withContext(Dispatchers.IO) {
        var apkSize = 0L
        try {
            val appInfo = context.packageManager.getApplicationInfo(instance.originalPackageName, 0)
            val apkFile = File(appInfo.sourceDir)
            if (apkFile.exists()) {
                apkSize = apkFile.length()
            }
        } catch (_: Exception) {
            apkSize = 42L * 1024 * 1024 // Fallback 42MB estimate
        }

        // Isolated data estimate based on active state or recorded bytes
        val dataSize = if (instance.storageSizeBytes > 0) {
            instance.storageSizeBytes
        } else {
            // Baseline sandbox directory space (databases, shared_prefs, files)
            12L * 1024 * 1024
        }

        val cacheSize = if (instance.isFrozen) {
            256L * 1024
        } else {
            4L * 1024 * 1024
        }

        StorageMetrics(
            apkSizeBytes = apkSize,
            dataSizeBytes = dataSize,
            cacheSizeBytes = cacheSize,
            totalBytes = apkSize + dataSize + cacheSize
        )
    }
}
