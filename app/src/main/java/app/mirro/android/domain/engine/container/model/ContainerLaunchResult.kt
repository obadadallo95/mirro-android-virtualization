package app.mirro.android.domain.engine.container.model

/**
 * Structured status codes for container launch operations.
 */
enum class ContainerLaunchStatus {
    LAUNCH_SUCCESS,
    RUNTIME_NOT_READY,
    APK_NOT_FOUND,
    APK_LOAD_FAILED,
    CLASSLOADER_FAILED,
    RESOURCE_LOAD_FAILED,
    WEBVIEW_ISOLATION_FAILED,
    MAIN_ACTIVITY_NOT_FOUND,
    NATIVE_LIBRARY_FAILED,
    UNSUPPORTED_PLATFORM_BEHAVIOR,
    LAUNCH_FAILED
}

/**
 * Detailed outcome of a container startup or launch sequence.
 */
data class ContainerLaunchResult(
    val status: ContainerLaunchStatus,
    val message: String,
    val diagnostics: ContainerRuntimeDiagnostics? = null,
    val exception: Throwable? = null
) {
    val isSuccess: Boolean get() = status == ContainerLaunchStatus.LAUNCH_SUCCESS
}

/**
 * Internal diagnostics telemetry kept 100% local on device.
 */
data class ContainerRuntimeDiagnostics(
    val cloneId: String,
    val packageName: String,
    val androidVersion: Int,
    val deviceModel: String,
    val apkPath: String,
    val splitCount: Int,
    val supportedAbis: List<String>,
    val mainActivity: String?,
    val applicationClassName: String?,
    val classloaderResult: String,
    val resourcesResult: String,
    val applicationInitResult: String,
    val webViewSuffixResult: String,
    val launchOutcome: String,
    val logs: List<String> = emptyList(),
    val errorStackTrace: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
