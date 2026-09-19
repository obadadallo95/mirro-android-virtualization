package app.mirro.android.domain.engine.container.proxy

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Extensible registry managing intercepted and proxied Android system services
 * for virtual container instances.
 */
class ServiceProxyRegistry(private val baseContext: Context) {

    private val proxiedServices = ConcurrentHashMap<String, Any>()

    fun registerProxy(serviceName: String, serviceProxy: Any) {
        proxiedServices[serviceName] = serviceProxy
    }

    fun getService(name: String): Any? {
        return proxiedServices[name] ?: baseContext.getSystemService(name)
    }

    fun hasCustomProxy(name: String): Boolean = proxiedServices.containsKey(name)
}
