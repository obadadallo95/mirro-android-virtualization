package app.mirro.android.domain.engine.container.context

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Looper
import app.mirro.android.domain.engine.container.loader.LoadedApkRuntime
import app.mirro.android.domain.engine.container.model.VirtualRuntimeIdentity
import app.mirro.android.domain.engine.container.proxy.ServiceProxyRegistry
import app.mirro.android.domain.engine.container.proxy.VirtualPackageManager
import java.io.File
import java.util.concurrent.Executor

/**
 * Virtualized ContextWrapper that redirects all app-facing file, database,
 * preference, resource, and package identity queries to the clone's private sandbox.
 */
class VirtualContext(
    base: Context,
    val identity: VirtualRuntimeIdentity,
    val runtime: LoadedApkRuntime,
    val serviceProxyRegistry: ServiceProxyRegistry = ServiceProxyRegistry(base)
) : ContextWrapper(base) {

    @Volatile
    var targetApplication: Application? = null

    val virtualPackageManager = VirtualPackageManager(
        hostPackageManager = base.packageManager,
        descriptor = runtime.descriptor,
        identity = identity
    )

    override fun getApplicationContext(): Context {
        return targetApplication ?: this
    }

    override fun getPackageName(): String = identity.originalPackageName

    override fun getOpPackageName(): String = identity.originalPackageName

    override fun getPackageCodePath(): String = runtime.descriptor.baseApkPath

    override fun getPackageResourcePath(): String = runtime.descriptor.baseApkPath

    override fun getApplicationInfo(): ApplicationInfo {
        return virtualPackageManager.getApplicationInfo(0)
    }

    override fun getPackageManager(): PackageManager {
        return baseContext.packageManager
    }

    override fun getClassLoader(): ClassLoader {
        return runtime.classLoader
    }

    override fun getResources(): Resources {
        return runtime.resources
    }

    override fun getAssets(): AssetManager {
        return runtime.resources.assets
    }

    override fun getMainLooper(): Looper {
        return baseContext.mainLooper
    }

    override fun getMainExecutor(): Executor {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            baseContext.mainExecutor
        } else {
            Executor { command -> android.os.Handler(baseContext.mainLooper).post(command) }
        }
    }

    override fun getContentResolver(): ContentResolver {
        return baseContext.contentResolver
    }

    override fun getFilesDir(): File {
        return identity.filesDir.also { if (!it.exists()) it.mkdirs() }
    }

    override fun getCacheDir(): File {
        return identity.cacheDir.also { if (!it.exists()) it.mkdirs() }
    }

    override fun getCodeCacheDir(): File {
        return identity.codeCacheDir.also { if (!it.exists()) it.mkdirs() }
    }

    override fun getNoBackupFilesDir(): File {
        return identity.noBackupDir.also { if (!it.exists()) it.mkdirs() }
    }

    override fun getDataDir(): File {
        return identity.sandboxRootDir.also { if (!it.exists()) it.mkdirs() }
    }

    override fun getDatabasePath(name: String): File {
        val dbDir = identity.databasesDir.also { if (!it.exists()) it.mkdirs() }
        return File(dbDir, name)
    }

    override fun getDir(name: String, mode: Int): File {
        val dir = File(identity.sandboxRootDir, "app_$name")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        val isolatedName = "mirro_${identity.cloneId}_$name"
        return baseContext.getSharedPreferences(isolatedName, mode)
    }

    override fun getSystemService(name: String): Any? {
        return serviceProxyRegistry.getService(name)
    }

    override fun getExternalFilesDir(type: String?): File? {
        val base = baseContext.getExternalFilesDir(type) ?: return getFilesDir()
        val isolated = File(base, "virtual_${identity.cloneId}")
        if (!isolated.exists()) isolated.mkdirs()
        return isolated
    }

    override fun getExternalCacheDir(): File? {
        val base = baseContext.externalCacheDir ?: return getCacheDir()
        val isolated = File(base, "virtual_${identity.cloneId}")
        if (!isolated.exists()) isolated.mkdirs()
        return isolated
    }

    override fun createPackageContext(packageName: String, flags: Int): Context {
        if (packageName == identity.originalPackageName) {
            return this
        }
        return baseContext.createPackageContext(packageName, flags)
    }

    override fun createConfigurationContext(overrideConfiguration: Configuration): Context {
        val newBase = baseContext.createConfigurationContext(overrideConfiguration)
        return VirtualContext(
            base = newBase,
            identity = identity,
            runtime = runtime,
            serviceProxyRegistry = serviceProxyRegistry
        ).also {
            it.targetApplication = this.targetApplication
        }
    }
}

