package app.mirro.android.domain.engine.container.framework

import app.mirro.android.domain.engine.container.model.VirtualRuntimeIdentity
import java.io.File

/** Single path authority for clone-local storage. Physical paths are never presented as guest paths. */
class VirtualStorageManager(private val identity: VirtualRuntimeIdentity) {
    fun filesDir(): File = identity.filesDir.ensure()
    fun cacheDir(): File = identity.cacheDir.ensure()
    fun codeCacheDir(): File = identity.codeCacheDir.ensure()
    fun noBackupFilesDir(): File = identity.noBackupDir.ensure()
    fun databasesDir(): File = identity.databasesDir.ensure()
    fun sharedPreferencesDir(): File = identity.sharedPrefsDir.ensure()
    fun deviceProtectedDataDir(): File = identity.sandboxRootDir.ensure()
    fun credentialProtectedDataDir(): File = identity.sandboxRootDir.ensure()
    fun externalFilesDir(base: File?, type: String?): File = File(base ?: filesDir(), "external_files/${type ?: "root"}").ensure()
    fun externalCacheDir(base: File?): File = File(base ?: cacheDir(), "external_cache").ensure()
    fun database(name: String): File = File(databasesDir(), name)
    fun namedDir(name: String): File = File(identity.sandboxRootDir, "app_$name").ensure()

    private fun File.ensure(): File { if (!exists()) mkdirs(); return this }
}
