package app.mirro.android.domain.engine.container.proxy

import android.app.Application
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import app.mirro.android.domain.engine.container.context.VirtualContext
import app.mirro.android.domain.engine.container.loader.LoadedApkRuntime
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.IdentityHashMap

/**
 * Hosts target-declared services in the already-owned container process.
 *
 * Calling ActivityManager with a target component is not valid here. The system sees Mirro's
 * UID as the caller and rejects a target service that is not exported, even when the request
 * originated from target code. This small in-process host preserves the normal Service
 * lifecycle for the common started/bound service cases without claiming a second Android UID.
 */
class VirtualServiceManager(
    private val hostContext: Context,
    private val virtualContext: VirtualContext,
    private val runtime: LoadedApkRuntime
) {

    private companion object {
        const val TAG = "MirroVirtualService"
    }

    private data class RunningService(
        val component: ComponentName,
        var service: Service? = null,
        var started: Boolean = false,
        var clientCount: Int = 0,
        var nextStartId: Int = 0
    )

    private data class Binding(
        val runningService: RunningService,
        val intent: Intent
    )

    private val lock = Any()
    private val mainHandler = Handler(virtualContext.mainLooper)
    private val runningServices = LinkedHashMap<String, RunningService>()
    private val bindings = IdentityHashMap<ServiceConnection, Binding>()

    fun startService(intent: Intent, foreground: Boolean): ComponentName? {
        val component = resolveTargetService(intent) ?: run {
            Log.w(TAG, "Ignoring unresolved target service start: $intent")
            return null
        }
        val running = synchronized(lock) {
            val value = runningServices.getOrPut(component.className) { RunningService(component) }
            value.started = true
            value.nextStartId += 1
            value
        }
        val startId = synchronized(lock) { running.nextStartId }
        val copiedIntent = Intent(intent).apply {
            setComponent(component)
            setPackage(component.packageName)
        }

        postToMain {
            val service = ensureCreated(running) ?: return@postToMain
            runCatching {
                service.onStartCommand(copiedIntent, 0, startId)
            }.onFailure { error ->
                Log.e(TAG, "Target service onStartCommand failed: ${component.className}", error)
            }
        }
        Log.i(
            TAG,
            "Hosted target ${if (foreground) "foreground " else ""}service locally: " +
                component.flattenToShortString()
        )
        return component
    }

    fun stopService(intent: Intent): Boolean {
        val component = resolveTargetService(intent) ?: return false
        val running = synchronized(lock) {
            runningServices[component.className]?.also { it.started = false }
        } ?: return false
        postToMain { destroyIfUnused(running) }
        return true
    }

    fun bindService(intent: Intent, conn: ServiceConnection, flags: Int): Boolean {
        val component = resolveTargetService(intent) ?: run {
            Log.w(TAG, "Ignoring unresolved target service bind: $intent")
            return false
        }
        val running = synchronized(lock) {
            val value = runningServices.getOrPut(component.className) { RunningService(component) }
            value.clientCount += 1
            val binding = Binding(value, Intent(intent).apply {
                setComponent(component)
                setPackage(component.packageName)
            })
            bindings[conn] = binding
            value
        }
        val bindingIntent = synchronized(lock) { bindings[conn]?.intent ?: Intent(intent) }

        postToMain {
            val service = ensureCreated(running)
            if (service == null) {
                runCatching { conn.onNullBinding(component) }
                return@postToMain
            }
            val binder = runCatching { service.onBind(bindingIntent) }
                .onFailure { error ->
                    Log.e(TAG, "Target service onBind failed: ${component.className}", error)
                }
                .getOrNull()
            if (binder != null) {
                runCatching { conn.onServiceConnected(component, binder) }
                    .onFailure { error ->
                        Log.e(TAG, "Target ServiceConnection callback failed", error)
                    }
            } else {
                runCatching { conn.onNullBinding(component) }
            }
        }
        Log.i(TAG, "Hosted target service binding locally: ${component.flattenToShortString()} flags=$flags")
        return true
    }

    fun hasBinding(conn: ServiceConnection): Boolean = synchronized(lock) {
        bindings.containsKey(conn)
    }

    fun unbindService(conn: ServiceConnection) {
        val binding = synchronized(lock) {
            bindings.remove(conn)?.also { it.runningService.clientCount-- }
        } ?: return
        postToMain {
            binding.runningService.service?.let { service ->
                runCatching { service.onUnbind(binding.intent) }
                    .onFailure { error -> Log.e(TAG, "Target service onUnbind failed", error) }
            }
            destroyIfUnused(binding.runningService)
        }
    }

    private fun resolveTargetService(intent: Intent): ComponentName? {
        val explicit = intent.component
        val component = explicit ?: if (intent.`package` == runtime.descriptor.packageName) {
            runCatching {
                virtualContext.virtualPackageManager.resolveService(intent, 0)?.serviceInfo?.let {
                    ComponentName(it.packageName, it.name)
                }
            }.getOrNull()
        } else {
            null
        }
        if (component == null || component.packageName != runtime.descriptor.packageName) return null

        val declared = runtime.descriptor.declaredServices
        if (declared.isNotEmpty() && component.className !in declared) return null
        return component
    }

    private fun ensureCreated(running: RunningService): Service? {
        synchronized(lock) {
            running.service?.let { return it }
        }

        val service = runCatching {
            val serviceClass = runtime.classLoader.loadClass(running.component.className)
            require(Service::class.java.isAssignableFrom(serviceClass)) {
                "${running.component.className} is not an android.app.Service"
            }
            serviceClass.getDeclaredConstructor().apply { isAccessible = true }
                .newInstance() as Service
        }.onFailure { error ->
            Log.e(TAG, "Unable to instantiate target service ${running.component.className}", error)
        }.getOrNull() ?: return null

        if (!attach(service, running.component.className)) return null

        val created = runCatching { service.onCreate() }
            .onFailure { error ->
                Log.e(TAG, "Target service onCreate failed: ${running.component.className}", error)
            }
            .isSuccess
        if (!created) return null

        synchronized(lock) {
            running.service ?: service.also { running.service = it }
        }
        Log.i(TAG, "Target service created locally: ${running.component.flattenToShortString()}")
        return running.service
    }

    private fun attach(service: Service, className: String): Boolean {
        return runCatching {
            enableHiddenApiReflection()
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val attach = Service::class.java.declaredMethods.firstOrNull {
                it.name == "attach" && it.parameterTypes.size == 6
            } ?: error("Service.attach is unavailable")
            attach.isAccessible = true
            val application = virtualContext.targetApplication
                ?: (hostContext.applicationContext as? Application)
                ?: error("Target Application is unavailable")
            attach.invoke(
                service,
                virtualContext,
                findField(hostContext, "mMainThread") ?: findField(findField(hostContext, "mBase"), "mMainThread"),
                className,
                Binder(),
                application,
                activityManagerService()
            )
        }.onFailure { error ->
            Log.e(TAG, "Unable to attach target service $className", error)
        }.isSuccess
    }

    private fun destroyIfUnused(running: RunningService) {
        val service = synchronized(lock) {
            if (running.started || running.clientCount > 0) return
            if (runningServices[running.component.className] !== running) return
            runningServices.remove(running.component.className)
            running.service.also { running.service = null }
        } ?: return
        runCatching { service.onDestroy() }
            .onFailure { error -> Log.e(TAG, "Target service onDestroy failed", error) }
        Log.i(TAG, "Target service destroyed locally: ${running.component.flattenToShortString()}")
    }

    private fun postToMain(block: () -> Unit) {
        if (Looper.myLooper() == virtualContext.mainLooper) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun activityManagerService(): Any? = runCatching {
        val activityManager = Class.forName("android.app.ActivityManager")
        activityManager.getDeclaredMethod("getService").apply { isAccessible = true }.invoke(null)
    }.getOrNull()

    private fun enableHiddenApiReflection() {
        runCatching {
            val vmRuntimeClass = Class.forName("dalvik.system.VMRuntime")
            val runtime = vmRuntimeClass.getDeclaredMethod("getRuntime").invoke(null)
            vmRuntimeClass.getDeclaredMethod("setHiddenApiExemptions", Array<String>::class.java)
                .invoke(
                    runtime,
                    arrayOf(
                        "Landroid/app/Service;",
                        "Landroid/app/ActivityManager;",
                        "Landroid/app/ActivityThread;"
                    ) as Any
                )
        }
    }

    private fun findField(receiver: Any?, name: String): Any? {
        var type = receiver?.javaClass
        while (type != null) {
            val field: Field? = runCatching { type.getDeclaredField(name) }.getOrNull()
            if (field != null) {
                return runCatching {
                    field.isAccessible = true
                    field.get(receiver)
                }.getOrNull()
            }
            type = type.superclass
        }
        return null
    }
}
