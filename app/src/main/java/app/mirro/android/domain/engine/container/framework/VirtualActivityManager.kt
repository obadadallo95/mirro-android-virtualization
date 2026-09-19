package app.mirro.android.domain.engine.container.framework

import android.content.ComponentName
import android.content.Intent
import app.mirro.android.domain.engine.container.loader.DynamicCodeManager
import app.mirro.android.domain.engine.container.model.ApkDescriptor

data class VirtualActivityResult(val resultCode: Int, val data: Intent?)

/** Runtime authority for logical Activity routing; TargetActivityHost is only the adapter. */
class VirtualActivityManager(
    private val cloneId: String,
    private val descriptor: ApkDescriptor,
    private val router: IntentRouter,
    private val dynamicCodeManager: DynamicCodeManager?
) {
    private val tasks = VirtualActivityTaskManager(cloneId)
    private val pendingResults = LinkedHashMap<String, (VirtualActivityResult) -> Unit>()

    fun start(intent: Intent, mode: ActivityLaunchMode = ActivityLaunchMode.STANDARD, result: ((VirtualActivityResult) -> Unit)? = null): ClassifiedValue<VirtualActivityRecord> {
        val route = router.classify(intent)
        if (route.kind != IntentRouteKind.TARGET_INTERNAL) {
            return ClassifiedValue(null, VirtualValueOrigin.UNSUPPORTED, "activity route is ${route.kind}")
        }
        val resolved = descriptor.mainActivity?.let { main ->
            intent.component ?: ComponentName(descriptor.packageName, main)
        } ?: intent.component ?: return ClassifiedValue(null, VirtualValueOrigin.UNSUPPORTED, "no target Activity")
        val resolution = dynamicCodeManager?.resolveClass(resolved.className, componentResolution = true)
        if (resolution != null && !resolution.found) {
            return ClassifiedValue(null, VirtualValueOrigin.UNSUPPORTED, "${resolution.category}: ${resolution.message}")
        }
        val record = tasks.start(resolved, intent, mode)
        result?.let { pendingResults[record.token] = it }
        return ClassifiedValue(record, VirtualValueOrigin.GUEST_VALUE)
    }

    fun deliverResult(token: String, resultCode: Int, data: Intent?) {
        pendingResults.remove(token)?.invoke(VirtualActivityResult(resultCode, data?.let(::Intent)))
    }

    fun finish(token: String): Boolean = tasks.finish(token)
    fun back(): VirtualActivityRecord? = tasks.back()
    fun snapshot(): List<VirtualActivityRecord> = tasks.snapshot()
}
