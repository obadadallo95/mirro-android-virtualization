package app.mirro.android.domain.engine.container.proxy

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import app.mirro.android.domain.engine.container.model.ApkDescriptor

/**
 * Virtualized package manager proxy providing consistent identity and component
 * resolution for applications running within the Mirro container sandbox.
 */
class VirtualPackageManager(
    private val hostPackageManager: PackageManager,
    private val descriptor: ApkDescriptor
) {

    fun getPackageName(): String = descriptor.packageName

    fun getApplicationInfo(flags: Int): ApplicationInfo {
        return try {
            hostPackageManager.getApplicationInfo(descriptor.packageName, flags)
        } catch (_: Exception) {
            ApplicationInfo().apply {
                packageName = descriptor.packageName
                sourceDir = descriptor.baseApkPath
                nativeLibraryDir = descriptor.nativeLibraryDir
                targetSdkVersion = descriptor.targetSdk
                minSdkVersion = descriptor.minSdk
            }
        }
    }

    fun getPackageInfo(flags: Int): PackageInfo {
        return try {
            hostPackageManager.getPackageInfo(descriptor.packageName, flags)
        } catch (_: Exception) {
            PackageInfo().apply {
                packageName = descriptor.packageName
                versionName = descriptor.versionName
                applicationInfo = getApplicationInfo(flags)
            }
        }
    }

    fun resolveActivity(intent: Intent, flags: Int): ResolveInfo? {
        // First check if target package matches
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
