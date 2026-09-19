package app.mirro.android.domain.engine.container.runtime

import android.app.Application
import android.content.Context
import android.os.Build
import android.webkit.WebView
import app.mirro.android.domain.engine.container.context.VirtualContext
import app.mirro.android.domain.engine.container.inspector.ApkInspector
import app.mirro.android.domain.engine.container.loader.DexRuntimeLoader
import app.mirro.android.domain.engine.container.loader.LoadedApkRuntime
import app.mirro.android.domain.engine.container.model.ApplicationBootstrapStage
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
    val dexLoader: DexRuntimeLoader = DexRuntimeLoader(context),
    val applicationBootstrapper: ApplicationBootstrapper = ApplicationBootstrapper()
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
                bootstrapStage = ApplicationBootstrapStage.NOT_STARTED,
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
            log("DEX and resources loaded successfully (ClassLoader: ${loadedRuntime.classLoader})")
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
                bootstrapStage = ApplicationBootstrapStage.NOT_STARTED,
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
        log("VirtualContext bound to package ${virtualContext.packageName} with sandbox dataDir ${virtualContext.dataDir.absolutePath}")

        // 6. Application Lifecycle Bootstrap (Structured Stages)
        log("Starting Application lifecycle bootstrap...")
        val bootstrapResult = applicationBootstrapper.bootstrap(
            applicationClassName = descriptor.applicationClassName,
            classLoader = loadedRuntime.classLoader,
            virtualContext = virtualContext,
            log = ::log
        )

        if (!bootstrapResult.isSuccess) {
            val diag = createDiagnostics(
                cloneId = cloneId,
                packageName = packageName,
                apkPath = descriptor.baseApkPath,
                splitCount = descriptor.splitApkPaths.size,
                mainActivity = descriptor.mainActivity,
                appClass = descriptor.applicationClassName,
                classloaderResult = "SUCCESS (Loaded ${loadedRuntime.classLoader})",
                resourcesResult = "SUCCESS (${loadedRuntime.resources})",
                appInitResult = bootstrapResult.summary,
                webViewResult = webViewResult,
                outcome = "APPLICATION_BOOTSTRAP_FAILED",
                bootstrapStage = bootstrapResult.currentStage,
                failedStage = bootstrapResult.failedStage,
                exceptionClass = bootstrapResult.exceptionClass,
                exceptionMessage = bootstrapResult.exceptionMessage,
                rootCauseClass = bootstrapResult.rootCauseClass,
                rootCauseMessage = bootstrapResult.rootCauseMessage,
                appClassLoader = bootstrapResult.appClassLoaderInfo,
                virtualContextClassLoader = bootstrapResult.virtualContextClassLoaderInfo,
                threadContextClassLoader = bootstrapResult.threadClassLoaderInfo,
                logs = logEntries,
                stackTrace = bootstrapResult.stackTrace
            )
            diagnosticLogs[cloneId] = diag

            return ContainerLaunchResult(
                status = ContainerLaunchStatus.APPLICATION_BOOTSTRAP_FAILED,
                message = "Application bootstrap failed at stage ${bootstrapResult.failedStage}: ${bootstrapResult.exceptionMessage}",
                diagnostics = diag,
                exception = bootstrapResult.rawException
            )
        }

        val activeInstance = ActiveContainerInstance(
            identity = identity,
            loadedRuntime = loadedRuntime,
            virtualContext = virtualContext,
            application = bootstrapResult.application
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
            appInitResult = bootstrapResult.summary,
            webViewResult = webViewResult,
            outcome = "APPLICATION_BOOTSTRAP_SUCCESS",
            bootstrapStage = ApplicationBootstrapStage.APPLICATION_ONCREATE_COMPLETED,
            appClassLoader = bootstrapResult.appClassLoaderInfo,
            virtualContextClassLoader = bootstrapResult.virtualContextClassLoaderInfo,
            threadContextClassLoader = bootstrapResult.threadClassLoaderInfo,
            logs = logEntries
        )
        diagnosticLogs[cloneId] = diag

        return ContainerLaunchResult(
            status = ContainerLaunchStatus.APPLICATION_BOOTSTRAP_SUCCESS,
            message = "Application bootstrap completed for $packageName",
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
        bootstrapStage: ApplicationBootstrapStage = ApplicationBootstrapStage.NOT_STARTED,
        failedStage: ApplicationBootstrapStage? = null,
        exceptionClass: String? = null,
        exceptionMessage: String? = null,
        rootCauseClass: String? = null,
        rootCauseMessage: String? = null,
        appClassLoader: String? = null,
        virtualContextClassLoader: String? = null,
        threadContextClassLoader: String? = null,
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
            bootstrapStage = bootstrapStage,
            failedStage = failedStage,
            exceptionClass = exceptionClass,
            exceptionMessage = exceptionMessage,
            rootCauseClass = rootCauseClass,
            rootCauseMessage = rootCauseMessage,
            applicationClassLoader = appClassLoader,
            virtualContextClassLoader = virtualContextClassLoader,
            threadContextClassLoader = threadContextClassLoader,
            logs = logs,
            errorStackTrace = stackTrace
        )
    }
}

