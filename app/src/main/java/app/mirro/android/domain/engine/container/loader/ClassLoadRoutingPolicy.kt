package app.mirro.android.domain.engine.container.loader

enum class ClassLoadOwner {
    PLATFORM,
    HOST,
    TARGET_APK,
    HOST_FALLBACK
}

/**
 * Explicit class ownership rules for the target runtime.
 *
 * Platform classes always remain shared with Android. Mirro classes always remain host-owned.
 * Everything else is target-owned only when the DEX index proves that the target contains it.
 */
class ClassLoadRoutingPolicy(
    private val targetClassIndex: TargetClassIndex
) {
    fun ownerFor(className: String): ClassLoadOwner {
        return when {
            isPlatformClass(className) -> ClassLoadOwner.PLATFORM
            className.startsWith("app.mirro.android.") -> ClassLoadOwner.HOST
            targetClassIndex.contains(className) -> ClassLoadOwner.TARGET_APK
            else -> ClassLoadOwner.HOST_FALLBACK
        }
    }

    fun sourceFor(className: String): String? = targetClassIndex.sourceFor(className)

    companion object {
        fun isPlatformClass(className: String): Boolean {
            return className.startsWith("java.") ||
                    className.startsWith("javax.") ||
                    className.startsWith("android.") ||
                    className.startsWith("dalvik.") ||
                    className.startsWith("org.w3c.") ||
                    className.startsWith("org.xml.")
        }
    }
}
