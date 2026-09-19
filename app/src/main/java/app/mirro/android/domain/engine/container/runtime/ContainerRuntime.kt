package app.mirro.android.domain.engine.container.runtime

import android.app.Application
import android.content.Context
import android.os.Build
import android.webkit.WebView
import app.mirro.android.domain.engine.container.context.VirtualContext
import app.mirro.android.domain.engine.container.inspector.ApkInspector
import app.mirro.android.domain.engine.container.loader.DexRuntimeLoader
import app.mirro.android.domain.engine.container.loader.LoadedApkRuntime
import app.mirro.android.domain.engine.container.model.ContainerLaunchResult
import app.mirro.android.domain.engine.container.model.ContainerLaunchStatus
import app.mirro.android.domain.engine.container.model.ContainerRuntimeDiagnostics
import app.mirro.android.domain.engine.container.model.VirtualRuntimeIdentity
import app.mirro.android.domain.engine.container.storage.VirtualFileSystem
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.ConcurrentHashMap

/**
 * Core runtime orchestrator for virtual container execution.
 *
 * Coordinates APK inspection, Dex loading, WebView isolation, virtual context
 * wrapping, and Application lifecycle initialization.
 */
class ContainerRuntime(
    private val context: Context,
    val virtualFileSystem: VirtualFileSystem = VirtualFileSystem(context),
    val apkInspector: ApkInspector = ApkInspector(context),
    val dexLoader: DexRuntimeLoader = DexRuntimeLoader(context)
) {

    private val activeRuntimes = ConcurrentHashMap<String, ActiveContainerInstance>()
    private val diagnosticLogs = ConcurrentHashMap<String, ContainerRuntimeDiagnostics>()

    companion object {
        @Volatile
        private var currentProcessWebViewSuffix: String? = null
    }

    data class ActiveContainerInstance(
        val identity: VirtualRuntimeIdentity,
        val loadedRuntime: LoadedApkRuntime,
        val virtualContext: VirtualContext,
        val application: Application?
    )

    /**
     * Initializes the container environment and returns the active instance or launch error.
     */
    fun prepareContainer(cloneId: String, packageName: String): ContainerLaunchResult {
        val logEntries = mutableListOf<String>()
        fun log(msg: String) { logEntries.add("${System.currentTimeMillis()}: $msg") }

        log("Starting container initialization for package: $packageName, cloneId: $cloneId")

        // 1. Inspect APK
        val descriptor = apkInspector.inspect(packageName)
        if (descriptor == null) {
            log("APK inspection failed: package not installed on device")
            val diag = createDiagnostics(
                cloneId = cloneId,
                packageName = packageName,
                apkPath = "N/A",
                splitCount = 0,
                mainActivity = null,
                appClass = null,
                classloaderResult = "SKIPPED",
                resourcesResult = "SKIPPED",
                appInitResult = "SKIPPED",
                webViewResult = "SKIPPED",
                outcome = "APK_NOT_FOUND",
                logs = logEntries
            )
            diagnosticLogs[cloneId] = diag
            return ContainerLaunchResult(
                status = ContainerLaunchStatus.APK_NOT_FOUND,
                message = "The target application '$packageName' is not installed on this device.",
                diagnostics = diag
            )
        }

        log("Found APK at: ${descriptor.baseApkPath} with ${descriptor.splitApkPaths.size} splits")

        // 2. Prepare Sandbox Filesystem
        val identity = virtualFileSystem.getRuntimeIdentity(cloneId, packageName)
        log("Sandbox initialized at: ${identity.sandboxRootDir.absolutePath}")

        // 3. Setup WebView Isolation
        var webViewResult = "NOT_ATTEMPTED"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val suffix = identity.webViewDataDirectorySuffix
                if (currentProcessWebViewSuffix == null) {
                    WebView.setDataDirectorySuffix(suffix)
                    currentProcessWebViewSuffix = suffix
                    webViewResult = "ISOLATED_SUFFIX_SET ($suffix)"
                    log("WebView data directory suffix configured: $suffix")
                } else if (currentProcessWebViewSuffix == suffix) {
                    webViewResult = "ISOLATED_SUFFIX_MATCH ($suffix)"
                    log("WebView data directory already matches active suffix")
                } else {
                    webViewResult = "PROCESS_ALREADY_INITIALIZED_WITH ($currentProcessWebViewSuffix)"
                    log("WebView in current host process was previously initialized with $currentProcessWebViewSuffix")
                }
            } catch (e: Exception) {
                webViewResult = "EXCEPTION: ${e.message}"
                log("WebView isolation warning: ${e.message}")
            }
        } else {
            webViewResult = "API_BELOW_28_COOKIE_SCOPED"
            log("WebView suffix API not required on SDK < 28")
        }

        // 4. Dex & Resource Loading
        val loadedRuntime: LoadedApkRuntime
        try {
            log("Loading DEX and resources...")
            loadedRuntime = dexLoader.load(descriptor)
            log("DEX and resources loaded successfully")
        } catch (e: Throwable) {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            log("Classloader/Resource load failed: ${e.message}")
            val diag = createDiagnostics(
                cloneId = cloneId,
                packageName = packageName,
                apkPath = descriptor.baseApkPath,
                splitCount = descriptor.splitApkPaths.size,
                mainActivity = descriptor.mainActivity,
                appClass = descriptor.applicationClassName,
                classloaderResult = "FAILED: ${e.message}",
                resourcesResult = "FAILED",
                appInitResult = "SKIPPED",
                webViewResult = webViewResult,
                outcome = "CLASSLOADER_FAILED",
                logs = logEntries,
                stackTrace = sw.toString()
            )
            diagnosticLogs[cloneId] = diag
            return ContainerLaunchResult(
                status = ContainerLaunchStatus.CLASSLOADER_FAILED,
                message = "Failed to load APK classes: ${e.localizedMessage}",
                diagnostics = diag,
                exception = e
            )
        }

        // 5. Virtual Context Wrap
        val virtualContext = VirtualContext(
            base = context,
            identity = identity,
            runtime = loadedRuntime
        )
        log("VirtualContext bound to package ${virtualContext.packageName}")

        // 6. Instantiate Target Application class if present
        var targetApp: Application? = null
        var appInitResult = "DEFAULT_APPLICATION"
        if (loadedRuntime.applicationClass != null) {
            try {
                val appInstance = loadedRuntime.applicationClass.getDeclaredConstructor().newInstance()
                if (appInstance is Application) {
                    targetApp = appInstance
                    // Attach base context via reflection if attachBaseContext is protected
                    try {
                        val attachMethod = Application::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
                        attachMethod.isAccessible = true
                        attachMethod.invoke(targetApp, virtualContext)
                        targetApp.onCreate()
                        appInitResult = "CUSTOM_APPLICATION_INITIALIZED (${loadedRuntime.applicationClass.name})"
                        log("Target Application initialized: ${loadedRuntime.applicationClass.name}")
                    } catch (e: Exception) {
                        appInitResult = "ATTACH_FAILED (${e.message}), USING_CONTAINER_CONTEXT"
                        log("Application attach warning: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                appInitResult = "INSTANTIATION_FAILED: ${e.message}"
                log("Target Application instantiation notice: ${e.message}")
            }
        }

        val activeInstance = ActiveContainerInstance(
            identity = identity,
            loadedRuntime = loadedRuntime,
            virtualContext = virtualContext,
            application = targetApp
        )
        activeRuntimes[cloneId] = activeInstance

        val diag = createDiagnostics(
            cloneId = cloneId,
            packageName = packageName,
            apkPath = descriptor.baseApkPath,
            splitCount = descriptor.splitApkPaths.size,
            mainActivity = descriptor.mainActivity,
            appClass = descriptor.applicationClassName,
            classloaderResult = "SUCCESS (Loaded ${loadedRuntime.classLoader})",
            resourcesResult = "SUCCESS (${loadedRuntime.resources})",
            appInitResult = appInitResult,
            webViewResult = webViewResult,
            outcome = "LAUNCH_SUCCESS",
            logs = logEntries
        )
        diagnosticLogs[cloneId] = diag

        return ContainerLaunchResult(
            status = ContainerLaunchStatus.LAUNCH_SUCCESS,
            message = "Container runtime initialized for $packageName",
            diagnostics = diag
        )
    }

    fun getActiveInstance(cloneId: String): ActiveContainerInstance? = activeRuntimes[cloneId]

    fun getLatestDiagnostics(cloneId: String): ContainerRuntimeDiagnostics? = diagnosticLogs[cloneId]

    private fun createDiagnostics(
        cloneId: String,
        packageName: String,
        apkPath: String,
        splitCount: Int,
        mainActivity: String?,
        appClass: String?,
        classloaderResult: String,
        resourcesResult: String,
        appInitResult: String,
        webViewResult: String,
        outcome: String,
        logs: List<String>,
        stackTrace: String? = null
    ): ContainerRuntimeDiagnostics {
        return ContainerRuntimeDiagnostics(
            cloneId = cloneId,
            packageName = packageName,
            androidVersion = Build.VERSION.SDK_INT,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            apkPath = apkPath,
            splitCount = splitCount,
            supportedAbis = Build.SUPPORTED_ABIS?.toList() ?: emptyList(),
            mainActivity = mainActivity,
            applicationClassName = appClass,
            classloaderResult = classloaderResult,
            resourcesResult = resourcesResult,
            applicationInitResult = appInitResult,
            webViewSuffixResult = webViewResult,
            launchOutcome = outcome,
            logs = logs,
            errorStackTrace = stackTrace
        )
    }
}
