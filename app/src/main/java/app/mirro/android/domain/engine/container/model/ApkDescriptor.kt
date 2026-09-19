package app.mirro.android.domain.engine.container.model

import java.io.File

/**
 * Structural descriptor of an installed target application APK.
 *
 * Extracted by [app.mirro.android.domain.engine.container.inspector.ApkInspector]
 * to guide ClassLoader loading, native library resolution, and component hosting.
 */
data class ApkDescriptor(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val baseApkPath: String,
    val splitApkPaths: List<String> = emptyList(),
    val nativeLibraryDir: String,
    val targetSdk: Int,
    val minSdk: Int,
    val mainActivity: String?,
    val applicationClassName: String?,
    val declaredActivities: List<String> = emptyList(),
    val declaredServices: List<String> = emptyList(),
    val declaredProviders: List<String> = emptyList(),
    val declaredReceivers: List<String> = emptyList(),
    val requestedPermissions: List<String> = emptyList(),
    val supportedAbis: List<String> = emptyList()
) {
    val allApkPaths: List<String>
        get() = listOf(baseApkPath) + splitApkPaths

    val hasNativeLibraries: Boolean
        get() = File(nativeLibraryDir).exists() && (File(nativeLibraryDir).listFiles()?.isNotEmpty() == true)
}
