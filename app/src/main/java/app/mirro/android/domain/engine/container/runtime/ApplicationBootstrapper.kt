package app.mirro.android.domain.engine.container.runtime

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import app.mirro.android.domain.engine.container.context.VirtualContext
import app.mirro.android.domain.engine.container.model.ApplicationBootstrapStage
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Manages the multi-stage lifecycle bootstrap for target Android Application instances
 * inside the virtual container sandbox.
 *
 * Implements strict classloader isolation, observable lifecycle stages, context attachment,
 * and deep exception unwrapping.
 */
class ApplicationBootstrapper {

    data class BootstrapResult(
        val isSuccess: Boolean,
        val application: Application?,
        val currentStage: ApplicationBootstrapStage,
        val failedStage: ApplicationBootstrapStage?,
        val summary: String,
        val exceptionClass: String? = null,
        val exceptionMessage: String? = null,
        val rootCauseClass: String? = null,
        val rootCauseMessage: String? = null,
        val stackTrace: String? = null,
        val appClassLoaderInfo: String? = null,
        val virtualContextClassLoaderInfo: String? = null,
        val threadClassLoaderInfo: String? = null,
        val rawException: Throwable? = null
    )

    fun bootstrap(
        applicationClassName: String?,
        classLoader: ClassLoader,
        virtualContext: VirtualContext,
        log: (String) -> Unit
    ): BootstrapResult {
        if (applicationClassName.isNullOrBlank()) {
            log("No custom Application class declared in manifest. Using default android.app.Application fallback.")
            return BootstrapResult(
                isSuccess = true,
                application = null,
                currentStage = ApplicationBootstrapStage.APPLICATION_ONCREATE_COMPLETED,
                failedStage = null,
                summary = "DEFAULT_APPLICATION",
                appClassLoaderInfo = classLoader.toString(),
                virtualContextClassLoaderInfo = virtualContext.classLoader.toString(),
                threadClassLoaderInfo = Thread.currentThread().contextClassLoader?.toString()
            )
        }

        var stage = ApplicationBootstrapStage.NOT_STARTED
        val originalThreadClassLoader = Thread.currentThread().contextClassLoader
        var appClassLoaderInfo: String? = null
        var virtualContextClassLoaderInfo: String? = null
        var threadClassLoaderInfo: String? = null

        try {
            // ClassLoader consistency: Set current thread context class loader
            Thread.currentThread().contextClassLoader = classLoader
            threadClassLoaderInfo = "${classLoader.javaClass.name}@${Integer.toHexString(System.identityHashCode(classLoader))}"
            virtualContextClassLoaderInfo = "${virtualContext.classLoader.javaClass.name}@${Integer.toHexString(System.identityHashCode(virtualContext.classLoader))}"

            log("Thread.currentThread().contextClassLoader set to target ClassLoader: $threadClassLoaderInfo")
            log("VirtualContext.classLoader: $virtualContextClassLoaderInfo")

            // Stage 1: APPLICATION_CLASS_RESOLVED
            log("Stage 1 (APPLICATION_CLASS_RESOLVED): Resolving Application class '$applicationClassName'...")
            val appClass = try {
                Class.forName(applicationClassName, true, classLoader)
            } catch (e: Throwable) {
                stage = ApplicationBootstrapStage.APPLICATION_CLASS_RESOLVED
                log("Stage 1 failed: Class.forName could not resolve '$applicationClassName'")
                return buildFailureResult(stage, e, appClassLoaderInfo, virtualContextClassLoaderInfo, threadClassLoaderInfo, log)
            }
            stage = ApplicationBootstrapStage.APPLICATION_CLASS_RESOLVED
            appClassLoaderInfo = "${appClass.classLoader?.javaClass?.name}@${Integer.toHexString(System.identityHashCode(appClass.classLoader))}"
            log("Stage 1 SUCCESS: Application class resolved ($applicationClassName, classLoader=$appClassLoaderInfo)")

            if (!Application::class.java.isAssignableFrom(appClass)) {
                val err = IllegalArgumentException("Resolved class '$applicationClassName' does not extend android.app.Application")
                return buildFailureResult(stage, err, appClassLoaderInfo, virtualContextClassLoaderInfo, threadClassLoaderInfo, log)
            }

            // Stage 2: APPLICATION_CONSTRUCTOR_FOUND
            log("Stage 2 (APPLICATION_CONSTRUCTOR_FOUND): Searching for no-argument constructor...")
            val constructor = try {
                appClass.getDeclaredConstructor().apply { isAccessible = true }
            } catch (e: Throwable) {
                stage = ApplicationBootstrapStage.APPLICATION_CONSTRUCTOR_FOUND
                log("Stage 2 failed: No accessible no-arg constructor found for '$applicationClassName'")
                return buildFailureResult(stage, e, appClassLoaderInfo, virtualContextClassLoaderInfo, threadClassLoaderInfo, log)
            }
            stage = ApplicationBootstrapStage.APPLICATION_CONSTRUCTOR_FOUND
            log("Stage 2 SUCCESS: No-arg constructor found and made accessible")

            // Stage 3: APPLICATION_INSTANCE_CREATED
            log("Stage 3 (APPLICATION_INSTANCE_CREATED): Instantiating Application instance...")
            val appInstance = try {
                constructor.newInstance() as Application
            } catch (e: Throwable) {
                stage = ApplicationBootstrapStage.APPLICATION_INSTANCE_CREATED
                log("Stage 3 failed: constructor.newInstance() threw an exception")
                return buildFailureResult(stage, e, appClassLoaderInfo, virtualContextClassLoaderInfo, threadClassLoaderInfo, log)
            }
            stage = ApplicationBootstrapStage.APPLICATION_INSTANCE_CREATED
            log("Stage 3 SUCCESS: Application instance created (${appInstance.javaClass.name})")

            // Stage 4: BASE_CONTEXT_ATTACHED
            log("Stage 4 (BASE_CONTEXT_ATTACHED): Attaching VirtualContext as base context...")
            try {
                // Find attachBaseContext on class hierarchy (declared on ContextWrapper)
                var attachMethod: Method? = null
                var currClass: Class<*>? = appInstance.javaClass
                while (currClass != null && currClass != Any::class.java) {
                    try {
                        attachMethod = currClass.getDeclaredMethod("attachBaseContext", Context::class.java)
                        break
                    } catch (_: NoSuchMethodException) {
                        currClass = currClass.superclass
                    }
                }
                if (attachMethod == null) {
                    attachMethod = ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
                }
                attachMethod.isAccessible = true
                attachMethod.invoke(appInstance, virtualContext)

                // Wire target application onto VirtualContext so getApplicationContext() resolves to it
                virtualContext.targetApplication = appInstance
            } catch (e: Throwable) {
                stage = ApplicationBootstrapStage.BASE_CONTEXT_ATTACHED
                log("Stage 4 failed: attachBaseContext invocation threw an exception")
                return buildFailureResult(stage, e, appClassLoaderInfo, virtualContextClassLoaderInfo, threadClassLoaderInfo, log)
            }
            stage = ApplicationBootstrapStage.BASE_CONTEXT_ATTACHED
            log("Stage 4 SUCCESS: VirtualContext attached to Application instance")

            // Stage 5 & 6: APPLICATION_ONCREATE_STARTED -> APPLICATION_ONCREATE_COMPLETED
            stage = ApplicationBootstrapStage.APPLICATION_ONCREATE_STARTED
            log("Stage 5 (APPLICATION_ONCREATE_STARTED): Invoking target Application.onCreate()...")
            try {
                appInstance.onCreate()
            } catch (e: Throwable) {
                log("Stage 5/6 failed: Application.onCreate() threw an uncaught exception")
                return buildFailureResult(stage, e, appClassLoaderInfo, virtualContextClassLoaderInfo, threadClassLoaderInfo, log)
            }

            stage = ApplicationBootstrapStage.APPLICATION_ONCREATE_COMPLETED
            log("Stage 6 SUCCESS: Target Application.onCreate() completed without exception")

            return BootstrapResult(
                isSuccess = true,
                application = appInstance,
                currentStage = ApplicationBootstrapStage.APPLICATION_ONCREATE_COMPLETED,
                failedStage = null,
                summary = "CUSTOM_APPLICATION_INITIALIZED ($applicationClassName)",
                appClassLoaderInfo = appClassLoaderInfo,
                virtualContextClassLoaderInfo = virtualContextClassLoaderInfo,
                threadClassLoaderInfo = threadClassLoaderInfo
            )
        } finally {
            // Restore thread context classloader
            Thread.currentThread().contextClassLoader = originalThreadClassLoader
        }
    }

