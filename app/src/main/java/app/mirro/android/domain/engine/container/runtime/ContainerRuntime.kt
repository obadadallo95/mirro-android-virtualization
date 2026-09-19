package app.mirro.android.domain.engine.container.runtime

import android.app.Application
import android.content.Context
import android.os.Build
import android.webkit.WebView
import app.mirro.android.domain.engine.container.context.VirtualContext
import app.mirro.android.domain.engine.container.inspector.ApkInspector
import app.mirro.android.domain.engine.container.loader.DexRuntimeLoader
import app.mirro.android.domain.engine.container.loader.LoadedApkRuntime
import app.mirro.android.domain.engine.container.loader.MirroTargetClassLoader
import app.mirro.android.domain.engine.container.model.ApplicationBootstrapStage
import app.mirro.android.domain.engine.container.model.ContainerLaunchResult
import app.mirro.android.domain.engine.container.model.ContainerLaunchStatus
import app.mirro.android.domain.engine.container.model.ContainerRuntimeDiagnostics
import app.mirro.android.domain.engine.container.model.VirtualRuntimeIdentity
import app.mirro.android.domain.engine.container.storage.VirtualFileSystem
import app.mirro.android.domain.model.CloneRuntimeState
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
    val applicationBootstrapper: ApplicationBootstrapper = ApplicationBootstrapper(),
    val processStrategy: ContainerProcessStrategy = SingleCloneProcessSlotStrategy(),
    private val stateListener: (RuntimeStateEvent) -> Unit = {}
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

    fun activeInstance(cloneId: String): ActiveContainerInstance? = activeRuntimes[cloneId]

    data class RuntimeStateEvent(
        val cloneId: String,
        val state: CloneRuntimeState,
        val failureStage: String? = null,
        val failureReason: String? = null
    )

    /**
     * Initializes the container environment and returns the active instance or launch error.
     */
    fun prepareContainer(cloneId: String, packageName: String): ContainerLaunchResult {
        val logEntries = mutableListOf<String>()
        fun log(msg: String) { logEntries.add("${System.currentTimeMillis()}: $msg") }

        log("Starting container initialization for package: $packageName, cloneId: $cloneId")

        activeRuntimes[cloneId]?.let { active ->
            if (active.identity.originalPackageName == packageName) {
                log("Application bootstrap already completed for this runtime; reusing active process-slot state")
                return ContainerLaunchResult(
                    status = ContainerLaunchStatus.APPLICATION_BOOTSTRAP_SUCCESS,
                    message = "Application bootstrap already completed for $packageName",
                    diagnostics = diagnosticLogs[cloneId]
                )
            }
        }

        // 1. Inspect APK
        val descriptor = apkInspector.inspect(packageName)
        if (descriptor == null) {
            log("APK inspection failed: package not installed on device")
            emitState(cloneId, CloneRuntimeState.FAILED, "APK_INSPECTION", "Target package is not installed")
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
        emitState(cloneId, CloneRuntimeState.APK_LOADED)

        // 2. Prepare Sandbox Filesystem
        val identity = virtualFileSystem.getRuntimeIdentity(cloneId, packageName)
        log("Sandbox initialized at: ${identity.sandboxRootDir.absolutePath}")

        val processClaim = processStrategy.claim(cloneId, identity.webViewDataDirectorySuffix)
        val currentBinding = processStrategy.currentBinding()
        if (processClaim == ProcessSlotClaim.BOUND_TO_OTHER_CLONE) {
            val reason = "Container process slot ${processStrategy.processName} is bound to clone " +
                    "${currentBinding?.cloneId ?: "another runtime"}. Restart the container process before launching a different clone."
            log(reason)
            emitState(cloneId, CloneRuntimeState.FAILED, "PROCESS_SLOT", reason)
            val diag = createDiagnostics(
                cloneId = cloneId,
                packageName = packageName,
                apkPath = descriptor.baseApkPath,
                splitCount = descriptor.splitApkPaths.size,
                mainActivity = descriptor.mainActivity,
                appClass = descriptor.applicationClassName,
                classloaderResult = "SKIPPED",
                resourcesResult = "SKIPPED",
                appInitResult = "SKIPPED",
                webViewResult = "SKIPPED",
                outcome = "PROCESS_SLOT_UNAVAILABLE",
                bootstrapStage = ApplicationBootstrapStage.NOT_STARTED,
                logs = logEntries,
                processSlotName = processStrategy.processName,
                processSlotBinding = currentBinding?.toString()
            )
            diagnosticLogs[cloneId] = diag
            return ContainerLaunchResult(
                status = ContainerLaunchStatus.PROCESS_SLOT_UNAVAILABLE,
                message = reason,
                diagnostics = diag
            )
        }

        log("Container process slot ${processStrategy.processName} claim: $processClaim")

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
                    val reason = "WebView data directory suffix '$currentProcessWebViewSuffix' does not match '$suffix'"
                    log("WebView isolation failed: $reason")
                    emitState(cloneId, CloneRuntimeState.FAILED, "WEBVIEW_ISOLATION", reason)
                    val diag = createDiagnostics(
                        cloneId = cloneId,
                        packageName = packageName,
                        apkPath = descriptor.baseApkPath,
                        splitCount = descriptor.splitApkPaths.size,
                        mainActivity = descriptor.mainActivity,
                        appClass = descriptor.applicationClassName,
                        classloaderResult = "SKIPPED",
                        resourcesResult = "SKIPPED",
                        appInitResult = "SKIPPED",
                        webViewResult = webViewResult,
                        outcome = "WEBVIEW_ISOLATION_FAILED",
                        bootstrapStage = ApplicationBootstrapStage.NOT_STARTED,
                        logs = logEntries,
                        processSlotName = processStrategy.processName,
                        processSlotBinding = processStrategy.currentBinding()?.toString()
                    )
                    diagnosticLogs[cloneId] = diag
                    return ContainerLaunchResult(
                        status = ContainerLaunchStatus.WEBVIEW_ISOLATION_FAILED,
                        message = reason,
                        diagnostics = diag
                    )
                }
            } catch (e: Exception) {
                webViewResult = "EXCEPTION: ${e.message}"
                val reason = "Unable to configure WebView data directory suffix: ${e.message}"
                log("WebView isolation failed: $reason")
                emitState(cloneId, CloneRuntimeState.FAILED, "WEBVIEW_ISOLATION", reason)
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val diag = createDiagnostics(
                    cloneId = cloneId,
                    packageName = packageName,
                    apkPath = descriptor.baseApkPath,
                    splitCount = descriptor.splitApkPaths.size,
                    mainActivity = descriptor.mainActivity,
                    appClass = descriptor.applicationClassName,
                    classloaderResult = "SKIPPED",
                    resourcesResult = "SKIPPED",
                    appInitResult = "SKIPPED",
                    webViewResult = webViewResult,
                    outcome = "WEBVIEW_ISOLATION_FAILED",
                    bootstrapStage = ApplicationBootstrapStage.NOT_STARTED,
                    logs = logEntries,
                    stackTrace = sw.toString(),
                    processSlotName = processStrategy.processName,
                    processSlotBinding = processStrategy.currentBinding()?.toString()
                )
                diagnosticLogs[cloneId] = diag
                return ContainerLaunchResult(
                    status = ContainerLaunchStatus.WEBVIEW_ISOLATION_FAILED,
                    message = reason,
                    diagnostics = diag,
                    exception = e
                )
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
                stackTrace = sw.toString(),
                processSlotName = processStrategy.processName,
                processSlotBinding = processStrategy.currentBinding()?.toString()
            )
            diagnosticLogs[cloneId] = diag
            emitState(
                cloneId,
                CloneRuntimeState.FAILED,
                "DEX_OR_RESOURCE_LOAD",
                e.message ?: "${e.javaClass.name}: unable to load target DEX or resources"
            )
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
        logClassLoaderTraces(loadedRuntime, ::log)

        if (!bootstrapResult.isSuccess) {
            emitState(
                cloneId,
                CloneRuntimeState.FAILED,
                bootstrapResult.failedStage?.name,
                bootstrapResult.rootCauseMessage ?: bootstrapResult.exceptionMessage
            )
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
                stackTrace = bootstrapResult.stackTrace,
                processSlotName = processStrategy.processName,
                processSlotBinding = processStrategy.currentBinding()?.toString()
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
        emitState(cloneId, CloneRuntimeState.APPLICATION_BOOTSTRAPPED)

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
            logs = logEntries,
            processSlotName = processStrategy.processName,
            processSlotBinding = processStrategy.currentBinding()?.toString()
        )
        diagnosticLogs[cloneId] = diag

        return ContainerLaunchResult(
            status = ContainerLaunchStatus.APPLICATION_BOOTSTRAP_SUCCESS,
            message = "Application bootstrap completed for $packageName",
            diagnostics = diag
        )
    }

    private fun emitState(
        cloneId: String,
        state: CloneRuntimeState,
        failureStage: String? = null,
        failureReason: String? = null
    ) {
        runCatching {
            stateListener(RuntimeStateEvent(cloneId, state, failureStage, failureReason))
        }
    }

    fun getActiveInstance(cloneId: String): ActiveContainerInstance? = activeRuntimes[cloneId]

    fun getLatestDiagnostics(cloneId: String): ContainerRuntimeDiagnostics? = diagnosticLogs[cloneId]

    /**
     * Records the first milestone that is visible to the user: the target Activity was attached
     * and resumed inside the host. Application.onCreate() alone is intentionally not treated as
     * a successful clone launch.
     */
    fun markActivityHosted(cloneId: String) {
        val current = diagnosticLogs[cloneId] ?: return
        val updated = current.copy(
            launchOutcome = "ACTIVITY_HOSTED",
            logs = current.logs + "${System.currentTimeMillis()}: Target Activity hosted successfully"
        )
        diagnosticLogs[cloneId] = updated
        emitState(cloneId, CloneRuntimeState.ACTIVITY_HOSTED)
    }

    /**
     * Converts a host-side Activity failure into the same structured launch result used by the
     * bootstrap pipeline. This prevents the UI and persisted clone state from reporting a green
     * bootstrap result when the target never became runnable.
     */
    fun markActivityHostFailed(cloneId: String, error: Throwable): ContainerLaunchResult {
        val current = diagnosticLogs[cloneId]
        val stackTrace = StringWriter().also { writer ->
            error.printStackTrace(PrintWriter(writer))
        }.toString()
        val updated = current?.copy(
            launchOutcome = "ACTIVITY_HOST_FAILED",
            exceptionClass = error.javaClass.name,
            exceptionMessage = error.message ?: error.localizedMessage,
            rootCauseClass = error.javaClass.name,
            rootCauseMessage = error.message ?: error.localizedMessage,
            errorStackTrace = stackTrace,
            logs = current.logs + "${System.currentTimeMillis()}: Target Activity hosting failed: ${error.message}"
        )
        if (updated != null) {
            diagnosticLogs[cloneId] = updated
        }
        emitState(
            cloneId,
            CloneRuntimeState.FAILED,
            failureStage = "ACTIVITY_HOSTED",
            failureReason = error.message ?: error.javaClass.name
        )
        return ContainerLaunchResult(
            status = ContainerLaunchStatus.LAUNCH_FAILED,
            message = "Target Activity could not be hosted: ${error.message ?: error.javaClass.simpleName}",
            diagnostics = updated ?: current,
            exception = error
        )
    }

    private fun logClassLoaderTraces(
        loadedRuntime: LoadedApkRuntime,
        log: (String) -> Unit
    ) {
        (loadedRuntime.classLoader as? MirroTargetClassLoader)?.traceSnapshot()?.forEach { trace ->
            log("Class ownership: ${trace.requestedClass} -> ${trace.owner} " +
                    "source=${trace.source ?: "n/a"} loader=${trace.definingClassLoader}")
        }
    }

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
        processSlotName: String? = null,
        processSlotBinding: String? = null,
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
            processSlotName = processSlotName,
            processSlotBinding = processSlotBinding,
            logs = logs,
            errorStackTrace = stackTrace
        )
    }
}
