package app.mirro.android.ui.container

import android.app.Activity
import android.app.FragmentHostCallback
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.IBinder
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.content.ContextWrapper
import app.mirro.android.domain.engine.container.runtime.ContainerRuntime
import app.mirro.android.domain.engine.container.loader.ClassResolutionCategory
import app.mirro.android.domain.engine.container.loader.LoaderObservationPhase
import app.mirro.android.domain.engine.container.loader.TargetActivityResolutionException
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Attaches and drives a target Activity inside the already-created Mirro host process.
 * Android does not allow the target package's Activity to be registered in Mirro's manifest,
 * so this uses the platform Activity attach path with the host Activity's process primitives.
 */
class TargetActivityHost(
    private val hostActivity: Activity,
    private val runtime: ContainerRuntime.ActiveContainerInstance,
    private val onAuthRoutingObserved: (String) -> Unit = {}
) {

    /**
     * Public FragmentHostCallback constructor retained by the platform. This avoids depending on
     * Activity.HostCallbacks and FragmentController.mHost, both hidden on recent Android builds.
     */
    private class TargetFragmentHost(
        private val targetActivity: Activity,
        context: Context
    ) : FragmentHostCallback<Activity>(context, Handler(context.mainLooper), 0) {
        override fun onGetHost(): Activity = targetActivity

        override fun <T : View> onFindViewById(id: Int): T? = targetActivity.findViewById(id)

        override fun onHasView(): Boolean = true
    }

    private companion object {
        const val TAG = "MirroTargetActivityHost"
    }

    private val targetInstrumentation by lazy {
        TargetInstrumentation(fieldValue<Instrumentation>(hostActivity, "mInstrumentation"))
    }

    private val authSessionRegistry by lazy {
        TargetAuthSessionRegistry(hostActivity)
    }

    private var targetActivity: Activity? = null

    init {
        ContainerAuthCallbackRouter.register(runtime.identity.cloneId, this)
    }

    fun start(): kotlin.Result<Activity> {
        val descriptor = runtime.loadedRuntime.descriptor
        val className = descriptor.mainActivity
            ?: return Result.failure(IllegalStateException("Target main Activity is not declared"))

        enableActivityReflection()
        return startTargetActivity(Intent().setComponent(ComponentName(descriptor.packageName, className)))
    }

    /**
     * Activity.startActivity() calls the Activity's Instrumentation directly, bypassing the
     * VirtualContext. Intercept target-owned components here so non-exported target Activities
     * (for example ChatGPT's WebAuthenticationActivity) stay inside the virtual host instead of
     * being rejected by ActivityTaskManager as a cross-UID launch.
     */
    private inner class TargetInstrumentation(
        private val delegate: Instrumentation
    ) : Instrumentation() {
        @Suppress("UNUSED_PARAMETER")
        fun execStartActivity(
            who: Context,
            contextThread: IBinder,
            token: IBinder,
            target: Activity,
            intent: Intent,
            requestCode: Int,
            options: android.os.Bundle?
        ): ActivityResult? {
            if (startTargetActivityIfOwned(intent)) {
                return null
            }
            if (requestCode >= 0) {
                authSessionRegistry.remember(
                    cloneId = runtime.identity.cloneId,
                    targetActivityClassName = target.javaClass.name,
                    requestCode = requestCode,
                    intent = intent
                )?.let { session ->
                    val routing = "cloneId=${session.cloneId}, " +
                        "activity=${session.targetActivityClassName}, requestCode=${session.requestCode}, " +
                        "callback=${session.callback?.describe() ?: "unknown"}"
                    onAuthRoutingObserved(routing)
                    Log.i(TAG, "Auth route captured: $routing")
                }
            }
            return runCatching {
                invokeDelegateStartActivity(
                    who = who,
                    contextThread = contextThread,
                    token = token,
                    target = target,
                    intent = intent,
                    requestCode = requestCode,
                    options = options
                )
            }.onFailure {
                if (requestCode >= 0) {
                    authSessionRegistry.forget(requestCode, runtime.identity.cloneId)
                }
            }.getOrThrow()
        }

        private fun invokeDelegateStartActivity(
            who: Context,
            contextThread: IBinder,
            token: IBinder,
            target: Activity,
            intent: Intent,
            requestCode: Int,
            options: android.os.Bundle?
        ): ActivityResult? {
            val method = Instrumentation::class.java.declaredMethods.firstOrNull {
                it.name == "execStartActivity" &&
                    it.parameterTypes.size == 7 &&
                    it.parameterTypes[3] == Activity::class.java
            } ?: error("Instrumentation.execStartActivity is unavailable")
            method.isAccessible = true
            return method.invoke(
                delegate,
                who,
                contextThread,
                token,
                target,
                intent,
                requestCode,
                options
            ) as? ActivityResult
        }
    }

    private fun startTargetActivityIfOwned(intent: Intent): Boolean {
        val component = intent.component ?: return false
        val descriptor = runtime.loadedRuntime.descriptor
        if (component.packageName != descriptor.packageName) return false
        if (component.className !in descriptor.declaredActivities) return false

        if (android.os.Looper.myLooper() == hostActivity.mainLooper) {
            startTargetActivity(intent)
        } else {
            hostActivity.runOnUiThread { startTargetActivity(intent) }
        }
        return true
    }

    private fun startTargetActivity(intent: Intent): kotlin.Result<Activity> {
        val descriptor = runtime.loadedRuntime.descriptor
        val className = intent.component?.className
            ?: return Result.failure(IllegalArgumentException("Target Activity intent has no component"))

        Log.i(TAG, "Starting target Activity $className for ${descriptor.packageName}")
        return runCatching {
            val previousActivity = targetActivity
            if (previousActivity != null) {
                val hostInstrumentation = fieldValue<Instrumentation>(hostActivity, "mInstrumentation")
                runCatching { hostInstrumentation.callActivityOnPause(previousActivity) }
                runCatching { hostInstrumentation.callActivityOnStop(previousActivity) }
                runCatching { hostInstrumentation.callActivityOnDestroy(previousActivity) }
                targetActivity = null
            }

            val activityClass = loadTargetActivityClass(className)
            val activity = activityClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance() as Activity
            val activityInfo = activityInfo(descriptor.packageName, className)
            attach(activity, intent, activityInfo)
            if (activityInfo.theme != 0) {
                // ActivityThread normally applies this after Activity.attach and before
                // dispatching onCreate. AppCompat targets reject the host's non-AppCompat theme.
                activity.setTheme(activityInfo.theme)
                Log.i(TAG, "Applied target Activity theme resource ${activityInfo.theme}")
            }

            val instrumentation = fieldValue<Instrumentation>(hostActivity, "mInstrumentation")
            targetActivity = activity
            instrumentation.callActivityOnCreate(activity, null)
            attachTargetDecorToHost(activity)
            instrumentation.callActivityOnStart(activity)
            dispatchTargetLifecycleEvent(activity, "ON_START")
            instrumentation.callActivityOnResume(activity)
            dispatchTargetLifecycleEvent(activity, "ON_RESUME")
            callTargetOnPostResume(activity)
            dispatchHostWindowCallbacks(activity)
            logTargetWindowState(activity, "after_resume")
            Log.i(TAG, "Target Activity started: ${activity.javaClass.name}")
            activity
        }.onFailure { error ->
            Log.e(TAG, "Target Activity failed: $className", error)
        }
    }

    /**
     * Some applications install secondary DEX code from Application.onCreate() or a native
     * startup task. Give that standard runtime path a bounded opportunity to finish before
     * declaring the launch failed. The retry is deliberately limited and never changes identity,
     * signatures, or security checks.
     */
    private fun loadTargetActivityClass(className: String): Class<*> {
        val manager = runtime.loadedRuntime.dynamicCodeManager
            ?: return runtime.loadedRuntime.classLoader.loadClass(className)
        var lastResolution = manager.resolveClass(
            className = className,
            phase = LoaderObservationPhase.COMPONENT_RESOLUTION,
            componentResolution = true
        )
        repeat(20) { attempt ->
            manager.observeSupportedBoundaries(
                application = runtime.application,
                context = runtime.virtualContext,
                phase = LoaderObservationPhase.LATE_OBSERVATION
            )
            lastResolution = manager.resolveClass(
                className = className,
                phase = LoaderObservationPhase.COMPONENT_RESOLUTION,
                componentResolution = true
            )
            lastResolution.resolvedClass?.let { resolved ->
                Log.i(
                    TAG,
                    "Resolved target Activity $className category=${lastResolution.category} " +
                        "loader=${lastResolution.loaderNodeId} source=${lastResolution.sourcePaths}"
                )
                return resolved
            }

            // Only DYNAMIC_LOADER_UNSEEN gets a bounded observation window. Other categories
            // already provide a terminal explanation and should not be disguised as retries.
            if (lastResolution.category != ClassResolutionCategory.DYNAMIC_LOADER_UNSEEN) {
                throw TargetActivityResolutionException(lastResolution)
            }
            if (attempt < 19) SystemClock.sleep(100L)
        }
        throw TargetActivityResolutionException(lastResolution)
    }

    fun pause() {
        targetActivity?.let { activity ->
            fieldValue<Instrumentation>(hostActivity, "mInstrumentation").callActivityOnPause(activity)
        }
    }

    /**
     * Browser/custom-tab results are delivered to the real host Activity because the target
     * Activity is not registered with ActivityTaskManager. Forward those results to the target
     * Activity so WebAuthenticationActivity can consume its OAuth callback.
     */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val session = authSessionRegistry.consumeActivityResult(runtime.identity.cloneId, requestCode, data)
        if (session == null) {
            Log.w(TAG, "Rejected untracked or mismatched Activity result: requestCode=$requestCode")
            return
        }
        targetActivity?.takeIf { it.javaClass.name == session.targetActivityClassName }?.let { activity ->
            val callback = Activity::class.java.getDeclaredMethod(
                "onActivityResult",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Intent::class.java
            ).apply { isAccessible = true }
            callback.invoke(activity, requestCode, resultCode, data)
            Log.i(
                TAG,
                "Forwarded Activity result to ${activity.javaClass.name}: " +
                    "requestCode=$requestCode resultCode=$resultCode hasData=${data != null}"
            )
        } ?: Log.w(TAG, "Rejected Activity result: auth owner is no longer active")
    }

    internal fun handleExternalCallback(intent: Intent): Boolean {
        val session = authSessionRegistry.consumeNewIntent(runtime.identity.cloneId, intent) ?: run {
            Log.w(TAG, "Rejected untracked or mismatched external auth callback")
            return false
        }
        val activity = targetActivity?.takeIf {
            it.javaClass.name == session.targetActivityClassName
        } ?: run {
            Log.w(TAG, "Rejected external auth callback: auth owner is no longer active")
            return false
        }
        fieldValue<Instrumentation>(hostActivity, "mInstrumentation")
            .callActivityOnNewIntent(activity, intent)
        Log.i(TAG, "Forwarded external auth callback to ${activity.javaClass.name}")
        return true
    }

    /**
     * Some browser flows deliver the callback as a new intent instead of an activity result.
     * The host owns the system ActivityRecord, so forward that callback to the target instance.
     */
    fun onNewIntent(intent: Intent) {
        handleExternalCallback(intent)
    }

    fun stop() {
        targetActivity?.let { activity ->
            fieldValue<Instrumentation>(hostActivity, "mInstrumentation").callActivityOnStop(activity)
        }
    }

    fun destroy() {
        targetActivity?.let { activity ->
            fieldValue<Instrumentation>(hostActivity, "mInstrumentation").callActivityOnDestroy(activity)
        }
        targetActivity = null
        authSessionRegistry.clear()
        ContainerAuthCallbackRouter.unregister(runtime.identity.cloneId, this)
        runtime.virtualContext.targetProviders.asReversed().forEach { provider ->
            runCatching { provider.shutdown() }
                .onFailure { error -> Log.w(TAG, "Target provider shutdown failed", error) }
        }
    }

    private fun attach(activity: Activity, intent: Intent, info: ActivityInfo) {
        val attach = Activity::class.java.declaredMethods
            .filter { it.name == "attach" }
            .maxByOrNull { it.parameterTypes.size }

        if (attach == null) {
            Log.w(TAG, "Activity.attach is hidden; using host-window lifecycle bridge")
            attachViaHostWindow(activity, intent, info)
            return
        }
        attach.isAccessible = true

        val args = arrayOfNulls<Any>(attach.parameterTypes.size)
        val hostThread = fieldValue<Any>(hostActivity, "mMainThread")
        val instrumentation = targetInstrumentation
        val token = fieldValue<IBinder>(hostActivity, "mToken")
        val config = hostActivity.resources.configuration

        // API 36 attach signature, with a compatibility map for earlier platform variants.
        args[0] = runtime.virtualContext
        args[1] = hostThread
        args[2] = instrumentation
        args[3] = token
        args[4] = 0
        args[5] = runtime.application
        args[6] = intent
        args[7] = info
        args[8] = info.nonLocalizedLabel ?: descriptorLabel(info)
        args[9] = null
        args[10] = null
        args[11] = null
        args[12] = config
        args[13] = null
        args[14] = null
        args[15] = null
        args[16] = null
        args[17] = null
        args[18] = null
        if (args.size > 19) args[19] = null

        attach.invoke(activity, *args)
    }

    /**
     * API 36 can hide Activity.attach from application reflection. The target still needs an
     * Activity-shaped lifecycle object, so bind it to the already system-owned host window.
     */
    private fun attachViaHostWindow(activity: Activity, intent: Intent, info: ActivityInfo) {
        invokeAttachBaseContext(activity, runtime.virtualContext)
        attachFragmentHost(activity)
        setField(activity, "mIntent", intent)
        setField(activity, "mComponent", intent.component)
        setField(activity, "mActivityInfo", info)
        setField(activity, "mUiThread", Thread.currentThread())
        setField(activity, "mMainThread", fieldValue<Any>(hostActivity, "mMainThread"))
        setField(activity, "mInstrumentation", targetInstrumentation)
        setField(activity, "mToken", fieldValue<IBinder>(hostActivity, "mToken"))
        setField(activity, "mApplication", runtime.application)
        setField(activity, "mCurrentConfig", hostActivity.resources.configuration)
        bindWindowToHost(activity)
        Log.i(TAG, "Target Activity bound through host-window lifecycle bridge")
    }

    /**
     * ActivityThread normally owns a separate Window for every registered Activity and adds that
     * Window to WindowManager during resume. Mirro's target Activity is not registered with
     * ActivityThread, so that add step never happens. Reuse the already system-owned host Window
     * before target onCreate; target setContentView/addContentView calls then populate the visible
     * Mirro surface instead of a detached, never-presented PhoneWindow.
     */
    private fun bindWindowToHost(activity: Activity) {
        setField(activity, "mWindow", hostActivity.window)
        setField(
            activity,
            "mWindowManager",
            hostActivity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        )
        Log.i(TAG, "Target Activity window bridged to host window")
    }

    /**
     * The target keeps its own PhoneWindow so target resource IDs are resolved against the target
     * Resources. Its decor is not registered with WindowManager because the target Activity is not
     * a real ActivityRecord, so embed that decor as the content of Mirro's system-owned window.
     */
    private fun attachTargetDecorToHost(activity: Activity) {
        val targetDecor = activity.window.decorView
        if (targetDecor.parent == null) {
            hostActivity.setContentView(targetDecor)
            Log.i(TAG, "Target decor embedded in host window")
        } else {
            Log.i(TAG, "Target decor already has a parent: ${targetDecor.parent.javaClass.name}")
        }
    }

    /**
     * A normal Activity receives these callbacks from ViewRootImpl after ActivityThread adds its
     * Window. The target Window cannot be added independently, so forward the callbacks once the
     * host decor is attached; splash/content state machines commonly use window focus to finish
     * their first-frame transition.
     */
    private fun dispatchHostWindowCallbacks(activity: Activity) {
        hostActivity.window.decorView.post {
            runCatching {
                activity.onAttachedToWindow()
                activity.onWindowFocusChanged(true)
                logTargetWindowState(activity, "after_window_callbacks")
                Log.i(TAG, "Target Activity window callbacks dispatched")
            }.onFailure { error ->
                Log.w(TAG, "Target Activity window callbacks failed", error)
            }
        }
    }

    /**
     * ActivityThread dispatches onPostResume after onResume. Instrumentation exposes
     * callActivityOnResume but not the corresponding post-resume helper, so invoke the
     * target override explicitly. FragmentActivity uses this callback to deliver the
     * RESUMED state to fragments; Compose/navigation code commonly waits for that state
     * before installing the first real content view.
     */
    private fun callTargetOnPostResume(activity: Activity) {
        var type: Class<*>? = activity.javaClass
        var method: Method? = null
        while (type != null && method == null) {
            method = runCatching {
                type.getDeclaredMethod("onPostResume")
            }.getOrNull()
            type = type.superclass
        }
        (method ?: error("Missing Activity.onPostResume")).apply { isAccessible = true }.invoke(activity)
        Log.i(TAG, "Target Activity post-resume callback dispatched")
    }

    /**
     * The target Activity is not registered with the host ActivityThread, so AndroidX's
     * ReportFragment/ActivityLifecycleCallbacks bridge never receives the normal START and
     * RESUME events. The target's own lifecycle coroutine therefore remains at CREATED and
     * never installs its Compose content. Deliver the missing events to the target lifecycle
     * registry after the corresponding Activity callback has run.
     */
    private fun dispatchTargetLifecycleEvent(activity: Activity, eventName: String) {
        runCatching {
            val lifecycle = activity.javaClass.methods
                .first { it.name == "getLifecycle" && it.parameterTypes.isEmpty() }
                .invoke(activity)
                ?: error("Target getLifecycle() returned null")

            val currentState = lifecycle.javaClass.methods
                .first { it.name == "b" && it.parameterTypes.isEmpty() }
                .invoke(lifecycle)
                ?.toString()
                ?: "UNKNOWN"
            val targetState = when (eventName) {
                "ON_CREATE" -> "CREATED"
                "ON_START" -> "STARTED"
                "ON_RESUME" -> "RESUMED"
                else -> error("Unsupported target lifecycle event: $eventName")
            }
            if (lifecycleRank(currentState) >= lifecycleRank(targetState)) {
                Log.i(TAG, "Target lifecycle already at $currentState; skipped $eventName")
                return
            }

            val eventMethod = lifecycle.javaClass.methods.firstOrNull { method ->
                method.parameterTypes.size == 1 &&
                    method.parameterTypes[0].isEnum &&
                    method.parameterTypes[0].enumConstants.any { it.toString() == eventName }
            } ?: error("Target LifecycleRegistry handleLifecycleEvent method not found for $eventName")
            val event = eventMethod.parameterTypes[0].enumConstants
                .first { it.toString() == eventName }
            eventMethod.isAccessible = true
            eventMethod.invoke(lifecycle, event)
            Log.i(TAG, "Target lifecycle event dispatched: $eventName ($currentState -> $targetState)")
        }.onFailure { error ->
            Log.w(TAG, "Target lifecycle event failed: $eventName", error)
        }
    }

    private fun lifecycleRank(state: String): Int = when (state) {
        "DESTROYED" -> 0
        "INITIALIZED" -> 1
        "CREATED" -> 2
        "STARTED" -> 3
        "RESUMED" -> 4
        else -> -1
    }

    private fun logTargetWindowState(activity: Activity, stage: String) {
        val decor = activity.window.decorView
        val group = decor as? ViewGroup
        Log.i(
            TAG,
                "Target window state [$stage]: decor=${decor.javaClass.name}, " +
                "attached=${decor.isAttachedToWindow}, hasFocus=${decor.hasWindowFocus()}, " +
                "childCount=${group?.childCount ?: -1}, content=${activity.findViewById<View>(android.R.id.content)?.javaClass?.name}, " +
                "contentChildCount=${(activity.findViewById<View>(android.R.id.content) as? ViewGroup)?.childCount ?: -1}, " +
                "lifecycle=${targetLifecycleState(activity)}, " +
                "tree=${describeViewTree(decor)}"
        )
        decor.postDelayed({
            val delayedGroup = decor as? ViewGroup
            Log.i(
                TAG,
                    "Target window state [${stage}_delayed]: attached=${decor.isAttachedToWindow}, " +
                    "hasFocus=${decor.hasWindowFocus()}, childCount=${delayedGroup?.childCount ?: -1}, " +
                    "content=${activity.findViewById<View>(android.R.id.content)?.javaClass?.name}, " +
                    "contentChildCount=${(activity.findViewById<View>(android.R.id.content) as? ViewGroup)?.childCount ?: -1}, " +
                    "lifecycle=${targetLifecycleState(activity)}, " +
                    "tree=${describeViewTree(decor)}"
            )
        }, 3_000L)
    }

    private fun targetLifecycleState(activity: Activity): String {
        return runCatching {
            val lifecycle = activity.javaClass.methods
                .first { it.name == "getLifecycle" && it.parameterTypes.isEmpty() }
                .invoke(activity)
            lifecycle.javaClass.methods
                .first { it.name == "b" && it.parameterTypes.isEmpty() }
                .invoke(lifecycle)
                .toString()
        }.getOrElse { "UNKNOWN(${it.javaClass.simpleName})" }
    }

    private fun describeViewTree(view: View, depth: Int = 0): String {
        if (depth >= 4) return view.javaClass.simpleName
        val group = view as? ViewGroup ?: return view.javaClass.simpleName
        val children = (0 until group.childCount).joinToString(",") {
            describeViewTree(group.getChildAt(it), depth + 1)
        }
        return "${view.javaClass.simpleName}[$children]"
    }

    /**
     * Activity.attach normally initializes this before Activity.onCreate dispatches fragments.
     * Without it, FragmentManager.moveToState() fails with the misleading "No activity"
     * exception even though the target Activity instance and ActivityInfo are valid.
     */
    private fun attachFragmentHost(activity: Activity) {
        val fragments = fieldValue<Any>(activity, "mFragments")
        val attachHost = fragments.javaClass.methods
            .firstOrNull { it.name == "attachHost" && it.parameterTypes.size == 1 }
            ?: fragments.javaClass.declaredMethods
                .firstOrNull { it.name == "attachHost" && it.parameterTypes.size == 1 }

        if (attachHost == null) {
            // Android 14+ may hide FragmentController.attachHost from application reflection.
            // Reproduce its small attachController operation through a host callback owned by
            // the target Activity. Android hides both Activity.HostCallbacks and the controller
            // fields, so we provide the same public FragmentHostCallback contract ourselves.
            val fragmentManager = activity.fragmentManager
            val hostCallbacks = TargetFragmentHost(activity, runtime.virtualContext)
            val attachController = fragmentManager.javaClass.methods
                .firstOrNull { it.name == "attachController" && it.parameterTypes.size == 3 }
                ?: fragmentManager.javaClass.declaredMethods
                    .firstOrNull { it.name == "attachController" && it.parameterTypes.size == 3 }
                ?: error("Missing FragmentManager.attachController")
            attachController.isAccessible = true
            attachController.invoke(fragmentManager, hostCallbacks, hostCallbacks, null)
            Log.i(TAG, "Target FragmentController attached through FragmentManager fields")
            return
        }

        attachHost.isAccessible = true
        attachHost.invoke(fragments, *arrayOfNulls<Any>(1))
        Log.i(TAG, "Target FragmentController attached")
    }

    private fun invokeAttachBaseContext(activity: Activity, context: Context) {
        var type: Class<*>? = activity.javaClass
        var method: Method? = null
        while (type != null && method == null) {
            method = runCatching {
                type.getDeclaredMethod("attachBaseContext", Context::class.java)
            }.getOrNull()
            type = type.superclass
        }
        (method ?: ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java))
            .apply { isAccessible = true }
            .invoke(activity, context)
    }

    /** Android hides ActivityThread/Activity.attach from ordinary application reflection. */
    private fun enableActivityReflection() {
        val result = runCatching {
            val vmRuntimeClass = Class.forName("dalvik.system.VMRuntime")
            val getRuntime = vmRuntimeClass.getDeclaredMethod("getRuntime")
            val runtime = getRuntime.invoke(null)
            val exemptions = vmRuntimeClass.getDeclaredMethod(
                "setHiddenApiExemptions",
                Array<String>::class.java
            )
            exemptions.invoke(
                runtime,
                arrayOf(
                    "Landroid/app/Activity;",
                    "Landroid/app/ActivityThread;",
                    "Landroid/app/Instrumentation;",
                    "Landroid/view/Window;"
                ) as Any
            )
            Log.i(TAG, "Hidden Activity reflection enabled")
        }
        if (result.isFailure) {
            // Android 16/SDK 36 may remove the exemption method entirely. The host has a
            // compatibility attach bridge and can continue without treating this as a launch
            // failure; keep the reason out of normal logs because it is expected on those builds.
            Log.i(TAG, "Hidden Activity reflection exemptions unavailable; using compatibility bridge")
        }
    }

    private fun activityInfo(packageName: String, className: String): ActivityInfo {
        val original = hostActivity.packageManager.getActivityInfo(
            ComponentName(packageName, className),
            android.content.pm.PackageManager.GET_META_DATA
        )
        return ActivityInfo(original).apply {
            applicationInfo = runtime.virtualContext.virtualPackageManager.getApplicationInfo(0)
            this.packageName = packageName
            this.name = className
        }
    }

    private fun descriptorLabel(info: ActivityInfo): CharSequence = info.name ?: "Mirro target Activity"

    private inline fun <reified T> fieldValue(receiver: Any, name: String): T {
        var type: Class<*>? = receiver.javaClass
        while (type != null) {
            try {
                val field: Field = type.getDeclaredField(name)
                field.isAccessible = true
                return field.get(receiver) as T
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        error("Missing Activity field $name")
    }

    private fun setField(receiver: Any, name: String, value: Any?) {
        var type: Class<*>? = receiver.javaClass
        while (type != null) {
            try {
                type.getDeclaredField(name).apply {
                    isAccessible = true
                    set(receiver, value)
                }
                return
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        error("Missing Activity field $name")
    }
}
