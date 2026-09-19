package app.mirro.android.domain.engine.container.model

import java.io.File

/**
 * Unique runtime identity for a virtualized container instance.
 *
 * Provides distinct sandbox paths, virtual user IDs, and WebView directory suffixes
 * to ensure total separation from the host app and from other clones.
 */
data class VirtualRuntimeIdentity(
    val cloneId: String,
    val originalPackageName: String,
    val virtualUserId: Int,
    val runtimeInstanceId: String,
    val sandboxRootDir: File,
    val webViewDataDirectorySuffix: String
) {
    val filesDir: File get() = File(sandboxRootDir, "files")
    val cacheDir: File get() = File(sandboxRootDir, "cache")
    val databasesDir: File get() = File(sandboxRootDir, "databases")
    val sharedPrefsDir: File get() = File(sandboxRootDir, "shared_prefs")
    val codeCacheDir: File get() = File(sandboxRootDir, "code_cache")
    val noBackupDir: File get() = File(sandboxRootDir, "no_backup")
    val webViewDir: File get() = File(sandboxRootDir, "webview")
    val tmpDir: File get() = File(sandboxRootDir, "tmp")

    fun getAllSandboxDirectories(): List<File> = listOf(
        sandboxRootDir,
        filesDir,
        cacheDir,
        databasesDir,
        sharedPrefsDir,
        codeCacheDir,
        noBackupDir,
        webViewDir,
        tmpDir
    )
}
