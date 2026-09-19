package app.mirro.android.domain.engine.container.loader

import android.content.Context
import android.content.res.Resources
import app.mirro.android.domain.engine.container.model.ApkDescriptor
import java.io.File

/**
 * Result container for loaded classes, resources, and classloader instance.
 */
data class LoadedApkRuntime(
    val descriptor: ApkDescriptor,
    val classLoader: ClassLoader,
    val resources: Resources,
    val classIndex: TargetClassIndex = TargetClassIndex.fromClassNames(emptySet())
)

/**
 * Loads target APK bytecode and Android resource assets into an isolated ClassLoader.
 */
class DexRuntimeLoader(private val context: Context) {

    /**
     * Loads the target application's classes and resources.
     */
    fun load(descriptor: ApkDescriptor): LoadedApkRuntime {
        // Index ownership before class loading so duplicate dependencies can be routed
        // deterministically instead of relying on PathClassLoader's parent-first behavior.
        val classIndex = TargetClassIndex.fromApkPaths(descriptor.executableApkPaths)
        val dexPath = descriptor.executableApkPaths.joinToString(File.pathSeparator)
        val nativeLibPath = descriptor.nativeLibraryDir

        val classLoader = MirroTargetClassLoader(
            dexPath,
            nativeLibPath,
            hostClassLoader = context.classLoader,
            targetClassIndex = classIndex
        )

        val resources = loadResources(descriptor)

        return LoadedApkRuntime(
            descriptor = descriptor,
            classLoader = classLoader,
            resources = resources,
            classIndex = classIndex
        )
    }

    private fun loadResources(descriptor: ApkDescriptor): Resources {
        return try {
            val targetContext = context.createPackageContext(
                descriptor.packageName,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
            )
            targetContext.resources
        } catch (_: Exception) {
            try {
                context.packageManager.getResourcesForApplication(descriptor.packageName)
            } catch (_: Exception) {
                // Fallback to host resources
                context.resources
            }
        }
    }
}
