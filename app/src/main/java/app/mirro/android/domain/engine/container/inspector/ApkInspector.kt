package app.mirro.android.domain.engine.container.inspector

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import app.mirro.android.domain.engine.container.model.ApkDescriptor

/**
 * Discovers and inspects installed APK properties required for container isolation.
 */
class ApkInspector(private val context: Context) {

    fun inspect(packageName: String): ApkDescriptor? {
        return try {
            val pm = context.packageManager
            val flags = PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_PROVIDERS or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PERMISSIONS

            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, flags)
            }

            val appInfo = packageInfo.applicationInfo ?: return null

            val baseApkPath = appInfo.sourceDir ?: ""
            val splitApkPaths = appInfo.splitSourceDirs?.toList() ?: emptyList()
            val nativeLibDir = appInfo.nativeLibraryDir ?: ""

            val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                appInfo.minSdkVersion
            } else {
                21
            }

            // Determine launch intent & main activity
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            val mainActivity = launchIntent?.component?.className
                ?: packageInfo.activities?.firstOrNull()?.name

            val declaredActivities = packageInfo.activities?.map { it.name } ?: emptyList()
            val declaredServices = packageInfo.services?.map { it.name } ?: emptyList()
            val declaredProviders = packageInfo.providers?.map { it.name } ?: emptyList()
            val declaredReceivers = packageInfo.receivers?.map { it.name } ?: emptyList()
            val requestedPermissions = packageInfo.requestedPermissions?.toList() ?: emptyList()

            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }

            val abis = Build.SUPPORTED_ABIS?.toList() ?: emptyList()

            ApkDescriptor(
                packageName = packageName,
                versionCode = versionCode,
                versionName = packageInfo.versionName ?: "Unknown",
                baseApkPath = baseApkPath,
                splitApkPaths = splitApkPaths,
                nativeLibraryDir = nativeLibDir,
                targetSdk = appInfo.targetSdkVersion,
                minSdk = minSdk,
                mainActivity = mainActivity,
                applicationClassName = appInfo.className,
                declaredActivities = declaredActivities,
                declaredServices = declaredServices,
                declaredProviders = declaredProviders,
                declaredReceivers = declaredReceivers,
                requestedPermissions = requestedPermissions,
                supportedAbis = abis
            )
        } catch (e: Exception) {
            null
        }
    }
}