    private fun buildFailureResult(
        failedStage: ApplicationBootstrapStage,
        throwable: Throwable,
        appClassLoaderInfo: String?,
        virtualContextClassLoaderInfo: String?,
        threadClassLoaderInfo: String?,
        log: (String) -> Unit
    ): BootstrapResult {
        val unwrapped = unwrapException(throwable)
        val rootCause = getRootCause(throwable)

        val sw = StringWriter()
        unwrapped.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()

        val excClass = unwrapped.javaClass.name
        val excMsg = unwrapped.message ?: unwrapped.localizedMessage ?: "No exception message"
        val rootClass = rootCause.javaClass.name
        val rootMsg = rootCause.message ?: rootCause.localizedMessage ?: "No root cause message"

        log("Bootstrap FAILURE at stage $failedStage:")
        log(" - Exception: $excClass: $excMsg")
        log(" - Root Cause: $rootClass: $rootMsg")

        return BootstrapResult(
            isSuccess = false,
            application = null,
            currentStage = ApplicationBootstrapStage.FAILED,
            failedStage = failedStage,
            summary = "BOOTSTRAP_FAILED_AT_${failedStage.name}: $excClass ($excMsg)",
            exceptionClass = excClass,
            exceptionMessage = excMsg,
            rootCauseClass = rootClass,
            rootCauseMessage = rootMsg,
            stackTrace = stackTrace,
            appClassLoaderInfo = appClassLoaderInfo,
            virtualContextClassLoaderInfo = virtualContextClassLoaderInfo,
            threadClassLoaderInfo = threadClassLoaderInfo,
            rawException = unwrapped
        )
    }

    companion object {
        fun unwrapException(throwable: Throwable): Throwable {
            var current = throwable
            while (true) {
                if (current is InvocationTargetException && current.targetException != null) {
                    current = current.targetException
                } else if (current.cause != null && current.cause !== current && (current is RuntimeException || current is Exception)) {
                    if (current.cause is InvocationTargetException) {
                        val ite = current.cause as InvocationTargetException
                        current = ite.targetException ?: current.cause!!
                    } else if (current.cause != null && current.cause !== current) {
                        current = current.cause!!
                    } else {
                        break
                    }
                } else {
                    break
                }
            }
            return current
        }

        fun getRootCause(throwable: Throwable): Throwable {
            var root = unwrapException(throwable)
            val visited = mutableSetOf<Throwable>()
            while (root.cause != null && root.cause !== root && visited.add(root)) {
                root = unwrapException(root.cause!!)
            }
            return root
        }
    }
}
