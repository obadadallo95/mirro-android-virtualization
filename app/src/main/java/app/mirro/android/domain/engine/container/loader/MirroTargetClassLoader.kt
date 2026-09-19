package app.mirro.android.domain.engine.container.loader

import dalvik.system.PathClassLoader
import java.net.URL
import java.util.Collections
import java.util.Enumeration
import java.util.LinkedHashSet
import java.util.concurrent.ConcurrentHashMap

data class ClassLoadTrace(
    val requestedClass: String,
    val owner: ClassLoadOwner,
    val source: String?,
    val definingClassLoader: String?
)

/**
 * Target runtime loader with explicit ownership routing.
 *
 * The underlying PathClassLoader is given a filtering parent. That parent refuses classes proven
 * to exist in the target APK, allowing PathClassLoader to load its own copy instead of resolving
 * the host's duplicate dependency first. Classes absent from the target fall back to the host.
 */
class MirroTargetClassLoader(
    dexPath: String,
    nativeLibraryPath: String,
    private val hostClassLoader: ClassLoader,
    val targetClassIndex: TargetClassIndex
) : ClassLoader(hostClassLoader) {

    private val platformClassLoader: ClassLoader =
        hostClassLoader.parent ?: ClassLoader.getSystemClassLoader()

    private val routingPolicy = ClassLoadRoutingPolicy(targetClassIndex)

    private val filteringParent = FilteringParentClassLoader(
        platformClassLoader = platformClassLoader,
        hostClassLoader = hostClassLoader,
        routingPolicy = routingPolicy
    )

    private val targetDexClassLoader = PathClassLoader(
        dexPath,
        nativeLibraryPath,
        filteringParent
    )

    private val traceByClass = ConcurrentHashMap<String, ClassLoadTrace>()

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        val owner = routingPolicy.ownerFor(name)
        return try {
            val loaded = when (owner) {
                ClassLoadOwner.PLATFORM -> platformClassLoader.loadClass(name)
                ClassLoadOwner.HOST,
                ClassLoadOwner.HOST_FALLBACK -> hostClassLoader.loadClass(name)
                ClassLoadOwner.TARGET_APK -> {
                    // Do not fall back when the index says the target owns the class. A missing or
                    // malformed target class must remain a visible runtime failure.
                    targetDexClassLoader.loadClass(name)
                }
            }

            if (resolve) {
                resolveClassIfOwnedByThisLoader(loaded)
            }

            traceByClass[name] = ClassLoadTrace(
                requestedClass = name,
                owner = owner,
                source = routingPolicy.sourceFor(name),
                definingClassLoader = loaded.classLoader?.describe() ?: "BOOT_CLASSLOADER"
            )
            loaded
        } catch (error: Throwable) {
            traceByClass[name] = ClassLoadTrace(
                requestedClass = name,
                owner = owner,
                source = routingPolicy.sourceFor(name),
                definingClassLoader = "LOAD_FAILED ${error.javaClass.name}: ${error.message ?: "no message"}"
            )
            throw error
        }
    }

    override fun getResource(name: String): URL? {
        return targetDexClassLoader.getResource(name) ?: hostClassLoader.getResource(name)
    }

    override fun getResources(name: String): Enumeration<URL> {
        val urls = LinkedHashSet<URL>()
        Collections.list(targetDexClassLoader.getResources(name)).forEach(urls::add)
        Collections.list(hostClassLoader.getResources(name)).forEach(urls::add)
        return Collections.enumeration(urls)
    }

    fun traceSnapshot(): List<ClassLoadTrace> = traceByClass.values.sortedBy { it.requestedClass }

    fun ownershipFor(className: String): ClassLoadOwner = routingPolicy.ownerFor(className)

    fun targetDexLoader(): ClassLoader = targetDexClassLoader

    private fun resolveClassIfOwnedByThisLoader(loaded: Class<*>) {
        if (loaded.classLoader === this) {
            resolveClass(loaded)
        }
    }

    private class FilteringParentClassLoader(
        private val platformClassLoader: ClassLoader,
        private val hostClassLoader: ClassLoader,
        private val routingPolicy: ClassLoadRoutingPolicy
    ) : ClassLoader(null) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (routingPolicy.ownerFor(name) == ClassLoadOwner.TARGET_APK) {
                throw ClassNotFoundException("Target-owned class must be loaded by the target DEX loader: $name")
            }

            val loaded = if (ClassLoadRoutingPolicy.isPlatformClass(name)) {
                platformClassLoader.loadClass(name)
            } else {
                hostClassLoader.loadClass(name)
            }

            if (resolve) {
                resolveClass(loaded)
            }
            return loaded
        }
    }

    private fun ClassLoader.describe(): String {
        return "${javaClass.name}@${Integer.toHexString(System.identityHashCode(this))}"
    }
}
