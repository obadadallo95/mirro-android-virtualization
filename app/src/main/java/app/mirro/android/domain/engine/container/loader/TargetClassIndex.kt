package app.mirro.android.domain.engine.container.loader

import dalvik.system.DexFile
import java.io.IOException

/**
 * Ownership index for classes physically present in the target APK set.
 *
 * The index is deliberately based on DEX contents rather than package prefixes. A prefix-only
 * child-first loader would still be ambiguous for dependencies that are absent from one target
 * but present in the host.
 */
data class TargetClassIndex(
    private val classToSource: Map<String, String>
) {
    val classCount: Int get() = classToSource.size

    fun contains(className: String): Boolean = classToSource.containsKey(className)

    fun sourceFor(className: String): String? = classToSource[className]

    fun classes(): Set<String> = classToSource.keys

    companion object {
        fun fromClassNames(classNames: Set<String>, source: String = "test-target.apk"): TargetClassIndex {
            return TargetClassIndex(classNames.associateWith { source })
        }

        /**
         * Indexes the base APK and all split candidates. Configuration-only splits are harmless:
         * they contain no DEX entries and therefore add nothing to the index.
         */
        fun fromApkPaths(apkPaths: List<String>): TargetClassIndex {
            val classToSource = linkedMapOf<String, String>()

            apkPaths.filter { it.isNotBlank() }.forEach { apkPath ->
                val dexFile = try {
                    @Suppress("DEPRECATION")
                    DexFile(apkPath)
                } catch (e: IOException) {
                    throw IllegalStateException("Unable to index target DEX from $apkPath", e)
                }

                try {
                    val entries = dexFile.entries()
                    while (entries.hasMoreElements()) {
                        val className = entries.nextElement()
                        classToSource.putIfAbsent(className, apkPath)
                    }
                } finally {
                    try {
                        dexFile.close()
                    } catch (_: IOException) {
                        // Closing is best-effort; loading has already completed.
                    }
                }
            }

            return TargetClassIndex(classToSource)
        }
    }
}
