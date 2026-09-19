package app.mirro.android.domain.engine.container.storage

import android.content.Context
import app.mirro.android.domain.engine.container.model.VirtualRuntimeIdentity
import app.mirro.android.domain.model.StorageMetrics
import java.io.File
import java.util.UUID

/**
 * Manages isolated directory sandboxes on internal storage for virtualized clone instances.
 *
 * Physical Layout:
 * `/data/user/0/app.mirro.android/files/virtual/<cloneId>/`
 * ├── files/
 * ├── cache/
 * ├── databases/
 * ├── shared_prefs/
 * ├── code_cache/
 * ├── no_backup/
 * ├── webview/
 * └── tmp/
 */
class VirtualFileSystem(private val context: Context) {

    private val virtualRoot: File
        get() = File(context.filesDir, "virtual").also { if (!it.exists()) it.mkdirs() }

    /**
     * Initializes a fresh, isolated filesystem sandbox for a new clone instance.
     */
    fun createSandbox(cloneId: String, packageName: String, virtualUserId: Int = 0): VirtualRuntimeIdentity {
        val sandboxDir = File(virtualRoot, cloneId)
        val suffix = sanitizeWebViewSuffix(cloneId)

        val identity = VirtualRuntimeIdentity(
            cloneId = cloneId,
            originalPackageName = packageName,
            virtualUserId = virtualUserId,
            runtimeInstanceId = UUID.randomUUID().toString(),
            sandboxRootDir = sandboxDir,
            webViewDataDirectorySuffix = suffix
        )

        identity.getAllSandboxDirectories().forEach { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }

        return identity
    }

    /**
     * Retrieves runtime identity and paths for an existing clone instance.
     */
    fun getRuntimeIdentity(cloneId: String, packageName: String, virtualUserId: Int = 0): VirtualRuntimeIdentity {
        val sandboxDir = File(virtualRoot, cloneId)
        val suffix = sanitizeWebViewSuffix(cloneId)

        val identity = VirtualRuntimeIdentity(
            cloneId = cloneId,
            originalPackageName = packageName,
            virtualUserId = virtualUserId,
            runtimeInstanceId = cloneId,
            sandboxRootDir = sandboxDir,
            webViewDataDirectorySuffix = suffix
        )

        identity.getAllSandboxDirectories().forEach { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }

        return identity
    }

    /**
     * Completely wipes only the targeted clone's sandbox directory without affecting others.
     */
    fun deleteSandbox(cloneId: String): Boolean {
        val sandboxDir = File(virtualRoot, cloneId)
        return if (sandboxDir.exists()) {
            sandboxDir.deleteRecursively()
        } else {
            true
        }
    }

    /**
     * Calculates storage consumption breakdown for a specific clone sandbox.
     */
    fun calculateStorageUsage(cloneId: String): StorageMetrics {
        val sandboxDir = File(virtualRoot, cloneId)
        if (!sandboxDir.exists()) {
            return StorageMetrics(apkSizeBytes = 0L, dataSizeBytes = 0L, cacheSizeBytes = 0L)
        }

        val cacheDir = File(sandboxDir, "cache")
        val codeCacheDir = File(sandboxDir, "code_cache")
        val cacheBytes = calculateDirectorySize(cacheDir) + calculateDirectorySize(codeCacheDir)

        val totalBytes = calculateDirectorySize(sandboxDir)
        val dataBytes = (totalBytes - cacheBytes).coerceAtLeast(0L)

        return StorageMetrics(
            apkSizeBytes = 0L, // Virtual container shares base APK from OS install
            dataSizeBytes = dataBytes,
            cacheSizeBytes = cacheBytes
        )
    }

    private fun calculateDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            total += if (f.isDirectory) calculateDirectorySize(f) else f.length()
        }
        return total
    }

    private fun sanitizeWebViewSuffix(cloneId: String): String {
        // Android requires suffix to contain only alphanumeric and underscore characters, no slashes.
        val cleaned = cloneId.replace("-", "_").replace(Regex("[^a-zA-Z0-9_]"), "")
        val suffix = "mirro_$cleaned"
        return if (suffix.length > 30) suffix.take(30) else suffix
    }
}
