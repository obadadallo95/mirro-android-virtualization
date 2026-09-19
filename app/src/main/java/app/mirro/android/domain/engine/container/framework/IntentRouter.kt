package app.mirro.android.domain.engine.container.framework

import android.content.ComponentName
import android.content.Intent
import app.mirro.android.domain.engine.container.model.VirtualRuntimeIdentity

enum class IntentRouteKind {
    TARGET_INTERNAL,
    HOST_EXTERNAL,
    BROWSER,
    SYSTEM_SETTINGS,
    CONTENT_URI,
    UNSUPPORTED
}

data class IntentRoute(
    val kind: IntentRouteKind,
    val intent: Intent,
    val component: ComponentName? = intent.component,
    val reason: String? = null
)

class IntentRouter(
    private val identity: VirtualRuntimeIdentity,
    private val registry: VirtualPackageRegistry
) {
    fun classify(intent: Intent): IntentRoute {
        val componentPackage = intent.component?.packageName
        val target = componentPackage == identity.originalPackageName || intent.`package` == identity.originalPackageName
        if (target) return IntentRoute(IntentRouteKind.TARGET_INTERNAL, Intent(intent).apply { setPackage(identity.originalPackageName) })
        if (intent.data?.scheme?.equals("content", ignoreCase = true) == true) {
            return IntentRoute(IntentRouteKind.CONTENT_URI, Intent(intent))
        }
        if (intent.action == Intent.ACTION_VIEW && intent.data?.scheme in setOf("http", "https")) {
            return IntentRoute(IntentRouteKind.BROWSER, Intent(intent))
        }
        if (intent.action?.startsWith("android.settings.") == true) {
            return IntentRoute(IntentRouteKind.SYSTEM_SETTINGS, Intent(intent))
        }
        return if (intent.component != null || intent.`package` != null) {
            IntentRoute(IntentRouteKind.HOST_EXTERNAL, Intent(intent))
        } else {
            IntentRoute(IntentRouteKind.UNSUPPORTED, Intent(intent), reason = "implicit unclassified intent")
        }
    }

    fun resolve(route: IntentRoute): ClassifiedValue<Intent> = when (route.kind) {
        IntentRouteKind.TARGET_INTERNAL -> registry.resolveActivity(route.intent).map { route.intent }
        IntentRouteKind.CONTENT_URI -> ClassifiedValue(route.intent, VirtualValueOrigin.HOST_MEDIATED, "content routing is explicit")
        IntentRouteKind.HOST_EXTERNAL,
        IntentRouteKind.BROWSER,
        IntentRouteKind.SYSTEM_SETTINGS -> ClassifiedValue(route.intent, VirtualValueOrigin.HOST_MEDIATED)
        IntentRouteKind.UNSUPPORTED -> ClassifiedValue(null, VirtualValueOrigin.UNSUPPORTED, route.reason)
    }
}

private inline fun <T, R> ClassifiedValue<T>.map(transform: (T) -> R): ClassifiedValue<R> =
    value?.let { ClassifiedValue(transform(it), origin, reason) } ?: ClassifiedValue(null, origin, reason)
