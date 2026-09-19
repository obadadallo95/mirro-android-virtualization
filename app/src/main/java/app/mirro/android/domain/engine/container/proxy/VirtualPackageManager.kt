package app.mirro.android.domain.engine.container.proxy

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import app.mirro.android.domain.engine.container.model.ApkDescriptor
import app.mirro.android.domain.engine.container.model.VirtualRuntimeIdentity

/**
 * Virtualized package manager proxy providing consistent identity and component
 * resolution for applications running within the Mirro container sandbox.
 */
class VirtualPackageManager(
    private val hostPackageManager: PackageManager,
    private val descriptor: ApkDescriptor,
    private val identity: VirtualRuntimeIdentity
) {

    fun getPackageName(): String = descriptor.packageName

    fun getApplicationInfo(flags: Int): ApplicationInfo {
        val appInfo = try {
            val original = hostPackageManager.getApplicationInfo(descriptor.packageName, flags)
            ApplicationInfo(original)
        } catch (_: Exception) {
            ApplicationInfo().apply {
                packageName = descriptor.packageName
                className = descriptor.applicationClassName
                name = descriptor.applicationClassName
            }
        }

        // Sanitize paths to point to container sandbox and APK paths
        appInfo.packageName = descriptor.packageName
        appInfo.sourceDir = descriptor.baseApkPath
        appInfo.publicSourceDir = descriptor.baseApkPath
        if (descriptor.splitApkPaths.isNotEmpty()) {
            appInfo.splitSourceDirs = descriptor.splitApkPaths.toTypedArray()
            appInfo.splitPublicSourceDirs = descriptor.splitApkPaths.toTypedArray()
        }
        appInfo.nativeLibraryDir = descriptor.nativeLibraryDir
        appInfo.targetSdkVersion = descriptor.targetSdk
        appInfo.minSdkVersion = descriptor.minSdk
        appInfo.dataDir = identity.sandboxRootDir.absolutePath

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appInfo.deviceProtectedDataDir = identity.sandboxRootDir.absolutePath
        }

        return appInfo
    }

    fun getPackageInfo(flags: Int): PackageInfo {
        return try {
            val original = hostPackageManager.getPackageInfo(descriptor.packageName, flags)
            PackageInfo().apply {
                packageName = original.packageName
                versionName = original.versionName
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    longVersionCode = original.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    versionCode = original.versionCode
                }
                applicationInfo = getApplicationInfo(flags)
                activities = original.activities
                services = original.services
                providers = original.providers
                receivers = original.receivers
                permissions = original.permissions
                requestedPermissions = original.requestedPermissions
            }
        } catch (_: Exception) {
            PackageInfo().apply {
                packageName = descriptor.packageName
                versionName = descriptor.versionName
                applicationInfo = getApplicationInfo(flags)
            }
        }
    }

    fun resolveActivity(intent: Intent, flags: Int): ResolveInfo? {
        val targetPackage = intent.`package` ?: intent.component?.packageName
        if (targetPackage == null || targetPackage == descriptor.packageName) {
            if (intent.component != null) {
                return ResolveInfo().apply {
                    activityInfo = ActivityInfo().apply {
                        name = intent.component!!.className
                        packageName = descriptor.packageName
                        applicationInfo = getApplicationInfo(flags)
                    }
                }
            }
        }
        return hostPackageManager.resolveActivity(intent, flags)
    }

    fun checkPermission(permName: String, pkgName: String): Int {
        return if (pkgName == descriptor.packageName && descriptor.requestedPermissions.contains(permName)) {
            hostPackageManager.checkPermission(permName, hostPackageManager.toString())
        } else {
            hostPackageManager.checkPermission(permName, pkgName)
        }
    }
}

