package app.mirro.android.domain.engine.container.model

import java.io.File

enum class SplitSourceKind {
    EXECUTABLE,
    RESOURCE_OR_CONFIGURATION
}

data class SplitSource(
    val path: String,
    val kind: SplitSourceKind,
    val available: Boolean = true
)

/**
 * Structural descriptor of an installed target application APK.
 *
 * Extracted by [app.mirro.android.domain.engine.container.inspector.ApkInspector]
 * to guide ClassLoader loading, native library resolution, and component hosting.
 */
data class ApkDescriptor(
    val packageName: String,
    val versionCode: Long = 1L,
    val versionName: String = "1.0",
    val baseApkPath: String = "",
    val splitApkPaths: List<String> = emptyList(),
    /** Split APKs that were verified to contain executable DEX entries. */
    val executableSplitApkPaths: List<String> = emptyList(),
    val nativeLibraryDir: String = "",
    val nativeLibraryInventory: List<String> = emptyList(),
    val targetNativeAbis: List<String> = emptyList(),
    val targetSdk: Int = 34,
    val minSdk: Int = 26,
    val processName: String? = null,
    val mainActivity: String? = null,
    val applicationClassName: String? = null,
    val declaredActivities: List<String> = emptyList(),
    val declaredServices: List<String> = emptyList(),
    val declaredProviders: List<String> = emptyList(),
    val declaredReceivers: List<String> = emptyList(),
    val requestedPermissions: List<String> = emptyList(),
    val supportedAbis: List<String> = emptyList(),
    val splitSources: List<SplitSource> = splitApkPaths.map { path ->
        SplitSource(path, SplitSourceKind.RESOURCE_OR_CONFIGURATION)
    }
) {
    val allApkPaths: List<String>
        get() = listOf(baseApkPath) + splitApkPaths

    val executableApkPaths: List<String>
        get() = listOf(baseApkPath) + executableSplitApkPaths

    val hasNativeLibraries: Boolean
        get() = File(nativeLibraryDir).exists() && (File(nativeLibraryDir).listFiles()?.isNotEmpty() == true)
}
