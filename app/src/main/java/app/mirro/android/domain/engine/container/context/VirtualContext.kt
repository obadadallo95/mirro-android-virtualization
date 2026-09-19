package app.mirro.android.domain.engine.container.context

import android.app.Application
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
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
import app.mirro.android.domain.engine.container.proxy.VirtualServiceManager
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

    /** Providers created by the container before the target Application.onCreate phase. */
    internal val targetProviders = mutableListOf<ContentProvider>()

    /**
     * Some framework APIs accept an explicit package name and enforce that it belongs to the
     * calling UID. During Application.attachBaseContext(), target applications may call such
     * APIs with their original package name even though the process is hosted by Mirro. Expose
     * the host identity only for that narrow bootstrap window; normal target identity remains
     * visible everywhere else.
     */
    @Volatile
    private var hostIdentityForSystemCalls = false

    @Volatile
    private var virtualAppWidgetManager: Any? = null

    /**
     * Target services cannot be handed to ActivityManager directly: the process is owned by
     * Mirro, so Android rejects non-exported target services as a cross-UID start. Keep target
     * services inside the same virtual process instead.
     */
    private val virtualServiceManager: VirtualServiceManager by lazy {
        VirtualServiceManager(
            hostContext = baseContext,
            virtualContext = this,
            runtime = runtime
        )
    }

    internal fun <T> withHostIdentityForSystemCalls(block: () -> T): T {
        hostIdentityForSystemCalls = true
        return try {
            block()
        } finally {
            hostIdentityForSystemCalls = false
        }
    }

    val virtualPackageManager = VirtualPackageManager(
        hostPackageManager = base.packageManager,
        descriptor = runtime.descriptor,
        identity = identity,
        hostPackageName = base.packageName
    )

    override fun getApplicationContext(): Context {
        return targetApplication ?: this
    }

    override fun getPackageName(): String = if (hostIdentityForSystemCalls) {
        baseContext.packageName
    } else {
        identity.originalPackageName
    }

    /** System-service attribution must use the package owned by this process's UID. */
    override fun getOpPackageName(): String = baseContext.opPackageName

    override fun getPackageCodePath(): String = runtime.descriptor.baseApkPath

    override fun getPackageResourcePath(): String = runtime.descriptor.baseApkPath

    override fun getApplicationInfo(): ApplicationInfo {
        return virtualPackageManager.getApplicationInfo(0)
    }

    override fun getPackageManager(): PackageManager {
        return virtualPackageManager
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
        if (name == APPWIDGET_SERVICE) {
            virtualAppWidgetManager?.let { return it }
            synchronized(this) {
                virtualAppWidgetManager?.let { return it }
                VirtualAppWidgetManagerFactory.create(this)?.let {
                    virtualAppWidgetManager = it
                    return it
                }
            }
        }
        return serviceProxyRegistry.getService(name)
    }

    override fun startService(service: Intent): ComponentName? {
        return if (isTargetComponentIntent(service)) {
            virtualServiceManager.startService(service, foreground = false)
        } else {
            baseContext.startService(service)
        }
    }

    override fun startForegroundService(service: Intent): ComponentName? {
        return if (isTargetComponentIntent(service)) {
            virtualServiceManager.startService(service, foreground = true)
        } else {
            baseContext.startForegroundService(service)
        }
    }

    override fun stopService(service: Intent): Boolean {
        return if (isTargetComponentIntent(service)) {
            virtualServiceManager.stopService(service)
        } else {
            baseContext.stopService(service)
        }
    }

    override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
        return if (isTargetComponentIntent(service)) {
            virtualServiceManager.bindService(service, conn, flags)
        } else {
            baseContext.bindService(service, conn, flags)
        }
    }

    override fun bindService(
        service: Intent,
        conn: ServiceConnection,
        flags: Context.BindServiceFlags
    ): Boolean {
        return if (isTargetComponentIntent(service)) {
            // The public android.jar omits the hidden BindServiceFlags value accessor. The
            // local host does not use system binding priority flags, so preserve the overload
            // while treating its value as zero.
            virtualServiceManager.bindService(service, conn, 0)
        } else {
            baseContext.bindService(service, conn, flags)
        }
    }

    override fun unbindService(conn: ServiceConnection) {
        if (virtualServiceManager.hasBinding(conn)) {
            virtualServiceManager.unbindService(conn)
        } else {
            baseContext.unbindService(conn)
        }
    }

    private fun isTargetComponentIntent(intent: Intent): Boolean {
        val componentPackage = intent.component?.packageName
        return componentPackage == identity.originalPackageName ||
            intent.`package` == identity.originalPackageName
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
