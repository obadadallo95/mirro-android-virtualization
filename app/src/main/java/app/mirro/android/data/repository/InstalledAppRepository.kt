package app.mirro.android.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import app.mirro.android.domain.analyzer.CompatibilityAnalyzer
import app.mirro.android.domain.model.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Real Android installed applications discovery repository.
 *
 * Inspects device launchable activities using PackageManager and evaluates
 * isolation compatibility factors via [CompatibilityAnalyzer].
 */
class InstalledAppRepository(
    private val context: Context,
    private val analyzer: CompatibilityAnalyzer = CompatibilityAnalyzer()
) {
    private val packageManager: PackageManager = context.packageManager
    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: Flow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: Flow<Boolean> = _isLoading.asStateFlow()

    fun getPackageInfo(packageName: String): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun refreshInstalledApps() = withContext(Dispatchers.IO) {
        _isLoading.value = true
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val queryFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PackageManager.ResolveInfoFlags.of(0L)
            } else {
                0
            }

            val resolveInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(mainIntent, queryFlags as PackageManager.ResolveInfoFlags)
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(mainIntent, queryFlags as Int)
            }

            val ownPackageName = context.packageName
            val discoveredApps = mutableListOf<InstalledApp>()
            val seenPackages = mutableSetOf<String>()

            for (resolveInfo in resolveInfoList) {
                val pkgName = resolveInfo.activityInfo?.packageName ?: continue
                if (pkgName == ownPackageName || seenPackages.contains(pkgName)) continue
                seenPackages.add(pkgName)

                try {
                    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(0L))
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getPackageInfo(pkgName, 0)
                    }

                    val appInfo = packageInfo.applicationInfo
                    val label = resolveInfo.loadLabel(packageManager)?.toString()
                        ?: appInfo?.loadLabel(packageManager)?.toString()
                        ?: pkgName

                    val icon = resolveInfo.loadIcon(packageManager)
                        ?: appInfo?.loadIcon(packageManager)

                    val isSystem = if (appInfo != null) {
                        (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    } else false

                    val targetSdk = appInfo?.targetSdkVersion ?: 0
                    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toLong()
                    }

                    val compatReport = analyzer.analyze(packageInfo)

                    discoveredApps.add(
                        InstalledApp(
                            packageName = pkgName,
                            label = label,
                            versionName = packageInfo.versionName,
                            versionCode = versionCode,
                            isSystemApp = isSystem,
                            targetSdkVersion = targetSdk,
                            installTimeMillis = packageInfo.firstInstallTime,
                            compatibilityStatus = compatReport.status,
                            iconDrawable = icon
                        )
                    )
                } catch (_: Exception) {
                    // Skip packages that cannot be inspected due to Android OS visibility limits
                }
            }

            // Sort alphabetically by app label
            discoveredApps.sortBy { it.label.lowercase() }
            _installedApps.value = discoveredApps
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun getAppByPackage(packageName: String): InstalledApp? = withContext(Dispatchers.IO) {
        _installedApps.value.find { it.packageName == packageName } ?: run {
            try {
                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, 0)
                }
                val appInfo = packageInfo.applicationInfo
                val label = appInfo?.loadLabel(packageManager)?.toString() ?: packageName
                val icon = appInfo?.loadIcon(packageManager)
                val isSystem = if (appInfo != null) {
                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                } else false
                val targetSdk = appInfo?.targetSdkVersion ?: 0
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
                val compat = analyzer.analyze(packageInfo)
                InstalledApp(
                    packageName = packageName,
                    label = label,
                    versionName = packageInfo.versionName,
                    versionCode = versionCode,
                    isSystemApp = isSystem,
                    targetSdkVersion = targetSdk,
                    installTimeMillis = packageInfo.firstInstallTime,
                    compatibilityStatus = compat.status,
                    iconDrawable = icon
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
