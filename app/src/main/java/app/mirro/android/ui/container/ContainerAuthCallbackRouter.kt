package app.mirro.android.ui.container

import android.content.Intent
import java.util.concurrent.ConcurrentHashMap

/** Routes only validated callback intents to the host that owns the matching clone. */
internal object ContainerAuthCallbackRouter {
    private val hosts = ConcurrentHashMap<String, TargetActivityHost>()

    fun register(cloneId: String, host: TargetActivityHost) {
        hosts[cloneId] = host
    }

    fun unregister(cloneId: String, host: TargetActivityHost) {
        hosts.remove(cloneId, host)
    }

    fun dispatch(intent: Intent): Boolean =
        hosts.values.any { it.handleExternalCallback(intent) }
}
